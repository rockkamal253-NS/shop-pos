package com.example.shoppos.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the logged-in principal to its managed entity (for audit fields). */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getCurrent(UserPrincipal principal) {
        return users.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists."));
    }
}
