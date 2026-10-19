package com.example.experiment_service.service;

/**
 * The business rules for experiment values: allowed ranges and defaults.
 * They are copied from the old app, so results stay the same.
 *
 * This class has only "pure" methods: same input gives the same output, and nothing is saved.
 * That makes them very easy to unit-test (we do that in a later step).
 * "static" = we call them without making an object: ExperimentRules.sorterFullPercent(150).
 */
public final class ExperimentRules {

    private static final int DEFAULT_FLEXIBLE_RESOURCES = 1;
    private static final int DEFAULT_SORTER_FULL_PERCENT = 90;
    private static final int MIN_CAGE_OCCUPANCY_FLOOR = 50;
    private static final int MAX_CAGE_OCCUPANCY_CEILING = 90;
    private static final int DEFAULT_MIN_CAGE_OCCUPANCY = 65;
    private static final int DEFAULT_MAX_CAGE_OCCUPANCY = 75;

    private ExperimentRules() {
        // not meant to be created
    }

    /** Only 0 or 1. Missing means 1. */
    public static Integer flexibleResources(Integer value) {
        if (value == null) {
            return DEFAULT_FLEXIBLE_RESOURCES;
        }
        return value == 0 ? 0 : 1;
    }

    /** Only 0 or 1. Always 0 when flexibleResources is 0. Missing means 1. */
    public static Integer phoneBoomChute(Integer value, Integer flexibleResources) {
        if (flexibleResources != null && flexibleResources == 0) {
            return 0;
        }
        if (value == null) {
            return 1;
        }
        return value == 0 ? 0 : 1;
    }

    /** Only 0 or 1. Missing means 0. */
    public static Integer dropChuteFlexible(Integer value) {
        if (value == null) {
            return 0;
        }
        return value == 0 ? 0 : 1;
    }

    /** Only 0 or 1. Missing means 0. */
    public static Integer flexibleUnloadResources(Integer value) {
        return value == null || value == 0 ? 0 : 1;
    }

    /** 0 to 100. Missing means 90. */
    public static Integer sorterFullPercent(Integer value) {
        if (value == null) {
            return DEFAULT_SORTER_FULL_PERCENT;
        }
        return Math.min(100, Math.max(0, value));
    }

    /** 50 to 90. Missing means 65. */
    public static Integer minimumCageOccupancy(Integer value) {
        if (value == null) {
            return DEFAULT_MIN_CAGE_OCCUPANCY;
        }
        return Math.min(MAX_CAGE_OCCUPANCY_CEILING, Math.max(MIN_CAGE_OCCUPANCY_FLOOR, value));
    }

    /** 50 to 90, and never below the minimum. Missing means 75. */
    public static Integer maximumCageOccupancy(Integer value, Integer minimum) {
        int sanitized = value == null
                ? DEFAULT_MAX_CAGE_OCCUPANCY
                : Math.min(MAX_CAGE_OCCUPANCY_CEILING, Math.max(MIN_CAGE_OCCUPANCY_FLOOR, value));
        return Math.max(sanitized, minimum);
    }
}