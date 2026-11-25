package ms.wiwi.examarchive.unit;

import ms.wiwi.examarchive.services.ApiKeyEncryptionService;
import org.junit.jupiter.api.Test;

import java.nio.BufferUnderflowException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class KeyEncryptionTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String randomKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static ApiKeyEncryptionService service() {
        return new ApiKeyEncryptionService(randomKey());
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() throws Exception {
        ApiKeyEncryptionService service = service();
        String plaintext = "sk-abcdef1234567890";

        String encrypted = service.encrypt(plaintext);

        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, service.decrypt(encrypted));
    }

    @Test
    void encryptedValueIsBase64AndContainsIv() throws Exception {
        ApiKeyEncryptionService service = service();
        byte[] decoded = Base64.getDecoder().decode(service.encrypt("secret"));
        assertTrue(decoded.length > 12, "Encrypted payload should be longer than the IV");
    }

    @Test
    void encrypt_emptyString_roundTrips() throws Exception {
        ApiKeyEncryptionService service = service();
        assertEquals("", service.decrypt(service.encrypt("")));
    }

    @Test
    void encrypt_unicodeContent_roundTrips() throws Exception {
        ApiKeyEncryptionService service = service();
        String plaintext = "Schlüssel🔑-秘密-Ω≈ç√∫˜µ≤";

        assertEquals(plaintext, service.decrypt(service.encrypt(plaintext)));
    }

    @Test
    void decrypt_tamperedCiphertext_throws() throws Exception {
        ApiKeyEncryptionService service = service();
        String encrypted = service.encrypt("secret");

        byte[] decoded = Base64.getDecoder().decode(encrypted);
        decoded[decoded.length - 1] ^= 0x01; // flip a bit in the ciphertext
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThrows(javax.crypto.AEADBadTagException.class, () -> service.decrypt(tampered));
    }

    @Test
    void decrypt_withWrongKey_throws() throws Exception {
        ApiKeyEncryptionService encrypter = service();
        ApiKeyEncryptionService decrypter = new ApiKeyEncryptionService(randomKey());

        String encrypted = encrypter.encrypt("secret");

        assertThrows(javax.crypto.AEADBadTagException.class, () -> decrypter.decrypt(encrypted));
    }

    @Test
    void decrypt_invalidBase64_throws() {
        ApiKeyEncryptionService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.decrypt("not-a-valid-base64-!!!"));
    }

    @Test
    void decrypt_shorterThanIv_throws() {
        ApiKeyEncryptionService service = service();
        String tooShort = Base64.getEncoder().encodeToString(new byte[4]);
        assertThrows(BufferUnderflowException.class, () -> service.decrypt(tooShort));
    }

    @Test
    void constructor_nullKey_throws() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyEncryptionService(null));
    }

    @Test
    void constructor_blankKey_throws() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyEncryptionService("   "));
    }

    @Test
    void constructor_invalidBase64Key_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyEncryptionService("not-base64!!!"));
    }

    @Test
    void constructor_undersizedKey_failsOnEncrypt() {
        ApiKeyEncryptionService service = new ApiKeyEncryptionService(
                Base64.getEncoder().encodeToString(new byte[8]));
        assertThrows(java.security.InvalidKeyException.class, () -> service.encrypt("secret"));
    }
}
