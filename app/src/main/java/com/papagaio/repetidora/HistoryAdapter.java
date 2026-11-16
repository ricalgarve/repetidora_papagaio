package com.papagaio.repetidora;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final Context context;
    private final List<HistoryEntry> list;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
    private MediaPlayer mediaPlayer;

    public HistoryAdapter(Context context, List<HistoryEntry> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryEntry entry = list.get(position);

        // Badge / Tipo
        if ("REPETICAO_AUDIO".equals(entry.getType())) {
            holder.tvBadge.setText("GRAVAÇÃO PAPAGAIO");
        } else {
            holder.tvBadge.setText("TRANSMISSÃO PERIÓDICA");
        }

        // Data / Hora
        holder.tvDate.setText(dateFormat.format(new Date(entry.getTimestamp())));

        // Título
        holder.tvTitle.setText(entry.getTitle());

        // Detalhes
        String details = "Status: " + entry.getStatus();
        if (entry.getDurationSeconds() > 0) {
            details = "Duração: " + entry.getDurationSeconds() + "s • " + details;
        }
        holder.tvDetails.setText(details);

        // Botão reproduzir
        String path = entry.getFilePath();
        if (path != null && !path.isEmpty()) {
            holder.btnPlay.setVisibility(View.VISIBLE);
            holder.btnPlay.setOnClickListener(v -> playAudio(path));
        } else {
            holder.btnPlay.setVisibility(View.GONE);
        }
    }

    private void playAudio(String path) {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();
            if (path.startsWith("content://")) {
                mediaPlayer.setDataSource(context, Uri.parse(path));
            } else {
                File f = new File(path);
                if (!f.exists()) {
                    Toast.makeText(context, "Arquivo não encontrado (pode ter sido expurgado)", Toast.LENGTH_SHORT).show();
                    return;
                }
                mediaPlayer.setDataSource(path);
            }

            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });
            mediaPlayer.prepareAsync();
            Toast.makeText(context, "Reproduzindo áudio...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(context, "Erro ao reproduzir: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge;
        TextView tvDate;
        TextView tvTitle;
        TextView tvDetails;
        Button btnPlay;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tv_item_badge);
            tvDate = itemView.findViewById(R.id.tv_item_date);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            tvDetails = itemView.findViewById(R.id.tv_item_details);
            btnPlay = itemView.findViewById(R.id.btn_item_play);
        }
    }
}
