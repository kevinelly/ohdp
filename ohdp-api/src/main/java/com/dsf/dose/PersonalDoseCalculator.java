package com.dsf.dose;

import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;

/** Decimal strings preserve the significant figures in the laboratory's original readings. */
@Component
public class PersonalDoseCalculator {
    public static final String VERSION="2026.10.14-dose.1";
    public static String text(JsonNode n,String key){return n.path(key).asText("").trim();}
    public static void require(boolean ok,String message){if(!ok)throw new BusinessException(message);}
    public static BigDecimal decimal(JsonNode n,String label){
        try {String s=n.asText().trim();require(s.length()<=40,"数值过长："+label);BigDecimal v=new BigDecimal(s);require(Math.abs(v.scale())<=12 && v.precision()<=20,"数值精度超出范围："+label);return v;}
        catch(NumberFormatException e){throw new BusinessException("请输入有效数值："+label);}
    }
    public static LocalDate date(String s,String label){try{return LocalDate.parse(s);}catch(Exception e){throw new BusinessException("请填写有效日期："+label);}}
    public void validate(ObjectNode data,boolean complete){
        for(String key:List.of("receipt","unitName","detectionCategory","sampleName"))require(!text(data,key).isBlank(),"请填写受理编号、受检单位、检测类别和样品名称");
        require(text(data,"receipt").length()<=100 && text(data,"unitName").length()<=255,"受理编号或单位名称过长");
        for(String key:List.of("city","county"))require(text(data,key).length()<=100,"地市/县区名称过长");
        for(String key:List.of("inspectionDate","reportDate","receivedDate","distributionStart","distributionEnd","monitoringStart","monitoringEnd"))if(!text(data,key).isBlank())date(text(data,key),key);
        if(!text(data,"distributionStart").isBlank()&&!text(data,"distributionEnd").isBlank())require(!date(text(data,"distributionEnd"),"发放结束日期").isBefore(date(text(data,"distributionStart"),"发放开始日期")),"发放结束日期不能早于开始日期");
        require(data.toString().length()<1500000,"单个项目数据过大，请分批建立项目");
        require(data.path("personnel").isArray() && data.path("readings").isArray(),"人员和测量数据格式错误");
        require(data.path("personnel").size()<=1000 && data.path("readings").size()<=2000,"单个项目最多1000人、2000条测量记录");
        Map<String,String> people=new HashMap<>();
        for(JsonNode p:data.path("personnel")) {
            String no=text(p,"dosimeterNo"),name=text(p,"name");
            require(!no.isBlank() && !name.isBlank(),"人员信息须填写剂量计编号和姓名");
            require(people.putIfAbsent(no,name)==null,"人员剂量计编号重复："+no);
            if(complete || !text(p,"startDate").isBlank() || !text(p,"endDate").isBlank()) {
                LocalDate start=date(text(p,"startDate"),name+"佩戴起始日期"),end=date(text(p,"endDate"),name+"佩戴结束日期");
                require(!end.isBefore(start),name+"佩戴结束日期不能早于起始日期");
            }
        }
        Set<String> ids=new HashSet<>();
        for(JsonNode r:data.path("readings")) {
            String no=text(r,"dosimeterNo"),type=text(r,"sampleType");
            require(Set.of("TEST","BACKGROUND","QC").contains(type),"请选择样品类别");
            require(!no.isBlank() && ids.add(no),"测量记录剂量计编号为空或重复："+no);
            if(type.equals("TEST"))require(people.containsKey(no) && people.get(no).equals(text(r,"name")),"剂量计 "+no+" 对应的姓名与人员信息不一致，禁止录入");
            require(r.path("values").isArray() && r.path("values").size()<=50,"每个剂量计最多50个原始测量值");
            for(JsonNode v:r.path("values"))if(!v.asText().isBlank())require(decimal(v,no+"原始测量值").signum()>=0,"原始测量值不能为负数");
            if(!text(r,"cf").isBlank())require(decimal(r.get("cf"),"刻度因子Cf").signum()>0,"刻度因子Cf必须大于0");
        }
        if(complete) {
            for(String k:List.of("sampleName","monitoringItem","standardBasis","detector","inspectionDate","monitoringStart","monitoringEnd","cycle","mdl","investigationLevel"))require(!text(data,k).isBlank(),"请补齐样品、检测依据、检测人、日期、监测周期、MDL及调查水平");
            require(!data.path("personnel").isEmpty(),"请先录入监测人员");
            require(!date(text(data,"monitoringEnd"),"监测结束").isBefore(date(text(data,"monitoringStart"),"监测开始")),"监测结束日期不能早于开始日期");
            date(text(data,"inspectionDate"),"检测日期");
            require(decimal(data.get("mdl"),"MDL").signum()>=0,"MDL不能为负数");
            require(decimal(data.get("investigationLevel"),"调查水平").signum()>0,"调查水平须大于0");
            require(!text(data,"instrumentDescription").isBlank(),"请填写检测仪器及证书信息");
        }
    }
    public ObjectNode calculate(ObjectNode data) {
        validate(data,true);
        List<BigDecimal> bg=new ArrayList<>();
        for(JsonNode r:data.path("readings"))if(text(r,"sampleType").equals("BACKGROUND"))bg.addAll(values(r));
        require(!bg.isEmpty(),"请录入本底剂量计原始测量值");
        BigDecimal background=mean(bg);ArrayNode results=JsonNodeFactory.instance.arrayNode();Set<String> tested=new HashSet<>();
        for(JsonNode r:data.path("readings")) {
            List<BigDecimal> raw=values(r);BigDecimal avg=mean(raw);int precision=raw.stream().mapToInt(BigDecimal::precision).min().orElse(1);
            MathContext display=new MathContext(precision,RoundingMode.HALF_EVEN);
            ObjectNode result=results.addObject();result.put("dosimeterNo",text(r,"dosimeterNo"));result.put("sampleType",text(r,"sampleType"));result.put("name",text(r,"name"));
            result.put("mean",avg.round(display).toPlainString());
            if(text(r,"sampleType").equals("TEST")) {
                BigDecimal cf=decimal(r.path("cf"),"刻度因子Cf");require(cf.signum()>0,"刻度因子Cf必须大于0");
                BigDecimal net=avg.subtract(background),hp=net.multiply(cf),rounded=hp.setScale(2,RoundingMode.HALF_EVEN);
                result.put("net",net.round(display).toPlainString());result.put("hp10",rounded.toPlainString());
                result.put("belowMdl",rounded.compareTo(decimal(data.get("mdl"),"MDL"))<0);
                result.put("investigationRequired",rounded.compareTo(decimal(data.get("investigationLevel"),"调查水平"))>=0);
                tested.add(text(r,"dosimeterNo"));
            }
        }
        for(JsonNode p:data.path("personnel")) {
            require(tested.contains(text(p,"dosimeterNo")),"人员 "+text(p,"name")+" 缺少待测剂量计记录");
            ((ObjectNode)p).put("wearingDays",ChronoUnit.DAYS.between(date(text(p,"startDate"),"起始日期"),date(text(p,"endDate"),"结束日期"))+1);
        }
        ObjectNode output=JsonNodeFactory.instance.objectNode();output.put("version",VERSION);output.put("backgroundMean",background.stripTrailingZeros().toPlainString());output.set("rows",results);
        output.put("note","本底均值取本项目全部本底原始读数的平均值；中间量以未修约值参与计算，显示按原始读数最少有效位数，Hp(10)保留2位小数（四舍六入五成双），按两位小数报告值提示MDL及调查水平。佩戴天数包含起止两日。低于MDL或负净计数保留计算值并提示，未擅自替换为0。年度评价需全年资料。");
        return output;
    }
    private List<BigDecimal> values(JsonNode r){List<BigDecimal> vs=new ArrayList<>();for(JsonNode v:r.path("values")){require(!v.asText().isBlank(),"剂量计 "+text(r,"dosimeterNo")+" 有空白测量值");vs.add(decimal(v,"原始测量值"));}require(!vs.isEmpty(),"剂量计 "+text(r,"dosimeterNo")+" 缺少测量值");return vs;}
    private BigDecimal mean(List<BigDecimal> v){return v.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(v.size()),MathContext.DECIMAL128);}
}
