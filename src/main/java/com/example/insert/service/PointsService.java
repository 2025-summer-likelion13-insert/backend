package com.example.insert.service;


import com.example.insert.entity.LevelRule;
import com.example.insert.entity.PointLedger;
import com.example.insert.entity.User;
import com.example.insert.repository.LevelRuleRepository;
import com.example.insert.repository.PointLedgerRepository;
import com.example.insert.repository.UserRepository;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service @RequiredArgsConstructor
public class PointsService {
    private static final String TYPE_REVIEW = "REVIEW";
    private static final int REVIEW_POINT = 100;

    private final UserRepository userRepo;
    private final PointLedgerRepository ledgerRepo;
    private final LevelRuleRepository levelRepo;

//    // =============================================================
//    // 포인트 테스트용 삭제 예정
//    private static final String TYPE_FAVORITE = "FAVORITE";
//
//
//    @Transactional
//    public void awardForFavorite(Long userId, Long placeId) {
//        // 멱등: 같은 장소를 같은 유저가 여러 번 찜해도 1회만 적립
//        if (ledgerRepo.existsByUserIdAndTypeAndSourceId(userId, TYPE_FAVORITE, placeId)) return;
//
//        User user = userRepo.findById(userId)
//                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
//
//        PointLedger pl = new PointLedger();
//        pl.setUser(user);
//        pl.setType(TYPE_FAVORITE);
//        pl.setSourceId(placeId);
//        pl.setDelta(REVIEW_POINT);          // +1 (임시)
//        pl.setReason("찜 적립(임시)");        // 이유 라벨
//        ledgerRepo.save(pl);
//
//        user.setPoints(user.getPoints() + REVIEW_POINT);
//
//        int p = user.getPoints();
//        int newLevel = levelRepo
//                .findTopByRequiredPointsLessThanEqualOrderByRequiredPointsDesc(p)
//                .map(LevelRule::getLevel).orElse(1);
//        if (newLevel != user.getLevel()) user.setLevel(newLevel);
//    }
//    // =============================================================


    @Transactional
    public void awardForReview(Long userId, Long reviewId) {
        if (ledgerRepo.existsByUserIdAndTypeAndSourceId(userId, TYPE_REVIEW, reviewId)) return;

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));

        PointLedger pl = new PointLedger();
        pl.setUser(user);
        pl.setType(TYPE_REVIEW);
        pl.setSourceId(reviewId);
        pl.setDelta(REVIEW_POINT);
        pl.setReason("리뷰 작성 적립");
        ledgerRepo.save(pl);

        user.setPoints(user.getPoints() + REVIEW_POINT);

        int p = user.getPoints();
        int newLevel = levelRepo
                .findTopByRequiredPointsLessThanEqualOrderByRequiredPointsDesc(p)
                .map(LevelRule::getLevel).orElse(1);
        if (newLevel != user.getLevel()) user.setLevel(newLevel);
    }

    @Transactional(readOnly = true)
    public PointsSummary getMySummary(Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        int curLevel = user.getLevel();
        int points = user.getPoints();

        Optional<LevelRule> next = levelRepo.findByLevel(curLevel + 1);

        Integer remain = next.map(n -> Math.max(0, n.getRequiredPoints() - points)).orElse(null);
        String levelName = levelRepo.findByLevel(curLevel).map(LevelRule::getName).orElse("bronze");
        String nextLevelName = next.map(LevelRule::getName).orElse(null);
        Integer nextLevel = next.map(LevelRule::getLevel).orElse(null);

        return new PointsSummary(points, curLevel, levelName, nextLevel, nextLevelName, remain);
    }

    @Getter @AllArgsConstructor
    public static class PointsSummary {
        private final int points;
        private final int level;
        private final String levelName;
        private final Integer nextLevel;
        private final String nextLevelName;
        private final Integer remainingToNext;
    }
}