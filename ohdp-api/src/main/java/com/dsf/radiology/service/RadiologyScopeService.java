package com.dsf.radiology.service;

import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyModality;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RadiologyScopeService {
    public static String type(String category) {
        String text=category==null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (text.contains("稳定") || text.contains("STABILITY")) return "STABILITY";
        if (text.contains("状态") || text.contains("STATE")) return "STATE";
        return "ACCEPTANCE";
    }

    /** A non-empty reason means the standard or declared equipment capability excludes this item. */
    public String exclusion(RadiologyCase inspection, String code, Map<String, BigDecimal> raw) {
        String type=type(inspection.getDetectionCategory());
        if (inspection.getModality()==RadiologyModality.CT) {
            if (!type.equals("ACCEPTANCE") && List.of("GTA","HUL").contains(code))
                return "WS 519—2019表A.1："+inspection.getDetectionCategory()+"不检测此项";
            if (type.equals("STABILITY") && List.of("LBA","MTF","LCD").contains(code))
                return "WS 519—2019表A.1：稳定性检测不检测此项";
            if (code.equals("RST") && !type.equals("ACCEPTANCE") && raw.get("R0")!=null
                    && raw.get("R0").compareTo(new BigDecimal("2"))<=0)
                return "WS 519—2019表A.1：本检测类别不检测标称层厚≤2 mm的此项";
        } else {
            if (type.equals("STATE") && List.of("ROQ","ETD","ILA").contains(code))
                return "WS 76—2020附录C：状态检测不检测此项";
            if (type.equals("STABILITY") && List.of("TVD","ROQ","HVL","ARP","AIC","DDI","REA","HCR","LCR").contains(code))
                return "WS 76—2020附录C：稳定性检测不检测此项";
            if (Boolean.FALSE.equals(inspection.getHasAec()) && List.of("ARP","ARS","AIC").contains(code))
                return "设备不具备AEC功能："+inspection.getFeatureReason();
            if (Boolean.FALSE.equals(inspection.getHasRawImages()) && List.of("DDI","STP","RUE","ART").contains(code))
                return "设备不能提供未处理图像："+inspection.getFeatureReason();
        }
        return "";
    }
}

