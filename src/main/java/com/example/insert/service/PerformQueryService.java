package com.example.insert.service;

import com.example.insert.dto.PerformCardDto;
import com.example.insert.dto.PerformDetailDto;
import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
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
    @Cacheable(cacheNames = "top10", key = "#st+'_'+#ed+'_'+#area")
    public List<Map<String,String>> getTop10(LocalDate st, LocalDate ed, String area) throws Exception {
        return kopis.boxOffice(st, ed, area);
    }
}
