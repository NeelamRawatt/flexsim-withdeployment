package com.example.experiment_service.dto;

import java.util.List;

/**
 * One zone row that we SEND BACK (read). chuteIds is the text "1,2,3" turned into a list of numbers.
 * The name "Flexsim..." is kept from the old app (the simulation reads this shape).
 */
public record FlexsimZoneResourceDetailDto(
        Integer shiftId,
        String zoneId,
        Integer resourceId,
        String chutes,
        List<Integer> chuteIds) {
}