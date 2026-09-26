package com.backend.exec.controller;

import com.backend.exec.language.Language;
import com.backend.exec.response.AvailableLanguagesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/languages")
public class LanguageController {

    @GetMapping("")
    public ResponseEntity<AvailableLanguagesResponse> listLanguages() {
        return ResponseEntity.ok(new AvailableLanguagesResponse(List.of(Language.values())));
    }
}
