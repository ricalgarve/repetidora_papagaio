package com.papagaio.repetidora;

public class HistoryEntry {
    private long id;
    private long timestamp;
    private String type; // "REPETICAO_AUDIO" ou "TRANSMISSAO_PERIODICA"
    private String title;
    private String filePath;
    private int durationSeconds;
    private String status;

    public HistoryEntry(long id, long timestamp, String type, String title, String filePath, int durationSeconds, String status) {
        this.id = id;
        this.timestamp = timestamp;
        this.type = type;
        this.title = title;
        this.filePath = filePath;
        this.durationSeconds = durationSeconds;
        this.status = status;
    }

    public HistoryEntry(long timestamp, String type, String title, String filePath, int durationSeconds, String status) {
        this(-1, timestamp, type, title, filePath, durationSeconds, status);
    }

    public long getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getFilePath() { return filePath; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getStatus() { return status; }
}
