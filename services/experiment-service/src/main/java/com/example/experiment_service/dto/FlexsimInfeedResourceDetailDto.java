package com.example.experiment_service.dto;

/** One infeed row that we SEND BACK (read). Same fields as the old app. */
public record FlexsimInfeedResourceDetailDto(
        Integer shiftId,
        String tc,
        Integer noOfResources) {
}