@echo off
chcp 65001 >nul
title 工单平台（MySQL版）
cd /d "%~dp0"

if not exist "target\ticket-agent-platform-0.1.0-SNAPSHOT.jar" (
    echo [错误] 未找到程序包，请先双击 build.bat 完成构建。
    pause
    exit /b 1
)

echo ============================================================
echo   企业工单多智能体处理平台（MySQL版）启动中...
echo   数据库: 本机 MySQL 的 ticketdb 库（root）
echo   启动后访问: http://127.0.0.1:8080
echo   注意: 需要本机 MySQL 服务已启动（3306端口）
echo   关闭本窗口 = 停止服务
echo ============================================================
echo.

"..\tools\jdk21\bin\java.exe" -Dfile.encoding=UTF-8 -jar target\ticket-agent-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=mysql

pause
