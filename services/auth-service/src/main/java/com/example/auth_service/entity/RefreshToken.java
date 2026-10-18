package com.example.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Matches the table "auth_refresh_token".
 *
 * A refresh token is a long-life ticket. When the short login token (10 minutes)
 * ends, the browser shows this ticket to get a new one, so the user does not
 * have to type the password again. We store it in the database so we can
 * cancel ("revoke") it at logout.
 *
 * Example row: token="a1b2c3...", user_id=1, expires_at=..., revoked=false
 */
@Entity
@Table(name = "auth_refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The long random text. Must be unique.
    @Column(nullable = false, unique = true, length = 200)
    private String token;

    // MANY tokens belong to ONE user. In the table this is the column user_id.
    // LAZY = do not load the user from the database until somebody asks for it
    // (saves time).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Instant = one exact moment in time (we store it in UTC).
    @Column(nullable = false)
    private Instant expiresAt;

    // true = this token was cancelled (logout, or used once already).
    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Runs just before the first save: fills createdAt with "now".
    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public RefreshToken() {
    }

    public RefreshToken(String token, User user, Instant expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}