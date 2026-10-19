package com.example.experiment_service.service;

import com.example.experiment_service.dto.FlexsimInfeedResourceDetailDto;
import com.example.experiment_service.dto.FlexsimSortPlanDetailDto;
import com.example.experiment_service.dto.FlexsimZoneResourceDetailDto;
import com.example.experiment_service.dto.InfeedResourceDetailDto;
import com.example.experiment_service.dto.SortPlanDetailDto;
import com.example.experiment_service.dto.ZoneResourceDetailDto;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.entity.InfeedResourceDetail;
import com.example.experiment_service.entity.SortPlanDetail;
import com.example.experiment_service.entity.ZoneResourceDetail;
import com.example.experiment_service.repository.InfeedResourceDetailRepository;
import com.example.experiment_service.repository.SortPlanDetailRepository;
import com.example.experiment_service.repository.ZoneResourceDetailRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saves and reads the three "detail lists" of an experiment: zone, infeed and sort plan.
 *
 * SAVE = REPLACE. When the client sends a list for an experiment, we first delete the old rows of
 * that experiment and then insert the new ones, in ONE transaction. Sending the same list twice
 * gives the same result (this is called "idempotent"). The old app only ADDED rows, so a retry
 * made duplicate rows.
 *
 * Who may save: only the owner of the experiment (same rule as changing the experiment).
 * Who may read: any allowed role (the controller checks the roles).
 */
@Service
public class ResourceDetailsService {

    /** A safety limit. A request with more rows than this is refused (400). */
    private static final int MAX_ROWS_PER_REQUEST = 5000;
    private static final int MAX_TEXT_LENGTH = 255;

    private final ExperimentService experimentService;
    private final ZoneResourceDetailRepository zoneRepository;
    private final InfeedResourceDetailRepository infeedRepository;
    private final SortPlanDetailRepository sortPlanRepository;

    public ResourceDetailsService(
            ExperimentService experimentService,
            ZoneResourceDetailRepository zoneRepository,
            InfeedResourceDetailRepository infeedRepository,
            SortPlanDetailRepository sortPlanRepository) {
        this.experimentService = experimentService;
        this.zoneRepository = zoneRepository;
        this.infeedRepository = infeedRepository;
        this.sortPlanRepository = sortPlanRepository;
    }

    // ------------------------------------------------------------------ ZONE

