package com.dsf.radiology.service;

import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.text.PDFTextStripper;

/** Word's SECTIONPAGES is unsupported by Writer. Number the actual rendered body pages. */
public final class RadiologyPdfPagination {
    private RadiologyPdfPagination() {}
    public static byte[] prepare(byte[] source) throws IOException {
        try(var doc=new XWPFDocument(new ByteArrayInputStream(source));var out=new ByteArrayOutputStream()) {
            // Writer expands the legacy DOC line spacing and character spacing differently.
            // Normalize only the conversion copy; keep the supplied Word front matter intact.
            boolean frontMatter=false;
            for(var p:doc.getParagraphs()) {
                String text=p.getText();
                if(text.replaceAll("\\s","").startsWith("封二")) frontMatter=true;
                if(frontMatter && !text.isBlank()) {
                    boolean title=text.trim().equals("注意事项");
                    p.setSpacingBefore(0);p.setSpacingAfter(title?180:60);p.setSpacingBetween(title?30:18,LineSpacingRule.EXACT);
                    var ppr=p.getCTP().getPPr();if(ppr.isSetSnapToGrid())ppr.getSnapToGrid().setVal(false);else ppr.addNewSnapToGrid().setVal(false);
                    for(var run:p.getRuns())run.setFontSize(title?22:12);
                    if(text.contains("封二") && text.contains("BHCDC")) {
                        String control=text.substring(text.indexOf("BHCDC")).replaceAll("\\s","");
                        p.setPageBreak(true);
                        while(!p.getRuns().isEmpty())p.removeRun(0);
                        var left=p.createRun();left.setText("封二");left.setFontSize(12);left.addTab();
                        var right=p.createRun();right.setText(control);right.setFontSize(10);
                        var tabs=p.getCTP().getPPr().isSetTabs()?p.getCTP().getPPr().getTabs():p.getCTP().getPPr().addNewTabs();
                        while(tabs.sizeOfTabArray()>0)tabs.removeTab(0);
                        var tab=tabs.addNewTab();tab.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT);tab.setPos(java.math.BigInteger.valueOf(9000));
                    }
                }
                if(p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetSectPr()) frontMatter=false;
            }
            for(var table:doc.getTables()) {
                String tableText=table.getText().replaceAll("\\s","");
                if(!tableText.contains("样品受理编号") && !tableText.contains("样品名称"))continue;
                if(table.getNumberOfRows()!=1 && table.getNumberOfRows()!=7)continue;
                for(var row:table.getRows()) for(var p:row.getCell(0).getParagraphs()) {
                    String label=p.getText().replace(" ","");
                    while(!p.getRuns().isEmpty())p.removeRun(0);
                    var run=p.createRun();run.setText(label);run.setFontFamily("宋体");run.setFontSize(12);
                }
            }
            for(var footer:doc.getFooterList()) for(var p:footer.getParagraphs()) {
                String xml=p.getCTP().xmlText();
                if(!xml.contains("SECTIONPAGES") && !xml.contains("NUMPAGES") && !xml.contains(" PAGE ")) continue;
                var node=p.getCTP().getDomNode();
                for(var child=node.getFirstChild();child!=null;) {var next=child.getNextSibling();if(!"pPr".equals(child.getLocalName()))node.removeChild(child);child=next;}
            }
            doc.write(out);return out.toByteArray();
        }
    }
    public static int bodyStart(PDDocument doc) throws IOException {
        var text=new PDFTextStripper();
        for(int i=0;i<doc.getNumberOfPages();i++) {
            text.setStartPage(i+1);text.setEndPage(i+1);String page=text.getText(doc).replaceAll("\\s","");
            if(page.contains("BHCDC/QBG24-206-5") || page.contains("检测项目及结果") || (page.contains("检测依据") && page.contains("检测仪器"))) return i;
        }
        return -1;
    }
    public static byte[] number(byte[] pdf) throws IOException {
        try(var doc=Loader.loadPDF(pdf);var out=new ByteArrayOutputStream()) {
            int first=bodyStart(doc);if(first<0)return pdf;
            Font font;
            try {font=Font.createFonts(new File("/usr/share/fonts/noto/NotoSerifCJK-Regular.ttc"))[0];}
            catch(FontFormatException ex){throw new IOException("报告页码字体加载失败",ex);}
            int count=doc.getNumberOfPages()-first;
            for(int i=first;i<doc.getNumberOfPages();i++) {
                String label="共 "+count+" 页  第 "+(i-first+1)+" 页";
                var image=new BufferedImage(1000,100,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
                try {g.setColor(Color.WHITE);g.fillRect(0,0,1000,100);g.setColor(Color.BLACK);g.setFont(font.deriveFont(64f));g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.drawString(label,5,76);} finally {g.dispose();}
                var page=doc.getPage(i);
                try(var stream=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)) {
                    // Reserve the page-count line below the existing border and stamp caption.
                    stream.setNonStrokingColor(Color.WHITE);stream.addRect(50,28,page.getMediaBox().getWidth()-100,24);stream.fill();
                    stream.drawImage(LosslessFactory.createFromImage(doc,image),page.getMediaBox().getWidth()-210,30,160,16);
                }
            }
            doc.save(out);return out.toByteArray();
        }
    }
}
