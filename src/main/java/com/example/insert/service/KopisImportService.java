package com.example.insert.service;

import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KopisImportService {

    private final KopisClient kopis;
    private final PerformRepository repo;

    /** 목록(필터) → 상세(sty) → performs 테이블에 "한 번만 INSERT" */
    public int importOnce(LocalDate st, LocalDate ed, Integer signgucode, String prfstate) throws Exception {
        int page = 1, rows = 100, saved = 0;

        while (true) {
            List<Map<String,String>> list = kopis.list(st, ed, signgucode, prfstate, page, rows);
            if (list.isEmpty()) break;

            for (var m : list) {
                String externalId = m.getOrDefault("mt20id", "");
                if (externalId.isBlank() || repo.findByExternalId(externalId).isPresent()) {
                    continue; // 이미 적재됨
                }

                String synopsis = kopis.synopsis(externalId).orElse("");

                Perform p = Perform.builder()
                        .externalId(externalId)
                        .title(m.getOrDefault("prfnm", ""))
                        .startDate(KopisClient.parseKopisDate(m.get("prfpdfrom")))
                        .endDate(KopisClient.parseKopisDate(m.get("prfpdto")))
                        .venueName(m.getOrDefault("fcltynm", ""))
                        .posterUrl(m.getOrDefault("poster", ""))
                        .synopsis(synopsis)
                        .genre(m.getOrDefault("genrenm", ""))
                        .area(m.getOrDefault("area", ""))
                        .state(m.getOrDefault("prfstate", ""))
                        .isAd(false)
                        .build();

                repo.save(p);
                saved++;
                // 필요 시 Thread.sleep(200);
            }
            page++;
        }
        return saved;
    }
}
