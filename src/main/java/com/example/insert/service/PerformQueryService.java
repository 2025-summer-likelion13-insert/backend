package com.example.insert.service;

import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformQueryService {

    private final PerformRepository repo;
    private final KopisClient kopis;

    // ✅ A 플로우(후보 40 → 종료 제외 → 10개 보장)를 구현한 서비스로 위임
    private final PerformFixedService fixedService;

    public List<PerformCardDto> getAds() {
        return repo.findByIsAdTrueOrderByStartDateAsc()
                .stream().map(PerformCardDto::of).toList();
    }

    public List<PerformCardDto> getUpcoming(int days) {
        LocalDate now = LocalDate.now();
        return repo.findByStartDateBetweenOrderByStartDateAsc(now, now.plusDays(days))
                .stream().map(PerformCardDto::of).toList();
    }

    public PerformDetailDto getDetailByExternalId(String externalId) {
        Perform p = repo.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + externalId));
        return PerformDetailDto.of(p);
    }

    /**
     * TOP10 — 캐시 가능 엔드포인트
     * 기존 시그니처(st, ed, area)는 호환성 때문에 유지하지만,
     * 내부적으로는 A 플로우를 따르는 fixedService.top10Fixed()로 일원화한다.
     */
    @Cacheable(
            cacheNames = "top10",
            key = "'A-flow-fixed-area28'"
    )
    public List<Map<String,String>> getTop10(LocalDate st, LocalDate ed, String area) {
        try {
            var list = fixedService.top10Fixed(); // ✅ 인천(28), st=curr-30, ed=curr, date 미전송, 종료 제외, 10개 보장
            return list.stream()
                    .map(m -> Map.of(
                            "rnum",   String.valueOf(m.getOrDefault("rnum",   "")),
                            "mt20id", String.valueOf(m.getOrDefault("mt20id", "")),
                            "prfnm",  String.valueOf(m.getOrDefault("prfnm",  "")),
                            "poster", String.valueOf(m.getOrDefault("poster", ""))
                    ))
                    .toList();
        } catch (Exception ex) {
            log.error("[/fixed/top10] fixedService.top10Fixed() failed: {}", ex.getMessage(), ex);
            return List.of(); // 프론트 안 깨지게 방어
        }
    }

    // 검색(무페이징)
    public List<PerformCardDto> searchByTitleNoPaging(String raw, Integer limit) {
        if (raw == null || raw.isBlank()) return List.of(); // 500 방지
        String[] tokens = raw.trim().split("\\s+");

        List<Perform> list = repo.findByTitleContainingIgnoreCase(
                tokens[0], Sort.by(Sort.Direction.DESC, "startDate"));

        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i].toLowerCase();
            list = list.stream()
                    .filter(p -> p.getTitle()!=null && p.getTitle().toLowerCase().contains(t))
                    .toList();
        }

        int cap = (limit == null || limit <= 0) ? 200 : Math.min(limit, 1000);
        return list.stream().limit(cap).map(PerformCardDto::of).toList();
    }
}
