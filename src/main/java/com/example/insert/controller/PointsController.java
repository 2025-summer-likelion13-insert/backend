package com.example.insert.controller;

import com.example.insert.dto.PointLedgerItem;
import com.example.insert.dto.PointLedgerPageResponse;
import com.example.insert.repository.PointLedgerRepository;
import com.example.insert.service.PointsService;
import com.example.insert.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;
    private final PointLedgerRepository ledgerRepo;
    private final CurrentUser current;

    // 3-1) 요약 조회 (기존)
    @GetMapping("/points")
    public Map<String, Object> getMyPoints() {
        Long userId = current.idOrThrow();
        var s = pointsService.getMySummary(userId);
        return Map.of(
                "points", s.getPoints(),
                "level", s.getLevel(),
                "levelName", s.getLevelName(),
                "nextLevel", s.getNextLevel(),
                "nextLevelName", s.getNextLevelName(),
                "remainingToNext", s.getRemainingToNext()
        );
    }

    // 3-2) 포인트 내역 조회 (신규)
    @GetMapping("/points/ledger")
    @Transactional(readOnly = true)
    public PointLedgerPageResponse getMyPointLedger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type // "REVIEW" | "FAVORITE" (옵션)
    ) {
        Long userId = current.idOrThrow();
        var pageable = PageRequest.of(page, size);

        var pg = (type == null || type.isBlank())
                ? ledgerRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : ledgerRepo.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type.toUpperCase(), pageable);

        List<PointLedgerItem> items = pg.getContent().stream()
                .map(e -> new PointLedgerItem(
                        e.getType(),
                        e.getDelta(),
                        e.getSourceId(),
                        e.getReason(),
                        e.getCreatedAt()
                ))
                .toList();

        return new PointLedgerPageResponse(
                items, pg.getNumber(), pg.getSize(), pg.getTotalElements(), pg.getTotalPages()
        );
    }
}
