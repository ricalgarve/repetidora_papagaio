package com.papagaio.repetidora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ForegroundService principal da Repetidora Papagaio (Dust Coyotes).
 *
 * Coordena:
 *  - AudioMonitor   → Leitura contínua com detecção de voz, silêncio para corte rápido e envio de bargraph
 *  - Normalização   → Normaliza o pico para 95% e aplica boost para acionar com firmeza o VOX do rádio
 *  - Retransmissão  → Toca imediatamente o áudio normalizado
 *  - PeriodicPlayer → Reproduz áudio programado em intervalos periódicos
 *  - HistoryDatabase→ Salva histórico e executa auto-purge por dias
 *
 * Mantém um WakeLock parcial para funcionamento ininterrupto com a tela bloqueada.
 */
public class RepetidoraService extends Service {

    private static final String TAG = "RepetidoraService";

    public static final String CHANNEL_ID       = "RepetidoraChannel";
    public static final int    NOTIFICATION_ID  = 42;

    public static final String ACTION_START = "com.papagaio.START";
    public static final String ACTION_STOP  = "com.papagaio.STOP";

    public static final String ACTION_STATUS_UPDATE = "com.papagaio.STATUS";
    public static final String EXTRA_STATUS_TEXT    = "status_text";
    public static final String EXTRA_AMPLITUDE      = "amplitude";

    private SettingsManager settings;
    private HistoryDatabase historyDb;
    private AudioMonitor    audioMonitor;
    private PeriodicPlayer  periodicPlayer;
    private MediaPlayer     playbackPlayer;
    private AudioTrack      activeAudioTrack;
    private volatile boolean isPlayingTrack = false;
    private PowerManager.WakeLock wakeLock;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (prefs, key) -> {
        if (key == null) return;
        if (SettingsManager.KEY_SENSITIVITY.equals(key)) {
            if (audioMonitor != null) {
                audioMonitor.setThreshold(settings.getAmplitudeThreshold());
            }
        } else if (SettingsManager.KEY_SILENCE_TIMEOUT_MS.equals(key)) {
            if (audioMonitor != null) {
                audioMonitor.setSilenceTimeoutMs(settings.getSilenceTimeoutMs());
            }
        } else if (SettingsManager.KEY_RECORD_DURATION.equals(key)) {
            if (audioMonitor != null) {
                audioMonitor.setMaxDurationSeconds(settings.getRecordDuration());
            }
        } else if (SettingsManager.KEY_ANTI_LOOP_ENABLED.equals(key)) {
            if (audioMonitor != null) {
                audioMonitor.setAntiLoopEnabled(settings.isAntiLoopEnabled());
            }
        } else if (SettingsManager.KEY_MONITOR_ENABLED.equals(key)) {
            boolean enabled = settings.isMonitorEnabled();
            if (enabled && audioMonitor == null) {
                startMonitor();
            } else if (!enabled && audioMonitor != null) {
                audioMonitor.stop();
                audioMonitor = null;
            }
            updateNotification(buildStatusText(settings.isMonitorEnabled(), settings.isPeriodicEnabled()));
        } else if (SettingsManager.KEY_PERIODIC_ENABLED.equals(key) || SettingsManager.KEY_PERIODIC_INTERVAL.equals(key)) {
            if (periodicPlayer != null) {
                periodicPlayer.stop();
                periodicPlayer = null;
            }
            if (settings.isPeriodicEnabled()) {
                startPeriodic();
            }
            updateNotification(buildStatusText(settings.isMonitorEnabled(), settings.isPeriodicEnabled()));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new SettingsManager(this);
        settings.getSharedPreferences().registerOnSharedPreferenceChangeListener(prefListener);
        historyDb = HistoryDatabase.getInstance(this);
        createNotificationChannel();

        // Rotina de expurgo ao iniciar
        new Thread(() -> {
            try {
                int purged = historyDb.purgeOldEntries(settings.getPurgeDays());
                Log.d(TAG, "Expurgo inicial executado: " + purged + " removidos.");
            } catch (Exception e) {
                Log.e(TAG, "Erro no expurgo inicial", e);
            }
        }, "PurgeThread").start();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RepetidoraPapagaio:WakeLock"
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            Log.d(TAG, "ACTION_STOP recebido");
            teardown();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Iniciando…"));

        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
            Log.d(TAG, "WakeLock adquirido");
        }

        settings.setServiceRunning(true);
        setup();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        teardown();
        try {
            settings.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(prefListener);
        } catch (Exception ignored) {}
        settings.setServiceRunning(false);
        super.onDestroy();
        Log.d(TAG, "Serviço destruído");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setup() {
        boolean monitorEnabled  = settings.isMonitorEnabled();
        boolean periodicEnabled = settings.isPeriodicEnabled();

        if (monitorEnabled) {
            startMonitor();
        }

        if (periodicEnabled) {
            startPeriodic();
        }

        String status = buildStatusText(monitorEnabled, periodicEnabled);
        updateNotification(status);
        broadcastStatus(status, -1);
        Log.d(TAG, "Setup concluído: monitor=" + monitorEnabled + " periodic=" + periodicEnabled);
    }

    private void teardown() {
        if (audioMonitor != null) {
            audioMonitor.stop();
            audioMonitor = null;
        }
        if (periodicPlayer != null) {
            periodicPlayer.stop();
            periodicPlayer = null;
        }
        stopPlaybackPlayer();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock liberado");
        }
    }

