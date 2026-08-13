package com.osmar.boutiqueos.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccountContext {

    private final ThreadLocal<Long> accountIdHolder = new ThreadLocal<>();

    public void setAccountId(Long accountId) {
        accountIdHolder.set(accountId);
    }

    public Long requireAccountId() {
        Long accountId = accountIdHolder.get();
        if (accountId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account context missing");
        }
        return accountId;
    }

    public void clear() {
        accountIdHolder.remove();
    }
}
