package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PointLedgerPageResponse {
    private final List<PointLedgerItem> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
}
