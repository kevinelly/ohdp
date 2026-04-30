package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.LabResultDto;
import java.util.List;
import org.springframework.data.domain.Page;

public interface OhdpLabResultService {

    // ── 检测结果 CRUD ──────────────────────────────────────────────────

    LabResultDto.LabResultResponse createLabResult(LabResultDto.LabResultRequest request);

    LabResultDto.LabResultResponse updateLabResult(Long id, LabResultDto.LabResultUpdateRequest request);

    LabResultDto.LabResultResponse getLabResult(Long id);

    Page<LabResultDto.LabResultResponse> pageLabResults(Long taskId, String factorName, int page, int size);

    void deleteLabResult(Long id);

    // ── 曲线数据点管理 ────────────────────────────────────────────────

    List<LabResultDto.CurvePointResponse> listCurvePoints(Long labResultId);

    List<LabResultDto.CurvePointResponse> replaceCurvePoints(Long labResultId, List<LabResultDto.CurvePointRequest> points);

    LabResultDto.CurvePointResponse updateCurvePoint(Long pointId, LabResultDto.CurvePointUpdateRequest request);

    // ── 计算 ──────────────────────────────────────────────────────────

    LabResultDto.CalcResponse calculate(Long labResultId);

    LabResultDto.CalcSampleResponse calcSample(Long labResultId, LabResultDto.CalcSampleRequest request);
}
