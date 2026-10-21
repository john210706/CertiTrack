package com.certitrack.repository;

import com.certitrack.model.entity.User;
import com.certitrack.model.enums.Role;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(Role role);
    long countByRole(Role role);
    Page<User> findByRole(Role role, Pageable pageable);
}
