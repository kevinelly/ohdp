package com.dsf.ohdp;

import com.dsf.ohdp.entity.OhdpResultItem;
import com.dsf.ohdp.support.ReportResultRules;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReportResultRulesTest {
    private OhdpResultItem result() {
        OhdpResultItem r=new OhdpResultItem();r.setFactorName("二甲苯");r.setMetricType("TWA");r.setValueNum(new BigDecimal("45"));r.setValueRaw("45");r.setLimitValue(new BigDecimal("50"));r.setUnit("mg/m3");r.setLimitUnit("mg/m³");return r;
    }
    @Test void usesDailyEffectiveLimitAndExplicitApplicability() {
        var r=result();r.setDailyExposureHours(new BigDecimal("10"));
        assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setReductionApplicable(true);r.setEvaluationBasis("客户答复 A.7.3.1，非豁免物质");
        assertThat(ReportResultRules.reduction(r)).isEqualByComparingTo("0.7");
        assertThat(ReportResultRules.effectiveLimit(r)).isEqualByComparingTo("35");
        assertThat(ReportResultRules.judge(r)).isEqualTo("不合格");
        r.setReductionApplicable(false);r.setEvaluationBasis("专业人员确认豁免");
        assertThat(ReportResultRules.effectiveLimit(r)).isEqualByComparingTo("50");
    }
    @Test void weeklyReductionRequiresMoreThanFiveDays() {
        var r=result();r.setReductionApplicable(true);r.setEvaluationBasis("A.7.3.2");r.setWeeklyExposureHours(new BigDecimal("48"));r.setWeeklyWorkDays(5);
        assertThat(ReportResultRules.reduction(r)).isEqualByComparingTo("1");
        r.setWeeklyWorkDays(6);assertThat(ReportResultRules.reduction(r)).isEqualByComparingTo("0.781250");
        r.setDailyExposureHours(new BigDecimal("6"));assertThat(ReportResultRules.errors(r,false)).anyMatch(s->s.contains("不一致"));
    }
    @Test void rejectsInvalidWorkingHours() {
        for(String h:new String[]{"-1","0","24","25"}) {var r=result();r.setDailyExposureHours(new BigDecimal(h));assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");}
        for(String h:new String[]{"-1","0","168","170"}) {var r=result();r.setWeeklyExposureHours(new BigDecimal(h));assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");}
    }
    @Test void rejectsDivergentValuesUnitsAndMissingLimits() {
        var r=result();r.setValueRaw("4.5");assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setValueRaw("45");r.setUnit("ug/m3");assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setUnit("mg/m3");r.setLimitValue(null);assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setLimitValue(new BigDecimal("50"));r.setValueRaw("45abc");assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
    }
    @Test void censoredValuesNeverBecomeFalsePassesOrFailures() {
        var r=result();r.setValueRaw("<45");assertThat(ReportResultRules.judge(r)).isEqualTo("合格");
        r.setLimitValue(new BigDecimal("40"));assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setValueRaw(">45");assertThat(ReportResultRules.judge(r)).isEqualTo("不合格");
        r.setLimitValue(new BigDecimal("50"));assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
    }
    @Test void mixedExposureRejectsCensoredInputsInsteadOfSummingDetectionLimits() {
        var r=result();r.setValueRaw("<45");r.setMixedExposureGroup("共同效应组");
        assertThat(ReportResultRules.errors(r,false)).anyMatch(s->s.contains("混合接触计算不支持带比较符"));
        assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setValueRaw("45");assertThat(ReportResultRules.judge(r)).isEqualTo("合格");
    }
    @Test void peakUsesConfirmedMultiplierWithoutTwaReduction() {
        var r=result();r.setMetricType("PE");r.setValueNum(new BigDecimal("5"));r.setValueRaw("5");r.setLimitValue(new BigDecimal("4"));
        assertThat(ReportResultRules.judge(r)).isEqualTo("未判定");
        r.setPeakLimitMultiplier(new BigDecimal("3"));r.setEvaluationBasis("经专业审核的PE规则和频次条件");r.setPeakExposureMinutes(BigDecimal.TEN);r.setPeakTimesPerShift(2);r.setPeakIntervalMinutes(new BigDecimal("60"));
        assertThat(ReportResultRules.effectiveLimit(r)).isEqualByComparingTo("12");assertThat(ReportResultRules.judge(r)).isEqualTo("合格");
    }
    @Test void physicalFactorsAreNeverChemicalsAndUnknownsAreExplicit() {
        for(String metric:new String[]{"WBGT","PF_E","PF_B","HF_E","HF_H","UHF","MICROWAVE","UVA","LASER","HAV"})assertThat(ReportResultRules.category("物理因素",metric,null)).isEqualTo("物理因素");
        assertThat(ReportResultRules.category("未知","UNKNOWN",null)).isEqualTo("其他");
        assertThat(ReportResultRules.errors(result(),true)).anyMatch(s->s.contains("测点编号"));
    }
}
