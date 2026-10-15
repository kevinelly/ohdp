package com.dsf.dose;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.dsf.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class PersonalDoseTest {
    @Autowired PersonalDoseService service;
    @Autowired PersonalDoseCalculator calculator;
    @Autowired PersonalDoseImportService imports;
    @Autowired PersonalDoseRepository repository;
    @Autowired ObjectMapper mapper;
    @Autowired com.dsf.radiology.controller.InspectionQueryController query;
    @Autowired com.dsf.radiology.repository.RadiologyCaseRepository radiology;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PersonalDoseQueryMigration migration;
    ObjectNode sample() throws Exception{return (ObjectNode)mapper.readTree("""
      {"receipt":"DOSE-TEST","unitName":"软件测试虚构单位","detectionCategory":"委托检测","sampleName":"个人剂量计","monitoringItem":"X、γ射线个人剂量当量","standardBasis":"GBZ 128—2019 职业性外照射个人监测规范","detector":"测试编制人","inspectionDate":"2026-10-14","monitoringStart":"2026-07-01","monitoringEnd":"2026-09-30","cycle":"3","mdl":"0.01","investigationLevel":"1.00","instrumentDescription":"虚构仪器及校准证书，非真实检测","conclusion":"软件测试虚构数据，仅用于验证本周期计算。","personnel":[{"dosimeterNo":"T001","name":"虚构人员甲","sex":"男","occupationCategory":"医学应用","startDate":"2026-07-01","endDate":"2026-09-30"}],"readings":[{"sampleType":"BACKGROUND","dosimeterNo":"B001","name":"","values":["1.00","2.00","3.00"],"cf":""},{"sampleType":"QC","dosimeterNo":"Q001","name":"","values":["100.0"],"cf":""},{"sampleType":"TEST","dosimeterNo":"T001","name":"虚构人员甲","values":["10.0","12.0","14.0"],"cf":"0.10"}]}
      """);}
    ObjectNode action(ObjectNode d,String a){return service.action(d.path("id").asLong(),a,d);}
    @Test void reportHeaderCommentsAndVersionedArchiveNamesFollowTemplate() throws Exception {
        var d=service.save(null,sample());long id=d.path("id").asLong();d=action(d,"calculate");
        for(String a:java.util.List.of("submit","review","approve","archive")){d.put("comment",a+"核对意见");d=action(d,a);}
        assertThat(d.path("history").toString()).contains("submit核对意见","review核对意见","approve核对意见");
        byte[] old=service.report(id,false);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(old))){assertThat(doc.getHeaderList().stream().map(h->h.getText()).reduce("",String::concat)).contains("BHCDC/QBG24-206-3").doesNotContain("BHCDC/QBG24-206-5");}
        var name=service.reportName(id);assertThat(name).contains("DOSE-TEST","个人剂量检测（第3周期）","软件测试虚构单位").endsWith(".docx");
        var revised=service.revision(id,d);for(String a:java.util.List.of("calculate","submit","review","approve","archive"))revised=action(revised,a);
        var list=service.reports(revised.path("id").asLong());assertThat(list).hasSize(2);assertThat(list.stream().map(v->v.get("fileName")).distinct().count()).isEqualTo(2);assertThat(service.report(id,false)).isEqualTo(old);assertThat(service.reportName(id)).isEqualTo(name);
    }
    @Test void expandedExcelRoundTripsAllDocumentFieldsAndAcceptsLegacyTemplate() throws Exception {
        java.util.Map<String,String> extra=java.util.Map.of("送检单位","虚构送检","检测地点","虚构地点","所在地市","北海市","所在县区","海城区","样品性状","正常","剂量计发放开始日期","2026-06-01","剂量计发放结束日期","2026-06-02","收检日期","2026-10-01","探测器类型","TLD","报告日期","2026-10-15");
        byte[] bytes;
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(imports.template("cases")));var out=new ByteArrayOutputStream()){
            var s=w.getSheetAt(0);var row=s.createRow(1);for(var cell:s.getRow(0)){String label=cell.getStringCellValue();String value=switch(label){case "受理编号"->"FULL-EXCEL";case "受检单位"->"虚构单位";case "检测类别"->"委托检测";case "样品名称"->"个人剂量计";default->extra.getOrDefault(label,"");};row.createCell(cell.getColumnIndex()).setCellValue(value);}w.write(out);bytes=out.toByteArray();
        }
        var file=new MockMultipartFile("file","full.xlsx","application/octet-stream",bytes);long id=imports.importFile("cases",null,null,file,false).path("ids").get(0).asLong();var d=service.get(id);
        assertThat(d.path("submittingUnit").asText()).isEqualTo("虚构送检");assertThat(d.path("distributionEnd").asText()).isEqualTo("2026-06-02");assertThat(d.path("detectorType").asText()).isEqualTo("TLD");assertThat(d.path("city").asText()).isEqualTo("北海市");
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes));var out=new ByteArrayOutputStream()){for(var row:w.getSheetAt(0))for(int i=30;i>=20;i--)if(row.getCell(i)!=null)row.removeCell(row.getCell(i));w.write(out);bytes=out.toByteArray();}
        assertThat(imports.importFile("cases",null,null,new MockMultipartFile("file","legacy.xlsx","application/octet-stream",bytes),true).path("count").asInt()).isEqualTo(1);
    }
    @Test void unifiedQueryFiltersDoseAndRadiologyAndPaginatesWithoutDuplicates() throws Exception {
        String marker="QUERY-"+java.util.UUID.randomUUID();
        for(int i=0;i<3;i++){var d=sample();d.put("receipt",marker+"-"+i);d.put("city","测试地市");d.put("county","测试县区");service.save(null,d);}
        var machine=new com.dsf.radiology.entity.RadiologyCase();machine.setCaseCode(marker);machine.setSampleAcceptanceNo(marker);machine.setInspectedUnitName("虚构CT单位");machine.setEquipmentName("CT");machine.setModality(com.dsf.radiology.model.RadiologyModality.CT);machine.setInspectionDate(java.time.LocalDate.of(2026,10,14));machine.setCity("测试地市");machine.setCounty("测试县区");radiology.saveAndFlush(machine);
        var first=query.query(null,null,2026,null,null,"测试地市","测试县区",marker,null,0,2);var second=query.query(null,null,2026,null,null,"测试地市","测试县区",marker,null,1,2);
        assertThat(first.getTotalElements()).isEqualTo(4);assertThat(first.getContent()).hasSize(2);assertThat(second.getContent()).hasSize(2);
        var all=new java.util.ArrayList<>(first.getContent());all.addAll(second.getContent());assertThat(all.stream().map(v->v.modality()+v.id()).distinct().count()).isEqualTo(4);
        assertThat(query.query(java.util.List.of("DOSE"),null,2026,null,null,null,null,marker,null,0,20).getTotalElements()).isEqualTo(3);
        assertThat(query.query(null,null,2025,null,null,null,null,marker,null,0,20).getTotalElements()).isZero();
        assertThat(query.query(null,null,2026,java.time.LocalDate.of(2027,1,1),null,null,null,marker,null,0,20).getTotalElements()).isZero();
    }
    @Test void metadataBackfillPreservesApprovedBytesAndVersions() throws Exception {
        var d=service.save(null,sample());for(String a:java.util.List.of("calculate","submit","review","approve","archive"))d=action(d,a);long id=d.path("id").asLong();byte[] original=service.report(id,false);
        jdbc.update("update personal_dose_case set query_indexed=null,inspection_date=null,generated_at=null,generated_by=null where id=?",id);
        migration.run(null);assertThat(service.get(id).path("version").asLong()).isEqualTo(d.path("version").asLong());assertThat(service.report(id,false)).isEqualTo(original);
        assertThat(repository.findById(id).orElseThrow().getInspectionDate()).isEqualTo(java.time.LocalDate.of(2026,10,14));assertThat(service.reports(id).get(0).get("generatedAt")).isNotNull();
    }
    @Test void calculationSubtractsBackgroundAndHonorsPrecisionAndThreshold() throws Exception {
        var d=sample();var c=calculator.calculate(d);var r=c.path("rows").get(2);assertThat(c.path("backgroundMean").asText()).isEqualTo("2");assertThat(r.path("mean").asText()).isEqualTo("12.0");assertThat(r.path("net").asText()).isEqualTo("10.0");assertThat(r.path("hp10").asText()).isEqualTo("1.00");assertThat(r.path("investigationRequired").asBoolean()).isTrue();assertThat(d.path("personnel").get(0).path("wearingDays").asInt()).isEqualTo(92);
    }
    @Test void identityMismatchAndDuplicateAreRejectedAtSave() throws Exception {
        var d=sample();((ObjectNode)d.path("readings").get(2)).put("name","错名");var mismatched=d;assertThatThrownBy(()->service.save(null,mismatched)).hasMessageContaining("不一致");
        d=sample();((ArrayNode)d.get("personnel")).add(d.path("personnel").get(0).deepCopy());var duplicate=d;assertThatThrownBy(()->calculator.validate(duplicate,false)).hasMessageContaining("重复");
    }
    @Test void missingBackgroundMissingSampleAndInvalidDatesFail() throws Exception {
        var d=sample();((ArrayNode)d.get("readings")).remove(0);assertThatThrownBy(()->calculator.calculate(d)).hasMessageContaining("本底");
        var noTest=sample();((ArrayNode)noTest.get("readings")).remove(2);assertThatThrownBy(()->calculator.calculate(noTest)).hasMessageContaining("缺少待测");
        var dates=sample();((ObjectNode)dates.path("personnel").get(0)).put("endDate","2026-06-30");assertThatThrownBy(()->calculator.calculate(dates)).hasMessageContaining("不能早于");
    }
    @Test void negativeNetIsNotSilentlyClampedAndMdlIsFlagged() throws Exception {var d=sample();((ObjectNode)d.path("readings").get(2)).putArray("values").add("0.10");var row=calculator.calculate(d).path("rows").get(2);assertThat(row.path("hp10").asText()).isEqualTo("-0.19");assertThat(row.path("belowMdl").asBoolean()).isTrue();}
    @Test void investigationFlagMatchesTheTwoDecimalReportValueAtRoundingBoundary() throws Exception {var d=sample();((ObjectNode)d.path("readings").get(2)).putArray("values").add("11.99");var row=calculator.calculate(d).path("rows").get(2);assertThat(row.path("hp10").asText()).isEqualTo("1.00");assertThat(row.path("investigationRequired").asBoolean()).isTrue();}
    @Test void workflowLocksDataArchivesExactBytesAndRevisionPreservesOriginal() throws Exception {
        var d=service.save(null,sample());long id=d.path("id").asLong();var stale=d.deepCopy();d=action(d,"calculate");var latest=d;
        assertThatThrownBy(()->service.save(id,stale)).hasMessageContaining("其他人员");
        d=action(d,"submit");var submitted=d;assertThatThrownBy(()->service.save(id,submitted)).hasMessageContaining("不能修改");
        d=action(d,"review");d=action(d,"approve");d=action(d,"archive");assertThat(d.path("status").asText()).isEqualTo("REPORTED");assertThat(d.path("reportSha256").asText()).hasSize(64);
        byte[] first=service.report(id,false);assertThat(service.report(id,false)).isEqualTo(first);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(first))){String all=doc.getParagraphs().toString()+doc.getTables().stream().map(t->t.getText()).reduce("",String::concat);assertThat(all).contains("虚构人员甲","1.00","Hp(10)","编制人");assertThat(doc.getParagraphs().stream().map(p->p.getText()).reduce("",String::concat)).contains("最低可探测水平","调查水平").doesNotContain("结果均低于限值");}
        var revised=service.revision(id,d);assertThat(revised.path("sourceCaseId").asLong()).isEqualTo(id);assertThat(revised.has("calculation")).isFalse();assertThat(service.report(id,false)).isEqualTo(first);
    }
    @Test void editingInvalidatesCalculationAndReturnRequiresReason() throws Exception {var d=service.save(null,sample());d=action(d,"calculate");d.put("mdl","0.02");d=service.save(d.path("id").asLong(),d);assertThat(d.has("calculation")).isFalse();var invalid=d;assertThatThrownBy(()->action(invalid,"submit")).hasMessageContaining("重新计算");d=action(d,"calculate");d=action(d,"submit");var submitted=d;assertThatThrownBy(()->action(submitted,"return")).hasMessageContaining("退回原因");d.put("comment","补充原始记录");assertThat(action(d,"return").path("status").asText()).isEqualTo("DATA_ENTERED");}
    @Test void reviewerPermissionIsEnforcedOnServer() throws Exception {
        var d=action(action(service.save(null,sample()),"calculate"),"submit");
        com.dsf.auth.security.AuthContext.set(com.dsf.auth.security.AuthenticatedUser.builder().userId(-999L).username("无复核权限测试用户").role(com.dsf.auth.entity.UserRole.USER).build());
        try{assertThatThrownBy(()->action(d,"review")).hasMessageContaining("无权限");}
        finally{com.dsf.auth.security.AuthContext.clear();}
    }
    @Test void batchImportValidatesAllRowsBeforeSaving() throws Exception {
        long before=repository.count();byte[] bytes;
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(imports.template("cases")));var out=new ByteArrayOutputStream()){var s=w.getSheetAt(0);for(int n=1;n<=2;n++){var r=s.createRow(n);r.createCell(0).setCellValue("BATCH-"+n);r.createCell(1).setCellValue(n==1?"虚构单位":"");r.createCell(2).setCellValue("委托检测");}w.write(out);bytes=out.toByteArray();}
        var file=new MockMultipartFile("file","cases.xlsx","application/octet-stream",bytes);assertThatThrownBy(()->imports.importFile("cases",null,null,file,false)).isInstanceOf(BusinessException.class);assertThat(repository.count()).isEqualTo(before);
    }
    @Test void importCannotReplacePersonWithMismatchedName() throws Exception {
        var d=service.save(null,sample());byte[] bytes;try(var w=new XSSFWorkbook(new ByteArrayInputStream(imports.template("personnel")));var out=new ByteArrayOutputStream()){var r=w.getSheetAt(0).createRow(1);r.createCell(0).setCellValue("T001");r.createCell(1).setCellValue("错误姓名");w.write(out);bytes=out.toByteArray();}
        assertThatThrownBy(()->imports.importFile("personnel",d.path("id").asLong(),d.path("version").asLong(),new MockMultipartFile("file","people.xlsx","application/octet-stream",bytes),false)).hasMessageContaining("不一致");assertThat(service.get(d.path("id").asLong()).path("personnel").get(0).path("name").asText()).isEqualTo("虚构人员甲");
    }
    @Test void readingExcelPreviewIsReadOnlyAndImportPreservesTrailingZeroes() throws Exception {
        var d=service.save(null,sample());byte[] bytes;
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(imports.template("readings")));var out=new ByteArrayOutputStream()){
            String[][] rows={{"本底剂量计","B001","","1.00;2.00;3.00",""},{"待测剂量计","T001","虚构人员甲","10.0;12.0;14.0","0.10"}};
            for(int i=0;i<rows.length;i++){var r=w.getSheetAt(0).createRow(i+1);for(int j=0;j<rows[i].length;j++)r.createCell(j).setCellValue(rows[i][j]);}w.write(out);bytes=out.toByteArray();
        }
        var file=new MockMultipartFile("file","readings.xlsx","application/octet-stream",bytes);Long id=d.path("id").asLong(),version=d.path("version").asLong();
        assertThat(imports.importFile("readings",id,version,file,true).path("count").asInt()).isEqualTo(2);assertThat(service.get(id).path("version").asLong()).isEqualTo(version);
        var saved=(ObjectNode)imports.importFile("readings",id,version,file,false).get("project");assertThat(saved.path("readings").get(1).path("values").get(0).asText()).isEqualTo("10.0");assertThat(action(saved,"calculate").path("calculation").path("rows").get(1).path("hp10").asText()).isEqualTo("1.00");
    }
}
