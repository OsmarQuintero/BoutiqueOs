package com.osmar.boutiqueos.customer;

import com.osmar.boutiqueos.config.AccountContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final AccountContext accountContext;

    public CustomerService(CustomerRepository repository, AccountContext accountContext) {
        this.repository = repository;
        this.accountContext = accountContext;
    }

    public List<CustomerResponse> list(String query) {
        Long accountId = accountContext.requireAccountId();
        if (query == null || query.isBlank()) {
            return repository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream().map(CustomerResponse::from).toList();
        }
        return repository.findByAccountIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(accountId, query).stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse get(Long id) {
        return repository.findByIdAndAccountId(id, accountContext.requireAccountId()).map(CustomerResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    public CustomerResponse create(CustomerRequest request) {
        Customer c = new Customer();
        c.setAccountId(accountContext.requireAccountId());
        c.setName(request.name());
        c.setPhone(request.phone());
        c.setNotes(request.notes());
        return CustomerResponse.from(repository.save(c));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer c = repository.findByIdAndAccountId(id, accountContext.requireAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        c.setName(request.name());
        c.setPhone(request.phone());
        c.setNotes(request.notes());
        return CustomerResponse.from(repository.save(c));
    }

    public void delete(Long id) {
        repository.deleteByIdAndAccountId(id, accountContext.requireAccountId());
    }
}
