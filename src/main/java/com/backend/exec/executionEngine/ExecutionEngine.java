package com.backend.exec.executionEngine;

import com.backend.exec.exception.ExecutionFailedException;
import com.backend.exec.language.LanguageConfig;
import com.backend.exec.language.LanguageRegistry;
import com.backend.exec.request.ExecuteRequest;
import com.backend.exec.response.ExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionEngine {

    private static final long TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_BYTES = 1_048_576;

    private final LanguageRegistry languageRegistry;

    @Value("${executor.work-dir:}")
    private String workDirBase;

    public ExecuteResponse execute(ExecuteRequest request) {
        LanguageConfig config = languageRegistry.get(request.language());
        log.debug("Execution started: image={}, file={}", config.image(), config.fileName());

        Path workDir = createWorkDir(config, request.code());
        long start = System.currentTimeMillis();

        try {
            Process process = startContainer(config, workDir);
            log.debug("Container started: image={}, pid={}", config.image(), process.pid());
            writeStdin(process, request.stdin());

            CompletableFuture<String> stdout = readStream(process.getInputStream());
            CompletableFuture<String> stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                log.warn("Execution timed out after {}s: language={}, elapsedMs={}",
                        TIMEOUT_SECONDS, request.language(), elapsed);
                return ExecuteResponse.timeout(
                        stdout.completeOnTimeout("", 1, TimeUnit.SECONDS).join(),
                        "Execution timed out after " + TIMEOUT_SECONDS + " seconds",
                        elapsed);
            }
            ExecuteResponse response = ExecuteResponse.success(stdout.join(), stderr.join(), process.exitValue(), elapsed);
            log.info("Execution finished: status={}, exitCode={}, elapsedMs={}, stdoutChars={}, stderrChars={}",
                    response.status(), response.exitCode(), elapsed,
                    response.stdout().length(), response.stderr().length());
            return response;
        } catch (IOException e) {
            log.error("Failed to start container for language={}: {}", request.language(), e.getMessage());
            throw new ExecutionFailedException("Failed to start execution container. Is Docker running?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Execution interrupted: language={}", request.language());
            throw new ExecutionFailedException("Execution was interrupted", e);
        } finally {
            cleanup(workDir);
        }
    }

    private Path createWorkDir(LanguageConfig config, String code) {
        try {
            Path dir;
            if (workDirBase == null || workDirBase.isBlank()) {
                dir = Files.createTempDirectory("code-exec-");
            } else {
                Path base = Files.createDirectories(Path.of(workDirBase));
                dir = Files.createTempDirectory(base, "code-exec-");
            }
            Files.writeString(dir.resolve(config.fileName()), code, StandardCharsets.UTF_8);
            log.debug("Wrote {} to {}", config.fileName(), dir);
            return dir;
        } catch (IOException e) {
            log.error("Failed to create work directory under '{}': {}", workDirBase, e.getMessage());
            throw new ExecutionFailedException("Failed to write code to a temporary directory", e);
        }
    }

    private Process startContainer(LanguageConfig config, Path workDir) throws IOException {
        List<String> command = List.of(
                "docker", "run",
                "--rm",
                "-i",
                "--network", "none",
                "--memory", "256m",
                "--cpus", "1.0",
                "--pids-limit", "128",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "-v", workDir.toAbsolutePath() + ":/code:ro",
                config.image(),
                "sh", "-c", config.runCommand()
        );
        return new ProcessBuilder(command).start();
    }

    private void writeStdin(Process process, String stdin) {
        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Container may exit before reading stdin — not an error.
            log.debug("Could not write stdin, container likely exited early: {}", e.getMessage());
        }
    }

    private CompletableFuture<String> readStream(InputStream stream) {
        // Runs on another thread, so carry the execId over for log lines.
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return CompletableFuture.supplyAsync(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try (stream) {
                byte[] bytes = stream.readNBytes(MAX_OUTPUT_BYTES);
                String output = new String(bytes, StandardCharsets.UTF_8);
                if (stream.read() != -1) {
                    log.warn("Output truncated at {} bytes", MAX_OUTPUT_BYTES);
                    output += "\n... [output truncated at " + MAX_OUTPUT_BYTES + " bytes]";
                }
                return output;
            } catch (IOException e) {
                log.debug("Failed to read container output: {}", e.getMessage());
                return "";
            } finally {
                MDC.clear();
            }
        });
    }

    private void cleanup(Path workDir) {
        try {
            FileSystemUtils.deleteRecursively(workDir);
            log.debug("Cleaned up {}", workDir);
        } catch (IOException e) {
            // Best-effort cleanup; temp dir will be reaped by the OS eventually.
            log.warn("Failed to clean up {}: {}", workDir, e.getMessage());
        }
    }
}
