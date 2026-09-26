package com.backend.exec.request;

import com.backend.exec.language.Language;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteRequest(
        @NotNull(message = "language is required (e.g. java, python, javascript, c, cpp)")
        Language language,

        @NotBlank(message = "code is required")
        @Size(max = 100_000, message = "code must be at most 100000 characters")
        String code,

        @Size(max = 10_000, message = "stdin must be at most 10000 characters")
        String stdin
){
}
