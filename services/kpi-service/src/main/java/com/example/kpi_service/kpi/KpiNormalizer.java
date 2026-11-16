package com.example.kpi_service.kpi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans up ONE raw snapshot from FlexSim, with the SAME rules the old app used when it saved
 * the snapshot into its 6 tables and read it back for React. So React gets the same numbers.
 *
 *   chutes:      every chute that appears in ANY chutewise map gets throughput, destinated,
 *                diverted and blockage state (0 when missing); lists only when not empty;
 *                blockage and total blockage time only when > 0.
 *   chute types: only BOOM, DROP, SPIRAL, REJECT (any case); shown as "Boom", "Drop" ...;
 *                unknown types are skipped; blockage only when > 0.
 *   resources:   zone and shift are read from the key, like "..._ZONE-2_1_7" (zone 2, shift 1, resource 7).
 *   conveyor:    occupancy = occupied slots / 522.
 *   missing numbers become 0, missing maps become empty maps.
 */
public final class KpiNormalizer {

    /** The sorter belt has 522 slots (same number as the old app). */
    static final double CONVEYOR_SLOTS = 522.0;
    private static final Set<String> CHUTE_TYPES = Set.of("BOOM", "DROP", "SPIRAL", "REJECT");
    private static final Pattern RESOURCE_KEY = Pattern.compile(".*ZONE-?(\\d+)_(\\d+)_(\\d+)$", Pattern.CASE_INSENSITIVE);

    private KpiNormalizer() {
    }

