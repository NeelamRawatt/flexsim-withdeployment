package com.example.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * ENTITY = a Java class that matches ONE database table.
 * One object of this class = one row in the table.
 *
 * This class matches the table "app_user" (created by V1__create_auth_tables.sql).
 * Each field below matches one column. Example row:
 *     id=1, username="developer", password="$2a$10$...", role="DEVELOPER"
 *
 * Hibernate (the tool behind Spring Data JPA) reads these annotations
 * (the lines that start with @) to know which table and column to use.
 * Because of ddl-auto=validate, Hibernate CHECKS at startup that every field
 * has a matching column. If one is missing, the service refuses to start.
 * That is good: it catches mistakes before real users see them.
 *
 * NOTE: the old monolith linked a user to "experiments" here. Experiments now
 * live in ANOTHER service with ANOTHER database, so that link is removed.
 * experiment-service will store only the user's id number.
 */
@Entity                      // "this class is a table row"
@Table(name = "app_user")    // which table
public class User {

    @Id                                                    // primary key (the unique id)
    @GeneratedValue(strategy = GenerationType.IDENTITY)    // the DATABASE makes the number 1, 2, 3...
    private Long id;

    @Column(nullable = false, unique = true)               // must have a value, no duplicates
    private String username;

    // A BCrypt hash (scrambled password). The real password is never stored.
    @Column(nullable = false)
    private String password;

    @Column
    private String role;

    // Runs automatically just before saving a new or changed user.
    // If no role was given, we use ADMIN (same behaviour as the old app).
    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (role == null || role.isBlank()) {
            role = "ADMIN";
        }
    }

    // Hibernate needs an empty constructor.
    public User() {
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Getters and setters = small methods to read and change the fields.
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}