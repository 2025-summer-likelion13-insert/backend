package com.example.insert.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    
    @GetMapping("/")
    public String home() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>InSert - AI 기반 장소 추천 시스템</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; background-color: #f5f5f5; }
                    .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    h1 { color: #333; text-align: center; }
                    .status { background: #e8f5e8; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #4caf50; }
                    .api-info { background: #e3f2fd; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #2196f3; }
                    .endpoint { background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 3px; font-family: monospace; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🚀 InSert - AI 기반 장소 추천 시스템</h1>
                    
                    <div class="status">
                        <h3>✅ 애플리케이션 상태</h3>
                        <p>백엔드 서버가 정상적으로 실행되고 있습니다!</p>
                        <p><strong>포트:</strong> 8080</p>
                        <p><strong>상태:</strong> 정상 실행 중</p>
                    </div>
                    
                    <div class="api-info">
                        <h3>🔗 사용 가능한 API 엔드포인트</h3>
                        
                        <h4>장소 추천 API</h4>
                        <div class="endpoint">POST /api/recommendations/places</div>
                        <div class="endpoint">GET /api/recommendations/places/{placeId}</div>
                        
                        <h4>사용자 일정 관리 API</h4>
                        <div class="endpoint">POST /api/schedules/places</div>
                        <div class="endpoint">GET /api/schedules/users/{userId}/events/{eventId}</div>
                        <div class="endpoint">DELETE /api/schedules/places/{scheduleId}</div>
                        <div class="endpoint">PUT /api/schedules/places/{scheduleId}/visit</div>
                        

                        <h4>이벤트 관리 API</h4>
                        <div class="endpoint">POST /api/events</div>
                        <div class="endpoint">GET /api/events</div>
                        <div class="endpoint">GET /api/events/{eventId}</div>
                        <div class="endpoint">GET /api/events/users/{userId}</div>
                        <div class="endpoint">GET /api/events/category/{category}</div>
                        <div class="endpoint">GET /api/events/search?name={name}</div>
                        <div class="endpoint">GET /api/events/external/{externalId}</div>
                        <div class="endpoint">GET /api/events/users/{userId}/external/{externalId}</div>
                        <div class="endpoint">PUT /api/events/{eventId}</div>
                        <div class="endpoint">DELETE /api/events/{eventId}</div>
                        

                        <h4>데이터베이스</h4>
                        <div class="endpoint">H2 Console: <a href="/h2-console" target="_blank">/h2-console</a></div>
                    </div>
                    
                    <div class="status">
                        <h3>🎯 주요 기능</h3>
                        <ul>
                            <li>AI 기반 맞춤형 장소 추천</li>
                            <li>사용자 프로필별 추천 (혼자/커플/가족)</li>
                            <li>이동수단 고려한 장소 추천</li>
                            <li>사용자 일정 관리</li>
                            <li>카테고리별 추천 (엑티비티, 식사, 카페)</li>
                        </ul>
                    </div>
                </div>
            </body>
            </html>
            """;
    }
}
