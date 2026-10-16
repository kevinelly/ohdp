package com.dsf.radiology.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RadiologyReportDisplayNamesTest {
    @Test void formalReportNamesFollowLatestTemplatesAndKeepFallbacks() {
        assertEquals("CTDIW",RadiologyReportComposer.reportItemName("CTDI","CT剂量指数"));
        assertEquals("高对比分辨率",RadiologyReportComposer.reportItemName("MTF","高对比分辨力"));
        assertEquals("均匀性",RadiologyReportComposer.reportItemName("HUU","水的均匀性"));
        assertEquals("CT值（水）",RadiologyReportComposer.reportItemName("HUA","水的CT值"));
        assertEquals("低对比可探测能力",RadiologyReportComposer.reportItemName("LCD","低对比可探测能力"));
        assertEquals("AEC电离室之间一致性",RadiologyReportComposer.reportItemName("AIC","旧名"));
    }
}
