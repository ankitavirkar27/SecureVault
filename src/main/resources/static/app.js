"use strict";

// ==========================================
// SecureVault - Frontend JavaScript
// ==========================================

// HTML elements
const fileInput = document.getElementById("fileInput");
const dropZone = document.getElementById("dropZone");
const selectedFile = document.getElementById("selectedFile");
const filePrompt = document.getElementById("filePrompt");

const passwordInput = document.getElementById("passwordInput");
const confirmPassword = document.getElementById("confirmPassword");
const passwordError = document.getElementById("passwordError");

const formStatus = document.getElementById("formStatus");
const submitButton = document.getElementById("submitButton");
const submitText = document.getElementById("submitText");

const encryptTab = document.getElementById("encryptTab");
const decryptTab = document.getElementById("decryptTab");

const operationHeading = document.getElementById("operationHeading");
const operationDescription = document.getElementById("operationDescription");
const submitIcon = document.getElementById("submitIcon");
const togglePassword = document.getElementById("togglePassword");

const historyBody = document.getElementById("historyBody");
const operationCount = document.getElementById("operationCount");
const refreshHistory = document.getElementById("refreshHistory");

// Application settings
const MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB

// Current operation: "encrypt" or "decrypt"
let currentMode = "encrypt";


// ==========================================
// 1. Display messages
// ==========================================

function showStatus(message, type = "") {
    formStatus.textContent = message;
    formStatus.className = "form-status";

    if (type) {
        formStatus.classList.add(type);
    }
}


// ==========================================
// 2. Switch between Encrypt and Decrypt
// ==========================================

function setMode(mode) {
    currentMode = mode;

    const encrypting = mode === "encrypt";

    // Update tab appearance
    document.querySelectorAll(".operation-tab").forEach((tab) => {
        const active = tab.dataset.mode === mode;

        tab.classList.toggle("active", active);
        tab.setAttribute("aria-selected", String(active));
    });

    // Update headings and button text
    operationHeading.textContent = encrypting
        ? "Choose a file to encrypt"
        : "Choose a file to decrypt";

    operationDescription.textContent = encrypting
        ? "Select the document you want to protect with a password."
        : "Select a previously encrypted SecureVault file to restore it.";

    submitText.textContent = encrypting
        ? "Encrypt file securely"
        : "Decrypt file securely";

    submitIcon.textContent = encrypting ? "◇" : "↙";

    // Reset form fields
    fileInput.value = "";
    selectedFile.textContent = "No file selected";

    filePrompt.innerHTML =
        'Drop your file here or <span>browse files</span>';

    passwordInput.value = "";
    confirmPassword.value = "";

    passwordError.textContent = "";

    showStatus("");
}

// Tab event listeners
encryptTab.addEventListener("click", () => {
    setMode("encrypt");
});

decryptTab.addEventListener("click", () => {
    setMode("decrypt");
});


// ==========================================
// 3. File selection and validation
// ==========================================

fileInput.addEventListener("change", () => {
    const file = fileInput.files[0];

    if (file) {
        displaySelectedFile(file);
    }
});

function displaySelectedFile(file) {
    showStatus("");

    if (file.size > MAX_FILE_SIZE) {
        fileInput.value = "";
        selectedFile.textContent = "File exceeds the 25 MB limit.";

        showStatus(
            "Please select a file smaller than 25 MB.",
            "error"
        );

        return;
    }

    if (currentMode === "decrypt" &&
        !file.name.toLowerCase().endsWith(".sfe")) {

        selectedFile.textContent =
            `${file.name} · ${formatBytes(file.size)}`;

        showStatus(
            "This file does not have the .sfe extension. Select a SecureVault encrypted file.",
            "error"
        );

        return;
    }

    selectedFile.textContent =
        `${file.name} · ${formatBytes(file.size)}`;
}


// ==========================================
// 4. Drag-and-drop file upload
// ==========================================

dropZone.addEventListener("dragover", (event) => {
    event.preventDefault();
    dropZone.classList.add("dragover");
});

dropZone.addEventListener("dragleave", () => {
    dropZone.classList.remove("dragover");
});

