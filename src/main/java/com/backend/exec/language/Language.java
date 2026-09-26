package com.backend.exec.language;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Language {
    PYTHON,
    JAVASCRIPT,
    JAVA,
    C,
    CPP;

    @JsonCreator
    public static Language fromValue(String value) {
        return Language.valueOf(value);
    }
}
