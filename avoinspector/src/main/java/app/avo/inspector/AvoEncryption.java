package app.avo.inspector;

import android.util.Log;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.Nullable;

class AvoEncryption {

    interface Base64Encoder {
        String encode(byte[] data);
    }

    static Base64Encoder base64Encoder = new Base64Encoder() {
        @Override
        public String encode(byte[] data) {
            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        }
    };

    @Nullable
    static String encrypt(String plaintext, String recipientPublicKeyHex) {
        try {
            if (plaintext == null || recipientPublicKeyHex == null || recipientPublicKeyHex.isEmpty()) {
                return null;
            }

            // Parse recipient public key from hex
            byte[] pubKeyBytes = hexToBytes(recipientPublicKeyHex);
            ECPublicKey recipientKey = parseUncompressedPublicKey(pubKeyBytes);

            // Generate ephemeral P-256 key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair ephemeralKeyPair = keyGen.generateKeyPair();

            // ECDH: compute shared secret
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(ephemeralKeyPair.getPrivate());
            keyAgreement.doPhase(recipientKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();

            // KDF: SHA-256 of shared secret X-coordinate (sharedSecret is already X only on most JCE providers)
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] aesKeyBytes = sha256.digest(sharedSecret);

            // AES-256-GCM encrypt
            SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Java GCM appends auth tag to ciphertext; split last 16 bytes
            int ciphertextLen = ciphertextWithTag.length - 16;
            byte[] ciphertext = new byte[ciphertextLen];
            byte[] authTag = new byte[16];
            System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertextLen);
            System.arraycopy(ciphertextWithTag, ciphertextLen, authTag, 0, 16);

            // Serialize ephemeral public key as uncompressed: 0x04 + X(32) + Y(32)
            ECPublicKey ephemeralPub = (ECPublicKey) ephemeralKeyPair.getPublic();
            byte[] ephemeralPubBytes = encodeUncompressedPoint(ephemeralPub);

            // Assemble: [Version 0x00 (1b)] + [EphemeralPubKey (65b)] + [IV (16b)] + [AuthTag (16b)] + [Ciphertext]
            byte[] output = new byte[1 + 65 + 16 + 16 + ciphertextLen];
            output[0] = 0x00; // version
            System.arraycopy(ephemeralPubBytes, 0, output, 1, 65);
            System.arraycopy(iv, 0, output, 66, 16);
            System.arraycopy(authTag, 0, output, 82, 16);
            System.arraycopy(ciphertext, 0, output, 98, ciphertextLen);

            return base64Encoder.encode(output);
        } catch (Exception e) {
            Log.e("Avo Inspector", "Encryption failed", e);
            return null;
        }
    }

    private static ECPublicKey parseUncompressedPublicKey(byte[] pubKeyBytes) throws Exception {
        // Handle both with and without 0x04 prefix
        int offset = 0;
        if (pubKeyBytes.length == 65 && pubKeyBytes[0] == 0x04) {
            offset = 1;
        } else if (pubKeyBytes.length != 64) {
            throw new IllegalArgumentException("Invalid public key length: " + pubKeyBytes.length);
        }

        byte[] xBytes = new byte[32];
        byte[] yBytes = new byte[32];
        System.arraycopy(pubKeyBytes, offset, xBytes, 0, 32);
        System.arraycopy(pubKeyBytes, offset + 32, yBytes, 0, 32);

        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);
        ECPoint point = new ECPoint(x, y);

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecSpec);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return (ECPublicKey) keyFactory.generatePublic(keySpec);
    }

    private static byte[] encodeUncompressedPoint(ECPublicKey publicKey) {
        ECPoint w = publicKey.getW();
        byte[] xBytes = toUnsigned32Bytes(w.getAffineX());
        byte[] yBytes = toUnsigned32Bytes(w.getAffineY());

        byte[] result = new byte[65];
        result[0] = 0x04;
        System.arraycopy(xBytes, 0, result, 1, 32);
        System.arraycopy(yBytes, 0, result, 33, 32);
        return result;
    }

    private static byte[] toUnsigned32Bytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        } else if (bytes.length > 32) {
            // Strip leading sign byte
            byte[] trimmed = new byte[32];
            System.arraycopy(bytes, bytes.length - 32, trimmed, 0, 32);
            return trimmed;
        } else {
            // Pad with leading zeros
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
            return padded;
        }
    }

    private static byte[] hexToBytes(String hex) {
        // Remove 0x prefix if present
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
