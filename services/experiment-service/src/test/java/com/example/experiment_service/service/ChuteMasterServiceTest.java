package com.example.experiment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.experiment_service.dto.SortNameSuggestionDto;
import com.example.experiment_service.entity.ChuteMaster;
import com.example.experiment_service.enums.ChuteType;
import com.example.experiment_service.repository.ChuteMasterRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChuteMasterServiceTest {

    @Mock
    private ChuteMasterRepository repository;

    private ChuteMasterService service;

    @BeforeEach
    void setUp() {
        service = new ChuteMasterService(repository);
        // One SPIRAL chute (13 cages), one DROP chute (cage 0) and one BOOM chute (cage 0).
        when(repository.findAll()).thenReturn(List.of(
                chute(402, 4, "West", ChuteType.DROP),
                chute(201, 4, "West", ChuteType.SPIRAL),
                chute(121, 2, "East", ChuteType.BOOM)));
    }

    private static ChuteMaster chute(int chuteId, int zoneId, String zoneName, ChuteType type) {
        ChuteMaster c = new ChuteMaster();
        c.setChuteId(chuteId);
        c.setZoneId(zoneId);
        c.setZoneName(zoneName);
        c.setChuteType(type);
        return c;
    }

    @Test
    void withoutQueryGivesAllSortNamesSortedAsText() {
        List<SortNameSuggestionDto> result = service.getSortNameSuggestions(null, null);

        assertThat(result).hasSize(15);   // 13 spiral cages + 1 drop + 1 boom
        assertThat(result.get(0).sortName()).isEqualTo("12100");
        assertThat(result.get(1).sortName()).isEqualTo("20101");
        assertThat(result.get(13).sortName()).isEqualTo("20113");
        assertThat(result.get(14).sortName()).isEqualTo("40200");
    }

    @Test
    void queryFiltersByStartOfTheSortName() {
        assertThat(service.getSortNameSuggestions("201", null)).hasSize(13);
        assertThat(service.getSortNameSuggestions("20105", null)).hasSize(1);
        assertThat(service.getSortNameSuggestions("999", null)).isEmpty();
    }

    @Test
    void lettersAndSymbolsInTheQueryAreIgnored() {
        assertThat(service.getSortNameSuggestions("CH-201", null)).hasSize(13);
        assertThat(service.getSortNameSuggestions("abc", null)).hasSize(15);   // no digits left = no filter
    }

    @Test
    void spiralChuteHasCagesAndLabel() {
        SortNameSuggestionDto fifth = service.getSortNameSuggestions("20105", null).get(0);

        assertThat(fifth.chuteId()).isEqualTo(201);
        assertThat(fifth.cageId()).isEqualTo(5);
        assertThat(fifth.chuteType()).isEqualTo("SPIRAL");
        assertThat(fifth.label()).isEqualTo("20105 | CH201 | West (Zone-4) | SPIRAL | Cage 05");
    }

    @Test
    void nonSpiralChuteUsesCageZero() {
        SortNameSuggestionDto drop = service.getSortNameSuggestions("40200", null).get(0);

        assertThat(drop.cageId()).isEqualTo(0);
        assertThat(drop.chuteType()).isEqualTo("DROP");
        assertThat(drop.label()).isEqualTo("40200 | CH402 | West (Zone-4) | DROP | Cage 00");
    }

    @Test
    void limitCutsTheListAndBadLimitFallsBackToDefault() {
        assertThat(service.getSortNameSuggestions(null, 5)).hasSize(5);
        assertThat(service.getSortNameSuggestions(null, 0)).hasSize(15);
        assertThat(service.getSortNameSuggestions(null, -3)).hasSize(15);
        assertThat(service.getSortNameSuggestions(null, 100000)).hasSize(15);
    }
}