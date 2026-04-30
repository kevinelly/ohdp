package com.dsf.system.repository;

import com.dsf.system.entity.SysDepartment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDepartmentRepository extends JpaRepository<SysDepartment, Long> {

    List<SysDepartment> findAllByDeletedFalseOrderBySortNoAsc();

    List<SysDepartment> findAllByEnabledAndDeletedFalseOrderBySortNoAsc(Boolean enabled);

    List<SysDepartment> findByParentIdAndDeletedFalseOrderBySortNoAsc(Long parentId);

    Optional<SysDepartment> findByIdAndDeletedFalse(Long id);
}
