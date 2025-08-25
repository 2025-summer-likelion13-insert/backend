package com.example.insert.controller;

import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.service.PerformFixedService;
import com.example.insert.service.PerformQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performs/fixed")
@RequiredArgsConstructor
@Slf4j
public class PerformFixedController {

    private final PerformFixedService fixedService;   // A 플로우(top10) 소스
    private final PerformQueryService queryService;   // upcoming/search/detail

    /** TOP10: A 플로우(후보40→종료제외→10보장) 결과만 노출 */
    /** TOP10: A 플로우(후보40→종료제외→10보장) 결과 노출 + DB 자동 보강 */
    @GetMapping("/top10")
    public List<Map<String, String>> top10() {
        try {
            // ① 먼저 DB 보강 (중복 안전: upsert + saveAndFlush)
            //    excludeEnded=false 로 고정 — 이미 top10Fixed()에서 종료 제외하므로 이중 필터 방지
            int imported = fixedService.importTop10ExactToDb(false);
            log.info("[/fixed/top10] auto-import done: {}", imported);

            // ② 그 다음 최신 TOP10 목록을 다시 계산해서 반환
            var list = fixedService.top10Fixed();
            return list.stream()
                    .map(m -> Map.of(
                            "rnum",   String.valueOf(m.getOrDefault("rnum", "")),
                            "mt20id", String.valueOf(m.getOrDefault("mt20id","")),
                            "prfnm",  String.valueOf(m.getOrDefault("prfnm","")),
                            "poster", String.valueOf(m.getOrDefault("poster",""))
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("[/fixed/top10] {}", e.getMessage(), e);
            return List.of();  // 실패해도 200 + []
        }
    }


    /** TOP10 딱 10개를 DB에 즉시 삽입(꼼수). excludeEnded=true면 공연종료(03)·종료일 지난 건 제외 */
    // import 붙임: org.springframework.web.bind.annotation.RequestMethod
    @RequestMapping(value = "/top10/import-exact", method = { RequestMethod.GET, RequestMethod.POST })
    public Map<String, Object> importTop10Exact(
            @RequestParam(name = "excludeEnded", defaultValue = "false") boolean excludeEnded
    ) {
        try {
            int n = fixedService.importTop10ExactToDb(excludeEnded);
            return Map.of("imported", n, "excludeEnded", excludeEnded);
        } catch (Exception e) {
            log.error("[/fixed/top10/import-exact] {}", e.getMessage(), e);
            return Map.of("imported", 0, "excludeEnded", excludeEnded, "error", e.getMessage());
        }
    }


    /** 다가오는 공연 (예외 시 200 + []) */
    @GetMapping("/upcoming")
    public List<PerformCardDto> upcoming(@RequestParam(required = false, defaultValue = "31") int days) {
        try {
            return queryService.getUpcoming(days <= 0 ? 31 : days);
        } catch (Exception e) {
            log.error("[/fixed/upcoming] {}", e.getMessage(), e);
            return List.of();
        }
    }

    /** 검색: 무페이징 (예외 시 200 + []) */
    @GetMapping("/search")
    public List<PerformCardDto> search(@RequestParam String q,
                                       @RequestParam(required = false) Integer limit) {
        try {
            return queryService.searchByTitleNoPaging(q, limit);
        } catch (Exception e) {
            log.error("[/fixed/search] {}", e.getMessage(), e);
            return List.of();
        }
    }

    /** 상세: DB 기반(by-external) */
    @GetMapping("/detail/{externalId}")
    public PerformDetailDto detail(@PathVariable String externalId) {
        return queryService.getDetailByExternalId(externalId.trim());
    }
}
