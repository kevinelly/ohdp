package com.dsf.radiology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.service.RadiologyCaseService;
import com.dsf.radiology.service.RadiologyCatalogService;
import com.dsf.radiology.service.RadiologyImportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class RadiologyImportServiceTest {

    private RadiologyImportService importer;

    @BeforeEach
    void setUp() {
        RadiologyCaseService caseService = mock(RadiologyCaseService.class);
        RadiologyCase entity = new RadiologyCase();
        entity.setModality(RadiologyModality.DR);
        when(caseService.requireCase(1L)).thenReturn(entity);
        importer = new RadiologyImportService(caseService, new RadiologyCatalogService());
    }

    @Test
    void dryRunAcceptsOneCompleteItemGroup() throws Exception {
        byte[] bytes = workbookWithRows(1, false);

        var preview = importer.importWorkbook(1L, file(bytes), "PERFORMANCE", null, true);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.appliedValues()).isZero();
        assertThat(preview.recognizedFields()).isGreaterThan(0);
    }

    @Test
    void rejectsMultipleDataRowsInsteadOfSilentlyIgnoringThem() throws Exception {
        byte[] bytes = workbookWithRows(2, false);

        assertThatThrownBy(() -> importer.importWorkbook(1L, file(bytes), "PERFORMANCE", null, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅允许一行数据");
    }

    @Test
    void reportsFormulaCellsAndMissingRequiredValues() throws Exception {
        byte[] bytes = workbookWithRows(1, true);

        var preview = importer.importWorkbook(1L, file(bytes), "PERFORMANCE", null, true);

        assertThat(preview.errors()).extracting(error -> error.message())
                .anyMatch(message -> message.contains("不得使用公式"))
                .anyMatch(message -> message.contains("缺少必填字段"));
    }

    private byte[] workbookWithRows(int rowCount, boolean formula) throws Exception {
        var template = importer.createTemplate(RadiologyModality.DR, "PERFORMANCE");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template.bytes()));
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheet("批量导入-性能");
            int hvl1 = findColumn(sheet, "HVL1");
            int hvl2 = findColumn(sheet, "HVL2");
            int hvl3 = findColumn(sheet, "HVL3");
            for (int index = 0; index < rowCount; index++) {
                var row = index == 0 ? sheet.getRow(2) : sheet.createRow(index + 2);
                if (formula && index == 0) row.createCell(hvl1).setCellFormula("1+1");
                else row.createCell(hvl1).setCellValue(2.1 + index);
                row.createCell(hvl2).setCellValue(2.2 + index);
                row.createCell(hvl3).setCellValue(2.3 + index);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private int findColumn(org.apache.poi.ss.usermodel.Sheet sheet, String code) {
        for (var cell : sheet.getRow(1)) if (code.equals(cell.getStringCellValue())) return cell.getColumnIndex();
        throw new IllegalArgumentException(code);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "dr.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }
}