    private void stopPlaybackPlayer() {
        isPlayingTrack = false;
        if (activeAudioTrack != null) {
            try {
                if (activeAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    activeAudioTrack.stop();
                }
                activeAudioTrack.release();
            } catch (Exception ignored) {}
            activeAudioTrack = null;
        }
        if (playbackPlayer != null) {
            try {
                if (playbackPlayer.isPlaying()) playbackPlayer.stop();
                playbackPlayer.release();
            } catch (Exception ignored) {}
            playbackPlayer = null;
        }
    }

    // ── Monitor Inteligente & Repetidora Rápida ──────────────────────────────

    private void startMonitor() {
        int threshold = settings.getAmplitudeThreshold();
        int silenceTimeoutMs = settings.getSilenceTimeoutMs();
        int maxDurationSec = settings.getRecordDuration();
        boolean antiLoop = settings.isAntiLoopEnabled();

        audioMonitor = new AudioMonitor(threshold, silenceTimeoutMs, maxDurationSec, antiLoop, new AudioMonitor.AudioEngineListener() {
            @Override
            public void onAmplitudeUpdate(int amplitude) {
                // Atualiza o bargraph na interface continuamente
                broadcastStatus(null, amplitude);
            }

            @Override
            public void onRecordingStarted() {
                String msg = "🔴 Gravando áudio do rádio…";
                updateNotification(msg);
                broadcastStatus(msg, -1);
            }

            @Override
            public void onRecordingFinished(byte[] pcmData, int sampleRate) {
                // Fim de transmissão pelo silêncio!
                String msg = "📢 Retransmitindo (VOX)…";
                updateNotification(msg);
                broadcastStatus(msg, -1);

                processAndPlayback(pcmData, sampleRate);
            }

            @Override
            public void onRecordingDiscarded() {
                String msg = "🎙 Monitorando som…";
                updateNotification(msg);
                broadcastStatus(msg, -1);
            }
        });

        audioMonitor.start();
    }

    /**
     * Processa o áudio gravado (normalização de pico + ganho boost) e retransmite de imediato.
     */
    private void processAndPlayback(byte[] pcmData, int sampleRate) {
        new Thread(() -> {
            try {
                boolean normalize = settings.isNormalizeAudio();
                int boost = settings.getOutputBoost();

                // 1. Normalizar e amplificar se configurado
                byte[] processedPcm = WavUtils.processPcm(pcmData, pcmData.length, normalize, boost);

                // 2. Salvar como WAV para reprodução e histórico
                File dir = getExternalFilesDir("Gravacoes");
                if (dir == null) dir = new File(getFilesDir(), "Gravacoes");
                if (!dir.exists()) dir.mkdirs();

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File wavFile = new File(dir, "RPT_" + ts + ".wav");
                WavUtils.saveWav(wavFile, processedPcm, processedPcm.length, sampleRate, 1);

                int durationSec = Math.max(1, (int) Math.round((double) processedPcm.length / (sampleRate * 2)));

                // Registrar no histórico
                historyDb.addEntry(new HistoryEntry(
                    System.currentTimeMillis(),
                    "REPETICAO_AUDIO",
                    "Gravação Papagaio (" + wavFile.getName() + ")",
                    wavFile.getAbsolutePath(),
                    durationSec,
                    "Retransmitida (VOX)"
                ));

                // 3. Reproduzir imediatamente sensibilizando o bargraph em tempo real
                playPcmToRadioWithBargraph(processedPcm, sampleRate);

            } catch (Exception e) {
                Log.e(TAG, "Falha ao processar e salvar áudio", e);
                if (audioMonitor != null) audioMonitor.setPlaybackActive(false);
                broadcastStatus("🎙 Monitorando som…", 0);
            }
        }, "ProcessAndPlay").start();
    }

