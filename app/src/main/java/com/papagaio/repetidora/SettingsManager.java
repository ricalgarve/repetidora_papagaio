package com.papagaio.repetidora;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gerencia todas as configurações do app via SharedPreferences.
 */
public class SettingsManager {

    private static final String PREF_NAME = "RepetidoraPrefs";

    // Chaves
    public static final String KEY_SENSITIVITY       = "sensitivity";
    public static final String KEY_RECORD_DURATION   = "record_duration";
    public static final String KEY_PERIODIC_URI      = "periodic_uri";
    public static final String KEY_PERIODIC_INTERVAL = "periodic_interval";
    public static final String KEY_PERIODIC_ENABLED  = "periodic_enabled";
    public static final String KEY_MONITOR_ENABLED   = "monitor_enabled";
    public static final String KEY_SERVICE_RUNNING   = "service_running";
    public static final String KEY_PURGE_DAYS         = "purge_days";
    public static final String KEY_SILENCE_TIMEOUT_MS = "silence_timeout_ms";
    public static final String KEY_NORMALIZE_AUDIO    = "normalize_audio";
    public static final String KEY_OUTPUT_BOOST       = "output_boost";
    public static final String KEY_KEEP_SCREEN_ON     = "keep_screen_on";
    public static final String KEY_ANTI_LOOP_ENABLED  = "anti_loop_enabled";
    public static final String KEY_VOLUME_INITIALIZED  = "volume_initialized";

