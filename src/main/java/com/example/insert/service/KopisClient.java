package com.example.insert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
@RequiredArgsConstructor
public class KopisClient {

    // 클래스 내부 맨 위에
    private static final Logger log = LoggerFactory.getLogger(KopisClient.class);

    @Value("${kopis.api.base}")
    private String base;

    @Value("${kopis.api.key}")
    private String key;

    private static final DateTimeFormatter D8  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter D10 = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private Document getXml(String url) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        var db = dbf.newDocumentBuilder();
        try (var is = new java.net.URL(url).openStream()) {
            return db.parse(is);
        }
    }

    /** 목록 조회 (기간/지역/상태/페이지)
     *  - 지역은 구·군 4자리 코드: signgucodesub
     *  - 상태는 '01'(공연예정), '02'(공연중)
     */
    public List<Map<String,String>> list(LocalDate st, LocalDate ed,
                                         Integer signgucodesub, String prfstate,
                                         int page, int rows) throws Exception {
        var sb = new StringBuilder(base)
                .append("/pblprfr?service=").append(enc(key))
                .append("&stdate=").append(st.format(D8))
                .append("&eddate=").append(ed.format(D8))
                .append("&cpage=").append(page)
                .append("&rows=").append(rows);

        // 지역: 구·군 4자리
        if (signgucodesub != null) sb.append("&signgucodesub=").append(signgucodesub);
        // 상태: 01/02
        if (prfstate != null && !prfstate.isBlank()) sb.append("&prfstate=").append(enc(prfstate));

        String url = sb.toString();
        log.info("[KOPIS:list] {}", url); // ✅ 여기가 중요

        var doc = getXml(sb.toString());
        var nodes = doc.getElementsByTagName("db");
        var out = new ArrayList<Map<String,String>>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var e = (Element) nodes.item(i);
            Map<String,String> m = new HashMap<>();
            for (String tag : List.of(
                    "mt20id","prfnm","prfpdfrom","prfpdto","fcltynm",
                    "poster","genrenm","area","prfstate","signgucodesub"
            )) {
                var n = e.getElementsByTagName(tag);
                m.put(tag, n.getLength() > 0 ? n.item(0).getTextContent() : "");
            }
            out.add(m);
        }
        return out;
    }

    /** 상세 핵심 필드: 종료 제외/필터링 용도 */
    public Optional<Map<String,String>> detailCore(String mt20id) throws Exception {
        var url = base + "/pblprfr/" + enc(mt20id) + "?service=" + enc(key);
        var doc = getXml(url);
        var nodes = doc.getElementsByTagName("db");
        if (nodes.getLength() == 0) return Optional.empty();
        var e = (Element) nodes.item(0);
        Map<String,String> m = new HashMap<>();
        for (String tag : List.of("prfstate","prfpdfrom","prfpdto","signgucodesub")) {
            var n = e.getElementsByTagName(tag);
            m.put(tag, n.getLength() > 0 ? n.item(0).getTextContent() : "");
        }
        return Optional.of(m);
    }

    /** 상세: 줄거리(sty) */
    public Optional<String> synopsis(String mt20id) throws Exception {
        var url = base + "/pblprfr/" + enc(mt20id) + "?service=" + enc(key);
        var doc = getXml(url);
        var nodes = doc.getElementsByTagName("db");
        if (nodes.getLength() == 0) return Optional.empty();
        var e = (Element) nodes.item(0);
        var n = e.getElementsByTagName("sty");
        return Optional.ofNullable(n.getLength() > 0 ? n.item(0).getTextContent() : null);
    }

    /** 박스오피스 Top10 — area(시도코드=2자리) 사용, date 미전송 */
    public List<Map<String,String>> boxOffice(LocalDate st, LocalDate ed, String area) throws Exception {
        var sb = new StringBuilder(base)
                .append("/boxoffice?service=").append(enc(key))
                .append("&stdate=").append(st.format(D8))
                .append("&eddate=").append(ed.format(D8));

        // 요구사항: 기본 인천(28). 파라미터가 비었으면 28로 고정.
        String areaCode = (area == null || area.isBlank()) ? "28" : area.trim();
        sb.append("&area=").append(enc(areaCode));

        var doc = getXml(sb.toString());
        var nodes = doc.getElementsByTagName("boxof"); // 명세에 따라 조정
        var out = new ArrayList<Map<String,String>>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var e = (Element) nodes.item(i);
            Map<String,String> m = new HashMap<>();
            for (String tag : List.of("rnum","mt20id","prfnm","poster")) {
                var n = e.getElementsByTagName(tag);
                m.put(tag, n.getLength() > 0 ? n.item(0).getTextContent() : "");
            }
            out.add(m);
        }
        return out;
    }

    /** KOPIS 날짜 문자열 파싱 (yyyy.MM.dd / yyyyMMdd / ISO) */
    public static LocalDate parseKopisDate(String s) {
        if (s == null) return null;
        s = s.trim();
        try { return LocalDate.parse(s, D10); } catch (Exception ignored) {}
        try { return LocalDate.parse(s, D8); }  catch (Exception ignored) {}
        try { return LocalDate.parse(s); }      catch (Exception ignored) {}
        return null;
    }
}