    /**
     * Reproduz o áudio PCM normalizado diretamente no rádio (AudioTrack)
     * e alimenta o Bargraph da interface em tempo real a cada 50ms (Modulação TX).
     */
    private void playPcmToRadioWithBargraph(byte[] pcmData, int sampleRate) {
        try {
            stopPlaybackPlayer();

            if (audioMonitor != null) {
                audioMonitor.setPlaybackActive(true);
            }

            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            int bufferSize = Math.max(minBufSize, 4096);

            AudioTrack track;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                track = new AudioTrack(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                );
            } else {
                track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                );
            }

            activeAudioTrack = track;
            isPlayingTrack = true;

            track.play();
            Log.d(TAG, "AudioTrack ativo. Retransmitindo áudio e modulando bargraph em tempo real.");

            // Bloco de 50ms em 16kHz Mono 16-bit = 1600 bytes
            int chunkSize = Math.max(320, (sampleRate * 2 * 50) / 1000);
            int offset = 0;

            while (offset < pcmData.length && isPlayingTrack) {
                int length = Math.min(chunkSize, pcmData.length - offset);
                track.write(pcmData, offset, length);

                // Calcular amplitude deste trecho para alimentar o Bargraph da UI
                int numShorts = length / 2;
                long sum = 0;
                for (int i = 0; i < numShorts; i++) {
                    int low = pcmData[offset + i * 2] & 0xFF;
                    int high = pcmData[offset + i * 2 + 1];
                    short s = (short) ((high << 8) | low);
                    sum += (long) s * s;
                }
                int rms = (numShorts > 0) ? (int) Math.sqrt((double) sum / numShorts) : 0;

                broadcastStatus(null, rms);
                offset += length;
            }

            // Aguarda esvaziamento do buffer de áudio
            if (isPlayingTrack) {
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro durante reprodução via AudioTrack", e);
        } finally {
            try {
                if (activeAudioTrack != null) {
                    if (activeAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        activeAudioTrack.stop();
                    }
                    activeAudioTrack.release();
                }
            } catch (Exception ignored) {}
            activeAudioTrack = null;
            isPlayingTrack = false;

            if (audioMonitor != null) {
                audioMonitor.setPlaybackActive(false);
            }

            String msg = "🎙 Monitorando som…";
            updateNotification(msg);
            broadcastStatus(msg, 0);
            Log.d(TAG, "Retransmissão finalizada com sucesso. Retornando para escuta.");
        }
    }

    // ── Reprodução periódica ─────────────────────────────────────────────────

    private void startPeriodic() {
        String uri = settings.getPeriodicUri();
        if (uri.isEmpty()) {
            Log.w(TAG, "Nenhum áudio periódico configurado");
            return;
        }
        periodicPlayer = new PeriodicPlayer(this);
        periodicPlayer.setListener(new PeriodicPlayer.PlaybackListener() {
            @Override
            public void onPlaybackStarted() {
                broadcastStatus("📢 Transmitindo áudio periódico…", -1);
                historyDb.addEntry(new HistoryEntry(
                    System.currentTimeMillis(),
                    "TRANSMISSAO_PERIODICA",
                    "Áudio Periódico Programado",
                    uri,
                    0,
                    "Transmitido no Rádio"
                ));
            }

            @Override
            public void onPlaybackFinished() {
                broadcastStatus("🎙 Monitorando som…", -1);
            }

            @Override
            public void onPlaybackError(String reason) {
                Log.e(TAG, "Erro player periódico: " + reason);
            }
        });
        periodicPlayer.start(uri, settings.getPeriodicInterval());
    }

    // ── Notificação ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Repetidora Papagaio",
                NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Serviço de repetidora em background (silencioso)");
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setVibrationPattern(new long[]{0});
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String statusText) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stopIntent = new Intent(this, RepetidoraService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦜 Repetidora Papagaio")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(new long[]{0})
            .setOnlyAlertOnce(true) // Impede vibrações ou alertas sonoros a cada troca de estado
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Parar", stopPending)
            .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(status));
    }

    // ── Broadcast para a UI ──────────────────────────────────────────────────

    private void broadcastStatus(String text, int amplitude) {
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        if (text != null)    intent.putExtra(EXTRA_STATUS_TEXT, text);
        if (amplitude >= 0)  intent.putExtra(EXTRA_AMPLITUDE, amplitude);
        sendBroadcast(intent);
    }

    private String buildStatusText(boolean monitor, boolean periodic) {
        if (monitor && periodic)   return "🎙 Monitorando + 📢 Periódico ativo";
        if (monitor)               return "🎙 Monitorando som…";
        if (periodic)              return "📢 Reprodução periódica ativa";
        return "⚡ Serviço ativo";
    }
}
