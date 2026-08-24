package org.raul.javawebscarper.auth;

/**
 * Ephemeral credentials received from an authorized admin request. They must never be persisted.
 */
public record SocialAuthCredentials(String login, String password) {
}
