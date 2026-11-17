# Estudo e Implementação: Proteção Anti-Looping e Inibição de Squelch Tail (Holdoff)

Este documento registra a pesquisa, análise técnica e o código desenvolvido para resolver o fenômeno de **auto-disparo / recebimento de áudio residual logo após a transmissão da repetidora** (*Parrot Repeater Self-Triggering Loop*).

---

## 1. Descrição do Fenômeno

Em uma repetidora papagaio simplex (um único rádio ou celular conectado via cabo/acústica):
1. O usuário transmite via rádio (*PTT* pressionado) falando "Teste 1, 2, 3".
2. O aplicativo detecta áudio no microfone, grava em memória e encerra ao notar silêncio.
3. O aplicativo inicia a reprodução do áudio para o rádio retransmitir (acionando o VOX do rádio).
4. O áudio do celular termina de tocar.
5. **O problema:** Logo em seguida, o aplicativo volta a indicar *"🔴 Gravando áudio..."* ou reproduz de 1 a 2 segundos de chiado/ruído branco, gerando um eco indesejado.

---

## 2. Causa Raiz Técnica

### A. VOX Hang Time / Delay Interno do Rádio
* A maioria dos transceptores (Baofeng UV-5R/UV-82, TYT, Motorola, Quansheng) possui no menu interno a opção **VOX Delay** (geralmente ajustada entre 0.5s e 2.0s).
* Quando o celular para de injetar áudio no conector de microfone do rádio, o circuito VOX do rádio **não desliga o PTT imediatamente**; ele mantém a transmissão no ar por mais 500ms a 1500ms para evitar que pausas entre palavras fiquem picotando.

### B. Estouro de Squelch (*Squelch Tail Burst*)
* No instante exato em que o rádio transmissor finalmente solta o PTT e comuta os diodos PIN/relé interno de TX para RX, ocorre um transiente elétrico e um estouro de chiado aberto no alto-falante/fone do rádio (*"CHHH-CLIK"*).
* Se o microfone do celular for reativado antes do rádio estabilizar em repouso, o celular capta esse estalo/chiado com amplitude alta (RMS > 2.000). O algoritmo VOX do aplicativo interpreta esse chiado como uma nova fala e inicia uma gravação espúria.

### C. Acoplamento Acústico e Linha de Terra (RF Feedback)
* Cabos PTT/MIC comuns de 3.5mm sem transformador de áudio 1:1 (isolador de linha 600Ω) ou sem divisor de tensão resistivo (atenuação de ~40dB de nível de linha para nível de microfone) transmitem indução de RF e ruídos de retorno para a entrada de gravação.

---

## 3. Algoritmos Desenvolvidos para a Solução

Três mecanismos combinados foram projetados:

### I. Máquina de Estados com `HOLDOFF` (Inibição Pós-TX)
Em vez de alternar diretamente de `PLAYING` para `IDLE`, insere-se um estado intermediário de quarentena (`HOLDOFF`):
* **Tempo Mínimo Obrigatório:** 1.500 ms de descarte total incondicional (para aguardar o VOX do rádio desarmar).
* **Verificação de Canal Livre (*Quiet Confirmation*):** Após os 1.500 ms, o monitor exige que a amplitude RMS permaneça abaixo do limiar (`amplitude < threshold`) por pelo menos **300 ms contínuos** antes de autorizar o retorno para `IDLE` (Escuta).
* **Timeout de Segurança:** 4.000 ms máximos para evitar travamento em ambientes com ruído constante.

### II. Corte da Cauda de Silêncio/Chiado (*Squelch Tail Trimming*)
Durante a gravação inicial, o tempo decorrido entre a última fala detectada e o disparo do corte por silêncio (ex: 800 ms) continha ruído branco captado na espera.
* **Corte:** `trimMs = silenceDurationMs - 80ms`.
* Os bytes correspondentes aos últimos 720 ms de chiado eram descartados do array PCM antes da gravação do WAV.

### III. Suavização Linear (*Fade-In e Fade-Out*)
* Fade-in de 15 ms e fade-out de 30 ms aplicados matematicamente nas amostras para evitar *pops* ou estalos de transiente DC.

---

## 4. Código-Fonte de Referência para Reativação Futura

### A. Em `AudioMonitor.java`

