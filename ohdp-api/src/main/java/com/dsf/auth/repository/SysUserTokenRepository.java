package com.dsf.auth.repository;

import com.dsf.auth.entity.SysUserToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserTokenRepository extends JpaRepository<SysUserToken, Long> {

    Optional<SysUserToken> findByTokenHashAndDeletedFalse(String tokenHash);

    List<SysUserToken> findByUserIdAndDeletedFalse(Long userId);
}
