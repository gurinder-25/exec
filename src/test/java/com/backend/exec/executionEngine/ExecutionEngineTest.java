package com.backend.exec.executionEngine;

import com.backend.exec.language.Language;
import com.backend.exec.language.LanguageRegistry;
import com.backend.exec.request.ExecuteRequest;
import com.backend.exec.response.ExecuteResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sections 1, 3, 4 and 5 of the test cases doc, run against real Docker.
 * Skipped when Docker isn't running.
 */
class ExecutionEngineTest {

    private static final int MAX_OUTPUT_BYTES = 1_048_576;

    private final ExecutionEngine engine = new ExecutionEngine(new LanguageRegistry());

    @BeforeAll
    static void requireDocker() {
        assumeTrue(dockerIsRunning(), "Docker is not running; skipping execution tests");
    }

    private ExecuteResponse run(Language language, String code) {
        return run(language, code, null);
    }

    private ExecuteResponse run(Language language, String code, String stdin) {
        return engine.execute(new ExecuteRequest(language, code, stdin));
    }

    // 1 · Happy path

    static Stream<Arguments> helloWorld() {
        return Stream.of(
                Arguments.of("HP-02", Language.PYTHON, "print(\"Hello, World!\")"),
                Arguments.of("HP-03", Language.JAVASCRIPT, "console.log(\"Hello, World!\");"),
                Arguments.of("HP-04", Language.JAVA, """
                        public class Main {
                          public static void main(String[] a) {
                            System.out.println("Hello, World!");
                          }
                        }"""),
                Arguments.of("HP-05", Language.C, """
                        #include <stdio.h>
                        int main() { printf("Hello, World!\\n"); return 0; }"""),
                Arguments.of("HP-06", Language.CPP, """
                        #include <iostream>
                        int main() { std::cout << "Hello, World!" << std::endl; }""")
        );
    }

