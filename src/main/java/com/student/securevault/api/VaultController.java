package com.student.securevault.api;

import com.student.securevault.history.OperationLog;
import com.student.securevault.history.OperationLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import com.student.securevault.crypto.VaultCryptoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultCryptoService cryptoService;
    private final OperationLogRepository logRepository;

    public VaultController(OperationLogRepository logRepository) {
        this.cryptoService = new VaultCryptoService();
        this.logRepository = logRepository;
    }

    @PostMapping("/encrypt")
    public ResponseEntity<byte[]> encrypt(
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password
    ) throws Exception {

        String fileName = safeFileName(file.getOriginalFilename());

        try {
            validateRequest(file, password);

            byte[] originalData = file.getBytes();
            char[] passwordChars = password.toCharArray();

            try {
                byte[] encryptedData =
                        cryptoService.encrypt(originalData, passwordChars);

                String outputName = fileName + ".sfe";

                ResponseEntity<byte[]> response =
                        fileResponse(encryptedData, outputName);

                logRepository.save(
                        new OperationLog("ENCRYPT", fileName, "SUCCESS")
                );

                return response;

            } finally {
                Arrays.fill(passwordChars, '\0');
            }

        } catch (Exception exception) {
            logRepository.save(
                    new OperationLog("ENCRYPT", fileName, "FAILURE")
            );

            throw exception;
        }
    }

    @PostMapping("/decrypt")
    public ResponseEntity<byte[]> decrypt(
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password
    ) throws Exception {

        String fileName = safeFileName(file.getOriginalFilename());

        try {
            validateRequest(file, password);

            byte[] encryptedData = file.getBytes();
            char[] passwordChars = password.toCharArray();

            try {
                byte[] decryptedData =
                        cryptoService.decrypt(encryptedData, passwordChars);

                String outputName = fileName.toLowerCase().endsWith(".sfe")
                        ? fileName.substring(0, fileName.length() - 4)
                        : fileName + ".decrypted";

                if (outputName.isBlank()) {
                    outputName = "decrypted-file";
                }

                ResponseEntity<byte[]> response =
                        fileResponse(decryptedData, outputName);

                logRepository.save(
                        new OperationLog("DECRYPT", fileName, "SUCCESS")
                );

                return response;

            } finally {
                Arrays.fill(passwordChars, '\0');
            }

        } catch (Exception exception) {
            logRepository.save(
                    new OperationLog("DECRYPT", fileName, "FAILURE")
            );

            throw exception;
        }
    }


    private void validateRequest(MultipartFile file, String password) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a non-empty file.");
        }

        if (file.getSize() > 25L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Files must be 25 MB or smaller."
            );
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Please enter a password.");
        }
    }

    private ResponseEntity<byte[]> fileResponse(
            byte[] data,
            String filename
    ) {
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
        );
        headers.setContentLength(data.length);
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    private String safeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "securevault-file";
        }

        String cleaned = filename.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        cleaned = cleaned.substring(lastSlash + 1);

        // Remove control characters that should not appear in response headers.
        cleaned = cleaned.replaceAll("\\p{Cntrl}", "_").trim();

        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "securevault-file";
        }

        return cleaned;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                Map.of("message", exception.getMessage())
        );
    }


    @ExceptionHandler(GeneralSecurityException.class)
    public ResponseEntity<Map<String, String>> handleCryptoError(
            GeneralSecurityException exception
    ) {
        return ResponseEntity.badRequest().body(
                Map.of(
                        "message",
                        "Decryption failed. Check the password and encrypted file."
                )
        );
    }
    @GetMapping("/history")
    public List<OperationLog> getHistory() {
        return logRepository.findTop50ByOrderByOperationTimeDesc();
    }

}

