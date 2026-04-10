package com.dsf.ohdp;

import com.dsf.ohdp.entity.ProjectStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OhdpSmokeTest {

    @Test
    void projectStatusShouldContainReportReady() {
        Assertions.assertNotNull(ProjectStatus.valueOf("REPORT_READY"));
    }
}
