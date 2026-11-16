package com.papagaio.repetidora;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Monitor e Gravador de Áudio Inteligente com Detecção de Fim de Fala (Silêncio).
 *
 * Funcionamento:
 *  1. Lê o microfone continuamente e envia a amplitude para o Bargraph da UI em tempo real.
 *  2. Ao detectar som acima do threshold, inicia a gravação na memória.
 *  3. Enquanto houver fala, mantém a gravação ativa.
 *  4. Ao detectar silêncio contínuo por 'silenceTimeoutMs' (ex: 800ms) ou atingir a duração máxima,
 *     encerra a gravação instantaneamente e entrega os dados PCM para normalização e retransmissão rápida.
 */
public class AudioMonitor implements Runnable {

    private static final String TAG = "AudioMonitor";
    public static final int SAMPLE_RATE  = 16000; // 16 kHz Mono - padrão ideal para rádio comunicação
    public static final int CHANNEL      = AudioFormat.CHANNEL_IN_MONO;
    public static final int ENCODING     = AudioFormat.ENCODING_PCM_16BIT;

    public interface AudioEngineListener {
        void onAmplitudeUpdate(int amplitude);
        void onRecordingStarted();
        void onRecordingFinished(byte[] pcmData, int sampleRate);
        void onRecordingDiscarded(); // se foi apenas um estalo (< 350ms)
    }

    private volatile int threshold;
    private volatile int silenceTimeoutMs;
    private volatile int maxDurationSeconds;
    private final AudioEngineListener listener;

    private volatile boolean running = false;
    private volatile boolean playbackActive = false;
    private Thread monitorThread;
    private AudioRecord audioRecord;

    private enum State { IDLE, RECORDING, PLAYING }
    private volatile State state = State.IDLE;

    private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream(64 * 1024);
    private long recordingStartTime = 0;
    private long lastSoundDetectedTime = 0;
    private long lastAmplitudeBroadcastTime = 0;

    public AudioMonitor(int threshold, int silenceTimeoutMs, int maxDurationSeconds, AudioEngineListener listener) {
        this.threshold = threshold;
        this.silenceTimeoutMs = Math.max(200, silenceTimeoutMs);
        this.maxDurationSeconds = Math.max(5, maxDurationSeconds);
        this.listener = listener;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
        Log.d(TAG, "Threshold atualizado para: " + threshold);
    }

    public void setSilenceTimeoutMs(int silenceTimeoutMs) {
        this.silenceTimeoutMs = Math.max(200, silenceTimeoutMs);
        Log.d(TAG, "Silence timeout atualizado para: " + this.silenceTimeoutMs + "ms");
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = Math.max(5, maxDurationSeconds);
    }

    public void start() {
        if (running) return;
        running = true;
        monitorThread = new Thread(this, "SmartAudioEngine");
        monitorThread.setPriority(Thread.MAX_PRIORITY);
        monitorThread.start();
        Log.d(TAG, "Iniciado. Threshold=" + threshold + ", Silêncio=" + silenceTimeoutMs + "ms");
    }

    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
        releaseRecord();
        Log.d(TAG, "Parado.");
    }

    public void setPlaybackActive(boolean active) {
        this.playbackActive = active;
        if (active) {
            state = State.PLAYING;
        } else {
            // Pequeno atraso para não captar cauda da própria transmissão
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            pcmBuffer.reset();
            state = State.IDLE;
        }
    }

    @Override
    public void run() {
        int bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (bufSize <= 0) bufSize = 2048;

        // MIC padrão é a fonte mais limpa e universal para áudio de rádio/fone sem corte de frequência DSP
        int[] preferredSources = new int[]{
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT
        };

        for (int src : preferredSources) {
            try {
                audioRecord = new AudioRecord(src, SAMPLE_RATE, CHANNEL, ENCODING, bufSize * 2);
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "AudioRecord inicializado com sucesso usando source=" + src);
                    break;
                } else {
                    audioRecord.release();
                    audioRecord = null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Tentativa de source " + src + " falhou", e);
                if (audioRecord != null) {
                    try { audioRecord.release(); } catch (Exception ignored) {}
                    audioRecord = null;
                }
            }
        }

        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Falha crítica: Não foi possível inicializar AudioRecord em nenhum source.");
            running = false;
            return;
        }

        audioRecord.startRecording();
        short[] shortBuffer = new short[bufSize / 2];
        byte[] byteBuffer = new byte[bufSize];
        Log.d(TAG, "AudioRecord ativo e gravando na taxa de 16000 Hz Mono.");

        while (running) {
            int shortsRead = audioRecord.read(shortBuffer, 0, shortBuffer.length);
            if (shortsRead <= 0) {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                continue;
            }

            // Converter para bytes (little-endian)
            for (int i = 0; i < shortsRead; i++) {
                byteBuffer[i * 2] = (byte) (shortBuffer[i] & 0xFF);
                byteBuffer[i * 2 + 1] = (byte) ((shortBuffer[i] >> 8) & 0xFF);
            }
            int bytesRead = shortsRead * 2;

            int amplitude = calculateRMS(shortBuffer, shortsRead);
            long now = System.currentTimeMillis();

            // Atualiza amplitude na UI em intervalos regulares (~50ms)
            if (now - lastAmplitudeBroadcastTime > 50) {
                lastAmplitudeBroadcastTime = now;
                if (listener != null) {
                    listener.onAmplitudeUpdate(amplitude);
                }
            }

            if (playbackActive) {
                continue;
            }

            if (state == State.IDLE) {
                if (amplitude > threshold) {
                    state = State.RECORDING;
                    recordingStartTime = now;
                    lastSoundDetectedTime = now;
                    pcmBuffer.reset();
                    pcmBuffer.write(byteBuffer, 0, bytesRead);
                    Log.d(TAG, "Iniciando gravação VOX! Amplitude=" + amplitude);
                    if (listener != null) listener.onRecordingStarted();
                }
            } else if (state == State.RECORDING) {
                pcmBuffer.write(byteBuffer, 0, bytesRead);

                if (amplitude > threshold) {
                    lastSoundDetectedTime = now;
                }

                long recordedDurationMs = now - recordingStartTime;
                long silenceDurationMs = now - lastSoundDetectedTime;

                // Condição de parada: silêncio de X ms OU duração máxima atingida
                boolean stoppedBySilence = (silenceDurationMs >= silenceTimeoutMs && recordedDurationMs > 400);
                boolean stoppedByMaxTime = (recordedDurationMs >= (maxDurationSeconds * 1000L));

                if (stoppedBySilence || stoppedByMaxTime) {
                    Log.d(TAG, "Fim da transmissão! Motivo: " + (stoppedBySilence ? "Silêncio (" + silenceDurationMs + "ms)" : "Tempo Máximo"));
                    state = State.PLAYING;

                    if (recordedDurationMs < 350) {
                        // Ruído muito curto descartado
                        pcmBuffer.reset();
                        state = State.IDLE;
                        if (listener != null) listener.onRecordingDiscarded();
                    } else {
                        byte[] finalData = pcmBuffer.toByteArray();
                        pcmBuffer.reset();
                        if (listener != null) {
                            listener.onRecordingFinished(finalData, SAMPLE_RATE);
                        }
                    }
                }
            }
        }

        releaseRecord();
    }

    private int calculateRMS(short[] buffer, int count) {
        if (count <= 0) return 0;
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += (long) buffer[i] * buffer[i];
        }
        return (int) Math.sqrt((double) sum / count);
    }

    private void releaseRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao liberar AudioRecord", e);
            }
            audioRecord = null;
        }
    }
}
