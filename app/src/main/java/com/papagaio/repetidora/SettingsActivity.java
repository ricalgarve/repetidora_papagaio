package com.papagaio.repetidora;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela de configurações avançadas da Repetidora Papagaio:
 *  - Sensibilidade do microfone (0–100 %)
 *  - Corte rápido por silêncio (200–3000 ms)
 *  - Normalização de pico (ligada/desligada)
 *  - Ganho de saída / Boost VOX (50–250 %)
 *  - Duração máxima de segurança (5–120 s)
 *  - Intervalo de transmissão periódica (1–240 min)
 *  - Expurgo do histórico (1–30 dias)
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;

    // Sensibilidade
    private SeekBar  seekSensitivity;
    private TextView tvSensitivityValue;

    // Silêncio (corte rápido)
    private SeekBar  seekSilence;
    private TextView tvSilenceValue;

    // Normalização e Boost
    private Switch   switchNormalize;
    private SeekBar  seekBoost;
    private TextView tvBoostValue;

    // Duração máxima
    private SeekBar  seekDuration;
    private TextView tvDurationValue;

    // Intervalo Periódico
    private SeekBar  seekInterval;
    private TextView tvIntervalValue;

    // Expurgo
    private SeekBar  seekPurgeDays;
    private TextView tvPurgeDaysValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("⚙  Ajustes da Repetidora");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        settings = new SettingsManager(this);

        setupSensitivity();
        setupSilenceTimeout();
        setupNormalizationAndBoost();
        setupMaxDuration();
        setupPeriodicInterval();
        setupPurgeDays();
    }

    private void setupSensitivity() {
        seekSensitivity   = findViewById(R.id.seek_sensitivity);
        tvSensitivityValue= findViewById(R.id.tv_sensitivity_value);

        seekSensitivity.setMax(100);
        seekSensitivity.setProgress(settings.getSensitivity());
        updateSensitivityLabel(settings.getSensitivity());

        seekSensitivity.setOnSeekBarChangeListener(simpleListener((progress, fromUser) -> {
            settings.setSensitivity(progress);
            updateSensitivityLabel(progress);
        }));
    }

    private void setupSilenceTimeout() {
        seekSilence   = findViewById(R.id.seek_silence);
        tvSilenceValue= findViewById(R.id.tv_silence_value);

        // 200 ms a 3000 ms (step de 50ms) -> 0 a 56
        int currentMs = settings.getSilenceTimeoutMs();
        int progress = (currentMs - 200) / 50;
        seekSilence.setMax(56);
        seekSilence.setProgress(Math.max(0, Math.min(56, progress)));
        updateSilenceLabel(currentMs);

        seekSilence.setOnSeekBarChangeListener(simpleListener((p, fromUser) -> {
            int ms = 200 + (p * 50);
            settings.setSilenceTimeoutMs(ms);
            updateSilenceLabel(ms);
        }));
    }

    private void setupNormalizationAndBoost() {
        switchNormalize = findViewById(R.id.switch_normalize);
        seekBoost       = findViewById(R.id.seek_boost);
        tvBoostValue    = findViewById(R.id.tv_boost_value);

        switchNormalize.setChecked(settings.isNormalizeAudio());
        switchNormalize.setOnCheckedChangeListener((v, isChecked) -> settings.setNormalizeAudio(isChecked));

        // Boost de 50% a 250% (step de 5%) -> 0 a 40
        int currentBoost = settings.getOutputBoost();
        int progress = (currentBoost - 50) / 5;
        seekBoost.setMax(40);
        seekBoost.setProgress(Math.max(0, Math.min(40, progress)));
        updateBoostLabel(currentBoost);

        seekBoost.setOnSeekBarChangeListener(simpleListener((p, fromUser) -> {
            int boost = 50 + (p * 5);
            settings.setOutputBoost(boost);
            updateBoostLabel(boost);
        }));
    }

    private void setupMaxDuration() {
        seekDuration   = findViewById(R.id.seek_duration);
        tvDurationValue= findViewById(R.id.tv_duration_value);

        seekDuration.setMax(115);
        seekDuration.setProgress(settings.getRecordDuration() - 5);
        updateDurationLabel(settings.getRecordDuration());

        seekDuration.setOnSeekBarChangeListener(simpleListener((progress, fromUser) -> {
            int secs = progress + 5;
            settings.setRecordDuration(secs);
            updateDurationLabel(secs);
        }));
    }

    private void setupPeriodicInterval() {
        seekInterval   = findViewById(R.id.seek_interval);
        tvIntervalValue= findViewById(R.id.tv_interval_value);

        seekInterval.setMax(239);
        seekInterval.setProgress(settings.getPeriodicInterval() - 1);
        updateIntervalLabel(settings.getPeriodicInterval());

        seekInterval.setOnSeekBarChangeListener(simpleListener((progress, fromUser) -> {
            int mins = progress + 1;
            settings.setPeriodicInterval(mins);
            updateIntervalLabel(mins);
        }));
    }

    private void setupPurgeDays() {
        seekPurgeDays   = findViewById(R.id.seek_purge_days);
        tvPurgeDaysValue= findViewById(R.id.tv_purge_days_value);

        seekPurgeDays.setMax(29);
        seekPurgeDays.setProgress(settings.getPurgeDays() - 1);
        updatePurgeDaysLabel(settings.getPurgeDays());

        seekPurgeDays.setOnSeekBarChangeListener(simpleListener((progress, fromUser) -> {
            int days = progress + 1;
            settings.setPurgeDays(days);
            updatePurgeDaysLabel(days);
        }));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ── Labels ───────────────────────────────────────────────────────────────

    private void updateSensitivityLabel(int value) {
        String desc;
        if      (value >= 80) desc = "Muito Alta — detecta até ruído leve";
        else if (value >= 60) desc = "Alta — áudio baixo do rádio";
        else if (value >= 40) desc = "Média (Padrão) — modulação ideal";
        else if (value >= 20) desc = "Baixa — precisa de áudio bem alto";
        else                  desc = "Muito Baixa — apenas som muito forte";
        tvSensitivityValue.setText(value + "%  —  " + desc);
    }

    private void updateSilenceLabel(int ms) {
        String rec = (ms == 800) ? " (Recomendado para Rádio)" : "";
        if (ms < 500) {
            tvSilenceValue.setText(ms + " ms — Ultra Rápido (quase sem pausa)" + rec);
        } else if (ms <= 1200) {
            tvSilenceValue.setText(ms + " ms — Rápido & Fluido" + rec);
        } else {
            tvSilenceValue.setText(ms + " ms — Pausa mais longa");
        }
    }

    private void updateBoostLabel(int boost) {
        if (boost == 100) {
            tvBoostValue.setText("100% (Ganho Padrão)");
        } else if (boost > 100) {
            tvBoostValue.setText(boost + "% (Boost Amplificado para Acionar VOX)");
        } else {
            tvBoostValue.setText(boost + "% (Volume Atenuado)");
        }
    }

    private void updateDurationLabel(int secs) {
        tvDurationValue.setText(secs + " segundos");
    }

    private void updateIntervalLabel(int mins) {
        if (mins < 60) {
            tvIntervalValue.setText(mins + " minuto" + (mins > 1 ? "s" : ""));
        } else {
            int h = mins / 60;
            int m = mins % 60;
            String txt = h + "h";
            if (m > 0) txt += " " + m + "min";
            tvIntervalValue.setText(txt);
        }
    }

    private void updatePurgeDaysLabel(int days) {
        if (days == 1) {
            tvPurgeDaysValue.setText("1 dia (apaga gravações com mais de 24 horas)");
        } else if (days == 2) {
            tvPurgeDaysValue.setText("2 dias (Padrão — mantém últimos 2 dias)");
        } else {
            tvPurgeDaysValue.setText(days + " dias");
        }
    }

    @FunctionalInterface
    interface SeekCallback {
        void onChange(int progress, boolean fromUser);
    }

    private SeekBar.OnSeekBarChangeListener simpleListener(SeekCallback cb) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { cb.onChange(p, u); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
    }
}
