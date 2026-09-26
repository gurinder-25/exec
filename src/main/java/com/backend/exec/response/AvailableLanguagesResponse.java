package com.backend.exec.response;

import com.backend.exec.language.Language;

import java.util.List;

public record AvailableLanguagesResponse(List<Language> availableLanguages) {
}
