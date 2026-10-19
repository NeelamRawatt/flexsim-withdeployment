package com.example.experiment_service.dto;

/**
 * One zone row that the client SENDS (save). A "record" is a short way to write an unchangeable data box.
 * shiftId can be sent as a number (1) or as text ("1"): Jackson converts it.
 * chutes is text like "1,2,3" or "[1,2,3]".
 */
public record ZoneResourceDetailDto(
        Long experimentId,
        Integer shiftId,
        String zoneId,
        Integer resourceId,
        String chutes) {
}