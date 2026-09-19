package com.amugeona.meogeuljido.auth.security;

import com.amugeona.meogeuljido.auth.redis.TokenBlacklistRepository;
import com.amugeona.meogeuljido.common.security.BearerTokenExtractor;
import com.amugeona.meogeuljido.common.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = BearerTokenExtractor.extract(request).orElse(null);
        if (token != null) {
            jwtTokenProvider.parseAccessToken(token).ifPresent(claims -> {
                if (tokenBlacklistRepository.isRejected(token, claims.userId(), claims.issuedAt())) {
                    return;
                }
               CustomUserDetails principal = CustomUserDetails.fromClaims(claims.userId(), claims.role());
               var authentication = new UsernamePasswordAuthenticationToken(
                       principal, null, principal.getAuthorities()
               );
               SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

}
