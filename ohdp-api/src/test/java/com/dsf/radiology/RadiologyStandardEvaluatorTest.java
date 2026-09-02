package com.dsf.radiology;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.radiology.model.RadiologyJudgement;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.service.RadiologyCalculationEngine;
import com.dsf.radiology.service.RadiologyStandardEvaluator;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RadiologyStandardEvaluatorTest {

    private final RadiologyCalculationEngine engine = new RadiologyCalculationEngine();
    private final RadiologyStandardEvaluator evaluator = new RadiologyStandardEvaluator();

    @Test
    void evaluatesEveryTvdGroupAgainstLargerOfFiveKvAndFivePercent() {
        Map<String, BigDecimal> raw = numbers(
                "TVD1-0", "60", "TVD1-1", "65", "TVD1-2", "65", "TVD1-3", "65",
                "TVD2-0", "80", "TVD2-1", "85.1", "TVD2-2", "85.1", "TVD2-3", "85.1",
                "TVD3-0", "120", "TVD3-1", "126", "TVD3-2", "126", "TVD3-3", "126");
        var calculation = engine.calculate("TVD", raw, Map.of(), Map.of());

        var evaluation = evaluator.evaluate(RadiologyModality.DR, "验收检测", "TVD", true,
                calculation.resultValue(), calculation.resultText(), raw, calculation.fields());

        assertThat(evaluation.judgement()).isEqualTo(RadiologyJudgement.UNQUALIFIED);
        assertThat(evaluation.description()).contains("第1组", "限值≤5 kV", "第3组", "限值≤6 kV");
    }

    @Test
    void usesCtProtectionLimitInsteadOfDrLimit() {
        assertThat(protectionJudgement(RadiologyModality.CT, "2.6")).isEqualTo(RadiologyJudgement.UNQUALIFIED);
        assertThat(protectionJudgement(RadiologyModality.DR, "26")).isEqualTo(RadiologyJudgement.NEEDS_ASSESSMENT);
    }

    @Test
    void distinguishesAcceptanceAndStateCtNoiseLimits() {
        BigDecimal value = new BigDecimal("0.40");
        var acceptance = evaluator.evaluate(RadiologyModality.CT, "验收检测", "NPS", true,
                value, null, Map.of(), List.of());
        var state = evaluator.evaluate(RadiologyModality.CT, "状态检测", "NPS", true,
                value, null, Map.of(), List.of());

        assertThat(acceptance.judgement()).isEqualTo(RadiologyJudgement.UNQUALIFIED);
        assertThat(state.judgement()).isEqualTo(RadiologyJudgement.QUALIFIED);
    }

    private RadiologyJudgement protectionJudgement(RadiologyModality modality, String value) {
        BigDecimal limit = modality == RadiologyModality.DR ? new BigDecimal("25") : new BigDecimal("2.5");
        BigDecimal result = new BigDecimal(value);
        if (result.compareTo(limit) <= 0) return RadiologyJudgement.QUALIFIED;
        return modality == RadiologyModality.DR ? RadiologyJudgement.NEEDS_ASSESSMENT : RadiologyJudgement.UNQUALIFIED;
    }

    private Map<String, BigDecimal> numbers(String... values) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], new BigDecimal(values[i + 1]));
        return result;
    }
}
