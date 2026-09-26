package com.backend.exec.response;

public record ExecuteResponse(
        String status,
        String stdout,
        String stderr,
        Integer exitCode,
        long executionTimeMs
){
    public static ExecuteResponse success(String stdout, String stderr, int exitCode, long executionTimeMs) {
        return new ExecuteResponse(exitCode == 0 ? "SUCCESS" : "ERROR", stdout, stderr, exitCode, executionTimeMs);
    }

    public static ExecuteResponse timeout(String stdout, String stderr, long executionTimeMs) {
        return new ExecuteResponse("TIMEOUT", stdout, stderr, null, executionTimeMs);
    }
}