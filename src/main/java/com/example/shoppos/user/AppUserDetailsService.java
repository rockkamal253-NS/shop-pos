package com.example.shoppos.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final LoginAttemptService loginAttemptService;

    public AppUserDetailsService(UserRepository users, LoginAttemptService loginAttemptService) {
        this.users = users;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        boolean nonLocked = !loginAttemptService.isLocked(username);
        return users.findByUsername(username)
                .map(user -> new UserPrincipal(user, nonLocked))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }
}
