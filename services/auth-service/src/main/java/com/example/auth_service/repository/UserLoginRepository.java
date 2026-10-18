package com.example.auth_service.repository;

import com.example.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITORY = the class that talks to the database for us.
 * We write only the method NAMES. Spring Data reads each name and writes the
 * SQL by itself. We write no SQL here.
 *
 * Free methods from JpaRepository: save(), findById(), findAll(), deleteById(), count()...
 *
 * Our extra methods (name -> SQL Spring creates):
 *   findByUsernameIgnoreCase("Dev")   -> select * from app_user where lower(username) = lower('Dev')
 *   existsByUsernameIgnoreCase("dev") -> is there at least one such row? (true/false)
 *   existsByRoleIgnoreCase("ADMIN")   -> is there a user with this role? (true/false)
 *   findByRoleInOrderByUsernameAsc    -> users whose role is in the list, sorted by name
 */
public interface UserLoginRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRoleIgnoreCase(String role);

    List<User> findByRoleInOrderByUsernameAsc(Collection<String> roles);
}