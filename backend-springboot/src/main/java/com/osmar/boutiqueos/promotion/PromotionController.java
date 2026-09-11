package com.osmar.boutiqueos.promotion;

import com.osmar.boutiqueos.subscription.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final SubscriptionService subscriptionService;

    public PromotionController(PromotionService promotionService, SubscriptionService subscriptionService) {
        this.promotionService = promotionService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public List<Promotion> list() {
        subscriptionService.requireFeature("promotions");
        return promotionService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Promotion create(@Valid @RequestBody PromotionRequest request) {
        subscriptionService.requireFeature("promotions");
        return promotionService.create(request);
    }

    @PutMapping("/{id}")
    public Promotion update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        subscriptionService.requireFeature("promotions");
        return promotionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        subscriptionService.requireFeature("promotions");
        promotionService.delete(id);
    }
}
