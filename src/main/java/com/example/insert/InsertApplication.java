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
            try {
                // 테스트 사용자가 없으면 생성 (ID 자동 생성)
                if (userRepository.findByEmail("test@example.com").isEmpty()) {
                    User testUser = User.builder()
                            .name("테스트사용자")
                            .email("test@example.com")
                            .password("password123")
                            .profileType(User.ProfileType.COUPLE)
                            .build();
                    
                    User savedUser = userRepository.save(testUser);
                    System.out.println("테스트 사용자가 생성되었습니다: ID=" + savedUser.getId() + ", 이름=" + savedUser.getName());
                }
                
                // 사용자 2도 생성
                if (userRepository.findByEmail("user2@example.com").isEmpty()) {
                    User user2 = User.builder()
                            .name("사용자2")
                            .email("user2@example.com")
                            .password("password123")
                            .profileType(User.ProfileType.COUPLE)
                            .build();
                    
                    User savedUser2 = userRepository.save(user2);
                    System.out.println("사용자2가 생성되었습니다: ID=" + savedUser2.getId() + ", 이름=" + savedUser2.getName());
                }
                
                System.out.println("데이터 초기화가 완료되었습니다.");
                
            } catch (Exception e) {
                System.err.println("데이터 초기화 중 오류 발생: " + e.getMessage());
                // 오류가 발생해도 애플리케이션은 계속 실행
            }
        };
    }
}
