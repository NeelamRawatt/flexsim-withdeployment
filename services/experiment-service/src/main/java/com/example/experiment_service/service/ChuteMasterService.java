package com.example.experiment_service.service;

import com.example.experiment_service.dto.SortNameSuggestionDto;
import com.example.experiment_service.entity.ChuteMaster;
import com.example.experiment_service.enums.ChuteType;
import com.example.experiment_service.repository.ChuteMasterRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives the "sort name" suggestions for the auto-complete box.
 * The chute list itself is filled by the Flyway file V2__seed_chute_master.sql (not by Java code).
 */
@Service
public class ChuteMasterService {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 2000;

    private final ChuteMasterRepository chuteMasterRepository;

    public ChuteMasterService(ChuteMasterRepository chuteMasterRepository) {
        this.chuteMasterRepository = chuteMasterRepository;
    }

    @Transactional(readOnly = true)
    public List<SortNameSuggestionDto> getSortNameSuggestions(String query, Integer limit) {
        String digits = onlyDigits(query);
        int resolvedLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<SortNameSuggestionDto> suggestions = new ArrayList<>();
        for (ChuteMaster chute : chuteMasterRepository.findAll()) {
            // Only SPIRAL chutes have cages 1..13. All others use cage 0.
            List<Integer> cages = chute.getChuteType() == ChuteType.SPIRAL
                    ? IntStream.rangeClosed(1, 13).boxed().toList()
                    : List.of(0);

            for (Integer cageId : cages) {
                String sortName = String.format(Locale.ROOT, "%03d%02d", chute.getChuteId(), cageId);
                if (!digits.isEmpty() && !sortName.startsWith(digits)) {
                    continue;
                }
                suggestions.add(new SortNameSuggestionDto(
                        sortName,
                        chute.getChuteId(),
                        cageId,
                        chute.getZoneId(),
                        chute.getZoneName(),
                        chute.getChuteType().name(),
                        String.format(
                                Locale.ROOT,
                                "%s | CH%s | %s (Zone-%d) | %s | Cage %02d",
                                sortName,
                                chute.getChuteId(),
                                chute.getZoneName(),
                                chute.getZoneId(),
                                chute.getChuteType().name(),
                                cageId)));
            }
        }

        return suggestions.stream()
                .sorted(Comparator.comparing(SortNameSuggestionDto::sortName))
                .limit(resolvedLimit)
                .toList();
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}