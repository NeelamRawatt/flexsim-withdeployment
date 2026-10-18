package com.example.auth_service.repository;

import com.example.auth_service.entity.RefreshToken;
import com.example.auth_service.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Database access for refresh tokens.
 *
 * Some methods use @Query. The text inside is JPQL: like SQL, but it uses
 * CLASS names (RefreshToken) and FIELD names (rt.revoked), not table names.
 *
 * @Modifying = this query CHANGES data (update/delete), it does not only read.
 * @Transactional = do it as one safe unit: all of it works, or none of it.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Find a token that has not been cancelled.
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    // All still-valid tokens of one user.
    List<RefreshToken> findByUserAndRevokedFalse(User user);

    // Cancel all active tokens of one user (used when the password changes).
    // Returns how many rows were changed.
    @Modifying
    @Transactional
    @Query("update RefreshToken rt set rt.revoked = true where rt.user = :user and rt.revoked = false and rt.expiresAt > :now")
    int revokeActiveTokens(@Param("user") User user, @Param("now") Instant now);

    // Cancel one token by its text (used at logout).
    @Modifying
    @Transactional
    @Query("update RefreshToken rt set rt.revoked = true where rt.token = :token and rt.revoked = false")
    int revokeByToken(@Param("token") String token);

    // Delete old expired tokens (cleanup).
    void deleteByExpiresAtBefore(Instant cutoff);
}