    // Valores padrão
    public static final int DEFAULT_SENSITIVITY          = 60;   // 0–100 % (60% padrão calibrado para VOX)
    public static final int DEFAULT_RECORD_DURATION      = 10;   // 10 segundos máximos de segurança padrão
    public static final int DEFAULT_PERIODIC_INTERVAL    = 10;   // minutos
    public static final int DEFAULT_PURGE_DAYS           = 2;    // expurgo a cada 2 dias por padrão
    public static final int DEFAULT_SILENCE_TIMEOUT_MS   = 1200; // ms de silêncio para cortar e retransmitir (1200ms padrão)
    public static final boolean DEFAULT_NORMALIZE_AUDIO  = true; // normalização de pico ativada
    public static final int DEFAULT_OUTPUT_BOOST         = 120;  // 120% de ganho para acionar VOX do rádio
    public static final boolean DEFAULT_KEEP_SCREEN_ON   = false;// Tela sempre ativa (desabilitado por padrão)
    public static final boolean DEFAULT_ANTI_LOOP_ENABLED= false;// Anti-looping holdoff (desabilitado por padrão)

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }

    // ── Sensibilidade (0–100) ────────────────────────────────────────────────
    public int getSensitivity() {
        return prefs.getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY);
    }
    public void setSensitivity(int value) {
        prefs.edit().putInt(KEY_SENSITIVITY, value).apply();
    }

    /**
     * Converte sensibilidade (0–100 %) em threshold de amplitude RMS realista.
     * Na escala 16-bit PCM do microfone Android:
     * - Ruído ambiente: RMS 30 a 150
     * - Voz suave / rádio baixo: RMS 400 a 800
     * - Voz normal / rádio padrão: RMS 1000 a 2500
     * - Grito / áudio alto: RMS 5000 a 10000+
     *
     * Mapeamento quadrático calibrado:
     * - Sensibilidade 100%: threshold = 150 (super sensível)
     * - Sensibilidade 80%:  threshold = 410 (alta sensibilidade)
     * - Sensibilidade 60%:  threshold = 1190 (padrão recomendado rádio)
     * - Sensibilidade 50%:  threshold = 1775
     * - Sensibilidade 30%:  threshold = 3330 (ambientes com ruído)
     * - Sensibilidade 0%:   threshold = 6650 (somente som muito forte)
     */
    public int getAmplitudeThreshold() {
        int s = getSensitivity();
        double factor = (100.0 - s) / 100.0;
        return (int) (150 + 6500 * factor * factor);
    }

    // ── Duração da gravação (segundos) ──────────────────────────────────────
    public int getRecordDuration() {
        return prefs.getInt(KEY_RECORD_DURATION, DEFAULT_RECORD_DURATION);
    }
    public void setRecordDuration(int seconds) {
        prefs.edit().putInt(KEY_RECORD_DURATION, seconds).apply();
    }

    // ── URI do áudio periódico ──────────────────────────────────────────────
    public String getPeriodicUri() {
        return prefs.getString(KEY_PERIODIC_URI, "");
    }
    public void setPeriodicUri(String uri) {
        prefs.edit().putString(KEY_PERIODIC_URI, uri).apply();
    }

    // ── Intervalo periódico (minutos) ───────────────────────────────────────
    public int getPeriodicInterval() {
        return prefs.getInt(KEY_PERIODIC_INTERVAL, DEFAULT_PERIODIC_INTERVAL);
    }
    public void setPeriodicInterval(int minutes) {
        prefs.edit().putInt(KEY_PERIODIC_INTERVAL, minutes).apply();
    }

    // ── Flags de ativação ───────────────────────────────────────────────────
    public boolean isPeriodicEnabled() {
        return prefs.getBoolean(KEY_PERIODIC_ENABLED, false);
    }
    public void setPeriodicEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PERIODIC_ENABLED, enabled).apply();
    }

    public boolean isMonitorEnabled() {
        return prefs.getBoolean(KEY_MONITOR_ENABLED, true);
    }
    public void setMonitorEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MONITOR_ENABLED, enabled).apply();
    }

    // ── Estado do serviço (para restaurar UI após morte do processo) ─────────
    public boolean isServiceRunning() {
        return prefs.getBoolean(KEY_SERVICE_RUNNING, false);
    }
    public void setServiceRunning(boolean running) {
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply();
    }

    // ── Dias para expurgo automático de histórico e áudios ─────────────────
    public int getPurgeDays() {
        return prefs.getInt(KEY_PURGE_DAYS, DEFAULT_PURGE_DAYS);
    }
    public void setPurgeDays(int days) {
        prefs.edit().putInt(KEY_PURGE_DAYS, days).apply();
    }

    // ── Silêncio para corte rápido e retransmissão (ms) ──────────────────────
    public int getSilenceTimeoutMs() {
        return prefs.getInt(KEY_SILENCE_TIMEOUT_MS, DEFAULT_SILENCE_TIMEOUT_MS);
    }
    public void setSilenceTimeoutMs(int ms) {
        prefs.edit().putInt(KEY_SILENCE_TIMEOUT_MS, ms).apply();
    }

    // ── Normalização de Volume / Pico (para acionar VOX) ─────────────────────
    public boolean isNormalizeAudio() {
        return prefs.getBoolean(KEY_NORMALIZE_AUDIO, DEFAULT_NORMALIZE_AUDIO);
    }
    public void setNormalizeAudio(boolean normalize) {
        prefs.edit().putBoolean(KEY_NORMALIZE_AUDIO, normalize).apply();
    }

    // ── Ganho / Boost de Saída (50% a 200%) ─────────────────────────────────
    public int getOutputBoost() {
        return prefs.getInt(KEY_OUTPUT_BOOST, DEFAULT_OUTPUT_BOOST);
    }
    public void setOutputBoost(int boostPercent) {
        prefs.edit().putInt(KEY_OUTPUT_BOOST, boostPercent).apply();
    }

    // ── Tela Sempre Ativa (Wake / Keep Screen On) ───────────────────────────
    public boolean isKeepScreenOn() {
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON);
    }
    public void setKeepScreenOn(boolean keepOn) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply();
    }

    // ── Proteção Anti-Looping (Holdoff PTT) ──────────────────────────────────
    public boolean isAntiLoopEnabled() {
        return prefs.getBoolean(KEY_ANTI_LOOP_ENABLED, DEFAULT_ANTI_LOOP_ENABLED);
    }
    public void setAntiLoopEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ANTI_LOOP_ENABLED, enabled).apply();
    }

    // ── Volume Inicializado (60% no primeiro boot) ───────────────────────────
    public boolean isVolumeInitialized() {
        return prefs.getBoolean(KEY_VOLUME_INITIALIZED, false);
    }
    public void setVolumeInitialized(boolean initialized) {
        prefs.edit().putBoolean(KEY_VOLUME_INITIALIZED, initialized).apply();
    }
}
