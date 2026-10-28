package com.student.securevault.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public class VaultCryptoService {

    private static final byte[] MAGIC = "SFE1".getBytes(StandardCharsets.US_ASCII);

    private static final int SALT_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 600_000;

    private static final int HEADER_LENGTH =
            MAGIC.length + SALT_LENGTH + NONCE_LENGTH;

    private static final int MIN_ENCRYPTED_LENGTH =
            HEADER_LENGTH + TAG_LENGTH_BYTES;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypts file contents and returns an SFE1-formatted byte array.
     */
    public byte[] encrypt(byte[] fileData, char[] password)
            throws GeneralSecurityException {

        validateInput(fileData, password);

        byte[] salt = new byte[SALT_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];

        secureRandom.nextBytes(salt);
        secureRandom.nextBytes(nonce);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
        );

        // Authenticate the file-format header as well as the encrypted data.
        byte[] header = ByteBuffer.allocate(HEADER_LENGTH)
                .put(MAGIC)
                .put(salt)
                .put(nonce)
                .array();

        cipher.updateAAD(header);
        byte[] encryptedData = cipher.doFinal(fileData);

        byte[] result = new byte[header.length + encryptedData.length];

        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(
                encryptedData, 0,
                result, header.length,
                encryptedData.length
        );

        return result;
    }

    /**
     * Decrypts an SFE1-formatted byte array.
     * Incorrect passwords or altered data cause authentication to fail.
     */
    public byte[] decrypt(byte[] encryptedFile, char[] password)
            throws GeneralSecurityException {

        validateInput(encryptedFile, password);

        if (encryptedFile.length < MIN_ENCRYPTED_LENGTH) {
            throw new GeneralSecurityException(
                    "The encrypted file is incomplete or invalid."
            );
        }

        ByteBuffer input = ByteBuffer.wrap(encryptedFile);

        byte[] magic = new byte[MAGIC.length];
        input.get(magic);

        if (!Arrays.equals(magic, MAGIC)) {
            throw new GeneralSecurityException(
                    "This file is not a supported SecureVault encrypted file."
            );
        }

        byte[] salt = new byte[SALT_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];

        input.get(salt);
        input.get(nonce);

        byte[] header = Arrays.copyOfRange(
                encryptedFile, 0, HEADER_LENGTH
        );

        byte[] ciphertext = Arrays.copyOfRange(
                encryptedFile, HEADER_LENGTH, encryptedFile.length
        );

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
        );

        cipher.updateAAD(header);

        // GCM verifies the authentication tag before returning plaintext.
        // Do not write plaintext to a destination file until this succeeds.
        return cipher.doFinal(ciphertext);
    }

    private SecretKey deriveKey(char[] password, byte[] salt)
            throws GeneralSecurityException {

        PBEKeySpec keySpec = new PBEKeySpec(
                password,
                salt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH_BITS
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] keyBytes = factory.generateSecret(keySpec).getEncoded();

            try {
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                Arrays.fill(keyBytes, (byte) 0);
            }
        } finally {
            keySpec.clearPassword();
        }
    }

    private void validateInput(byte[] data, char[] password) {
        if (data == null) {
            throw new IllegalArgumentException("File data cannot be null.");
        }

        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        // The web application will separately enforce its 25 MB upload limit.
        if (data.length > 25 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Files must be 25 MB or smaller."
            );
        }
    }
}
