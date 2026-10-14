package com.dsf.radiology.service;

import com.dsf.radiology.model.RadiologyJudgement;
import com.dsf.radiology.model.RadiologyModality;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Official-standard evaluator for the built-in DR/CT calculation catalog.
 * Device-, phantom- and manufacturer-specific inputs remain explicit rather than being hidden in a generic limit.
 */
@Component
public class RadiologyStandardEvaluator {

    public record Evaluation(RadiologyJudgement judgement, String description) {
    }

    private enum DetectionType { ACCEPTANCE, STATE, STABILITY }

    public Evaluation evaluate(
            RadiologyModality modality,
            String detectionCategory,
            String itemCode,
            boolean complete,
            BigDecimal resultValue,
            String resultText,
            Map<String, BigDecimal> raw,
            List<RadiologyCalculationEngine.CalculatedField> fields) {
        if (!complete) return evaluation(RadiologyJudgement.INCOMPLETE, "数据不完整");
        Map<String, BigDecimal> calculated = fields.stream()
                .filter(field -> field.valueNum() != null)
                .collect(java.util.stream.Collectors.toMap(
                        RadiologyCalculationEngine.CalculatedField::code,
                        RadiologyCalculationEngine.CalculatedField::valueNum,
                        (left, right) -> right));
        DetectionType type = detectionType(detectionCategory);
        return modality == RadiologyModality.DR
                ? evaluateDr(type, itemCode, resultValue, resultText, raw, calculated)
                : evaluateCt(type, itemCode, resultValue, raw, calculated);
    }

    private Evaluation evaluateDr(
            DetectionType type,
            String item,
            BigDecimal result,
            String resultText,
            Map<String, BigDecimal> raw,
            Map<String, BigDecimal> calculated) {
        String standard = "WS 76—2020";
        if(List.of("DDI","HCR","LCR").contains(item) && resultText!=null && resultText.startsWith("建立基线"))
            return evaluation(RadiologyJudgement.BASELINE_ESTABLISHED, standard+"：首次建立基线");
        return switch (item) {
            case "TVD" -> evaluateTvd(raw, calculated);
            case "ETD" -> evaluateEtd(raw, calculated);
            case "ROA", "ROQ", "ARP" -> upper(result, 10, "%", standard);
            case "HVL" -> lower(result, 2.3, "mmAl（80 kV）", standard);
            case "ARS" -> upper(result, type == DetectionType.STABILITY ? 25 : 20, "%", standard);
            case "AIC" -> upper(result, type == DetectionType.ACCEPTANCE ? 10 : 15, "%", standard);
            case "UBD" -> resultText!=null && (resultText.startsWith(">") || resultText.startsWith("＞"))
                ? evaluation(result.compareTo(new BigDecimal("3"))>=0?RadiologyJudgement.UNQUALIFIED:RadiologyJudgement.NOT_CONFIGURED,standard+"：大于限定读数需能确定是否超过3°")
                : resultText!=null && (resultText.startsWith("<") || resultText.startsWith("＜"))
                ? evaluation(result.compareTo(new BigDecimal("3"))<=0?RadiologyJudgement.QUALIFIED:RadiologyJudgement.NOT_CONFIGURED,standard+"：小于限定读数需能确定是否不超过3°")
                : upper(result,3,"°",standard);
            case "LRD" -> upper(result, 10, "mm（SID=1 m）", standard);
            case "DDI" -> upper(result, 20, "%（厂家公式或基线）", standard);
            case "STP" -> lower(result, type == DetectionType.ACCEPTANCE ? 0.98 : 0.95,
                    "R²（按设备档案选择的响应模型）", standard);
            case "RUE" -> upper(result, 5, "%", standard);
            case "REA" -> upper(result, 2, "%", standard);
            case "ILA" -> evaluateResidualImage(result, resultText);
            case "ART" -> expectedNo("无影响临床诊断的伪影".equals(resultText)?"否":resultText, "不得有影响临床诊断的伪影", standard);
            case "HCR" -> lower(result, type!=DetectionType.STATE && raw.get("HCR-MANUFACTURER")==null && raw.get("厂家规定值/基线值")==null && raw.get("HCR-NYQUIST")!=null ? 80:90, type==DetectionType.STATE?"%（相对基线）":"%（相对厂家规定值、Nyquist参考或基线）", standard);
            case "LCR" -> type == DetectionType.ACCEPTANCE
                    ? evaluation(RadiologyJudgement.BASELINE_ESTABLISHED, standard + "：验收检测建立基线，不作数值合格判定")
                    : upper(result, 2, "个可观察细节（相对基线的绝对变化）", standard);
            case "RPD" -> evaluation(RadiologyJudgement.NOT_APPLICABLE, "场所防护按点位逐项判定");
            default -> evaluation(RadiologyJudgement.NOT_CONFIGURED, "没有内置标准判定规则");
        };
    }

