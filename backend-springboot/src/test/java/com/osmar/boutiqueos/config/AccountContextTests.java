package com.osmar.boutiqueos.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountContextTests {

    private final AccountContext accountContext = new AccountContext();

    @Test
    void throwsUnauthorizedWhenNoAccountIsSet() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                accountContext::requireAccountId
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void returnsTheSetAccountId() {
        accountContext.setAccountId(202L);

        assertEquals(202L, accountContext.requireAccountId());
    }

    @Test
    void failsClosedAfterClear() {
        accountContext.setAccountId(202L);
        accountContext.clear();

        assertThrows(ResponseStatusException.class, accountContext::requireAccountId);
    }
}
