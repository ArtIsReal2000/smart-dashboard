package com.smartdashboard.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesEncryptorTest {

    private AesEncryptor encryptor;

    @BeforeEach
    void setUp() {
        // 32-byte dummy key for testing (base64 encoded)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) keyBytes[i] = (byte) (i + 1);
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        encryptor = new AesEncryptor(base64Key);
    }

    @Test
    void testEncryptAndDecrypt() {
        String secret = "super-secret-oauth-refresh-token-xyz-123";
        String encrypted = encryptor.encrypt(secret);

        assertNotNull(encrypted);
        assertNotEquals(secret, encrypted);

        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(secret, decrypted);
    }

    @Test
    void testNullHandling() {
        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void testInvalidKeyLength() {
        // Less than 32 bytes should throw exception
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new AesEncryptor(shortKey));
    }
}
