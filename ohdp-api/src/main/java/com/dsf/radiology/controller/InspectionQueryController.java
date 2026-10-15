package com.dsf.radiology.controller;

import com.dsf.dose.*;
import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.model.*;
import com.dsf.radiology.repository.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/inspection-query") @RequiredArgsConstructor
public class InspectionQueryController {
    private final RadiologyCaseRepository radiology;
    private final RadiologyReportRepository reports;
    private final PersonalDoseRepository dose;
    public record Row(Long id,String modality,String receipt,String unitName,String equipmentName,String equipmentNo,
            LocalDate inspectionDate,String status,String city,String county,LocalDateTime generatedAt,LocalDateTime updatedAt) {}
    @GetMapping @Transactional(readOnly=true)
    public Page<Row> query(@RequestParam(required=false) List<String> modalities,
            @RequestParam(required=false) String status,@RequestParam(required=false) Integer year,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String city,@RequestParam(required=false) String county,
            @RequestParam(required=false) String receipt,@RequestParam(required=false) String company,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        if(page<0 || page>10000 || size<1 || size>100)throw new BusinessException("查询分页参数超出范围");
        if(year!=null && (year<1900 || year>2200))throw new BusinessException("检测年份超出范围");
        if(from!=null && to!=null && from.isAfter(to))throw new BusinessException("查询结束日期不能早于开始日期");
        var types=modalities==null||modalities.isEmpty()?Set.of("CT","DR","DOSE"):new HashSet<>(modalities);
        if(!Set.of("CT","DR","DOSE").containsAll(types))throw new BusinessException("不支持的检测类别");
        RadiologyCaseStatus state=null;if(status!=null&&!status.isBlank())try{state=RadiologyCaseStatus.valueOf(status);}catch(IllegalArgumentException e){throw new BusinessException("报告状态无效");}
        final var selectedState=state;
        var requested=PageRequest.of(page,size);int top=(page+1)*size;
        var first=PageRequest.of(0,top,Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("id")));
        List<Row> merged=new ArrayList<>();long total=0;
        var machineTypes=types.stream().filter(t->!t.equals("DOSE")).map(RadiologyModality::valueOf).toList();
        if(!machineTypes.isEmpty()) {
            Specification<RadiologyCase> spec=(root,q,b)->{var p=filters(root,b,year,from,to,city,county,receipt,company,"sampleAcceptanceNo","inspectedUnitName",true);p.add(root.get("modality").in(machineTypes));if(selectedState!=null)p.add(b.equal(root.get("status"),selectedState));return b.and(p.toArray(Predicate[]::new));};
            var result=radiology.findAll(spec,first);total+=result.getTotalElements();
            for(var e:result)merged.add(new Row(e.getId(),e.getModality().name(),e.getSampleAcceptanceNo(),e.getInspectedUnitName(),e.getEquipmentName(),e.getEquipmentNo(),e.getInspectionDate(),e.getStatus().name(),e.getCity(),e.getCounty(),null,e.getUpdatedAt()));
        }
        if(types.contains("DOSE")) {
            Specification<PersonalDoseCase> spec=(root,q,b)->{var p=filters(root,b,year,from,to,city,county,receipt,company,"receipt","unitName",false);if(selectedState!=null)p.add(b.equal(root.get("status"),selectedState.name()));return b.and(p.toArray(Predicate[]::new));};
            var result=dose.findAll(spec,first);total+=result.getTotalElements();
            for(var e:result)merged.add(new Row(e.getId(),"DOSE",e.getReceipt(),e.getUnitName(),null,null,e.getInspectionDate(),e.getStatus(),e.getCity(),e.getCounty(),e.getGeneratedAt(),e.getUpdatedAt()));
        }
        merged.sort(Comparator.comparing(Row::updatedAt).reversed().thenComparing(Row::id,Comparator.reverseOrder()).thenComparing(Row::modality));
        var content=new ArrayList<Row>();
        for(var row:merged.subList(Math.min(page*size,merged.size()),Math.min(top,merged.size()))) {
            if(!row.modality().equals("DOSE")){var at=reports.findFirstByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(row.id()).map(com.dsf.radiology.entity.RadiologyReport::getGeneratedAt).orElse(null);row=new Row(row.id(),row.modality(),row.receipt(),row.unitName(),row.equipmentName(),row.equipmentNo(),row.inspectionDate(),row.status(),row.city(),row.county(),at,row.updatedAt());}
            content.add(row);
        }
        return new PageImpl<>(content,requested,total);
    }
    private List<Predicate> filters(Root<?> root,CriteriaBuilder b,Integer year,LocalDate from,LocalDate to,String city,String county,String receipt,String company,String receiptField,String companyField,boolean machine) {
        var p=new ArrayList<Predicate>();p.add(b.isFalse(root.get("deleted")));
        String[] fields={"city","county",receiptField,companyField},values={city,county,receipt,company};
        for(int i=0;i<fields.length;i++)if(values[i]!=null&&!values[i].isBlank()){String v=values[i].trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_");p.add(b.like(root.get(fields[i]),"%"+v+"%",'\\'));}
        LocalDate lower=from,upper=to;
        if(year!=null){var start=LocalDate.of(year,1,1);var end=LocalDate.of(year,12,31);if(lower==null||lower.isBefore(start))lower=start;if(upper==null||upper.isAfter(end))upper=end;}
        if(lower!=null && upper!=null && lower.isAfter(upper))p.add(b.disjunction());
        Expression<LocalDate> end=machine?b.coalesce(root.get("inspectionEndDate"),root.get("inspectionDate")):root.get("inspectionDate");
        if(lower!=null)p.add(b.greaterThanOrEqualTo(end,lower));if(upper!=null)p.add(b.lessThanOrEqualTo(root.get("inspectionDate"),upper));return p;
    }
}
