package app.avo.inspector;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

import static org.junit.Assert.*;

public class AvoEncryptionTests {

    private KeyPair recipientKeyPair;
    private String recipientPublicKeyHex;

    @Before
    public void setUp() throws Exception {
        // Swap base64Encoder to java.util.Base64 for unit tests (android.util.Base64 is not available)
        AvoEncryption.base64Encoder = new AvoEncryption.Base64Encoder() {
            @Override
            public String encode(byte[] data) {
                return java.util.Base64.getEncoder().encodeToString(data);
            }
        };

        // Generate a P-256 key pair for the recipient
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        recipientKeyPair = keyGen.generateKeyPair();

        // Convert public key to hex (uncompressed format: 04 + X + Y)
        ECPublicKey pubKey = (ECPublicKey) recipientKeyPair.getPublic();
        byte[] x = toUnsigned32Bytes(pubKey.getW().getAffineX());
        byte[] y = toUnsigned32Bytes(pubKey.getW().getAffineY());

        StringBuilder hex = new StringBuilder("04");
        for (byte b : x) hex.append(String.format("%02x", b));
        for (byte b : y) hex.append(String.format("%02x", b));
        recipientPublicKeyHex = hex.toString();
    }

    @Test
    public void encryptDecryptRoundTrip() throws Exception {
        String plaintext = "\"hello world\"";
        String encrypted = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);

        assertNotNull("Encryption should succeed", encrypted);

        // Decrypt
        String decrypted = decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void encryptDecryptString() throws Exception {
        String plaintext = "\"test string value\"";
        String encrypted = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);
        assertNotNull(encrypted);
        assertEquals(plaintext, decrypt(encrypted));
    }

    @Test
    public void encryptDecryptInteger() throws Exception {
        String plaintext = "42";
        String encrypted = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);
        assertNotNull(encrypted);
        assertEquals(plaintext, decrypt(encrypted));
    }

    @Test
    public void encryptDecryptDouble() throws Exception {
        String plaintext = "3.14";
        String encrypted = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);
        assertNotNull(encrypted);
        assertEquals(plaintext, decrypt(encrypted));
    }

    @Test
    public void encryptDecryptBoolean() throws Exception {
        String plaintext = "true";
        String encrypted = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);
        assertNotNull(encrypted);
        assertEquals(plaintext, decrypt(encrypted));
    }

    @Test
    public void outputFormat() throws Exception {
        String encrypted = AvoEncryption.encrypt("test", recipientPublicKeyHex);
        assertNotNull(encrypted);

        byte[] decoded = java.util.Base64.getDecoder().decode(encrypted);

        // Minimum: 1 (version) + 65 (pubkey) + 16 (IV) + 16 (authTag) + at least 1 byte ciphertext
        assertTrue("Output should be at least 99 bytes", decoded.length >= 99);

        // Version byte
        assertEquals("Version byte should be 0x00", 0x00, decoded[0]);

        // Ephemeral public key starts with 0x04 (uncompressed)
        assertEquals("Ephemeral key should start with 0x04", 0x04, decoded[1]);
    }

    @Test
    public void differentEncryptionsProduceDifferentOutput() throws Exception {
        String plaintext = "same plaintext";
        String encrypted1 = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);
        String encrypted2 = AvoEncryption.encrypt(plaintext, recipientPublicKeyHex);

        assertNotNull(encrypted1);
        assertNotNull(encrypted2);
        assertNotEquals("Different encryptions should produce different output", encrypted1, encrypted2);

        // Both should decrypt to the same plaintext
        assertEquals(plaintext, decrypt(encrypted1));
        assertEquals(plaintext, decrypt(encrypted2));
    }

    @Test
    public void nullKeyReturnsNull() {
        assertNull(AvoEncryption.encrypt("test", null));
    }

    @Test
    public void emptyKeyReturnsNull() {
        assertNull(AvoEncryption.encrypt("test", ""));
    }

    @Test
    public void nullPlaintextReturnsNull() {
        assertNull(AvoEncryption.encrypt(null, recipientPublicKeyHex));
    }

    @Test
    public void invalidKeyReturnsNull() {
        // Invalid hex key should return null (not throw)
        assertNull(AvoEncryption.encrypt("test", "deadbeef"));
    }

    // =========================================================================
    // Test helper: ECDH + AES-GCM decryption
    // =========================================================================

    private String decrypt(String base64Encrypted) throws Exception {
        byte[] data = java.util.Base64.getDecoder().decode(base64Encrypted);

        // Parse format: [Version(1)] [EphemeralPubKey(65)] [IV(16)] [AuthTag(16)] [Ciphertext]
        assertEquals("Version byte", 0x00, data[0]);

        byte[] ephemeralPubBytes = new byte[65];
        System.arraycopy(data, 1, ephemeralPubBytes, 0, 65);

        byte[] iv = new byte[16];
        System.arraycopy(data, 66, iv, 0, 16);

        byte[] authTag = new byte[16];
        System.arraycopy(data, 82, authTag, 0, 16);

        int ciphertextLen = data.length - 98;
        byte[] ciphertext = new byte[ciphertextLen];
        System.arraycopy(data, 98, ciphertext, 0, ciphertextLen);

        // Reconstruct ephemeral public key
        byte[] xBytes = new byte[32];
        byte[] yBytes = new byte[32];
        System.arraycopy(ephemeralPubBytes, 1, xBytes, 0, 32);
        System.arraycopy(ephemeralPubBytes, 33, yBytes, 0, 32);

        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);
        ECPoint point = new ECPoint(x, y);

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

        ECPublicKey ephemeralPubKey = (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new java.security.spec.ECPublicKeySpec(point, ecSpec));

        // ECDH with recipient private key
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(recipientKeyPair.getPrivate());
        keyAgreement.doPhase(ephemeralPubKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        // KDF: SHA-256
        byte[] aesKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
        SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        // Reassemble ciphertext + authTag (Java GCM expects them concatenated)
        byte[] ciphertextWithTag = new byte[ciphertextLen + 16];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertextLen);
        System.arraycopy(authTag, 0, ciphertextWithTag, ciphertextLen, 16);

        // AES-GCM decrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        byte[] plainBytes = cipher.doFinal(ciphertextWithTag);

        return new String(plainBytes, "UTF-8");
    }

    private static byte[] toUnsigned32Bytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        } else if (bytes.length > 32) {
            byte[] trimmed = new byte[32];
            System.arraycopy(bytes, bytes.length - 32, trimmed, 0, 32);
            return trimmed;
        } else {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
            return padded;
        }
    }
}
