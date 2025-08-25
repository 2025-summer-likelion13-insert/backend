package com.example.insert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Ingest 설정 (스캔은 @ConfigurationPropertiesScan으로)
 * - regions: signgucodesub 4자리 코드 (예: 2826, 2818, 2817)
 * - states : 공연 상태 코드 (01=공연예정, 02=공연중)만 사용
 * - currDate / startDate / endDate 는 yyyy-MM-dd 형식
 *
 * 헬퍼:
 *  - top10StartDate = currDate - 30일
 *  - top10EndDate   = currDate
 *  - ingestStartDate = currDate (null이면 today)
 *  - ingestEndDate   = endDate (null이면 currDate)
 *  - rowsOrDefault   = rows>0 ? rows : 20
 */
@ConfigurationProperties(prefix = "perform.ingest")
public class PerformIngestProperties {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate currDate;

    /** 페이지 당 개수 (기본 20 권장) */
    private Integer rows;

    /** 구·군 코드(4자리, signgucodesub) */
    private List<Integer> regions; // 2826,2818,2817

    /** 상태 코드(01=공연예정, 02=공연중) */
    private List<String> states;   // 01,02

    // ---- getters/setters (바인딩용) ----
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }

    public LocalDate getCurrDate() { return currDate; }
    public void setCurrDate(LocalDate v) { this.currDate = v; }

    public Integer getRows() { return rows; }
    public void setRows(Integer rows) { this.rows = rows; }

    public List<Integer> getRegions() { return regions; }
    public void setRegions(List<Integer> regions) { this.regions = regions; }

    public List<String> getStates() { return states; }
    public void setStates(List<String> states) { this.states = states; }

    // ---- 헬퍼: null-safe/규칙성 날짜 계산 ----

    /** null-safe 현재 기준일 (설정 없으면 today) */
    public LocalDate currOrToday() {
        return currDate != null ? currDate : LocalDate.now();
    }

    /** TOP10: 시작일 = currDate - 30일 */
    public LocalDate getTop10StartDate() {
        return currOrToday().minusDays(30);
    }

    /** TOP10: 종료일 = currDate */
    public LocalDate getTop10EndDate() {
        return currOrToday();
    }

    /** Ingest: 시작일 = currDate (fallback today) */
    public LocalDate getIngestStartDate() {
        return currOrToday();
    }

    /** Ingest: 종료일 = endDate (없으면 currDate) */
    public LocalDate getIngestEndDate() {
        return endDate != null ? endDate : currOrToday();
    }

    /** rows 기본값(20) */
    public int getRowsOrDefault() {
        return (rows != null && rows > 0) ? rows : 20;
    }

    /** regions null-safe */
    public List<Integer> getRegionsOrEmpty() {
        return regions != null ? regions : Collections.emptyList();
    }

    /** states null-safe */
    public List<String> getStatesOrEmpty() {
        return states != null ? states : Collections.emptyList();
    }
}