```java
public class AudioMonitor implements Runnable {

    private enum State { IDLE, RECORDING, PLAYING, HOLDOFF }
    private volatile State state = State.IDLE;
    private volatile long holdoffStartTime = 0;
    private volatile long quietSinceTime = 0;

    public void setPlaybackActive(boolean active) {
        this.playbackActive = active;
        if (active) {
            state = State.PLAYING;
            holdoffStartTime = 0;
            quietSinceTime = 0;
        } else {
            // Inicia o período de segurança após soltar o PTT do rádio
            holdoffStartTime = System.currentTimeMillis();
            quietSinceTime = 0;
            pcmBuffer.reset();
            state = State.HOLDOFF;
        }
    }

    @Override
    public void run() {
        // ... inicialização do AudioRecord ...
        while (running) {
            int shortsRead = audioRecord.read(shortBuffer, 0, shortBuffer.length);
            if (shortsRead <= 0) {
                try { Thread.sleep(20); } catch (Exception ignored) {}
                continue;
            }

            int amplitude = calculateRMS(shortBuffer, shortsRead);
            long now = System.currentTimeMillis();

            if (playbackActive) {
                continue; // Descarta enquanto estiver tocando
            }

            if (state == State.HOLDOFF) {
                long elapsed = now - holdoffStartTime;
                if (elapsed >= 1500) { // 1.5 segundos mínimos
                    if (amplitude < threshold) {
                        if (quietSinceTime == 0) {
                            quietSinceTime = now;
                        } else if (now - quietSinceTime >= 300) {
                            // Canal confirmado limpo e sem chiado de squelch!
                            pcmBuffer.reset();
                            state = State.IDLE;
                        }
                    } else {
                        quietSinceTime = 0;
                        if (elapsed >= 4000) { // Timeout de segurança
                            pcmBuffer.reset();
                            state = State.IDLE;
                        }
                    }
                }
                continue;
            }

            if (state == State.IDLE) {
                if (amplitude > threshold) {
                    state = State.RECORDING;
                    recordingStartTime = now;
                    lastSoundDetectedTime = now;
                    pcmBuffer.reset();
                    pcmBuffer.write(byteBuffer, 0, bytesRead);
                    if (listener != null) listener.onRecordingStarted();
                }
            } else if (state == State.RECORDING) {
                pcmBuffer.write(byteBuffer, 0, bytesRead);
                if (amplitude > threshold) {
                    lastSoundDetectedTime = now;
                }

                long recordedDurationMs = now - recordingStartTime;
                long silenceDurationMs = now - lastSoundDetectedTime;
                boolean stoppedBySilence = (silenceDurationMs >= silenceTimeoutMs && recordedDurationMs > 400);

                if (stoppedBySilence) {
                    state = State.PLAYING;
                    playbackActive = true;

                    byte[] allData = pcmBuffer.toByteArray();
                    pcmBuffer.reset();

                    // Corte cirúrgico da cauda de espera
                    byte[] finalData = allData;
                    if (silenceDurationMs > 100) {
                        long trimMs = silenceDurationMs - 80;
                        int bytesToTrim = (int) (trimMs * (SAMPLE_RATE * 2 / 1000));
                        int newLength = allData.length - bytesToTrim;
                        if (newLength % 2 != 0) newLength--;
                        if (newLength >= 12800 && newLength < allData.length) {
                            finalData = new byte[newLength];
                            System.arraycopy(allData, 0, finalData, 0, newLength);
                        }
                    }

                    applyFadeIn(finalData, SAMPLE_RATE, 15);
                    applyFadeOut(finalData, SAMPLE_RATE, 30);

                    if (listener != null) {
                        listener.onRecordingFinished(finalData, SAMPLE_RATE);
                    }
                }
            }
        }
    }
}
```

### B. Em `RepetidoraService.java`
```java
// Notificar o AudioMonitor ao iniciar e finalizar playback
if (audioMonitor != null) {
    audioMonitor.setPlaybackActive(true);
}
// Ao terminar reprodução:
if (audioMonitor != null) {
    audioMonitor.setPlaybackActive(false);
}
```

---

## 5. Recomendações Práticas de Hardware e Rádio

1. **Ajuste de VOX no Rádio:**
   * Reduzir o parâmetro **VOX-D (VOX Delay)** no menu do rádio transmissor para o menor valor possível (ex: `0.5s` ou `1`), para que ele solte o PTT o mais rápido possível após o áudio cessar.
2. **Ajuste de SQL (Squelch) no Rádio:**
   * Ajustar o squelch do rádio receptor para um nível firme (nível 4 ou 5) para que o fechamento da portadora seja instantâneo e não gere cauda ruidosa longa.
3. **Cabo de Interface (Atenuação de Áudio):**
   * A saída do celular (fone) tem ~1V RMS. A entrada de microfone do rádio espera ~10mV RMS.
   * Recomenda-se um divisor resistivo (ex: resistor de 10kΩ em série e 1kΩ para o terra) no cabo de transmissão para evitar sobremodulação e disparo permanente do VOX do rádio.
