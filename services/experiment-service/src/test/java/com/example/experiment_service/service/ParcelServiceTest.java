package com.example.experiment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.experiment_service.dto.FlexsimParcelDto;
import com.example.experiment_service.dto.ParcelCountRequestDto;
import com.example.experiment_service.dto.SortPlanResponseDto;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.entity.ParcelDetail;
import com.example.experiment_service.repository.ExperimentRepository;
import com.example.experiment_service.repository.ParcelDetailRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Unit test of the parcel rules. The database is a fake (Mockito). */
@ExtendWith(MockitoExtension.class)
class ParcelServiceTest {

    private static final LocalDate DAY = LocalDate.of(2024, 11, 28);
    private static final LocalDateTime START = LocalDateTime.of(2024, 11, 28, 23, 0);

    @Mock
    private ParcelDetailRepository parcelRepository;
    @Mock
    private ExperimentRepository experimentRepository;

    private ParcelService service;

    @BeforeEach
    void setUp() {
        service = new ParcelService(parcelRepository, experimentRepository);
    }

    private static Experiment nightShift() {
        Experiment experiment = new Experiment();
        experiment.setExperimentId(42L);
        experiment.setSelectedDate(DAY);
        experiment.setStartTime(START);
        experiment.setEndTime(LocalDateTime.of(2024, 11, 28, 6, 30));   // end BEFORE start = night shift
        return experiment;
    }

    private static ParcelDetail parcel(String id, LocalDateTime time, String sortName) {
        ParcelDetail parcel = new ParcelDetail();
        parcel.setParcelId(id);
        parcel.setParcelTimeSlot(time);
        parcel.setParcelEdtDate(DAY);
        parcel.setParcelInfeedName("IF-02");
        parcel.setSortName(sortName);
        parcel.setParcelWeight(145.0);
        return parcel;
    }

    private static FlexsimParcelDto dto(String id) {
        return new FlexsimParcelDto(START, id, "IF-02", "10661", "", 1.0, 1.0, 1.0, 1.0, 1.0, 180);
    }

    @Test
    void nightShiftReadsUntilNextMorningAndAddsFlexsimTime() {
        when(experimentRepository.findById(42L)).thenReturn(Optional.of(nightShift()));
        LocalDateTime nextMorning = LocalDateTime.of(2024, 11, 29, 6, 30);
        when(parcelRepository.findByParcelEdtDateAndParcelTimeSlotBetweenOrderByParcelTimeSlotAscIdAsc(DAY, START, nextMorning))
                .thenReturn(List.of(parcel("P1", START.plusMinutes(10), "10661"),
                        parcel("P2", LocalDateTime.of(2024, 11, 29, 1, 0), "10440|10440")));

        List<FlexsimParcelDto> parcels = service.getParcelsForExperiment(42L);

        verify(parcelRepository).findByParcelEdtDateAndParcelTimeSlotBetweenOrderByParcelTimeSlotAscIdAsc(DAY, START, nextMorning);
        assertThat(parcels).extracting(FlexsimParcelDto::parcelId).containsExactly("P1", "P2");
        assertThat(parcels.get(0).flexsimTimeSlot()).isEqualTo(600 + 180);        // 10 min after start + warm-up
        assertThat(parcels.get(1).flexsimTimeSlot()).isEqualTo(2 * 3600 + 180);   // 01:00 = 2 h after 23:00
        assertThat(parcels.get(0).parcelWeight()).isEqualTo(145.0);
    }

    @Test
    void unknownExperimentIs404() {
        when(experimentRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getParcelsForExperiment(9L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void experimentWithoutTimesIs400() {
        Experiment experiment = nightShift();
        experiment.setStartTime(null);
        when(experimentRepository.findById(42L)).thenReturn(Optional.of(experiment));

        assertThatThrownBy(() -> service.getParcelsForExperiment(42L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }

    @Test
    void noChangeModeKeepsTheRealParcels() {
        List<FlexsimParcelDto> base = List.of(dto("A"), dto("B"), dto("C"));

        assertThat(ParcelService.scale(base, null, 20, null)).isEqualTo(base);
        assertThat(ParcelService.scale(base, "INCREMENT", 0, null)).isEqualTo(base);
    }

    @Test
    void fewerParcelsKeepsEveryNthParcel() {
        List<FlexsimParcelDto> base = List.of(dto("A"), dto("B"), dto("C"), dto("D"));

        List<FlexsimParcelDto> half = ParcelService.scale(base, "DECREMENT", 50, null);

        assertThat(half).extracting(FlexsimParcelDto::parcelId).containsExactly("A", "C");
    }

    @Test
    void moreParcelsAddsNamedCopies() {
        List<FlexsimParcelDto> base = List.of(dto("A"), dto("B"));

        List<FlexsimParcelDto> more = ParcelService.scale(base, "INCREMENT", 50, null);

        assertThat(more).extracting(FlexsimParcelDto::parcelId).containsExactly("A", "B", "A-DUP-1");
    }

    @Test
    void newParcelCountWinsOverThePercentage() {
        List<FlexsimParcelDto> base = List.of(dto("A"), dto("B"));

        assertThat(ParcelService.scale(base, "INCREMENT", 10, 5)).hasSize(5);
        assertThat(ParcelService.targetCount(100, "decrease", 25, null)).isEqualTo(75);
    }

    @Test
    void sortPlanCountsOnlyTheFirstSortName() {
        LocalDateTime end = START.plusHours(7);
        when(parcelRepository.findSortNamesBetween(START, end)).thenReturn(List.of("10661", "10661|20991", "10440 | 10440", "10440"));

        SortPlanResponseDto plan = service.getSortPlan(new ParcelCountRequestDto(START, end, null));

        assertThat(plan.totalParcelCount()).isEqualTo(4L);
        assertThat(plan.sortNameCounts()).containsExactly(
                org.assertj.core.api.Assertions.entry("10440", 2L),
                org.assertj.core.api.Assertions.entry("10661", 2L));
    }

    @Test
    void wrongTimeWindowsAre400() {
        assertThatThrownBy(() -> service.getParcelCount(new ParcelCountRequestDto(START, START, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("after");
        assertThatThrownBy(() -> service.getParcelCount(new ParcelCountRequestDto(START, START.plusDays(3), null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("2 days");
    }
}
