package com.jean.tokenmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class WalletManager {

    private static final String PREFS = "token_monitor_wallet";
    private static final String PREF_MNEMONIC = "encrypted_mnemonic";
    private static final String PREF_ADDRESS = "wallet_address";
    private static final String KEY_ALIAS = "token_monitor_wallet_local_key_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public WalletManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasWallet() {
        return prefs.contains(PREF_MNEMONIC) && prefs.contains(PREF_ADDRESS);
    }

    public CreatedWallet createNewWallet() throws Exception {
        if (hasWallet()) throw new IllegalStateException("Wallet already exists");

        byte[] entropy = new byte[16]; // BIP-39: 128 bits = 12 words
        random.nextBytes(entropy);
        String mnemonic = MnemonicUtils.generateMnemonic(entropy);
        Arrays.fill(entropy, (byte) 0);

        String address = importMnemonicInternal(mnemonic);
        return new CreatedWallet(mnemonic, address);
    }

    public String importMnemonic(String input) throws Exception {
        if (hasWallet()) throw new IllegalStateException("Wallet already exists");
        String mnemonic = normalizeMnemonic(input);
        if (!MnemonicUtils.validateMnemonic(mnemonic)) {
            throw new IllegalArgumentException("Invalid mnemonic");
        }
        return importMnemonicInternal(mnemonic);
    }

    private String importMnemonicInternal(String mnemonic) throws Exception {
        String normalized = normalizeMnemonic(mnemonic);
        if (!MnemonicUtils.validateMnemonic(normalized)) {
            throw new IllegalArgumentException("Invalid mnemonic");
        }

        String address = deriveAddress(normalized);
        String encrypted = encryptLocal(normalized);
        prefs.edit()
                .putString(PREF_MNEMONIC, encrypted)
                .putString(PREF_ADDRESS, address)
                .commit();
        return address;
    }

    public String getAddress() {
        String address = prefs.getString(PREF_ADDRESS, null);
        if (address == null) throw new IllegalStateException("No wallet");
        return address;
    }

    String getMnemonicForInternalBackup() throws Exception {
        String payload = prefs.getString(PREF_MNEMONIC, null);
        if (payload == null) throw new IllegalStateException("No wallet");
        return decryptLocal(payload);
    }

    public String createEncryptedBackup(char[] password) throws Exception {
        if (!hasWallet()) throw new IllegalStateException("No wallet");
        if (password == null || password.length < 8) throw new IllegalArgumentException("Weak password");

        String mnemonic = getMnemonicForInternalBackup();
        try {
            JSONObject secret = new JSONObject();
            secret.put("version", 1);
            secret.put("mnemonic", mnemonic);
            secret.put("address", getAddress());
            return BackupCrypto.encrypt(secret.toString(), password, getAddress());
        } finally {
            mnemonic = null;
        }
    }

    public String restoreEncryptedBackup(String backupJson, char[] password) throws Exception {
        if (hasWallet()) throw new IllegalStateException("Wallet already exists");
        String plaintext = BackupCrypto.decrypt(backupJson, password);
        JSONObject secret = new JSONObject(plaintext);
        String mnemonic = normalizeMnemonic(secret.getString("mnemonic"));
        String expected = secret.optString("address", "");

        if (!MnemonicUtils.validateMnemonic(mnemonic)) {
            throw new IllegalArgumentException("Invalid backup mnemonic");
        }

        String derived = deriveAddress(mnemonic);
        if (!expected.isEmpty() && !derived.equalsIgnoreCase(expected)) {
            throw new IllegalArgumentException("Address mismatch");
        }
        return importMnemonicInternal(mnemonic);
    }

    private String normalizeMnemonic(String input) {
        if (input == null) return "";
        return input.trim().toLowerCase(java.util.Locale.US).replaceAll("\\s+", " ");
    }

    private String deriveAddress(String mnemonic) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        try {
            Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
            final int H = 0x80000000;
            int[] path = {44 | H, 60 | H, 0 | H, 0, 0}; // m/44'/60'/0'/0/0
            Bip32ECKeyPair child = Bip32ECKeyPair.deriveKeyPair(master, path);
            return Credentials.create(child).getAddress();
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    private String encryptLocal(String plaintext) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        JSONObject json = new JSONObject();
        json.put("v", 1);
        json.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        json.put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        return json.toString();
    }

    private String decryptLocal(String payload) throws Exception {
        JSONObject json = new JSONObject(payload);
        byte[] iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP);
        byte[] ct = Base64.decode(json.getString("ct"), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(ct);
        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    public static class CreatedWallet {
        public final String mnemonic;
        public final String address;

        CreatedWallet(String mnemonic, String address) {
            this.mnemonic = mnemonic;
            this.address = address;
        }
    }
}
