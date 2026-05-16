package com.rostra.auth.service;

import com.rostra.auth.entity.User;
import com.rostra.auth.repository.UserRepository;
import com.rostra.auth.dto.AuthResponseDTO;
import com.rostra.auth.dto.LoginRequestDTO;
import com.rostra.auth.dto.RegisterRequestDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.max-age-seconds}")
    private int cookieMaxAge;

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request, HttpServletResponse response) {
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already in use");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .build();

        user = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = refreshTokenService.issueRefreshToken(user);
        setRefreshTokenCookie(response, rawRefreshToken);

        return AuthResponseDTO.of(accessToken, user.getId(), user.getEmail(), user.getDisplayName());
    }

    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = refreshTokenService.issueRefreshToken(user);
        setRefreshTokenCookie(response, rawRefreshToken);

        return AuthResponseDTO.of(accessToken, user.getId(), user.getEmail(), user.getDisplayName());
    }

    @Transactional
    public AuthResponseDTO refresh(String rawRefreshToken) {
        String newRawRefreshToken = refreshTokenService.rotate(rawRefreshToken);
        User user = refreshTokenService.getUserFromToken(newRawRefreshToken);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return AuthResponseDTO.of(accessToken, user.getId(), user.getEmail(), user.getDisplayName());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeAll(rawRefreshToken);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie("refresh_token", rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(cookieMaxAge);
        response.addCookie(cookie);
    }
}
