package com.dsf.radiology;

import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.*;
import com.dsf.radiology.service.*;
import java.time.LocalDate;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest @ActiveProfiles("test")
class RadiologyScopeNavigationTest {
    @Autowired RadiologyCaseService cases;
    @Autowired RadiologyReportService reports;
    RadiologyDto.CaseRequest request(String... groups){
        var r=RadiologyTestFixture.request();r.setModality(RadiologyModality.DR);r.setEquipmentName("虚构DR");r.setInspectionEndDate(LocalDate.of(2026,10,13));
        var p=new RadiologyProfile();p.setSelectedGroups(List.of(groups));p.setScopeConfirmed(true);r.setProfile(p);return r;
    }
    @Test void stateAndNoAecExclusionsAreVisibleBeforeFirstCalculation(){
        var r=request("GENERAL","SPECIAL");r.setHasAec(false);r.setFeatureReason("铭牌与说明书确认无AEC");var c=cases.createCase(r);
        for(String code:List.of("ROQ","ETD","ILA","ARP","ARS","AIC"))assertThat(c.itemScope()).anySatisfy(s->{assertThat(s.itemCode()).isEqualTo(code);assertThat(s.selected()).isTrue();assertThat(s.applicable()).isFalse();assertThat(s.pending()).isFalse();assertThat(s.reason()).isNotBlank();});
        cases.calculate(c.id());assertThat(cases.getCase(c.id()).submissionIssues()).noneMatch(s->List.of("输出量线性","曝光时间指示","残影","AEC").stream().anyMatch(s::contains));
    }
    @Test void onlyProtectionDoesNotRequireQualityAndReportListsExclusionReasons() throws Exception {
        var r=request("PROTECTION");var c=cases.createCase(r);var b=new RadiologyDto.MeasurementBatchRequest();b.setValues(RadiologyTestFixture.drMeasurements().getValues().stream().filter(v->v.getItemCode().equals("RPD")).toList());cases.saveMeasurements(c.id(),b);cases.saveProtectionPoints(c.id(),RadiologyTestFixture.points());
        var result=cases.calculate(c.id());assertThat(result.items()).filteredOn(i->!i.itemCode().equals("RPD")).allMatch(i->i.judgement()==RadiologyJudgement.NOT_APPLICABLE);
        assertThat(cases.getCase(c.id()).submissionIssues()).isEmpty();
        try(var doc=new XWPFDocument(new ByteArrayInputStream(reports.preview(c.id())))){assertThat(doc.getTables().stream().map(t->t.getText()).toList().toString()).contains("本次委托检测范围未包含").doesNotContain("待录入");}
    }
    @Test void previewIsReadOnlyAndUsesSameScopeRules(){
        var r=request("GENERAL","SPECIAL");var c=cases.createCase(r);var before=cases.getCase(c.id());var p=new RadiologyDto.ScopePreviewRequest();p.setProfile(r.getProfile());p.setHasAec(false);p.setFeatureReason("预览机型无AEC");
        var preview=cases.previewScope(c.id(),p);assertThat(preview).anySatisfy(s->{assertThat(s.itemCode()).isEqualTo("ARP");assertThat(s.reason()).contains("预览机型无AEC");});
        var reread=cases.getCase(c.id());assertThat(reread.dataVersion()).isEqualTo(before.dataVersion());assertThat(reread.hasAec()).isTrue();assertThat(reread.updatedAt()).isEqualTo(before.updatedAt());
    }
    @Test void newDraftRequiresExplicitScopeAndModelConfirmation(){
        var r=request();r.getProfile().setScopeConfirmed(false);r.getProfile().setItemApplicability(new HashMap<>());var c=cases.createCase(r);
        assertThatThrownBy(()->cases.calculate(c.id())).hasMessageContaining("确认本次检测范围");
        r.getProfile().setSelectedGroups(List.of("GENERAL"));r.getProfile().setScopeConfirmed(true);
        assertThatThrownBy(()->cases.updateCase(c.id(),r)).hasMessageContaining("待确认");
        r.getProfile().setItemApplicability(Map.of("ARP",false,"ARS",false,"AIC",false));r.getProfile().setItemExclusionReasons(Map.of("ARP","机型无AEC","ARS","机型无AEC","AIC","机型无AEC"));
        assertThat(cases.updateCase(c.id(),r).itemScope()).noneMatch(s->s.selected()&&s.pending());
    }
    @Test void perItemModelConfirmationDoesNotBlanketExcludeAllRawImageRelatedItems(){
        var r=request("SPECIAL");r.setHasRawImages(false);r.setFeatureReason("历史图像条件");r.getProfile().setItemApplicability(Map.of("DDI",true,"STP",false,"RUE",true,"ART",true));r.getProfile().setItemExclusionReasons(Map.of("STP","该机型不开展信号传递检测"));var c=cases.createCase(r);
        for(String code:List.of("DDI","RUE","ART"))assertThat(c.itemScope()).anySatisfy(s->{assertThat(s.itemCode()).isEqualTo(code);assertThat(s.applicable()).isTrue();});
        assertThat(c.itemScope()).anySatisfy(s->{assertThat(s.itemCode()).isEqualTo("STP");assertThat(s.applicable()).isFalse();assertThat(s.reason()).contains("机型不开展");});
    }
    @Test void changedScopeRetainsRawDataAndRejectsFurtherEditsToExcludedItems(){
        var r=request("GENERAL","SPECIAL");var c=cases.createCase(r);var b=new RadiologyDto.MeasurementBatchRequest();b.setValues(List.of(RadiologyTestFixture.value("HCR","HCR1","2.5")));cases.saveMeasurements(c.id(),b);cases.calculate(c.id());
        r.getProfile().setSelectedGroups(List.of("GENERAL"));var changed=cases.updateCase(c.id(),r);assertThat(changed.calculationFresh()).isFalse();assertThat(changed.measurements()).anyMatch(v->v.itemCode().equals("HCR")&&v.fieldCode().equals("HCR1")&&v.valueNum().doubleValue()==2.5);
        b.setValues(List.of(RadiologyTestFixture.value("HCR","HCR1","3")));assertThatThrownBy(()->cases.saveMeasurements(c.id(),b)).hasMessageContaining("不允许录入");
        b.setValues(List.of(RadiologyTestFixture.value("HCR","HCR1","2.5")));cases.saveMeasurements(c.id(),b);cases.calculate(c.id());assertThat(cases.getCase(c.id()).itemResults()).anySatisfy(i->{assertThat(i.itemCode()).isEqualTo("HCR");assertThat(i.judgement()).isEqualTo(RadiologyJudgement.NOT_APPLICABLE);});
        assertThat(cases.getCase(c.id()).submissionIssues()).noneMatch(s->s.contains("高对比")||s.contains("防护点位"));
    }
}
