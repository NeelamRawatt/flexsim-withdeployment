package com.example.experiment_service.service;

import com.example.experiment_service.dto.ExperimentDto;
import com.example.experiment_service.dto.PageResponse;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.enums.ExperimentRunStatus;
import com.example.experiment_service.mapper.ExperimentMapper;
import com.example.experiment_service.repository.ExperimentRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The business logic for experiments. The controller only receives the request;
 * this class holds the rules (who may do what, limits, defaults).
 *
 * @Transactional = the database steps of one method are ONE unit: all happen or none.
 * readOnly = true tells the database "I will only read" (a little faster and safer).
 */
@Service
public class ExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * The JSON name the client may sort by -> the field name in the Experiment class.
     * A fixed list on purpose. If we passed any text from the client to the database,
     * a wrong name would crash with a 500 error, and it is bad practice anyway.
     */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdAt", "createdAt",
            "experimentId", "experimentId",
            "experimentName", "experimentName",
            "terminal", "terminal",
            "sortingType", "sortingType",
            "selectedDate", "selectedDate",
            "status", "currentRunStatus",
            "username", "ownerUsername");

    private final ExperimentRepository experimentRepository;
    private final ExperimentMapper experimentMapper;

    public ExperimentService(ExperimentRepository experimentRepository, ExperimentMapper experimentMapper) {
        this.experimentRepository = experimentRepository;
        this.experimentMapper = experimentMapper;
    }

    @Transactional
    public ExperimentDto create(AuthenticatedUser user, ExperimentDto dto) {
        Experiment entity = new Experiment();
        experimentMapper.copyEditableFields(dto, entity);
        // The owner comes from the TOKEN, never from the request body.
        entity.setUserId(user.userId());
        entity.setOwnerUsername(user.username());
        Experiment saved = experimentRepository.save(entity);
        return experimentMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ExperimentDto getById(Long experimentId) {
        return experimentMapper.toDto(findOrThrow(experimentId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExperimentDto> history(int page, int size, String sort, String status) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), buildSort(sort));

        String wanted = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        Page<Experiment> result;
        if (wanted.isEmpty() || "ALL".equals(wanted)) {
            result = experimentRepository.findAll(pageable);
        } else {
            try {
                result = experimentRepository.findByCurrentRunStatus(ExperimentRunStatus.valueOf(wanted), pageable);
            } catch (IllegalArgumentException unknownStatus) {
                // Same as the old app: an unknown status gives an empty list.
                result = Page.empty(pageable);
            }
        }
        return PageResponse.from(result.map(experimentMapper::toDto));
    }

    @Transactional
    public ExperimentDto update(Long experimentId, ExperimentDto dto, AuthenticatedUser user) {
        Experiment entity = findOrThrow(experimentId);
        requireOwner(entity, user);
        experimentMapper.copyEditableFields(dto, entity);
        // No save() needed: the entity is "managed" inside the transaction, so changes are saved at the end.
        return experimentMapper.toDto(entity);
    }

    @Transactional
    public ExperimentDto updateAllSortedAt(Long experimentId, String allSortedAt, AuthenticatedUser user) {
        if (allSortedAt != null && allSortedAt.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allSortedAt is too long (max 20 characters)");
        }
        Experiment entity = findOrThrow(experimentId);
        requireOwner(entity, user);
        entity.setAllSortedAt(allSortedAt);
        return experimentMapper.toDto(entity);
    }

    /**
     * Deletes ONE experiment row. The database removes the child rows by itself (ON DELETE CASCADE).
     * Run data and KPIs live in other services; they will be told with a Kafka event in a later step.
     * The owner may delete. ADMIN and DEVELOPER may delete any experiment (same rule as the old app).
     */
    @Transactional
    public void delete(Long experimentId, AuthenticatedUser user) {
        Experiment entity = findOrThrow(experimentId);
        boolean canForceDelete = "ADMIN".equals(user.role()) || "DEVELOPER".equals(user.role());
        if (!canForceDelete) {
            requireOwner(entity, user);
        }
        experimentRepository.delete(entity);
        log.info("Experiment {} deleted by user {} (role {})", experimentId, user.username(), user.role());
    }

    /**
     * For the other services of this application (zone, infeed, sort plan, parcel limits):
     * the experiment must exist (else 404) and the user must be its owner (else 403).
     * No @Transactional here on purpose: the caller already has a transaction and we join it.
     */
    public Experiment findOwnedOrThrow(Long experimentId, AuthenticatedUser user) {
        Experiment entity = findOrThrow(experimentId);
        requireOwner(entity, user);
        return entity;
    }

    /** The experiment must exist (else 404). Used by read URLs of the other services. */
    public void requireExists(Long experimentId) {
        findOrThrow(experimentId);
    }

    private Experiment findOrThrow(Long experimentId) {
        return experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Experiment not found with id: " + experimentId));
    }

    /** Only the creator may change an experiment. The check uses the user ID from the token. */
    private void requireOwner(Experiment experiment, AuthenticatedUser user) {
        if (experiment.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Experiment is not assigned to a user");
        }
        if (!experiment.getUserId().equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this experiment");
        }
    }

    /** "createdAt,desc" -> Sort. Unknown field gives 400. Adds experimentId as a tie-breaker so paging is stable. */
    private Sort buildSort(String sort) {
        String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",");
        String requested = parts[0].trim();
        String field = SORTABLE_FIELDS.get(requested);
        if (field == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sort by '" + requested + "'");
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort result = Sort.by(direction, field);
        if (!"experimentId".equals(field)) {
            result = result.and(Sort.by(Sort.Direction.DESC, "experimentId"));
        }
        return result;
    }
}