package com.example.experiment_service.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the text "1,2,3" or "[1, 2, 3]" into the list [1, 2, 3].
 *
 * strict = true  (used when SAVING): a bad value like "1,a" throws an error, so bad data never enters the database.
 * strict = false (used when READING): a bad value is skipped, so one old bad row cannot break the whole screen.
 *
 * Only static methods, no saved state: easy to unit-test.
 */
public final class ChuteIdParser {

    private ChuteIdParser() {
        // not meant to be created
    }

    public static List<Integer> parse(String chutes, boolean strict) {
        List<Integer> result = new ArrayList<>();
        if (chutes == null) {
            return result;
        }
        String cleaned = chutes.replaceAll("[\\[\\]]", "").trim();
        if (cleaned.isEmpty()) {
            return result;
        }
        for (String token : cleaned.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(value));
            } catch (NumberFormatException notANumber) {
                if (strict) {
                    throw new IllegalArgumentException("Chute id is not a number: " + value);
                }
                // not strict: skip this value
            }
        }
        return result;
    }
}