package com.osmar.boutiqueos.customer;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.subscription.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final AccountContext accountContext;
    private final SubscriptionService subscriptionService;

    public CustomerService(CustomerRepository repository, AccountContext accountContext, SubscriptionService subscriptionService) {
        this.repository = repository;
        this.accountContext = accountContext;
        this.subscriptionService = subscriptionService;
    }

    public List<CustomerResponse> list(String query) {
        Long accountId = accountContext.requireAccountId();
        if (query == null || query.isBlank()) {
            return repository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream().map(CustomerResponse::from).toList();
        }
        return repository.findByAccountIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(accountId, query).stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse get(Long id) {
        return CustomerResponse.from(requireCustomer(id));
    }

    public CustomerResponse create(CustomerRequest request) {
        subscriptionService.checkLimits("customer");
        Customer c = new Customer();
        c.setAccountId(accountContext.requireAccountId());
        c.setName(request.name());
        c.setPhone(request.phone());
        c.setNotes(request.notes());
        return CustomerResponse.from(repository.save(c));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer c = requireCustomer(id);
        c.setName(request.name());
        c.setPhone(request.phone());
        c.setNotes(request.notes());
        return CustomerResponse.from(repository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(requireCustomer(id));
    }

    private Customer requireCustomer(Long id) {
        return repository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
    }
}
