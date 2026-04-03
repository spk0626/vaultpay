package com.vaultpay.user.repository;

import com.vaultpay.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for User.
 *
 * JpaRepository<User, UUID> gives us for FREE:
 *   save(), findById(), findAll(), delete(), count(), existsById(), ...
 *
 * Spring Data auto-generates the implementation of findByEmail() at startup by parsing the method name: "findBy" + "Email" → WHERE email = ?
 *
 * No SQL, no boilerplate. This interface IS the complete implementation.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);  //this method signature tells Spring Data JPA to generate a query that finds a User entity based on the email field. The "findByEmail" part is parsed by Spring Data to understand that we want to search for a User where the "email" column matches the provided parameter. The return type Optional<User> indicates that the result may or may not contain a User, allowing us to handle the case where no user with the given email exists without risking a NullPointerException.

    boolean existsByEmail(String email);  
}