    @Transactional
    public String saveZoneDetails(List<ZoneResourceDetailDto> dtos, AuthenticatedUser user) {
        if (dtos == null || dtos.isEmpty()) {
            return "Zone Resource Details Skipped";
        }
        Long experimentId = singleExperimentId(dtos.stream().map(ZoneResourceDetailDto::experimentId).toList());
        checkRowCount(dtos.size());

        List<ZoneResourceDetail> rows = new ArrayList<>(dtos.size());
        for (ZoneResourceDetailDto dto : dtos) {
            checkLength(dto.zoneId(), "zoneId");
            checkLength(dto.chutes(), "chutes");
            try {
                ChuteIdParser.parse(dto.chutes(), true);   // only to check the text; bad text gives 400
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
            ZoneResourceDetail row = new ZoneResourceDetail();
            row.setShiftId(dto.shiftId());
            row.setZoneId(dto.zoneId());
            row.setResourceId(dto.resourceId());
            row.setChutes(dto.chutes());
            rows.add(row);
        }

        Experiment experiment = experimentService.findOwnedOrThrow(experimentId, user);
        rows.forEach(row -> row.setExperiment(experiment));
        zoneRepository.deleteByExperimentId(experimentId);
        zoneRepository.saveAll(rows);
        return "Zone Resource Details Saved";
    }

    @Transactional(readOnly = true)
    public List<FlexsimZoneResourceDetailDto> getZoneDetails(Long experimentId) {
        experimentService.requireExists(experimentId);
        Sort sort = Sort.by("shiftId").ascending()
                .and(Sort.by("zoneId").ascending())
                .and(Sort.by("resourceId").ascending())
                .and(Sort.by("id").ascending());
        return zoneRepository.findByExperiment_ExperimentId(experimentId, sort).stream()
                .map(row -> new FlexsimZoneResourceDetailDto(
                        row.getShiftId(),
                        row.getZoneId(),
                        row.getResourceId(),
                        row.getChutes(),
                        ChuteIdParser.parse(row.getChutes(), false)))
                .toList();
    }

    // ---------------------------------------------------------------- INFEED

    @Transactional
    public String saveInfeedDetails(List<InfeedResourceDetailDto> dtos, AuthenticatedUser user) {
        if (dtos == null || dtos.isEmpty()) {
            return "Infeed Resource Details Skipped";
        }
        Long experimentId = singleExperimentId(dtos.stream().map(InfeedResourceDetailDto::experimentId).toList());
        checkRowCount(dtos.size());

        List<InfeedResourceDetail> rows = new ArrayList<>(dtos.size());
        for (InfeedResourceDetailDto dto : dtos) {
            checkLength(dto.zoneId(), "zoneId");
            checkLength(dto.infeed(), "infeed");
            checkLength(dto.tc(), "tc");
            InfeedResourceDetail row = new InfeedResourceDetail();
            row.setShiftId(dto.shiftId());
            row.setZoneId(dto.zoneId());
            row.setInfeed(dto.infeed());
            row.setTc(dto.tc());
            row.setNoOfResources(dto.noOfResources());
            row.setActive(dto.active());
            rows.add(row);
        }

        Experiment experiment = experimentService.findOwnedOrThrow(experimentId, user);
        rows.forEach(row -> row.setExperiment(experiment));
        infeedRepository.deleteByExperimentId(experimentId);
        infeedRepository.saveAll(rows);
        return "Infeed Resource Details Saved";
    }

    @Transactional(readOnly = true)
    public List<FlexsimInfeedResourceDetailDto> getInfeedDetails(Long experimentId) {
        experimentService.requireExists(experimentId);
        Sort sort = Sort.by("shiftId").ascending()
                .and(Sort.by("tc").ascending())
                .and(Sort.by("id").ascending());
        return infeedRepository.findByExperiment_ExperimentId(experimentId, sort).stream()
                .map(row -> new FlexsimInfeedResourceDetailDto(row.getShiftId(), row.getTc(), row.getNoOfResources()))
                .toList();
    }

    // ------------------------------------------------------------- SORT PLAN

    @Transactional
    public String saveSortPlanDetails(List<SortPlanDetailDto> dtos, AuthenticatedUser user) {
        if (dtos == null || dtos.isEmpty()) {
            return "Sort Plan Details Skipped";
        }
        Long experimentId = singleExperimentId(dtos.stream().map(SortPlanDetailDto::experimentId).toList());
        checkRowCount(dtos.size());

        List<SortPlanDetail> rows = new ArrayList<>(dtos.size());
        for (SortPlanDetailDto dto : dtos) {
            checkLength(dto.oldSortName(), "oldSortName");
            checkLength(dto.newSortName(), "newSortName");
            SortPlanDetail row = new SortPlanDetail();
            row.setOldSortName(dto.oldSortName());
            row.setNewSortName(dto.newSortName());
            rows.add(row);
        }

        Experiment experiment = experimentService.findOwnedOrThrow(experimentId, user);
        rows.forEach(row -> row.setExperiment(experiment));
        sortPlanRepository.deleteByExperimentId(experimentId);
        sortPlanRepository.saveAll(rows);
        return "Sort Plan Details Saved";
    }

    @Transactional(readOnly = true)
    public List<FlexsimSortPlanDetailDto> getSortPlanDetails(Long experimentId) {
        experimentService.requireExists(experimentId);
        return sortPlanRepository.findByExperiment_ExperimentId(experimentId, Sort.by("id").ascending()).stream()
                .map(row -> new FlexsimSortPlanDetailDto(row.getOldSortName(), row.getNewSortName()))
                .toList();
    }

    // --------------------------------------------------------------- helpers

    /** Every row of one request must name the SAME experiment. Returns that id. */
    private Long singleExperimentId(List<Long> ids) {
        Set<Long> distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.size() != 1 || ids.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Every row must have the same experimentId");
        }
        return distinct.iterator().next();
    }

    private void checkRowCount(int count) {
        if (count > MAX_ROWS_PER_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many rows in one request (max " + MAX_ROWS_PER_REQUEST + ")");
        }
    }

    private void checkLength(String value, String fieldName) {
        if (value != null && value.length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " is too long (max " + MAX_TEXT_LENGTH + " characters)");
        }
    }
}