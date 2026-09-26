package com.backend.exec.controller;

import com.backend.exec.service.CodeExecutionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 2 of the test cases doc: bad requests are rejected before any code runs. */
@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CodeExecutionService codeExecutionService;

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("VA-01 missing language", "{\"code\":\"print(1)\"}"),
                Arguments.of("VA-02 lowercase language", "{\"language\":\"python\",\"code\":\"print(1)\"}"),
                Arguments.of("VA-03 unsupported language", "{\"language\":\"RUBY\",\"code\":\"puts 1\"}"),
                Arguments.of("VA-04 missing code", "{\"language\":\"PYTHON\"}"),
                Arguments.of("VA-05 blank code", "{\"language\":\"PYTHON\",\"code\":\"   \"}"),
                Arguments.of("VA-06 code too long",
                        "{\"language\":\"PYTHON\",\"code\":\"" + "a".repeat(100_001) + "\"}"),
                Arguments.of("VA-07 stdin too long",
                        "{\"language\":\"PYTHON\",\"code\":\"print(1)\",\"stdin\":\"" + "a".repeat(10_001) + "\"}"),
                Arguments.of("VA-08 broken JSON", "{\"language\":\"PYTHON\",")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidRequest(String name, String body) throws Exception {
        mockMvc.perform(post("/api/v1/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(codeExecutionService, never()).execute(any());
    }
}