    private Evaluation evaluateCt(
            DetectionType type,
            String item,
            BigDecimal result,
            Map<String, BigDecimal> raw,
            Map<String, BigDecimal> calculated) {
        String standard = "WS 519—2019";
        if (type == DetectionType.STABILITY && List.of("HUA", "HUU", "NPS", "RST").contains(item))
            return stability(item, result, raw, calculated);
        return switch (item) {
            case "TPA" -> upper(result, 2, "mm", standard);
            case "GTA" -> type == DetectionType.ACCEPTANCE
                    ? upper(result, 2, "°", standard)
                    : evaluation(RadiologyJudgement.NOT_APPLICABLE, standard + "：扫描架倾角精度仅列入验收检测") ;
            case "CTDI" -> evaluateCtdi(type, raw, calculated);
            case "LBA" -> upper(result, type == DetectionType.ACCEPTANCE ? 2 : 3, "mm", standard);
            case "RST" -> evaluateSliceThickness(type, result, raw.get("R0"));
            case "MTF" -> strictLower(result, type == DetectionType.ACCEPTANCE ? 6 : 5,
                    "lp/cm（普通扫描模式；高分辨模式需设备资料）", standard);
            case "LCD" -> strictUpper(result, type == DetectionType.ACCEPTANCE ? 2.5 : 3, "mm", standard);
            case "HUL" -> type == DetectionType.ACCEPTANCE
                    ? upper(result, 50, "HU（相对模体标称值）", standard)
                    : evaluation(RadiologyJudgement.NOT_APPLICABLE, standard + "：CT值线性仅列入验收检测");
            case "HUA" -> upper(result == null ? null : result.abs(), type == DetectionType.ACCEPTANCE ? 4 : 6,
                    "HU（水 CT 值绝对值）", standard);
            case "HUU" -> upper(result, type == DetectionType.ACCEPTANCE ? 5 : 6, "HU", standard);
            case "NPS" -> strictUpper(result, type == DetectionType.ACCEPTANCE ? 0.35 : 0.45, "%", standard);
            case "RPD" -> evaluation(RadiologyJudgement.NOT_APPLICABLE, "场所防护按点位逐项判定");
            default -> evaluation(RadiologyJudgement.NOT_CONFIGURED, "没有内置标准判定规则");
        };
    }

    private Evaluation evaluateTvd(Map<String, BigDecimal> raw, Map<String, BigDecimal> calculated) {
        boolean qualified = true;
        StringBuilder details = new StringBuilder("WS 76—2020：");
        for (int group = 1; group <= raw.getOrDefault("__GROUP_COUNT",new BigDecimal("3")).intValue(); group++) {
            double nominal = required(raw, "TVD" + group + "-0");
            double measured = required(calculated, "MEAN_" + group);
            double error = Math.abs(measured - nominal);
            double tolerance = Math.max(5, nominal * 0.05);
            qualified &= BigDecimal.valueOf(error).compareTo(BigDecimal.valueOf(tolerance)) <= 0;
            if (group > 1) details.append("；");
            details.append("第").append(group).append("组 |Δ|=").append(number(error))
                    .append(" kV，限值≤").append(number(tolerance)).append(" kV");
        }
        return evaluation(qualified ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED, details.toString());
    }

