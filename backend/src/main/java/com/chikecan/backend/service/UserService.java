package com.chikecan.backend.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chikecan.backend.dto.AgentSummaryResponse;
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

  /**
   * ADMINがチケットへ割り当て可能なAGENT候補一覧を取得する。
   * URL側の/api/admin/**制限・Controllerの@PreAuthorizeに加え、
   * Service自身にも@PreAuthorizeを付与し、将来Controller以外から
   * 呼び出されるようになった場合でもADMIN以外は取得できないようにする。
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<AgentSummaryResponse> listAgents() {
    return userRepository.findByRoleAndEnabledTrueOrderByNameAscIdAsc(Role.AGENT).stream()
        .map(AgentSummaryResponse::new)
        .toList();
  }

  /**
   * ログイン中のユーザーをDBから再取得して返す。
   * セッションに保持されたAppUserDetailsはログイン時点のスナップショットであり、
   * XP獲得等の更新を反映しないため、/api/auth/meではこちらを使い常に最新値を返す。
   */
  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
    return new UserResponse(user);
  }
}
