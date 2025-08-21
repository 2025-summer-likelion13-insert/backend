package com.example.insert.service;

import com.example.insert.dto.LikeItem;
import com.example.insert.dto.LikePageResponse;
import com.example.insert.dto.LikeResponse;
import com.example.insert.entity.Like;
import com.example.insert.entity.Perform;
import com.example.insert.entity.User;
import com.example.insert.repository.LikeRepository;
import com.example.insert.repository.PerformRepository;
import com.example.insert.repository.UserRepository;
import com.example.insert.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepo;
    private final PerformRepository performRepo;
    private final UserRepository userRepo;
    private final CurrentUser current;

    private Perform findPerformOr404(String externalId) {
        return performRepo.findByExternalId(externalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PERFORM_NOT_FOUND: " + externalId));
    }

    private long countByPerform(Perform p) {
        return likeRepo.countByPerformId(p.getId());
    }

    /** 멱등 ON: 항상 liked=true 보장 */
    @Transactional
    public LikeResponse on(String externalId) {
        Long uid = current.idOrThrow();
        Perform p = findPerformOr404(externalId);

        var existing = likeRepo.findByUserIdAndPerformId(uid, p.getId());
        if (existing.isPresent()) {
            return new LikeResponse(externalId, true, countByPerform(p));
        }
        try {
            User userRef = userRepo.getReferenceById(uid);
            likeRepo.save(Like.builder().user(userRef).perform(p).build());
        } catch (DataIntegrityViolationException e) {
            log.debug("likeOn race: {}", e.getMessage()); // 경합 시 최종상태 true면 OK
        }
        return new LikeResponse(externalId, true, countByPerform(p));
    }

    /** 멱등 OFF: 항상 liked=false 보장 */
    @Transactional
    public LikeResponse off(String externalId) {
        Long uid = current.idOrThrow();
        Perform p = findPerformOr404(externalId);

        var existing = likeRepo.findByUserIdAndPerformId(uid, p.getId());
        if (existing.isEmpty()) {
            return new LikeResponse(externalId, false, countByPerform(p));
        }
        likeRepo.delete(existing.get());
        return new LikeResponse(externalId, false, countByPerform(p));
    }

    /** (호환) 토글 — 필요시 제거 */
    @Transactional
    public LikeResponse toggle(String externalId) {
        Long uid = current.idOrThrow();
        Perform p = findPerformOr404(externalId);

        var existing = likeRepo.findByUserIdAndPerformId(uid, p.getId());
        if (existing.isPresent()) {
            likeRepo.delete(existing.get());
            return new LikeResponse(externalId, false, countByPerform(p));
        }
        try {
            User userRef = userRepo.getReferenceById(uid);
            likeRepo.save(Like.builder().user(userRef).perform(p).build());
        } catch (DataIntegrityViolationException e) {
            log.debug("toggle race: {}", e.getMessage());
            // 경합 시 최종상태 true 보장
        }
        return new LikeResponse(externalId, true, countByPerform(p));
    }

    @Transactional(readOnly = true)
    public LikePageResponse myLikes(int page, int size) {
        Long uid = current.idOrThrow();
        var pg = likeRepo.findByUserIdOrderByCreatedAtDesc(uid, PageRequest.of(page, size));
        var items = pg.getContent().stream()
                .map(l -> new LikeItem(l.getPerform().getExternalId(), l.getCreatedAt()))
                .toList();
        return new LikePageResponse(items, pg.getNumber(), pg.getSize(), pg.getTotalElements(), pg.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String externalId) {
        Long uid = current.idOrThrow();
        var p = findPerformOr404(externalId);
        boolean liked = likeRepo.findByUserIdAndPerformId(uid, p.getId()).isPresent();
        return Map.of("externalId", externalId, "liked", liked);
    }
}
