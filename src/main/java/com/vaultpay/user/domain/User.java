package com.vaultpay.user.domain;

import com.vaultpay.common.audit.Auditable;
import jakarta.persistence.*;                         // JPA annotations for ORM mapping. It means we are using Java Persistence API to map this class to a database table. The @Entity annotation indicates that this class is a JPA entity, and @Table specifies the name of the database table. The @Id and @GeneratedValue annotations indicate that the id field is the primary key and will be automatically generated (in this case, as a UUID).
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;          // Collections are a group of objects. They are used to store, retrieve, manipulate, and communicate aggregate data. In our case, we use Collection to represent the authorities (roles/permissions) granted to the user. This allows us to easily manage and check the user's permissions within Spring Security.
import java.util.List;
import java.util.UUID;

/**
 * The User entity — maps to the "users" table defined in V1__create_users.sql.
 *
 * Implements UserDetails → this is what Spring Security needs to do authentication.
 * By implementing this interface, our User class can be used directly by Spring Security
 * without a separate wrapper object.
 *
 * @NoArgsConstructor — JPA requires a no-args constructor to instantiate entities when
 *                      loading from the database. We make it protected so our own code
 *                      must use the builder or all-args constructor.
 *
 * @Builder — generates a fluent builder: User.builder().email("a@b.com").build()
 *            Much cleaner than long constructors with many arguments.  A fluent builder is a design pattern that allows us to create objects in a more readable and flexible way. Instead of using a long constructor with many parameters, we can use a builder to set each property step by step, making the code easier to understand and maintain.
 */  

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)                                // JPA requires a no-args constructor, but we make it protected to encourage using the builder for object creation. It means that we don't want other parts of our code to create User objects using the default constructor (which would require setting all fields manually). Instead, we want them to use the builder pattern, which provides a more readable and convenient way to create User instances. By making the no-args constructor protected, we prevent external code from using it directly, while still allowing JPA to use it when loading entities from the database.
@AllArgsConstructor                                                         // generates a constructor with all fields as parameters, which is used by the builder and can also be used for manual instantiation if needed. This constructor takes all the properties of the User class (id, email, password, fullName) as parameters and initializes the User object with those values. It provides an alternative way to create User instances without using the builder, although in practice we will likely prefer the builder for its readability and flexibility.
public class User extends Auditable implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // NEVER store plaintext — this field holds the BCrypt hash
    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    // ── UserDetails interface implementation ──────────────────────────────────
    // Spring Security calls these methods during authentication.

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // No role-based access in this version — a single user role for all.
        // To add roles later: return user.getRoles().stream().map(...).toList();
        return List.of();   // returns an empty list of authorities
    }

    @Override
    public String getUsername() {
        // Spring Security uses "username". we use email as the unique identifier
        return email;
    }

    @Override
    public boolean isAccountNonExpired()  { return true; }    // we don't have account expiration logic, so we return true to indicate the account is valid indefinitely. In a real application, you might check a field like "accountExpirationDate" against the current date to determine if the account has expired.

    @Override
    public boolean isAccountNonLocked()   { return true; }   

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()            { return true; }
}