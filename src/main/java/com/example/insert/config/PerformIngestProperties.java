package com.example.insert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@ConfigurationProperties(prefix = "perform.ingest")
public class PerformIngestProperties {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate currDate;
    private Integer rows;
    private List<Integer> regions; // 2826,2818,2817
    private List<String> states;   // 01,02

    // getters/setters
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
}

