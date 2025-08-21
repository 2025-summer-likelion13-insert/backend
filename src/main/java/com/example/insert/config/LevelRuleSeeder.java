package com.example.insert.config;

import com.example.insert.entity.LevelRule;
import com.example.insert.repository.LevelRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class LevelRuleSeeder implements CommandLineRunner {
    private final LevelRuleRepository repo;
    @Override public void run(String... args) {
        upsert(1,"bronze",0);
        upsert(2,"silver",300);
        upsert(3,"gold",1000);
        upsert(4,"star",2000);
    }
    private void upsert(int level, String name, int req) {
        repo.findById(level).ifPresentOrElse(
                lr -> { lr.setName(name); lr.setRequiredPoints(req); repo.save(lr); },
                () -> { LevelRule lr=new LevelRule();
                    lr.setLevel(level); lr.setName(name); lr.setRequiredPoints(req); repo.save(lr); }
        );
    }
}
