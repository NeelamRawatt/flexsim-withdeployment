package com.example.experiment_service.service;

import static com.example.experiment_service.testsupport.StatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResourceDetailsServiceTest {

    private static final Long EXP_ID = 1001L;
    private static final AuthenticatedUser USER = new AuthenticatedUser(5L, "owner", "OPERATOR");

    @Mock
    private ExperimentService experimentService;
    @Mock
    private ZoneResourceDetailRepository zoneRepository;
    @Mock
    private InfeedResourceDetailRepository infeedRepository;
    @Mock
    private SortPlanDetailRepository sortPlanRepository;

    private ResourceDetailsService service;

    @BeforeEach
    void setUp() {
        service = new ResourceDetailsService(experimentService, zoneRepository, infeedRepository, sortPlanRepository);
    }

    private static ZoneResourceDetailDto zone(Long expId, int shift, String zoneId, int resource, String chutes) {
        return new ZoneResourceDetailDto(expId, shift, zoneId, resource, chutes);
    }

    private Experiment ownedExperiment() {
        Experiment e = new Experiment();
        e.setExperimentId(EXP_ID);
        when(experimentService.findOwnedOrThrow(EXP_ID, USER)).thenReturn(e);
        return e;
    }

    // ------------------------------------------------------------- zone save

    @Test
    void savingZonesDeletesOldRowsFirstThenInsertsNewOnes() {
        Experiment experiment = ownedExperiment();

        String answer = service.saveZoneDetails(List.of(zone(EXP_ID, 1, "Z1", 1, "[1,2,3]")), USER);

        assertThat(answer).isEqualTo("Zone Resource Details Saved");
        InOrder order = inOrder(zoneRepository);
        order.verify(zoneRepository).deleteByExperimentId(EXP_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ZoneResourceDetail>> rows = ArgumentCaptor.forClass(List.class);
        order.verify(zoneRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(1);
        assertThat(rows.getValue().get(0).getExperiment()).isSameAs(experiment);
        assertThat(rows.getValue().get(0).getChutes()).isEqualTo("[1,2,3]");
    }

    @Test
    void emptyOrNullListIsSkippedAndTouchesNothing() {
        assertThat(service.saveZoneDetails(List.of(), USER)).isEqualTo("Zone Resource Details Skipped");
        assertThat(service.saveZoneDetails(null, USER)).isEqualTo("Zone Resource Details Skipped");
        verifyNoInteractions(experimentService, zoneRepository);
    }

    @Test
    void rowsWithDifferentExperimentIdsGive400() {
        assertStatus(() -> service.saveZoneDetails(
                List.of(zone(EXP_ID, 1, "Z1", 1, "1"), zone(EXP_ID + 1, 1, "Z1", 2, "2")), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(experimentService, zoneRepository);
    }

    @Test
    void rowWithoutExperimentIdGives400() {
        assertStatus(() -> service.saveZoneDetails(List.of(zone(null, 1, "Z1", 1, "1")), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(experimentService, zoneRepository);
    }

    @Test
    void badChuteTextGives400BeforeAnythingIsDeleted() {
        assertStatus(() -> service.saveZoneDetails(List.of(zone(EXP_ID, 1, "Z1", 1, "[1,abc]")), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(experimentService, zoneRepository);
    }

    @Test
    void textLongerThan255CharactersGives400() {
        assertStatus(() -> service.saveZoneDetails(List.of(zone(EXP_ID, 1, "z".repeat(256), 1, "1")), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void moreThan5000RowsGive400() {
        List<ZoneResourceDetailDto> tooMany = Collections.nCopies(5001, zone(EXP_ID, 1, "Z1", 1, "1"));

        assertStatus(() -> service.saveZoneDetails(tooMany, USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void notTheOwnerGives403AndNothingIsDeleted() {
        when(experimentService.findOwnedOrThrow(EXP_ID, USER))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no"));

        assertStatus(() -> service.saveZoneDetails(List.of(zone(EXP_ID, 1, "Z1", 1, "1")), USER), HttpStatus.FORBIDDEN);
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void unknownExperimentGives404() {
        when(experimentService.findOwnedOrThrow(EXP_ID, USER))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));

        assertStatus(() -> service.saveZoneDetails(List.of(zone(EXP_ID, 1, "Z1", 1, "1")), USER), HttpStatus.NOT_FOUND);
        verifyNoInteractions(zoneRepository);
    }

    // -------------------------------------------------------------- zone read

    @Test
    void readingZonesGivesChuteIdsAsNumbersAndSkipsBadOldValues() {
        ZoneResourceDetail good = new ZoneResourceDetail();
        good.setShiftId(1);
        good.setZoneId("Z1");
        good.setResourceId(2);
        good.setChutes("[1,2,3]");
        ZoneResourceDetail bad = new ZoneResourceDetail();
        bad.setChutes("4,oops,5");
        when(zoneRepository.findByExperiment_ExperimentId(eq(EXP_ID), any(Sort.class))).thenReturn(List.of(good, bad));

        List<FlexsimZoneResourceDetailDto> result = service.getZoneDetails(EXP_ID);

        verify(experimentService).requireExists(EXP_ID);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).chuteIds()).containsExactly(1, 2, 3);
        assertThat(result.get(0).chutes()).isEqualTo("[1,2,3]");
        assertThat(result.get(1).chuteIds()).containsExactly(4, 5);
    }

    @Test
    void readingZonesOfUnknownExperimentGives404() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing")).when(experimentService).requireExists(7L);

        assertStatus(() -> service.getZoneDetails(7L), HttpStatus.NOT_FOUND);
        verifyNoInteractions(zoneRepository);
    }

    // ----------------------------------------------------------------- infeed

    @Test
    void savingAndReadingInfeedRows() {
        Experiment experiment = ownedExperiment();
        InfeedResourceDetailDto dto = new InfeedResourceDetailDto(EXP_ID, 2, "Z1", "I1", "TCA", 4, true);

        assertThat(service.saveInfeedDetails(List.of(dto), USER)).isEqualTo("Infeed Resource Details Saved");

        InOrder order = inOrder(infeedRepository);
        order.verify(infeedRepository).deleteByExperimentId(EXP_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InfeedResourceDetail>> rows = ArgumentCaptor.forClass(List.class);
        order.verify(infeedRepository).saveAll(rows.capture());
        InfeedResourceDetail saved = rows.getValue().get(0);
        assertThat(saved.getExperiment()).isSameAs(experiment);
        assertThat(saved.getTc()).isEqualTo("TCA");
        assertThat(saved.getNoOfResources()).isEqualTo(4);
        assertThat(saved.getActive()).isTrue();

        when(infeedRepository.findByExperiment_ExperimentId(eq(EXP_ID), any(Sort.class))).thenReturn(List.of(saved));
        List<FlexsimInfeedResourceDetailDto> read = service.getInfeedDetails(EXP_ID);
        assertThat(read).hasSize(1);
        assertThat(read.get(0).tc()).isEqualTo("TCA");
        assertThat(read.get(0).noOfResources()).isEqualTo(4);
    }

    @Test
    void infeedEmptyListIsSkipped() {
        assertThat(service.saveInfeedDetails(List.of(), USER)).isEqualTo("Infeed Resource Details Skipped");
        verifyNoInteractions(infeedRepository);
    }

    // -------------------------------------------------------------- sort plan

    @Test
    void savingAndReadingSortPlanRows() {
        ownedExperiment();

        assertThat(service.saveSortPlanDetails(List.of(new SortPlanDetailDto(EXP_ID, "OLD", "NEW")), USER))
                .isEqualTo("Sort Plan Details Saved");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SortPlanDetail>> rows = ArgumentCaptor.forClass(List.class);
        verify(sortPlanRepository).saveAll(rows.capture());
        SortPlanDetail saved = rows.getValue().get(0);

        when(sortPlanRepository.findByExperiment_ExperimentId(eq(EXP_ID), any(Sort.class))).thenReturn(List.of(saved));
        List<FlexsimSortPlanDetailDto> read = service.getSortPlanDetails(EXP_ID);
        assertThat(read).hasSize(1);
        assertThat(read.get(0).oldSortName()).isEqualTo("OLD");
        assertThat(read.get(0).newSortName()).isEqualTo("NEW");
    }

    @Test
    void sortPlanNameTooLongGives400() {
        assertStatus(() -> service.saveSortPlanDetails(
                List.of(new SortPlanDetailDto(EXP_ID, "x".repeat(256), "NEW")), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(sortPlanRepository);
    }
}