package com.example.insert.controller;

import com.example.insert.config.PerformIngestProperties;
import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.service.PerformFixedService;
import com.example.insert.service.PerformQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performs/fixed")
@RequiredArgsConstructor
public class PerformFixedController {

    private final PerformFixedService fixedService;     // ✅ 이거 하나만 남기기
    private final PerformQueryService queryService;     // (upcoming/search에서 쓰이면 유지)
    private final PerformIngestProperties props;        // (다른 곳에서 쓰면 유지)

    /** TOP10: 안전 경로(fixedService)로 복귀 */
    @GetMapping("/top10")
    public List<Map<String,String>> top10() {
        try {
            var list = fixedService.top10Fixed();       // ✅ 여기만 변경
            return list.stream()
                    .map(m -> Map.of(
                            "mt20id", String.valueOf(m.getOrDefault("mt20id","")),
                            "prfnm",  String.valueOf(m.getOrDefault("prfnm","")),
                            "poster", String.valueOf(m.getOrDefault("poster",""))
                    ))
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();  // 실패해도 200 + []
        }
    }


    // [B] 다가오는 공연 (예외 방탄: 200+[] 반환)
    @GetMapping("/upcoming")
    public List<PerformCardDto> upcoming(
            @RequestParam(required = false, defaultValue = "31") int days
    ) {
        try {
            if (days <= 0) days = 31;
            return queryService.getUpcoming(days);
        } catch (Exception e) {
            System.err.println("[/fixed/upcoming] ERROR: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    // 검색: 무페이징, 예외시 200 + []
    @GetMapping("/search")
    public List<PerformCardDto> search(@RequestParam String q,
                                       @RequestParam(required = false) Integer limit) {
        try {
            return queryService.searchByTitleNoPaging(q, limit);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // 상세: DB만 본다 (by-external)
    @GetMapping("/detail/{externalId}")
    public PerformDetailDto detail(@PathVariable String externalId) {
        return queryService.getDetailByExternalId(externalId.trim());
    }


}
