package com.vaultpay.user.service;

import com.vaultpay.common.exception.BusinessException;
import com.vaultpay.security.jwt.JwtService;
import com.vaultpay.user.domain.User;
import com.vaultpay.user.dto.UserDtos;
import com.vaultpay.user.mapper.UserMapper;
import com.vaultpay.user.repository.UserRepository;
import com.vaultpay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UserService handles user registration and authentication.
 *
 * Also implements UserDetailsService — Spring Security's interface for loading
 * a user by username (email) during authentication. By implementing it here,
 * we avoid a separate UserDetailsServiceImpl class.
 *
 * @Transactional on register(): we create a user AND a wallet in one DB transaction.
 * If wallet creation fails, the user record is also rolled back — data stays consistent.
 *
 * NOTE: We use constructor injection (via @RequiredArgsConstructor) everywhere.
 * Field injection (@Autowired) is discouraged because:
 *   - It makes dependencies hidden
 *   - The class can't be instantiated normally (hard to unit test)
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository     userRepository;
    private final WalletService      walletService;
    private final PasswordEncoder    passwordEncoder;
    private final JwtService         jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper         userMapper;

    /**
     * Register a new user and automatically create their wallet.
     *
     * FLOW: validate email uniqueness → hash password → save user → create wallet → issue JWT
     */
    @Transactional                                                                     // ensures that if wallet creation fails, the user record is rolled back. This keeps our data consistent — we won't have users without wallets.
    public UserDtos.AuthResponse register(UserDtos.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw BusinessException.conflict("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.email().toLowerCase().trim())
                .password(passwordEncoder.encode(request.password()))  // BCrypt hash — never plaintext
                .fullName(request.fullName().trim())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Auto-create a wallet for the new user within the same transaction
        walletService.createWallet(user);

        String token = jwtService.generateToken(user);
        return new UserDtos.AuthResponse(token, userMapper.toUserResponse(user));
    }

    /**
     * Authenticate an existing user and return a JWT.
     *
     * AuthenticationManager.authenticate() internally:
     *   1. Calls loadUserByUsername(email) to get the stored user
     *   2. Calls passwordEncoder.matches(rawPassword, storedHash) to verify
     *   3. Throws BadCredentialsException if anything doesn't match
     */
    public UserDtos.AuthResponse login(UserDtos.LoginRequest request) {
        // This single call does all the heavy lifting
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // If we reach here, authentication succeeded
        User user = findByEmail(request.email());
        String token = jwtService.generateToken(user);
        log.info("User logged in: {}", user.getEmail());

        return new UserDtos.AuthResponse(token, userMapper.toUserResponse(user));
    }

    /**
     * Get a user's profile by their ID.
     */
    @Transactional(readOnly = true)                                // readOnly since we're just fetching data, not modifying it. This can optimize performance by avoiding unnecessary locking and allowing certain database optimizations.
    public UserDtos.UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        return userMapper.toUserResponse(user);
    }

    // ── UserDetailsService implementation ─────────────────────────────────────

    /**
     * Called by Spring Security during authentication to load the user by email.
     * Must throw UsernameNotFoundException (not null) if user doesn't exist.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // ── Package-level helpers (used by controllers and other services) ────────

    /**
     * Returns the domain User entity by ID.
     * Used by TransactionService when loading the receiver of a transfer.
     */
    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));
    }

    /**
     * Returns the domain User entity. Used by other services (e.g. WalletService)
     * that need the full entity to perform operations.
     */
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    /**
     * Returns a mapped DTO. Used by controllers that only need to present user data.
     * Keeps the entity from ever leaking to the API layer.
     */
    @Transactional(readOnly = true)
    public UserDtos.UserResponse findByEmailAsDto(String email) {
        return userMapper.toUserResponse(findByEmail(email));
    }
}