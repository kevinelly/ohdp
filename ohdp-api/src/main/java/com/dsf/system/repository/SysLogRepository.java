package com.dsf.system.repository;

import com.dsf.system.entity.SysLog;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysLogRepository extends JpaRepository<SysLog, Long> {

    @Query("SELECT l FROM SysLog l WHERE " +
            "(:userName IS NULL OR l.userName LIKE %:userName%) AND " +
            "(:module IS NULL OR l.module = :module) AND " +
            "(:resultType IS NULL OR l.resultType = :resultType) AND " +
            "(:startTime IS NULL OR l.createdAt >= :startTime) AND " +
            "(:endTime IS NULL OR l.createdAt <= :endTime) " +
            "ORDER BY l.createdAt DESC")
    Page<SysLog> queryLogs(
            @Param("userName") String userName,
            @Param("module") String module,
            @Param("resultType") String resultType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
