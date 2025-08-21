package com.example.insert.repository;

import com.example.insert.entity.PointLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {
    boolean existsByUserIdAndTypeAndSourceId(Long userId, String type, Long sourceId);

    // ▼ 내역 조회용 (최신순)
    Page<PointLedger> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<PointLedger> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);
}
