package com.cadenly.scheduler.security;

import com.cadenly.scheduler.persistence.UserEntity;
import com.cadenly.scheduler.persistence.UserJpaRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Authenticates by email against the same users table JpaOwnerDirectory reads for owner resolution. */
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userJpaRepository;

    public JpaUserDetailsService(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userJpaRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for email: " + email));
        return new User(user.getEmail(), user.getPasswordHash(), List.of());
    }
}