    public static SimulationKpi normalize(SimulationKpi raw) {
        Double time = raw.simulationTime() == null ? 0.0 : raw.simulationTime();

        // ---- chutes ----
        Set<String> chuteIds = new TreeSet<>();
        for (Map<String, ?> map : List.<Map<String, ?>>of(
                orEmpty(raw.chutewiseThroughput()), orEmpty(raw.chutewiseDestinated()), orEmpty(raw.chutewiseDiverted()),
                orEmpty(raw.chutewiseCageCount()), orEmpty(raw.chutewiseCageFillParcelCount()),
                orEmpty(raw.chutewiseCageFillParcelVolume()), orEmpty(raw.chutewiseFilledCageHistory()),
                orEmpty(raw.chutewiseBlockageState()), orEmpty(raw.chutewiseBlockage()),
                orEmpty(raw.chutewiseTotalBlockageTime()))) {
            for (String key : map.keySet()) {
                if (key != null && !key.isBlank()) {
                    chuteIds.add(key);
                }
            }
        }

        Map<String, Integer> throughput = new LinkedHashMap<>();
        Map<String, Integer> destinated = new LinkedHashMap<>();
        Map<String, Integer> diverted = new LinkedHashMap<>();
        Map<String, List<Integer>> cageCount = new LinkedHashMap<>();
        Map<String, List<Integer>> cageFillCount = new LinkedHashMap<>();
        Map<String, List<Double>> cageFillVolume = new LinkedHashMap<>();
        Map<String, List<List<FilledCage>>> filledHistory = new LinkedHashMap<>();
        Map<String, Integer> blockageState = new LinkedHashMap<>();
        Map<String, Integer> blockage = new LinkedHashMap<>();
        Map<String, Double> blockageTime = new LinkedHashMap<>();

        for (String chute : chuteIds) {
            throughput.put(chute, intOf(raw.chutewiseThroughput(), chute));
            destinated.put(chute, intOf(raw.chutewiseDestinated(), chute));
            diverted.put(chute, intOf(raw.chutewiseDiverted(), chute));
            putIfNotEmpty(cageCount, chute, orEmpty(raw.chutewiseCageCount()).get(chute));
            putIfNotEmpty(cageFillCount, chute, orEmpty(raw.chutewiseCageFillParcelCount()).get(chute));
            putIfNotEmpty(cageFillVolume, chute, orEmpty(raw.chutewiseCageFillParcelVolume()).get(chute));
            putIfNotEmpty(filledHistory, chute, orEmpty(raw.chutewiseFilledCageHistory()).get(chute));
            blockageState.put(chute, intOf(raw.chutewiseBlockageState(), chute) == 1 ? 1 : 0);
            int blocked = intOf(raw.chutewiseBlockage(), chute);
            if (blocked > 0) {
                blockage.put(chute, blocked);
            }
            Double seconds = orEmpty(raw.chutewiseTotalBlockageTime()).get(chute);
            if (seconds != null && seconds > 0) {
                blockageTime.put(chute, seconds);
            }
        }

        // ---- chute types ----
        Map<String, Integer> typeThroughput = new TreeMap<>();
        Map<String, Integer> typeBlockage = new TreeMap<>();
        Set<String> types = new TreeSet<>();
        types.addAll(orEmpty(raw.chutetypeThroughput()).keySet());
        types.addAll(orEmpty(raw.chutetypeBlockage()).keySet());
        for (String type : types) {
            if (type == null || !CHUTE_TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) {
                continue;   // unknown chute type: skipped, same as the old app
            }
            String label = titleCase(type.trim());
            typeThroughput.merge(label, intOf(raw.chutetypeThroughput(), type), Integer::sum);
            int blocked = intOf(raw.chutetypeBlockage(), type);
            if (blocked > 0) {
                typeBlockage.merge(label, blocked, Integer::sum);
            }
        }

        // ---- resources ----
        Map<String, ResourceMetrics> resources = new TreeMap<>();
        orEmpty(raw.resourceMetrics()).forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            ResourceMetrics metrics = value == null ? new ResourceMetrics(null, null, null, null) : value;
            Integer zoneId = null;
            Integer shiftId = metrics.shiftId();
            Matcher matcher = RESOURCE_KEY.matcher(key);
            if (matcher.matches()) {
                zoneId = Integer.valueOf(matcher.group(1));
                shiftId = Integer.valueOf(matcher.group(2));
            }
            resources.put(key, new ResourceMetrics(zoneId, shiftId, zeroIfNull(metrics.parcelsHandled()),
                    zeroIfNull(metrics.weightHandled())));
        });

        // ---- infeeds ----
        Map<String, InfeedMetrics> infeeds = new TreeMap<>();
        orEmpty(raw.infeedMetrics()).forEach((name, value) -> {
            if (name != null && !name.isBlank()) {
                infeeds.put(name, new InfeedMetrics(value == null ? 0 : zeroIfNull(value.induction())));
            }
        });

        // ---- parcels and conveyor ----
        ParcelMetrics parcels = raw.parcelMetrics() == null ? null : new ParcelMetrics(raw.experimentId(),
                raw.parcelMetrics().parcelScanned(), raw.parcelMetrics().parcelThroughput(),
                raw.parcelMetrics().parcelBlockage(), raw.parcelMetrics().parcelReject(),
                raw.parcelMetrics().parcelRejectRefeed(), time);
        ConveyorDetails conveyor = null;
        if (raw.conveyorDetails() != null) {
            int slots = zeroIfNull(raw.conveyorDetails().occupiedSlots());
            conveyor = new ConveyorDetails(slots, slots / CONVEYOR_SLOTS);
        }

        return new SimulationKpi(raw.experimentId(), time, throughput, blockage, destinated, diverted,
                cageCount, cageFillCount, cageFillVolume, filledHistory, typeThroughput, typeBlockage,
                blockageTime, blockageState, resources, infeeds, parcels, conveyor);
    }

    // ---------------- small helpers ----------------

    private static <V> Map<String, V> orEmpty(Map<String, V> map) {
        return map == null ? Map.of() : map;
    }

    private static int intOf(Map<String, Integer> map, String key) {
        if (map == null) {
            return 0;
        }
        Integer value = map.get(key);
        return value == null ? 0 : value;
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static <T> void putIfNotEmpty(Map<String, List<T>> target, String key, Collection<T> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, new ArrayList<>(values));
        }
    }

    /** "BOOM" -> "Boom", "spiral" -> "Spiral" */
    static String titleCase(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
