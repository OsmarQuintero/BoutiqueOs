package com.osmar.boutiqueos.customer;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<CustomerResponse> list(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll().stream().map(CustomerResponse::from).toList();
        }
        return repository.findByNameContainingIgnoreCase(query).stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse get(Long id) {
        return repository.findById(id).map(CustomerResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    public CustomerResponse create(CustomerRequest request) {
        Customer c = new Customer();
        c.setName(request.name());
        c.setPhone(request.phone());
        c.setNotes(request.notes());
        return CustomerResponse.from(repository.save(c));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        c.setName(request.name());
        c.setPhone(request.phone());
        c.setNotes(request.notes());
        return CustomerResponse.from(repository.save(c));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
