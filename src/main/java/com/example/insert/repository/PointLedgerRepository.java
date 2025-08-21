package com.example.insert.repository;

import com.example.insert.entity.PointLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {
    boolean existsByUserIdAndTypeAndSourceId(Long userId, String type, Long sourceId);
}
