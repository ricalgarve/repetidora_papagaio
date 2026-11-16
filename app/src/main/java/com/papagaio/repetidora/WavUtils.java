package com.papagaio.repetidora;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utilitário para salvar e normalizar arquivos WAV PCM 16-bit Mono.
 */
public class WavUtils {

    /**
     * Normaliza e aplica boost no array de amostras PCM 16-bit.
     * @param pcmData dados em bytes (little-endian 16-bit)
     * @param length quantidade de bytes válidos
     * @param normalize se deve normalizar o pico
     * @param boostPercent ganho adicional (ex: 100 = 1.0x, 150 = 1.5x)
     * @return novo byte[] processado
     */
    public static byte[] processPcm(byte[] pcmData, int length, boolean normalize, int boostPercent) {
        int numSamples = length / 2;
        short[] samples = new short[numSamples];

        int maxPeak = 0;
        for (int i = 0; i < numSamples; i++) {
            int low = pcmData[i * 2] & 0xFF;
            int high = pcmData[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            samples[i] = sample;
            int abs = Math.abs((int) sample);
            if (abs > maxPeak) {
                maxPeak = abs;
            }
        }

        float factor = 1.0f;
        if (normalize && maxPeak > 150) {
            // Normalizar pico para 95% do máximo (31000)
            factor = 31000.0f / maxPeak;
        }

        // Aplicar boost do usuário
        float boostFactor = (boostPercent > 0 ? boostPercent : 100) / 100.0f;
        float totalMultiplier = factor * boostFactor;

        byte[] output = new byte[length];
        for (int i = 0; i < numSamples; i++) {
            int processed = Math.round(samples[i] * totalMultiplier);
            if (processed > 32767) processed = 32767;
            else if (processed < -32768) processed = -32768;

            short finalSample = (short) processed;
            output[i * 2] = (byte) (finalSample & 0xFF);
            output[i * 2 + 1] = (byte) ((finalSample >> 8) & 0xFF);
        }

        return output;
    }

    /**
     * Salva os bytes PCM como arquivo WAV padrão com cabeçalho RIFF de 44 bytes.
     */
    public static void saveWav(File outputFile, byte[] pcmData, int length, int sampleRate, int channels) throws IOException {
        long totalDataLen = length + 36;
        long byteRate = sampleRate * channels * 2;

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            byte[] header = new byte[44];
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            header[4] = (byte) (totalDataLen & 0xff);
            header[5] = (byte) ((totalDataLen >> 8) & 0xff);
            header[6] = (byte) ((totalDataLen >> 16) & 0xff);
            header[7] = (byte) ((totalDataLen >> 24) & 0xff);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size (16 para PCM)
            header[20] = 1; header[21] = 0; // AudioFormat (1 para PCM linear)
            header[22] = (byte) channels; header[23] = 0;
            header[24] = (byte) (sampleRate & 0xff);
            header[25] = (byte) ((sampleRate >> 8) & 0xff);
            header[26] = (byte) ((sampleRate >> 16) & 0xff);
            header[27] = (byte) ((sampleRate >> 24) & 0xff);
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            header[32] = (byte) (channels * 2); header[33] = 0; // block align
            header[34] = 16; header[35] = 0; // bits por amostra (16)
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            header[40] = (byte) (length & 0xff);
            header[41] = (byte) ((length >> 8) & 0xff);
            header[42] = (byte) ((length >> 16) & 0xff);
            header[43] = (byte) ((length >> 24) & 0xff);

            out.write(header, 0, 44);
            out.write(pcmData, 0, length);
        }
    }
}
