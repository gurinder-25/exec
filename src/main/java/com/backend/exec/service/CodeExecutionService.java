package com.backend.exec.service;

import com.backend.exec.executionEngine.ExecutionEngine;
import com.backend.exec.request.ExecuteRequest;
import com.backend.exec.response.ExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private static final String EXEC_ID_KEY = "execId";

    private final ExecutionEngine executionEngine;

    public ExecuteResponse execute(ExecuteRequest request) {
        MDC.put(EXEC_ID_KEY, UUID.randomUUID().toString().substring(0, 8));
        long start = System.currentTimeMillis();
        try {
            log.info("Execution request received: language={}, codeChars={}, stdinChars={}",
                    request.language(), request.code().length(),
                    request.stdin() == null ? 0 : request.stdin().length());

            ExecuteResponse response = executionEngine.execute(request);

            log.info("Execution request completed: status={}, totalMs={}",
                    response.status(), System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException e) {
            log.error("Execution request failed after {}ms: {}", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        } finally {
            MDC.remove(EXEC_ID_KEY);
        }
    }
}
