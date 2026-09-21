package com.herafi.application_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.application_service.dto.ChatContextResponse;
import com.herafi.application_service.service.JobApplicationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/applications")
@RequiredArgsConstructor
public class InternalApplicationController {

    private final JobApplicationService service;

    @GetMapping("/{applicationId}/chat-context")
    public ChatContextResponse getChatContext(@PathVariable UUID applicationId) {
        return service.getChatContext(applicationId);
    }
}