    private Evaluation evaluateEtd(Map<String, BigDecimal> raw, Map<String, BigDecimal> calculated) {
        boolean qualified = true;
        StringBuilder details = new StringBuilder("WS 76—2020：");
        for (int group = 1; group <= raw.getOrDefault("__GROUP_COUNT",new BigDecimal("3")).intValue(); group++) {
            double nominal = required(raw, "ETD" + group + "-0");
            double measured = required(calculated, "MEAN_" + group);
            double error = Math.abs(measured - nominal);
            double tolerance = nominal >= 100 ? nominal * 0.10 : Math.max(2, nominal * 0.15);
            qualified &= BigDecimal.valueOf(error).compareTo(BigDecimal.valueOf(tolerance)) <= 0;
            if (group > 1) details.append("；");
            details.append("第").append(group).append("组 |Δ|=").append(number(error))
                    .append(" ms，限值≤").append(number(tolerance)).append(" ms");
        }
        return evaluation(qualified ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED, details.toString());
    }

    private Evaluation evaluateResidualImage(BigDecimal result, String resultText) {
        String standard = "WS 76—2020";
        if (isNo(resultText) || "不存在残影".equals(resultText)) return evaluation(RadiologyJudgement.QUALIFIED, standard + "：未见残影");
        if (result == null) return evaluation(RadiologyJudgement.INCOMPLETE, standard + "：存在残影时需计算像素值误差");
        return upper(result, 5, "%（存在残影时的像素值误差）", standard);
    }

    private Evaluation evaluateCtdi(
            DetectionType type, Map<String, BigDecimal> raw, Map<String, BigDecimal> calculated) {
        String standard = "WS 519—2019";
        if (type == DetectionType.ACCEPTANCE) {
            if(!calculated.containsKey("HEAD_CTDIW")) return compareReferences(standard,15,
                    calculated.get("BODY_CTDIW"),raw.get("CTDI-BODY-MANUFACTURER"),"体模厂家值",null,null,null);
            return compareReferences(standard, 15,
                    calculated.get("HEAD_CTDIW"), raw.get("CTDI-HEAD-MANUFACTURER"), "头模厂家值",
                    calculated.get("BODY_CTDIW"), raw.get("CTDI-BODY-MANUFACTURER"), calculated.containsKey("BODY_CTDIW")?"体模厂家值":null);
        }
        if (type == DetectionType.STABILITY) {
            return compareReferences(standard, 15,
                    calculated.get("HEAD_CTDIW"), raw.get("CTDI-HEAD-BASELINE"), "头模基线",
                    null, null, null);
        }
        BigDecimal manufacturer = raw.get("CTDI-HEAD-MANUFACTURER");
        return manufacturer == null
                ? upper(calculated.get("HEAD_CTDIW"), 50, "mGy（无厂家规定值时的头模状态检测控制值）", standard)
                : compareReferences(standard, 20,
                        calculated.get("HEAD_CTDIW"), manufacturer, "头模厂家值", null, null, null);
    }

    private Evaluation compareReferences(
            String standard,
            double tolerancePercent,
            BigDecimal measured1,
            BigDecimal reference1,
            String label1,
            BigDecimal measured2,
            BigDecimal reference2,
            String label2) {
        if (measured1 == null || reference1 == null || reference1.signum() == 0
                || (label2 != null && (measured2 == null || reference2 == null || reference2.signum() == 0))) {
            return evaluation(RadiologyJudgement.NOT_CONFIGURED,
                    standard + "：需要录入非零的" + label1 + (label2 == null ? "" : "和" + label2));
        }
        double deviation1 = relativeDeviation(measured1, reference1);
        double deviation2 = label2 == null ? 0 : relativeDeviation(measured2, reference2);
        boolean qualified = deviation1 <= tolerancePercent && deviation2 <= tolerancePercent;
        String description = standard + "：" + label1 + "偏差=" + number(deviation1) + "%"
                + (label2 == null ? "" : "；" + label2 + "偏差=" + number(deviation2) + "%")
                + "，限值±" + number(tolerancePercent) + "%";
        return evaluation(qualified ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED, description);
    }

