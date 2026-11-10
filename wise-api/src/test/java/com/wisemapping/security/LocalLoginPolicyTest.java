package com.wisemapping.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLoginPolicyTest {

    private static LocalLoginPolicy policy(String oidcClientId, String allowed) {
        final LocalLoginPolicy p = new LocalLoginPolicy();
        ReflectionTestUtils.setField(p, "oidcClientId", oidcClientId);
        ReflectionTestUtils.setField(p, "allowedAccounts", allowed);
        return p;
    }

    @Test
    void withoutOidc_everyLocalAccountMaySignIn() {
        assertTrue(policy("", "").isLocalLoginAllowedFor("anyone@example.com"));
    }

    @Test
    void withOidcAndNoList_noLocalAccountMaySignIn() {
        assertFalse(policy("client", "").isLocalLoginAllowedFor("bot@example.com"));
        assertFalse(policy("client", null).isLocalLoginAllowedFor("bot@example.com"));
    }

    @Test
    void withOidc_onlyListedAccountsMaySignIn_caseInsensitive() {
        final LocalLoginPolicy p = policy("client", " Bot@Example.com , other@example.com");
        assertTrue(p.isLocalLoginAllowedFor("bot@example.com"));
        assertTrue(p.isLocalLoginAllowedFor("other@example.com"));
        assertFalse(p.isLocalLoginAllowedFor("admin@example.com"));
        assertFalse(p.isLocalLoginAllowedFor(""));
        assertFalse(p.isLocalLoginAllowedFor(null));
    }

    @Test
    void withOidc_listedAccounts_doNotReopenLocalLoginGlobally() {
        // registration / password reset rely on isLocalLoginEnabled(): stays false
        assertFalse(policy("client", "bot@example.com").isLocalLoginEnabled());
    }
}
