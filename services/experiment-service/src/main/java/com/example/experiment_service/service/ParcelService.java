package com.example.experiment_service.service;

import com.example.experiment_service.dto.FlexsimParcelDto;
import com.example.experiment_service.dto.ParcelCountRequestDto;
import com.example.experiment_service.dto.ParcelCountResponseDto;
import com.example.experiment_service.dto.SortPlanResponseDto;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.entity.ParcelDetail;
import com.example.experiment_service.repository.ExperimentRepository;
import com.example.experiment_service.repository.ParcelDetailRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The parcel logic. Same rules as the old app (ParcelService), moved here because
 * the parcels are chosen with the EXPERIMENT's settings (date, shift, more / fewer parcels).
 *
 * 1. getParcelsForExperiment (for FlexSim, through the flexsim-worker):
 *      - the real parcels of the experiment's day (selectedDate) between its start and end
 *        NIGHT SHIFT: end before start = the next day (same rule as the worker)
 *      - flexsimTimeSlot = seconds after the start + 180
 *      - then "more / fewer parcels" if the experiment asks for it (see scale())
 * 2. getParcelCount / getSortPlan (for the screen): numbers for a time window.
 */
@Service
@Transactional(readOnly = true)
public class ParcelService {

    /** FlexSim needs 3 minutes to warm up before the first parcel (same as the old app). */
    static final int FLEXSIM_WARM_UP_SECONDS = 180;
    /** Protection: one question may cover at most 2 days (a shift is shorter). */
    static final Duration MAX_WINDOW = Duration.ofDays(2);

    private final ParcelDetailRepository parcelRepository;
    private final ExperimentRepository experimentRepository;

    public ParcelService(ParcelDetailRepository parcelRepository, ExperimentRepository experimentRepository) {
        this.parcelRepository = parcelRepository;
        this.experimentRepository = experimentRepository;
    }

    // ------------------------------------------------------------------ for FlexSim

    public List<FlexsimParcelDto> getParcelsForExperiment(Long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Experiment not found with id: " + experimentId));
        if (experiment.getSelectedDate() == null || experiment.getStartTime() == null || experiment.getEndTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The experiment needs a date, a start time and an end time before parcels can be read");
        }

        LocalDateTime start = experiment.getStartTime();
        LocalDateTime end = shiftEnd(start, experiment.getEndTime());

        List<FlexsimParcelDto> parcels = new ArrayList<>();
        for (ParcelDetail parcel : parcelRepository
                .findByParcelEdtDateAndParcelTimeSlotBetweenOrderByParcelTimeSlotAscIdAsc(
                        experiment.getSelectedDate(), start, end)) {
            parcels.add(toFlexsim(parcel, start));
        }
        return scale(parcels, experiment.getParcelChangeMode(), experiment.getParcelChangeValue(),
                experiment.getNewParcelCount());
    }

    /** NIGHT SHIFT rule: end before (or equal to) start = the shift ends on the next day. */
    static LocalDateTime shiftEnd(LocalDateTime start, LocalDateTime end) {
        return end.isAfter(start) ? end : end.plusDays(1);
    }

    static FlexsimParcelDto toFlexsim(ParcelDetail parcel, LocalDateTime start) {
        int flexsimTimeSlot = (int) Duration.between(start, parcel.getParcelTimeSlot()).getSeconds()
                + FLEXSIM_WARM_UP_SECONDS;
        return new FlexsimParcelDto(parcel.getParcelTimeSlot(), parcel.getParcelId(), parcel.getParcelInfeedName(),
                parcel.getSortName(), parcel.getRejectCode(), parcel.getParcelWeight(), parcel.getParcelWidth(),
                parcel.getParcelLength(), parcel.getParcelHeight(), parcel.getParcelVolume(), flexsimTimeSlot);
    }

