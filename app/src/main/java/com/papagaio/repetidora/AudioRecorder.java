package com.papagaio.repetidora;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Grava áudio do microfone por uma duração fixa (em segundos).
 * Salva o arquivo em getExternalFilesDir("Gravacoes") — sem permissão necessária no Android 10+.
 */
public class AudioRecorder {

    private static final String TAG = "AudioRecorder";

    public interface RecordingCallback {
        void onRecordingStarted(String filePath);
        void onRecordingFinished(String filePath);
        void onRecordingError(String error);
    }

    private final Context context;
    private MediaRecorder mediaRecorder;
    private volatile boolean recording = false;
    private RecordingCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AudioRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }

    public boolean isRecording() {
        return recording;
    }

    /**
     * Inicia a gravação em thread separada.
     * @param durationSeconds duração máxima em segundos.
     */
    public void startRecording(int durationSeconds) {
        if (recording) return;

        new Thread(() -> {
            String outputPath = buildOutputPath();
            if (outputPath == null) {
                postError("Não foi possível criar o arquivo de gravação");
                return;
            }

            try {
                mediaRecorder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    ? new MediaRecorder(context)
                    : new MediaRecorder();

                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setAudioSamplingRate(8000);
                mediaRecorder.setAudioEncodingBitRate(12200);
                mediaRecorder.setOutputFile(outputPath);
                mediaRecorder.prepare();
                mediaRecorder.start();
                recording = true;

                final String fp = outputPath;
                mainHandler.post(() -> { if (callback != null) callback.onRecordingStarted(fp); });
                Log.d(TAG, "Gravando por " + durationSeconds + "s → " + fp);

                Thread.sleep(durationSeconds * 1000L);

                stopRecording();
                mainHandler.post(() -> { if (callback != null) callback.onRecordingFinished(fp); });

            } catch (InterruptedException ie) {
                // Parada prematura solicitada externamente
                stopRecording();
                Log.d(TAG, "Gravação interrompida externamente.");
            } catch (Exception e) {
                Log.e(TAG, "Erro na gravação", e);
                recording = false;
                releaseRecorder();
                postError(e.getMessage());
            }
        }, "AudioRecorder").start();
    }

    /** Para a gravação imediatamente (pode ser chamado de qualquer thread). */
    public void stopRecording() {
        if (!recording) return;
        recording = false;
        try {
            mediaRecorder.stop();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao parar o recorder", e);
        }
        releaseRecorder();
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }
    }

    private String buildOutputPath() {
        // Diretório específico do app — não precisa de permissão no Android 10+
        File dir = context.getExternalFilesDir("Gravacoes");
        if (dir == null) dir = new File(context.getFilesDir(), "Gravacoes");
        if (!dir.exists() && !dir.mkdirs()) return null;

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "REC_" + ts + ".3gp").getAbsolutePath();
    }

    private void postError(String msg) {
        mainHandler.post(() -> { if (callback != null) callback.onRecordingError(msg); });
    }
}
