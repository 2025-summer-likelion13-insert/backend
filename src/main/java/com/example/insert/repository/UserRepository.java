package com.example.insert.repository;

import com.example.insert.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

<<<<<<< HEAD
import com.example.insert.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


=======
>>>>>>> origin/main
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 이메일 존재 여부 확인
     */
    boolean existsByEmail(String email);
<<<<<<< HEAD


=======
>>>>>>> origin/main
}
