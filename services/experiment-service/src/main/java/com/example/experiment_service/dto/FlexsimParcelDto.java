package com.example.experiment_service.dto;

import java.time.LocalDateTime;

/**
 * One parcel as FlexSim reads it (GET /api/parcels/getParcels/{id}). SAME field names as the old app,
 * because the FlexSim model reads these names.
 *
 * flexsimTimeSlot = seconds after the start of the simulation when the parcel arrives
 *                   (+180 s: FlexSim needs 3 minutes to warm up, same as the old app).
 */
public record FlexsimParcelDto(
        LocalDateTime parcelTimeSlot,
        String parcelId,
        String parcelInfeedName,
        String sortName,
        String rejectCode,
        Double parcelWeight,
        Double parcelWidth,
        Double parcelLength,
        Double parcelHeight,
        Double parcelVolume,
        Integer flexsimTimeSlot) {

    /** A copy with another parcelId (used when the experiment asks for MORE parcels than the real day had). */
    public FlexsimParcelDto withParcelId(String newParcelId) {
        return new FlexsimParcelDto(parcelTimeSlot, newParcelId, parcelInfeedName, sortName, rejectCode,
                parcelWeight, parcelWidth, parcelLength, parcelHeight, parcelVolume, flexsimTimeSlot);
    }
}
