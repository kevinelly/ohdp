package com.dsf.ohdp.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

/** Rebuild variable result tables with a rectangular grid and explicit source fields. */
final class ReportTableRepair {
    private ReportTableRepair() {}
    private static String t(JsonNode n, String k) { return n.path(k).asText(""); }
    private static List<JsonNode> list(JsonNode n) { List<JsonNode> a = new ArrayList<>(); if(n.isArray()) n.forEach(a::add); return a; }
    private static String metric(JsonNode n) { return ReportResultRules.metric(t(n,"metricType")); }
    private static String category(JsonNode n) { return ReportResultRules.category(t(n,"factorName"),t(n,"metricType"),t(n,"factorCategory")); }
    private static BigDecimal number(JsonNode n, String k) { try { return new BigDecimal(t(n,k)); } catch(Exception e) { return BigDecimal.ZERO; } }
    private static String decimal(BigDecimal n) { return n.stripTrailingZeros().toPlainString(); }
    private static String value(JsonNode n) { return t(n,"valueRaw").isBlank() ? t(n,"valueNum") : t(n,"valueRaw"); }
    private static BigDecimal limit(JsonNode n) { return t(n,"adjustedLimitValue").isBlank() ? number(n,"limitValue") : number(n,"adjustedLimitValue"); }
    private static String joint(List<JsonNode> a, String key) { return a.stream().map(n->t(n,key)).filter(s->!s.isBlank()).distinct().sorted().reduce((x,y)->x+"；"+y).orElse("—"); }
    private static String job(JsonNode n) { return t(n,"workLocation")+"\u0000"+t(n,"jobName"); }
    private static String point(JsonNode n) { return t(n,"pointCode"); }
    private static String factorPoint(JsonNode n) { return t(n,"factorName")+"\u0000"+point(n); }

