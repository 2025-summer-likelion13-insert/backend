package com.example.insert.service;

import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;   // ← 추가
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j // ← 추가
public class PerformQueryService {

    private final PerformRepository repo;
    private final KopisClient kopis;

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

    /** TOP10 — 런타임 호출(필요 시 캐시) */
    @Cacheable(
            cacheNames = "top10",
            key = "T(String).valueOf(#st)+'_'+T(String).valueOf(#ed)+'_'+T(String).valueOf(#area)" // ← null-safe
    )
    public List<Map<String,String>> getTop10(LocalDate st, LocalDate ed, String area) {
        // 1) 날짜 기본값
        LocalDate s = (st != null) ? st : LocalDate.now().minusDays(7);
        LocalDate e = (ed != null) ? ed : LocalDate.now();
        if (e.isBefore(s)) { // 뒤집힘 방지
            LocalDate tmp = s;
            s = e;
            e = tmp;
        }

        // 2) area 정규화: "", "all", 공백 → null
        String a = (area == null || area.isBlank() || "all".equalsIgnoreCase(area)) ? null : area.trim();

        try {
            return kopis.boxOffice(s, e, a);
        } catch (Exception ex) {
            log.error("[/fixed/top10] kopis.boxOffice failed (s={}, e={}, area={}): {}",
                    s, e, a, ex.getMessage(), ex);
            // 프론트 안깨지게 빈 리스트 리턴
            return List.of();
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
