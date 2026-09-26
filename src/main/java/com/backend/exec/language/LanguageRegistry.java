package com.backend.exec.language;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LanguageRegistry {

    private static final Map<Language, LanguageConfig> CONFIGS = Map.of(
            Language.PYTHON, new LanguageConfig(
                    "python:3.12-alpine",
                    "main.py",
                    "python3 /code/main.py"),
            Language.JAVASCRIPT, new LanguageConfig(
                    "node:20-alpine",
                    "main.js",
                    "node /code/main.js"),
            Language.JAVA, new LanguageConfig(
                    "eclipse-temurin:21-jdk-alpine",
                    "Main.java",
                    "javac -d /tmp /code/Main.java && java -cp /tmp Main"),
            Language.C, new LanguageConfig(
                    "gcc:13-bookworm",
                    "main.c",
                    "gcc /code/main.c -o /tmp/main && /tmp/main"),
            Language.CPP, new LanguageConfig(
                    "gcc:13-bookworm",
                    "main.cpp",
                    "g++ /code/main.cpp -o /tmp/main && /tmp/main")
    );

    public LanguageConfig get(Language language) {
        return CONFIGS.get(language);
    }
}
