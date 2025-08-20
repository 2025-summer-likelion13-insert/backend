package com.example.insert.controller;

import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import com.example.insert.service.KopisImportService;
import com.example.insert.service.PerformQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/performs")
@RequiredArgsConstructor
public class PerformController {

    private final KopisImportService importService;
    private final PerformQueryService queryService;

    private final PerformRepository performRepository;

    @GetMapping("/{id}")
    public ResponseEntity<PerformDetailDto> getDetail(@PathVariable String id) {
        return findByIdOrExternalId(id)
                .map(PerformDetailDto::of)   // ← 너희 프로젝트의 DTO 변환 메서드 사용
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<Perform> findByIdOrExternalId(String id) {
        if (id.matches("\\d+")) {
            return performRepository.findById(Long.parseLong(id));
        }
        return performRepository.findByExternalId(id);
    }

    /** (관리) KOPIS → DB 원샷 적재 */
    @PostMapping("/admin/import")
    public String importOnce(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate stdate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eddate,
            @RequestParam(required = false) Integer signgucode,
            @RequestParam(required = false) String prfstate
    ) throws Exception {
        int saved = importService.importOnce(stdate, eddate, signgucode, prfstate);
        return "saved=" + saved;
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

    /** TOP10 (KOPIS 실시간/캐시) */
    @GetMapping("/top10")
    public List<Map<String,String>> top10(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate stdate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eddate,
            @RequestParam(required = false) String area
    ) throws Exception {
        return queryService.getTop10(stdate, eddate, area);
    }
}
