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
        int page = 1, savedOrUpdated = 0, failed = 0;

        while (true) {
            List<Map<String, String>> list = kopis.list(st, ed, signgucodesub, prfstate, page, rows);
            log.info("[INGEST] region={} state={} page={} rows={} fetched={}",
                    signgucodesub, prfstate, page, rows, (list==null?0:list.size()));

            if (list == null || list.isEmpty()) break;

            for (var m : list) {
                // --- 항목별 보호막: 어떤 공연이 실패해도 다음으로 진행 ---
                try {
                    String externalIdRaw = m.getOrDefault("mt20id", "").trim();
                    if (externalIdRaw.isEmpty()) continue;

                    // 엔티티 제약에 맞춰 정규화
                    String externalId = cut(externalIdRaw, 32);
                    seen.add(externalId); // 이번 런에서 관측됨

                    // 상태코드 정규화
                    String stateRaw  = m.getOrDefault("prfstate", "").trim();
                    String stateNorm = switch (stateRaw) {
                        case "01", "공연예정" -> "01";
                        case "02", "공연중"   -> "02";
                        case "03", "공연완료" -> "03";
                        default -> stateRaw;
                    };
                    // 01/02만 통과
                    if (!"01".equals(stateNorm) && !"02".equals(stateNorm)) {
                        continue;
                    }

                    // 날짜 파싱
                    LocalDate startDate = KopisClient.parseKopisDate(m.get("prfpdfrom"));
                    LocalDate endDate   = KopisClient.parseKopisDate(m.get("prfpdto"));

                    // 필드 정규화(엔티티 길이에 맞춤)
                    String title      = cutEmptyAsNull(m.getOrDefault("prfnm", ""), 255);
                    if (title == null) title = "[제목미상]";
                    String venueName  = cutEmptyAsNull(m.getOrDefault("fcltynm", ""), 255);
                    String posterUrl  = m.getOrDefault("poster", "");
                    if (posterUrl != null && posterUrl.length() > 60000) posterUrl = posterUrl.substring(0, 60000); // TEXT 안전권
                    String genre      = cutEmptyAsNull(m.getOrDefault("genrenm", ""), 64);
                    String area       = cutEmptyAsNull(m.getOrDefault("area", ""), 64);
                    String sigungu    = cutEmptyAsNull(m.getOrDefault("signgucodesub", ""), 4);
                    String state      = cutEmptyAsNull(stateNorm, 32);

                    Optional<Perform> opt = repo.findByExternalId(externalId);
                    if (opt.isPresent()) {
                        Perform p = opt.get();
                        boolean changed = false;

                        if (!safeEquals(p.getTitle(), title)) { p.setTitle(title); changed = true; }
                        if (!safeEquals(p.getVenueName(), venueName)) { p.setVenueName(venueName); changed = true; }
                        if (!safeEquals(p.getPosterUrl(), posterUrl)) { p.setPosterUrl(posterUrl); changed = true; }
                        if (!safeEquals(p.getGenre(), genre)) { p.setGenre(genre); changed = true; }
                        if (!safeEquals(p.getArea(), area)) { p.setArea(area); changed = true; }
                        if (!safeEquals(p.getState(), state)) { p.setState(state); changed = true; }
                        if (!safeEquals(p.getStartDate(), startDate)) { p.setStartDate(startDate); changed = true; }
                        if (!safeEquals(p.getEndDate(), endDate)) { p.setEndDate(endDate); changed = true; }

                        if (changed) {
                            // synopsis는 변경 시에만(비용 절감). DB가 아직 TEXT일 수도 있으니 안전 컷.
                            String synopsis = kopis.synopsis(externalId).orElse(p.getSynopsis());
                            if (synopsis != null && synopsis.length() > 500_000) synopsis = synopsis.substring(0, 500_000);
                            p.setSynopsis(synopsis);

                            repo.save(p);
                            repo.flush(); // ★ 이 항목에서 문제 있으면 여기서 즉시 터져서 로그로 식별 가능
                            savedOrUpdated++;
                        }
                    } else {
                        // 신규 생성
                        String synopsis = kopis.synopsis(externalId).orElse("");
                        if (synopsis.length() > 500_000) synopsis = synopsis.substring(0, 500_000);

                        Perform p = Perform.builder()
                                .externalId(externalId)
                                .title(title)
                                .startDate(startDate)
                                .endDate(endDate)
                                .venueName(venueName)
                                .posterUrl(posterUrl)
                                .synopsis(synopsis)
                                .genre(genre)
                                .area(area)
                                .state(state)          // 01/02 저장
                                .sigunguCode(sigungu)
                                .isAd(false)
                                .build();

                        repo.save(p);
                        repo.flush(); // ★ 항목별 즉시 검증
                        savedOrUpdated++;
                    }

                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    failed++;
                    String cause = (e.getMostSpecificCause()!=null? e.getMostSpecificCause().getMessage(): e.getMessage());
                    log.warn("[INGEST] skip one (DataIntegrityViolation) cause={}", cause, e);
                    // 현재 영속성 컨텍스트 오염 방지
                    try { repo.flush(); } catch (Exception ignore) {}
                } catch (Exception e) {
                    failed++;
                    log.warn("[INGEST] skip one (Exception) msg={}", e.toString(), e);
                }
            }
            page++;
        }
        log.info("[INGEST] DONE region={} state={} totalSavedOrUpdated={} failed={}",
                signgucodesub, prfstate, savedOrUpdated, failed);
        return savedOrUpdated;
    }

    /* === 아래 헬퍼들을 같은 클래스에 추가 (이미 비슷한게 있으면 생략) === */
    private static String cut(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) : s;
    }
    private static String cutEmptyAsNull(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return (t.length() > max) ? t.substring(0, max) : t;
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
