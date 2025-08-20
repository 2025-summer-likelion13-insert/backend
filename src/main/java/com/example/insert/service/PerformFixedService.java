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
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformFixedService {

    // 주의: KopisClient 사용 안 함 (현재 구현에선 직접 XML 호출)
    private final PerformIngestProperties props;
    private final PerformRepository repo;

    @PersistenceContext
    private EntityManager em;

    @Value("${kopis.api.key}")
    private String kopisApiKey;

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    /* ------------------------------ 공개 메서드 ------------------------------ */

    /** 고정값(기간 × 지역 × 상태) 전량 적재 */
    @Transactional
    public int importAllFixed() {
        Objects.requireNonNull(props, "PerformIngestProperties 주입 실패");
        Objects.requireNonNull(kopisApiKey, "kopis.api.key 누락");

        LocalDate ps = Optional.ofNullable(props.getStartDate()).orElse(LocalDate.now().minusDays(30));
        LocalDate pe = Optional.ofNullable(props.getEndDate()).orElse(LocalDate.now());
        String st = ps.format(YYMMDD);
        String ed = pe.format(YYMMDD);

        List<Integer> regions = Optional.ofNullable(props.getRegions()).orElse(List.of());
        List<String> states = Optional.ofNullable(props.getStates()).orElse(List.of("01","02"));
        int rows = Optional.ofNullable(props.getRows()).orElse(100);

        int total = 0;
        for (Integer region : regions) {
            for (String state : states) {
                total += pullAndUpsertList(st, ed, region, state, rows);
            }
        }
        return total;
    }

    // PerformFixedService.java (기존 메서드 교체)
    public List<Map<String,Object>> top10Fixed() {
        LocalDate curr = Optional.ofNullable(props.getCurrDate()).orElse(LocalDate.now());
        String d = curr.format(YYMMDD); // yyyyMMdd
        String st = d, ed = d;

        // regions 첫 값 → 광역코드(예: 2826 -> 28)
        Integer area = null;
        List<Integer> regions = Optional.ofNullable(props.getRegions()).orElse(List.of());
        if (!regions.isEmpty() && regions.get(0) != null) {
            int raw = regions.get(0);
            area = (raw >= 100) ? (raw / 100) : raw;
        }

        List<Map<String,Object>> list = callBoxOffice(st, ed, area, d, null, null);

        // rnum 오름차순 상위 10개
        return list.stream()
                .sorted(Comparator.comparingInt(m -> parseIntSafe(String.valueOf(m.getOrDefault("rnum","999")), 999)))
                .limit(10)
                .toList();
    }

    /** 고정 currDate 기준 31일 윈도우로 DB 조회 */
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> upcomingFixed() {
        Objects.requireNonNull(props, "PerformIngestProperties 주입 실패");

        LocalDate from = Optional.ofNullable(props.getCurrDate()).orElse(LocalDate.now());
        LocalDate to   = from.plusDays(31);

        var query = em.createQuery("""
            select p.id, p.externalId, p.title, p.startDate, p.endDate, p.venueName, p.posterUrl
            from Perform p
            where p.startDate between :from and :to
            order by p.startDate asc
        """);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<Object[]> rows = query.getResultList();
        List<Map<String,Object>> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("externalId", r[1]);
            m.put("title", r[2]);
            m.put("startDate", r[3]);
            m.put("endDate", r[4]);
            m.put("venueName", r[5]);
            m.put("posterUrl", r[6]);
            out.add(m);
        }
        return out;
    }

    /* ------------------------------ 내부 구현 ------------------------------ */

    /** 목록→ID들→상세 병행 upsert */
    private int pullAndUpsertList(String st, String ed, Integer region, String state, int rows) {
        int saved = 0;
        int page = 1;

        while (true) {
            URI url = URI.create(String.format(
                    "http://www.kopis.or.kr/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=%d&rows=%d&signgucode=%s&prfstate=%s",
                    kopisApiKey, st, ed, page, rows, String.valueOf(region), state
            ));
            Document doc = fetchXml(url);
            NodeList items = doc.getElementsByTagName("db");
            if (items == null || items.getLength() == 0) break;

            for (int i=0; i<items.getLength(); i++) {
                Element e = (Element) items.item(i);
                String mt20id   = text(e, "mt20id");
                String prfnm    = text(e, "prfnm");
                String pdFrom   = text(e, "prfpdfrom");
                String pdTo     = text(e, "prfpdto");
                String venue    = text(e, "fcltynm");
                String poster   = text(e, "poster");

                String synopsis = fetchSynopsis(mt20id);
                upsertPerform(mt20id, prfnm, pdFrom, pdTo, venue, poster, synopsis);
                saved++;
            }
            page++;
        }
        return saved;
    }

    /**
     * KOPIS 박스오피스 호출 (박스오피스는 '하루' 기준이므로 st=ed=date 권장)
     * @param st   yyyyMMdd
     * @param ed   yyyyMMdd
     * @param area 시도코드 (예: 11 서울, 28 인천) — null 가능
     * @param date 기준일 (필수, st~ed 범위 내)
     */
    // PerformFixedService.java (기존 메서드 교체)
    private List<Map<String,Object>> callBoxOffice(String st, String ed, Integer area,
                                                   String date, String cate, String seats) {
        String base = "http://www.kopis.or.kr/openApi/restful/boxoffice";
        StringBuilder qs = new StringBuilder()
                .append("service=").append(kopisApiKey)
                .append("&stdate=").append(st)
                .append("&eddate=").append(ed)
                .append("&date=").append(date);
        if (area != null) qs.append("&area=").append(area);   // ✅ null이면 파라미터 제거
        if (cate != null && !cate.isBlank())  qs.append("&catecode=").append(cate);
        if (seats != null && !seats.isBlank()) qs.append("&srchseatscale=").append(seats);

        URI url = URI.create(base + "?" + qs);
        log.info("[boxoffice] url={}", url);

        try {
            Document doc = fetchXml(url);
            NodeList items = doc.getElementsByTagName("boxof"); // 응답 아이템
            List<Map<String,Object>> out = new ArrayList<>();
            for (int i=0; i<(items==null?0:items.getLength()); i++) {
                Element e = (Element) items.item(i);
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("rnum",   text(e, "rnum"));
                m.put("mt20id", text(e, "mt20id"));
                m.put("prfnm",  text(e, "prfnm"));
                m.put("poster", text(e, "poster"));
                out.add(m);
            }
            log.info("[boxoffice] parsed items={}", out.size());
            return out;
        } catch (Exception ex) {
            log.error("[boxoffice] call failed: {}", ex.getMessage(), ex);
            // ❗ 실패해도 예외 던지지 않고 빈 배열
            return Collections.emptyList();
        }
    }


    /** 상세 줄거리 호출 */
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

    /** Perform upsert (externalId 기준) */
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

    /* ------------------------------ XML/리플렉션 유틸 ------------------------------ */

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
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return (n == null) ? null : n.getTextContent();
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
            // 필드가 없으면 무시(로그만 남기고 계속 진행)
            log.debug("필드 없음: {}", field);
        } catch (Exception e) {
            throw new RuntimeException("필드 세팅 실패: " + field, e);
        }
    }

    /** 시군구 목록 -> “허용 ID 집합” (동일 기간에 목록 API 통해 수집) */
    private Set<String> collectAllowedIds(String st, String ed, List<Integer> signgucodes, int rows) {
        Set<String> allowed = new LinkedHashSet<>();
        if (signgucodes == null || signgucodes.isEmpty()) return allowed;

        for (Integer sgc : signgucodes) {
            int page = 1;
            while (true) {
                URI url = URI.create(String.format(
                        "http://www.kopis.or.kr/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=%d&rows=%d&signgucode=%s",
                        kopisApiKey, st, ed, page, rows, String.valueOf(sgc)
                ));
                Document doc = fetchXml(url);
                NodeList items = doc.getElementsByTagName("db");
                if (items == null || items.getLength() == 0) break;

                for (int i = 0; i < items.getLength(); i++) {
                    Element e = (Element) items.item(i);
                    String id = text(e, "mt20id");
                    if (id != null && !id.isBlank()) allowed.add(id);
                }
                page++;
            }
        }
        return allowed;
    }

    /** 제목 부분검색(무페이징) */
    public List<PerformCardDto> searchByTitleNoPaging(String q, Integer limit) {
        if (q == null || q.isBlank()) return List.of();
        var list = repo.findByTitleContainingIgnoreCase(q.trim(), Sort.by(Sort.Direction.DESC, "startDate"));
        int cap = (limit == null || limit <= 0) ? 200 : Math.min(limit, 1000);
        return list.stream().limit(cap).map(PerformCardDto::of).toList();
    }

    /* ------------------------------ helpers ------------------------------ */
    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
