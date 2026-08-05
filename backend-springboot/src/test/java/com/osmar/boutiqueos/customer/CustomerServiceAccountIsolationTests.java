package com.osmar.boutiqueos.customer;

import com.osmar.boutiqueos.config.AccountContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CustomerServiceAccountIsolationTests {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountContext accountContext;

    @AfterEach
    void cleanup() {
        accountContext.clear();
        customerRepository.deleteAll();
    }

    @Test
    void deletesOnlyTheCustomerFromTheCurrentAccount() {
        Customer firstAccountCustomer = customerRepository.save(customer(101L, "Cliente Cuenta 1"));
        Customer secondAccountCustomer = customerRepository.save(customer(202L, "Cliente Cuenta 2"));

        accountContext.setAccountId(101L);
        customerService.delete(firstAccountCustomer.getId());

        assertFalse(customerRepository.existsById(firstAccountCustomer.getId()));
        assertTrue(customerRepository.existsById(secondAccountCustomer.getId()));
    }

    private Customer customer(Long accountId, String name) {
        Customer customer = new Customer();
        customer.setAccountId(accountId);
        customer.setName(name);
        return customer;
    }
}
