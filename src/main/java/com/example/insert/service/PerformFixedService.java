package com.example.insert.service;

import com.example.insert.config.PerformIngestProperties;
import com.example.insert.dto.PerformCardDto;
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

        return selected;
    }

    /**
     * TOP10 딱 10개만 DB에 즉시 삽입(꼼수). excludeEnded=true면 종료(03)·종료일 지난 건 제외
     */
    @Transactional
    public int importTop10ExactToDb(boolean excludeEnded) {
        LocalDate curr = Optional.ofNullable(props.getCurrDate()).orElse(LocalDate.now());
        String st = curr.minusDays(30).format(YYMMDD);
        String ed = curr.format(YYMMDD);
        Integer area = 28; // TOP10은 area=28 고정

        // 박스오피스 호출 → rnum 정렬 상위 10개만
        List<Map<String, Object>> raw = callBoxOffice(st, ed, area);
        List<Map<String, Object>> top10 = raw.stream()
                .sorted(Comparator.comparingInt(m -> parseIntSafe(String.valueOf(m.getOrDefault("rnum","999")), 999)))
                .limit(10)
                .toList();

        int imported = 0;
        for (var m : top10) {
            String mt20id = str(m.get("mt20id")).trim();
            if (mt20id.isEmpty()) continue;

            // 이미 있으면 스킵
            if (repo.findByExternalId(mt20id).isPresent()) continue;

            String title  = str(m.get("prfnm"));
            String poster = str(m.get("poster"));

            // 상세로 날짜/상태/장소/구군 등 보강 (실패해도 최소 삽입)
            LocalDate startDate = null, endDate = null;
            String venue = null, genre = null, areaStr = null, stateNorm = null, sigungu = null;
            try {
                var det = fetchDetailBasic(mt20id);
                if (!det.isEmpty()) {
                    startDate = parseDateFlexible(det.get("prfpdfrom"));
                    endDate   = parseDateFlexible(det.get("prfpdto"));
                    venue     = det.get("fcltynm");
                    genre     = det.get("genrenm");
                    areaStr   = det.get("area");
                    sigungu   = det.get("signgucodesub");
                    // 상태 정규화
                    String stateRaw = String.valueOf(det.getOrDefault("prfstate","")).trim();
                    stateNorm = switch (stateRaw) {
                        case "01", "공연예정" -> "01";
                        case "02", "공연중"   -> "02";
                        case "03", "공연완료" -> "03";
                        default -> stateRaw;
                    };
                    if (excludeEnded && ("03".equals(stateNorm) || (endDate != null && endDate.isBefore(curr)))) {
                        continue;
                    }
                }
            } catch (Exception ignore) {
                // ignore
            }

            // 리플렉션으로 신규 엔티티 생성/세팅 → 영속화
            Object p = instantiatePerform();
            set(p, "externalId", mt20id);
            set(p, "title", title.isBlank() ? "[제목미상]" : title);
            set(p, "posterUrl", poster);
            set(p, "startDate", startDate);
            set(p, "endDate", endDate);
            set(p, "venueName", venue);
            set(p, "genre", genre);
            set(p, "area", areaStr);
            set(p, "state", stateNorm);
            set(p, "sigunguCode", sigungu == null ? "" : sigungu);
            set(p, "isAd", false);
            em.persist(p);
            imported++;
        }

        log.info("[TOP10→DB exact] imported={} of {} (st={} ed={} area={})",
                imported, Math.min(10, raw.size()), st, ed, area);
        return imported;
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
        var list = em.createQuery("""
            select p from Perform p where p.externalId = :eid
        """, getPerformClass()).setParameter("eid", externalId).getResultList();

        Object p;
        if (list.isEmpty()) {
            p = instantiatePerform();
            set(p, "externalId", externalId);
            em.persist(p);
        } else {
            p = list.get(0);
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
                set(p, "endDate", LocalDate.parse(to.replaceAll("[^0-9]", ""), YYMMDD));
            }
        } catch (Exception ignore) {}
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
