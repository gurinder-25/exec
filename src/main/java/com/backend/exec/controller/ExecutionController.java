package com.backend.exec.controller;

import com.backend.exec.request.ExecuteRequest;
import com.backend.exec.response.ExecuteResponse;
import com.backend.exec.service.CodeExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExecutionController {

    private final CodeExecutionService codeExecutionService;

    @PostMapping("execute")
    public ResponseEntity<ExecuteResponse> execute(@Valid @RequestBody ExecuteRequest executeRequest) {
        return ResponseEntity.ok(codeExecutionService.execute(executeRequest));
    }
}
