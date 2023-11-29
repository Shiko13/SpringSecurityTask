package com.epam.service;

import com.epam.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean checkAccess(String password, User user) {
        return passwordEncoder.matches(password, user.getPassword() );
    }
}