    private double relativeDeviation(BigDecimal measured, BigDecimal reference) {
        return measured.subtract(reference).abs().divide(reference.abs(), 12, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();
    }

    private Evaluation evaluateSliceThickness(DetectionType type, BigDecimal result, BigDecimal nominalValue) {
        String standard = "WS 519—2019";
        if (type == DetectionType.STABILITY) {
            return evaluation(RadiologyJudgement.NOT_CONFIGURED,
                    standard + "：稳定性检测需基线值，允许变化为 20% 或 1 mm 中较大者");
        }
        if (nominalValue == null) return evaluation(RadiologyJudgement.INCOMPLETE, "缺少标称层厚 R0");
        double nominal = nominalValue.doubleValue();
        double tolerance = nominal > 2 ? 1 : nominal >= 1 ? nominal * 0.5 : 0.5;
        return upper(result, tolerance, "mm（标称层厚 " + number(nominal) + " mm）", standard);
    }

    private Evaluation upper(BigDecimal value, double limit, String unit, String standard) {
        if (value == null) return evaluation(RadiologyJudgement.INCOMPLETE, "缺少最终数值");
        return evaluation(value.compareTo(BigDecimal.valueOf(limit)) <= 0 ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                standard + "：≤" + number(limit) + " " + unit);
    }

    private Evaluation lower(BigDecimal value, double limit, String unit, String standard) {
        if (value == null) return evaluation(RadiologyJudgement.INCOMPLETE, "缺少最终数值");
        return evaluation(value.compareTo(BigDecimal.valueOf(limit)) >= 0 ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                standard + "：≥" + number(limit) + " " + unit);
    }

    private Evaluation strictUpper(BigDecimal value, double limit, String unit, String standard) {
        if (value == null) return evaluation(RadiologyJudgement.INCOMPLETE, "缺少最终数值");
        return evaluation(value.compareTo(BigDecimal.valueOf(limit)) < 0 ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                standard + "：<" + number(limit) + " " + unit);
    }
    private Evaluation strictLower(BigDecimal value, double limit, String unit, String standard) {
        if (value == null) return evaluation(RadiologyJudgement.INCOMPLETE, "缺少最终数值");
        return evaluation(value.compareTo(BigDecimal.valueOf(limit)) > 0 ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                standard + "：>" + number(limit) + " " + unit);
    }
    private Evaluation stability(String item, BigDecimal result, Map<String, BigDecimal> raw, Map<String, BigDecimal> calculated) {
        BigDecimal baseline = raw.get(item + "-BASELINE");
        BigDecimal actual = item.equals("RST") ? calculated.get("MEASURED") : result;
        if (baseline == null || actual == null) return evaluation(RadiologyJudgement.NOT_CONFIGURED, "WS 519—2019：缺少" + item + "稳定性基线");
        BigDecimal allowed = switch(item) {
            case "HUA" -> new BigDecimal("4");
            case "HUU" -> new BigDecimal("2");
            case "NPS" -> baseline.abs().multiply(new BigDecimal("0.10"));
            case "RST" -> baseline.abs().multiply(new BigDecimal("0.20")).max(BigDecimal.ONE);
            default -> BigDecimal.ZERO;
        };
        if ((item.equals("NPS") || item.equals("RST")) && baseline.signum() <= 0)
            return evaluation(RadiologyJudgement.NOT_CONFIGURED, "稳定性基线必须大于0");
        BigDecimal difference=actual.subtract(baseline).abs();
        return evaluation(difference.compareTo(allowed) <= 0 ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                "WS 519—2019：基线=" + baseline.toPlainString() + "；绝对变化=" + difference.toPlainString() + "；允许变化≤" + allowed.toPlainString());
    }

    private Evaluation expectedNo(String value, String requirement, String standard) {
        if (!StringUtils.hasText(value)) return evaluation(RadiologyJudgement.INCOMPLETE, "缺少是/否结果");
        return evaluation(isNo(value) ? RadiologyJudgement.QUALIFIED : RadiologyJudgement.UNQUALIFIED,
                standard + "：" + requirement);
    }

    private DetectionType detectionType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("稳定") || normalized.contains("STABILITY")) return DetectionType.STABILITY;
        if (normalized.contains("状态") || normalized.contains("STATE")) return DetectionType.STATE;
        return DetectionType.ACCEPTANCE;
    }

    private boolean isNo(String value) {
        return StringUtils.hasText(value)
                && (value.trim().equals("否") || value.trim().equals("无") || value.trim().equalsIgnoreCase("no"));
    }

    private double required(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        if (value == null) throw new IllegalArgumentException("缺少标准判定字段：" + key);
        return value.doubleValue();
    }

    private String number(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Evaluation evaluation(RadiologyJudgement judgement, String description) {
        return new Evaluation(judgement, description);
    }
}
