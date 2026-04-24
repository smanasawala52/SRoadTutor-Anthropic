package com.sroadtutor.auth.repository;

import com.sroadtutor.auth.model.AuthProvider;
import com.sroadtutor.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByAuthProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
