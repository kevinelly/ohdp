package com.dsf.radiology.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.RadiologyJudgement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Each condition row keeps its own raw readings, precision and assessment. */
@Component @RequiredArgsConstructor
public class RadiologyMeasurementRows {
    public static final Set<String> ITEMS=Set.of("TVD","ROA","ETD","HCR","LCR","RST");
    private final ObjectMapper mapper;
    private final RadiologyCalculationEngine engine;
    private final RadiologyStandardEvaluator evaluator;
    private final RadiologyScopeService scope;
    public record RowResult(int rowId,String condition,String result,String judgement,String rule,boolean complete,String message) {}
    public record Result(RadiologyCalculationEngine.CalculationResult calculation,RadiologyStandardEvaluator.Evaluation evaluation) {}
    public List<Integer> ids(String code,Map<String,String> conditions) {
        String json=conditions.get("__rows."+code);
        if(json==null || !ITEMS.contains(code)) return List.of();
        try {
            var tree=mapper.readTree(json);var ids=new ArrayList<Integer>();
            if(!tree.isArray() || tree.isEmpty() || tree.size()>30) throw new IllegalArgumentException();
            for(var value:tree) {
                if(!value.isIntegralNumber() || value.intValue()<1 || value.intValue()>999 || ids.contains(value.intValue())) throw new IllegalArgumentException();
                ids.add(value.intValue());
            }
            return ids;
        } catch(Exception ex) {throw new BusinessException(code+"测量行应为1至30个不同的有效行号");}
    }
    public Result calculate(RadiologyCase c,String code,Map<String,BigDecimal> numbers,Map<String,String> texts,
            Map<String,Map<String,BigDecimal>> allNumbers,Map<String,Map<String,String>> allTexts,Map<String,String> conditions) {
        var ids=ids(code,conditions);if(ids.isEmpty()) return null;
        var output=new ArrayList<RadiologyCalculationEngine.CalculatedField>();var rows=new ArrayList<RowResult>();
        for(int id:ids) {
            String prefix="R"+id+"__";var n=new LinkedHashMap<String,BigDecimal>();var t=new LinkedHashMap<String,String>();
            numbers.forEach((k,v)->{if(k.startsWith(prefix))n.put(k.substring(prefix.length()),v);else if(k.startsWith("__"))n.put(k,v);});
            texts.forEach((k,v)->{if(k.startsWith(prefix))t.put(k.substring(prefix.length()),v);});
            n.put("__GROUP_COUNT",BigDecimal.ONE);
            String condition=conditions.getOrDefault(code+".R"+id,conditions.getOrDefault(code,""));
            if(condition.isBlank()) condition=conditions.getOrDefault(code,"");
            String excluded=scope.exclusion(c,code,n);
            if(!excluded.isEmpty()) {rows.add(new RowResult(id,condition,"不检测","NOT_APPLICABLE",excluded,true,excluded));continue;}
            var result=engine.calculate(code,n,t,allNumbers,allTexts);
            var evaluation=evaluator.evaluate(c.getModality(),c.getDetectionCategory(),code,result.complete(),result.resultValue(),result.resultText(),n,result.fields());
            String display=result.resultText();
            if(display==null) display=result.fields().stream().filter(f->f.code().equals("RESULT")).findFirst()
                .map(f->f.displayValue()+(f.unit().isBlank()?"":" "+f.unit())).orElse(result.message());
            if(List.of("TVD","ETD").contains(code))display=display.replace("第1组 ","");
            rows.add(new RowResult(id,condition,display,evaluation.judgement().name(),evaluation.description().replace("第1组","第"+id+"行"),result.complete(),result.complete()?"计算完成":result.message()));
            for(var f:result.fields()) output.add(new RadiologyCalculationEngine.CalculatedField(prefix+f.code(),"第"+id+"行 "+f.label().replace("第1组",""),f.valueNum(),f.valueText()==null?null:f.valueText().replace("第1组 ",""),f.unit(),f.displayScale()));
        }
        boolean complete=rows.stream().allMatch(RowResult::complete);
        var priority=List.of("INCOMPLETE","NOT_CONFIGURED","NEEDS_ASSESSMENT","UNQUALIFIED","QUALIFIED","BASELINE_ESTABLISHED","NOT_APPLICABLE");
        String judgement=priority.stream().filter(j->rows.stream().anyMatch(r->r.judgement().equals(j))).findFirst().orElse("INCOMPLETE");
        String summary=rows.stream().map(r->"第"+r.rowId()+"行："+r.result()).collect(java.util.stream.Collectors.joining("；"));
        String message=rows.stream().filter(r->!r.complete()).map(r->"第"+r.rowId()+"行："+r.message()).collect(java.util.stream.Collectors.joining("；"));
        try {output.add(RadiologyCalculationEngine.CalculatedField.text("ROWS_JSON","分行评价",mapper.writeValueAsString(rows)));}
        catch(Exception ex){throw new BusinessException("分行结果保存失败");}
        return new Result(new RadiologyCalculationEngine.CalculationResult(code,complete,message,null,summary,"",output),
            new RadiologyStandardEvaluator.Evaluation(RadiologyJudgement.valueOf(judgement),"按各测量行条件分别评价，详见分行结果"));
    }
}
