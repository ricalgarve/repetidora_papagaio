# 🦜 Repetidora Papagaio — Android App

App Android escrito em **Java puro**, sem SDKs de rastreamento, que funciona com **tela bloqueada**.

---

## ✨ Funcionalidades

| Função | Detalhe |
|---|---|
| 🎙 Monitor de Som | Detecta som via microfone e grava automaticamente |
| 🎚 Sensibilidade configurável | Slider 0–100 % (define o threshold de amplitude) |
| ⚡ Corte Rápido por Silêncio | **Configurável de 200 a 3000 ms (padrão 800ms)**: ao soltar o PTT do rádio, já corta e retransmite de imediato |
| 🔊 Normalização de Volume | Eleva o pico do áudio a 95% para garantir acionamento do circuito VOX do rádio |
| 🚀 Ganho / Boost VOX | Ajuste de 50% a 250% na saída para rádios que exigem sinal mais forte |
| 📊 Bargraph (VU Meter) no Rodapé | Barra dinâmica com gradiente verde/amarelo/vermelho e indicador visual do limiar VOX |
| 🔊 Controle de Volume no App | Slider de volume de saída integrado direto na tela principal |
| ⏱ Duração máxima de segurança | 5 a 120 segundos (corte por timeout se squelch travar aberto) |
| 📢 Reprodução periódica | Toca um áudio (beacon/identificador) a cada N minutos/horas |
| 🔁 Intervalo configurável | 1 minuto a 4 horas |
| 📱 Tela bloqueada | ForegroundService + WakeLock parcial |
| 🔔 Notificação persistente | Com botão "Parar" direto da barra |
| 📜 Histórico de transmissões | Registra cada áudio captado e transmissão periódica |
| 🧹 Expurgo automático | **2 dias por padrão** (configurável de 1 a 30 dias), apaga logs e gravações antigas |

---

## 📁 Estrutura do Projeto

```
RepetidoraPapagaio/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/papagaio/repetidora/
│       │   ├── MainActivity.java        ← Tela principal
│       │   ├── SettingsActivity.java    ← Configurações (sensibilidade, tempos, expurgo)
│       │   ├── HistoryActivity.java     ← Visualização e gerenciamento do histórico
│       │   ├── HistoryAdapter.java      ← Adaptador da lista de transmissões
│       │   ├── HistoryDatabase.java     ← SQLite nativo com auto-purge por dias
│       │   ├── HistoryEntry.java        ← Modelo de registro de histórico
│       │   ├── RepetidoraService.java   ← ForegroundService coordenador + parrot playback
│       │   ├── AudioMonitor.java        ← Monitoramento contínuo do microfone
│       │   ├── AudioRecorder.java       ← Gravação por duração fixa
│       │   ├── PeriodicPlayer.java      ← Reprodução periódica
│       │   └── SettingsManager.java     ← SharedPreferences wrapper
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_settings.xml
│           │   ├── activity_history.xml
│           │   └── item_history.xml
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── themes.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 🔨 Como Compilar

### Pré-requisitos
- **Android Studio** Hedgehog (2023.1.1) ou superior  
- **JDK 17** (já instalado no sistema: `java -version`)
- SDK Android 34 instalado no Android Studio

### Passos
1. Abra o **Android Studio**
2. `File → Open` → selecione a pasta `RepetidoraPapagaio/`
3. Aguarde o **Gradle Sync** terminar
4. Conecte o celular com **Depuração USB** ativada
5. Clique em **▶ Run** (ou `Shift+F10`)

### Compilar e Compartilhar via Wi-Fi (Automático)

#### No Windows:
Basta dar dois cliques em:
```cmd
build.bat
```
Ele irá:
1. Compilar o APK (`assembleDebug`) com Gradle.
2. Copiar o APK gerado para `RepetidoraPapagaio.apk`.
3. Iniciar o servidor HTTP local na rede Wi-Fi exibindo o endereço IP e um **QR Code** no terminal para você escanear com o celular ou abrir no navegador de outra máquina.

#### No Linux / macOS:
```bash
./build.sh
```

#### Apenas iniciar o servidor de download (se o APK já foi compilado):
```bash
python3 serve_apk.py
# ou no Windows:
python serve_apk.py
```

---

### Compilar manualmente via linha de comando
```bash
cd RepetidoraPapagaio
# Gerar o wrapper do Gradle (apenas na primeira vez):
gradle wrapper --gradle-version 8.6
# Compilar debug:
./gradlew assembleDebug
# APK gerado em: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Permissões necessárias

| Permissão | Motivo |
|---|---|
| `RECORD_AUDIO` | Microfone para monitoramento e gravação |
| `FOREGROUND_SERVICE` | Executar em background |
| `FOREGROUND_SERVICE_MICROPHONE` | Exigido Android 14+ para mic em foreground |
| `WAKE_LOCK` | Manter CPU ativa com tela desligada |
| `READ_MEDIA_AUDIO` | Selecionar áudio periódico (Android 13+) |
| `POST_NOTIFICATIONS` | Notificação persistente (Android 13+) |

---

## 📂 Onde ficam as gravações?

As gravações são salvas em:
```
/sdcard/Android/data/com.papagaio.repetidora/files/Gravacoes/
```
Formato: `REC_YYYYMMDD_HHMMSS.3gp`

> Não precisa de permissão de escrita no Android 10+.

---

## 🔒 Privacidade

- **Zero rastreadores** — sem Firebase, Analytics, Admob, etc.
- **Zero conexão de rede** — o app não faz nenhuma chamada HTTP
- Dados ficam apenas no dispositivo

---

## ⚙️ Configurações disponíveis

| Configuração | Faixa | Padrão |
|---|---|---|
| Sensibilidade do microfone | 0–100 % | 40 % |
| Corte Rápido por Silêncio | 200–3.000 ms | 800 ms (Rápido) |
| Normalização de Pico | Ligada / Desligada | Ligada (95% máx) |
| Ganho de Saída / Boost VOX | 50–250 % | 120% (Potência para rádio) |
| Duração máxima de segurança | 5–120 s | 30 s |
| Intervalo de reprodução | 1–240 min | 10 min |
| Expurgo do Histórico | 1–30 dias | 2 dias |
