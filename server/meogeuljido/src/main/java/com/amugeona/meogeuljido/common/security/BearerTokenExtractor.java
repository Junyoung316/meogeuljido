package com.amugeona.meogeuljido.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public class BearerTokenExtractor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private BearerTokenExtractor() {
    }

    public static Optional<String> extract(HttpServletRequest request) {
        String header = request.getHeader(HEADER);

        if (header != null && header.startsWith(PREFIX)) {
            return Optional.of(header.substring(PREFIX.length()));
        }

        return Optional.empty();
    }

}
