package com.example.experiment_service.dto;

/** One infeed row that the client SENDS (save). */
public record InfeedResourceDetailDto(
        Long experimentId,
        Integer shiftId,
        String zoneId,
        String infeed,
        String tc,
        Integer noOfResources,
        Boolean active) {
}