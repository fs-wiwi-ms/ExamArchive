package ms.wiwi.examarchive.services;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class ApiKeyEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyEncryptionService(String masterEncryptionKey) {
        if (masterEncryptionKey == null || masterEncryptionKey.isBlank()) {
            throw new IllegalStateException("EXAMARCHIVE_ENCRYPTION_KEY is not set");
        }
        byte[] decodedKey = Base64.getDecoder().decode(masterEncryptionKey);
        this.masterKey = new SecretKeySpec(decodedKey, "AES");
    }

    public String encrypt(String plainApiKey) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

        byte[] cipherText = cipher.doFinal(plainApiKey.getBytes(StandardCharsets.UTF_8));

        // IV und Ciphertext zusammenfügen (IV + Ciphertext)
        byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                .put(iv)
                .put(cipherText)
                .array();

        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encryptedBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);

        ByteBuffer buffer = ByteBuffer.wrap(combined);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        buffer.get(iv);

        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }
}
