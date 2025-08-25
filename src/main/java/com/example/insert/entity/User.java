package com.example.insert.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
<<<<<<< HEAD
import jakarta.persistence.*;

=======
>>>>>>> origin/main
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "profile_type")
    @Enumerated(EnumType.STRING)
    private ProfileType profileType;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
<<<<<<< HEAD

    @Column(nullable = false)
    private int points = 0;

    @Column(nullable = false)
    private int level = 1;
=======
>>>>>>> origin/main
    
    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
    
    public enum ProfileType {
        ALONE,      // 혼자
        COUPLE,     // 커플
        FAMILY      // 가족 단위
    }
}
