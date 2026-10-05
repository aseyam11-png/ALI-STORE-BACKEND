package com.store.ai.controller;

import com.store.ai.dto.AskAliRequest;
import com.store.ai.dto.AskAliResponse;
import com.store.ai.service.AskAliService;
import com.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {
    private final AskAliService askAliService;

    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<AskAliResponse>> ask(@Valid @RequestBody AskAliRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                askAliService.ask(request.prompt()),
                "Ask Ali response generated successfully"));
    }

    @PostMapping("/discover")
    public ResponseEntity<ApiResponse<AskAliResponse>> discover(@Valid @RequestBody AskAliRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                askAliService.discover(request.prompt()),
                "AI discovery response generated successfully"));
    }
}