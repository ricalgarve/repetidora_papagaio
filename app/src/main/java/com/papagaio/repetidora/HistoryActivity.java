package com.papagaio.repetidora;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private HistoryDatabase historyDb;
    private SettingsManager settings;
    private HistoryAdapter adapter;
    private final List<HistoryEntry> entries = new ArrayList<>();

    private TextView tvPurgeInfo;
    private TextView tvEmpty;
    private RecyclerView recyclerView;
    private Button btnPurgeNow;
    private Button btnClearAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📜 Histórico de Envios");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        historyDb = HistoryDatabase.getInstance(this);
        settings = new SettingsManager(this);

        tvPurgeInfo = findViewById(R.id.tv_purge_info);
        tvEmpty = findViewById(R.id.tv_empty_history);
        recyclerView = findViewById(R.id.recycler_history);
        btnPurgeNow = findViewById(R.id.btn_purge_now);
        btnClearAll = findViewById(R.id.btn_clear_all_history);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(this, entries);
        recyclerView.setAdapter(adapter);

        updatePurgeInfo();
        loadHistory();

        btnPurgeNow.setOnClickListener(v -> {
            int days = settings.getPurgeDays();
            int deleted = historyDb.purgeOldEntries(days);
            Toast.makeText(this, "Expurgo manual: " + deleted + " registros removidos (> " + days + " dias)", Toast.LENGTH_SHORT).show();
            loadHistory();
        });

        btnClearAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Limpar Histórico")
                    .setMessage("Deseja apagar todos os registros do histórico?")
                    .setPositiveButton("Sim, apagar", (dialog, which) -> {
                        historyDb.clearAll();
                        loadHistory();
                        Toast.makeText(this, "Histórico limpo.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }

    private void updatePurgeInfo() {
        int days = settings.getPurgeDays();
        if (days > 0) {
            tvPurgeInfo.setText("🧹 Expurgo automático ativo: a cada " + days + " dia" + (days > 1 ? "s" : ""));
        } else {
            tvPurgeInfo.setText("🧹 Expurgo automático desativado");
        }
    }

    private void loadHistory() {
        entries.clear();
        entries.addAll(historyDb.getAllEntries());
        adapter.notifyDataSetChanged();

        if (entries.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) {
            adapter.releasePlayer();
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
