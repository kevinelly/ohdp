package com.dsf.radiology.service;

import com.dsf.radiology.entity.RadiologyAcceptanceRule;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.repository.RadiologyAcceptanceRuleRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RadiologyRuleInitializer implements ApplicationRunner {

    private static final String RULE_VERSION = "WS-2026.4";

    private final RadiologyAcceptanceRuleRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedNumeric(RadiologyModality.DR, "ROA", "辐射输出量重复性", null, "10", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "ROQ", "输出量线性", null, "10", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "HVL", "有用线束半值层", "2.3", null, "mmAl", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "ARP", "AEC重复性", null, "10", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "ARS", "AEC响应", null, "20", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "AIC", "AEC电离室一致性", null, "10", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "UBD", "有用线束垂直度偏离", null, "3", "°", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "LRD", "光野与照射野四边的偏离", null, "10", "mm", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "DDI", "探测器剂量指示", null, "20", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "STP", "信号传递特性", "0.98", null, "R²", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "RUE", "响应均匀性", null, "5", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "REA", "测距误差", null, "2", "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "HCR", "高对比度分辨力", "90", null, "%", "WS 76—2020");
        seedNumeric(RadiologyModality.DR, "LCR", "低对比度分辨力", null, "2", "个细节", "WS 76—2020");
        seedTextRule(RadiologyModality.DR, "ART", "伪影", "否", "WS 76—2020");
        seedRpd(RadiologyModality.DR, "25");

        seedNumeric(RadiologyModality.CT, "TPA", "诊断床定位精度", null, "2", "mm", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "GTA", "扫描架倾角精度", null, "2", "°", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "LBA", "定位光精度", null, "2", "mm", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "MTF", "高对比分辨力", "6", null, "lp/cm", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "LCD", "低对比可探测能力", null, "2.5", "mm", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "HUL", "CT值线性", null, "50", "HU", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "HUA", "CT值（水）", null, "4", "HU", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "HUU", "CT值均匀性", null, "5", "HU", "WS 519—2019");
        seedNumeric(RadiologyModality.CT, "NPS", "噪声", null, "0.35", "%", "WS 519—2019");
        seedRpd(RadiologyModality.CT, "2.5");
    }

    private void seedRpd(RadiologyModality modality, String limit) {
        RadiologyAcceptanceRule rule = officialRule(modality, "RPD", "场所卫生防护");
        rule.setModality(modality);
        rule.setItemCode("RPD");
        rule.setItemName("场所卫生防护");
        rule.setRuleVersion(RULE_VERSION);
        rule.setUpperLimit(new BigDecimal(limit));
        rule.setUnit("μSv/h");
        rule.setStandardCode("GBZ 130—2020");
        rule.setStandardName("放射诊断放射防护要求");
        rule.setEnabled(true);
        rule.setDeleted(false);
        repository.save(rule);
    }

    private void seedNumeric(
            RadiologyModality modality, String code, String name, String lower, String upper, String unit, String standard) {
        RadiologyAcceptanceRule rule = officialRule(modality, code, name);
        rule.setLowerLimit(lower == null ? null : new BigDecimal(lower));
        rule.setUpperLimit(upper == null ? null : new BigDecimal(upper));
        rule.setExpectedText(null);
        rule.setUnit(unit);
        rule.setStandardCode(standard);
        rule.setStandardName("官方质量控制检测规范；检测类型差异由标准判定引擎处理");
        rule.setEnabled(true);
        rule.setDeleted(false);
        repository.save(rule);
    }

    private void seedTextRule(RadiologyModality modality, String code, String name, String expected, String standard) {
        RadiologyAcceptanceRule rule = officialRule(modality, code, name);
        rule.setLowerLimit(null);
        rule.setUpperLimit(null);
        rule.setExpectedText(expected);
        rule.setUnit("");
        rule.setStandardCode(standard);
        rule.setStandardName("设备质量控制检测规范");
        rule.setEnabled(true);
        rule.setDeleted(false);
        repository.save(rule);
    }

    private RadiologyAcceptanceRule officialRule(RadiologyModality modality, String code, String name) {
        RadiologyAcceptanceRule rule = repository.findAll().stream()
                .filter(value -> value.getModality() == modality)
                .filter(value -> value.getItemCode().equals(code))
                .filter(value -> value.getRuleVersion().equals(RULE_VERSION))
                .findFirst().orElseGet(RadiologyAcceptanceRule::new);
        rule.setModality(modality);
        rule.setItemCode(code);
        rule.setItemName(name);
        rule.setRuleVersion(RULE_VERSION);
        return rule;
    }
}
