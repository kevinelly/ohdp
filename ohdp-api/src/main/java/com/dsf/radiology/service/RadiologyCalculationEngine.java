package com.dsf.radiology.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RadiologyCalculationEngine {

    public record CalculatedField(String code, String label, BigDecimal valueNum, String valueText, String unit, Integer displayScale) {
        public CalculatedField(String code,String label,BigDecimal valueNum,String valueText,String unit) {
            this(code,label,valueNum,valueText,unit,6);
        }
        public String displayValue() {
            return valueText!=null ? valueText : valueNum==null ? "" : valueNum.setScale(displayScale==null ? 6:displayScale,RoundingMode.HALF_EVEN).toPlainString();
        }
        static CalculatedField number(String code, String label, double value, int scale, String unit) {
            if (!Double.isFinite(value)) throw new ArithmeticException("结果不是有限数值");
            return new CalculatedField(code, label, BigDecimal.valueOf(value), null, unit, scale);
        }

        static CalculatedField text(String code, String label, String value) {
            return new CalculatedField(code, label, null, value, "");
        }
    }

    public record CalculationResult(
            String itemCode,
            boolean complete,
            String message,
            BigDecimal resultValue,
            String resultText,
            String resultUnit,
            List<CalculatedField> fields) {
    }

    private record Regression(String model, double a, double b, double c, double r2) {
        double inverse(double y) {
            return switch (model) {
                case "LINEAR" -> (y - c) / a;
                case "LOG" -> Math.exp((y - c) / a);
                case "POWER" -> Math.pow((y - c) / a, 1 / b);
                default -> Double.NaN;
            };
        }
    }

    public CalculationResult calculate(
            String itemCode,
            Map<String, BigDecimal> numbers,
            Map<String, String> texts,
            Map<String, Map<String, BigDecimal>> allNumbers) {
        return calculate(itemCode, numbers, texts, allNumbers, Map.of());
    }

    public CalculationResult calculate(
            String itemCode,
            Map<String, BigDecimal> numbers,
            Map<String, String> texts,
            Map<String, Map<String, BigDecimal>> allNumbers,
            Map<String, Map<String, String>> allTexts) {
        try {
            return switch (itemCode) {
                case "TVD" -> tvd(numbers);
                case "ROA", "ARP" -> repeatability(itemCode, numbers);
                case "ROQ" -> roq(numbers);
                case "HVL" -> simpleAverage(itemCode, numbers, "HVL", 3, "mmAl");
                case "ETD" -> etd(numbers);
                case "ARS" -> ars(numbers);
                case "AIC" -> aic(numbers);
                case "UBD" -> ubd(numbers,texts);
                case "LRD" -> lrd(numbers);
                case "DDI" -> ddi(numbers);
                case "STP" -> stp(numbers, texts);
                case "RUE" -> rue(numbers, allNumbers.get("STP"), allTexts.get("STP"));
                case "REA" -> rea(numbers);
                case "ILA" -> ila(numbers, texts);
                case "ART" -> art(texts);
                case "HCR" -> hcr(numbers);
                case "LCR" -> lcr(numbers,texts);
                case "TPA" -> tpa(numbers);
                case "GTA" -> gta(numbers, texts);
                case "CTDI" -> ctdi(numbers);
                case "LBA" -> scaledDirect(itemCode, numbers, "LBA", "LBA-FACTOR", 1, "mm");
                case "RST" -> rst(numbers);
                case "MTF" -> scaledDirect(itemCode, numbers, "MTF", "MTF-FACTOR", 1, "lp/cm");
                case "LCD" -> lcd(numbers);
                case "HUL" -> hul(numbers);
                case "HUA" -> simpleAverage(itemCode, numbers, "HUA", 3, "HU");
                case "HUU" -> huu(numbers, allNumbers.get("HUA"));
                case "NPS" -> nps(numbers);
                case "RPD" -> rpd(numbers);
                default -> incomplete(itemCode, "没有对应的计算规则");
            };
        } catch (IncompleteException ex) {
            return incomplete(itemCode, ex.getMessage());
        } catch (ArithmeticException ex) {
            return incomplete(itemCode, "计算条件不合法：" + ex.getMessage());
        }
    }

    private CalculationResult tvd(Map<String, BigDecimal> n) {
        List<CalculatedField> out = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        for (int group = 1; group <= 3; group++) {
            double nominal = req(n, "TVD" + group + "-0");
            double average = mean(req(n, "TVD" + group + "-1"), req(n, "TVD" + group + "-2"), req(n, "TVD" + group + "-3"));
            double corrected = average * value(n, "TVD-K" + group, 1);
            double deviation = corrected - nominal;
            double relative = 100 * deviation / nominal;
            out.add(CalculatedField.number("MEAN_" + group, "第" + group + "组校正均值", corrected, 2, "kV"));
            out.add(CalculatedField.number("DEVIATION_KV_" + group, "第" + group + "组绝对偏离", deviation, 1, "kV"));
            out.add(CalculatedField.number("DEVIATION_PERCENT_" + group, "第" + group + "组相对偏离", relative, 1, "%"));
            out.add(CalculatedField.number("DISPLAY_"+group,"第"+group+"组检测结果",nominal<100 ? deviation : relative,1,nominal<100 ? "kV" : "%"));
            summaries.add("第"+group+"组 "+round(nominal<100 ? deviation:relative,1).toPlainString()+(nominal<100 ? " kV":"%"));
        }
        return completeGrouped("TVD", summaries, out);
    }

    private CalculationResult repeatability(String code, Map<String, BigDecimal> n) {
        List<Double> values = new ArrayList<>();
        for (int i = 1; i <= 5; i++) values.add(req(n, code + i));
        double factor = value(n, code + "-K", 1);
        double average = mean(values) * factor;
        double cv = sampleStdDev(values) / mean(values) * 100;
        return complete(code, cv, null, "%", List.of(
                CalculatedField.number("MEAN", "校正平均值", average, code.equals("ROA") ? 4 : rawScale(n,code), code.equals("ROA") ? (value(n,"__MICROGY",0)==1?"μGy":"mGy"):""),
                CalculatedField.number("RESULT", "变异系数", cv, 1, "%")));
    }

    private int rawScale(Map<String,BigDecimal> n,String prefix) {
        return n.entrySet().stream().filter(e->e.getKey().startsWith(prefix) && e.getValue()!=null && !e.getKey().contains("-K"))
            .mapToInt(e->Math.max(0,e.getValue().scale())).max().orElse(1);
    }

    private CalculationResult roq(Map<String, BigDecimal> n) {
        List<Double> first=new ArrayList<>(), second=new ArrayList<>();
        for(int i=1;i<=5;i++) { first.add(req(n,"ROQ1-"+i));second.add(req(n,"ROQ2-"+i)); }
        double k=value(n,"ROQ-K",1);
        if(k<=0) throw new IncompleteException("校准因子必须大于0");
        double m1=mean(first)*k, m2=mean(second)*k;
        double q1=m1/(req(n,"I1")*req(n,"T1")), q2=m2/(req(n,"I2")*req(n,"T2"));
        double signed=100*(q1-q2)/(q1+q2);
        return complete("ROQ",Math.abs(signed),round(signed,1).toPlainString()+"%","%",List.of(
            CalculatedField.number("MEAN_1","第一组校正平均值",m1,4,""),
            CalculatedField.number("MEAN_2","第二组校正平均值",m2,4,""),
            CalculatedField.number("Q_1","第一组归一化输出量",q1,4,""),
            CalculatedField.number("Q_2","第二组归一化输出量",q2,4,""),
            CalculatedField.number("RESULT","输出量线性",Math.abs(signed),1,"%")));
    }

    private CalculationResult simpleAverage(String item, Map<String, BigDecimal> n, String prefix, int count, String unit) {
        List<Double> values = new ArrayList<>();
        for (int i = 1; i <= count; i++) values.add(req(n, prefix + i));
        double average = mean(values);
        return complete(item, average, null, unit, List.of(CalculatedField.number("RESULT", "检测结果", average, item.equals("HVL") ? 1 : 1, unit)));
    }

    private CalculationResult etd(Map<String, BigDecimal> n) {
        List<CalculatedField> out = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        for (int group = 1; group <= 3; group++) {
            double nominal = req(n, "ETD" + group + "-0");
            double average = mean(req(n, "ETD" + group + "-1"), req(n, "ETD" + group + "-2"), req(n, "ETD" + group + "-3"));
            double corrected = average * value(n, "ETD-K" + group, 1);
            double deviation = corrected - nominal;
            double relative = 100 * deviation / nominal;
            out.add(CalculatedField.number("MEAN_" + group, "第" + group + "组校正均值", corrected, rawScale(n,"ETD"+group+"-"), "ms"));
            out.add(CalculatedField.number("DEVIATION_MS_" + group, "第" + group + "组绝对偏离", deviation, 1, "ms"));
            out.add(CalculatedField.number("DEVIATION_PERCENT_" + group, "第" + group + "组相对偏离", relative, 1, "%"));
            summaries.add("第" + group + "组 " + round(deviation, 1).toPlainString() + " ms（"
                    + round(relative, 1).toPlainString() + "%）");
        }
        return completeGrouped("ETD", summaries, out);
    }

    private CalculationResult ars(Map<String, BigDecimal> n) {
        double m1 = mean(req(n, "ARS1-1"), req(n, "ARS1-2"), req(n, "ARS1-3"), req(n, "ARS1-4"), req(n, "ARS1-5"));
        double m2 = mean(req(n, "ARS2-1"), req(n, "ARS2-2"), req(n, "ARS2-3"), req(n, "ARS2-4"), req(n, "ARS2-5"));
        double overall = mean(m1, m2);
        double e1 = 100 * (m1 - overall) / overall;
        double e2 = 100 * (m2 - overall) / overall;
        double result = Math.max(Math.abs(e1), Math.abs(e2));
        return complete("ARS", result, null, "%", List.of(
                CalculatedField.number("MEAN_1", "第一组平均值", m1, rawScale(n,"ARS"), ""),
                CalculatedField.number("MEAN_2", "第二组平均值", m2, rawScale(n,"ARS"), ""),
                CalculatedField.number("OVERALL_MEAN", "总平均值", overall, rawScale(n,"ARS"), ""),
                CalculatedField.number("DEVIATION_1", "第一组相对偏差", e1, 1, "%"),
                CalculatedField.number("DEVIATION_2", "第二组相对偏差", e2, 1, "%"),
                CalculatedField.number("RESULT", "最大相对偏差", result, 1, "%")));
    }

    private CalculationResult aic(Map<String, BigDecimal> n) {
        double center = mean(req(n, "AIC-C1"), req(n, "AIC-C2"), req(n, "AIC-C3"));
        double left = mean(req(n, "AIC-L1"), req(n, "AIC-L2"), req(n, "AIC-L3"));
        double right = mean(req(n, "AIC-R1"), req(n, "AIC-R2"), req(n, "AIC-R3"));
        double overall = mean(center, left, right);
        double ec = 100 * (center - overall) / overall;
        double el = 100 * (left - overall) / overall;
        double er = 100 * (right - overall) / overall;
        double result = Math.max(Math.abs(ec), Math.max(Math.abs(el), Math.abs(er)));
        return complete("AIC", result, null, "%", List.of(
                CalculatedField.number("CENTER_MEAN", "中央电离室平均值", center, 1, ""),
                CalculatedField.number("LEFT_MEAN", "左电离室平均值", left, 1, ""),
                CalculatedField.number("RIGHT_MEAN", "右电离室平均值", right, 1, ""),
                CalculatedField.number("OVERALL_MEAN", "总平均值", overall, 1, ""),
                CalculatedField.number("RESULT", "最大偏差", result, 1, "%")));
    }

    private CalculationResult ubd(Map<String,BigDecimal> n,Map<String,String> texts) {
        double value=req(n,"UBD");String qualifier=texts.getOrDefault("UBD-QUALIFIER", "");
        if(!List.of("",">","<","＞","＜").contains(qualifier)) throw new IncompleteException("限定符须为大于、小于或空");
        return complete("UBD",value,qualifier+round(value,1).toPlainString()+"°","°",List.of(CalculatedField.number("RESULT","垂直度偏离",value,1,"°")));
    }

    private CalculationResult direct(String item, Map<String, BigDecimal> n, String key, String unit) {
        double result = req(n, key);
        return complete(item, result, null, unit, List.of(CalculatedField.number("RESULT", "检测结果", result, 1, unit)));
    }

    private CalculationResult scaledDirect(
            String item, Map<String, BigDecimal> n, String key, String factorKey, double defaultFactor, String unit) {
        double raw = req(n, key);
        double factor = value(n, factorKey, defaultFactor);
        double result = raw * factor;
        return complete(item, result, null, unit, List.of(
                CalculatedField.number("RAW", "原始读数", raw, 4, ""),
                CalculatedField.number("FACTOR", "换算系数", factor, 6, ""),
                CalculatedField.number("RESULT", "检测结果", result, 1, unit)));
    }

    private CalculationResult lrd(Map<String, BigDecimal> n) {
        List<CalculatedField> out = new ArrayList<>();
        double max = 0;
        for (String side : List.of("a1", "a2", "b1", "b2")) {
            double result = req(n, "LRD-" + side + "-1") - req(n, "LRD-" + side + "-0");
            max = Math.max(max, Math.abs(result));
            out.add(CalculatedField.number("RESULT_" + side.toUpperCase(), side + "边偏离", result, 1, "mm"));
        }
        out.add(CalculatedField.number("RESULT", "最大绝对偏离", max, 1, "mm"));
        return complete("LRD", max, null, "mm", out);
    }

    private CalculationResult ddi(Map<String, BigDecimal> n) {
        double average=mean(req(n,"DDI1"),req(n,"DDI2"),req(n,"DDI3"));
        BigDecimal reference=n.get("DDI-CALCULATED");
        if(reference==null) reference=n.get("DDI-BASELINE");
        if(reference==null) return complete("DDI",average,"建立基线值："+round(average,3).toPlainString(),"",List.of(
            CalculatedField.number("MEAN","DDI测量平均值",average,3,""),CalculatedField.number("RESULT","DDI基线值",average,3,"")));
        if(reference.signum()<=0) throw new IncompleteException("DDI计算值或基线值必须大于0");
        double result=100*(average-reference.doubleValue())/reference.doubleValue();
        return complete("DDI",Math.abs(result),round(result,1).toPlainString()+"%","%",List.of(
            CalculatedField.number("MEAN","DDI测量平均值",average,3,""),
            CalculatedField.number("DEVIATION","相对偏差",result,1,"%"),CalculatedField.number("RESULT","绝对相对偏差",Math.abs(result),1,"%")));
    }

    private CalculationResult stp(Map<String, BigDecimal> n, Map<String, String> texts) {
        double[] x = new double[5];
        double[] y = new double[5];
        for (int i = 0; i < 5; i++) {
            x[i] = req(n, "STP-K" + (i + 1));
            y[i] = req(n, "STP-R" + (i + 1));
        }
        List<Regression> regressions = regressions(x, y);
        Regression best = regressions.stream().max(Comparator.comparingDouble(Regression::r2)).orElseThrow();
        Regression selected = selectRegression(regressions, text(texts, "STP-MODEL"));
        List<CalculatedField> out = new ArrayList<>();
        for (Regression regression : regressions) {
            out.add(CalculatedField.number(regression.model + "_A", regression.model + " 系数 a", regression.a, 6, ""));
            out.add(CalculatedField.number(regression.model + "_B", regression.model + " 指数/自变量参数 b", regression.b, 6, ""));
            out.add(CalculatedField.number(regression.model + "_C", regression.model + " 常数项 c", regression.c, 6, ""));
            out.add(CalculatedField.number(regression.model + "_R2", regression.model + " 决定系数 R²", regression.r2, 6, ""));
        }
        out.add(CalculatedField.text("BEST_MODEL", "拟合优度最高的信号传递模型", best.model));
        out.add(CalculatedField.text("SELECTED_MODEL", "采用的信号传递模型", selected.model));
        out.add(CalculatedField.number("RESULT", "所选模型决定系数 R²", selected.r2, 4, ""));
        return complete("STP", selected.r2, round(selected.r2,4).toPlainString(), "", out);
    }

    private CalculationResult rue(
            Map<String, BigDecimal> n, Map<String, BigDecimal> stp, Map<String, String> stpTexts) {
        if (stp == null) throw new IncompleteException("需先录入 STP 剂量与像素值数据");
        double[] x = new double[5];
        double[] y = new double[5];
        for (int i = 0; i < 5; i++) {
            x[i] = req(stp, "STP-K" + (i + 1));
            y[i] = req(stp, "STP-R" + (i + 1));
        }
        Regression selected = selectRegression(regressions(x, y), text(stpTexts == null ? Map.of() : stpTexts, "STP-MODEL"));
        List<Double> doses = new ArrayList<>();
        List<CalculatedField> out = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            double dose = selected.inverse(req(n, "RUE" + i));
            if (!Double.isFinite(dose)) throw new IncompleteException("STP 逆变换结果无效");
            doses.add(dose);
            out.add(CalculatedField.number("DOSE_" + i, "区域" + i + "剂量值", dose, rawScale(n,"RUE"), ""));
        }
        double average = mean(doses);
        double cv = sampleStdDev(doses) / average * 100;
        out.add(CalculatedField.text("MODEL", "采用拟合模型", selected.model));
        out.add(CalculatedField.number("MEAN", "剂量平均值", average, rawScale(n,"RUE"), ""));
        out.add(CalculatedField.number("RESULT", "响应均匀性", cv, 1, "%"));
        return complete("RUE", cv, null, "%", out);
    }

    private Regression selectRegression(List<Regression> regressions, String requestedModel) {
        String model = requestedModel == null ? "" : requestedModel.trim().toUpperCase(java.util.Locale.ROOT);
        if (model.isEmpty() || model.equals("BEST_FIT") || model.equals("BEST")) {
            return regressions.stream().max(Comparator.comparingDouble(Regression::r2)).orElseThrow();
        }
        return regressions.stream().filter(value -> value.model.equals(model)).findFirst()
                .orElseThrow(() -> new IncompleteException("STP-MODEL 仅支持 LINEAR、LOG、POWER 或 BEST_FIT"));
    }

    private CalculationResult rea(Map<String, BigDecimal> n) {
        double horizontal = 100 * (req(n, "REA-am") - req(n, "REA-at")) / req(n, "REA-at");
        double vertical = 100 * (req(n, "REA-bm") - req(n, "REA-bt")) / req(n, "REA-bt");
        double result = Math.max(Math.abs(horizontal), Math.abs(vertical));
        return complete("REA", result, null, "%", List.of(
                CalculatedField.number("HORIZONTAL", "水平测距误差", horizontal, 1, "%"),
                CalculatedField.number("VERTICAL", "垂直测距误差", vertical, 1, "%"),
                CalculatedField.number("RESULT", "最大绝对误差", result, 1, "%")));
    }

    private CalculationResult ila(Map<String, BigDecimal> n, Map<String, String> texts) {
        String answer = text(texts, "是否有残影（如“否”无需填后两项）");
        if (isNo(answer)) {
            return complete("ILA", 0, "不存在残影", "%", List.of(CalculatedField.text("RESULT_TEXT", "是否存在残影", "否")));
        }
        if (!isYes(answer)) throw new IncompleteException("请填写是否有残影（是/否）");
        double d1 = req(n, "残影区D1");
        double d0 = req(n, "非残影区D0");
        if (d0 == 0) throw new IncompleteException("非残影区 D0 不能为 0");
        double result = Math.abs(100 * (d1 - d0) / d0);
        return complete("ILA", result, "存在残影，且像素值误差为"+round(result,1).toPlainString()+"%", "%", List.of(
                CalculatedField.text("RESULT_TEXT", "是否存在残影", "是"),
                CalculatedField.number("RESULT", "相对误差", result, 1, "%")));
    }

    private CalculationResult art(Map<String, String> texts) {
        String answer = text(texts, "填“是”或“否”");
        if (!isYes(answer) && !isNo(answer)) throw new IncompleteException("请填写是否有伪影（是/否）");
        String source=texts.getOrDefault("ART-SOURCE", "");
        if(isYes(answer) && !List.of("探测器","监视器").contains(source)) throw new IncompleteException("存在伪影时须选择来源：探测器或监视器");
        String result=isYes(answer) ? "存在影响临床诊断的伪影，来源"+source : "无影响临床诊断的伪影";
        return complete("ART",isYes(answer)?1:0,result,"",List.of(CalculatedField.text("RESULT_TEXT","伪影结果",result)));
    }

    private CalculationResult hcr(Map<String, BigDecimal> n) {
        double measured=req(n,"HCR1");
        BigDecimal reference=n.get("HCR-MANUFACTURER");
        if(reference==null) reference=n.get("厂家规定值/基线值"); // imported legacy records
        double factor=1;
        if(reference==null && n.get("HCR-NYQUIST")!=null) {reference=n.get("HCR-NYQUIST");factor=1.4;}
        if(reference==null) reference=n.get("HCR-BASELINE");
        if(reference==null) return complete("HCR",measured,"建立基线值："+round(measured,1).toPlainString()+" lp/mm","lp/mm",List.of(
            CalculatedField.number("RESULT","分辨力基线",measured,1,"lp/mm")));
        if(reference.signum()<=0) throw new IncompleteException("分辨力参考值必须大于0");
        double result=100*measured/(reference.doubleValue()*factor);
        return complete("HCR",result,null,"%",List.of(CalculatedField.number("RESULT","参考值保持率",result,1,"%")));
    }

    private CalculationResult lcr(Map<String, BigDecimal> n,Map<String,String> texts) {
        if("%".equals(texts.get("LCR-BASELINE-UNIT")) && n.get("厂家规定值/基线值")!=null && value(n,"__ESTABLISH_BASELINE",0)!=1)
            throw new IncompleteException("百分比基线与细节数的换算尚无明确业务公式，请核定后使用；可选择个数基线");
        double details = req(n, "LCR1（可观察细节数）");
        double contrast = req(n, "LCR2（可观察对比度）");
        if (value(n,"__ESTABLISH_BASELINE",0)==1 || n.get("厂家规定值/基线值")==null)
            return complete("LCR",details,"建立基线值可观察影像细节数"+round(details,0).toPlainString()+"个（对比度"+round(contrast,3).stripTrailingZeros().toPlainString()+"%）", "个细节",List.of(
                    CalculatedField.number("CONTRAST","可观察对比度",contrast,3,"%"),
                    CalculatedField.number("RESULT","可观察细节数基线",details,2,"个细节")));
        double baseline = req(n, "厂家规定值/基线值");
        double signedDelta = details - baseline;
        double result = Math.abs(signedDelta);
        return complete("LCR", result, "与基线值比较相差"+round(signedDelta,0).toPlainString()+"个细节变化（对比度"+round(contrast,3).stripTrailingZeros().toPlainString()+"%）", "", List.of(
                CalculatedField.number("DETAIL_DELTA", "可观察细节数减基线值", signedDelta, 2, ""),
                CalculatedField.number("CONTRAST", "可观察对比度", contrast, 3, "%"),
                CalculatedField.number("RESULT", "与基线的绝对差", result, 2, "")));
    }

    private CalculationResult tpa(Map<String, BigDecimal> n) {
        double forward = Math.abs(req(n, "Lfor") - req(n, "Cfor")) - req(n, "Dfor");
        double backward = Math.abs(req(n, "Lback") - req(n, "Cback")) - req(n, "Dback");
        double result = Math.max(Math.abs(forward), Math.abs(backward));
        return complete("TPA", result, null, "mm", List.of(
                CalculatedField.number("FORWARD", "前进定位误差", forward, 2, "mm"),
                CalculatedField.number("BACKWARD", "后退定位误差", backward, 2, "mm"),
                CalculatedField.number("RESULT", "最大绝对定位误差", result, 2, "mm")));
    }

    private CalculationResult gta(Map<String, BigDecimal> n, Map<String,String> texts) {
        double alpha1 = req(n, "扫描倾角显示值α1");
        String method=texts.get("__METHOD");
        if(method!=null && !List.of("PHANTOM","INCLINOMETER").contains(method)) throw new IncompleteException("请选择模体法或倾角仪法");
        double measured;
        if ("PHANTOM".equals(method) || (method==null && n.get("模体上下边沿间距L1") != null && n.get("倾斜α1角后模体上下边沿间距L2") != null)) {
            double ratio = req(n, "模体上下边沿间距L1") / req(n, "倾斜α1角后模体上下边沿间距L2");
            if (ratio < -1 || ratio > 1) throw new IncompleteException("GTA 模体间距比必须在 [-1,1] 范围");
            measured = Math.toDegrees(Math.acos(ratio));
        } else {
            measured = req(n, "测量值α2");
        }
        double result = measured - alpha1;
        return complete("GTA", Math.abs(result), null, "°", List.of(
                CalculatedField.number("MEASURED", "测量角度", measured, 2, "°"),
                CalculatedField.number("DEVIATION", "倾角偏差", result, 2, "°"),
                CalculatedField.number("RESULT", "绝对倾角偏差", Math.abs(result), 2, "°")));
    }

    private CalculationResult ctdi(Map<String, BigDecimal> n) {
        List<CalculatedField> out = new ArrayList<>();
        double head = ctdiGroup(n, "H", "层数n1", "层厚T1", "刻度系数k1", out);
        double body = value(n, "__HEAD_ONLY", 0) == 1 ? head : ctdiGroup(n, "S", "层数n2", "层厚T2", "刻度系数k2", out);
        double result = Math.max(head, body);
        out.add(CalculatedField.number("RESULT", "CTDIw 最大值", result, 1, "mGy"));
        String description="头模 CTDIw："+round(head,1).toPlainString()+" mGy";
        if (value(n,"__HEAD_ONLY",0)!=1) description+="；体模 CTDIw："+round(body,1).toPlainString()+" mGy";
        return complete("CTDI", result, description, "mGy", out);
    }

    private double ctdiGroup(Map<String, BigDecimal> n, String prefix, String nKey, String tKey, String kKey, List<CalculatedField> out) {
        double layers = req(n, nKey);
        double thickness = req(n, tKey);
        double factor = req(n, kKey);
        double center = mean(req(n, prefix + "CD1"), req(n, prefix + "CD2"), req(n, prefix + "CD3"));
        List<Double> peripheral = new ArrayList<>();
        for (String direction : List.of("U", "D", "L", "R")) {
            peripheral.add(mean(req(n, prefix + direction + "D1"), req(n, prefix + direction + "D2"), req(n, prefix + direction + "D3")));
        }
        if (layers <= 0 || thickness <= 0 || factor <= 0) throw new IncompleteException("CTDI层数、层厚和校准系数必须大于0");
        double scale = 10 * factor / (layers * thickness);
        double center100 = center * scale;
        double peripheral100 = mean(peripheral) * scale;
        double weighted = center100 / 3 + peripheral100 * 2 / 3;
        String name = prefix.equals("H") ? "HEAD" : "BODY";
        out.add(CalculatedField.number(name+"_MEAN_CD", "中心原始读数平均值", center, rawScale(n,prefix+"CD"), "mGy·cm"));
        for(int i=0;i<4;i++) {
            String direction=List.of("U","D","L","R").get(i);
            out.add(CalculatedField.number(name+"_MEAN_"+direction+"D", "周边"+List.of("上","下","左","右").get(i)+"原始读数平均值", peripheral.get(i), rawScale(n,prefix+direction+"D"), "mGy·cm"));
            out.add(CalculatedField.number(name+"_"+direction+"_CTDI100", "周边"+List.of("上","下","左","右").get(i)+" CTDI100", peripheral.get(i)*scale, 2, "mGy"));
        }
        out.add(CalculatedField.number(name + "_CENTER", name + " 中心 CTDI100", center100, 2, "mGy"));
        out.add(CalculatedField.number(name + "_PERIPHERAL", name + " 周边 CTDI100", peripheral100, 2, "mGy"));
        out.add(CalculatedField.number(name + "_CTDIW", name + " CTDIw", weighted, 1, "mGy"));
        return weighted;
    }

    private CalculationResult rst(Map<String, BigDecimal> n) {
        double background=req(n,"CTbackground"), maximum=req(n,"CTmax");
        double half=(background+maximum)/2, width=maximum-background;
        double average=mean(req(n,"R1"),req(n,"R2"),req(n,"R3"),req(n,"R4"));
        double factor=req(n,"RST-FACTOR");
        if(factor<=0) throw new IncompleteException("层厚换算系数必须大于0，并有模体说明书依据");
        double measured=average*factor, result=measured-req(n,"R0");
        return complete("RST",Math.abs(result),null,"mm",List.of(
            CalculatedField.number("WWMIN","最小窗宽",width,0,"HU"),CalculatedField.number("CTHM","半高CT值",half,0,"HU"),
            CalculatedField.number("MEAN","实测宽度平均值",average,2,"pixel"),CalculatedField.number("FACTOR","模体层厚换算系数",factor,2,"mm/pixel"),
            CalculatedField.number("MEASURED","实测层厚",measured,2,"mm"),CalculatedField.number("DEVIATION","层厚偏差",result,1,"mm"),
            CalculatedField.number("RESULT","绝对层厚偏差",Math.abs(result),1,"mm")));
    }

    private CalculationResult lcd(Map<String, BigDecimal> n) {
        int[] diameters={15,9,8,7,6,5,4,3,2};
        String[] suffix={"1","05","03"}, legacy={"1","0.5","0.3"};
        double[] contrast={1,0.5,0.3}, values=new double[3];
        List<CalculatedField> fields=new ArrayList<>();
        for(int i=0;i<3;i++) {
            BigDecimal count=n.get("LCD-COUNT-"+suffix[i]);double diameter;
            if(count!=null) {
                int index;
                try {index=count.intValueExact();}catch(ArithmeticException e){throw new IncompleteException("可见孔径数必须为1至9的整数");}
                if(index<1 || index>9) throw new IncompleteException("可见孔径数必须为1至9的整数");
                diameter=diameters[index-1]; values[i]=diameter*contrast[i];
            } else {diameter=req(n,"mm"+legacy[i]);values[i]=req(n,"NUM"+legacy[i])*diameter;}
            values[i]=round(values[i],1).doubleValue();
            fields.add(CalculatedField.number("DIAMETER_"+suffix[i],"最小直径 "+legacy[i]+"%",diameter,1,"mm"));
            fields.add(CalculatedField.number("CAL_"+suffix[i],legacy[i]+"% 对比度计算值",values[i],1,"mm"));
        }
        double result=mean(values[0],values[1],values[2]);
        fields.add(CalculatedField.number("RESULT","低对比可探测能力",result,1,"mm"));
        return complete("LCD",result,null,"mm",fields);
    }

    private CalculationResult hul(Map<String, BigDecimal> n) {
        Map<String, Double> nominal = new LinkedHashMap<>();
        nominal.put("Air", value(n, "Air-REF", -1000));
        nominal.put("LDPE", value(n, "LDPE-REF", -100));
        nominal.put("Acylic", value(n, "Acylic-REF", 120));
        nominal.put("Teflon", value(n, "Teflon-REF", 950));
        List<CalculatedField> out = new ArrayList<>();
        double max = 0;
        for (Map.Entry<String, Double> entry : nominal.entrySet()) {
            String material = entry.getKey();
            double average = mean(req(n, material + "-HU1"), req(n, material + "-HU2"), req(n, material + "-HU3"));
            double deviation = average - entry.getValue();
            max = Math.max(max, Math.abs(deviation));
            out.add(CalculatedField.number(material.toUpperCase() + "_MEAN", material + " 平均 CT 值", average, 1, "HU"));
            out.add(CalculatedField.number(material.toUpperCase() + "_DEVIATION", material + " CT 值偏差", deviation, 1, "HU"));
        }
        out.add(CalculatedField.number("RESULT", "最大绝对 CT 值偏差", max, 1, "HU"));
        return complete("HUL", max, null, "HU", out);
    }

    private CalculationResult huu(Map<String, BigDecimal> n, Map<String, BigDecimal> hua) {
        double center;
        if (hua != null && hua.values().stream().filter(Objects::nonNull).count() >= 3) {
            center = mean(req(hua, "HUA1"), req(hua, "HUA2"), req(hua, "HUA3"));
        } else {
            throw new IncompleteException("需先录入 HUA 水模体中心 CT 值");
        }
        List<CalculatedField> out = new ArrayList<>();
        double max = 0;
        for (String direction : List.of("U", "D", "L", "R")) {
            List<Double> readings = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                BigDecimal value = n.get("HUU-" + direction + i);
                if (value != null) readings.add(value.doubleValue());
            }
            if (readings.size() < 2) throw new IncompleteException("HUU " + direction + " 方向至少需要 2 个读数");
            double average = mean(readings);
            double deviation = average - center;
            max = Math.max(max, Math.abs(deviation));
            out.add(CalculatedField.number("MEAN_" + direction, direction + " 方向平均值", average, 1, "HU"));
            out.add(CalculatedField.number("DEVIATION_" + direction, direction + " 方向与中心差值", deviation, 1, "HU"));
        }
        out.add(CalculatedField.number("RESULT", "最大绝对均匀性偏差", max, 1, "HU"));
        return complete("HUU", max, null, "HU", out);
    }

    private CalculationResult nps(Map<String, BigDecimal> n) {
        double layer = value(n, "NPS-n", 10);
        double average = mean(req(n, "NPS1"), req(n, "NPS2"), req(n, "NPS3"));
        double result = Math.sqrt(layer / 10) * average / 10;
        return complete("NPS", result, null, "%", List.of(
                CalculatedField.number("MEAN", "噪声读数平均值", average, 1, ""),
                CalculatedField.number("RESULT", "噪声", result, 2, "%")));
    }

    private CalculationResult rpd(Map<String, BigDecimal> n) {
        List<Double> readings = new ArrayList<>();
        for (int i = 1; i <= 10; i++) readings.add(req(n, "RPD0-" + i));
        double k = value(n, "k", 1);
        double a = value(n, "a", 0.001);
        double average = mean(readings);
        double result = k * a * average;
        double std = k * a * sampleStdDev(readings);
        String text = round(result, 3).toPlainString() + "±" + round(std, 3).toPlainString();
        return complete("RPD", result, text, "μSv/h", List.of(
                CalculatedField.number("MEAN", "本底读数平均值", average, 3, ""),
                CalculatedField.number("STD_DEV", "本底标准偏差", std, 3, "μSv/h"),
                CalculatedField.number("RESULT", "本底剂量率", result, 3, "μSv/h"),
                CalculatedField.text("RESULT_TEXT", "本底检测结果", text)));
    }

    public List<BigDecimal> calculateProtectionReadings(List<BigDecimal> readings, BigDecimal k, BigDecimal a) {
        return calculateProtectionReadings(readings, k, a, BigDecimal.ONE);
    }

    public List<BigDecimal> calculateProtectionReadings(
            List<BigDecimal> readings, BigDecimal k, BigDecimal a, BigDecimal normalizationFactor) {
        if (readings == null || readings.isEmpty() || readings.stream().anyMatch(Objects::isNull)) return List.of();
        double average = readings.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
        double result = average
                * (k == null ? 1 : k.doubleValue())
                * (a == null ? 0.001 : a.doubleValue())
                * (normalizationFactor == null ? 1 : normalizationFactor.doubleValue());
        return List.of(BigDecimal.valueOf(average), BigDecimal.valueOf(result));
    }

    private List<Regression> regressions(double[] x, double[] y) {
        List<Regression> out = new ArrayList<>();
        out.add(regression("LINEAR", x, y, false));
        if (arraysPositive(x)) out.add(regression("LOG", x, y, true));
        if (arraysPositive(x)) out.add(powerRegression(x, y));
        return out;
    }

    private Regression regression(String model, double[] x, double[] y, boolean logX) {
        double[] transformed = new double[x.length];
        for (int i = 0; i < x.length; i++) transformed[i] = logX ? Math.log(x[i]) : x[i];
        double xMean = mean(transformed);
        double yMean = mean(y);
        double numerator = 0;
        double denominator = 0;
        for (int i = 0; i < transformed.length; i++) {
            numerator += (transformed[i] - xMean) * (y[i] - yMean);
            denominator += Math.pow(transformed[i] - xMean, 2);
        }
        double slope = numerator / denominator;
        double intercept = yMean - slope * xMean;
        double r2 = r2(y, transformed, slope, intercept, false);
        return new Regression(model, slope, logX ? 0 : 1, intercept, r2);
    }

    private Regression powerRegression(double[] x, double[] y) {
        Regression best = null;
        for (double exponent = -5; exponent <= 5; exponent += 0.02) {
            if (Math.abs(exponent) < 0.01) continue;
            Regression candidate = powerAtExponent(x, y, exponent);
            if (best == null || candidate.r2 > best.r2) best = candidate;
        }
        double center = best == null ? 1 : best.b;
        for (double exponent = center - 0.03; exponent <= center + 0.03; exponent += 0.0002) {
            if (Math.abs(exponent) < 0.0001) continue;
            Regression candidate = powerAtExponent(x, y, exponent);
            if (best == null || candidate.r2 > best.r2) best = candidate;
        }
        return best;
    }

    private Regression powerAtExponent(double[] x, double[] y, double exponent) {
        double[] powered = new double[x.length];
        for (int i = 0; i < x.length; i++) powered[i] = Math.pow(x[i], exponent);
        double zMean = mean(powered);
        double yMean = mean(y);
        double numerator = 0;
        double denominator = 0;
        for (int i = 0; i < powered.length; i++) {
            numerator += (powered[i] - zMean) * (y[i] - yMean);
            denominator += Math.pow(powered[i] - zMean, 2);
        }
        if (denominator == 0) return new Regression("POWER", 0, exponent, yMean, Double.NEGATIVE_INFINITY);
        double coefficient = numerator / denominator;
        double offset = yMean - coefficient * zMean;
        double r2 = r2(y, powered, coefficient, offset, false);
        return new Regression("POWER", coefficient, exponent, offset, r2);
    }

    private double r2(double[] actual, double[] x, double slope, double intercept, boolean exponential) {
        double mean = mean(actual);
        double residual = 0;
        double total = 0;
        for (int i = 0; i < actual.length; i++) {
            double predicted = exponential ? Math.exp(intercept + slope * x[i]) : intercept + slope * x[i];
            residual += Math.pow(actual[i] - predicted, 2);
            total += Math.pow(actual[i] - mean, 2);
        }
        return total == 0 ? 1 : 1 - residual / total;
    }

    private boolean arraysPositive(double[] values) {
        for (double value : values) if (value <= 0) return false;
        return true;
    }

    private CalculationResult complete(
            String item, double result, String resultText, String unit, List<CalculatedField> fields) {
        return new CalculationResult(item, true, "计算完成", BigDecimal.valueOf(result), resultText, unit, List.copyOf(fields));
    }

    private CalculationResult completeGrouped(String item, List<String> summaries, List<CalculatedField> fields) {
        String summary = String.join("；", summaries);
        List<CalculatedField> persistedFields = new ArrayList<>(fields);
        persistedFields.add(CalculatedField.text("RESULT_TEXT", "分组检测结果", summary));
        return new CalculationResult(
                item,
                true,
                "计算完成；三组结果单位不同，须分别判定",
                null,
                summary,
                "",
                List.copyOf(persistedFields));
    }

    private CalculationResult incomplete(String item, String message) {
        return new CalculationResult(item, false, message, null, null, "", List.of());
    }

    private static BigDecimal round(double value, int scale) {
        if (!Double.isFinite(value)) throw new ArithmeticException("结果不是有限数值");
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN);
    }

    private double req(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        if (value == null) throw new IncompleteException("缺少字段：" + key);
        return value.doubleValue();
    }

    private double value(Map<String, BigDecimal> values, String key, double defaultValue) {
        BigDecimal value = values.get(key);
        return value == null ? defaultValue : value.doubleValue();
    }

    private String text(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    private boolean isYes(String value) {
        return value.equalsIgnoreCase("yes") || value.equals("是") || value.equals("有");
    }

    private boolean isNo(String value) {
        return value.equalsIgnoreCase("no") || value.equals("否") || value.equals("无");
    }

    private double mean(double... values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private double sampleStdDev(List<Double> values) {
        if (values.size() < 2) throw new IncompleteException("标准偏差至少需要 2 个数值");
        double mean = mean(values);
        double sum = values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum();
        return Math.sqrt(sum / (values.size() - 1));
    }

    private static class IncompleteException extends RuntimeException {
        IncompleteException(String message) {
            super(message);
        }
    }
}
