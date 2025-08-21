package com.example.insert.controller;

import com.example.insert.dto.LikeDtos.*;
import com.example.insert.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/likes/perform")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /** 토글 */
    @PostMapping("/toggle")
    public ResponseEntity<ToggleLikeResponse> toggle(@RequestBody ToggleLikeRequest req) {
        return ResponseEntity.ok(likeService.toggleByExternalId(req.externalId()));
    }

    /** 내 찜 목록 */
    @GetMapping
    public ResponseEntity<MyLikesResponse> myLikes(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(likeService.listMyLikes(offset, limit));
    }

    /** 다건 여부 */
    @PostMapping("/has-many")
    public ResponseEntity<HasManyResponse> hasMany(@RequestBody HasManyRequest req) {
        return ResponseEntity.ok(likeService.hasManyByExternalIds(req.externalIds()));
    }
}
