package com.example.insert.repository;

import com.example.insert.entity.Perform;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PerformRepository extends JpaRepository<Perform, Long> {

    Optional<Perform> findByExternalId(String externalId);

    List<Perform> findByIsAdTrueOrderByStartDateAsc();

    List<Perform> findByStartDateBetweenOrderByStartDateAsc(LocalDate from, LocalDate to);

    // 제목 부분 일치(대소문자 무시) + 정렬만
    List<Perform> findByTitleContainingIgnoreCase(String q, Sort sort);

    // ====== 편의 조회 (A/B 플로우 보강용) ======

    /** DB 백필 후보: 인천(구·군 4자리), 상태 01/02, 기간 내 */
    @Query("""
        select p from Perform p
        where p.state in :states
          and p.sigunguCode in :sigunguCodes
          and p.startDate >= :from and p.endDate <= :to
        order by case when p.state='02' then 0 else 1 end, p.startDate asc, p.createdAt desc
        """)
    List<Perform> findActiveInRegionsWithinPeriod(
            Collection<String> states,
            Collection<String> sigunguCodes,
            LocalDate from,
            LocalDate to
    );

    /** 외부 ID 다건 조회(중복 제거/존재 여부 체크 등에 유용) */
    List<Perform> findByExternalIdIn(Collection<String> externalIds);

    // ====== 정리(Cleanup) 보조 메서드(선택) ======

    /** 상태 코드로 일괄 삭제 (예: '03') */
    @Modifying
    @Query("delete from Perform p where p.state = :state")
    int deleteByStateHard(String state);

    /** 종료일 기준 일괄 삭제 */
    @Modifying
    @Query("delete from Perform p where p.endDate < :before")
    int deleteEndedBefore(LocalDate before);
}
