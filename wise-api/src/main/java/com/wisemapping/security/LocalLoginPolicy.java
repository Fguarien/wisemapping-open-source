/*
 *    Copyright [2007-2025] [wisemapping]
 *
 *   Licensed under WiseMapping Public License, Version 1.0 (the "License").
 *   It is basically the Apache License, Version 2.0 (the "License") plus the
 *   "powered by wisemapping" text requirement on every single page;
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the license at
 *
 *       https://github.com/wisemapping/wisemapping-open-source/blob/main/LICENSE.md
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.wisemapping.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether local (database) accounts can sign in with email and password.
 *
 * <p>When the generic OpenID Connect provider (registration id "oidc") is configured, it becomes
 * the way users sign in: database password login, self-registration and password reset are
 * disabled, so every login goes through the identity provider and its policies (MFA, ...).
 * Existing local accounts move to OIDC through the account-link confirmation on their first
 * OIDC login.
 */
@Component
public class LocalLoginPolicy {

    private static final Logger logger = LoggerFactory.getLogger(LocalLoginPolicy.class);

    @Value("${spring.security.oauth2.client.registration.oidc.client-id:}")
    private String oidcClientId;

    /**
     * Comma-separated emails of local (database) accounts that may still sign in with a password while
     * OpenID Connect is configured — service accounts that cannot follow a browser redirect (an
     * automation bot calling the REST API). Empty by default: upstream behaviour, every local login
     * is refused. Registration and password reset stay disabled whatever this list holds.
     */
    @Value("${app.login.local.allowed-accounts:}")
    private String allowedAccounts;

    @PostConstruct
    void logPolicy() {
        if (!isLocalLoginEnabled()) {
            logger.info("OpenID Connect is configured: email/password login, registration and password reset are disabled for local accounts");
            if (allowedAccounts != null && !allowedAccounts.isBlank()) {
                logger.info("Local password login kept for {} listed service account(s) (app.login.local.allowed-accounts)",
                        allowedAccounts.split(",").length);
            }
        }
    }

    public boolean isLocalLoginEnabled() {
        return oidcClientId == null || oidcClientId.isBlank();
    }

    /**
     * Whether this account may sign in with email and password: always without OpenID Connect,
     * otherwise only when listed in {@code app.login.local.allowed-accounts} (case-insensitive).
     */
    public boolean isLocalLoginAllowedFor(String email) {
        if (isLocalLoginEnabled()) {
            return true;
        }
        if (email == null || email.isBlank() || allowedAccounts == null || allowedAccounts.isBlank()) {
            return false;
        }
        final String candidate = email.trim();
        for (String allowed : allowedAccounts.split(",")) {
            if (!allowed.isBlank() && allowed.trim().equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }
}
