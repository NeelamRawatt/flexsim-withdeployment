package com.example.auth_service.security;

import com.example.auth_service.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UNIT TEST = tests ONE class alone: very fast, no database, no Spring.
 *
 * The pattern in every test is "Arrange, Act, Assert":
 *   Arrange = prepare data, Act = call the method, Assert = check the result.
 * assertThat(...) is the check. If it is false, the test fails (turns red).
 */
class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    private User userWithPassword(String storedPassword) {
        User user = new User("neelam", storedPassword, "ADMIN");
        user.setId(1L);
        return user;
    }

    @Test
    @DisplayName("encodePassword gives a BCrypt hash, not the plain text")
    void encodePasswordGivesBcryptHash() {
        String hash = service.encodePassword("Secret@123");

        assertThat(hash).startsWith("$2");          // BCrypt hashes start with $2a$ or $2b$
        assertThat(hash).isNotEqualTo("Secret@123");
    }

    @Test
    @DisplayName("a correct password matches a BCrypt hash")
    void correctPasswordMatchesHash() {
        User user = userWithPassword(service.encodePassword("Secret@123"));

        assertThat(service.matches(user, "Secret@123")).isTrue();
    }

    @Test
    @DisplayName("a wrong password does not match")
    void wrongPasswordDoesNotMatch() {
        User user = userWithPassword(service.encodePassword("Secret@123"));

        assertThat(service.matches(user, "wrong")).isFalse();
    }

    @Test
    @DisplayName("a plain-text password in the database is NEVER accepted (production safety)")
    void plainTextPasswordIsNeverAccepted() {
        User user = userWithPassword("oldPlainPassword");

        assertThat(service.matches(user, "oldPlainPassword")).isFalse();
    }

    @Test
    @DisplayName("null password or null stored password never matches")
    void nullValuesNeverMatch() {
        assertThat(service.matches(userWithPassword("x"), null)).isFalse();
        assertThat(service.matches(userWithPassword(null), "x")).isFalse();
    }

    @Test
    @DisplayName("normalizeRole cleans the role and falls back to the LOWEST rights")
    void normalizeRoleRules() {
        assertThat(service.normalizeRole(null)).isEqualTo("VIEWER");           // missing -> VIEWER
        assertThat(service.normalizeRole("   ")).isEqualTo("VIEWER");          // blank   -> VIEWER
        assertThat(service.normalizeRole(" operator ")).isEqualTo("OPERATOR"); // trims + upper case
        assertThat(service.normalizeRole("developer")).isEqualTo("DEVELOPER");
        assertThat(service.normalizeRole("hacker")).isEqualTo("VIEWER");       // unknown -> VIEWER
    }
}
