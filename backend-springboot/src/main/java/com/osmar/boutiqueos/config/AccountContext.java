package com.osmar.boutiqueos.config;

import org.springframework.stereotype.Component;

@Component
public class AccountContext {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final ThreadLocal<Long> accountIdHolder = new ThreadLocal<>();

    public void setAccountId(Long accountId) {
        accountIdHolder.set(accountId);
    }

    public Long requireAccountId() {
        Long accountId = accountIdHolder.get();
        return accountId == null ? DEFAULT_ACCOUNT_ID : accountId;
    }

    public void clear() {
        accountIdHolder.remove();
    }
}
