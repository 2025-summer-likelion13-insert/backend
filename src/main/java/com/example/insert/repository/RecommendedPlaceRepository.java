package com.example.insert.repository;

import com.example.insert.entity.RecommendedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendedPlaceRepository extends JpaRepository<RecommendedPlace, Long> {
    
    /**
     * 장소명으로 장소 검색
     */
    RecommendedPlace findByName(String name);
    
    /**
     * 주소로 장소 검색
     */
    RecommendedPlace findByAddress(String address);
    
    /**
     * 좌표로 장소 검색 (위도, 경도)
     */
    RecommendedPlace findByLatitudeAndLongitude(Double latitude, Double longitude);
}
