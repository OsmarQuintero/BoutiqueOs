package com.osmar.boutiqueos.onboarding;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/start")
    public OnboardingStartResponse start(@Valid @RequestBody OnboardingStartRequest request) {
        return onboardingService.start(request);
    }

    @PostMapping("/complete")
    public OnboardingCompleteResponse complete(@Valid @RequestBody OnboardingCompleteRequest request) {
        return onboardingService.complete(request);
    }
}
