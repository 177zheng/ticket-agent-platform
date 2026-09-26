@echo off
chcp 65001 >nul
title 企业工单多智能体处理平台
cd /d "%~dp0"

if not exist "target\ticket-agent-platform-0.1.0-SNAPSHOT.jar" (
    echo [错误] 未找到程序包，请先双击 build.bat 完成构建。
    pause
    exit /b 1
)

echo ============================================
echo   企业工单多智能体处理平台 启动中...
echo   启动完成后，浏览器访问: http://127.0.0.1:8080
echo   关闭本窗口 = 停止服务
echo   若提示端口被占用，说明已有一个实例在运行，直接用浏览器访问即可
echo ============================================
echo.

"..\tools\jdk21\bin\java.exe" -Dfile.encoding=UTF-8 -jar target\ticket-agent-platform-0.1.0-SNAPSHOT.jar

pause