dropZone.addEventListener("drop", (event) => {
    event.preventDefault();
    dropZone.classList.remove("dragover");

    const file = event.dataTransfer.files[0];

    if (!file) {
        return;
    }

    const transfer = new DataTransfer();

    transfer.items.add(file);
    fileInput.files = transfer.files;

    displaySelectedFile(file);
});


// ==========================================
// 5. Show or hide passwords
// ==========================================

togglePassword.addEventListener("click", () => {
    const shouldShow = passwordInput.type === "password";

    passwordInput.type = shouldShow ? "text" : "password";
    confirmPassword.type = shouldShow ? "text" : "password";

    togglePassword.textContent = shouldShow ? "Hide" : "Show";

    togglePassword.setAttribute(
        "aria-label",
        shouldShow ? "Hide password" : "Show password"
    );
});


// ==========================================
// 6. Password validation
// ==========================================

passwordInput.addEventListener("input", () => {
    passwordError.textContent = "";
});

confirmPassword.addEventListener("input", () => {
    passwordError.textContent = "";
});

function validatePasswords(password, confirmation) {
    if (password.length < 8) {
        passwordError.textContent =
            "Use a password containing at least 8 characters.";

        passwordInput.focus();

        return false;
    }

    if (password !== confirmation) {
        passwordError.textContent =
            "The passwords do not match.";

        confirmPassword.focus();

        return false;
    }

    return true;
}


// ==========================================
// 7. File size formatting
// ==========================================

function formatBytes(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}


// ==========================================
// 8. Encrypt or decrypt a file
// ==========================================

document.getElementById("vaultForm").addEventListener(
    "submit",
    async (event) => {
        event.preventDefault();

        showStatus("");
        passwordError.textContent = "";

        const file = fileInput.files[0];
        const password = passwordInput.value;
        const confirmation = confirmPassword.value;

        // Validate file selection
        if (!file) {
            showStatus(
                "Please select a file first.",
                "error"
            );

            return;
        }

        // Validate file size
        if (file.size > MAX_FILE_SIZE) {
            showStatus(
                "The selected file exceeds the 25 MB limit.",
                "error"
            );

            return;
        }

        // Validate file type for decryption
        if (currentMode === "decrypt" &&
            !file.name.toLowerCase().endsWith(".sfe")) {

            showStatus(
                "Please select a SecureVault .sfe encrypted file.",
                "error"
            );

            return;
        }

        // Validate passwords
        if (!validatePasswords(password, confirmation)) {
            return;
        }

        submitButton.disabled = true;

        submitText.textContent =
            currentMode === "encrypt"
                ? "Encrypting file..."
                : "Decrypting file...";

        try {
            const endpoint =
                currentMode === "encrypt"
                    ? "/api/vault/encrypt"
                    : "/api/vault/decrypt";

            const formData = new FormData();

            formData.append("file", file);
            formData.append("password", password);

            const response = await fetch(endpoint, {
                method: "POST",
                body: formData
            });

            // Handle unsuccessful server responses without throwing.
            if (!response.ok) {
                let message =
                    "The operation could not be completed.";

                try {
                    const data = await response.json();
                    message = data.message || message;
                } catch {
                    // Keep the default message for non-JSON responses.
                }

                showStatus(message, "error");
                return;
            }

            // Download the processed file returned by the Java backend.
            const blob = await response.blob();

            const downloadUrl = URL.createObjectURL(blob);
            const downloadLink = document.createElement("a");

            downloadLink.href = downloadUrl;
            downloadLink.download = getDownloadName(
                file.name,
                currentMode
            );

            document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();

            // Release the temporary object URL.
            setTimeout(() => {
                URL.revokeObjectURL(downloadUrl);
            }, 1000);

            showStatus(
                currentMode === "encrypt"
                    ? "Encryption completed. Your protected file has been downloaded."
                    : "Decryption completed. Your restored file has been downloaded.",
                "success"
            );

            // Refresh activity history after successful processing.
            await loadHistory();

        } catch (error) {
            console.error("SecureVault operation failed:", error);

            showStatus(
                "Unable to contact the server. Check that SecureVault is running and try again.",
                "error"
            );

        } finally {
            submitButton.disabled = false;

            submitText.textContent =
                currentMode === "encrypt"
                    ? "Encrypt file securely"
                    : "Decrypt file securely";
        }
    }
);


