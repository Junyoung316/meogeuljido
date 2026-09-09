package com.amugeona.meogeuljido.auth.security;

import com.amugeona.meogeuljido.common.security.AuthenticatedUser;
import com.amugeona.meogeuljido.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails, AuthenticatedUser {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String role;

    private CustomUserDetails(Long id, String email, String passwordHash, String role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * 로그인 시점 - DB에서 조회한 User 전체로부터 생성 (비밀번호 대조에 필요)
     */
    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole().name());
    }

    /**
     * 매 요청 시점(JwtAuthenticationFilter) - JWT 클레임만을 생성, DB 조회 없음
     */
    public static CustomUserDetails fromClaims(Long userId, String role) {
        return new CustomUserDetails(userId, null, null, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

}