    static void apply(XWPFDocument doc, JsonNode project, JsonNode supplement, JsonNode positions, JsonNode results, boolean prune) {
        List<XWPFTable> tables = new ArrayList<>(doc.getTables());
        if(tables.size()<108) return;
        List<JsonNode> all=list(results);
        List<XWPFTable> unused=new ArrayList<>();
        if(!supplement.path("vocAnalysisPerformed").asBoolean(false)) unused.add(tables.get(5));
        for(int i:new int[]{7,8,9}) {
            String field=switch(i){case 7->"xrayDevices";case 8->"sealedSources";default->"unsealedSources";};
            if(list(supplement.path(field)).isEmpty()) {
                grid(tables.get(i-1),List.of("源项","情况"),List.of());unused.add(tables.get(i-1));
            }
        }
        if(!supplement.path("exclusionScreeningPerformed").asBoolean(false))unused.add(tables.get(13));
        for(int index=16;index<=44;index++) {
            int target=index;
            List<JsonNode> rows=all.stream().filter(n->bodyTable(n)==target).toList();
            resultTable(tables.get(index-1),rows,false);
            if(rows.isEmpty()) unused.add(tables.get(index-1));
        }
        // PPE: category/name/manufacturer/model/parameters/job/use, in template column order.
        grid(tables.get(11), List.of("分类","类别","生产厂家","型号","参数","岗位","使用情况"),
                list(supplement.path("personalProtectiveEquipment")).stream().map(n->List.of(t(n,"category"),t(n,"ppeName"),t(n,"manufacturer"),t(n,"model"),t(n,"protectionParameters"),t(n,"positions"),t(n,"usageStatus"))).toList());

        Map<String,JsonNode> max=new LinkedHashMap<>();
        for(JsonNode n:all) {
            String key=job(n)+"\u0000"+t(n,"factorName")+"\u0000"+metric(n)+"\u0000"+t(n,"measurementBodyPart")+"\u0000"+t(n,"measurementBand")+"\u0000"+t(n,"unit");
            JsonNode prev=max.get(key);
            if(prev==null || number(n,"valueNum").compareTo(number(prev,"valueNum"))>0) max.put(key,n);
        }
        List<List<String>> summary=new ArrayList<>();
        for(JsonNode n:max.values()) summary.add(List.of(t(n,"workLocation"),t(n,"jobName"),t(n,"factorName"),metric(n),value(n),limitText(n),t(n,"limitUnit"),t(n,"judgement"),t(n,"measurementDate")));
        grid(tables.get(44),List.of("工作场所","岗位","因素","指标","最大结果","有效限值","单位","判定","对应日期"),summary);
        List<JsonNode> failed=all.stream().filter(n->"不合格".equals(t(n,"judgement"))).toList();
        grid(tables.get(47),List.of("工作场所","岗位","测点","因素/指标","结果/有效限值","有效限值","原因"),failed.stream().map(n->List.of(t(n,"workLocation"),t(n,"jobName"),point(n),t(n,"factorName")+" / "+metric(n),ratio(n),limitText(n),t(n,"overLimitReason"))).toList());
        if(failed.isEmpty()) unused.add(tables.get(47));
        fillMixed(tables.get(45),all,supplement);
        if(!"APPLICABLE".equals(t(supplement,"mixedExposureAssessment"))) unused.add(tables.get(45));
        fillStatistics(tables.get(63),tables.get(64),all,list(positions));
        for(int i:new int[]{49,50}) {
            String cat=i==49?"化学物质":"粉尘";
            Map<String,JsonNode> limits=new LinkedHashMap<>();
            for(JsonNode n:all)if(cat.equals(category(n)))limits.putIfAbsent(t(n,"factorName")+metric(n)+limitText(n),n);
            grid(tables.get(i-1),List.of("危害因素","指标","原限值","有效限值","单位","采用依据"),limits.values().stream().map(n->List.of(t(n,"factorName"),metric(n),t(n,"limitValue"),limitText(n),t(n,"limitUnit"),t(n,"evaluationBasis"))).toList());
            if(limits.isEmpty())unused.add(tables.get(i-1));
        }

        // Each result remains identifiable even when several factors share a result sheet.
        int[][] groups={{69,70},{71,72,73,74},{75,76,77,78},{79,80,81},{82,83,84,85},{86,87,88},{89,90},{91,92},{93,94},{95,96},{97,98},{99,100},{101,102},{103,104},{105,106}};
        for(int[] group:groups) {
            List<JsonNode> groupRows=new ArrayList<>();
            for(int j=1;j<group.length;j++) {
                int target=group[j];
                List<JsonNode> rows=all.stream().filter(n->detailTable(n)==target).toList();
                groupRows.addAll(rows);
                resultTable(tables.get(target-1),rows,true);
                if(rows.isEmpty()) unused.add(tables.get(target-1));
            }
            infoTable(tables.get(group[0]-1),groupRows);
            if(!groupRows.isEmpty()) {
                int pos=doc.getPosOfTable(tables.get(group[0]-1));
                if(pos>0 && doc.getBodyElements().get(pos-1) instanceof XWPFParagraph title) {
                    title.setPageBreak(true);title.setKeepNext(true);
                }
            }
            if(groupRows.isEmpty()) unused.add(tables.get(group[0]-1));
        }
        if(!supplement.path("vocAnalysisPerformed").asBoolean(false)) {
            grid(tables.get(106),List.of("检测项目","开展情况"),List.of(List.of("挥发性有机组分定性分析","未开展，不涉及")));
            grid(tables.get(107),List.of("组分","CAS号","峰面积百分比"),List.of());
            unused.add(tables.get(106));unused.add(tables.get(107));
        } else {
            grid(tables.get(106),List.of("项目","资料"),List.of(
                    List.of("检测机构及来源",t(supplement,"vocTestingOrganization")+" / "+t(supplement,"vocSourceReportNo")),
                    List.of("采样日期",t(supplement,"vocSamplingDate")),List.of("实验室检测日期",t(supplement,"vocDetectionDate")),
                    List.of("依据 / 方法 / 仪器",t(supplement,"vocDetectionBasis")+" / "+t(supplement,"vocDetectionMethod")+" / "+t(supplement,"vocInstrument"))));
            grid(tables.get(107),List.of("组分","CAS号","峰面积百分比"),list(supplement.path("vocAnalysisItems")).stream().map(n->List.of(t(n,"detectedComponents"),t(n,"casNo"),t(n,"peakAreaPercent"))).toList());
        }
        // Index-sensitive writes have finished. Remove only tables known to have no project data.
        if(prune) {
            // Remove empty result sheets with their page breaks and titles, not only their tables.
            List<IBodyElement> body=new ArrayList<>(doc.getBodyElements());
            Set<IBodyElement> remove=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int[] group:groups) if(unused.contains(tables.get(group[0]-1))) {
                int start=body.indexOf(tables.get(group[0]-1));
                while(start>0 && body.get(start-1) instanceof XWPFParagraph)start--;
                int end=body.indexOf(tables.get(group[group.length-1]-1));
                if(start>=0 && end>=start)remove.addAll(body.subList(start,end+1));
            }
            if(!supplement.path("vocAnalysisPerformed").asBoolean(false)) {
                int start=body.indexOf(tables.get(106));while(start>0 && body.get(start-1) instanceof XWPFParagraph)start--;
                remove.addAll(body.subList(start,body.size()));
            }
            for(int i=body.size()-1;i>=0;i--)if(remove.contains(body.get(i)))doc.removeBodyElement(i);
            for(XWPFTable table:unused)removeTable(doc,table);
        }
    }

    private static int bodyTable(JsonNode n) {
        int physical=ReportResultRules.physicalTable(metric(n));if(physical>0)return physical;
        return switch(metric(n)) {
            case "SILICA" -> 16; case "TWA","CTWA" -> 17;case "STEL","CSTE" -> 18;case "MAC","CME" -> 19;case "PE","CPE" -> 20;
            case "X_GAMMA","RADIATION_DOSE_RATE" -> 40;case "SURFACE_CONTAMINATION" -> 41;case "SEALED_SOURCE_LEAKAGE" -> 42;case "AIR_RADIONUCLIDE" -> 43;case "RADON" -> 44;default -> 0;
        };
    }
    private static int detailTable(JsonNode n) {
        int b=bodyTable(n);
        if(b==16)return 70;
        if(b>=17 && b<=20) return "个体".equals(t(n,"detectionMode")) ? 72 : "定点长时间".equals(t(n,"detectionMode")) ? 73 : 74;
        if(b==21 || b==22)return "个体".equals(t(n,"detectionMode"))?76:77;
        return switch(b) { case 23->78;case 24,25->t(n,"measurementBodyPart").isBlank()?80:81;case 26,27->83;case 28->84;case 29,30->87;case 31,32->88;case 33->90;case 34,35->92;case 36->94;case 37->96;case 38,39->98;case 40->100;case 41,42->102;case 43->104;case 44->106;default->0;};
    }
    private static String ratio(JsonNode n) {
        if(!Set.of("化学物质","粉尘").contains(category(n)) || limit(n).signum()<=0)return "—";
        return decimal(number(n,"valueNum").divide(limit(n),2,RoundingMode.HALF_UP));
    }
    private static String limitText(JsonNode n) {
        String text=decimal(limit(n));
        if(number(n,"reductionFactor").signum()>0 && number(n,"reductionFactor").compareTo(BigDecimal.ONE)<0) text="PC-TWAa "+limit(n).setScale(2,RoundingMode.HALF_UP).toPlainString()+"\nRF="+t(n,"reductionFactor");
        else if(Set.of("PE","CPE").contains(metric(n)))text="PE "+text+"\n倍数="+t(n,"peakLimitMultiplier");
        return text;
    }
    private static void resultTable(XWPFTable table,List<JsonNode> rows,boolean detail) {
        List<String> headers=new ArrayList<>(List.of(detail?"样品/测点编号":"测点编号","工作场所/岗位","因素/指标","采样/测量日期","方式","结果（单位）"));
        boolean body=rows.stream().anyMatch(n->!t(n,"measurementBodyPart").isBlank()),band=rows.stream().anyMatch(n->!t(n,"measurementBand").isBlank());
        if(body)headers.add("部位");if(band)headers.add("波段");headers.add(detail?"记录/条件":"有效限值/判定");
        List<List<String>> values=new ArrayList<>();
        for(JsonNode n:rows) {
            boolean noise=bodyTable(n)>=21 && bodyTable(n)<=23;
            List<String> row=new ArrayList<>(List.of((detail && !t(n,"sampleCode").isBlank()?t(n,"sampleCode")+"\n":"")+point(n),t(n,"workLocation")+"\n"+t(n,"jobName"),t(n,"factorName")+"\n"+metric(n),t(n,"measurementDate"),t(n,"detectionMode")+(!noise || t(n,"noiseType").isBlank()?"":"\n"+t(n,"noiseType")),value(n)+" "+t(n,"unit")));
            if(body)row.add(t(n,"measurementBodyPart"));if(band)row.add(t(n,"measurementBand"));
            String effective=limitText(n),unit=t(n,"limitUnit");int newline=effective.indexOf('\n');
            effective=newline<0?effective+" "+unit:effective.substring(0,newline)+" "+unit+effective.substring(newline);
            row.add(detail?t(n,"sourceRecordNo")+"\n"+t(n,"measurementConditions"):effective+"\n"+t(n,"judgement"));values.add(row);
        }
        grid(table,headers,values);
    }
    private static void infoTable(XWPFTable table,List<JsonNode> rows) {
        if(rows.isEmpty()){grid(table,List.of("检测项目","情况"),List.of(List.of("本项目不涉及","未开展")));return;}
        List<List<String>> v=new ArrayList<>();
        v.add(List.of("检测项目",joint(rows,"factorName")));v.add(List.of("实际采样/测量日期",joint(rows,"measurementDate")));
        if(rows.stream().anyMatch(n->!t(n,"laboratoryDate").isBlank()))v.add(List.of("实验室检测日期",joint(rows,"laboratoryDate")));
        Map<String,JsonNode> sources=new LinkedHashMap<>();
        for(JsonNode n:rows)sources.putIfAbsent(t(n,"factorName")+" / "+t(n,"sourceRecordNo")+" / "+t(n,"detectionMethod"),n);
        sources.forEach((key,n)->{
            v.add(List.of(key,"机构："+t(n,"testingOrganization")+"\n依据："+t(n,"detectionBasis")+"\n方法："+t(n,"detectionMethod")+"\n仪器："+t(n,"instrument")+"\n检出限："+t(n,"detectionLimit")+"；定量下限："+t(n,"quantificationLimit")));
        });
        for(JsonNode n:rows)if(!t(n,"evaluationBasis").isBlank())v.add(List.of(t(n,"factorName")+" / "+metric(n)+" 评价依据",t(n,"evaluationBasis")+"\n日工时="+t(n,"dailyExposureHours")+"；周工时="+t(n,"weeklyExposureHours")+"；周工作天数="+t(n,"weeklyWorkDays")+(Set.of("PE","CPE").contains(metric(n))?"\n接触分钟="+t(n,"peakExposureMinutes")+"；每班次数="+t(n,"peakTimesPerShift")+"；间隔分钟="+t(n,"peakIntervalMinutes"):"")));
        grid(table,List.of("项目","实际检测资料"),v);
    }
    private static void fillMixed(XWPFTable table,List<JsonNode> all,JsonNode supplement) {
        Map<String,List<JsonNode>> groups=new LinkedHashMap<>();
        for(JsonNode n:all)if(!t(n,"mixedExposureGroup").isBlank())groups.computeIfAbsent(t(n,"mixedExposureGroup")+" / "+job(n).replace('\u0000','/')+" / "+t(n,"measurementDate"),k->new ArrayList<>()).add(n);
        List<List<String>> rows=new ArrayList<>();
        for(var entry:groups.entrySet()) {
            BigDecimal sum=BigDecimal.ZERO;List<String> calculations=new ArrayList<>();boolean valid=true;
            for(JsonNode n:entry.getValue()) {
                if(limit(n).signum()<=0 || !"合格".equals(t(n,"judgement")) && !"不合格".equals(t(n,"judgement"))){valid=false;continue;}
                sum=sum.add(number(n,"valueNum").divide(limit(n),12,RoundingMode.HALF_UP));
                calculations.add(t(n,"factorName")+": "+value(n)+"/"+decimal(limit(n))+" "+t(n,"unit"));
            }
            rows.add(List.of(entry.getKey(),String.join("\n",calculations),valid?decimal(sum.setScale(4,RoundingMode.HALF_UP)):"无法判定",joint(entry.getValue(),"mixedExposureBasis"),valid?(sum.compareTo(BigDecimal.ONE)<=0?"合格":"不合格"):"未判定"));
        }
        if(rows.isEmpty())rows.add(List.of("不涉及","", "",t(supplement,"mixedExposureAssessmentBasis"),"不适用"));
        grid(table,List.of("组合/岗位/日期","各因素 Ci/Li","Σ(Ci/Li)","共同效应及适用依据","判定（≤1）"),rows);
    }
    static long mixedFailures(List<JsonNode> all) {
        Map<String,BigDecimal> sums=new LinkedHashMap<>();
        for(JsonNode n:all)if(!t(n,"mixedExposureGroup").isBlank() && limit(n).signum()>0) {
            String key=t(n,"mixedExposureGroup")+job(n)+t(n,"measurementDate");
            sums.merge(key,number(n,"valueNum").divide(limit(n),12,RoundingMode.HALF_UP),BigDecimal::add);
        }
        return sums.values().stream().filter(n->n.compareTo(BigDecimal.ONE)>0).count();
    }
    private static void fillStatistics(XWPFTable exposure,XWPFTable detection,List<JsonNode> all,List<JsonNode> positions) {
        Map<String,List<JsonNode>> factors=new LinkedHashMap<>();
        for(JsonNode n:all)factors.computeIfAbsent(category(n)+"\u0000"+t(n,"factorName"),k->new ArrayList<>()).add(n);
        List<List<String>> people=new ArrayList<>(),points=new ArrayList<>();int totalPoints=0,totalFailures=0;
        for(var entry:factors.entrySet()) {
            List<JsonNode> rows=entry.getValue();Set<String> jobs=new HashSet<>();rows.forEach(n->jobs.add(job(n)));
            int workers=positions.stream().filter(p->jobs.contains(job(p))).mapToInt(p->p.path("workerCount").asInt()).sum();
            String[] key=entry.getKey().split("\u0000",-1);
            people.add(List.of(key[0],key[1],String.valueOf(workers)));
            boolean complete=rows.stream().allMatch(n->!point(n).isBlank());
            long count=rows.stream().map(ReportTableRepair::factorPoint).distinct().count(),fail=rows.stream().filter(n->"不合格".equals(t(n,"judgement"))).map(ReportTableRepair::factorPoint).distinct().count();
            points.add(List.of(key[0],key[1],complete?""+count:"测点编号缺失",complete?""+fail:"无法统计"));if(complete){totalPoints+=count;totalFailures+=fail;}
        }
        boolean complete=all.stream().allMatch(n->!point(n).isBlank());
        points.add(List.of("合计（按因素计点）","",complete?""+totalPoints:"无法统计",complete?""+totalFailures:"无法统计"));
        grid(exposure,List.of("危害类别","具体因素","接触人数（各因素间不累加）"),people);
        grid(detection,List.of("危害类别","具体因素","检测点数（同因素测点跨日/指标去重）","超标点数"),points);
    }
    static void grid(XWPFTable table,List<String> headers,List<List<String>> data) {
        while(table.getNumberOfRows()>0)table.removeRow(0);
        table.getCTTbl().setTblGrid(CTTblGrid.Factory.newInstance());
        var grid=table.getCTTbl().getTblGrid();
        int width=9000/headers.size();for(int i=0;i<headers.size();i++)grid.addNewGridCol().setW(BigInteger.valueOf(width));
        table.setWidth(9000);
        var props=table.getCTTbl().getTblPr();if(props.isSetTblLayout())props.unsetTblLayout();props.addNewTblLayout().setType(STTblLayoutType.FIXED);
        add(table,headers,true,width);
        if(data.isEmpty()){List<String> empty=new ArrayList<>(Collections.nCopies(headers.size(),"—"));empty.set(0,"不涉及");add(table,empty,false,width);}
        else for(List<String> row:data)add(table,row,false,width);
    }
    private static void add(XWPFTable table,List<String> values,boolean header,int width) {
        XWPFTableRow row=table.createRow();while(row.getTableCells().size()<values.size())row.createCell();
        while(row.getTableCells().size()>values.size())row.removeCell(row.getTableCells().size()-1);
        row.setCantSplitRow(true);row.setRepeatHeader(header);
        for(int i=0;i<values.size();i++) {
            XWPFTableCell c=row.getCell(i);c.getCTTc().setTcPr(CTTcPr.Factory.newInstance());c.setWidth(""+width);
            while(c.getParagraphs().size()>1)c.removeParagraph(1);
            XWPFParagraph p=c.getParagraphs().isEmpty()?c.addParagraph():c.getParagraphs().get(0);
            while(!p.getRuns().isEmpty())p.removeRun(0);p.setSpacingBefore(0);p.setSpacingAfter(40);p.setKeepNext(header);
            XWPFRun r=p.createRun();r.setFontFamily("宋体");r.setFontSize(values.size()>7?8:9);r.setBold(header);
            String[] lines=values.get(i).split("\n",-1);for(int j=0;j<lines.length;j++){if(j>0)r.addBreak();r.setText(lines[j]);}
        }
    }
    private static void removeTable(XWPFDocument doc,XWPFTable table) {
        int pos=doc.getPosOfTable(table);if(pos<0)return;
        if(pos>0 && doc.getBodyElements().get(pos-1) instanceof XWPFParagraph p && (p.getText().trim().startsWith("表") || p.getText().trim().startsWith("检测结果"))) {
            doc.removeBodyElement(pos);doc.removeBodyElement(pos-1);
            if(pos>1 && doc.getBodyElements().get(pos-2) instanceof XWPFParagraph heading && heading.getText().trim().matches("(?:7\\.[123]\\.\\d+(?:\\.\\d+)?|4\\.2)\\s.*"))doc.removeBodyElement(pos-2);
        } else doc.removeBodyElement(pos);
    }
}
