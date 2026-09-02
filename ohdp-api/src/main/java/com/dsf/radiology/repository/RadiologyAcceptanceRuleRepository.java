package com.dsf.radiology.repository;

import com.dsf.radiology.entity.RadiologyAcceptanceRule;
import com.dsf.radiology.model.RadiologyModality;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadiologyAcceptanceRuleRepository extends JpaRepository<RadiologyAcceptanceRule, Long> {
    List<RadiologyAcceptanceRule> findByDeletedFalseAndModalityOrderByItemCodeAsc(RadiologyModality modality);

    Optional<RadiologyAcceptanceRule> findFirstByDeletedFalseAndEnabledTrueAndModalityAndItemCodeOrderByRuleVersionDesc(
            RadiologyModality modality, String itemCode);
}
