package com.example.insert.controller;

import com.example.insert.dto.PerformCardDto;
import com.example.insert.service.PerformFixedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PerformFixedController {

    private final PerformFixedService fixedService;

    /** TOP10: 서비스(Map) → 카드 DTO(3필드)로 축소 반환 */
    @GetMapping("/api/performs/fixed/top10")
    public List<PerformCardDto> top10() {
        return fixedService.top10Fixed().stream()
                .map(m -> new PerformCardDto(
                        String.valueOf(m.getOrDefault("mt20id", "")),   // 외부아이디 (KOPIS)
                        String.valueOf(m.getOrDefault("prfnm", "")),    // 제목
                        String.valueOf(m.getOrDefault("poster", ""))    // 포스터
                ))
                .toList();
    }

    /** 다가오는 공연: 서비스(Map) → 카드 DTO(3필드)로 축소 반환 (DB기반) */
    @GetMapping("/api/performs/fixed/upcoming")
    public List<PerformCardDto> upcoming() {
        return fixedService.upcomingFixed().stream()
                .map(m -> new PerformCardDto(
                        String.valueOf(m.getOrDefault("externalId", "")),
                        String.valueOf(m.getOrDefault("title", "")),
                        String.valueOf(m.getOrDefault("posterUrl", ""))
                ))
                .toList();
    }
}
