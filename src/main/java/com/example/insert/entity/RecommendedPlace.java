package com.example.insert.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommended_places")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedPlace {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlaceCategory category;
    
    @Column(name = "address", nullable = false)
    private String address;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Column(name = "rating")
    private Double rating;
    
    @Column(name = "price_range")
    private String priceRange;
    
    @Column(name = "opening_hours")
    private String openingHours;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "website")
    private String website;
    
    @Column(name = "ai_reason", columnDefinition = "TEXT")
    private String aiReason;
    
    @Column(name = "distance_from_venue")
    private Double distanceFromVenue;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum PlaceCategory {
        ACTIVITY,   // 엑티비티
        DINING,     // 식사 장소
        CAFE        // 카페
    }
}
