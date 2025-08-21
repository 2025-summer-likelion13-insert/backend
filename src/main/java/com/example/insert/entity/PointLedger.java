package com.example.insert.entity;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_ledger",
        uniqueConstraints = @UniqueConstraint(name="uq_point_once",
                columnNames = {"user_id","type","source_id"}))
@Getter @Setter @NoArgsConstructor
public class PointLedger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(nullable=false, length=20)
    private String type; // "REVIEW"

    @Column(name="source_id", nullable=false)
    private Long sourceId; // reviewId

    @Column(nullable=false)
    private int delta; // +1

    @Column(nullable=false, length=200)
    private String reason; // "리뷰 작성 적립"

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
