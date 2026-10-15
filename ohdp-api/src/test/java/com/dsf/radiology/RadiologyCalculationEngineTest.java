package com.dsf.radiology;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.radiology.service.RadiologyCalculationEngine;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RadiologyCalculationEngineTest {

    private final RadiologyCalculationEngine engine = new RadiologyCalculationEngine();

    @Test void aicExposesSignedChamberDeviationsWithoutChangingMaximum() {
        var n=new LinkedHashMap<String,BigDecimal>();
        for(int i=1;i<=3;i++){n.put("AIC-C"+i,new BigDecimal("90"));n.put("AIC-L"+i,new BigDecimal("100"));n.put("AIC-R"+i,new BigDecimal("110"));}
        var result=engine.calculate("AIC",n,Map.of(),Map.of());
        assertThat(result.complete()).isTrue();
        assertThat(result.resultValue()).isEqualByComparingTo("10");
        var fields=new LinkedHashMap<String,String>();result.fields().forEach(f->fields.put(f.code(),f.displayValue()));
        assertThat(fields).containsEntry("CENTER_DEVIATION","-10.0").containsEntry("LEFT_DEVIATION","0.0").containsEntry("RIGHT_DEVIATION","10.0");
    }

    @Test void averagesUseSignificantDigitsAcrossOrdersOfMagnitude() {
        var n=new LinkedHashMap<String,BigDecimal>();String[] readings={"9.9","9.9","9.9","99","99"};
        for(int i=0;i<5;i++)n.put("ARP"+(i+1),new BigDecimal(readings[i]));
        var result=engine.calculate("ARP",n,Map.of(),Map.of());
        assertThat(result.fields().stream().filter(f->f.code().equals("MEAN")).findFirst().orElseThrow().displayValue()).isEqualTo("46");
        for(int i=0;i<5;i++)n.put("ARP"+(i+1),new BigDecimal("1.00"));
        assertThat(engine.calculate("ARP",n,Map.of(),Map.of()).fields().get(0).displayValue()).isEqualTo("1.00");
        n.put("ARP-K",new BigDecimal("1000"));
        assertThat(engine.calculate("ARP",n,Map.of(),Map.of()).fields().get(0).displayValue()).isEqualTo("1000");
    }

    @Test
    void confirmedPowerModelHasNoOffsetAndInverseRecoversDose() {
        Map<String, BigDecimal> raw=new LinkedHashMap<>(), pixels=new LinkedHashMap<>();
        for(int i=1;i<=5;i++){raw.put("STP-K"+i,BigDecimal.valueOf(i));raw.put("STP-R"+i,BigDecimal.valueOf(2*i*i));pixels.put("RUE"+i,BigDecimal.valueOf(2*i*i));}
        var fitted=engine.calculate("STP",raw,Map.of("STP-MODEL","POWER"),Map.of());
        assertThat(fitted.complete()).isTrue();
        assertThat(fitted.fields().stream().filter(f->f.code().equals("POWER_C")).findFirst().orElseThrow().valueNum()).isZero();
        var inverse=engine.calculate("RUE",pixels,Map.of(),Map.of("STP",raw),Map.of("STP",Map.of("STP-MODEL","POWER")));
        assertThat(inverse.complete()).isTrue();
        for(int i=1;i<=5;i++){String key="DOSE_"+i;assertThat(inverse.fields().stream().filter(f->f.code().equals(key)).findFirst().orElseThrow().valueNum()).isCloseTo(BigDecimal.valueOf(i),org.assertj.core.data.Offset.offset(new BigDecimal("0.001")));}
    }

    @Test
    void calculatesHvlAndUsesHalfEvenRounding() {
        var result = engine.calculate("HVL", numbers("HVL1", "2.22", "HVL2", "2.23", "HVL3", "2.245"), Map.of(), Map.of());

        assertThat(result.complete()).isTrue();
        assertThat(result.resultValue()).isCloseTo(new BigDecimal("2.231666666666667"),org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
        assertThat(result.fields().get(0).displayValue()).isEqualTo("2.2");
        assertThat(new RadiologyCalculationEngine.CalculatedField("X","",new BigDecimal("2.225"),null,"",2).displayValue()).isEqualTo("2.22");
    }

    @Test
    void blankInputIsIncompleteInsteadOfZeroOrDivisionError() {
        var result = engine.calculate("ROA", Map.of(), Map.of(), Map.of());

        assertThat(result.complete()).isFalse();
        assertThat(result.message()).contains("ROA1");
        assertThat(result.resultValue()).isNull();
    }

    @Test
    void calculatesRealSignalTransferRegressions() {
        Map<String, BigDecimal> values = numbers(
                "STP-K1", "1.199", "STP-K2", "5.248", "STP-K3", "10.42", "STP-K4", "19.21", "STP-K5", "33.41",
                "STP-R1", "60918.117", "STP-R2", "49839.441", "STP-R3", "40281.706", "STP-R4", "28378.819", "STP-R5", "20179.48");

        var result = engine.calculate("STP", values, Map.of(), Map.of("STP", values));

        assertThat(result.complete()).isTrue();
        assertThat(result.fields()).extracting(RadiologyCalculationEngine.CalculatedField::code)
                .contains("LINEAR_R2", "LOG_R2", "POWER_R2", "BEST_MODEL", "RESULT");
        assertThat(result.resultValue()).isBetween(new BigDecimal("0.90"), BigDecimal.ONE);
    }

    @Test
    void calculatesCtDoseIndexForHeadAndBody() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.putAll(numbers("层数n1", "3", "层厚T1", "10", "刻度系数k1", "1", "层数n2", "3", "层厚T2", "10", "刻度系数k2", "1"));
        for (String prefix : new String[] {"HC", "HU", "HD", "HL", "HR", "SC", "SU", "SD", "SL", "SR"}) {
            values.putAll(numbers(prefix + "D1", "100", prefix + "D2", "100", prefix + "D3", "100"));
        }

        var result = engine.calculate("CTDI", values, Map.of(), Map.of());

        assertThat(result.complete()).isTrue();
        assertThat(result.resultValue()).isCloseTo(new BigDecimal("33.33333333333333"),org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    void calculatesRpdBackgroundAndProtectionPoint() {
        Map<String, BigDecimal> values = numbers("k", "1.27", "a", "0.001");
        for (int i = 1; i <= 10; i++) values.put("RPD0-" + i, BigDecimal.valueOf(90 + i));

        var result = engine.calculate("RPD", values, Map.of(), Map.of());
        var point = engine.calculateProtectionReadings(
                java.util.List.of(new BigDecimal("88"), new BigDecimal("99"), new BigDecimal("98"), new BigDecimal("87"), new BigDecimal("86")),
                new BigDecimal("1.27"), new BigDecimal("0.001"));

        assertThat(result.complete()).isTrue();
        assertThat(result.resultUnit()).isEqualTo("μSv/h");
        assertThat(point).hasSize(2);
        assertThat(point.get(1)).isCloseTo(new BigDecimal("0.116332"),org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    void keepsAbsoluteAndRelativeTvdResultsTogether() {
        Map<String, BigDecimal> values = numbers(
                "TVD1-0", "60", "TVD1-1", "61", "TVD1-2", "61", "TVD1-3", "61",
                "TVD2-0", "80", "TVD2-1", "79", "TVD2-2", "79", "TVD2-3", "79",
                "TVD3-0", "100", "TVD3-1", "102", "TVD3-2", "102", "TVD3-3", "102");

        var result = engine.calculate("TVD", values, Map.of(), Map.of());

        assertThat(result.complete()).isTrue();
        assertThat(result.resultValue()).isNull();
        assertThat(result.resultText()).contains("1.0 kV", "-1.0 kV", "2.0%");
        assertThat(result.fields()).filteredOn(field -> field.code().equals("RESULT_TEXT"))
                .extracting(RadiologyCalculationEngine.CalculatedField::valueText)
                .containsExactly(result.resultText());
    }

    @Test
    void calculatesCtNoiseAsNumericPercentAndNormalizesToTenMillimetres() {
        var result = engine.calculate("NPS", numbers(
                "NPS1", "3.5", "NPS2", "3.5", "NPS3", "3.5", "NPS-n", "10"), Map.of(), Map.of());

        assertThat(result.resultValue()).isEqualByComparingTo("0.350000");
        assertThat(result.resultUnit()).isEqualTo("%");
    }

    @Test
    void doesNotApplyUnexplainedPointFourTwoToLbaOrMtf() {
        var lba = engine.calculate("LBA", numbers("LBA", "2"), Map.of(), Map.of());
        var mtf = engine.calculate("MTF", numbers("MTF", "6"), Map.of(), Map.of());

        assertThat(lba.resultValue()).isEqualByComparingTo("2.000000");
        assertThat(mtf.resultValue()).isEqualByComparingTo("6.000000");
    }

    @Test
    void lcrPreservesSignedDeltaButUsesAbsoluteDifferenceAsResult() {
        var result = engine.calculate("LCR", numbers(
                "LCR1（可观察细节数）", "8",
                "LCR2（可观察对比度）", "0.5",
                "厂家规定值/基线值", "10"), Map.of(), Map.of());

        assertThat(result.resultValue()).isEqualByComparingTo("2.000000");
        assertThat(result.fields()).filteredOn(field -> field.code().equals("DETAIL_DELTA"))
                .extracting(RadiologyCalculationEngine.CalculatedField::valueNum)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("-2.00"));
    }

    @Test
    void ctdiManufacturerComparisonIsOptionalSignedAndPartSpecific() {
        var values = numbers("层数n1","1","层厚T1","10","刻度系数k1","1", "层数n2","1","层厚T2","10","刻度系数k2","1");
        for (String prefix : new String[]{"HC","HU","HD","HL","HR","SC","SU","SD","SL","SR"})
            for (int i=1;i<=3;i++) values.put(prefix+"D"+i, new BigDecimal("10"));
        var absent=engine.calculate("CTDI",values,Map.of(),Map.of());
        assertThat(absent.fields()).noneMatch(f->f.code().endsWith("_MANUFACTURER_DEVIATION"));
        values.put("CTDI-HEAD-MANUFACTURER",new BigDecimal("20"));
        values.put("CTDI-BODY-MANUFACTURER",new BigDecimal("8"));
        var result=engine.calculate("CTDI",values,Map.of(),Map.of());
        assertThat(result.fields()).filteredOn(f->f.code().equals("HEAD_MANUFACTURER_DEVIATION")).singleElement().satisfies(f->{assertThat(f.valueNum()).isEqualByComparingTo("-50");assertThat(f.unit()).isEqualTo("%");});
        assertThat(result.fields()).filteredOn(f->f.code().equals("BODY_MANUFACTURER_DEVIATION")).singleElement().satisfies(f->assertThat(f.valueNum()).isEqualByComparingTo("25"));
        assertThat(result.resultValue()).isEqualByComparingTo(absent.resultValue());
        values.put("CTDI-HEAD-MANUFACTURER",BigDecimal.ZERO);
        assertThat(engine.calculate("CTDI",values,Map.of(),Map.of()).fields()).noneMatch(f->f.code().equals("HEAD_MANUFACTURER_DEVIATION"));
    }

    private Map<String, BigDecimal> numbers(String... values) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], new BigDecimal(values[i + 1]));
        return result;
    }
}
