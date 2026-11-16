#!/usr/bin/env bash
# ==============================================================================
#  Repetidora Papagaio (Dust Coyotes) - Build & Servidor de APK no Linux / macOS
# ==============================================================================

set -e
cd "$(dirname "$0")"

# Configurar JDK e Android SDK se existirem no sistema
if [ -d "/home/familia/jdk/jdk-17.0.12+7" ]; then
    export JAVA_HOME="/home/familia/jdk/jdk-17.0.12+7"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -d "/home/familia/android-sdk" ]; then
    export ANDROID_HOME="/home/familia/android-sdk"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi

echo "=============================================================================="
echo "       REPETIDORA PAPAGAIO (DUST COYOTES) - BUILD & SERVIDOR APK             "
echo "=============================================================================="
echo ""

# 1. Gerar gradlew se não existir
if [ ! -f "gradlew" ]; then
    if command -v gradle >/dev/null 2>&1; then
        echo "[*] Gerando Gradle Wrapper..."
        gradle wrapper --gradle-version 8.6
        chmod +x gradlew
    fi
fi

# 2. Compilar APK se gradlew existir
if [ -f "gradlew" ]; then
    echo "[*] Compilando APK Android (assembleDebug)..."
    chmod +x gradlew
    ./gradlew assembleDebug || {
        echo "[AVISO] Falha ao compilar com gradle."
        if [ ! -f "RepetidoraPapagaio.apk" ] && [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
            echo "[ERRO] Nenhum APK encontrado para servir."
            exit 1
        fi
    }

    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        cp -f "app/build/outputs/apk/debug/app-debug.apk" "RepetidoraPapagaio.apk"
        echo "[*] APK gerado em: RepetidoraPapagaio.apk"
    fi
fi

echo ""
echo "=============================================================================="
echo "[*] Iniciando Servidor Wi-Fi na Rede Local..."
echo "=============================================================================="
echo ""

python3 serve_apk.py
