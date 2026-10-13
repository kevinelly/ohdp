package com.dsf.radiology;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.RadiologyModality;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

final class RadiologyTestFixture {
    static RadiologyDto.CaseRequest request() {
        var r=new RadiologyDto.CaseRequest();
        String suffix=UUID.randomUUID().toString().substring(0,8);
        r.setCaseCode("CT-COMPARE-"+suffix);r.setSampleAcceptanceNo("TEST-"+suffix);
        r.setModality(RadiologyModality.CT);r.setInspectedUnitName("软件验收虚构医院");r.setEquipmentName("普通诊断CT");
        r.setEquipmentNo("TEST-CT-001");r.setEquipmentModel("测试型号");r.setDetectionCategory("状态检测");
        r.setAgencyName("软件验收测试机构");r.setInspectionDate(LocalDate.of(2026,10,13));
        r.setInstrumentDescription("软件验收仪器，不用于真实检测");return r;
    }
    static RadiologyDto.MeasurementRequest value(String item,String field,String number) {
        var v=new RadiologyDto.MeasurementRequest();v.setItemCode(item);v.setFieldCode(field);v.setValueNum(new BigDecimal(number));return v;
    }
    static RadiologyDto.MeasurementBatchRequest measurements() {
        var values=new ArrayList<RadiologyDto.MeasurementRequest>();
        values.addAll(List.of(value("TPA","Dfor","0"),value("TPA","Lfor","5"),value("TPA","Cfor","5"),
            value("TPA","Dback","0"),value("TPA","Lback","5"),value("TPA","Cback","5")));
        values.addAll(List.of(value("CTDI","层数n1","1"),value("CTDI","层厚T1","10"),value("CTDI","刻度系数k1","1")));
        for(String p:List.of("HC","HU","HD","HL","HR")) for(int i=1;i<=3;i++) values.add(value("CTDI",p+"D"+i,"10"));
        values.add(value("LBA","LBA","1"));
        values.addAll(List.of(value("RST","CTbackground","0"),value("RST","CTmax","100"),value("RST","R0","3"),value("RST","RST-FACTOR","1")));
        for(int i=1;i<=4;i++) values.add(value("RST","R"+i,"3"));
        values.add(value("MTF","MTF","6"));
        for(String pair:List.of("1","0.5","0.3")) {
            values.add(value("LCD","NUM"+pair,"1"));values.add(value("LCD","mm"+pair,"2"));
        }
        for(int i=1;i<=3;i++) {
            values.add(value("HUA","HUA"+i,"0"));values.add(value("NPS","NPS"+i,"3"));
            for(String d:List.of("U","D","L","R")) values.add(value("HUU","HUU-"+d+i,"0"));
        }
        for(int i=1;i<=10;i++) values.add(value("RPD","RPD0-"+i,"100"));
        var batch=new RadiologyDto.MeasurementBatchRequest();batch.setValues(values);
        var conditions=new HashMap<String,String>();
        for(String c:List.of("TPA","CTDI","LBA","RST","MTF","LCD","HUA","HUU","NPS"))
            conditions.put(c,"软件验收条件；头部常规扫描120 kV；层厚10 mm；对应模体与方法条件为测试数据");
        batch.setConditions(conditions);return batch;
    }
    static RadiologyDto.ProtectionPointBatchRequest points() {
        var p=new RadiologyDto.ProtectionPointRequest();p.setPointCode("P1");p.setPointName("测试控制室操作位");
        p.setReadings(Collections.nCopies(5,new BigDecimal("1200")));p.setSortNo(1);
        var batch=new RadiologyDto.ProtectionPointBatchRequest();batch.setPoints(List.of(p));return batch;
    }
    static RadiologyDto.MeasurementBatchRequest drMeasurements() {
        var values=new ArrayList<RadiologyDto.MeasurementRequest>();
        for(int g=1;g<=3;g++) for(int i=0;i<=3;i++) values.add(value("TVD","TVD"+g+"-"+i,"80"));
        for(int i=1;i<=5;i++) values.add(value("ROA","ROA"+i,"10"));
        for(int i=1;i<=3;i++) {values.add(value("HVL","HVL"+i,"3"));values.add(value("DDI","DDI"+i,"100"));}
        values.add(value("DDI","DDI-BASELINE","100"));values.add(value("UBD","UBD","0"));
        for(String s:List.of("a1","a2","b1","b2")) for(int i=0;i<=1;i++) values.add(value("LRD","LRD-"+s+"-"+i,"0"));
        for(String s:List.of("at","am","bt","bm")) values.add(value("REA","REA-"+s,"100"));
        var art=new RadiologyDto.MeasurementRequest();art.setItemCode("ART");art.setFieldCode("填“是”或“否”");art.setValueText("否");values.add(art);
        values.add(value("HCR","HCR1","2.5"));values.add(value("HCR","厂家规定值/基线值","2.5"));
        values.add(value("LCR","LCR1（可观察细节数）","10"));values.add(value("LCR","LCR2（可观察对比度）","0.3"));
        values.add(value("LCR","厂家规定值/基线值","10"));
        for(int i=1;i<=10;i++) values.add(value("RPD","RPD0-"+i,"100"));
        var conditions=new HashMap<String,String>();
        for(String c:List.of("TVD","ROA","HVL","DDI","UBD","LRD","REA","ART","HCR","LCR")) conditions.put(c,"80 kV，SID=1 m；状态检测；虚构软件验收条件");
        var batch=new RadiologyDto.MeasurementBatchRequest();batch.setValues(values);batch.setConditions(conditions);return batch;
    }
    static RadiologyDto.WorkflowRequest workflow() {
        var r=new RadiologyDto.WorkflowRequest();r.setComment("软件验收流程");r.setOperatorName("不应信任的前端姓名");return r;
    }
}

