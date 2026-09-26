package com.backend.exec.service;

import com.backend.exec.executionEngine.ExecutionEngine;
import com.backend.exec.request.ExecuteRequest;
import com.backend.exec.response.ExecuteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private final ExecutionEngine executionEngine;

    public ExecuteResponse execute(ExecuteRequest request) {
        return executionEngine.execute(request);
    }
}
