package com.example.insert.repository;

import com.example.insert.entity.LevelRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LevelRuleRepository extends JpaRepository<LevelRule, Integer> {
    Optional<LevelRule> findTopByRequiredPointsLessThanEqualOrderByRequiredPointsDesc(int points);
    Optional<LevelRule> findByLevel(int level);
}
