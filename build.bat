@echo off
chcp 65001 >nul
title 构建工单平台
cd /d "%~dp0"

set "JAVA_HOME=%~dp0..\tools\jdk21"
echo 正在使用工作区自带的 JDK21 + Maven 构建（不依赖系统 Java）...
echo.

call "%~dp0..\tools\maven\bin\mvn.cmd" -q -DskipTests package
if errorlevel 1 (
    echo.
    echo [构建失败] 请把上面的报错信息发给助手排查。
) else (
    echo.
    echo [构建成功] 产物: target\ticket-agent-platform-0.1.0-SNAPSHOT.jar
    echo 现在可以双击 start.bat 启动了。
)
pause
