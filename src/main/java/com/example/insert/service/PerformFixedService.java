package com.example.insert.service;

import com.example.insert.config.PerformIngestProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformFixedService {

    private final PerformIngestProperties props;

    @PersistenceContext
    private EntityManager em;

    @Value("${kopis.api.key}")
    private String kopisApiKey;

    private final RestTemplate http = new RestTemplate();
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    /* ------------------------------ 공개 메서드 ------------------------------ */

    /** 고정값(기간 × 지역 × 상태) 전량 적재 */
    @Transactional
    public int importAllFixed() {
        int total = 0;
        String st = props.getStartDate().format(YYMMDD);
        String ed = props.getEndDate().format(YYMMDD);

        for (Integer region : props.getRegions()) {
            for (String state : props.getStates()) {
                total += pullAndUpsertList(st, ed, region, state, props.getRows());
            }
        }
        return total;
    }

    /** 고정값 TOP10: (curr-30) ~ curr 로 31일 창 + 기준일(date)=curr */
    public List<Map<String,Object>> top10Fixed() {
        // ✅ 변경 포인트: 과거 30일 ~ 오늘
        var curr = props.getCurrDate();
        var stD  = curr.minusDays(30);  // 시작 = 오늘-30
        var edD  = curr;                // 종료 = 오늘(현재 날짜)

        String st   = stD.format(YYMMDD);   // yyyyMMdd
        String ed   = edD.format(YYMMDD);   // yyyyMMdd
        String date = curr.format(YYMMDD);  // boxoffice 필수 파라미터 (st~ed 범위 내)

        // 시군구(예: 2826,2818,2817) → 시도(28)로 변환
        java.util.Set<Integer> areas = props.getRegions().stream()
                .map(r -> (r >= 100) ? (r / 100) : r)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        // (동일 기간) 시군구 조건으로 목록API에서 허용 공연ID 수집
        java.util.Set<String> allowed = collectAllowedIds(st, ed, props.getRegions(), props.getRows());

        // 시도별 박스오피스 호출 병합
        java.util.Map<String, java.util.Map<String,Object>> merged = new java.util.LinkedHashMap<>();
        for (Integer area : areas) {
            for (java.util.Map<String,Object> m : callBoxOffice(st, ed, area, date, null, null)) {
                Object key = m.get("mt20id");
                if (key != null) merged.putIfAbsent(key.toString(), m);
            }
        }

        // 시군구 허용ID와 교집합 → rnum 정렬 → 상위 10
        java.util.List<java.util.Map<String,Object>> filtered = merged.values().stream()
                .filter(m -> {
                    Object id = m.get("mt20id");
                    return id != null && (allowed.isEmpty() || allowed.contains(id.toString()));
                })
                .sorted(java.util.Comparator.comparingInt(m -> {
                    try { return Integer.parseInt(String.valueOf(m.getOrDefault("rnum", "999"))); }
                    catch (Exception ignore) { return 999; }
                }))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());

        // 교집합이 비면 시도 기준 TOP10이라도 노출(옵션)
        if (filtered.isEmpty()) {
            filtered = new java.util.ArrayList<>(merged.values()).stream()
                    .sorted(java.util.Comparator.comparingInt(m -> {
                        try { return Integer.parseInt(String.valueOf(m.getOrDefault("rnum", "999"))); }
                        catch (Exception ignore) { return 999; }
                    }))
                    .limit(10)
                    .collect(java.util.stream.Collectors.toList());
        }
        return filtered;
    }




    /** 고정 currDate 기준 31일 윈도우로 DB 조회 */
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> upcomingFixed() {
        LocalDate from = props.getCurrDate();
        LocalDate to   = from.plusDays(31);

        // JPQL로 기존 Perform 엔티티 조회 (리포지토리 수정 없이)
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

                // 상세 줄거리 호출
                String synopsis = fetchSynopsis(mt20id);

                // upsert by externalId
                upsertPerform(mt20id, prfnm, pdFrom, pdTo, venue, poster, synopsis);
                saved++;
            }
            page++;
        }
        return saved;
    }

    /** KOPIS 박스오피스 호출
     * @param st     yyyyMMdd (최대 31일 윈도우의 시작)
     * @param ed     yyyyMMdd (최대 31일 윈도우의 끝)
     * @param area   시도코드 (예: 11 서울, 28 인천)
     * @param date   기준일 (필수, st~ed 범위 내)
     * @param cate   장르코드(옵션, null 가능)
     * @param seats  좌석수(옵션, null 가능) 예: "100","300","500","1000","5000","10000"
     */
    private List<Map<String,Object>> callBoxOffice(String st, String ed, Integer area,
                                                   String date, String cate, String seats) {
        String base = "http://www.kopis.or.kr/openApi/restful/boxoffice";
        StringBuilder qs = new StringBuilder()
                .append("service=").append(kopisApiKey)
                .append("&stdate=").append(st)
                .append("&eddate=").append(ed)
                .append("&date=").append(date)
                .append("&area=").append(String.valueOf(area));
        if (cate != null && !cate.isBlank())  qs.append("&catecode=").append(cate);
        if (seats != null && !seats.isBlank()) qs.append("&srchseatscale=").append(seats);

        URI url = URI.create(base + "?" + qs);
        log.info("boxoffice url={}", url);

        Document doc = fetchXml(url);

        // 루트: <boxofs> / 아이템: <boxof>
        NodeList items = doc.getElementsByTagName("boxof");
        List<Map<String,Object>> out = new ArrayList<>();
        for (int i=0; i<(items==null?0:items.getLength()); i++) {
            Element e = (Element) items.item(i);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("rnum",   text(e, "rnum"));
            m.put("mt20id", text(e, "mt20id"));
            m.put("prfnm",  text(e, "prfnm"));
            m.put("poster", text(e, "poster"));
            // 필요하면 기간/장소도 씁니다: prfpd, prfplcnm
            out.add(m);
        }
        log.info("boxoffice parsed items={}", out.size());
        return out;
    }



    /** 상세 줄거리 호출 */
    private String fetchSynopsis(String mt20id) {
        URI url = URI.create(String.format(
                "http://www.kopis.or.kr/openApi/restful/pblprfr/%s?service=%s",
                mt20id, kopisApiKey
        ));
        Document doc = fetchXml(url);
        // 상세 응답의 루트가 <db> 하나일 수 있음
        NodeList items = doc.getElementsByTagName("db");
        if (items.getLength() == 0) return null;
        Element e = (Element) items.item(0);
        return text(e, "sty");
    }

    /** Perform upsert (externalId 기준) */
    @Transactional
    protected void upsertPerform(String externalId, String title, String from, String to,
                                 String venue, String poster, String synopsis) {
        // 존재 여부
        var list = em.createQuery("""
            select p from Perform p where p.externalId = :eid
        """, getPerformClass()).setParameter("eid", externalId).getResultList();

        Object p;
        if (list.isEmpty()) {
            p = instantiatePerform();
            set(p, "externalId", externalId);
            em.persist(p);
        } else {
            p = list.get(0); // managed
        }

        // 필드 매핑 (엔티티 필드명: externalId, title, startDate, endDate, venueName, posterUrl, synopsis 가정)
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
        // managed 상태라 flush 시 업데이트됨
    }

    /* ------------------------------ XML/리플렉션 유틸 ------------------------------ */

    private Document fetchXml(URI uri) {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(5000);
        var rt = new org.springframework.web.client.RestTemplate(f);

        // ✅ 문자열이 아니라 "바이트"로 받는다 (인코딩 보존)
        org.springframework.http.ResponseEntity<byte[]> res = rt.getForEntity(uri, byte[].class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new RuntimeException("KOPIS HTTP " + res.getStatusCodeValue() + " @ " + uri);
        }

        try {
            var fac = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            fac.setNamespaceAware(false);
            fac.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var b = fac.newDocumentBuilder();

            // ✅ 응답 바이트를 "그대로" 파서에 전달 → XML 선언/헤더의 charset을 따라 해석
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
            // 필드명이 다르면 여기에서 한 번만 경고 던지고 무시할 수도 있음
        } catch (Exception e) {
            throw new RuntimeException("필드 세팅 실패: " + field, e);
        }
    }

    // 시군구 목록 -> “허용 ID 집합”
    private java.util.Set<String> collectAllowedIds(String st, String ed, java.util.List<Integer> signgucodes, int rows) {
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        for (Integer sgc : signgucodes) {
            int page = 1;
            while (true) {
                java.net.URI url = java.net.URI.create(String.format(
                        "http://www.kopis.or.kr/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=%d&rows=%d&signgucode=%s",
                        kopisApiKey, st, ed, page, rows, String.valueOf(sgc)
                ));
                org.w3c.dom.Document doc = fetchXml(url);
                org.w3c.dom.NodeList items = doc.getElementsByTagName("db");
                if (items == null || items.getLength() == 0) break;

                for (int i = 0; i < items.getLength(); i++) {
                    org.w3c.dom.Element e = (org.w3c.dom.Element) items.item(i);
                    String id = text(e, "mt20id");
                    if (id != null && !id.isBlank()) allowed.add(id);
                }
                page++;
            }
        }
        return allowed;
    }

}


