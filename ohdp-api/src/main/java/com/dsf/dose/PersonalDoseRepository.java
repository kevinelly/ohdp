package com.dsf.dose;
import org.springframework.data.jpa.repository.*;
public interface PersonalDoseRepository extends JpaRepository<PersonalDoseCase,Long>, JpaSpecificationExecutor<PersonalDoseCase> {}
