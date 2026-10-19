package com.example.experiment_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * Tests token checking. The tokens are made here in the test with the same library (jjwt).
 * The secrets below are FAKE test values. They are not used anywhere else.
 */
class JwtTokenVerifierTest {

    // 40 characters, has "-" so it is NOT treated as Base64: used as plain text bytes.
    private static final String TEXT_SECRET = "unit-test-secret-unit-test-secret-123456";
    private static final String OTHER_SECRET = "another-test-secret-another-test-secret-9";

    private final JwtTokenVerifier verifier = new JwtTokenVerifier(TEXT_SECRET);

    private static SecretKey textKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(SecretKey key, String subject, Object role, Object uid, long validForMillis) {
        var builder = Jwts.builder().subject(subject).expiration(new Date(System.currentTimeMillis() + validForMillis));
        if (role != null) {
            builder.claim("role", role);
        }
        if (uid != null) {
            builder.claim("uid", uid);
        }
        return builder.signWith(key).compact();
    }

    @Test
    void acceptsAGoodToken() {
        String jwt = token(textKey(TEXT_SECRET), "neelam", "ADMIN", 7L, 60_000);

        Optional<AuthenticatedUser> user = verifier.verify(jwt);

        assertThat(user).isPresent();
        assertThat(user.get().username()).isEqualTo("neelam");
        assertThat(user.get().userId()).isEqualTo(7L);
        assertThat(user.get().role()).isEqualTo("ADMIN");
    }

    @Test
    void roleIsChangedToUpperCase() {
        String jwt = token(textKey(TEXT_SECRET), "bob", "operator", 8, 60_000);   // uid as Integer on purpose

        assertThat(verifier.verify(jwt)).get().extracting(AuthenticatedUser::role).isEqualTo("OPERATOR");
        assertThat(verifier.verify(jwt)).get().extracting(AuthenticatedUser::userId).isEqualTo(8L);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String jwt = token(textKey(OTHER_SECRET), "neelam", "ADMIN", 7L, 60_000);
        assertThat(verifier.verify(jwt)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String jwt = token(textKey(TEXT_SECRET), "neelam", "ADMIN", 7L, -60_000);
        assertThat(verifier.verify(jwt)).isEmpty();
    }

    @Test
    void rejectsTokenWithoutUidOrRoleOrSubject() {
        assertThat(verifier.verify(token(textKey(TEXT_SECRET), "neelam", "ADMIN", null, 60_000))).isEmpty();
        assertThat(verifier.verify(token(textKey(TEXT_SECRET), "neelam", null, 7L, 60_000))).isEmpty();
        assertThat(verifier.verify(token(textKey(TEXT_SECRET), " ", "ADMIN", 7L, 60_000))).isEmpty();
    }

    @Test
    void rejectsTextThatIsNotAToken() {
        assertThat(verifier.verify("hello")).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }

    @Test
    void rejectsTokenThatWasChanged() {
        String jwt = token(textKey(TEXT_SECRET), "neelam", "OPERATOR", 7L, 60_000);
        String[] parts = jwt.split("\\.");
        // swap the middle part (the data) with data of an admin token: the signature no longer fits
        String adminParts = token(textKey(TEXT_SECRET), "neelam", "ADMIN", 7L, 60_000).split("\\.")[1];
        String tampered = parts[0] + "." + adminParts + "." + parts[2];
        assertThat(verifier.verify(tampered)).isEmpty();
    }

    @Test
    void secretWrittenInBase64IsDecodedLikeAuthService() {
        byte[] bytes = new byte[48];
        Arrays.fill(bytes, (byte) 7);
        String base64Secret = Base64.getEncoder().encodeToString(bytes);   // 64 characters, only Base64 letters
        JwtTokenVerifier base64Verifier = new JwtTokenVerifier(base64Secret);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        assertThat(base64Verifier.verify(token(key, "neelam", "ADMIN", 7L, 60_000))).isPresent();
    }

    @Test
    void tooShortSecretStopsTheStart() {
        assertThatThrownBy(() -> new JwtTokenVerifier("short")).isInstanceOf(RuntimeException.class);
    }
}