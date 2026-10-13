package com.dsf.ohdp.support;

import com.dsf.ohdp.entity.OhdpResultItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** One source of truth for result validation, effective limits and comparison. */
public final class ReportResultRules {
    private ReportResultRules() {}
    private static final Pattern NUMBER = Pattern.compile("^(<=|>=|<|>|≤|≥)?\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)$");
    public static String metric(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT).replace(" ", ""); }
    public static String category(String factor, String metric, String explicit) {
        String m = metric(metric);
        if (Set.of("X_GAMMA", "RADIATION_DOSE_RATE", "SURFACE_CONTAMINATION", "SEALED_SOURCE_LEAKAGE", "AIR_RADIONUCLIDE", "RADON").contains(m)) return "放射性因素";
        if (physicalTable(m) > 0) return "物理因素";
        if (factor != null && (factor.contains("粉尘") || factor.contains("烟尘") || factor.contains("矽尘") || factor.contains("水泥尘") || factor.contains("游离二氧化硅")) || m.contains("SILICA")) return "粉尘";
        if (Set.of("TWA", "CTWA", "STEL", "CSTE", "CME", "MAC", "PE", "CPE").contains(m)) return "化学物质";
        return Set.of("化学物质", "粉尘", "物理因素", "放射性因素", "生物因素", "其他").contains(blank(explicit)) ? explicit : "其他";
    }
    public static int physicalTable(String metric) {
        return switch (metric(metric)) {
            case "LEX8H", "LEX,8H", "LEX,W" -> 21;
            case "LAEQ" -> 22;
            case "PULSE_NOISE", "LCPEAK" -> 23;
            case "WBGT", "WBGT_TWA" -> 24;
            case "WBGT_LOCATION" -> 25;
            case "POWER_FREQUENCY_E8" -> 26;
            case "POWER_FREQUENCY_E", "PF_E" -> 27;
            case "POWER_FREQUENCY_B", "PF_B" -> 28;
            case "HF_E8" -> 29;
            case "HF_E" -> 30;
            case "HF_H8" -> 31;
            case "HF_H" -> 32;
            case "UHF" -> 33;
            case "MICROWAVE_DOSE" -> 34;
            case "MICROWAVE", "MICROWAVE_POWER" -> 35;
            case "UV", "UVA", "UVB", "UVC", "UV_IRRADIANCE", "UV_EXPOSURE" -> 36;
            case "LASER", "LASER_IRRADIANCE", "LASER_EXPOSURE" -> 37;
            case "VIBRATION", "HAV", "AHW4" -> 38;
            case "VIBRATION_X", "VIBRATION_Y", "VIBRATION_Z" -> 39;
            default -> 0;
        };
    }
    public static String blank(String s) { return s == null ? "" : s.trim(); }
    private static String unit(String s) { return blank(s).replace("μ", "u").replace("µ", "u").replace("³", "3").replace("²", "2").replace("（", "(").replace("）", ")").replace(" ", ""); }
    public static BigDecimal numeric(OhdpResultItem r) {
        if (blank(r.getValueRaw()).isEmpty()) return r.getValueNum();
        var matcher = NUMBER.matcher(r.getValueRaw().trim());
        return matcher.matches() ? new BigDecimal(matcher.group(2)) : null;
    }
    public static List<String> errors(OhdpResultItem r, boolean formal) {
        List<String> e = new ArrayList<>();
        BigDecimal n = numeric(r);
        if (n == null) e.add("缺少有效检测值（原始值仅支持数值或带比较符的数值）");
        if (n != null && r.getValueNum() != null && n.compareTo(r.getValueNum()) != 0) e.add("原始值与数值不一致");
        if (!blank(r.getMixedExposureGroup()).isEmpty()) {
            var raw = NUMBER.matcher(blank(r.getValueRaw()));
            if (raw.matches() && raw.group(1) != null) e.add("混合接触计算不支持带比较符的检测值，请确认可用于相加评价的数值");
        }
        if (r.getLimitValue() == null || r.getLimitValue().signum() <= 0) e.add("限值必须大于0");
        if (unit(r.getUnit()).isEmpty() || !unit(r.getUnit()).equals(unit(r.getLimitUnit()))) e.add("检测单位与限值单位必须一致，请先换算");
        if (r.getDailyExposureHours() != null && (r.getDailyExposureHours().signum() <= 0 || r.getDailyExposureHours().compareTo(new BigDecimal("24")) >= 0)) e.add("每日工时须大于0且小于24");
        if (r.getWeeklyExposureHours() != null && (r.getWeeklyExposureHours().signum() <= 0 || r.getWeeklyExposureHours().compareTo(new BigDecimal("168")) >= 0)) e.add("每周工时须大于0且小于168");
        if (r.getWeeklyWorkDays() != null && (r.getWeeklyWorkDays() < 1 || r.getWeeklyWorkDays() > 7)) e.add("每周工作天数须为1至7");
        if (r.getWeeklyExposureHours() != null && r.getDailyExposureHours() != null && r.getWeeklyWorkDays() != null
                && r.getWeeklyExposureHours().compareTo(r.getDailyExposureHours().multiply(BigDecimal.valueOf(r.getWeeklyWorkDays()))) != 0) e.add("日工时、周工时与每周工作天数不一致");
        if (isTwa(r) && (greater(r.getDailyExposureHours(), 8) || greater(r.getWeeklyExposureHours(), 40))) {
            if (r.getReductionApplicable() == null || blank(r.getEvaluationBasis()).isEmpty()) e.add("超时TWA需确认折减适用性或豁免依据");
            if (greater(r.getWeeklyExposureHours(), 40) && r.getWeeklyWorkDays() == null) e.add("周工时折减需每周工作天数");
        }
        if (isPeak(r)) {
            if (r.getPeakLimitMultiplier() == null || r.getPeakLimitMultiplier().signum() <= 0 || blank(r.getEvaluationBasis()).isEmpty()) e.add("PE需录入经确认的限值倍数及适用依据");
            if (r.getPeakExposureMinutes() == null || r.getPeakTimesPerShift() == null || r.getPeakIntervalMinutes() == null) e.add("PE需接触时间、每班次数和间隔时间");
            if (r.getPeakExposureMinutes() != null && r.getPeakExposureMinutes().signum() <= 0 || r.getPeakTimesPerShift() != null && r.getPeakTimesPerShift() <= 0 || r.getPeakIntervalMinutes() != null && r.getPeakIntervalMinutes().signum() < 0) e.add("PE接触时间、次数须大于0，间隔不得为负数");
        }
        if (formal) {
            required(e, "测点编号", r.getPointCode()); required(e, "采样/测量方式", r.getDetectionMode());
            required(e, "检测依据", r.getDetectionBasis()); required(e, "检测方法", r.getDetectionMethod());
            required(e, "实际仪器编号", r.getInstrument()); required(e, "检测机构", r.getTestingOrganization());
            required(e, "评价限值的适用依据", r.getEvaluationBasis());
            required(e, "原始记录或外部报告编号", r.getSourceRecordNo());
            if (r.getMeasurementDate() == null) e.add("缺少实际采样/测量日期");
            if (Set.of("化学物质", "粉尘").contains(category(r.getFactorName(), r.getMetricType(), r.getFactorCategory()))) {
                required(e, "样品编号", r.getSampleCode());
                required(e, "检出限（不适用时说明原因）", r.getDetectionLimit());
                required(e, "定量下限（不适用时说明原因）", r.getQuantificationLimit());
                if (r.getLaboratoryDate() == null) e.add("缺少实验室检测日期");
                if (r.getLaboratoryDate() != null && r.getMeasurementDate() != null && r.getLaboratoryDate().isBefore(r.getMeasurementDate())) e.add("实验室检测日期早于采样日期");
            }
            if (!Set.of("个体", "定点长时间", "定点短时间", "定点", "直读").contains(blank(r.getDetectionMode()))) e.add("采样/测量方式不受支持");
            if (physicalTable(r.getMetricType()) == 0 && !Set.of("TWA","CTWA","STEL","CSTE","CME","MAC","PE","CPE","SILICA","X_GAMMA","RADIATION_DOSE_RATE","SURFACE_CONTAMINATION","SEALED_SOURCE_LEAKAGE","AIR_RADIONUCLIDE","RADON").contains(metric(r.getMetricType()))) e.add("当前指标尚无对应报告表，请使用支持的指标");
            if (physicalTable(r.getMetricType()) > 0) required(e, "测量条件（频率、劳动强度、接触时间等）", r.getMeasurementConditions());
            if (physicalTable(r.getMetricType()) >= 21 && physicalTable(r.getMetricType()) <= 23 && !Set.of("稳态","非稳态","脉冲").contains(blank(r.getNoiseType()))) e.add("需确认稳态、非稳态或脉冲噪声类型");
            if ("不合格".equals(judge(r))) required(e, "该结果的超标原因", r.getOverLimitReason());
        }
        return e;
    }
    private static void required(List<String> e, String label, String value) { if (blank(value).isEmpty() || "/".equals(value)) e.add("缺少" + label); }
    private static boolean greater(BigDecimal n, int bound) { return n != null && n.compareTo(BigDecimal.valueOf(bound)) > 0; }
    public static boolean isTwa(OhdpResultItem r) { return Set.of("TWA", "CTWA").contains(metric(r.getMetricType())); }
    public static boolean isPeak(OhdpResultItem r) { return Set.of("PE", "CPE").contains(metric(r.getMetricType())); }
    public static BigDecimal reduction(OhdpResultItem r) {
        BigDecimal f = BigDecimal.ONE;
        if (!isTwa(r) || !Boolean.TRUE.equals(r.getReductionApplicable())) return f;
        if (greater(r.getDailyExposureHours(), 8) && !greater(r.getDailyExposureHours(), 24)) {
            BigDecimal h = r.getDailyExposureHours();
            f = f.min(BigDecimal.valueOf(8).multiply(BigDecimal.valueOf(24).subtract(h)).divide(h.multiply(BigDecimal.valueOf(16)), 12, RoundingMode.HALF_UP));
        }
        if (greater(r.getWeeklyExposureHours(), 40) && r.getWeeklyWorkDays() != null && r.getWeeklyWorkDays() > 5) {
            BigDecimal h = r.getWeeklyExposureHours();
            f = f.min(BigDecimal.valueOf(40).multiply(BigDecimal.valueOf(168).subtract(h)).divide(h.multiply(BigDecimal.valueOf(128)), 12, RoundingMode.HALF_UP));
        }
        return f.setScale(6, RoundingMode.HALF_UP);
    }
    public static BigDecimal effectiveLimit(OhdpResultItem r) {
        if (r.getLimitValue() == null) return null;
        if (isPeak(r)) return r.getPeakLimitMultiplier() == null ? null : r.getLimitValue().multiply(r.getPeakLimitMultiplier());
        BigDecimal f = reduction(r);
        return r.getLimitValue().multiply(f).setScale(f.compareTo(BigDecimal.ONE) < 0 ? 2 : 6, RoundingMode.HALF_UP);
    }
    public static String judge(OhdpResultItem r) {
        if (!errors(r, false).isEmpty()) return "未判定";
        BigDecimal n = numeric(r), limit = effectiveLimit(r);
        if (limit == null || limit.signum() <= 0) return "未判定";
        String raw = blank(r.getValueRaw());
        if (raw.startsWith("<") || raw.startsWith("≤")) return n.compareTo(limit) <= 0 ? "合格" : "未判定";
        if (raw.startsWith(">") || raw.startsWith("≥")) return n.compareTo(limit) > 0 || (raw.startsWith(">") && !raw.startsWith(">=") && n.compareTo(limit) == 0) ? "不合格" : "未判定";
        return n.compareTo(limit) <= 0 ? "合格" : "不合格";
    }
}
