package com.dsf.radiology;

import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.service.*;
import com.dsf.radiology.model.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class RadiologyDocumentGapTest {
    final RadiologyCalculationEngine engine=new RadiologyCalculationEngine();
    final RadiologyStandardEvaluator evaluator=new RadiologyStandardEvaluator();
    Map<String,BigDecimal> n(String... data){var m=new LinkedHashMap<String,BigDecimal>();for(int i=0;i<data.length;i+=2)m.put(data[i],new BigDecimal(data[i+1]));return m;}
    @Test void stateLowContrastWithoutReferenceEstablishesBaseline() {
        var raw=n("LCR1（可观察细节数）","10","LCR2（可观察对比度）","0.3");
        var r=engine.calculate("LCR",raw,Map.of(),Map.of());assertThat(r.complete()).isTrue();
        assertThat(evaluator.evaluate(RadiologyModality.DR,"状态检测","LCR",true,r.resultValue(),r.resultText(),raw,r.fields()).judgement()).isEqualTo(RadiologyJudgement.BASELINE_ESTABLISHED);
    }
    @Test void uniformityInverseDoseRetainsRawInputPrecision() {
        var raw=n("RUE1","7.00","RUE2","7.00","RUE3","7.00","RUE4","7.00","RUE5","7.00");
        var stp=n("STP-K1","1","STP-K2","2","STP-K3","3","STP-K4","4","STP-K5","5","STP-R1","5","STP-R2","7","STP-R3","9","STP-R4","11","STP-R5","13");
        var r=engine.calculate("RUE",raw,Map.of(),Map.of("STP",stp),Map.of("STP",Map.of("STP-MODEL","LINEAR")));
        assertThat(r.fields()).filteredOn(f->f.code().equals("MEAN")).extracting(RadiologyCalculationEngine.CalculatedField::displayValue).containsExactly("2.00");
    }
    @Test void gantryMethodChoiceOverridesReadingsRetainedFromOtherMethod() {
        var raw=n("扫描倾角显示值α1","0","模体上下边沿间距L1","100","倾斜α1角后模体上下边沿间距L2","100","测量值α2","2");
        assertThat(engine.calculate("GTA",raw,Map.of("__METHOD","PHANTOM"),Map.of()).resultValue()).isEqualByComparingTo("0");
        assertThat(engine.calculate("GTA",raw,Map.of("__METHOD","INCLINOMETER"),Map.of()).resultValue()).isEqualByComparingTo("2");
        raw.remove("测量值α2");assertThat(engine.calculate("GTA",raw,Map.of("__METHOD","INCLINOMETER"),Map.of()).complete()).isFalse();
    }
    @Test void firstDdiMeasurementCreatesBaselineWithoutFabricatingReference() {
        var raw=n("DDI1","100","DDI2","110","DDI3","120");var r=engine.calculate("DDI",raw,Map.of(),Map.of());
        assertThat(r.complete()).isTrue();assertThat(r.resultText()).contains("建立基线值：110.000");
        assertThat(evaluator.evaluate(RadiologyModality.DR,"验收检测","DDI",true,r.resultValue(),r.resultText(),raw,r.fields()).judgement()).isEqualTo(RadiologyJudgement.BASELINE_ESTABLISHED);
    }
    @Test void ddiCalculatedReferenceTakesPrecedenceOverStoredBaseline() {
        var r=engine.calculate("DDI",n("DDI1","110","DDI2","110","DDI3","110","DDI-CALCULATED","100","DDI-BASELINE","200"),Map.of(),Map.of());
        assertThat(r.resultValue()).isEqualByComparingTo("10");
    }
    @Test void highContrastWithoutReferenceCreatesLpPerMmBaseline() {
        var r=engine.calculate("HCR",n("HCR1","2.55"),Map.of(),Map.of());assertThat(r.resultText()).isEqualTo("建立基线值：2.6 lp/mm");
    }
    @Test void nyquistReferenceUsesItsOwnThresholdAtAndBelowBoundary() {
        for(String measured:List.of("1.12","1.119")) {
            var raw=n("HCR1",measured,"HCR-NYQUIST","1");var r=engine.calculate("HCR",raw,Map.of(),Map.of());
            var judgment=evaluator.evaluate(RadiologyModality.DR,"验收检测","HCR",true,r.resultValue(),r.resultText(),raw,r.fields());
            assertThat(judgment.judgement()).isEqualTo(measured.equals("1.12")?RadiologyJudgement.QUALIFIED:RadiologyJudgement.UNQUALIFIED);
        }
    }
    @Test void outputLinearityRequiresFiveReadingsPerGroup() {
        var raw=n("I1","100","I2","200","T1","1","T2","1");
        for(int i=1;i<=3;i++){raw.put("ROQ1-"+i,new BigDecimal("10"));raw.put("ROQ2-"+i,new BigDecimal("20"));}
        assertThat(engine.calculate("ROQ",raw,Map.of(),Map.of()).complete()).isFalse();
        for(int i=4;i<=5;i++){raw.put("ROQ1-"+i,new BigDecimal("10"));raw.put("ROQ2-"+i,new BigDecimal("20"));}
        assertThat(engine.calculate("ROQ",raw,Map.of(),Map.of()).resultValue()).isEqualByComparingTo("0");
    }
    @Test void noPreprocessedImagesExcludesAllFourDocumentItems() {
        var c=new com.dsf.radiology.entity.RadiologyCase();c.setModality(RadiologyModality.DR);c.setDetectionCategory("验收检测");c.setHasRawImages(false);c.setFeatureReason("无法获取图像");
        for(String code:List.of("DDI","STP","RUE","ART"))assertThat(new RadiologyScopeService().exclusion(c,code,Map.of())).contains("无法获取图像");
    }
    @Test void apertureCountAutomaticallyMapsMinimumDiameter() {
        var r=engine.calculate("LCD",n("LCD-COUNT-1","9","LCD-COUNT-05","9","LCD-COUNT-03","9"),Map.of(),Map.of());
        assertThat(r.resultValue()).isCloseTo(new BigDecimal("1.2"),org.assertj.core.data.Offset.offset(new BigDecimal("0.0000001")));
        assertThat(engine.calculate("LCD",n("LCD-COUNT-1","1.5","LCD-COUNT-05","9","LCD-COUNT-03","9"),Map.of(),Map.of()).complete()).isFalse();
    }
    @Test void sliceWindowIsDerivedAndDisplayedAsIntegers() {
        var r=engine.calculate("RST",n("CTbackground","1","CTmax","100","R0","3","R1","3","R2","3","R3","3","R4","3","RST-FACTOR","1"),Map.of(),Map.of());
        assertThat(r.fields()).filteredOn(f->f.code().equals("CTHM")).extracting(RadiologyCalculationEngine.CalculatedField::displayValue).containsExactly("50");
        assertThat(r.fields()).filteredOn(f->f.code().equals("WWMIN")).extracting(RadiologyCalculationEngine.CalculatedField::displayValue).containsExactly("99");
    }
    @Test void residualImageRetainsItsMeasuredErrorInReportText() {
        var r=engine.calculate("ILA",n("残影区D1","103","非残影区D0","100"),Map.of("是否有残影（如“否”无需填后两项）","是"),Map.of());
        assertThat(r.resultText()).isEqualTo("存在残影，且像素值误差为3.0%");
    }
    @Test void artifactRequiresSourceWhenPresent() {
        assertThat(engine.calculate("ART",Map.of(),Map.of("填“是”或“否”","是"),Map.of()).complete()).isFalse();
        assertThat(engine.calculate("ART",Map.of(),Map.of("填“是”或“否”","是","ART-SOURCE","监视器"),Map.of()).resultText()).contains("来源监视器");
    }
    @Test void percentLowContrastBaselineCannotSilentlyBecomeCountBaseline() {
        var r=engine.calculate("LCR",n("LCR1（可观察细节数）","10","LCR2（可观察对比度）","0.3","厂家规定值/基线值","0.3"),Map.of("LCR-BASELINE-UNIT","%"),Map.of());
        assertThat(r.complete()).isFalse();assertThat(r.message()).contains("基线已统一按细节个数");
    }
    @Test void greaterThanThreeDegreesDoesNotPassEqualityBoundary() {
        var raw=n("UBD","3");var r=engine.calculate("UBD",raw,Map.of("UBD-QUALIFIER",">"),Map.of());
        assertThat(evaluator.evaluate(RadiologyModality.DR,"验收检测","UBD",true,r.resultValue(),r.resultText(),raw,r.fields()).judgement()).isEqualTo(RadiologyJudgement.UNQUALIFIED);
    }
}
