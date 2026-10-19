package com.example.experiment_service.service;

import static com.example.experiment_service.testsupport.StatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.experiment_service.dto.ExperimentDto;
import com.example.experiment_service.dto.PageResponse;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.enums.ExperimentRunStatus;
import com.example.experiment_service.mapper.ExperimentMapper;
import com.example.experiment_service.repository.ExperimentRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * A "mock" is a fake helper made by Mockito. Here the repository (database) and the mapper are fakes,
 * so we test ONLY the rules inside ExperimentService. No database is needed.
 */
@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(5L, "owner", "OPERATOR");
    private static final AuthenticatedUser OTHER = new AuthenticatedUser(6L, "other", "OPERATOR");
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(9L, "boss", "ADMIN");

    @Mock
    private ExperimentRepository repository;
    @Mock
    private ExperimentMapper mapper;

    private ExperimentService service;

    @BeforeEach
    void setUp() {
        service = new ExperimentService(repository, mapper);
    }

    private static Experiment experimentOwnedBy(Long userId) {
        Experiment e = new Experiment();
        e.setExperimentId(1001L);
        e.setUserId(userId);
        return e;
    }

    // ---------------------------------------------------------------- create

    @Test
    void createTakesTheOwnerFromTheTokenNotFromTheBody() {
        when(repository.save(any(Experiment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(Experiment.class))).thenReturn(new ExperimentDto());

        service.create(OWNER, new ExperimentDto());

        ArgumentCaptor<Experiment> saved = ArgumentCaptor.forClass(Experiment.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(5L);
        assertThat(saved.getValue().getOwnerUsername()).isEqualTo("owner");
    }

    // ---------------------------------------------------------------- delete

    @Test
    void ownerCanDelete() {
        Experiment e = experimentOwnedBy(5L);
        when(repository.findById(1001L)).thenReturn(Optional.of(e));

        service.delete(1001L, OWNER);

        verify(repository).delete(e);
    }

    @Test
    void anotherOperatorCannotDelete() {
        when(repository.findById(1001L)).thenReturn(Optional.of(experimentOwnedBy(5L)));

        assertStatus(() -> service.delete(1001L, OTHER), HttpStatus.FORBIDDEN);

        verify(repository, never()).delete(any(Experiment.class));
    }

    @Test
    void adminCanDeleteAnyExperiment() {
        Experiment e = experimentOwnedBy(5L);
        when(repository.findById(1001L)).thenReturn(Optional.of(e));

        service.delete(1001L, ADMIN);

        verify(repository).delete(e);
    }

    @Test
    void deleteOfUnknownExperimentGives404() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertStatus(() -> service.delete(42L, OWNER), HttpStatus.NOT_FOUND);
    }

    @Test
    void experimentWithoutOwnerCannotBeDeletedByAnOperator() {
        when(repository.findById(1001L)).thenReturn(Optional.of(experimentOwnedBy(null)));

        assertStatus(() -> service.delete(1001L, OWNER), HttpStatus.FORBIDDEN);
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateByAnotherUserGives403AndChangesNothing() {
        when(repository.findById(1001L)).thenReturn(Optional.of(experimentOwnedBy(5L)));

        assertStatus(() -> service.update(1001L, new ExperimentDto(), OTHER), HttpStatus.FORBIDDEN);

        verifyNoInteractions(mapper);
    }

    @Test
    void updateByOwnerCopiesTheFields() {
        Experiment e = experimentOwnedBy(5L);
        ExperimentDto dto = new ExperimentDto();
        when(repository.findById(1001L)).thenReturn(Optional.of(e));
        when(mapper.toDto(e)).thenReturn(dto);

        assertThat(service.update(1001L, dto, OWNER)).isSameAs(dto);

        verify(mapper).copyEditableFields(dto, e);
    }

    @Test
    void allSortedAtLongerThan20CharactersGives400() {
        assertStatus(() -> service.updateAllSortedAt(1001L, "x".repeat(21), OWNER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    // ------------------------------------------------- findOwnedOrThrow etc.

    @Test
    void findOwnedOrThrowReturnsTheExperimentForTheOwner() {
        Experiment e = experimentOwnedBy(5L);
        when(repository.findById(1001L)).thenReturn(Optional.of(e));

        assertThat(service.findOwnedOrThrow(1001L, OWNER)).isSameAs(e);
    }

    @Test
    void findOwnedOrThrowGives403ForAnotherUserAnd404WhenMissing() {
        when(repository.findById(1001L)).thenReturn(Optional.of(experimentOwnedBy(5L)));
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertStatus(() -> service.findOwnedOrThrow(1001L, OTHER), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.findOwnedOrThrow(42L, OWNER), HttpStatus.NOT_FOUND);
    }

    @Test
    void requireExistsGives404WhenMissing() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertStatus(() -> service.requireExists(42L), HttpStatus.NOT_FOUND);
    }

    // --------------------------------------------------------------- history

    @Test
    void historyCapsThePageSizeAt100AndFixesNegativePage() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.history(-3, 100000, "createdAt,desc", null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void historySortsByRequestedFieldThenByExperimentIdForStablePaging() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.history(0, 8, "terminal,asc", "ALL");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        Sort sort = pageable.getValue().getSort();
        assertThat(sort.getOrderFor("terminal").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("experimentId")).isNotNull();
    }

    @Test
    void historyStatusNameIsMappedToTheRealFieldName() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.history(0, 8, "status,desc", null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("currentRunStatus")).isNotNull();
    }

    @Test
    void historyWithUnknownSortFieldGives400() {
        assertStatus(() -> service.history(0, 8, "password,asc", null), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void historyFiltersByKnownStatus() {
        when(repository.findByCurrentRunStatus(eq(ExperimentRunStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<ExperimentDto> result = service.history(0, 8, "createdAt,desc", "completed");

        assertThat(result.empty()).isTrue();
        verify(repository).findByCurrentRunStatus(eq(ExperimentRunStatus.COMPLETED), any(Pageable.class));
    }

    @Test
    void historyWithUnknownStatusGivesAnEmptyPageWithoutAskingTheDatabase() {
        PageResponse<ExperimentDto> result = service.history(0, 8, "createdAt,desc", "banana");

        assertThat(result.empty()).isTrue();
        assertThat(result.content()).isEmpty();
        verifyNoInteractions(repository);
    }
}