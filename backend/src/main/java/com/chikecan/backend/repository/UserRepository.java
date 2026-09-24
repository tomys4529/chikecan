package com.chikecan.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  List<User> findByRoleAndEnabledTrueOrderByNameAscIdAsc(Role role);
}
