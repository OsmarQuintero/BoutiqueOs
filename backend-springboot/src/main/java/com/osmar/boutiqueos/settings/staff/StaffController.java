package com.osmar.boutiqueos.settings.staff;

import com.osmar.boutiqueos.config.CurrentUser;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Cuentas de caja. Solo la duena, y solo en el plan que incluye varios usuarios. */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;
    private final SubscriptionService subscriptionService;
    private final CurrentUser currentUser;

    public StaffController(StaffService staffService, SubscriptionService subscriptionService, CurrentUser currentUser) {
        this.staffService = staffService;
        this.subscriptionService = subscriptionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<StaffRequests.View> list() {
        requireOwnerWithPlan();
        return staffService.list().stream().map(StaffRequests.View::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffRequests.View create(@Valid @RequestBody StaffRequests.Create request) {
        requireOwnerWithPlan();
        return StaffRequests.View.from(staffService.create(request));
    }

    @PutMapping("/{id}")
    public StaffRequests.View update(@PathVariable Long id, @Valid @RequestBody StaffRequests.Update request) {
        requireOwnerWithPlan();
        return StaffRequests.View.from(staffService.update(id, request));
    }

    @PostMapping("/{id}/password")
    public StaffRequests.View resetPassword(@PathVariable Long id, @Valid @RequestBody StaffRequests.NewPassword request) {
        requireOwnerWithPlan();
        return StaffRequests.View.from(staffService.resetPassword(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        requireOwnerWithPlan();
        staffService.delete(id);
    }

    private void requireOwnerWithPlan() {
        // La politica de caja ya lo bloquea en el interceptor; esto es la segunda llave.
        if (currentUser.isCashier()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la duena puede administrar usuarios de caja");
        }
        subscriptionService.requireFeature("multi_user");
    }
}
