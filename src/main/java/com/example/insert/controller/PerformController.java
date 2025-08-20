package com.example.insert.controller;

import com.example.insert.config.PerformIngestProperties;
import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import com.example.insert.service.KopisImportService;
import com.example.insert.service.PerformQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Performs API
 * - TOP10: A 플로우(후보40→종료제외→10보장)로 일원화
 * - Import: B 플로우(증분 인제스트 + 정리) 오케스트레이션
 */
@RestController
@RequestMapping("/api/performs")
@RequiredArgsConstructor
@Slf4j
public class PerformController {

    private final KopisImportService importService;
    private final PerformQueryService queryService;
    private final PerformRepository performRepository;
    private final PerformIngestProperties props;

    /* ============================= 조회 ============================= */

    @GetMapping("/{id}")
    public ResponseEntity<PerformDetailDto> getDetail(@PathVariable String id) {
        return findByIdOrExternalId(id)
                .map(PerformDetailDto::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<Perform> findByIdOrExternalId(String id) {
        if (id.matches("\\d+")) {
            return performRepository.findById(Long.parseLong(id));
        }
        return performRepository.findByExternalId(id);
    }

    /** 광고 공연 카드 */
    @GetMapping("/ads")
    public List<PerformCardDto> ads() {
        return queryService.getAds();
    }

    /** 다가오는 공연 */
    @GetMapping("/upcoming")
    public List<PerformCardDto> upcoming(@RequestParam(defaultValue = "31") int days) {
        return queryService.getUpcoming(days);
    }

    /** 상세 (KOPIS ID 기반) */
    @GetMapping("/by-external/{externalId}")
    public PerformDetailDto detailByExternal(@PathVariable String externalId) {
        return queryService.getDetailByExternalId(externalId);
    }

    /**
     * TOP10 (인천 28, st=curr-30d, ed=curr, date 미전송, 종료 제외, 10개 보장)
     * - 외부 파라미터 없이 고정 규칙으로 반환 (A 플로우)
     */
    @GetMapping("/top10")
    public List<Map<String,String>> top10() {
        // queryService 내부에서 fixedService.top10Fixed() 호출로 일원화됨
        return queryService.getTop10(null, null, null);
    }

    /* ============================= 관리/인제스트 ============================= */

    /**
     * (관리) 증분 인제스트 1회 실행 (B 플로우)
     * - 기간: currDate ~ endDate (프로퍼티)
     * - 지역: props.regions (signgucodesub 4자리)
     * - 상태: props.states ('01','02')
     * - rows: 기본 props.rows (요청으로 override 가능)
     *
     * 반환 예: "upserted=123 deleted=7"
     */
    @PostMapping("/admin/import")
    public String importAll(
            @RequestParam(required = false) Integer rows,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate overrideCurr,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate overrideEnd
    ) throws Exception {
        LocalDate currDate = (overrideCurr != null) ? overrideCurr
                : (props.getCurrDate() != null ? props.getCurrDate() : LocalDate.now());
        LocalDate endDate = (overrideEnd != null) ? overrideEnd
                : (props.getEndDate() != null ? props.getEndDate() : currDate);

        int pageRows = (rows != null && rows > 0) ? rows
                : (props.getRows() != null && props.getRows() > 0 ? props.getRows() : 40);

        List<Integer> regions = (props.getRegions() != null) ? props.getRegions() : List.of();
        List<String> states = (props.getStates() != null) ? props.getStates() : List.of("01", "02");

        if (regions.isEmpty() || states.isEmpty()) {
            return "upserted=0 deleted=0 (regions/states empty)";
        }

        Set<String> seen = new LinkedHashSet<>();
        int upserted = 0;

        for (Integer region : regions) {
            for (String state : states) {
                upserted += importService.importOnce(currDate, endDate, region, state, pageRows, seen);
            }
        }

        int deleted = importService.cleanupAfterRun(currDate, currDate, endDate, seen);
        log.info("[admin/import] curr={} ~ end={}, rows={}, regions={}, states={}, upserted={}, deleted={}",
                currDate, endDate, pageRows, regions, states, upserted, deleted);

        return "upserted=" + upserted + " deleted=" + deleted;
    }
}
