package com.dsf.radiology;

import static org.assertj.core.api.Assertions.*;
import com.dsf.radiology.service.RadiologyPdfPagination;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;

class RadiologyPdfPaginationTest {
    @Test void conversionCopyRemovesOnlyUnsupportedFooterFieldsAndPreservesArchivedDocx() throws Exception {
        byte[] source=getClass().getResourceAsStream("/templates/radiology/CT-v20261013-r2.docx").readAllBytes(),original=source.clone();
        byte[] prepared=RadiologyPdfPagination.prepare(source);
        assertThat(source).isEqualTo(original);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(prepared));var before=new XWPFDocument(new ByteArrayInputStream(original))) {
            assertThat(doc.getParagraphs()).extracting(p->p.getText().replaceAll("\\s","")).containsAll(before.getParagraphs().stream().map(p->p.getText().replaceAll("\\s","")).toList());
            assertThat(doc.getFooterList()).allMatch(f->f.getParagraphs().stream().noneMatch(p->p.getCTP().xmlText().contains("SECTIONPAGES")));
            assertThat(doc.getFooterList()).anyMatch(f->f.getText().contains("检验检测单位"));
        }
    }
    @Test void bodyDetectionUsesRenderedHeaderInsteadOfAssumingTwoFrontPages() throws Exception {
        try(var doc=new PDDocument()) {
            for(int i=0;i<5;i++) {
                var page=new PDPage();doc.addPage(page);
                try(var stream=new PDPageContentStream(doc,page)) {stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN),12);stream.newLineAtOffset(50,750);stream.showText(i<3?"Front matter":"BHCDC/QBG24-206-5 (2016)");stream.endText();}
            }
            assertThat(RadiologyPdfPagination.bodyStart(doc)).isEqualTo(3);
        }
    }
}
