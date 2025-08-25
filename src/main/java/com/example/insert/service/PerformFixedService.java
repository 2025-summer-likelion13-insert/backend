package com.example.insert.service;

import com.example.insert.config.PerformIngestProperties;
import com.example.insert.dto.PerformCardDto;
import com.example.insert.entity.Perform;
import com.example.insert.repository.PerformRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformFixedService {

    private final PerformIngestProperties props;
    private final PerformRepository repo;

    @PersistenceContext
    private EntityManager em;

    @Value("${kopis.api.key}")
    private String kopisApiKey;

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    /* ------------------------------ 공개 메서드 ------------------------------ */

    /**
     * 증분 인제스트(전량) — 기간: currDate ~ endDate, 지역: signgucodesub(4자리), 상태: 01/02
     * 목록 → (필요시 상세) → upsert
     */
    @Transactional
    public int importAllFixed() {
        Objects.requireNonNull(props, "PerformIngestProperties 주입 실패");
        Objects.requireNonNull(kopisApiKey, "kopis.api.key 누락");

        LocalDate curr = Optional.ofNullable(props.getCurrDate()).orElse(LocalDate.now());
        LocalDate end  = Optional.ofNullable(props.getEndDate()).orElse(curr);
        String st = curr.format(YYMMDD);
        String ed = end.format(YYMMDD);

        List<Integer> regions = Optional.ofNullable(props.getRegions()).orElse(List.of());
        List<String> states = Optional.ofNullable(props.getStates()).orElse(List.of("01", "02"));
        int rows = Optional.ofNullable(props.getRows()).orElse(100);

        int total = 0;
        for (Integer signgucodesub : regions) {
            for (String state : states) {
                total += pullAndUpsertList(st, ed, signgucodesub, state, rows);
            }
        }
        return total;
    }

    /**
     * TOP10 — 후보를 40개까지 쌓고(박스오피스 + pblprfr 백필), 종료 제외 후 10개 보장
     * - 박스오피스: area=28(인천), st=curr-30d, ed=curr, date 미전송
     */
    public List<Map<String, Object>> top10Fixed() {
        LocalDate curr = Optional.ofNullable(props.getCurrDate()).orElse(LocalDate.now());
        String st = curr.minusDays(30).format(YYMMDD);
        String ed = curr.format(YYMMDD);

        // 1) 박스오피스 10개(랭킹 소스)
        List<Map<String, Object>> box = callBoxOffice(st, ed, 28);
        // 후보를 삽입 순서 유지 + 중복 제거
        LinkedHashMap<String, Map<String, Object>> candidates = new LinkedHashMap<>();
        for (var m : box) {
            String id = String.valueOf(m.getOrDefault("mt20id", "")).trim();
            if (!id.isEmpty()) candidates.putIfAbsent(id, m);
        }

        // 2) 백필: DB → pblprfr 로 40개까지
        int target = 40;
        if (candidates.size() < target) {
            // 2-1) DB 우선 (state 01/02, 기간 curr~endDate)
            LocalDate end = Optional.ofNullable(props.getEndDate()).orElse(curr);
            try {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createQuery("""
                    select p.externalId, p.title, p.posterUrl
                    from Perform p
                    where p.state in ('01','02')
                      and p.startDate >= :st
                      and p.endDate   <= :ed
                    order by (case when p.state='02' then 0 else 1 end), p.startDate asc
                    """)
                        .setParameter("st", curr)
                        .setParameter("ed", end)
                        .setMaxResults(target * 2)
                        .getResultList();

                for (Object[] r : rows) {
                    String id = (String) r[0];
                    if (id == null || id.isBlank()) continue;
                    if (!candidates.containsKey(id)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("mt20id", id);
                        m.put("prfnm", r[1] != null ? r[1] : "");
                        m.put("poster", r[2] != null ? r[2] : "");
                        candidates.put(id, m);
                        if (candidates.size() >= target) break;
                    }
                }
            } catch (Exception ignore) {
                // 엔티티 스키마/인덱스 상황에 따라 실패해도 무시
            }
        }

        if (candidates.size() < target) {
            // 2-2) pblprfr 백필 — signgucodesub(4자리), prfstate(01/02), 기간: curr~endDate
            LocalDate end = Optional.ofNullable(props.getEndDate()).orElse(curr);
            String ingestSt = curr.format(YYMMDD);
            String ingestEd = end.format(YYMMDD);
            int rows = Math.max(20, Optional.ofNullable(props.getRows()).orElse(40));
            List<Integer> regions = Optional.ofNullable(props.getRegions()).orElse(List.of());
            List<String> states = Optional.ofNullable(props.getStates()).orElse(List.of("01", "02"));

            int pageLimitPerCombo = 3;
            outer:
            for (Integer region : regions) {
                for (String state : states) {
                    for (int page = 1; page <= pageLimitPerCombo; page++) {
                        List<Map<String, Object>> pageItems = callList(ingestSt, ingestEd, region, state, page, rows);
                        if (pageItems.isEmpty()) break;
                        for (var m : pageItems) {
                            String id = String.valueOf(m.getOrDefault("mt20id", "")).trim();
                            if (!id.isEmpty() && !candidates.containsKey(id)) {
                                candidates.put(id, m);
                                if (candidates.size() >= target) break outer;
                            }
                        }
                    }
                }
            }

        }

        // 3) 종료 제외 필터 + 10개 보장
        List<Map<String, Object>> selected = new ArrayList<>(10);
        List<String> processed = new ArrayList<>();
        Iterator<Map.Entry<String, Map<String, Object>>> it = candidates.entrySet().iterator();

        while (selected.size() < 10 && it.hasNext()) {
            var e = it.next();
            String id = e.getKey();
            processed.add(id);

            boolean ended = false;
            try {
                var det = fetchDetailCore(id);
                String state = det.getOrDefault("prfstate", "");
                if ("03".equals(state)) {
                    ended = true;
                } else {
                    LocalDate dto = parseDateFlexible(det.get("prfpdto"));
                    LocalDate currDate = curr;
                    if (dto != null && dto.isBefore(currDate)) ended = true;
                }
            } catch (Exception ex) {
                // 상세 실패 시 제외하지 않고 통과
            }
            if (!ended) selected.add(e.getValue());
        }

        // 3-1) 아직 10개 미만이면 pblprfr을 더 끌어와 후보를 확장
        if (selected.size() < 10) {
            LocalDate end = Optional.ofNullable(props.getEndDate()).orElse(curr);
            String ingestSt = curr.format(YYMMDD);
            String ingestEd = end.format(YYMMDD);
            int rows = Math.max(20, Optional.ofNullable(props.getRows()).orElse(40));
            List<Integer> regions = Optional.ofNullable(props.getRegions()).orElse(List.of());
            List<String> states = Optional.ofNullable(props.getStates()).orElse(List.of("01", "02"));

            int addedBatches = 0;
            while (selected.size() < 10 && addedBatches < 3) {
                boolean anyAdded = false;
                for (Integer region : regions) {
                    for (String state : states) {
                        int page = addedBatches + 4; // 1~3페이지는 위에서 사용
                        List<Map<String, Object>> pageItems = callList(ingestSt, ingestEd, region, state, page, rows);
                        if (pageItems.isEmpty()) continue;
                        for (var m : pageItems) {
                            String id = String.valueOf(m.getOrDefault("mt20id", "")).trim();
                            if (id.isEmpty()) continue;
                            if (processed.contains(id)) continue;
                            // 상세 검사
                            boolean ended = false;
                            try {
                                var det = fetchDetailCore(id);
                                String stCode = det.getOrDefault("prfstate", "");
                                if ("03".equals(stCode)) {
                                    ended = true;
                                } else {
                                    LocalDate dto = parseDateFlexible(det.get("prfpdto"));
                                    if (dto != null && dto.isBefore(curr)) ended = true;
                                }
                            } catch (Exception ignore) {}
                            if (!ended) {
                                selected.add(m);
                                processed.add(id);
                                anyAdded = true;
                                if (selected.size() >= 10) break;
                            }
                        }
                        if (selected.size() >= 10) break;
                    }
                    if (selected.size() >= 10) break;
                }
                if (!anyAdded) break;
                addedBatches++;
            }
        }

        log.info("[TOP10] selected ids={}",
                selected.stream().map(m -> String.valueOf(m.get("mt20id"))).toList());

        return selected;
    }

    /**
     * TOP10 딱 10개만 DB에 즉시 삽입(꼼수). excludeEnded=true면 종료(03)·종료일 지난 건 제외
     */
    @Transactional
    public int importTop10ExactToDb(boolean excludeEnded) {
        // 1) 종료 제외된 TOP10 집합
        List<Map<String, Object>> top10 = top10Fixed();
        if (excludeEnded) {
            top10 = top10.stream().filter(this::isNotPastEndDateSafe).toList();
        }

        int ok = 0;
        List<String> failed = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (var m : top10) {
            String id = String.valueOf(m.getOrDefault("mt20id","")).trim();
            if (id.isEmpty()) continue;
            ids.add(id);

            try {
                upsertTop10One(m);   // ← 여기서 기본 정보 upsert (상세 시도 포함)
                ok++;
                log.info("[TOP10→DB] upsert ok {}", id);
            } catch (Exception e) {
                failed.add(id);
                log.warn("[TOP10→DB] upsert FAIL {} cause={}", id, e.toString());
                try { em.clear(); } catch (Exception ignore) {}
            }
        }

        // 2) 상세가 비어 있는 항목들만 재보강(네트워크/레이트리밋 때문일 수 있음)
        try {
            int enriched = enrichMissingDetails(ids);
            log.info("[TOP10→DB] enrich missing details done: {}", enriched);
        } catch (Exception e) {
            log.warn("[TOP10→DB] enrich step skipped due to {}", e.toString());
        }

        log.info("[TOP10→DB exact] ok={} failed={} idsFailed={}", ok, failed.size(), failed);
        return ok;
    }

    @Transactional
    public int enrichMissingDetails(List<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()) return 0;

        // DB에서 해당 10개를 한 번에 읽고, “상세가 빈” 것만 추림
        List<Perform> list = repo.findByExternalIdIn(externalIds);
        int changed = 0;

        for (Perform p : list) {
            boolean need =
                    isBlank(p.getSynopsis()) ||
                            p.getStartDate() == null ||
                            p.getEndDate() == null ||
                            isBlank(p.getVenueName()) ||
                            isBlank(p.getState());

            if (!need) continue;

            String eid = p.getExternalId();
            try {
                // 상세/줄거리 재조회
                Map<String, String> det = fetchDetailBasic(eid);
                String synopsis = fetchSynopsis(eid);

                boolean dirty = false;

                if (det != null && !det.isEmpty()) {
                    LocalDate from = parseYmdOrNull(det.get("prfpdfrom"));
                    LocalDate to   = parseYmdOrNull(det.get("prfpdto"));
                    String    st   = normalizeState(det.get("prfstate"));
                    String    ven  = cutEmptyAsNull(det.get("fcltynm"), 255);
                    String    gen  = cutEmptyAsNull(det.get("genrenm"), 64);
                    String    ar   = cutEmptyAsNull(det.get("area"), 64);
                    String    sig  = cutEmptyAsNull(det.get("signgucodesub"), 4);

                    if (from != null && !from.equals(p.getStartDate())) { p.setStartDate(from); dirty = true; }
                    if (to   != null && !to.equals(p.getEndDate()))     { p.setEndDate(to);   dirty = true; }
                    if (!isBlank(st) && !st.equals(p.getState()))       { p.setState(cut(st, 32)); dirty = true; }
                    if (!safeEquals(p.getVenueName(), ven))             { p.setVenueName(ven); dirty = true; }
                    if (!safeEquals(p.getGenre(), gen))                 { p.setGenre(gen);     dirty = true; }
                    if (!safeEquals(p.getArea(), ar))                   { p.setArea(ar);       dirty = true; }
                    if (!safeEquals(p.getSigunguCode(), sig))           { p.setSigunguCode(sig); dirty = true; }
                }

                if (!isBlank(synopsis)) {
                    if (synopsis.length() > 500_000) synopsis = synopsis.substring(0, 500_000);
                    if (!safeEquals(p.getSynopsis(), synopsis)) { p.setSynopsis(synopsis); dirty = true; }
                }

                if (dirty) {
                    repo.saveAndFlush(p);
                    changed++;
                    log.info("[ENRICH] updated {}", eid);
                }
            } catch (Exception ex) {
                log.warn("[ENRICH] fail eid={} cause={}", eid, ex.toString());
                try { em.clear(); } catch (Exception ignore) {}
            }

            // KOPIS 과다호출 방지(너무 빠르면 429/타임아웃) — 필요시 딜레이
            // try { Thread.sleep(50); } catch (InterruptedException ignore) {}
        }
        return changed;
    }

    private static boolean isBlank(String s){ return s==null || s.isBlank(); }
    private static boolean safeEquals(Object a, Object b){ return (a==b) || (a!=null && a.equals(b)); }
    private static String cut(String s, int max){ return (s!=null && s.length()>max)? s.substring(0,max): s; }
    private static String cutEmptyAsNull(String s, int max){
        if (s==null) return null;
        String t=s.trim();
        if (t.isEmpty()) return null;
        return t.length()>max? t.substring(0,max): t;
    }









    private boolean isNotPastEndDateSafe(Map<String, Object> m) {
        try {
            String ymd = String.valueOf(m.getOrDefault("prfpdto",""));
            // yyyymmdd 형태라면:
            java.time.LocalDate end = java.time.LocalDate.parse(ymd, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return !end.isBefore(java.time.LocalDate.now());
        } catch (Exception ignore) {
            // 날짜 파싱 실패 시 제외하지 않음(너무 보수적으로 털지 않기)
            return true;
        }
    }


    private void upsertTop10One(Map<String, Object> m) {
        String externalId = String.valueOf(m.getOrDefault("mt20id","")).trim();
        if (externalId.isEmpty()) throw new IllegalArgumentException("mt20id empty");

        // ---------- 상세 보강 ----------
        LocalDate startDate = null, endDate = null;
        String state = null, venue = null, genre = null, area = null, sigungu = null;
        try {
            var det = fetchDetailBasic(externalId);
            if (!det.isEmpty()) {
                startDate = parseYmdOrNull(det.get("prfpdfrom"));
                endDate   = parseYmdOrNull(det.get("prfpdto"));
                state     = det.get("prfstate");
                venue     = det.get("fcltynm");
                genre     = det.get("genrenm");
                area      = det.get("area");
                sigungu   = det.get("signgucodesub");
            }
        } catch (Exception ignore) {}

        // ---------- 값 정규화 (엔티티 제약에 정확히 맞춤) ----------
        // external_id VARCHAR(32)
        externalId = cut(externalId, 32);

        // title VARCHAR(255) - 비면 기본값
        String title = cutEmptyAsNull(String.valueOf(m.getOrDefault("prfnm","")).trim(), 255);
        if (title == null) title = "[제목미상]";

        // poster_url TEXT - UTF-8 바이트 기준 안전 컷(64KB 미만)
        String poster = String.valueOf(m.getOrDefault("poster",""));
        poster = cutByUtf8Bytes(poster, 60_000);

        // state VARCHAR(32) - 코드화(빈값이면 '01'로 기본)
        state = normalizeState(state);
        if (state == null || state.isBlank()) state = "01";
        state = cut(state, 32);

        // venue VARCHAR(255), genre/area VARCHAR(64), sigungu_code VARCHAR(4)
        venue   = cutEmptyAsNull(venue, 255);
        genre   = cutEmptyAsNull(genre, 64);
        area    = cutEmptyAsNull(area, 64);
        sigungu = cutEmptyAsNull(sigungu, 4);

        // ---------- UPSERT ----------
        Perform entity = repo.findByExternalId(externalId).orElseGet(Perform::new);
        boolean isNew = (entity.getId() == null);

        entity.setExternalId(externalId);
        entity.setTitle(title);
        entity.setPosterUrl(poster);
        entity.setState(state);
        if (startDate != null) entity.setStartDate(startDate);
        if (endDate   != null) entity.setEndDate(endDate);
        if (venue     != null) entity.setVenueName(venue);
        if (genre     != null) entity.setGenre(genre);
        if (area      != null) entity.setArea(area);
        if (sigungu   != null) entity.setSigunguCode(sigungu);
        // synopsis는 여기선 저장 안 함(오류 원인 제거)

        try {
            repo.saveAndFlush(entity);               // ★ 항목별 즉시 DB 적용
            log.info("[TOP10→DB] {} {} titleLen={} posterBytes={} state={} sigungu={}",
                    (isNew ? "insert" : "update"),
                    externalId,
                    title.length(),
                    (poster == null ? 0 : poster.getBytes(java.nio.charset.StandardCharsets.UTF_8).length),
                    state, sigungu);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String cause = (e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
            log.error("[TOP10→DB] integrity error extId={} titleLen={} posterLen={} venueLen={} genreLen={} areaLen={} sigungu={} state={} cause={}",
                    externalId,
                    (title == null ? -1 : title.length()),
                    (poster == null ? -1 : poster.length()),
                    (venue == null ? -1 : venue.length()),
                    (genre == null ? -1 : genre.length()),
                    (area == null ? -1 : area.length()),
                    sigungu, state, cause, e);
            throw e; // 상위에서 failed 집계
        } catch (Exception e) {
            log.error("[TOP10→DB] unexpected error extId={} msg={}", externalId, e.toString(), e);
            throw e;
        }
    }

    /* --- helpers (같은 클래스에 추가/유지) --- */

    private static String normalizeState(String s) {
        if (s == null || s.isBlank()) return "01";
        String t = s.trim();
        if (t.equals("01") || t.contains("예정")) return "01";
        if (t.equals("02") || t.contains("중"))   return "02";
        if (t.equals("03") || t.contains("완료") || t.contains("종료")) return "03";
        return t;
    }
    private static String cutByUtf8Bytes(String s, int maxBytes) {
        if (s == null) return null;
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (b.length <= maxBytes) return s;
        int bytes = 0, i = 0, len = s.length();
        while (i < len && bytes < maxBytes) {
            int cp = s.codePointAt(i);
            int need = new String(Character.toChars(cp))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes + need > maxBytes) break;
            bytes += need;
            i += Character.charCount(cp);
        }
        return s.substring(0, i);
    }






    private String safeLen(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private java.time.LocalDate parseYmdOrNull(String ymd) {
        try {
            if (ymd == null || ymd.isBlank()) return null;
            return java.time.LocalDate.parse(ymd, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }



    /* ------------------------------ 내부 구현 ------------------------------ */

    /** 목록→상세 업서트 (signgucodesub & prfstate 사용) */
    private int pullAndUpsertList(String st, String ed, Integer signgucodesub, String state, int rows) {
        int saved = 0;
        int page = 1;

        while (true) {
            List<Map<String, Object>> items = callList(st, ed, signgucodesub, state, page, rows);
            if (items.isEmpty()) break;

            for (var m : items) {
                String mt20id = str(m.get("mt20id"));
                if (mt20id.isBlank()) continue;

                // 이미 존재하면 갱신, 없으면 신규
                var list = em.createQuery("""
                    select p from Perform p where p.externalId = :eid
                """, getPerformClass())
                        .setParameter("eid", mt20id)
                        .getResultList();

                Object p;
                if (list.isEmpty()) {
                    p = instantiatePerform();
                    set(p, "externalId", mt20id);
                    em.persist(p);
                } else {
                    p = list.get(0);
                }

                set(p, "title",      str(m.get("prfnm")));
                set(p, "venueName",  str(m.get("fcltynm")));
                set(p, "posterUrl",  str(m.get("poster")));
                set(p, "genre",      str(m.get("genrenm")));
                set(p, "area",       str(m.get("area")));
                set(p, "state",      str(m.get("prfstate"))); // 응답 그대로 저장(필요시 정규화 가능)
                try {
                    String from = str(m.get("prfpdfrom"));
                    String to   = str(m.get("prfpdto"));
                    if (!from.isBlank()) {
                        set(p, "startDate", LocalDate.parse(from.replaceAll("[^0-9]", ""), YYMMDD));
                    }
                    if (!to.isBlank()) {
                        set(p, "endDate",   LocalDate.parse(to.replaceAll("[^0-9]", ""), YYMMDD));
                    }
                } catch (Exception ignore) {}
                saved++;
            }
            page++;
        }
        return saved;
    }

    /** pblprfr 호출 (signgucodesub=4자리, prfstate=01/02, date 미사용) */
    private List<Map<String, Object>> callList(String st, String ed,
                                               Integer signgucodesub, String prfstate,
                                               int page, int rows) {
        try {
            String base = "http://www.kopis.or.kr/openApi/restful/pblprfr";
            StringBuilder qs = new StringBuilder()
                    .append("service=").append(kopisApiKey)
                    .append("&stdate=").append(st)
                    .append("&eddate=").append(ed)
                    .append("&cpage=").append(page)
                    .append("&rows=").append(rows);
            if (signgucodesub != null) qs.append("&signgucodesub=").append(signgucodesub);
            if (prfstate != null && !prfstate.isBlank()) qs.append("&prfstate=").append(prfstate);

            URI url = URI.create(base + "?" + qs);
            Document doc = fetchXml(url);
            NodeList items = doc.getElementsByTagName("db");
            if (items == null || items.getLength() == 0) return Collections.emptyList();

            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                Element e = (Element) items.item(i);
                Map<String, Object> m = new LinkedHashMap<>();
                for (String tag : List.of("mt20id","prfnm","prfpdfrom","prfpdto","fcltynm","poster","genrenm","area","prfstate","signgucodesub")) {
                    m.put(tag, text(e, tag));
                }
                out.add(m);
            }
            return out;
        } catch (Exception ex) {
            log.error("[pblprfr] call failed: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /** KOPIS 박스오피스 호출 — area(2자리)만 사용, date 미전송 */
    private List<Map<String, Object>> callBoxOffice(String st, String ed, Integer area) {
        String base = "http://www.kopis.or.kr/openApi/restful/boxoffice";
        StringBuilder qs = new StringBuilder()
                .append("service=").append(kopisApiKey)
                .append("&stdate=").append(st)
                .append("&eddate=").append(ed);
        if (area != null) qs.append("&area=").append(area); // 28 고정 전달

        URI url = URI.create(base + "?" + qs);
        log.info("[boxoffice] url={}", url);

        try {
            Document doc = fetchXml(url);
            NodeList items = doc.getElementsByTagName("boxof");
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < (items == null ? 0 : items.getLength()); i++) {
                Element e = (Element) items.item(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rnum",   text(e, "rnum"));
                m.put("mt20id", text(e, "mt20id"));
                m.put("prfnm",  text(e, "prfnm"));
                m.put("poster", text(e, "poster"));
                out.add(m);
            }
            log.info("[boxoffice] parsed items={}", out.size());
            // rnum 기준 정렬 보장
            out.sort(Comparator.comparingInt(o -> parseIntSafe(String.valueOf(o.getOrDefault("rnum", "999")), 999)));
            return out;
        } catch (Exception ex) {
            log.error("[boxoffice] call failed: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /** 상세 핵심(종료 제외 판정용): prfstate, prfpdfrom, prfpdto */
    private Map<String, String> fetchDetailCore(String mt20id) {
        String base = "http://www.kopis.or.kr/openApi/restful/pblprfr/";
        URI url = URI.create(base + mt20id + "?service=" + kopisApiKey);
        Document doc = fetchXml(url);
        NodeList items = doc.getElementsByTagName("db");
        Map<String, String> m = new HashMap<>();
        if (items != null && items.getLength() > 0) {
            Element e = (Element) items.item(0);
            m.put("prfstate",  text(e, "prfstate"));
            m.put("prfpdfrom", text(e, "prfpdfrom"));
            m.put("prfpdto",   text(e, "prfpdto"));
        }
        return m;
    }

    /** 상세(간이) — 날짜/상태/장소/장르/지역/구군 코드까지 */
    private Map<String, String> fetchDetailBasic(String mt20id) {
        String base = "http://www.kopis.or.kr/openApi/restful/pblprfr/";
        URI url = URI.create(base + mt20id + "?service=" + kopisApiKey);
        Document doc = fetchXml(url);
        NodeList items = doc.getElementsByTagName("db");
        Map<String, String> out = new HashMap<>();
        if (items != null && items.getLength() > 0) {
            Element e = (Element) items.item(0);
            for (String tag : List.of("prfpdfrom","prfpdto","fcltynm","genrenm","area","prfstate","signgucodesub")) {
                out.put(tag, text(e, tag));
            }
        }
        return out;
    }

    /** 상세 줄거리(기존 유지) */
    private String fetchSynopsis(String mt20id) {
        URI url = URI.create(String.format(
                "http://www.kopis.or.kr/openApi/restful/pblprfr/%s?service=%s",
                mt20id, kopisApiKey
        ));
        Document doc = fetchXml(url);
        NodeList items = doc.getElementsByTagName("db");
        if (items == null || items.getLength() == 0) return null;
        Element e = (Element) items.item(0);
        return text(e, "sty");
    }

    /** Perform upsert (externalId 기준) — 인제스트용 */
    @Transactional
    protected void upsertPerform(String externalId, String title, String from, String to,
                                 String venue, String poster, String synopsis) {
        // --- 길이/널 방어 ---
        externalId = cut(externalId, 32);
        title      = defaultIfBlank(cut(title, 255), "[제목미상]");
        venue      = cutEmptyAsNull(venue, 255);
        poster     = (poster != null && poster.length() > 60000) ? poster.substring(0, 60000) : poster;

        // synopsis 방어: DB가 MEDIUMTEXT면 사실 컷이 거의 필요 없지만,
        // 스키마가 아직 TEXT/VARCHAR일 수도 있으니 안전 컷(문자 기준) 적용
        if (synopsis != null && synopsis.length() > 500_000) {
            synopsis = synopsis.substring(0, 500_000);
        }

        var list = em.createQuery("""
        select p from Perform p where p.externalId = :eid
    """, getPerformClass()).setParameter("eid", externalId).getResultList();

        Object p = list.isEmpty() ? instantiatePerform() : list.get(0);
        if (list.isEmpty()) {
            set(p, "externalId", externalId);
            em.persist(p);
        }

        set(p, "title", title);
        set(p, "venueName", venue);
        set(p, "posterUrl", poster);
        set(p, "synopsis", synopsis);

        try {
            if (from != null && !from.isBlank()) {
                set(p, "startDate", LocalDate.parse(from.replaceAll("[^0-9]", ""), YYMMDD));
            }
            if (to != null && !to.isBlank()) {
                set(p, "endDate",   LocalDate.parse(to.replaceAll("[^0-9]", ""), YYMMDD));
            }
        } catch (Exception ignore) {}

        // ★ 항목별로 즉시 DB에 보내 예외를 현재 건으로 한정
        try {
            em.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String cause = (e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
            log.error("[INGEST] integrity error extId={} titleLen={} synopsisLen={} cause={}",
                    externalId, (title==null?-1:title.length()), (synopsis==null?-1:synopsis.length()), cause, e);
            // 현재 건만 롤백하고 다음 건 진행하도록 예외를 상위로 전파하지 않게 설계하려면 여기서 swallow하고 return;
            throw e; // ← 만약 계속 진행하고 싶으면 이 줄을 지우고 return; 로 바꾸세요.
        }
    }


    /* ------------------------------ XML/리플렉션/유틸 ------------------------------ */

    private Document fetchXml(URI uri) {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(5000);
        var rt = new org.springframework.web.client.RestTemplate(f);

        var res = rt.getForEntity(uri, byte[].class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new RuntimeException("KOPIS HTTP " + res.getStatusCodeValue() + " @ " + uri);
        }

        try {
            DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
            fac.setNamespaceAware(false);
            fac.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var b = fac.newDocumentBuilder();
            return b.parse(new java.io.ByteArrayInputStream(res.getBody()));
        } catch (Exception e) {
            throw new RuntimeException("XML parse error: " + e.getMessage(), e);
        }
    }

    private static String text(Element e, String tag) {
        NodeList nl = e.getElementsByTagName(tag);
        if (nl == null || nl.getLength() == 0) return "";
        var n = nl.item(0);
        return (n == null) ? "" : n.getTextContent();
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // null-safe Object → String
    private static String str(Object v) {
        return (v == null) ? "" : String.valueOf(v);
    }

    private static LocalDate parseDateFlexible(String s) {
        if (s == null || s.isBlank()) return null;
        String digits = s.replaceAll("[^0-9]", "");
        try { return LocalDate.parse(digits, YYMMDD); } catch (Exception e) { return null; }
    }

    // --- Perform 엔티티 접근(리플렉션; 기존 파일 수정 없이 사용) ---
    private Class<?> performClassCache;
    private Class<?> getPerformClass() {
        if (performClassCache == null) {
            try { performClassCache = Class.forName("com.example.insert.entity.Perform"); }
            catch (ClassNotFoundException e) { throw new IllegalStateException("Perform 엔티티를 찾을 수 없습니다.", e); }
        }
        return performClassCache;
    }
    private Object instantiatePerform() {
        try { return getPerformClass().getDeclaredConstructor().newInstance(); }
        catch (Exception e) { throw new IllegalStateException("Perform 인스턴스 생성 실패", e); }
    }
    private void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException nsf) {
            log.debug("필드 없음: {}", field);
        } catch (Exception e) {
            throw new RuntimeException("필드 세팅 실패: " + field, e);
        }
    }

    /** 제목 부분검색(무페이징) — 기존 유지 */
    public List<PerformCardDto> searchByTitleNoPaging(String q, Integer limit) {
        if (q == null || q.isBlank()) return List.of();
        var list = repo.findByTitleContainingIgnoreCase(q.trim(), Sort.by(Sort.Direction.DESC, "startDate"));
        int cap = (limit == null || limit <= 0) ? 200 : Math.min(limit, 1000);
        return list.stream().limit(cap).map(PerformCardDto::of).toList();
    }
}
