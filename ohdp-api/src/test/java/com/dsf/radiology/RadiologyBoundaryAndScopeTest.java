package com.dsf.radiology;
import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.model.*;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class RadiologyBoundaryAndScopeTest {
    final RadiologyStandardEvaluator evaluator=new RadiologyStandardEvaluator();
    final RadiologyScopeService scope=new RadiologyScopeService();
    @Test void strictCtBoundaryValuesAreUnqualified() {
        for(var item:Map.of("NPS","0.45","LCD","3.0","MTF","5.0").entrySet())
            assertThat(evaluator.evaluate(RadiologyModality.CT,"状态检测",item.getKey(),true,new BigDecimal(item.getValue()),null,Map.of(),List.of()).judgement())
                .as(item.getKey()).isEqualTo(RadiologyJudgement.UNQUALIFIED);
    }
    @Test void noiseDoesNotRoundABoundaryIntoCompliance() {
        assertThat(evaluator.evaluate(RadiologyModality.CT,"状态检测","NPS",true,new BigDecimal("0.44999999"),null,Map.of(),List.of()).judgement())
            .isEqualTo(RadiologyJudgement.QUALIFIED);
    }
    @Test void stabilityUsesWaterCtBaselineAndNotStateLimit() {
        assertThat(evaluator.evaluate(RadiologyModality.CT,"稳定性检测","HUA",true,new BigDecimal("5"),null,Map.of("HUA-BASELINE",new BigDecimal("0")),List.of()).judgement())
            .isEqualTo(RadiologyJudgement.UNQUALIFIED);
        assertThat(evaluator.evaluate(RadiologyModality.CT,"稳定性检测","HUA",true,new BigDecimal("8"),null,Map.of("HUA-BASELINE",new BigDecimal("5")),List.of()).judgement())
            .isEqualTo(RadiologyJudgement.QUALIFIED);
    }
    @Test void stabilityMissingBaselineRemainsUnassessed() {
        assertThat(evaluator.evaluate(RadiologyModality.CT,"稳定性检测","NPS",true,new BigDecimal("0.2"),null,Map.of(),List.of()).judgement())
            .isEqualTo(RadiologyJudgement.NOT_CONFIGURED);
    }
    @Test void drStateAndStabilityHaveDifferentItemMatrices() {
        var c=new RadiologyCase();c.setModality(RadiologyModality.DR);c.setDetectionCategory("状态检测");
        assertThat(scope.exclusion(c,"ROQ",Map.of())).isNotEmpty();
        assertThat(scope.exclusion(c,"ETD",Map.of())).isNotEmpty();
        assertThat(scope.exclusion(c,"ILA",Map.of())).isNotEmpty();
        assertThat(scope.exclusion(c,"TVD",Map.of())).isEmpty();
        c.setDetectionCategory("稳定性检测");
        assertThat(scope.exclusion(c,"TVD",Map.of())).isNotEmpty();
        assertThat(scope.exclusion(c,"ETD",Map.of())).isEmpty();
    }
    @Test void noAecIsAnExplicitExclusionWithReason() {
        var c=new RadiologyCase();c.setModality(RadiologyModality.DR);c.setHasAec(false);c.setFeatureReason("型号说明书确认无AEC");
        assertThat(scope.exclusion(c,"ARP",Map.of())).contains("型号说明书确认");
        assertThat(scope.exclusion(c,"HVL",Map.of())).isEmpty();
    }
}

