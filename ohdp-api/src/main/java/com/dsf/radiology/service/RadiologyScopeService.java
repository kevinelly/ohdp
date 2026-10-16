package com.dsf.radiology.service;

import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.model.RadiologyProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RadiologyScopeService {
    public static final List<String> CONDITIONAL=List.of("ARP","ARS","AIC","DDI","STP","RUE","ART");
    public record ItemScope(String itemCode,String group,boolean selected,boolean applicable,boolean pending,String reason) {
        public boolean isModelDependent(){return CONDITIONAL.contains(itemCode);}
    }
    public static String group(RadiologyModality modality,String code) {
        return code.equals("RPD")?"PROTECTION":modality==RadiologyModality.CT?"QC":List.of("DDI","STP","RUE","REA","ILA","ART","HCR","LCR").contains(code)?"SPECIAL":"GENERAL";
    }
    public ItemScope decision(RadiologyCase inspection,RadiologyProfile profile,String code,Map<String,BigDecimal> raw) {
        String group=group(inspection.getModality(),code);
        if(profile.getSelectedGroups()!=null && !profile.getSelectedGroups().contains(group))return new ItemScope(code,group,false,false,false,"本次委托检测范围未包含此项目组");
        String reason=exclusion(inspection,code,raw,profile);
        if(!reason.isEmpty())return new ItemScope(code,group,true,false,false,reason);
        if(Boolean.FALSE.equals(profile.getScopeConfirmed()))return new ItemScope(code,group,true,false,true,"请先确认本次检测范围及设备适用条件");
        if(inspection.getModality()==RadiologyModality.DR && CONDITIONAL.contains(code) && profile.getItemApplicability()!=null) {
            Boolean applicable=profile.getItemApplicability().get(code);
            if(applicable==null)return new ItemScope(code,group,true,false,true,"依据设备实际机型，待确认是否开展此项");
            if(!applicable) {
                String note=profile.getItemExclusionReasons()==null?null:profile.getItemExclusionReasons().get(code);
                if(note==null || note.isBlank())return new ItemScope(code,group,true,false,true,"请填写本项目不开展的机型依据或原因");
                return new ItemScope(code,group,true,false,false,"依据设备实际机型不开展："+note.trim());
            }
        }
        return new ItemScope(code,group,true,true,false,"");
    }
    public static String type(String category) {
        String text=category==null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (text.contains("稳定") || text.contains("STABILITY")) return "STABILITY";
        if (text.contains("状态") || text.contains("STATE")) return "STATE";
        return "ACCEPTANCE";
    }

    /** A non-empty reason means the standard or declared equipment capability excludes this item. */
    public String exclusion(RadiologyCase inspection, String code, Map<String, BigDecimal> raw) {
        return exclusion(inspection,code,raw,null);
    }
    private String exclusion(RadiologyCase inspection,String code,Map<String,BigDecimal> raw,RadiologyProfile profile) {
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
            if ((profile==null || profile.getItemApplicability()==null) && Boolean.FALSE.equals(inspection.getHasRawImages()) && List.of("DDI","STP","RUE","ART").contains(code))
                return "设备不能提供未处理图像："+inspection.getFeatureReason();
        }
        return "";
    }
}

