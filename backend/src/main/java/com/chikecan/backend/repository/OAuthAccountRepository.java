package com.chikecan.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chikecan.backend.entity.OAuthAccount;
import com.chikecan.backend.entity.OAuthProvider;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

  Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
