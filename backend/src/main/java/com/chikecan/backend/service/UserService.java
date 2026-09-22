package com.chikecan.backend.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chikecan.backend.dto.RegisterRequest;
import com.chikecan.backend.dto.UserResponse;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.DuplicateEmailException;
import com.chikecan.backend.repository.UserRepository;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public UserResponse register(RegisterRequest request) {
    String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new DuplicateEmailException("このメールアドレスは既に登録されています");
    }

    String passwordHash = passwordEncoder.encode(request.getPassword());
    User user = new User(request.getName(), normalizedEmail, passwordHash, Role.USER, true);
    User saved = userRepository.save(user);

    return new UserResponse(saved);
  }
}
