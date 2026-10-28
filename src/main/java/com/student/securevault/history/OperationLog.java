package com.student.securevault.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    public OperationLog() {
    }

    public OperationLog(String operationType, String fileName, String status) {
        this.operationType = operationType;
        this.fileName = fileName;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        if (operationTime == null) {
            operationTime = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getOperationTime() {
        return operationTime;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
