package com.student.securevault.history;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OperationLogRepository
        extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findTop50ByOrderByOperationTimeDesc();
}
