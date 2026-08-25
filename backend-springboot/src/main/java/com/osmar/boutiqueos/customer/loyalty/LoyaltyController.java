package com.osmar.boutiqueos.customer.loyalty;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/transactions/{customerId}")
    public List<LoyaltyTransactionResponse> getHistory(@PathVariable Long customerId) {
        return loyaltyService.getHistory(customerId);
    }

    @PostMapping("/redeem/{customerId}")
    public LoyaltyTransactionResponse redeem(@PathVariable Long customerId, @Valid @RequestBody RedeemRequest request) {
        return loyaltyService.redeem(customerId, request);
    }

    @PostMapping("/adjust/{customerId}")
    public LoyaltyTransactionResponse adjust(
            @PathVariable Long customerId,
            @RequestParam int points,
            @RequestParam(required = false) String reason
    ) {
        return loyaltyService.adjust(customerId, points, reason);
    }

    @GetMapping("/rewards")
    public List<LoyaltyRewardResponse> getActiveRewards() {
        return loyaltyService.getActiveRewards();
    }

    @GetMapping("/rewards/all")
    public List<LoyaltyRewardResponse> getAllRewards() {
        return loyaltyService.getAllRewards();
    }

    @PostMapping("/rewards")
    public LoyaltyRewardResponse createReward(@Valid @RequestBody LoyaltyRewardRequest request) {
        return loyaltyService.createReward(request);
    }

    @PutMapping("/rewards/{id}")
    public LoyaltyRewardResponse updateReward(@PathVariable Long id, @Valid @RequestBody LoyaltyRewardRequest request) {
        return loyaltyService.updateReward(id, request);
    }

    @DeleteMapping("/rewards/{id}")
    public void deleteReward(@PathVariable Long id) {
        loyaltyService.deleteReward(id);
    }
}
