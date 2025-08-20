package com.example.insert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class KopisClient {

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

    /** 목록 조회 (필터 적용) */
    public List<Map<String,String>> list(LocalDate st, LocalDate ed,
                                         Integer signgucode, String prfstate,
                                         int page, int rows) throws Exception {
        var sb = new StringBuilder(base)
                .append("/pblprfr?service=").append(enc(key))
                .append("&stdate=").append(st.format(D8))
                .append("&eddate=").append(ed.format(D8))
                .append("&cpage=").append(page)
                .append("&rows=").append(rows);
        if (signgucode != null) sb.append("&signgucode=").append(signgucode);
        if (prfstate != null && !prfstate.isBlank()) sb.append("&prfstate=").append(enc(prfstate));

        var doc = getXml(sb.toString());
        var nodes = doc.getElementsByTagName("db");
        var out = new ArrayList<Map<String,String>>();
        for (int i=0;i<nodes.getLength();i++) {
            var e = (Element) nodes.item(i);
            Map<String,String> m = new HashMap<>();
            for (String tag : List.of("mt20id","prfnm","prfpdfrom","prfpdto","fcltynm","poster","genrenm","area","prfstate")) {
                var n = e.getElementsByTagName(tag);
                m.put(tag, n.getLength()>0 ? n.item(0).getTextContent() : "");
            }
            out.add(m);
        }
        return out;
    }

    /** 상세: 줄거리(sty) */
    public Optional<String> synopsis(String mt20id) throws Exception {
        var url = base + "/pblprfr/" + enc(mt20id) + "?service=" + enc(key);
        var doc = getXml(url);
        var nodes = doc.getElementsByTagName("db");
        if (nodes.getLength()==0) return Optional.empty();
        var e = (Element) nodes.item(0);
        var n = e.getElementsByTagName("sty");
        return Optional.ofNullable(n.getLength()>0 ? n.item(0).getTextContent() : null);
    }

    /** 박스오피스 Top10 (런타임/캐시 용) */
    public List<Map<String,String>> boxOffice(LocalDate st, LocalDate ed, String area) throws Exception {
        var sb = new StringBuilder(base)
                .append("/boxoffice?service=").append(enc(key))
                .append("&stdate=").append(st.format(D8))
                .append("&eddate=").append(ed.format(D8));
        if (area != null && !area.isBlank()) sb.append("&area=").append(enc(area));

        var doc = getXml(sb.toString());
        var nodes = doc.getElementsByTagName("boxof"); // 실제 태그는 명세에 맞춰 조정
        var out = new ArrayList<Map<String,String>>();
        for (int i=0;i<nodes.getLength();i++) {
            var e = (Element) nodes.item(i);
            Map<String,String> m = new HashMap<>();
            for (String tag : List.of("rnum","mt20id","prfnm","poster")) {
                var n = e.getElementsByTagName(tag);
                m.put(tag, n.getLength()>0 ? n.item(0).getTextContent() : "");
            }
            out.add(m);
        }
        return out;
    }

    public static LocalDate parseKopisDate(String s) {
        if (s == null) return null;
        s = s.trim();
        try { return LocalDate.parse(s, D10); } catch (Exception ignored) {}
        try { return LocalDate.parse(s, D8); }  catch (Exception ignored) {}
        try { return LocalDate.parse(s); }      catch (Exception ignored) {}
        return null;
    }
}
