package com.chikecan.backend.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    User user = userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
    return new AppUserDetails(user);
  }
}
