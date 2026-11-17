package com.papagaio.repetidora;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 100;

    private SettingsManager settings;
    private AudioManager audioManager;

    // Views de Status e Controle
    private TextView    tvStatus;
    private Switch      switchMonitor;
    private Switch      switchPeriodic;
    private TextView    tvAudioFile;
    private Button      btnStartStop;
    private Button      btnPickAudio;
    private Button      btnHistory;
    private Button      btnSettings;

    // Controle de Volume do Rádio (Acionamento VOX)
    private SeekBar     seekAppVolume;
    private TextView    tvVolumeValue;

    // Bargraph / VU Meter no Rodapé
    private ProgressBar progressBargraph;
    private TextView    tvVoxTriggerIndicator;
    private TextView    tvBargraphDetails;
    private TextView    tvThresholdMarker;

    private boolean serviceRunning = false;

    // Receiver contínuo para alimentar o Bargraph em tempo real
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String text = intent.getStringExtra(RepetidoraService.EXTRA_STATUS_TEXT);
            int amplitude = intent.getIntExtra(RepetidoraService.EXTRA_AMPLITUDE, -1);

            if (text != null) {
                tvStatus.setText(text);
            }

            if (amplitude >= 0) {
                // Escala perceptiva calibrada para áudio de voz e rádio (0 a ~8000 RMS)
                int pct = 0;
                if (amplitude > 50) {
                    pct = (int) Math.min(100, Math.sqrt((double) amplitude / 8000.0) * 100.0);
                }
                progressBargraph.setProgress(pct);

                int threshold = settings.getAmplitudeThreshold();
                int thresholdPct = (int) Math.min(100, Math.sqrt((double) threshold / 8000.0) * 100.0);

                boolean isTransmitting = (tvStatus.getText() != null) &&
                    (tvStatus.getText().toString().contains("Retransmitindo") || 
                     tvStatus.getText().toString().contains("Transmitindo"));

                if (isTransmitting) {
                    tvVoxTriggerIndicator.setText("● TRANSMITINDO (TX)");
                    tvVoxTriggerIndicator.setTextColor(Color.parseColor("#2563EB")); // Azul
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        progressBargraph.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#2563EB")));
                    }
                    tvBargraphDetails.setText("Modulação TX: " + pct + "% (RMS: " + amplitude + ") | Rádio no Ar");
                } else if (amplitude > threshold) {
                    tvVoxTriggerIndicator.setText("● DISPARANDO VOX");
                    tvVoxTriggerIndicator.setTextColor(Color.parseColor("#DC2626")); // Vermelho
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        progressBargraph.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#DC2626")));
                    }
                    tvBargraphDetails.setText("Sinal: " + pct + "% (RMS: " + amplitude + ") | Gatilho VOX: " + thresholdPct + "% (Sens: " + settings.getSensitivity() + "%)");
                } else if (pct > 50) {
                    tvVoxTriggerIndicator.setText("● ESCUTA (RX)");
                    tvVoxTriggerIndicator.setTextColor(Color.parseColor("#D97706")); // Âmbar
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        progressBargraph.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#D97706")));
                    }
                    tvBargraphDetails.setText("Sinal: " + pct + "% (RMS: " + amplitude + ") | Gatilho VOX: " + thresholdPct + "% (Sens: " + settings.getSensitivity() + "%)");
                } else {
                    tvVoxTriggerIndicator.setText("● ESCUTA (RX)");
                    tvVoxTriggerIndicator.setTextColor(Color.parseColor("#16A34A")); // Verde
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        progressBargraph.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#16A34A")));
                    }
                    tvBargraphDetails.setText("Sinal: " + pct + "% (RMS: " + amplitude + ") | Gatilho VOX: " + thresholdPct + "% (Sens: " + settings.getSensitivity() + "%)");
                }
            }
        }
    };

    private final ActivityResultLauncher<Intent> audioPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    settings.setPeriodicUri(uri.toString());
                    updateAudioFileLabel();
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        bindViews();
        setupVolumeControl();
        restoreUiState();
        setupListeners();
        applyKeepScreenOn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyKeepScreenOn();
        IntentFilter filter = new IntentFilter(RepetidoraService.ACTION_STATUS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }

        switchMonitor.setChecked(settings.isMonitorEnabled());
        switchPeriodic.setChecked(settings.isPeriodicEnabled());
        updateAudioFileLabel();
        updateBargraphThresholdLabel();
        syncSystemVolumeToSeekbar();

        serviceRunning = settings.isServiceRunning();
        updateStartStopButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    private void bindViews() {
        tvStatus              = findViewById(R.id.tv_status);
        switchMonitor         = findViewById(R.id.switch_monitor);
        switchPeriodic        = findViewById(R.id.switch_periodic);
        tvAudioFile           = findViewById(R.id.tv_audio_file);
        btnStartStop          = findViewById(R.id.btn_start_stop);
        btnPickAudio          = findViewById(R.id.btn_pick_audio);
        btnHistory            = findViewById(R.id.btn_history);
        btnSettings           = findViewById(R.id.btn_settings);

        seekAppVolume         = findViewById(R.id.seek_app_volume);
        tvVolumeValue         = findViewById(R.id.tv_volume_value);

        progressBargraph      = findViewById(R.id.progress_bargraph);
        tvVoxTriggerIndicator = findViewById(R.id.tv_vox_trigger_indicator);
        tvBargraphDetails     = findViewById(R.id.tv_bargraph_details);
        tvThresholdMarker     = findViewById(R.id.tv_threshold_marker);
    }

    private void setupVolumeControl() {
        if (audioManager == null) return;
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // Se for primeira execução após instalação, inicializa volume em 60%
        if (!settings.isVolumeInitialized()) {
            int targetVol = (int) Math.round(maxVol * 0.60);
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
            } catch (Exception ignored) {}
            settings.setVolumeInitialized(true);
        }

        int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        seekAppVolume.setMax(maxVol);
        seekAppVolume.setProgress(curVol);
        int pct = (maxVol > 0) ? (curVol * 100 / maxVol) : 60;
        tvVolumeValue.setText(pct + "%");

        seekAppVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    int p = (maxVol > 0) ? (progress * 100 / maxVol) : 0;
                    tvVolumeValue.setText(p + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void syncSystemVolumeToSeekbar() {
        if (audioManager == null || seekAppVolume == null) return;
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        seekAppVolume.setProgress(curVol);
        int pct = (maxVol > 0) ? (curVol * 100 / maxVol) : 100;
        tvVolumeValue.setText(pct + "%");
    }

    private void restoreUiState() {
        switchMonitor.setChecked(settings.isMonitorEnabled());
        switchPeriodic.setChecked(settings.isPeriodicEnabled());
        updateAudioFileLabel();
        updateBargraphThresholdLabel();
        serviceRunning = settings.isServiceRunning();
        updateStartStopButton();
        if (!serviceRunning) {
            tvStatus.setText("Parado");
        }
    }

    private void updateBargraphThresholdLabel() {
        int threshold = settings.getAmplitudeThreshold();
        int thresholdPct = (int) Math.min(100, Math.sqrt((double) threshold / 8000.0) * 100.0);
        tvThresholdMarker.setText("▲ LIMIAR (" + thresholdPct + "%)");
        tvBargraphDetails.setText("Sinal: 0% | Gatilho VOX: " + thresholdPct + "% (Sens: " + settings.getSensitivity() + "%)");
    }

    private void setupListeners() {
        switchMonitor.setOnCheckedChangeListener((v, checked) ->
            settings.setMonitorEnabled(checked));

        switchPeriodic.setOnCheckedChangeListener((v, checked) ->
            settings.setPeriodicEnabled(checked));

        btnStartStop.setOnClickListener(v -> {
            if (serviceRunning) {
                doStop();
            } else {
                if (checkAndRequestPermissions()) doStart();
            }
        });

        btnPickAudio.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            audioPickerLauncher.launch(intent);
        });

        btnHistory.setOnClickListener(v ->
            startActivity(new Intent(this, HistoryActivity.class)));

        btnSettings.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void doStart() {
        Intent intent = new Intent(this, RepetidoraService.class);
        intent.setAction(RepetidoraService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        serviceRunning = true;
        updateStartStopButton();
        tvStatus.setText("Iniciando repetidora…");
    }

    private void doStop() {
        Intent intent = new Intent(this, RepetidoraService.class);
        intent.setAction(RepetidoraService.ACTION_STOP);
        startService(intent);
        serviceRunning = false;
        updateStartStopButton();
        tvStatus.setText("Parado");
        progressBargraph.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBargraph.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#16A34A")));
        }
        tvVoxTriggerIndicator.setText("● AGUARDANDO");
        tvVoxTriggerIndicator.setTextColor(Color.parseColor("#6B7280"));
        updateBargraphThresholdLabel();
    }

    private void updateStartStopButton() {
        btnStartStop.setText(serviceRunning ? "⏹  Parar Repetidora" : "▶  Iniciar Repetidora");
    }

    private boolean checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();

        if (!hasPermission(Manifest.permission.RECORD_AUDIO))
            needed.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO))
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (needed.isEmpty()) return true;

        ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        return false;
    }

    private boolean hasPermission(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERMISSIONS) return;

        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                    "Permissões necessárias não concedidas.",
                    Toast.LENGTH_LONG).show();
                return;
            }
        }
        doStart();
    }

    private void updateAudioFileLabel() {
        String uri = settings.getPeriodicUri();
        if (uri.isEmpty()) {
            tvAudioFile.setText("Nenhum áudio selecionado");
            return;
        }
        String name = getDisplayNameFromUri(Uri.parse(uri));
        tvAudioFile.setText("🎵  " + (name != null ? name : uri));
    }

    private String getDisplayNameFromUri(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        String path = uri.getPath();
        if (path != null && path.contains("/")) {
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return null;
    }

    private void applyKeepScreenOn() {
        if (settings != null && settings.isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
