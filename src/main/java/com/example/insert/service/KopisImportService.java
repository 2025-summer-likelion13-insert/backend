package com.example.insert.service;

import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class KopisImportService {

    // 클래스 내부 맨 위에
    private static final Logger log = LoggerFactory.getLogger(KopisClient.class);

    private final KopisClient kopis;
    private final PerformRepository repo;

    /** 허용 상태(01=공연예정, 02=공연중) */
    private static final Set<String> ALLOWED_STATES = Set.of("01", "02");

    /**
     * [증분 인제스트 1회] — 페이지 끝까지 돌며 upsert, 본 externalId는 seen에 기록
     * - 지역: signgucodesub (구·군 4자리)
     * - 상태: prfstate (01/02)
     * - 기간: [st, ed]
     * - rows는 파라미터로 받는 값 사용 (외부에서 20~100 설정 권장)
     */
    @Transactional
    public int importOnce(LocalDate st, LocalDate ed, Integer signgucodesub, String prfstate,
                          int rows, Set<String> seen) throws Exception {
        int page = 1, savedOrUpdated = 0;

        while (true) {
            List<Map<String, String>> list = kopis.list(st, ed, signgucodesub, prfstate, page, rows);
            log.info("[INGEST] region={} state={} page={} rows={} fetched={}",
                    signgucodesub, prfstate, page, rows, (list==null?0:list.size())); // ✅ 핵심 로그

            if (list == null || list.isEmpty()) break;

            for (var m : list) {
                String externalId = m.getOrDefault("mt20id", "").trim();
                if (externalId.isEmpty()) continue;

                seen.add(externalId); // 이번 런에서 관측됨

                // 응답의 prfstate가 "01/02/03" 또는 "공연예정/공연중/공연완료" 어떤 형태든 01/02/03으로 정규화
                String stateRaw = m.getOrDefault("prfstate", "").trim();
                String stateNorm = switch (stateRaw) {
                    case "01", "공연예정" -> "01";
                    case "02", "공연중"   -> "02";
                    case "03", "공연완료" -> "03";
                    default -> stateRaw; // 알 수 없으면 원본 그대로
                };

                // 01/02만 통과
                if (!"01".equals(stateNorm) && !"02".equals(stateNorm)) {
                    continue;
                }


                LocalDate startDate = KopisClient.parseKopisDate(m.get("prfpdfrom"));
                LocalDate endDate   = KopisClient.parseKopisDate(m.get("prfpdto"));

                // upsert
                Optional<Perform> opt = repo.findByExternalId(externalId);
                if (opt.isPresent()) {
                    Perform p = opt.get();
                    boolean changed = false;

                    if (!safeEquals(p.getTitle(), m.getOrDefault("prfnm", ""))) {
                        p.setTitle(m.getOrDefault("prfnm", ""));
                        changed = true;
                    }
                    if (!safeEquals(p.getVenueName(), m.getOrDefault("fcltynm", ""))) {
                        p.setVenueName(m.getOrDefault("fcltynm", ""));
                        changed = true;
                    }
                    if (!safeEquals(p.getPosterUrl(), m.getOrDefault("poster", ""))) {
                        p.setPosterUrl(m.getOrDefault("poster", ""));
                        changed = true;
                    }
                    if (!safeEquals(p.getGenre(), m.getOrDefault("genrenm", ""))) {
                        p.setGenre(m.getOrDefault("genrenm", ""));
                        changed = true;
                    }
                    if (!safeEquals(p.getArea(), m.getOrDefault("area", ""))) {
                        p.setArea(m.getOrDefault("area", ""));
                        changed = true;
                    }
                    if (!safeEquals(p.getState(), stateNorm)) {
                        p.setState(stateNorm);
                        changed = true;
                    }
                    if (!safeEquals(p.getStartDate(), startDate)) {
                        p.setStartDate(startDate);
                        changed = true;
                    }
                    if (!safeEquals(p.getEndDate(), endDate)) {
                        p.setEndDate(endDate);
                        changed = true;
                    }

                    if (changed) {
                        // synopsis는 변경 시에만 갱신 고려(비용 절감). 필요 없으면 주석 처리 가능.
                        String synopsis = kopis.synopsis(externalId).orElse(p.getSynopsis());
                        if (synopsis != null && synopsis.length() > 4000) synopsis = synopsis.substring(0, 4000);
                        p.setSynopsis(synopsis);
                        repo.save(p);
                        savedOrUpdated++;
                    }
                } else {
                    String synopsis = kopis.synopsis(externalId).orElse("");
                    Perform p = Perform.builder()
                            .externalId(externalId)
                            .title(m.getOrDefault("prfnm", ""))
                            .startDate(startDate)
                            .endDate(endDate)
                            .venueName(m.getOrDefault("fcltynm", ""))
                            .posterUrl(m.getOrDefault("poster", ""))
                            .synopsis(synopsis)
                            .genre(m.getOrDefault("genrenm", ""))
                            .area(m.getOrDefault("area", ""))
                            .state(stateNorm)          // 01/02만 저장
                            .sigunguCode(m.getOrDefault("signgucodesub", ""))
                            .isAd(false)
                            .build();
                    repo.save(p);
                    savedOrUpdated++;
                }
            }
            page++;
        }
        log.info("[INGEST] DONE region={} state={} totalSavedOrUpdated={}",
                signgucodesub, prfstate, savedOrUpdated);
        return savedOrUpdated;
    }

    /**
     * [정리(Cleanup)] — 다음 조건의 레코드는 삭제(하드)
     *  1) state == '03'(공연완료)
     *  2) endDate < currDate (종료일이 기준일보다 과거)
     *  3) 이번 런의 관리범위(st~ed, 01/02)인데 seen에 없는 것(= 목록에서 사라짐)
     *
     *  리포지토리 메서드 확장 없이 동작하도록 findAll 후 필터링(데이터가 크면 전용 쿼리로 최적화 권장).
     */
    @Transactional
    public int cleanupAfterRun(LocalDate currDate, LocalDate st, LocalDate ed, Set<String> seen) {
        int deleted = 0;
        for (Perform p : repo.findAll()) {
            String stCode = nullSafe(p.getState());
            LocalDate s = p.getStartDate();
            LocalDate e = p.getEndDate();

            boolean shouldDelete =
                    "03".equals(stCode) ||
                            (e != null && e.isBefore(currDate)) ||
                            (ALLOWED_STATES.contains(stCode)
                                    && s != null && !s.isBefore(st)     // s >= st
                                    && e != null && !e.isAfter(ed)      // e <= ed
                                    && (p.getExternalId() != null && !seen.contains(p.getExternalId()))
                            );

            if (shouldDelete) {
                repo.delete(p);
                deleted++;
            }
        }
        return deleted;
    }

    // --------- 유틸 ---------
    private static boolean safeEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
    private static String nullSafe(String s) { return s == null ? "" : s; }
}
