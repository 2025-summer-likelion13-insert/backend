package com.example.insert;

import com.example.insert.entity.User;
import com.example.insert.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class InsertApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsertApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository) {
        return args -> {
            // 테스트 사용자가 없으면 생성
            if (userRepository.findById(1L).isEmpty()) {
                User testUser = User.builder()
                        .id(1L)
                        .name("테스트사용자")
                        .email("test@example.com")
                        .password("password123")
                        .profileType(User.ProfileType.COUPLE)
                        .build();
                
                userRepository.save(testUser);
                System.out.println("테스트 사용자가 생성되었습니다: " + testUser.getName());
            }
            
            // 사용자 2도 생성
            if (userRepository.findById(2L).isEmpty()) {
                User user2 = User.builder()
                        .id(2L)
                        .name("사용자2")
                        .email("user2@example.com")
                        .password("password123")
                        .profileType(User.ProfileType.COUPLE)
                        .build();
                
                userRepository.save(user2);
                System.out.println("사용자2가 생성되었습니다: " + user2.getName());
            }
        };
    }
}
