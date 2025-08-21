package com.example.insert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Tag(name = "테스트 API", description = "Swagger 작동 테스트용")
public class TestController {

    @GetMapping("/hello")
    @Operation(summary = "Hello World 테스트", description = "Swagger가 정상 작동하는지 테스트")
    public String hello() {
        return "Hello World! Swagger is working!";
    }
}
