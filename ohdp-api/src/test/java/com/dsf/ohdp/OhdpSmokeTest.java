package com.dsf.ohdp;

import com.dsf.ohdp.entity.ProjectStatus;
import com.dsf.ohdp.support.OhdpCodeLabels;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OhdpSmokeTest {

    @Test
    void projectStatusShouldContainReportReady() {
        Assertions.assertNotNull(ProjectStatus.valueOf("REPORT_READY"));
    }

    @Test
    void stableCodesShouldHaveChineseBusinessLabels() {
        Assertions.assertEquals("化学因素", OhdpCodeLabels.label("CHEMICAL"));
        Assertions.assertEquals("待复核", OhdpCodeLabels.label("COMPLETED"));
        Assertions.assertEquals("国家级", OhdpCodeLabels.label("NATIONAL"));
        Assertions.assertEquals("部门规章", OhdpCodeLabels.label("DEPARTMENT"));
        Assertions.assertEquals("区县级", OhdpCodeLabels.regionLevel(3));
    }
}
