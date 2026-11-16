package com.papagaio.repetidora;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Banco de dados SQLite local para armazenar o histórico de mensagens/transmissões.
 * Inclui rotina de expurgo automático (auto purge) configurável por dias.
 */
public class HistoryDatabase extends SQLiteOpenHelper {

    private static final String TAG = "HistoryDatabase";
    private static final String DATABASE_NAME = "repetidora_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";
    public static final String COL_ID = "id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_TYPE = "type";
    public static final String COL_TITLE = "title";
    public static final String COL_FILE_PATH = "file_path";
    public static final String COL_DURATION = "duration_seconds";
    public static final String COL_STATUS = "status";

    private static HistoryDatabase instance;

    public static synchronized HistoryDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private HistoryDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_HISTORY + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_TITLE + " TEXT, "
                + COL_FILE_PATH + " TEXT, "
                + COL_DURATION + " INTEGER, "
                + COL_STATUS + " TEXT"
                + ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    /**
     * Registra um novo envio/transmissão no histórico.
     */
    public synchronized long addEntry(HistoryEntry entry) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, entry.getTimestamp());
        cv.put(COL_TYPE, entry.getType());
        cv.put(COL_TITLE, entry.getTitle());
        cv.put(COL_FILE_PATH, entry.getFilePath());
        cv.put(COL_DURATION, entry.getDurationSeconds());
        cv.put(COL_STATUS, entry.getStatus());

        long id = db.insert(TABLE_HISTORY, null, cv);
        Log.d(TAG, "Registro inserido ID: " + id + " - " + entry.getTitle());
        return id;
    }

    /**
     * Retorna todos os registros ordenados do mais recente para o mais antigo.
     */
    public synchronized List<HistoryEntry> getAllEntries() {
        List<HistoryEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_HISTORY + " ORDER BY " + COL_TIMESTAMP + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COL_ID);
                int timeIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
                int typeIdx = cursor.getColumnIndexOrThrow(COL_TYPE);
                int titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE);
                int pathIdx = cursor.getColumnIndexOrThrow(COL_FILE_PATH);
                int durIdx = cursor.getColumnIndexOrThrow(COL_DURATION);
                int statIdx = cursor.getColumnIndexOrThrow(COL_STATUS);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIdx);
                    long timestamp = cursor.getLong(timeIdx);
                    String type = cursor.getString(typeIdx);
                    String title = cursor.getString(titleIdx);
                    String filePath = cursor.getString(pathIdx);
                    int duration = cursor.getInt(durIdx);
                    String status = cursor.getString(statIdx);

                    list.add(new HistoryEntry(id, timestamp, type, title, filePath, duration, status));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * Executa o expurgo de registros com mais de 'days' dias.
     * Também remove os arquivos gravados associados se aplicável.
     * @param days Limite em dias (ex: 2). Se <= 0, o expurgo automático fica desativado.
     * @return Quantidade de registros expurgados.
     */
    public synchronized int purgeOldEntries(int days) {
        if (days <= 0) {
            Log.d(TAG, "Expurgo desativado (dias <= 0)");
            return 0;
        }

        long cutoffTimestamp = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        SQLiteDatabase db = getWritableDatabase();

        // 1. Procurar arquivos associados para excluir do armazenamento
        String selectQuery = "SELECT " + COL_FILE_PATH + " FROM " + TABLE_HISTORY + " WHERE " + COL_TIMESTAMP + " < ?";
        Cursor cursor = db.rawQuery(selectQuery, new String[]{String.valueOf(cutoffTimestamp)});
        int filesDeleted = 0;

        if (cursor != null) {
            try {
                int pathIdx = cursor.getColumnIndexOrThrow(COL_FILE_PATH);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(pathIdx);
                    if (path != null && !path.isEmpty()) {
                        File f = new File(path);
                        // Excluir apenas se estiver na pasta interna/privada de gravações do app
                        if (f.exists() && f.isFile() && path.contains("Gravacoes")) {
                            if (f.delete()) {
                                filesDeleted++;
                            }
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }

        // 2. Deletar do banco de dados
        int deletedRows = db.delete(TABLE_HISTORY, COL_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTimestamp)});
        Log.i(TAG, "Expurgo concluído: " + deletedRows + " registros e " + filesDeleted + " arquivos apagados (mais de " + days + " dias).");
        return deletedRows;
    }

    /**
     * Limpa todo o histórico manualmente.
     */
    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
    }
}
