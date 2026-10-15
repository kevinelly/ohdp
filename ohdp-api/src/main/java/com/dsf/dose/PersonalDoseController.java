package com.dsf.dose;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.data.domain.Page;
import java.util.*;

@RestController @RequestMapping("/api/personal-dose") @RequiredArgsConstructor
public class PersonalDoseController {
    private final PersonalDoseService service;
    private final PersonalDoseImportService imports;
    @GetMapping("/cases") public Page<Map<String,Object>> list(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(keyword,status,page,size);}
    @GetMapping("/cases/{id}") public ObjectNode get(@PathVariable Long id){return service.get(id);}
    @PostMapping("/cases") public ObjectNode create(@RequestBody ObjectNode d){return service.save(null,d);}
    @PutMapping("/cases/{id}") public ObjectNode save(@PathVariable Long id,@RequestBody ObjectNode d){return service.save(id,d);}
    @PostMapping("/cases/{id}/revision") public ObjectNode revision(@PathVariable Long id,@RequestBody ObjectNode d){return service.revision(id,d);}
    @PostMapping("/cases/{id}/actions/{action}") public ObjectNode action(@PathVariable Long id,@PathVariable String action,@RequestBody ObjectNode d){return service.action(id,action,d);}
    @DeleteMapping("/cases/{id}") public void delete(@PathVariable Long id,@RequestParam Long version){service.delete(id,version);}
    @GetMapping("/cases/{id}/report") public ResponseEntity<byte[]> report(@PathVariable Long id,@RequestParam(defaultValue="false") boolean preview){return file(service.report(id,preview),service.reportName(id),"application/vnd.openxmlformats-officedocument.wordprocessingml.document");}
    @GetMapping("/cases/{id}/reports") public List<Map<String,Object>> reports(@PathVariable Long id){return service.reports(id);}
    @GetMapping("/cases/{id}/preview-pages") public List<String> pages(@PathVariable Long id){return service.previewPages(id);}
    @GetMapping("/import-template/{kind}") public ResponseEntity<byte[]> template(@PathVariable String kind){return file(imports.template(kind),"personal-dose-"+kind+".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");}
    @PostMapping("/import/{kind}") public ObjectNode upload(@PathVariable String kind,@RequestParam(required=false) Long id,@RequestParam(required=false) Long version,@RequestParam(defaultValue="true") boolean dryRun,@RequestPart("file") MultipartFile file){return imports.importFile(kind,id,version,file,dryRun);}
    private ResponseEntity<byte[]> file(byte[] b,String name,String type){return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(b);}
}
