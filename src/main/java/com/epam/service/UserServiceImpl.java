package com.epam.service;

import com.epam.error.AccessException;
import com.epam.error.ErrorMessageConstants;
import com.epam.error.TooManyAttemptsException;
import com.epam.model.User;
import com.epam.model.dto.UserActivateDtoInput;
import com.epam.model.dto.UserDtoInput;
import com.epam.model.dto.UserWithPassword;
import com.epam.repo.UserRepo;
import com.epam.util.RandomStringGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepo userRepo;

    private final AuthenticationService authenticationService;

    private final LoginAttemptServiceImpl loginAttemptService;

    private final Counter loginAttemptsCounter;

    private final PasswordEncoder passwordEncoder;

    @Value("${password.length}")
    private int passwordLength;

    public UserServiceImpl(UserRepo userRepo, AuthenticationService authenticationService, MeterRegistry meterRegistry,
                           PasswordEncoder passwordEncoder, LoginAttemptServiceImpl loginAttemptService) {
        this.userRepo = userRepo;
        this.authenticationService = authenticationService;
        this.loginAttemptsCounter = meterRegistry.counter("login_attempts", "outcome", "success");
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional
    public UserWithPassword save(UserDtoInput userDtoInput) {
        log.info("save, userDtoInput = {}", userDtoInput);

        return createEntireUser(userDtoInput);
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        log.info("changePassword, username = {}", username);
        User user = getUserByUsername(username);

        if (authenticationService.checkAccess(oldPassword, user)) {
            throw new AccessException(ErrorMessageConstants.ACCESS_ERROR_MESSAGE);
        }

        user.setPassword(newPassword);
        userRepo.save(user);
    }

    @Override
    public void switchActivate(String username, String password, UserActivateDtoInput userInput) {
        log.info("switchActivate, username = {}", username);

        User user = getUserByUsername(username);
        if (authenticationService.checkAccess(password, user)) {
            throw new AccessException(ErrorMessageConstants.ACCESS_ERROR_MESSAGE);
        }

        user.setIsActive(userInput.getIsActive());
        userRepo.save(user);
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        if (Character.isDigit(username.charAt(username.length() - 1))) {
            String[] parts = username.split("-");
            String userNameWithPostfix = parts[0].trim();
            Integer postfix = Integer.valueOf(parts[1]);
            return userRepo.findByUsernameAndPostfix(userNameWithPostfix, postfix);
        }

        return userRepo.findByUsernameAndPostfix(username, 0);
    }

//    @Override
//    public void login(String username, String password) {
//        log.info("changePassword, userName = {}", username);
//        User user = getUserByUsername(username);
//
//        if (authenticationService.checkAccess(password, user)) {
//            throw new AccessException(ErrorMessageConstants.ACCESS_ERROR_MESSAGE);
//        }
//
//        if (loginAttemptsCounter != null) {
//            loginAttemptsCounter.increment();
//        }
//    }

    private User getUserByUsername(String username) {
        return userRepo.findByUsername(username)
                       .orElseThrow(() -> new AccessException(ErrorMessageConstants.ACCESS_ERROR_MESSAGE));
    }


    public UserWithPassword createEntireUser(UserDtoInput userDtoInput) {
        String rawPassword = RandomStringGenerator.generateRandomString(passwordLength);
        String encodedPassword = passwordEncoder.encode(rawPassword);
        String userName = userDtoInput.getFirstName().toLowerCase() + "." + userDtoInput.getLastName().toLowerCase();
        Integer maxPostfix = 0;

        if (isUsernameExistsInDatabase(userName)) {
            maxPostfix = userRepo.findMaxPostfixByUsername(userName);
            maxPostfix++;
        }

        return UserWithPassword.builder()
                               .firstName(userDtoInput.getFirstName())
                               .lastName(userDtoInput.getLastName())
                               .username(userName)
                               .encodedPassword(encodedPassword)
                               .rawPassword(rawPassword)
                               .postfix(maxPostfix)
                               .isActive(false)
                               .build();

    }

    public boolean isUsernameExistsInDatabase(String username) {
        return userRepo.existsByUsername(username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (loginAttemptService.isBlocked()) {
            throw new TooManyAttemptsException("Too many attempts. You will be unlocked in 5 minutes.");
        }

        User user = findUserByUsername(username)
                            .orElseThrow(
                                    () -> new UsernameNotFoundException("User not found with username: " + username));

        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                                                                 .password(user.getPassword())
                                                                 .roles("USER")
                                                                 .build();
    }
}
