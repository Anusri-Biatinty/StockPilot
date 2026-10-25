package com.stockpilot.repository;

import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRole_NameInAndEnabledTrue(Collection<RoleName> roles);

    long countByRole_NameAndEnabledTrue(RoleName role);
}
