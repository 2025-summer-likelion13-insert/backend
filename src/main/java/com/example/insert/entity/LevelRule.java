package com.example.insert.entity;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor;

@Entity @Table(name = "level_rule")
@Getter @Setter @NoArgsConstructor
public class LevelRule {
    @Id
    private Integer level; // 1,2,3...

    @Column(nullable = false)
    private String name;   // bronze/silver/gold...

    @Column(name = "required_points", nullable = false)
    private Integer requiredPoints;

    private String iconUrl;
}
