package com.dsf.ohdp.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.ohdp.dto.LabResultDto;
import com.dsf.ohdp.entity.OhdpLabCurvePoint;
import com.dsf.ohdp.entity.OhdpLabResult;
import com.dsf.ohdp.repository.OhdpLabCurvePointRepository;
import com.dsf.ohdp.repository.OhdpLabResultRepository;
import com.dsf.ohdp.util.LinearRegressionUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OhdpLabResultServiceImpl implements OhdpLabResultService {

    private final OhdpLabResultRepository labResultRepository;
    private final OhdpLabCurvePointRepository curvePointRepository;

    // ─────────────────────────────── 检测结果 CRUD ──────────────────────────

    @Override
    public LabResultDto.LabResultResponse createLabResult(LabResultDto.LabResultRequest request) {
        OhdpLabResult entity = new OhdpLabResult();
        applyRequest(entity, request);
        OhdpLabResult saved = labResultRepository.save(entity);

        if (request.getCurvePoints() != null && !request.getCurvePoints().isEmpty()) {
            saveCurvePointEntities(saved.getId(), request.getCurvePoints());
        }

        return toResponse(saved, null);
    }

    @Override
    public LabResultDto.LabResultResponse updateLabResult(Long id, LabResultDto.LabResultUpdateRequest request) {
        OhdpLabResult entity = requireLabResult(id);
        applyUpdateRequest(entity, request);
        OhdpLabResult saved = labResultRepository.save(entity);
        return toResponse(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LabResultDto.LabResultResponse getLabResult(Long id) {
        OhdpLabResult entity = requireLabResult(id);
        List<OhdpLabCurvePoint> points =
                curvePointRepository.findByLabResultIdAndDeletedFalseOrderByPointIndexAsc(id);
        return toResponse(entity, points);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LabResultDto.LabResultResponse> pageLabResults(Long taskId, String factorName, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<OhdpLabResult> resultPage;
        boolean hasTask = taskId != null;
        boolean hasFactor = factorName != null && !factorName.isBlank();

        if (hasTask && hasFactor) {
            resultPage = labResultRepository.findByTaskIdAndFactorNameContainingAndDeletedFalse(taskId, factorName, pageable);
        } else if (hasTask) {
            resultPage = labResultRepository.findByTaskIdAndDeletedFalse(taskId, pageable);
        } else if (hasFactor) {
            resultPage = labResultRepository.findByFactorNameContainingAndDeletedFalse(factorName, pageable);
        } else {
            resultPage = labResultRepository.findByDeletedFalse(pageable);
        }

        return resultPage.map(e -> toResponse(e, null));
    }

    @Override
    public void deleteLabResult(Long id) {
        OhdpLabResult entity = requireLabResult(id);
        entity.setDeleted(true);
        labResultRepository.save(entity);
    }

    // ─────────────────────────────── 曲线数据点管理 ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<LabResultDto.CurvePointResponse> listCurvePoints(Long labResultId) {
        requireLabResult(labResultId);
        return curvePointRepository
                .findByLabResultIdAndDeletedFalseOrderByPointIndexAsc(labResultId)
                .stream()
                .map(this::toCurvePointResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<LabResultDto.CurvePointResponse> replaceCurvePoints(
            Long labResultId, List<LabResultDto.CurvePointRequest> points) {
        requireLabResult(labResultId);

        // 软删除所有旧数据点
        List<OhdpLabCurvePoint> existing =
                curvePointRepository.findByLabResultIdAndDeletedFalse(labResultId);
        existing.forEach(p -> p.setDeleted(true));
        curvePointRepository.saveAll(existing);

        // 保存新数据点
        List<OhdpLabCurvePoint> saved = saveCurvePointEntities(labResultId, points);
        return saved.stream().map(this::toCurvePointResponse).collect(Collectors.toList());
    }

    @Override
    public LabResultDto.CurvePointResponse updateCurvePoint(
            Long pointId, LabResultDto.CurvePointUpdateRequest request) {
        OhdpLabCurvePoint point = curvePointRepository
                .findByIdAndDeletedFalse(pointId)
                .orElseThrow(() -> new BusinessException("曲线数据点不存在: " + pointId));
        if (request.getPointIndex() != null) point.setPointIndex(request.getPointIndex());
        if (request.getConcentration() != null) point.setConcentration(request.getConcentration());
        if (request.getAbsorbance() != null) point.setAbsorbance(request.getAbsorbance());
        if (request.getAbsorbanceRaw() != null) point.setAbsorbanceRaw(request.getAbsorbanceRaw());
        if (request.getIsExcluded() != null) point.setIsExcluded(request.getIsExcluded());
        return toCurvePointResponse(curvePointRepository.save(point));
    }

    // ─────────────────────────────── 计算 ────────────────────────────────────

    @Override
    public LabResultDto.CalcResponse calculate(Long labResultId) {
        OhdpLabResult labResult = requireLabResult(labResultId);

        List<OhdpLabCurvePoint> activePoints =
                curvePointRepository.findByLabResultIdAndIsExcludedFalseAndDeletedFalseOrderByPointIndexAsc(labResultId);

        if (activePoints.size() < 2) {
            throw new BusinessException("有效数据点不足（至少需要2个未排除的数据点）");
        }

        List<double[]> xyPairs = activePoints.stream()
                .map(p -> new double[]{
                        p.getConcentration() != null ? p.getConcentration().doubleValue() : 0.0,
                        p.getAbsorbance() != null ? p.getAbsorbance().doubleValue() : 0.0
                })
                .collect(Collectors.toList());

        LinearRegressionUtil.RegressionResult regression = LinearRegressionUtil.calculate(xyPairs);

        labResult.setInterceptA(regression.intercept());
        labResult.setSlopeB(regression.slope());
        labResult.setCorrelationR(regression.correlation());
        labResultRepository.save(labResult);

        List<LabResultDto.CurvePointResponse> pointResponses =
                curvePointRepository.findByLabResultIdAndDeletedFalseOrderByPointIndexAsc(labResultId)
                        .stream()
                        .map(this::toCurvePointResponse)
                        .collect(Collectors.toList());

        return LabResultDto.CalcResponse.builder()
                .labResultId(labResultId)
                .interceptA(regression.intercept())
                .slopeB(regression.slope())
                .correlationR(regression.correlation())
                .curvePoints(pointResponses)
                .build();
    }

    @Override
    public LabResultDto.CalcSampleResponse calcSample(Long labResultId, LabResultDto.CalcSampleRequest request) {
        OhdpLabResult labResult = requireLabResult(labResultId);

        if (labResult.getInterceptA() == null || labResult.getSlopeB() == null) {
            throw new BusinessException("请先执行标准曲线回归计算");
        }
        if (labResult.getSlopeB().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("斜率b不能为零");
        }

        BigDecimal desorptionVolume = labResult.getDesorptionVolume() != null
                ? labResult.getDesorptionVolume() : BigDecimal.ONE;
        BigDecimal desorptionEfficiency = labResult.getDesorptionEfficiency() != null
                ? labResult.getDesorptionEfficiency() : BigDecimal.valueOf(100.0);
        BigDecimal samplingVol = request.getSamplingVolume();
        int calcScale = labResult.getCalcDecimalPlaces() != null ? labResult.getCalcDecimalPlaces() : 4;
        int resultScale = labResult.getResultDecimalPlaces() != null ? labResult.getResultDecimalPlaces() : 4;

        // x = (y - a) / b
        double y = request.getAbsorbance().doubleValue();
        double a = labResult.getInterceptA().doubleValue();
        double b = labResult.getSlopeB().doubleValue();
        double x = (y - a) / b;
        BigDecimal concentrationX = BigDecimal.valueOf(x).setScale(calcScale, RoundingMode.HALF_UP);

        // C_sample = x * desorptionVolume / (desorptionEfficiency / 100)
        double cSampleRaw = x * desorptionVolume.doubleValue() / (desorptionEfficiency.doubleValue() / 100.0);
        BigDecimal concentrationSample = BigDecimal.valueOf(cSampleRaw).setScale(calcScale, RoundingMode.HALF_UP);

        // C_air = C_sample * 1000 / samplingVolume
        double cAirRaw = cSampleRaw * 1000.0 / samplingVol.doubleValue();
        BigDecimal concentrationAir = BigDecimal.valueOf(cAirRaw).setScale(resultScale, RoundingMode.HALF_UP);

        labResult.setResultValue(concentrationAir);
        labResult.setSamplingVolume(samplingVol);
        labResultRepository.save(labResult);

        return LabResultDto.CalcSampleResponse.builder()
                .labResultId(labResultId)
                .concentrationX(concentrationX)
                .concentrationSample(concentrationSample)
                .concentrationAir(concentrationAir)
                .resultValue(concentrationAir)
                .resultUnit(labResult.getResultUnit())
                .build();
    }

    // ─────────────────────────────── 私有辅助方法 ────────────────────────────

    private OhdpLabResult requireLabResult(Long id) {
        return labResultRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("检测结果不存在: " + id));
    }

    private void applyRequest(OhdpLabResult entity, LabResultDto.LabResultRequest req) {
        entity.setTaskId(req.getTaskId());
        entity.setSampleId(req.getSampleId());
        entity.setFactorId(req.getFactorId());
        entity.setFactorName(req.getFactorName());
        entity.setMethodId(req.getMethodId());
        entity.setMethodName(req.getMethodName());
        if (req.getDesorptionVolume() != null) entity.setDesorptionVolume(req.getDesorptionVolume());
        if (req.getDesorptionEfficiency() != null) entity.setDesorptionEfficiency(req.getDesorptionEfficiency());
        entity.setSamplingVolume(req.getSamplingVolume());
        if (req.getResultDecimalPlaces() != null) entity.setResultDecimalPlaces(req.getResultDecimalPlaces());
        if (req.getCalcDecimalPlaces() != null) entity.setCalcDecimalPlaces(req.getCalcDecimalPlaces());
        entity.setResultUnit(req.getResultUnit());
        entity.setMinDetectValue(req.getMinDetectValue());
        entity.setMinQuantifyValue(req.getMinQuantifyValue());
        entity.setAnalystName(req.getAnalystName());
        entity.setAnalysisDate(req.getAnalysisDate());
        entity.setNotes(req.getNotes());
    }

    private void applyUpdateRequest(OhdpLabResult entity, LabResultDto.LabResultUpdateRequest req) {
        if (req.getSampleId() != null) entity.setSampleId(req.getSampleId());
        if (req.getFactorId() != null) entity.setFactorId(req.getFactorId());
        if (req.getFactorName() != null) entity.setFactorName(req.getFactorName());
        if (req.getMethodId() != null) entity.setMethodId(req.getMethodId());
        if (req.getMethodName() != null) entity.setMethodName(req.getMethodName());
        if (req.getDesorptionVolume() != null) entity.setDesorptionVolume(req.getDesorptionVolume());
        if (req.getDesorptionEfficiency() != null) entity.setDesorptionEfficiency(req.getDesorptionEfficiency());
        if (req.getSamplingVolume() != null) entity.setSamplingVolume(req.getSamplingVolume());
        if (req.getResultDecimalPlaces() != null) entity.setResultDecimalPlaces(req.getResultDecimalPlaces());
        if (req.getCalcDecimalPlaces() != null) entity.setCalcDecimalPlaces(req.getCalcDecimalPlaces());
        if (req.getResultUnit() != null) entity.setResultUnit(req.getResultUnit());
        if (req.getMinDetectValue() != null) entity.setMinDetectValue(req.getMinDetectValue());
        if (req.getMinQuantifyValue() != null) entity.setMinQuantifyValue(req.getMinQuantifyValue());
        if (req.getAnalystName() != null) entity.setAnalystName(req.getAnalystName());
        if (req.getAnalysisDate() != null) entity.setAnalysisDate(req.getAnalysisDate());
        if (req.getNotes() != null) entity.setNotes(req.getNotes());
    }

    private List<OhdpLabCurvePoint> saveCurvePointEntities(
            Long labResultId, List<LabResultDto.CurvePointRequest> requests) {
        List<OhdpLabCurvePoint> entities = requests.stream().map(req -> {
            OhdpLabCurvePoint p = new OhdpLabCurvePoint();
            p.setLabResultId(labResultId);
            p.setPointIndex(req.getPointIndex());
            p.setConcentration(req.getConcentration());
            p.setAbsorbance(req.getAbsorbance());
            p.setAbsorbanceRaw(req.getAbsorbanceRaw());
            p.setIsExcluded(req.getIsExcluded() != null ? req.getIsExcluded() : false);
            return p;
        }).collect(Collectors.toList());
        return curvePointRepository.saveAll(entities);
    }

    private LabResultDto.LabResultResponse toResponse(OhdpLabResult e, List<OhdpLabCurvePoint> points) {
        List<LabResultDto.CurvePointResponse> curvePointResponses = points == null ? null
                : points.stream().map(this::toCurvePointResponse).collect(Collectors.toList());
        return LabResultDto.LabResultResponse.builder()
                .id(e.getId())
                .taskId(e.getTaskId())
                .sampleId(e.getSampleId())
                .factorId(e.getFactorId())
                .factorName(e.getFactorName())
                .methodId(e.getMethodId())
                .methodName(e.getMethodName())
                .interceptA(e.getInterceptA())
                .slopeB(e.getSlopeB())
                .correlationR(e.getCorrelationR())
                .desorptionVolume(e.getDesorptionVolume())
                .desorptionEfficiency(e.getDesorptionEfficiency())
                .samplingVolume(e.getSamplingVolume())
                .resultDecimalPlaces(e.getResultDecimalPlaces())
                .calcDecimalPlaces(e.getCalcDecimalPlaces())
                .resultValue(e.getResultValue())
                .resultUnit(e.getResultUnit())
                .minDetectValue(e.getMinDetectValue())
                .minQuantifyValue(e.getMinQuantifyValue())
                .analystName(e.getAnalystName())
                .analysisDate(e.getAnalysisDate())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .curvePoints(curvePointResponses)
                .build();
    }

    private LabResultDto.CurvePointResponse toCurvePointResponse(OhdpLabCurvePoint p) {
        return LabResultDto.CurvePointResponse.builder()
                .id(p.getId())
                .labResultId(p.getLabResultId())
                .pointIndex(p.getPointIndex())
                .concentration(p.getConcentration())
                .absorbance(p.getAbsorbance())
                .absorbanceRaw(p.getAbsorbanceRaw())
                .isExcluded(p.getIsExcluded())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
