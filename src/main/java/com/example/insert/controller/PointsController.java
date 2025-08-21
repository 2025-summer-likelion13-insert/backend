package com.example.insert.controller;

import com.example.insert.service.PointsService;
import com.example.insert.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication; // ← 이걸로 import
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;
    private final AuthUtils authUtils;

    @GetMapping("/points")
    public Map<String, Object> getMyPoints(Authentication authentication) {
        Long userId = authUtils.getUserId(authentication);

        var s = pointsService.getMySummary(userId);
        Map<String, Object> body = new HashMap<>();
        body.put("points", s.getPoints());
        body.put("level", s.getLevel());
        body.put("levelName", s.getLevelName());
        body.put("nextLevel", s.getNextLevel());
        body.put("nextLevelName", s.getNextLevelName());
        body.put("remainingToNext", s.getRemainingToNext());
        return body;
    }
}
