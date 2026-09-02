package com.dsf.radiology.service;

import com.dsf.radiology.model.RadiologyFieldRole;
import com.dsf.radiology.model.RadiologyModality;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RadiologyCatalogService {

    public static final String CALCULATION_VERSION = "2026.4-WS";

    public record FieldDefinition(
            String code, String label, RadiologyFieldRole role, String unit, boolean required, int sortNo) {
    }

    public record ItemDefinition(
            RadiologyModality modality,
            String code,
            String name,
            String standardCode,
            int sortNo,
            List<FieldDefinition> fields) {
    }

    private final Map<RadiologyModality, List<ItemDefinition>> catalog;
    private final Map<RadiologyModality, Map<String, String>> importIndex;

    public RadiologyCatalogService() {
        this.catalog = Map.of(
                RadiologyModality.DR, Collections.unmodifiableList(buildDr()),
                RadiologyModality.CT, Collections.unmodifiableList(buildCt()));
        this.importIndex = Map.of(
                RadiologyModality.DR, buildImportIndex(catalog.get(RadiologyModality.DR)),
                RadiologyModality.CT, buildImportIndex(catalog.get(RadiologyModality.CT)));
    }

    public List<ItemDefinition> getCatalog(RadiologyModality modality) {
        return catalog.get(modality);
    }

    public Optional<ItemDefinition> findItem(RadiologyModality modality, String itemCode) {
        return catalog.get(modality).stream().filter(item -> item.code().equalsIgnoreCase(itemCode)).findFirst();
    }

    public Optional<FieldDefinition> findField(RadiologyModality modality, String itemCode, String fieldCode) {
        return findItem(modality, itemCode).flatMap(item -> item.fields().stream()
                .filter(field -> field.code().equalsIgnoreCase(fieldCode)).findFirst());
    }

    public Optional<String> itemCodeForImportField(RadiologyModality modality, String fieldCode) {
        return Optional.ofNullable(importIndex.get(modality).get(normalize(fieldCode)));
    }

    private List<ItemDefinition> buildDr() {
        List<ItemDefinition> items = new ArrayList<>();
        items.add(item(RadiologyModality.DR, "TVD", "管电压指示的偏离", "WS 76—2020", 10,
                "TVD1-0", "TVD1-1", "TVD1-2", "TVD1-3", "TVD2-0", "TVD2-1", "TVD2-2", "TVD2-3",
                "TVD3-0", "TVD3-1", "TVD3-2", "TVD3-3", "TVD-K1", "TVD-K2", "TVD-K3"));
        items.add(item(RadiologyModality.DR, "ROA", "辐射输出量重复性", "WS 76—2020", 20,
                "ROA1", "ROA2", "ROA3", "ROA4", "ROA5", "ROA-K"));
        items.add(item(RadiologyModality.DR, "ROQ", "输出量线性", "WS 76—2020", 30,
                "I1", "T1", "ROQ1-1", "ROQ1-2", "ROQ1-3", "I2", "T2", "ROQ2-1", "ROQ2-2", "ROQ2-3"));
        items.add(item(RadiologyModality.DR, "HVL", "有用线束半值层", "WS 76—2020", 40, "HVL1", "HVL2", "HVL3"));
        items.add(item(RadiologyModality.DR, "ETD", "曝光时间指示的偏离", "WS 76—2020", 50,
                "ETD1-0", "ETD1-1", "ETD1-2", "ETD1-3", "ETD2-0", "ETD2-1", "ETD2-2", "ETD2-3",
                "ETD3-0", "ETD3-1", "ETD3-2", "ETD3-3", "ETD-K1", "ETD-K2", "ETD-K3"));
        items.add(item(RadiologyModality.DR, "ARP", "AEC重复性", "WS 76—2020", 60, "ARP1", "ARP2", "ARP3", "ARP4", "ARP5"));
        items.add(item(RadiologyModality.DR, "ARS", "AEC响应", "WS 76—2020", 70,
                "ARS1-1", "ARS1-2", "ARS1-3", "ARS1-4", "ARS1-5", "ARS2-1", "ARS2-2", "ARS2-3", "ARS2-4", "ARS2-5"));
        items.add(item(RadiologyModality.DR, "AIC", "AEC电离室一致性", "WS 76—2020", 80,
                "AIC-C1", "AIC-C2", "AIC-C3", "AIC-L1", "AIC-L2", "AIC-L3", "AIC-R1", "AIC-R2", "AIC-R3"));
        items.add(item(RadiologyModality.DR, "UBD", "有用线束垂直度偏离", "WS 76—2020", 90, "UBD"));
        items.add(item(RadiologyModality.DR, "LRD", "光野与照射野四边的偏离", "WS 76—2020", 100,
                "LRD-a1-0", "LRD-a1-1", "LRD-a2-0", "LRD-a2-1", "LRD-b1-0", "LRD-b1-1", "LRD-b2-0", "LRD-b2-1"));
        items.add(item(RadiologyModality.DR, "DDI", "探测器剂量指示", "WS 76—2020", 110,
                "DDI1", "DDI2", "DDI3", "DDI-BASELINE"));
        items.add(item(RadiologyModality.DR, "STP", "信号传递特性", "WS 76—2020", 120,
                "STP-K1", "STP-K2", "STP-K3", "STP-K4", "STP-K5", "STP-R1", "STP-R2", "STP-R3", "STP-R4", "STP-R5", "STP-MODEL"));
        items.add(item(RadiologyModality.DR, "RUE", "响应均匀性", "WS 76—2020", 130, "RUE1", "RUE2", "RUE3", "RUE4", "RUE5"));
        items.add(item(RadiologyModality.DR, "REA", "测距误差", "WS 76—2020", 140, "REA-at", "REA-am", "REA-bt", "REA-bm"));
        items.add(item(RadiologyModality.DR, "ILA", "残影", "WS 76—2020", 150,
                "是否有残影（如“否”无需填后两项）", "残影区D1", "非残影区D0"));
        items.add(item(RadiologyModality.DR, "ART", "伪影", "WS 76—2020", 160, "填“是”或“否”"));
        items.add(item(RadiologyModality.DR, "HCR", "高对比度分辨力", "WS 76—2020", 170, "HCR1", "厂家规定值/基线值"));
        items.add(item(RadiologyModality.DR, "LCR", "低对比度分辨力", "WS 76—2020", 180,
                "LCR1（可观察细节数）", "LCR2（可观察对比度）", "厂家规定值/基线值"));
        items.add(item(RadiologyModality.DR, "RPD", "场所卫生防护", "GBZ 130—2020", 190,
                "k", "a", "RPD-mA", "RPD0-1", "RPD0-2", "RPD0-3", "RPD0-4", "RPD0-5", "RPD0-6", "RPD0-7", "RPD0-8", "RPD0-9", "RPD0-10"));
        return items;
    }

    private List<ItemDefinition> buildCt() {
        List<ItemDefinition> items = new ArrayList<>();
        items.add(item(RadiologyModality.CT, "TPA", "诊断床定位精度", "WS 519—2019", 10,
                "Dfor", "Lfor", "Cfor", "Dback", "Lback", "Cback"));
        items.add(item(RadiologyModality.CT, "GTA", "扫描架倾角精度", "WS 519—2019", 20,
                "扫描倾角显示值α1", "模体上下边沿间距L1", "倾斜α1角后模体上下边沿间距L2", "测量值α2"));
        items.add(item(RadiologyModality.CT, "CTDI", "CT剂量指数", "WS 519—2019", 30,
                "层数n1", "层厚T1", "刻度系数k1", "HCD1", "HCD2", "HCD3", "HUD1", "HUD2", "HUD3",
                "HDD1", "HDD2", "HDD3", "HLD1", "HLD2", "HLD3", "HRD1", "HRD2", "HRD3",
                "层数n2", "层厚T2", "刻度系数k2", "SCD1", "SCD2", "SCD3", "SUD1", "SUD2", "SUD3",
                "SDD1", "SDD2", "SDD3", "SLD1", "SLD2", "SLD3", "SRD1", "SRD2", "SRD3",
                "CTDI-HEAD-MANUFACTURER", "CTDI-BODY-MANUFACTURER", "CTDI-HEAD-BASELINE", "CTDI-BODY-BASELINE"));
        items.add(item(RadiologyModality.CT, "LBA", "定位光精度", "WS 519—2019", 40, "LBA", "LBA-FACTOR"));
        items.add(item(RadiologyModality.CT, "RST", "重建层厚偏差", "WS 519—2019", 50,
                "CTbackground", "CTmax", "WWmin", "CThm", "R0", "R1", "R2", "R3", "R4", "RST-FACTOR"));
        items.add(item(RadiologyModality.CT, "MTF", "高对比分辨力", "WS 519—2019", 60, "MTF", "MTF-FACTOR"));
        items.add(item(RadiologyModality.CT, "LCD", "低对比可探测能力", "WS 519—2019", 70,
                "NUM1", "mm1", "NUM0.5", "mm0.5", "NUM0.3", "mm0.3"));
        items.add(item(RadiologyModality.CT, "HUL", "CT值线性", "WS 519—2019", 80,
                "Air-HU1", "Air-HU2", "Air-HU3", "LDPE-HU1", "LDPE-HU2", "LDPE-HU3",
                "Acylic-HU1", "Acylic-HU2", "Acylic-HU3", "Teflon-HU1", "Teflon-HU2", "Teflon-HU3",
                "Air-REF", "LDPE-REF", "Acylic-REF", "Teflon-REF"));
        items.add(item(RadiologyModality.CT, "HUA", "CT值（水）", "WS 519—2019", 90, "HUA1", "HUA2", "HUA3"));
        items.add(item(RadiologyModality.CT, "HUU", "CT值均匀性", "WS 519—2019", 100,
                "HUU-U1", "HUU-U2", "HUU-U3", "HUU-D1", "HUU-D2", "HUU-D3",
                "HUU-L1", "HUU-L2", "HUU-L3", "HUU-R1", "HUU-R2", "HUU-R3"));
        items.add(item(RadiologyModality.CT, "NPS", "噪声", "WS 519—2019", 110, "NPS1", "NPS2", "NPS3", "NPS-n"));
        items.add(item(RadiologyModality.CT, "RPD", "场所卫生防护", "GBZ 130—2020", 120,
                "k", "a", "RPD0-1", "RPD0-2", "RPD0-3", "RPD0-4", "RPD0-5", "RPD0-6", "RPD0-7", "RPD0-8", "RPD0-9", "RPD0-10"));
        return items;
    }

    private ItemDefinition item(
            RadiologyModality modality, String code, String name, String standard, int sortNo, String... fieldCodes) {
        List<FieldDefinition> fields = new ArrayList<>();
        for (int i = 0; i < fieldCodes.length; i++) {
            String fieldCode = fieldCodes[i];
            boolean optional = fieldCode.contains("-K") || fieldCode.equals("DDI-BASELINE")
                    || fieldCode.equals("k") || fieldCode.equals("a") || fieldCode.equals("NPS-n")
                    || fieldCode.equals("CThm") || fieldCode.equals("HUU-L3")
                    || fieldCode.endsWith("-FACTOR") || fieldCode.endsWith("-REF") || fieldCode.equals("RPD-mA")
                    || fieldCode.equals("STP-MODEL") || fieldCode.startsWith("CTDI-");
            fields.add(new FieldDefinition(fieldCode, label(fieldCode), RadiologyFieldRole.RAW, unit(fieldCode), !optional, i + 1));
        }
        return new ItemDefinition(modality, code, name, standard, sortNo, Collections.unmodifiableList(fields));
    }

    private Map<String, String> buildImportIndex(List<ItemDefinition> items) {
        Map<String, String> index = new LinkedHashMap<>();
        for (ItemDefinition item : items) {
            for (FieldDefinition field : item.fields()) {
                index.put(normalize(field.code()), item.code());
            }
        }
        if (items.stream().anyMatch(item -> item.modality() == RadiologyModality.DR)) {
            index.put("HCR-BASELINE", "HCR");
            index.put("LCR-BASELINE", "LCR");
        }
        return Collections.unmodifiableMap(index);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace("\u3000", " ").toUpperCase();
    }

    private String label(String code) {
        return switch (code) {
            case "k" -> "仪器修正系数 k";
            case "a" -> "单位转换常数 a";
            case "NPS-n" -> "扫描层厚 n";
            case "DDI-BASELINE" -> "厂家规定值/基线值";
            case "RPD-mA" -> "DR 防护检测管电流（默认 100 mA）";
            case "STP-MODEL" -> "信号传递模型（LINEAR/LOG/POWER/BEST_FIT）";
            case "CTDI-HEAD-MANUFACTURER" -> "厂家头模 CTDIw 规定值";
            case "CTDI-BODY-MANUFACTURER" -> "厂家体模 CTDIw 规定值";
            case "CTDI-HEAD-BASELINE" -> "头模 CTDIw 稳定性基线";
            case "CTDI-BODY-BASELINE" -> "体模 CTDIw 稳定性基线";
            case "LBA-FACTOR" -> "定位光换算系数（直接录入 mm 时填 1）";
            case "RST-FACTOR" -> "模体层厚换算系数（暂按 Excel 默认 0.42）";
            case "MTF-FACTOR" -> "分辨力换算系数（直接录入 lp/cm 时填 1）";
            case "Air-REF", "LDPE-REF", "Acylic-REF", "Teflon-REF" -> code + " 模体标称 CT 值";
            case "TVD-K1", "TVD-K2", "TVD-K3", "ETD-K1", "ETD-K2", "ETD-K3", "ROA-K" -> "校准因子 " + code;
            default -> code;
        };
    }

    private String unit(String code) {
        if (code.startsWith("RPD0-")) return "nSv/h";
        if (code.equals("a") || code.equals("k") || code.endsWith("-FACTOR")) return "";
        if (code.equals("RPD-mA")) return "mA";
        if (code.startsWith("CTDI-")) return "mGy";
        if (code.endsWith("-REF")) return "HU";
        if (code.startsWith("TVD")) return "kV";
        if (code.startsWith("ETD") || code.equals("T1") || code.equals("T2")) return "ms";
        if (code.startsWith("HVL")) return "mmAl";
        if (code.startsWith("REA") || code.startsWith("LRD") || code.equals("LBA")) return "mm";
        if (code.contains("HU") || code.startsWith("CT")) return "HU";
        return "";
    }
}
