package com.jean.tokenmonitor;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCrypto {

    private static final int ITERATIONS = 310000;
    private static final int KEY_BITS = 256;
    private static final String AAD = "TokenMonitorJean-Backup-v1";

    private BackupCrypto() {}

    public static String encrypt(String plaintext, char[] password, String address) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] keyBytes = deriveKey(password, salt, ITERATIONS);
        byte[] ciphertext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }

        JSONObject out = new JSONObject();
        out.put("format", "TokenMonitorJeanBackup");
        out.put("version", 1);
        out.put("kdf", "PBKDF2WithHmacSHA256");
        out.put("iterations", ITERATIONS);
        out.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        out.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        out.put("cipher", "AES-256-GCM");
        out.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        out.put("address", address);
        return out.toString();
    }

    public static String decrypt(String backupJson, char[] password) throws Exception {
        JSONObject in = new JSONObject(backupJson);
        if (!"TokenMonitorJeanBackup".equals(in.optString("format"))) {
            throw new IllegalArgumentException("Unknown format");
        }
        if (in.optInt("version", 0) != 1) {
            throw new IllegalArgumentException("Unsupported version");
        }

        int iterations = in.getInt("iterations");
        if (iterations < 100000 || iterations > 2000000) {
            throw new IllegalArgumentException("Unsafe iteration count");
        }

        byte[] salt = Base64.decode(in.getString("salt"), Base64.NO_WRAP);
        byte[] iv = Base64.decode(in.getString("iv"), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(in.getString("ciphertext"), Base64.NO_WRAP);

        byte[] keyBytes = deriveKey(password, salt, iterations);
        byte[] plaintext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            plaintext = cipher.doFinal(ciphertext);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }

        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
