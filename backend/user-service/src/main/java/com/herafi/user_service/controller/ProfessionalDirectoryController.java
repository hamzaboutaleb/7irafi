package com.herafi.user_service.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.PageResponse;
import com.herafi.user_service.dto.ProfessionalCardResponse;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.service.ProfessionalProfileService;

@RestController
@RequestMapping("/users/professionals")
public class ProfessionalDirectoryController {
    private final ProfessionalProfileService professionalProfileService;

    public ProfessionalDirectoryController(ProfessionalProfileService professionalProfileService) {
        this.professionalProfileService = professionalProfileService;
    }

    private static String normalise(String code) {
        if (code == null || code.isBlank())
            return null;

        return code.strip();
    }

    @GetMapping
    public ApiResponse<PageResponse<ProfessionalCardResponse>> listProfessionals(
            @PageableDefault(size = 12, sort = "userId") Pageable pageable,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String cityCode) {

        Page<ProfessionalProfile> profiles = professionalProfileService.getActiveProfessionals(pageable,
                normalise(categoryCode),
                normalise(cityCode));

        PageResponse<ProfessionalCardResponse> response = PageResponse.from(profiles, ProfessionalCardResponse::from);

        return ApiResponse.success(response);
    }
}
