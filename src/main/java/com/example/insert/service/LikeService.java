package com.example.insert.service;

import com.example.insert.dto.LikeDtos.*;
import com.example.insert.entity.Like;
import com.example.insert.entity.Perform;
import com.example.insert.repository.LikeRepository;
import com.example.insert.repository.PerformRepository;
import com.example.insert.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepo;
    private final PerformRepository performRepo;
    private final CurrentUser current;

    /** externalId 기준 토글 */
    @Transactional
    public ToggleLikeResponse toggleByExternalId(String externalId) {
        log.info("[LIKE] toggle req externalId={} userId={}", externalId, current.idOrThrow());
        if (externalId == null || externalId.isBlank())
            throw new IllegalArgumentException("externalId required");

        Long userId = current.idOrThrow();


        // A안: 404로 명확히
        var p = performRepo.findByExternalId(externalId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "perform not found: " + externalId));


        return likeRepo.findByUserIdAndPerformId(userId, p.getId())
                .map(existing -> { // 있으면 삭제
                    likeRepo.delete(existing);
                    long cnt = likeRepo.countByPerformId(p.getId());
                    return new ToggleLikeResponse(false, null, cnt);
                })
                .orElseGet(() -> { // 없으면 생성
                    Like l = Like.builder().user(existingUserRef(userId))
                            .perform(p)
                            .build();
                    Like saved = likeRepo.save(l);
                    long cnt = likeRepo.countByPerformId(p.getId());
                    return new ToggleLikeResponse(true, saved.getId(), cnt);
                });
    }

    /** 내 찜 목록 페이징 */
    @Transactional(readOnly = true)
    public MyLikesResponse listMyLikes(int offset, int limit) {
        Long userId = current.idOrThrow();
        int off = Math.max(0, offset);
        int lim = (limit <= 0 || limit > 100) ? 20 : limit;

        var page = likeRepo.findByUserIdFetchPerform(userId, PageRequest.of(off / lim, lim));
        var items = page.getContent().stream().map(l -> new LikedPerformItem(
                l.getPerform().getExternalId(),
                l.getPerform().getTitle(),
                l.getPerform().getPosterUrl(),
                // createdAt(LocalDateTime) → OffsetDateTime (UTC)
                l.getCreatedAt() == null ? null : l.getCreatedAt().atOffset(ZoneOffset.UTC)
        )).toList();

        int next = off + items.size();
        return new MyLikesResponse(items, next, page.getTotalElements());
    }

    /** 여러 externalId의 찜 여부 */
    @Transactional(readOnly = true)
    public HasManyResponse hasManyByExternalIds(List<String> externalIds) {
        Long userId = current.idOrThrow();
        if (externalIds == null || externalIds.isEmpty())
            return new HasManyResponse(Collections.emptyMap());

        var liked = new HashSet<>(likeRepo.findLikedExternalIds(userId, externalIds));
        Map<String, Boolean> map = externalIds.stream()
                .collect(Collectors.toMap(e -> e, liked::contains, (a,b)->a, LinkedHashMap::new));
        return new HasManyResponse(map);
    }

    /** user 참조 프록시 생성(불필요한 SELECT 방지) */
    private com.example.insert.entity.User existingUserRef(Long id) {
        var u = new com.example.insert.entity.User();
        u.setId(id);
        return u;
    }
}