    @ParameterizedTest(name = "{0} hello world in {1}")
    @MethodSource("helloWorld")
    void helloWorld(String id, Language language, String code) {
        ExecuteResponse response = run(language, code);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.stdout()).isEqualTo("Hello, World!\n");
        assertThat(response.exitCode()).isZero();
    }

    static Stream<Arguments> stdin() {
        return Stream.of(
                Arguments.of("HP-07", Language.PYTHON, "print(\"Hi \" + input())"),
                Arguments.of("HP-08", Language.JAVASCRIPT, """
                        const s = require("fs").readFileSync(0, "utf8").trim();
                        console.log("Hi " + s);"""),
                Arguments.of("HP-09", Language.JAVA, """
                        import java.util.Scanner;
                        public class Main {
                          public static void main(String[] a) {
                            System.out.println("Hi " + new Scanner(System.in).nextLine());
                          }
                        }"""),
                Arguments.of("HP-10", Language.C, """
                        #include <stdio.h>
                        int main() { char s[100]; scanf("%99s", s); printf("Hi %s\\n", s); }"""),
                Arguments.of("HP-11", Language.CPP, """
                        #include <iostream>
                        #include <string>
                        int main() { std::string s; std::cin >> s; std::cout << "Hi " << s << std::endl; }""")
        );
    }

    @ParameterizedTest(name = "{0} stdin in {1}")
    @MethodSource("stdin")
    void stdin(String id, Language language, String code) {
        ExecuteResponse response = run(language, code, "Ada");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.stdout()).isEqualTo("Hi Ada\n");
    }

    static Stream<Arguments> noOutput() {
        return Stream.of(
                Arguments.of("HP-12", Language.PYTHON, "x = 1"),
                Arguments.of("HP-13", Language.JAVASCRIPT, "let x = 1;"),
                Arguments.of("HP-14", Language.JAVA, "public class Main { public static void main(String[] a) {} }"),
                Arguments.of("HP-15", Language.C, "int main() { return 0; }"),
                Arguments.of("HP-16", Language.CPP, "int main() { return 0; }")
        );
    }

    @ParameterizedTest(name = "{0} no output in {1}")
    @MethodSource("noOutput")
    void noOutput(String id, Language language, String code) {
        ExecuteResponse response = run(language, code);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.stdout()).isEmpty();
        assertThat(response.stderr()).isEmpty();
        assertThat(response.exitCode()).isZero();
    }

    // 3 · Program failures

    static Stream<Arguments> runtimeException() {
        return Stream.of(
                Arguments.of("PF-01", Language.PYTHON, "print(1 / 0)", 1, "ZeroDivisionError"),
                Arguments.of("PF-02", Language.JAVASCRIPT, "null.x;", 1, "TypeError"),
                Arguments.of("PF-03", Language.JAVA, """
                        public class Main {
                          public static void main(String[] a) {
                            int[] x = new int[1];
                            x[2] = 1;
                          }
                        }""", 1, "ArrayIndexOutOfBoundsException"),
                Arguments.of("PF-04", Language.C, "int main() { int *p = 0; *p = 1; }", 139, ""),
                Arguments.of("PF-05", Language.CPP, """
                        #include <stdexcept>
                        int main() { throw std::runtime_error("boom"); }""", 134, "boom")
        );
    }

    @ParameterizedTest(name = "{0} runtime exception in {1}")
    @MethodSource("runtimeException")
    void runtimeException(String id, Language language, String code, int exitCode, String stderrPart) {
        ExecuteResponse response = run(language, code);

        assertThat(response.status()).isEqualTo("ERROR");
        assertThat(response.exitCode()).isEqualTo(exitCode);
        assertThat(response.stderr()).contains(stderrPart);
    }

    static Stream<Arguments> compileError() {
        return Stream.of(
                Arguments.of("PF-06", Language.PYTHON, "print(\"hi\"", "SyntaxError"),
                Arguments.of("PF-07", Language.JAVASCRIPT, "let = ;", "SyntaxError"),
                Arguments.of("PF-08", Language.JAVA, """
                        public class Main {
                          public static void main(String[] a) { int x = }
                        }""", "error:"),
                Arguments.of("PF-09", Language.C, "int main( { return 0; }", "error:"),
                Arguments.of("PF-10", Language.CPP, "int main() { int x = }", "error:")
        );
    }

    @ParameterizedTest(name = "{0} compile error in {1}")
    @MethodSource("compileError")
    void compileError(String id, Language language, String code, String stderrPart) {
        ExecuteResponse response = run(language, code);

        assertThat(response.status()).isEqualTo("ERROR");
        assertThat(response.exitCode()).isEqualTo(1);
        assertThat(response.stdout()).isEmpty();
        assertThat(response.stderr()).contains(stderrPart);
    }

    // 4 · Timeouts & output limits

    @Nested
    class TimeoutsAndOutputLimits {

        @Test
        void to01_infiniteLoopHitsTheTimeout() {
            ExecuteResponse response = run(Language.PYTHON, "while True: pass");

            assertThat(response.status()).isEqualTo("TIMEOUT");
            assertThat(response.exitCode()).isNull();
            assertThat(response.stderr()).isEqualTo("Execution timed out after 10 seconds");
            assertThat(response.executionTimeMs()).isBetween(10_000L, 12_000L);
        }

        @Test
        void to02_outputBeforeTimeoutIsKept() {
            ExecuteResponse response = run(Language.PYTHON, """
                    print("started", flush=True)
                    while True: pass""");

            assertThat(response.status()).isEqualTo("TIMEOUT");
            assertThat(response.stdout()).isEqualTo("started\n");
        }

        @Test
        void to03_justUnderTheLimit() {
            ExecuteResponse response = run(Language.PYTHON, """
                    import time
                    time.sleep(7)
                    print("done")""");

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(response.stdout()).isEqualTo("done\n");
            assertThat(response.executionTimeMs()).isGreaterThanOrEqualTo(7_000L);
        }

        @Test
        void to04_stdoutOverOneMegabyteIsCutOff() {
            ExecuteResponse response = run(Language.PYTHON, "print(\"a\" * 2000000)");

            assertThat(response.stdout())
                    .startsWith("a".repeat(MAX_OUTPUT_BYTES))
                    .endsWith("... [output truncated at " + MAX_OUTPUT_BYTES + " bytes]");
            assertThat(response.executionTimeMs()).isLessThan(10_000L);
        }

        @Test
        void to05_endlessOutputIsCutOff() {
            ExecuteResponse response = run(Language.PYTHON, "while True: print(\"x\")");

            assertThat(response.stdout()).endsWith("... [output truncated at " + MAX_OUTPUT_BYTES + " bytes]");
            assertThat(response.executionTimeMs()).isLessThan(12_000L);
        }
    }

    // 5 · Sandbox

    @Nested
    class Sandbox {

        @Test
        void sb01_noNetwork() {
            ExecuteResponse response = run(Language.PYTHON, """
                    import urllib.request
                    urllib.request.urlopen("http://example.com", timeout=3)""");

            assertThat(response.status()).isEqualTo("ERROR");
            assertThat(response.stderr()).contains("URLError");
        }

        @Test
        void sb02_codeDirectoryIsReadOnly() {
            ExecuteResponse response = run(Language.PYTHON, "open(\"/code/x.txt\", \"w\")");

            assertThat(response.status()).isEqualTo("ERROR");
            assertThat(response.stderr()).contains("Read-only file system");
        }

        @Test
        void sb03_memoryIsCapped() {
            ExecuteResponse response = run(Language.PYTHON, """
                    b = b"a" * (600 * 1024 * 1024)
                    print("allocated")""");

            assertThat(response.status()).isEqualTo("ERROR");
            assertThat(response.exitCode()).isEqualTo(137);
            assertThat(response.stdout()).doesNotContain("allocated");
        }

        @Test
        void sb04_processesAreCapped() {
            ExecuteResponse response = run(Language.PYTHON, """
                    import threading, time
                    for i in range(1000):
                        threading.Thread(target=time.sleep, args=(5,)).start()""");

            assertThat(response.status()).isEqualTo("ERROR");
            assertThat(response.stderr()).contains("can't start new thread");
        }

        @Test
        void sb05_noLinuxCapabilities() {
            ExecuteResponse response = run(Language.PYTHON, """
                    import os
                    os.chown("/tmp", 1234, 1234)""");

            assertThat(response.status()).isEqualTo("ERROR");
            assertThat(response.stderr()).contains("Operation not permitted");
        }
    }

    private static boolean dockerIsRunning() {
        try {
            Process process = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
