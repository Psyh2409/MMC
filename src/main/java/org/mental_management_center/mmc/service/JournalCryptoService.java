package org.mental_management_center.mmc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
public class JournalCryptoService {

    private static final String ALGORITHM = "AES";

    // Секретний ключ підтягується з налаштувань додатка.
    // Для тесту впиши 16-символьний рядок за замовчуванням
    @Value("${app.crypto.secret-key}")
    private String secretKey;

    // 1. КОНВЕЄР: Стиснення -> Шифрування
    public byte[] encryptAndCompress(String plainText) {
        if (plainText == null || plainText.isEmpty()) return null;
        try {
            // Крок А: Стискаємо в GZIP
            byte[] compressedBytes = compress(plainText);

            // Крок Б: Шифруємо через AES-256
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            return cipher.doFinal(compressedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Помилка шифрування щоденника", e);
        }
    }

    // 2. ЗВОРОТНІЙ КОНВЕЄР: Дешифрування -> Розпакування
    public String decryptAndDecompress(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length == 0) return null;
        try {
            // Крок А: Дешифруємо AES
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedData);

            // Крок Б: Розпаковуємо GZIP
            return decompress(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Помилка дешифрування щоденника. Можливо, ключ невірний.", e);
        }
    }

    // Внутрішні утиліти стиснення (як у твоїй статті)
    private byte[] compress(String str) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        }
    }

    private String decompress(byte[] compressed) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // 3. Шифрування чистих байтів заголовка медіафайлу (без GZIP)
    public byte[] encryptBytes(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(rawBytes);
        } catch (Exception e) {
            throw new RuntimeException("Помилка шифрування заголовка медіафайлу", e);
        }
    }

    // 4. Дешифрування чистих байтів заголовка медіафайлу
    public byte[] decryptBytes(byte[] encryptedBytes) {
        if (encryptedBytes == null || encryptedBytes.length == 0) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Помилка дешифрування заголовка медіафайлу", e);
        }
    }
}