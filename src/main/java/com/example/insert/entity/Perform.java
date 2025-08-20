package com.example.insert.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "performs",
        uniqueConstraints = @UniqueConstraint(name = "uk_performs_external_id", columnNames = "external_id")
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Perform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                   // 내부 PK (자동증가)

    @Column(name = "external_id", length = 32, nullable = false)
    private String externalId;                         // KOPIS mt20id

    @Column(name = "title", length = 255, nullable = false)
    private String title;                              // prfnm

    @Column(name = "start_date")
    private LocalDate startDate;                       // prfpdfrom

    @Column(name = "end_date")
    private LocalDate endDate;                         // prfpdto

    @Column(name = "venue_name", length = 255)
    private String venueName;                          // fcltynm

    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;                          // poster

    @Column(name = "synopsis", columnDefinition = "MEDIUMTEXT")
    private String synopsis;                           // sty

    @Column(name = "genre", length = 64)
    private String genre;                              // genrenm (옵션)

    @Column(name = "area", length = 64)
    private String area;                               // area (옵션)

    @Column(name = "state", length = 32)
    private String state;                              // prfstate (원문 코드/문자)

    @Column(name = "is_ad", nullable = false)
    private boolean isAd = false;                      // 광고 여부

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
