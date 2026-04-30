package com.dsf.ohdp.repository;

import com.dsf.ohdp.entity.OhdpDevice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OhdpDeviceRepository extends JpaRepository<OhdpDevice, Long> {

    Page<OhdpDevice> findByDeletedFalse(Pageable pageable);

    Page<OhdpDevice> findByDeviceTypeAndDeletedFalse(String deviceType, Pageable pageable);

    Page<OhdpDevice> findByStatusAndDeletedFalse(String status, Pageable pageable);

    Page<OhdpDevice> findByDeviceTypeAndStatusAndDeletedFalse(String deviceType, String status, Pageable pageable);

    @Query("SELECT d FROM OhdpDevice d WHERE d.deleted = false AND " +
            "(LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.deviceModel) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<OhdpDevice> findByKeywordAndDeletedFalse(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT d FROM OhdpDevice d WHERE d.deleted = false AND d.deviceType = :deviceType AND " +
            "(LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.deviceModel) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<OhdpDevice> findByKeywordAndDeviceTypeAndDeletedFalse(@Param("keyword") String keyword,
            @Param("deviceType") String deviceType, Pageable pageable);

    @Query("SELECT d FROM OhdpDevice d WHERE d.deleted = false AND d.status = :status AND " +
            "(LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.deviceModel) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<OhdpDevice> findByKeywordAndStatusAndDeletedFalse(@Param("keyword") String keyword,
            @Param("status") String status, Pageable pageable);

    @Query("SELECT d FROM OhdpDevice d WHERE d.deleted = false AND d.deviceType = :deviceType AND d.status = :status AND " +
            "(LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.deviceModel) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<OhdpDevice> findByKeywordAndDeviceTypeAndStatusAndDeletedFalse(@Param("keyword") String keyword,
            @Param("deviceType") String deviceType, @Param("status") String status, Pageable pageable);

    Optional<OhdpDevice> findByIdAndDeletedFalse(Long id);
}
