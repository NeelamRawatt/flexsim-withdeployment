package com.example.experiment_service.repository;

import com.example.experiment_service.entity.ChuteMaster;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChuteMasterRepository extends JpaRepository<ChuteMaster, Long> {

    Optional<ChuteMaster> findByChuteId(Integer chuteId);
}