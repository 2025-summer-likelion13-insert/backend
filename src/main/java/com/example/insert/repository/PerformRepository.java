package com.example.insert.repository;

import com.example.insert.entity.Perform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


import org.springframework.data.domain.Sort;


public interface PerformRepository extends JpaRepository<Perform, Long> {

    Optional<Perform> findByExternalId(String externalId);

    List<Perform> findByIsAdTrueOrderByStartDateAsc();

    List<Perform> findByStartDateBetweenOrderByStartDateAsc(LocalDate from, LocalDate to);

    // 제목 부분 일치(대소문자 무시) + 정렬만
    List<Perform> findByTitleContainingIgnoreCase(String q, Sort sort);



}
