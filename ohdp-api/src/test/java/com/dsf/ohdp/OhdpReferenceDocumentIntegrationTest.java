package com.dsf.ohdp;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.ohdp.entity.OhdpLaw;
import com.dsf.ohdp.repository.OhdpLawRepository;
import com.dsf.ohdp.service.OhdpReferenceDocumentService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OhdpReferenceDocumentIntegrationTest {

    @Autowired
    private OhdpLawRepository lawRepository;

    @Autowired
    private OhdpReferenceDocumentService documentService;

    @Test
    void uploadsAndKeepsTwoDocumentVersionsWithIntegrityMetadata() throws Exception {
        OhdpLaw law = new OhdpLaw();
        law.setTitle("法规原文版本集成测试");
        law.setDocNo("TEST-" + System.nanoTime());
        law.setLevel("NATIONAL");
        law = lawRepository.save(law);

        var first = documentService.upload(
                "law",
                law.getId(),
                "首次发布版",
                "https://example.test/law",
                file("law-v1.pdf", "第一版法规原文"));
        var second = documentService.upload(
                "LAW",
                law.getId(),
                "修订版",
                null,
                file("law-v2.pdf", "第二版法规原文"));

        var versions = documentService.list("LAW", law.getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getId()).isEqualTo(second.getId());
        assertThat(versions.get(0).getCurrent()).isTrue();
        assertThat(versions.get(1).getId()).isEqualTo(first.getId());
        assertThat(versions.get(1).getCurrent()).isFalse();
        assertThat(second.getSha256()).hasSize(64);

        var download = documentService.download(second.getId());
        assertThat(download.fileName()).isEqualTo("law-v2.pdf");
        assertThat(download.resource().exists()).isTrue();
        assertThat(download.resource().getContentAsByteArray())
                .isEqualTo("第二版法规原文".getBytes(StandardCharsets.UTF_8));
        assertThat(lawRepository.findByIdAndDeletedFalse(law.getId()).orElseThrow().getFileId())
                .isEqualTo(second.getId());
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(
                "file",
                name,
                "application/pdf",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
