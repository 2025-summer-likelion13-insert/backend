package com.example.insert.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_likes_user_perform",
                columnNames = { "user_id", "perform_id" }
        )
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_likes_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perform_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_likes_perform"))
    private Perform perform;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
