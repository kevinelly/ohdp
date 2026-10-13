package com.dsf.desktop;

import com.dsf.common.config.DesktopSampleDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:desktop_sample_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "BHFYZX_DATA_HOME=./target/desktop-test", "BHFYZX_SAMPLE_ZIP=./target/desktop-test/sample.zip",
    "BHFYZX_INSTANCE_TOKEN=desktop-test-secret", "dsf.auth.initial-password=Test@345"
})
@ActiveProfiles({"test", "desktop"})
@AutoConfigureMockMvc
class DesktopSampleDataTest {
    @Autowired DesktopSampleDataService service;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;

    @BeforeEach void fixture() throws Exception {
        for (String table : List.of("desktop_sample_row", "desktop_sample_file", "ohdp_project_position", "ohdp_project", "ohdp_company_profile")) db.update("DELETE FROM " + table);
        writeFixture(false);
    }

    private ObjectNode base(long id) {
        return mapper.createObjectNode().put("id", id).put("deleted", 0)
            .put("created_at", "2026-09-17 12:00:00").put("updated_at", "2026-09-17 12:00:00");
    }

    private void writeFixture(boolean broken) throws Exception {
        ObjectNode document = mapper.createObjectNode().put("format", 1).put("snapshotId", "fixture");
        document.putArray("files");
        var tables = document.putArray("tables");
        tables.addObject().put("name", "ohdp_company_profile").putArray("rows").add(base(70).put("company_name", "测试单位").put("company_address", "测试地址"));
        tables.addObject().put("name", "ohdp_project").putArray("rows").add(base(90).put("project_code", "SAMPLE-90")
                .put("company_name", "测试单位").put("company_address", "测试地址").put("company_profile_id", 70)
                .put("detect_type", "定期检测").put("status", "DRAFT"));
        ObjectNode position = base(100).put("project_id", 90);
        if (!broken) position.put("job_name", "测试岗位");
        tables.addObject().put("name", "ohdp_project_position").putArray("rows").add(position);
        Path zip = Path.of("target/desktop-test/sample.zip");
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("dataset.json")); output.write(mapper.writeValueAsBytes(document)); output.closeEntry();
        }
    }

    @Test void importRemapsRelationsAndDeleteKeepsIndependentBusiness() {
        db.update("INSERT INTO ohdp_company_profile (company_name,company_address,deleted,created_at,updated_at) VALUES ('真实单位','真实地址',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO ohdp_project(project_code,company_name,company_address,detect_type,status,deleted,created_at,updated_at) VALUES ('REAL','真实单位','真实地址','定期检测','DRAFT',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        service.importData();
        long imported = ((Number)service.status().get("rows")).longValue();
        assertThat(imported).isEqualTo(3);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ohdp_project_position p JOIN ohdp_project j ON p.project_id=j.id JOIN ohdp_company_profile c ON j.company_profile_id=c.id WHERE c.company_name='测试单位'", Long.class)).isEqualTo(1);
        service.importData();
        assertThat(((Number)service.status().get("rows")).longValue()).isEqualTo(imported);
        service.deleteData();
        assertThat(db.queryForList("SELECT project_code FROM ohdp_project", String.class)).containsExactly("REAL");
        assertThat(db.queryForList("SELECT company_name FROM ohdp_company_profile", String.class)).containsExactly("真实单位");
        service.importData();
        assertThat(((Number)service.status().get("rows")).longValue()).isEqualTo(imported);
    }

    @Test void deletionIsCancelledWhenNewBusinessReferencesSample() {
        service.importData();
        long project = db.queryForObject("SELECT id FROM ohdp_project WHERE project_code='SAMPLE-90'", Long.class);
        db.update("INSERT INTO ohdp_project_position(project_id,job_name,deleted,created_at,updated_at) VALUES (?,'后来新增的岗位',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", project);
        assertThatThrownBy(() -> service.deleteData()).hasMessageContaining("后来新增的业务记录引用");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ohdp_project_position", Long.class)).isEqualTo(2);
        assertThat(service.status().get("imported")).isEqualTo(true);
    }

    @Test void failedImportRollsBackAllRows() throws Exception {
        writeFixture(true);
        assertThatThrownBy(() -> service.importData()).hasMessageContaining("已回滚");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ohdp_company_profile", Long.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ohdp_project", Long.class)).isZero();
        assertThat(service.status().get("imported")).isEqualTo(false);
    }

    @Test void maintenanceEndpointsRequireLauncherSecret() throws Exception {
        mvc.perform(post("/desktop/sample-data/import")).andExpect(status().isForbidden());
        mvc.perform(post("/desktop/sample-data/delete").header("X-Desktop-Token", "wrong")).andExpect(status().isForbidden());
        mvc.perform(post("/desktop/sample-data/import").header("X-Desktop-Token", "desktop-test-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(true));
    }
}