// ==========================================
// 9. Determine the downloaded filename
// ==========================================

function getDownloadName(originalName, mode) {
    if (mode === "encrypt") {
        return originalName + ".sfe";
    }

    if (originalName.toLowerCase().endsWith(".sfe")) {
        return originalName.slice(0, -4);
    }

    return "decrypted_" + originalName;
}


// ==========================================
// 10. Load activity history from Java API
// ==========================================

async function loadHistory() {
    try {
        const response = await fetch("/api/vault/history");

        if (!response.ok) {
            showHistoryUnavailable();
            return;
        }

        const entries = await response.json();

        if (!Array.isArray(entries)) {
            showHistoryUnavailable();
            return;
        }

        // Update operation counter
        operationCount.innerHTML =
            `${entries.length} <small>operations</small>`;

        // Display empty state
        if (entries.length === 0) {
            historyBody.replaceChildren();

            const row = document.createElement("tr");
            const cell = document.createElement("td");

            cell.colSpan = 4;
            cell.className = "empty-state";
            cell.textContent = "No operations recorded yet.";

            row.appendChild(cell);
            historyBody.appendChild(row);

            return;
        }

        // Clear existing rows
        historyBody.replaceChildren();

        // Add each history entry safely
        entries.forEach((entry) => {
            const row = document.createElement("tr");

            // File name
            const fileCell = document.createElement("td");
            const fileWrap = document.createElement("div");
            const fileIcon = document.createElement("span");
            const fileName = document.createElement("span");

            fileWrap.className = "file-cell";
            fileIcon.className = "file-mini-icon";
            fileIcon.textContent = "▤";

            fileName.textContent =
                entry.fileName || "Unknown file";

            fileWrap.append(fileIcon, fileName);
            fileCell.appendChild(fileWrap);

            // Operation type
            const operationCell = document.createElement("td");
            const operationBadge = document.createElement("span");

            operationBadge.className = "badge operation";
            operationBadge.textContent =
                entry.operationType || "UNKNOWN";

            operationCell.appendChild(operationBadge);

            // Operation status
            const statusCell = document.createElement("td");
            const statusBadge = document.createElement("span");

            const status = String(
                entry.status || "UNKNOWN"
            ).toUpperCase();

            const successful = status === "SUCCESS";

            statusBadge.className =
                `badge ${successful ? "success" : "failure"}`;

            statusBadge.textContent = status;

            statusCell.appendChild(statusBadge);

            // Date and time
            const dateCell = document.createElement("td");

            if (entry.operationTime) {
                const parsedDate = new Date(entry.operationTime);

                dateCell.textContent =
                    Number.isNaN(parsedDate.getTime())
                        ? String(entry.operationTime)
                        : parsedDate.toLocaleString();
            } else {
                dateCell.textContent = "—";
            }

            row.append(
                fileCell,
                operationCell,
                statusCell,
                dateCell
            );

            historyBody.appendChild(row);
        });

    } catch (error) {
        console.warn("Could not load activity history:", error);

        showHistoryUnavailable();
    }
}


// ==========================================
// 11. Show history unavailable message
// ==========================================

function showHistoryUnavailable() {
    historyBody.replaceChildren();

    const row = document.createElement("tr");
    const cell = document.createElement("td");

    cell.colSpan = 4;
    cell.className = "empty-state";
    cell.textContent =
        "Activity history will appear here once the Java API is connected.";

    row.appendChild(cell);
    historyBody.appendChild(row);
}


// ==========================================
// 12. Refresh history button
// ==========================================

refreshHistory.addEventListener("click", () => {
    loadHistory().catch((error) => {
        console.error("History refresh failed:", error);
    });
});


// ==========================================
// 13. Initial setup
// ==========================================

// Load existing history when the page opens.
// The function handles its own errors.
loadHistory().catch((error) => {
    console.error("Initial history loading failed:", error);
});