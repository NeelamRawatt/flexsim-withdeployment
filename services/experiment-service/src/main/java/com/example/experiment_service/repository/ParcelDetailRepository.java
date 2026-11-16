package com.example.experiment_service.repository;

import com.example.experiment_service.entity.ParcelDetail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads the real parcels (parcel_details). Same three questions as the old app. */
public interface ParcelDetailRepository extends JpaRepository<ParcelDetail, Long> {

    /**
     * FlexSim's parcels: one operational day, between start and end, in arrival order.
     * "id" as the second sort key: parcels with the SAME second always come in the same order,
     * so the scaling (more / fewer parcels) gives the same result every time.
     */
    List<ParcelDetail> findByParcelEdtDateAndParcelTimeSlotBetweenOrderByParcelTimeSlotAscIdAsc(
            LocalDate parcelEdtDate, LocalDateTime start, LocalDateTime end);

    /** The screen: how many parcels between start and end. */
    long countByParcelTimeSlotBetween(LocalDateTime start, LocalDateTime end);

    /** The screen: the sort names of all parcels between start and end (counted in the service). */
    @Query("select p.sortName from ParcelDetail p where p.parcelTimeSlot between :start and :end")
    List<String> findSortNamesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
