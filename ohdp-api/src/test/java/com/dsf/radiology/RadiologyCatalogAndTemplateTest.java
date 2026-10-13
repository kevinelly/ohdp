package com.dsf.radiology;

import static org.assertj.core.api.Assertions.assertThat;

import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.service.RadiologyCatalogService;
import com.dsf.radiology.service.RadiologyImportService;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class RadiologyCatalogAndTemplateTest {

    private final RadiologyCatalogService catalog = new RadiologyCatalogService();
    private final RadiologyImportService importer = new RadiologyImportService(null, catalog);

    @Test
    void catalogContainsAllDemandItems() {
        assertThat(catalog.getCatalog(RadiologyModality.DR)).hasSize(19)
                .extracting(RadiologyCatalogService.ItemDefinition::code)
                .containsExactly("TVD", "ROA", "ROQ", "HVL", "ETD", "ARP", "ARS", "AIC", "UBD", "LRD", "DDI", "STP", "RUE", "REA", "ILA", "ART", "HCR", "LCR", "RPD");
        assertThat(catalog.getCatalog(RadiologyModality.CT)).hasSize(12)
                .extracting(RadiologyCatalogService.ItemDefinition::code)
                .containsExactly("TPA", "GTA", "CTDI", "LBA", "RST", "MTF", "LCD", "HUL", "HUA", "HUU", "NPS", "RPD");
    }

    @Test
    void generatedTemplatesIncludeCorrectionFieldsAndInstructions() throws Exception {
        assertColumns(RadiologyModality.DR, "PERFORMANCE", 115);
        assertColumns(RadiologyModality.DR, "PROTECTION", 43);
        assertColumns(RadiologyModality.CT, "PERFORMANCE", 107);
        assertColumns(RadiologyModality.CT, "PROTECTION", 42);

        var template = importer.createTemplate(RadiologyModality.DR, "PERFORMANCE");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template.bytes()))) {
            assertThat(workbook.getSheet("填写说明").getRow(2).getCell(1).getStringCellValue())
                    .isEqualTo(RadiologyImportService.TEMPLATE_VERSION);
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_字段字典"))).isTrue();
            var sheet = workbook.getSheet("批量导入-性能");
            assertThat(sheet.getRow(1).getCell(12).getStringCellValue()).isEqualTo("TVD-K1");
            assertThat(sheet.getDataValidations()).isNotEmpty();
            assertThat(sheet.getRow(2)).allSatisfy(cell -> assertThat(cell.getCellType().name()).isNotEqualTo("FORMULA"));
        }
    }

    private void assertColumns(RadiologyModality modality, String type, int expected) throws Exception {
        var template = importer.createTemplate(modality, type);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template.bytes()))) {
            var sheet = workbook.getSheet(type.equals("PROTECTION") ? "批量导入-防护" : "批量导入-性能");
            assertThat((int) sheet.getRow(1).getLastCellNum()).isEqualTo(expected);
        }
    }
}
