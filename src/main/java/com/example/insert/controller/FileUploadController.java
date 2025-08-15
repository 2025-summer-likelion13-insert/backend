package com.example.insert.controller;

import com.example.insert.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "${cors.allowed-origins}")
@Tag(name = "파일 업로드 API", description = "리뷰용 사진/동영상 파일 업로드 관련 API")
public class FileUploadController {

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Value("${file.upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${file.upload.allowed-types:image/jpeg,image/png,image/gif,video/mp4,video/avi,video/mov}")
    private String allowedTypes;

    /**
     * 단일 파일 업로드
     */
    @PostMapping("/upload")
    @Operation(
            summary = "단일 파일 업로드",
            description = "리뷰용 사진이나 동영상 파일을 업로드합니다. (최대 10MB)"
    )
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @RequestParam("file") 
            @io.swagger.v3.oas.annotations.Parameter(
                    description = "업로드할 파일",
                    required = true,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            type = "string",
                            format = "binary"
                    )
            )
            MultipartFile file) {
        
        System.out.println("=== 파일 업로드 시작 ===");
        System.out.println("파일명: " + file.getOriginalFilename());
        System.out.println("파일 크기: " + file.getSize() + " bytes");
        System.out.println("Content-Type: " + file.getContentType());
        
        try {
            // 파일 검증
            validateFile(file);
            System.out.println("파일 검증 완료");
            
            // 파일 저장
            String fileName = saveFile(file);
            System.out.println("파일 저장 완료: " + fileName);
            
            String fileUrl = "/api/files/download/" + fileName;
            
            System.out.println("=== 파일 업로드 성공 ===");
            return ResponseEntity.ok(ApiResponse.success(fileUrl, "파일이 성공적으로 업로드되었습니다."));
            
        } catch (IllegalArgumentException e) {
            System.err.println("파일 검증 실패: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
                    
        } catch (Exception e) {
            System.err.println("파일 업로드 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 다중 파일 업로드
     */
    @PostMapping("/upload-multiple")
    @Operation(
            summary = "다중 파일 업로드",
            description = "여러 개의 리뷰용 사진이나 동영상 파일을 업로드합니다. (최대 20개, 각각 최대 10MB)"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "업로드할 파일들",
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "multipart/form-data"
            )
    )
    public ResponseEntity<ApiResponse<List<String>>> uploadMultipleFiles(
            @RequestParam("files") 
            @io.swagger.v3.oas.annotations.Parameter(
                    description = "업로드할 파일들 (최대 20개)",
                    required = true
            )
            MultipartFile[] files) {
        
        if (files.length > 20) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("최대 20개까지만 업로드 가능합니다."));
        }
        
        List<String> fileUrls = new ArrayList<>();
        
        try {
            for (MultipartFile file : files) {
                // 파일 검증
                validateFile(file);
                
                // 파일 저장
                String fileName = saveFile(file);
                String fileUrl = "/api/files/download/" + fileName;
                fileUrls.add(fileUrl);
            }
            
            return ResponseEntity.ok(ApiResponse.success(fileUrls, "모든 파일이 성공적으로 업로드되었습니다."));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("파일 업로드 중 오류가 발생했습니다."));
        }
    }

    /**
     * 파일 다운로드
     */
    @GetMapping("/download/{fileName}")
    @Operation(
            summary = "파일 다운로드",
            description = "업로드된 파일을 다운로드합니다."
    )
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        
        try {
            Path filePath = Paths.get(uploadPath).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                String contentType = determineContentType(fileName);
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 파일 검증
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }
        
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("파일 크기는 최대 10MB까지 가능합니다.");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. (지원: JPEG, PNG, GIF, MP4, AVI, MOV)");
        }
    }

    /**
     * 파일 저장
     */
    private String saveFile(MultipartFile file) throws IOException {
        System.out.println("=== 파일 저장 시작 ===");
        
        // 업로드 디렉토리 생성
        Path uploadDir = Paths.get(uploadPath);
        System.out.println("업로드 디렉토리 경로: " + uploadDir.toAbsolutePath());
        
        if (!Files.exists(uploadDir)) {
            System.out.println("업로드 디렉토리 생성 중...");
            Files.createDirectories(uploadDir);
            System.out.println("업로드 디렉토리 생성 완료");
        }
        
        // 고유한 파일명 생성 (UUID + 원본 확장자)
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String fileName = UUID.randomUUID().toString() + extension;
        System.out.println("생성된 파일명: " + fileName);
        
        Path filePath = uploadDir.resolve(fileName);
        System.out.println("최종 파일 경로: " + filePath.toAbsolutePath());
        
        // 파일 저장
        System.out.println("파일 복사 시작...");
        Files.copy(file.getInputStream(), filePath);
        System.out.println("파일 복사 완료");
        
        return fileName;
    }

    /**
     * 파일 확장자에 따른 Content-Type 결정
     */
    private String determineContentType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (fileName.endsWith(".avi")) {
            return "video/avi";
        } else if (fileName.endsWith(".mov")) {
            return "video/mov";
        } else {
            return "application/octet-stream";
        }
    }
}
