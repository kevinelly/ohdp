package com.dsf.dose;

import com.dsf.auth.security.AuthContext;
import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.service.RadiologyCaseService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;
import static com.dsf.dose.PersonalDoseCalculator.*;

@Service @RequiredArgsConstructor @Transactional
public class PersonalDoseService {
    private final PersonalDoseRepository repository;
    private final PersonalDoseCalculator calculator;
    private final PersonalDoseReportComposer composer;
    private final ObjectMapper mapper;
    private final RadiologyCaseService permissions;
    private final com.dsf.radiology.service.RadiologyPdfPreviewService previews;
    public ObjectNode read(String json){try{return (ObjectNode)mapper.readTree(json);}catch(Exception e){throw new BusinessException("项目数据读取失败");}}
    private PersonalDoseCase entity(Long id){return repository.findById(id).filter(e->!Boolean.TRUE.equals(e.getDeleted())).orElseThrow(()->new BusinessException("个人剂量项目不存在"));}
    private ObjectNode view(PersonalDoseCase e){ObjectNode d=read(e.getPayload());d.put("id",e.getId());d.put("version",e.getVersion());d.put("status",e.getStatus());d.put("sourceCaseId",e.getSourceCaseId());d.put("reportSha256",e.getReportSha256());d.put("reportFileName",reportName(e));try{d.set("history",mapper.readTree(e.getHistory()));}catch(Exception ex){throw new BusinessException("流程记录读取失败");}return d;}
    public ObjectNode get(Long id){return view(entity(id));}
    public Page<Map<String,Object>> list(String keyword,String status,int page,int size){
        return repository.findAll((root,q,b)->{var clauses=new ArrayList<jakarta.persistence.criteria.Predicate>();clauses.add(b.isFalse(root.get("deleted")));if(keyword!=null&&!keyword.isBlank()){String escaped=keyword.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");clauses.add(b.or(b.like(root.get("receipt"),"%"+escaped+"%",'\\'),b.like(root.get("unitName"),"%"+escaped+"%",'\\')));}if(status!=null&&!status.isBlank())clauses.add(b.equal(root.get("status"),status));return b.and(clauses.toArray(new jakarta.persistence.criteria.Predicate[0]));},PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by("id").descending())).map(e->Map.<String,Object>of("id",e.getId(),"receipt",e.getReceipt(),"unitName",e.getUnitName(),"status",e.getStatus(),"updatedAt",e.getUpdatedAt(),"version",e.getVersion()));
    }
    private void editable(PersonalDoseCase e){require(Set.of("DRAFT","DATA_ENTERED","CALCULATED").contains(e.getStatus()),"已提交审核的项目不能修改；请退回或建立修订版");}
    private void version(PersonalDoseCase e,JsonNode d){require(d.has("version") && d.path("version").canConvertToLong() && Objects.equals(e.getVersion(),d.path("version").asLong()),"项目已被其他人员修改，请刷新后重试");}
    public ObjectNode save(Long id,ObjectNode request){
        ObjectNode d=request.deepCopy();d.remove(List.of("id","version","status","history","reportSha256","reportFileName","sourceCaseId","calculation"));calculator.validate(d,false);
        PersonalDoseCase e=id==null?new PersonalDoseCase():entity(id);if(id!=null){editable(e);version(e,request);}
        index(e,d);e.setReceipt(text(d,"receipt"));e.setUnitName(text(d,"unitName"));e.setPayload(d.toString());e.setStatus(d.path("readings").isEmpty()?"DRAFT":"DATA_ENTERED");history(e,id==null?"创建":"保存","数据修改后需重新计算");repository.saveAndFlush(e);return view(e);
    }
    public ObjectNode action(Long id,String action,ObjectNode request){
        PersonalDoseCase e=entity(id);version(e,request);ObjectNode d=read(e.getPayload());String comment=text(request,"comment");
        switch(action){
            case "calculate"->{editable(e);d.set("calculation",calculator.calculate(d));e.setPayload(d.toString());e.setStatus("CALCULATED");}
            case "submit"->{require(e.getStatus().equals("CALCULATED"),"请保存并重新计算后提交复核");require(VERSION.equals(d.path("calculation").path("version").asText()),"计算规则已更新，请重新计算");require(!text(d,"conclusion").isBlank(),"请填写本周期结果评价后提交复核");e.setStatus("PENDING_REVIEW");}
            case "review"->{permissions.requirePermission("RADIOLOGY:WORKFLOW:REVIEW");require(e.getStatus().equals("PENDING_REVIEW"),"当前项目不在待复核阶段");e.setStatus("REVIEWED");}
            case "approve"->{permissions.requirePermission("RADIOLOGY:WORKFLOW:APPROVE");require(e.getStatus().equals("REVIEWED"),"请先完成复核");e.setApprovedSnapshot(d.toString());e.setReport(composer.compose(d,false));e.setStatus("APPROVED");}
            case "return"->{permissions.requirePermission(e.getStatus().equals("PENDING_REVIEW")?"RADIOLOGY:WORKFLOW:REVIEW":"RADIOLOGY:WORKFLOW:APPROVE");require(Set.of("PENDING_REVIEW","REVIEWED").contains(e.getStatus()),"当前阶段不支持退回");require(!comment.isBlank(),"请填写退回原因");d.remove("calculation");e.setPayload(d.toString());e.setStatus("DATA_ENTERED");}
            case "archive"->{permissions.requirePermission("RADIOLOGY:REPORT:GENERATE");require(e.getStatus().equals("APPROVED"),"仅批准项目可生成归档报告");byte[] report=e.getReport();require(report!=null,"批准报告快照缺失，请建立修订版");e.setReport(report);try{e.setReportSha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(report)));}catch(Exception ex){throw new BusinessException("报告校验失败");}e.setGeneratedAt(LocalDateTime.now());e.setGeneratedBy(actor());e.setReportFileName(newReportName(e));e.setStatus("REPORTED");}
            default->throw new BusinessException("未知流程操作");
        }
        history(e,action,comment);repository.saveAndFlush(e);return view(e);
    }
    public ObjectNode revision(Long id,ObjectNode req){PersonalDoseCase e=entity(id);version(e,req);require(Set.of("APPROVED","REPORTED").contains(e.getStatus()),"批准后才能建立修订版");ObjectNode d=read(e.getPayload());d.remove("calculation");PersonalDoseCase n=new PersonalDoseCase();index(n,d);n.setSourceCaseId(id);n.setReceipt(e.getReceipt());n.setUnitName(e.getUnitName());n.setPayload(d.toString());history(n,"创建修订版","源项目ID："+id);repository.saveAndFlush(n);return view(n);}
    public void delete(Long id,Long version){PersonalDoseCase e=entity(id);editable(e);require(Objects.equals(version,e.getVersion()),"项目已更新，请刷新");e.setDeleted(true);history(e,"删除","");repository.saveAndFlush(e);}
    public byte[] report(Long id,boolean preview){PersonalDoseCase e=entity(id);if(!preview){permissions.requirePermission("RADIOLOGY:REPORT:GENERATE");require(e.getStatus().equals("REPORTED") && e.getReport()!=null,"请先生成归档报告");return e.getReport();}return e.getReport()!=null?e.getReport():composer.compose(read(e.getApprovedSnapshot()!=null?e.getApprovedSnapshot():e.getPayload()),e.getApprovedSnapshot()==null);}
    public List<String> previewPages(Long id){return previews.renderPages(previews.convert(report(id,true)));}
    static void index(PersonalDoseCase e,JsonNode d) {
        e.setCity(text(d,"city"));e.setCounty(text(d,"county"));
        e.setInspectionDate(text(d,"inspectionDate").isBlank()?null:date(text(d,"inspectionDate"),"检测日期"));e.setQueryIndexed(true);
    }
    private String actor(){return AuthContext.get()==null?"system":AuthContext.get().getUsername();}
    private String safe(String s){return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");}
    private String newReportName(PersonalDoseCase e){
        JsonNode d=read(e.getPayload());String stamp=Optional.ofNullable(e.getGeneratedAt()).orElse(e.getUpdatedAt()).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "检测报告--"+safe(e.getReceipt())+"[个人剂量检测（第"+safe(text(d,"cycle"))+"周期）-"+safe(e.getUnitName())+"]."+stamp+"-R"+e.getId()+".docx";
    }
    private String reportName(PersonalDoseCase e){return e.getReportFileName()!=null?e.getReportFileName():newReportName(e);}
    public String reportName(Long id){return reportName(entity(id));}
    public List<Map<String,Object>> reports(Long id){
        PersonalDoseCase root=entity(id);Set<Long> visited=new HashSet<>();
        while(root.getSourceCaseId()!=null && visited.add(root.getId())) root=entity(root.getSourceCaseId());
        var queue=new ArrayDeque<PersonalDoseCase>();queue.add(root);visited.clear();var result=new ArrayList<Map<String,Object>>();
        while(!queue.isEmpty()) {var e=queue.remove();if(!visited.add(e.getId()))continue;
            if(e.getStatus().equals("REPORTED")){var row=new LinkedHashMap<String,Object>();row.put("caseId",e.getId());row.put("reportNo",e.getReceipt());row.put("fileName",reportName(e));row.put("generatedAt",e.getGeneratedAt());row.put("generatedBy",e.getGeneratedBy());row.put("sha256",e.getReportSha256());result.add(row);}
            queue.addAll(repository.findBySourceCaseIdAndDeletedFalseOrderByIdAsc(e.getId()));
        }
        result.sort(Comparator.comparingLong((Map<String,Object> v)->((Number)v.get("caseId")).longValue()).reversed());return result;
    }
    private void history(PersonalDoseCase e,String action,String comment){try{ArrayNode a=(ArrayNode)mapper.readTree(e.getHistory());ObjectNode row=a.addObject();row.put("action",action);row.put("actor",AuthContext.get()==null?"system":AuthContext.get().getUsername());row.put("at",LocalDateTime.now().toString());row.put("comment",comment);e.setHistory(a.toString());}catch(Exception ex){throw new BusinessException("流程记录保存失败");}}
}
