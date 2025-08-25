package com.example.insert.config;

import com.example.insert.service.KopisImportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(PerformIngestProperties.class)
public class PerformConfig {

    // Lombok @Slf4j 없이도 바로 찍히게 수동 로거 사용
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PerformConfig.class);

    /**
     * 앱 시작 직후 자동 인제스트
     * - props.currDate ~ props.endDate
     * - props.regions(예: 2826,2818,2817) × props.states(01,02)
     * - 비동기 스레드로 실행(부팅 지연 방지)
     */
    @Bean
    CommandLineRunner bootIngest(KopisImportService svc, PerformIngestProperties props) {
        return args -> {
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

                LocalDate curr = Optional.ofNullable(props.getCurrDate()).orElse(LocalDate.now());
                LocalDate end  = Optional.ofNullable(props.getEndDate()).orElse(curr);
                List<Integer> regions = Optional.ofNullable(props.getRegions()).orElse(List.of());
                List<String>  states  = Optional.ofNullable(props.getStates()).orElse(List.of("01","02"));
                int rows = Optional.ofNullable(props.getRows()).orElse(100);

                log.info("[BOOT-INGEST] start curr={} end={} regions={} states={} rows={}",
                        curr, end, regions, states, rows);

                int total = 0;
                for (Integer r : regions) {
                    for (String s : states) {
                        try {
                            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                            int n = svc.importOnce(curr, end, r, s, rows, seen); // ✅ 여기
                            total += n;
                            log.info("[BOOT-INGEST] region={} state={} savedOrUpdated={} seen={}",
                                    r, s, n, seen.size());
                        } catch (Exception e) {
                            log.error("[BOOT-INGEST] region={} state={} failed: {}", r, s, e.getMessage(), e);
                        }
                    }
                }
                log.info("[BOOT-INGEST] done totalSavedOrUpdated={}", total);
            }, "boot-ingest").start();
        };
    }

}