    /**
     * "What if there were MORE (or FEWER) parcels?" (same rules as the old app)
     *
     *   no mode, or value <= 0          -> the real parcels, unchanged
     *   newParcelCount set              -> exactly that many
     *   mode DECREMENT / DECREASE, 20   -> 20 % fewer
     *   any other mode (INCREMENT), 20  -> 20 % more
     *
     * FEWER: every n-th parcel is kept (spread over the whole shift, not just the first hours).
     * MORE:  copies of parcels spread over the shift are added, with ids like "P1-DUP-1".
     * Always the same result for the same input (no random), so two runs are comparable.
     */
    static List<FlexsimParcelDto> scale(List<FlexsimParcelDto> base, String mode, Integer changeValue,
                                        Integer newParcelCount) {
        if (base.isEmpty()) {
            return new ArrayList<>();
        }
        if (mode == null || changeValue == null || changeValue <= 0) {
            return new ArrayList<>(base);
        }
        int target = targetCount(base.size(), mode, changeValue, newParcelCount);
        if (target <= 0) {
            return new ArrayList<>();
        }
        if (target == base.size()) {
            return new ArrayList<>(base);
        }
        return target < base.size() ? downsample(base, target) : upsample(base, target);
    }

    static int targetCount(int baseCount, String mode, int changeValue, Integer newParcelCount) {
        if (newParcelCount != null && newParcelCount > 0) {
            return newParcelCount;
        }
        double delta = baseCount * changeValue / 100.0;
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if ("DECREMENT".equals(normalized) || "DECREASE".equals(normalized)) {
            return Math.max(0, (int) Math.round(baseCount - delta));
        }
        return Math.max(0, (int) Math.round(baseCount + delta));
    }

    private static List<FlexsimParcelDto> downsample(List<FlexsimParcelDto> source, int target) {
        List<FlexsimParcelDto> selected = new ArrayList<>(target);
        double step = (double) source.size() / target;
        for (int i = 0; i < target; i++) {
            selected.add(source.get(Math.min((int) Math.floor(i * step), source.size() - 1)));
        }
        return selected;
    }

    private static List<FlexsimParcelDto> upsample(List<FlexsimParcelDto> source, int target) {
        List<FlexsimParcelDto> expanded = new ArrayList<>(target);
        expanded.addAll(source);
        int additional = target - source.size();
        for (int i = 0; i < additional; i++) {
            int index = Math.min((int) Math.floor(i * (double) source.size() / additional), source.size() - 1);
            FlexsimParcelDto original = source.get(index);
            String baseId = original.parcelId() == null || original.parcelId().isBlank()
                    ? "SYNTHETIC-PARCEL" : original.parcelId();
            expanded.add(original.withParcelId(baseId + "-DUP-" + (i + 1)));
        }
        return expanded;
    }

    // ------------------------------------------------------------------ for the screen

    public ParcelCountResponseDto getParcelCount(ParcelCountRequestDto request) {
        checkWindow(request);
        return new ParcelCountResponseDto(parcelRepository.countByParcelTimeSlotBetween(request.startTime(), request.endTime()));
    }

    /**
     * How many parcels go to each sort name. A sort name can hold several values ("10440|10440"):
     * only the FIRST one counts (same as the old app). TreeMap = the answer is sorted by name.
     */
    public SortPlanResponseDto getSortPlan(ParcelCountRequestDto request) {
        checkWindow(request);
        List<String> sortNames = parcelRepository.findSortNamesBetween(request.startTime(), request.endTime());
        Map<String, Long> counts = new TreeMap<>();
        for (String raw : sortNames) {
            counts.merge(firstSortName(raw), 1L, Long::sum);
        }
        return new SortPlanResponseDto((long) sortNames.size(), counts);
    }

    static String firstSortName(String raw) {
        return raw == null ? "" : raw.split("\\|")[0].trim();
    }

    private static void checkWindow(ParcelCountRequestDto request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }
        if (Duration.between(request.startTime(), request.endTime()).compareTo(MAX_WINDOW) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The time window can be at most 2 days");
        }
    }
}
