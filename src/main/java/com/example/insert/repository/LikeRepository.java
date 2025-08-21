package com.example.insert.repository;

import com.example.insert.entity.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndPerformId(Long userId, Long performId);

    boolean existsByUserIdAndPerformId(Long userId, Long performId);

    long countByPerformId(Long performId);

    /** 내 찜 목록 (perform 즉시 로드 + 페이징 정상 작동) */
    @EntityGraph(attributePaths = "perform")
    Page<Like> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 주어진 externalId들 중 내가 찜한 것의 externalId 목록 */
    @Query("""
        select p.externalId
        from Like l join l.perform p
        where l.user.id = :userId and p.externalId in :externalIds
    """)
    List<String> findLikedExternalIds(@Param("userId") Long userId,
                                      @Param("externalIds") Collection<String> externalIds);
}
