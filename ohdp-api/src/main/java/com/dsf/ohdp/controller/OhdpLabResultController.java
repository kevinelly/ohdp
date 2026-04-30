package com.dsf.ohdp.controller;

import com.dsf.ohdp.dto.LabResultDto;
import com.dsf.ohdp.service.OhdpLabResultService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OHDP-LabResult")
@RestController
@RequestMapping("/api/ohdp/lab")
@RequiredArgsConstructor
public class OhdpLabResultController {

    private final OhdpLabResultService labResultService;

    // ─────────────────────────── 检测结果 CRUD ───────────────────────────

    @GetMapping("/results")
    public Page<LabResultDto.LabResultResponse> listResults(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String factorName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return labResultService.pageLabResults(taskId, factorName, page, size);
    }

    @GetMapping("/results/{id}")
    public LabResultDto.LabResultResponse getResult(@PathVariable Long id) {
        return labResultService.getLabResult(id);
    }

    @PostMapping("/results")
    public LabResultDto.LabResultResponse createResult(
            @Valid @RequestBody LabResultDto.LabResultRequest request) {
        return labResultService.createLabResult(request);
    }

    @PutMapping("/results/{id}")
    public LabResultDto.LabResultResponse updateResult(
            @PathVariable Long id,
            @RequestBody LabResultDto.LabResultUpdateRequest request) {
        return labResultService.updateLabResult(id, request);
    }

    @DeleteMapping("/results/{id}")
    public void deleteResult(@PathVariable Long id) {
        labResultService.deleteLabResult(id);
    }

    // ─────────────────────────── 曲线数据点 ─────────────────────────────

    @GetMapping("/results/{resultId}/curve-points")
    public List<LabResultDto.CurvePointResponse> listCurvePoints(@PathVariable Long resultId) {
        return labResultService.listCurvePoints(resultId);
    }

    @PostMapping("/results/{resultId}/curve-points")
    public List<LabResultDto.CurvePointResponse> replaceCurvePoints(
            @PathVariable Long resultId,
            @RequestBody List<@Valid LabResultDto.CurvePointRequest> points) {
        return labResultService.replaceCurvePoints(resultId, points);
    }

    @PutMapping("/curve-points/{pointId}")
    public LabResultDto.CurvePointResponse updateCurvePoint(
            @PathVariable Long pointId,
            @RequestBody LabResultDto.CurvePointUpdateRequest request) {
        return labResultService.updateCurvePoint(pointId, request);
    }

    // ─────────────────────────── 计算 API ────────────────────────────────

    @PostMapping("/results/{resultId}/calculate")
    public LabResultDto.CalcResponse calculate(@PathVariable Long resultId) {
        return labResultService.calculate(resultId);
    }

    @PostMapping("/results/{resultId}/calc-sample")
    public LabResultDto.CalcSampleResponse calcSample(
            @PathVariable Long resultId,
            @Valid @RequestBody LabResultDto.CalcSampleRequest request) {
        return labResultService.calcSample(resultId, request);
    }
}
