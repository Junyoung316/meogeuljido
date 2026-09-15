package com.amugeona.meogeuljido.auth.security;

import com.amugeona.meogeuljido.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(CustomUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
