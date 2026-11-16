package com.papagaio.repetidora;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

/**
 * Reproduz periodicamente um arquivo de áudio com intervalo configurável.
 * Suporta tanto caminhos de arquivo absolutos quanto URIs content://.
 */
public class PeriodicPlayer {

    private static final String TAG = "PeriodicPlayer";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable scheduleRunnable;
    private MediaPlayer mediaPlayer;
    private volatile boolean running = false;

    public interface PlaybackListener {
        void onPlaybackStarted();
        void onPlaybackFinished();
        void onPlaybackError(String reason);
    }

    private PlaybackListener listener;

    public PeriodicPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    /**
     * Inicia o agendamento periódico.
     * @param audioUri  URI (content://) ou caminho absoluto do arquivo de áudio.
     * @param intervalMinutes intervalo em minutos entre reproduções.
     */
    public void start(String audioUri, int intervalMinutes) {
        if (audioUri == null || audioUri.isEmpty()) {
            Log.w(TAG, "Nenhum áudio configurado");
            return;
        }

        running = true;
        long intervalMs = intervalMinutes * 60_000L;

        scheduleRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                playAudio(audioUri);
                handler.postDelayed(this, intervalMs);
            }
        };

        // Primeira reprodução após o intervalo completo
        handler.postDelayed(scheduleRunnable, intervalMs);
        Log.d(TAG, "PeriodicPlayer iniciado. Intervalo=" + intervalMinutes + "min uri=" + audioUri);
    }

    /** Para o agendamento e a reprodução atual. */
    public void stop() {
        running = false;
        if (scheduleRunnable != null) handler.removeCallbacks(scheduleRunnable);
        stopPlayer();
        Log.d(TAG, "PeriodicPlayer parado.");
    }

    private void playAudio(String uriString) {
        stopPlayer();
        try {
            mediaPlayer = new MediaPlayer();

            if (uriString.startsWith("content://")) {
                Uri uri = Uri.parse(uriString);
                mediaPlayer.setDataSource(context, uri);
            } else {
                // Caminho absoluto
                File file = new File(uriString);
                if (!file.exists()) {
                    Log.e(TAG, "Arquivo não encontrado: " + uriString);
                    if (listener != null) listener.onPlaybackError("Arquivo não encontrado");
                    return;
                }
                mediaPlayer.setDataSource(uriString);
            }

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Log.d(TAG, "Reproduzindo: " + uriString);
                if (listener != null) listener.onPlaybackStarted();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Reprodução concluída.");
                if (listener != null) listener.onPlaybackFinished();
                stopPlayer();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Erro na reprodução: what=" + what + " extra=" + extra);
                if (listener != null) listener.onPlaybackError("Erro " + what);
                stopPlayer();
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao configurar MediaPlayer", e);
            if (listener != null) listener.onPlaybackError(e.getMessage());
            stopPlayer();
        }
    }

    private void stopPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}
