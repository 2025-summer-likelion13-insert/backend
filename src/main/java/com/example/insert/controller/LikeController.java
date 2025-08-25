package com.example.insert.controller;

import com.example.insert.dto.LikePageResponse;
import com.example.insert.dto.LikeResponse;
import com.example.insert.dto.ToggleRequest;
import com.example.insert.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/likes/perform")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /** 멱등 ON: 추가 */
    @PutMapping("/{externalId}")
    public ResponseEntity<LikeResponse> likeOn(@PathVariable String externalId) {
        return ResponseEntity.ok(likeService.on(externalId));
    }

    /** 멱등 OFF: 삭제 */
    @DeleteMapping("/{externalId}")
    public ResponseEntity<LikeResponse> likeOff(@PathVariable String externalId) {
        return ResponseEntity.ok(likeService.off(externalId));
    }

    /** (호환) 토글 — 추후 제거 가능 */
    @PostMapping("/toggle")
    @Deprecated
    public ResponseEntity<LikeResponse> toggle(@RequestBody ToggleRequest req) {
        return ResponseEntity.ok(likeService.toggle(req.externalId()));
    }

    /** 내 찜 목록 (페이징) */
    @GetMapping("/me")
    public ResponseEntity<LikePageResponse> myLikes(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(likeService.myLikes(page, size));
    }

    /** 단건 상태 조회 */
    @GetMapping("/{externalId}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String externalId) {
        return ResponseEntity.ok(likeService.status(externalId));
    }
}
