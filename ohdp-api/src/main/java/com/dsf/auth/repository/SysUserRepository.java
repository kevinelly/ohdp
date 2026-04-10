package com.dsf.auth.repository;

import com.dsf.auth.entity.SysUser;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsernameAndDeletedFalse(String username);

    Optional<SysUser> findByIdAndDeletedFalse(Long id);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmployeeIdAndDeletedFalse(Long employeeId);

    boolean existsByEmployeeIdAndDeletedFalseAndIdNot(Long employeeId, Long id);

    Page<SysUser> findByDeletedFalseAndUsernameContaining(String username, Pageable pageable);
}
