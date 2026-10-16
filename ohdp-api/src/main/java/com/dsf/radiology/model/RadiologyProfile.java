package com.dsf.radiology.model;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** Case-specific report information is copied into the approved snapshot. */
@Data
public class RadiologyProfile {
    private String sampleName;
    private String submittingUnit;
    private String commissionedUnit;
    private String equipmentParameters;
    private LocalDate manufacturedDate;
    private String detectorName;
    private String backgroundLocation;
    private String backgroundOperation = "未曝光";
    private String calibrationMode = "INSTRUMENT";
    private String instrumentResponseTime;
    private String protectionConditions;
    private String overallConclusion;
    private List<String> selectedGroups;
    /** Null preserves legacy scope semantics; false is an unconfirmed new-case draft. */
    private Boolean scopeConfirmed;
    private java.util.Map<String,Boolean> itemApplicability;
    private java.util.Map<String,String> itemExclusionReasons;
    private List<InstrumentRecord> instruments;

    @Data
    public static class InstrumentRecord {
        private Long deviceId;
        private String name;
        private String model;
        private String deviceNo;
        private String certificateNo;
        private LocalDate validFrom;
        private LocalDate validTo;
    }
}
