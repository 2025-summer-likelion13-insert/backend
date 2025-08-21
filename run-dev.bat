@echo off
echo ========================================
echo Insert Backend - 개발 환경 실행
echo ========================================
echo.
echo H2 파일 기반 데이터베이스로 실행합니다.
echo 데이터가 유지됩니다.
echo.
echo H2 콘솔: http://localhost:8080/h2-console
echo JDBC URL: jdbc:h2:file:./data/insert_dev
echo Username: sa
echo Password: (비어있음)
echo.
echo ========================================
echo.

gradlew bootRun

pause
