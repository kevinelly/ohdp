package com.dsf.ohdp;

import com.dsf.ohdp.entity.*;
import com.dsf.ohdp.repository.*;
import com.dsf.ohdp.service.OhdpProjectService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportPrecheckSafetyTest {
    @Autowired OhdpProjectRepository projects;
    @Autowired OhdpResultItemRepository results;
    @Autowired OhdpProjectService service;
    @Test void formalGenerationRejectsPendingAndInconsistentLegacyResults() {
        OhdpProject p=new OhdpProject();p.setProjectCode("AUDIT-"+System.nanoTime());p.setCompanyName("预检安全测试");p.setCompanyAddress("验收测试地址");p.setDetectType("定期检测");p.setStatus(ProjectStatus.JUDGED);p=projects.save(p);
        OhdpResultItem r=new OhdpResultItem();r.setProjectId(p.getId());r.setJobName("焊工");r.setFactorName("二甲苯");r.setMetricType("TWA");r.setValueNum(new BigDecimal("45"));r.setValueRaw("4.5");r.setUnit("ug/m3");r.setLimitUnit("mg/m3");r.setLimitValue(new BigDecimal("50"));r.setJudgement("合格");results.save(r);
        var check=service.precheckReport(p.getId());assertThat(check.getPassed()).isFalse();
        assertThat(check.getMissingItems()).anyMatch(x->x.contains("原始值与数值不一致")).anyMatch(x->x.contains("单位必须一致")).anyMatch(x->x.contains("未完成有效判定"));
        Long id=p.getId();assertThatThrownBy(()->service.generateReport(id,true)).hasMessageContaining("检查未通过");
    }
}
