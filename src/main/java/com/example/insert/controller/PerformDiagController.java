package com.example.insert.controller;

import com.example.insert.config.PerformIngestProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/performs/admin/diag")
@RequiredArgsConstructor
@Slf4j
public class PerformDiagController {

    @PersistenceContext private final EntityManager em;
    private final PerformIngestProperties props;

    @GetMapping
    public Map<String, Object> summary() {
        LocalDate curr = props.getCurrDate() != null ? props.getCurrDate() : LocalDate.now();
        LocalDate end  = props.getEndDate()  != null ? props.getEndDate()  : curr;
        List<String> states = props.getStates() != null ? props.getStates() : List.of("01","02");
        List<Integer> regions = props.getRegions() != null ? props.getRegions() : List.of(2826,2818,2817);

        Map<String,Object> out = new LinkedHashMap<>();

        // 전체
        long total = ((Number) em.createQuery("select count(p) from Perform p").getSingleResult()).longValue();
        out.put("total", total);

        // 상태별
        List<Object[]> byState = em.createQuery("select p.state, count(p) from Perform p group by p.state", Object[].class)
                .getResultList();
        out.put("byState", toMap(byState));

        // 구군코드별
        List<Object[]> bySigungu = em.createQuery("select p.sigunguCode, count(p) from Perform p group by p.sigunguCode order by count(p) desc", Object[].class)
                .setMaxResults(20).getResultList();
        out.put("bySigungu", toMap(bySigungu));

        // 우리가 기대하는 조건(상태 01/02 + 기간 + 지역)
        List<String> sigunguStr = regions.stream().map(String::valueOf).toList();
        Long inSpec = ((Number) em.createQuery("""
            select count(p) from Perform p
            where p.state in :states
              and p.startDate >= :st and p.endDate <= :ed
              and p.sigunguCode in :sg
        """).setParameter("states", states)
                .setParameter("st", curr)
                .setParameter("ed", end)
                .setParameter("sg", sigunguStr)
                .getSingleResult()).longValue();
        out.put("inSpecCount", inSpec);

        // 최근 적재 10건(행동 확인)
        List<Object[]> recent = em.createQuery("""
            select p.externalId, p.title, p.startDate, p.endDate, p.state, p.sigunguCode, p.createdAt
            from Perform p order by p.createdAt desc
        """, Object[].class).setMaxResults(10).getResultList();
        out.put("recent10", recent.stream().map(r -> Map.of(
                "externalId", r[0], "title", r[1], "startDate", r[2], "endDate", r[3],
                "state", r[4], "sigungu", r[5], "createdAt", r[6]
        )).toList());

        // 설정값도 같이 반환(오해 방지)
        out.put("config", Map.of(
                "currDate", curr, "endDate", end,
                "states", states, "regions", regions
        ));

        return out;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String k = Objects.toString(r[0], "NULL");
            Long v = ((Number) r[1]).longValue();
            m.put(k, v);
        }
        return m;
    }
}
