@echo off
setlocal enabledelayedexpansion
title Repetidora Papagaio - Compilacao e Servidor Wi-Fi

cd /d "%~dp0"

echo ==============================================================================
echo        REPETIDORA PAPAGAIO (DUST COYOTES) - BUILD & SERVIDOR APK
echo ==============================================================================
echo.

REM 1. Verificar se o gradlew.bat existe
if not exist "gradlew.bat" (
    echo [*] Gerando o Gradle Wrapper...
    where gradle >nul 2>nul
    if !errorlevel! equ 0 (
        call gradle wrapper --gradle-version 8.6
    ) else (
        echo [AVISO] 'gradle' nao encontrado no PATH do Windows.
        echo Tentando prosseguir se o APK ja existir...
    )
)

REM 2. Compilar o APK se o gradlew estiver disponivel
if exist "gradlew.bat" (
    echo [*] Compilando APK Android (assembleDebug)...
    echo.
    call gradlew.bat assembleDebug
    if !errorlevel! neq 0 (
        echo.
        echo [ALERTA] Falha na compilacao com Gradle.
        if exist "RepetidoraPapagaio.apk" (
            echo Um APK compilado anteriormente foi encontrado e sera compartilhado.
        ) else if exist "app\build\outputs\apk\debug\app-debug.apk" (
            echo APK debug anterior encontrado.
        ) else (
            echo [ERRO] Nenhum APK disponivel para servir. Verifique os erros acima.
            pause
            exit /b 1
        )
    ) else (
        echo.
        echo [SUCESSO] APK compilado com sucesso!
        if exist "app\build\outputs\apk\debug\app-debug.apk" (
            copy /y "app\build\outputs\apk\debug\app-debug.apk" "RepetidoraPapagaio.apk" >nul
            echo [*] APK copiado para: RepetidoraPapagaio.apk
        )
    )
) else (
    echo [*] Pulando etapa do gradlew. Usando APK existente.
)

echo.
echo ==============================================================================
echo [*] Iniciando Servidor de Download Wi-Fi / Rede Local...
echo ==============================================================================
echo.

REM 3. Iniciar o servidor Python
where python >nul 2>nul
if !errorlevel! equ 0 (
    python serve_apk.py
) else (
    where py >nul 2>nul
    if !errorlevel! equ 0 (
        py -3 serve_apk.py
    ) else (
        echo [ERRO] Python nao foi encontrado no sistema!
        echo Instale o Python (https://www.python.org) e marque 'Add to PATH'.
        pause
        exit /b 1
    )
)

pause
