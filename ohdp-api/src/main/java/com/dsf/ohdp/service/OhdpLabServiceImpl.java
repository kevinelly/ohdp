package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.LabDto;
import com.dsf.ohdp.entity.OhdpLabTask;
import com.dsf.ohdp.entity.OhdpLabTaskSample;
import com.dsf.ohdp.repository.OhdpLabTaskRepository;
import com.dsf.ohdp.repository.OhdpLabTaskSampleRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kevin
 * @description 实验室任务单服务实现
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OhdpLabServiceImpl implements OhdpLabService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_REVIEWED = "REVIEWED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private final OhdpLabTaskRepository labTaskRepository;
    private final OhdpLabTaskSampleRepository labTaskSampleRepository;

    // -------------------------------------------------------------------------
    // Task operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<LabDto.TaskResponse> findTaskPage(LabDto.TaskQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize());
        return labTaskRepository.findByConditions(
                query.getProjectId(),
                query.getTaskStatus(),
                query.getKeyword(),
                pageable
        ).map(this::toTaskResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LabDto.TaskDetailResponse findTaskById(Long id) {
        OhdpLabTask task = requireTask(id);
        List<OhdpLabTaskSample> samples = labTaskSampleRepository
                .findByTaskIdAndDeletedFalseOrderByIdAsc(id);
        LabDto.TaskDetailResponse detail = toTaskDetailResponse(task);
        detail.setSamples(samples.stream().map(this::toSampleResponse).collect(Collectors.toList()));
        return detail;
    }

    @Override
    public LabDto.TaskResponse createTask(LabDto.TaskRequest request) {
        OhdpLabTask task = new OhdpLabTask();
        task.setTaskCode(generateTaskCode());
        task.setProjectId(request.getProjectId());
        task.setTaskStatus(STATUS_PENDING);
        task.setIssuerName(request.getIssuerName());
        task.setIssuedDate(request.getIssuedDate());
        task.setNotes(request.getNotes());
        labTaskRepository.save(task);

        if (request.getSamples() != null && !request.getSamples().isEmpty()) {
            for (LabDto.SampleRequest sr : request.getSamples()) {
                saveSampleForTask(task.getId(), sr);
            }
        }

        return toTaskResponse(task);
    }

    @Override
    public LabDto.TaskResponse updateTask(Long id, LabDto.TaskUpdateRequest request) {
        OhdpLabTask task = requireTask(id);
        if (!STATUS_PENDING.equals(task.getTaskStatus())) {
            throw new RuntimeException("只有 PENDING 状态的任务单才能修改，当前状态：" + task.getTaskStatus());
        }
        task.setIssuerName(request.getIssuerName());
        task.setIssuedDate(request.getIssuedDate());
        task.setNotes(request.getNotes());
        labTaskRepository.save(task);
        return toTaskResponse(task);
    }

    @Override
    public void deleteTask(Long id) {
        OhdpLabTask task = requireTask(id);
        if (!STATUS_PENDING.equals(task.getTaskStatus())) {
            throw new RuntimeException("只有 PENDING 状态的任务单才能删除，当前状态：" + task.getTaskStatus());
        }
        task.setDeleted(true);
        labTaskRepository.save(task);
    }

    @Override
    public LabDto.TaskResponse receiveTask(Long id, LabDto.ReceiveRequest request) {
        OhdpLabTask task = requireTask(id);
        if (!STATUS_PENDING.equals(task.getTaskStatus())) {
            throw new RuntimeException("只有 PENDING 状态的任务单才能领取，当前状态：" + task.getTaskStatus());
        }
        task.setTaskStatus(STATUS_IN_PROGRESS);
        task.setReceiverName(request.getReceiverName());
        task.setStartTime(LocalDateTime.now());
        labTaskRepository.save(task);
        return toTaskResponse(task);
    }

    @Override
    public LabDto.TaskResponse submitTask(Long id) {
        OhdpLabTask task = requireTask(id);
        if (!STATUS_IN_PROGRESS.equals(task.getTaskStatus())) {
            throw new RuntimeException("只有 IN_PROGRESS 状态的任务单才能提交审核，当前状态：" + task.getTaskStatus());
        }
        task.setTaskStatus(STATUS_COMPLETED);
        task.setEndTime(LocalDateTime.now());
        labTaskRepository.save(task);
        return toTaskResponse(task);
    }

    @Override
    public LabDto.TaskResponse reviewTask(Long id, LabDto.ReviewRequest request) {
        OhdpLabTask task = requireTask(id);
        if (!STATUS_COMPLETED.equals(task.getTaskStatus())) {
            throw new RuntimeException("只有 COMPLETED 状态的任务单才能审核，当前状态：" + task.getTaskStatus());
        }
        task.setTaskStatus(Boolean.TRUE.equals(request.getApproved()) ? STATUS_REVIEWED : STATUS_REJECTED);
        task.setReviewNote(request.getReviewNote());
        labTaskRepository.save(task);
        return toTaskResponse(task);
    }

    // -------------------------------------------------------------------------
    // Sample operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<LabDto.SampleResponse> listSamples(Long taskId) {
        requireTask(taskId);
        return labTaskSampleRepository.findByTaskIdAndDeletedFalseOrderByIdAsc(taskId)
                .stream().map(this::toSampleResponse).collect(Collectors.toList());
    }

    @Override
    public LabDto.SampleResponse addSample(Long taskId, LabDto.SampleRequest request) {
        OhdpLabTask task = requireTask(taskId);
        if (!STATUS_PENDING.equals(task.getTaskStatus()) && !STATUS_IN_PROGRESS.equals(task.getTaskStatus())) {
            throw new RuntimeException(
                    "只有 PENDING 或 IN_PROGRESS 状态的任务单才能新增样品，当前状态：" + task.getTaskStatus());
        }
        OhdpLabTaskSample sample = saveSampleForTask(taskId, request);
        return toSampleResponse(sample);
    }

    @Override
    public LabDto.SampleResponse updateSample(Long sampleId, LabDto.SampleRequest request) {
        OhdpLabTaskSample sample = requireSample(sampleId);
        applySampleRequest(sample, request);
        labTaskSampleRepository.save(sample);
        return toSampleResponse(sample);
    }

    @Override
    public void deleteSample(Long sampleId) {
        OhdpLabTaskSample sample = requireSample(sampleId);
        sample.setDeleted(true);
        labTaskSampleRepository.save(sample);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String generateTaskCode() {
        return "TASK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%03d", new Random().nextInt(1000));
    }

    private OhdpLabTask requireTask(Long id) {
        return labTaskRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("任务单不存在，id=" + id));
    }

    private OhdpLabTaskSample requireSample(Long id) {
        return labTaskSampleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("样品记录不存在，id=" + id));
    }

    private OhdpLabTaskSample saveSampleForTask(Long taskId, LabDto.SampleRequest request) {
        OhdpLabTaskSample sample = new OhdpLabTaskSample();
        sample.setTaskId(taskId);
        applySampleRequest(sample, request);
        labTaskSampleRepository.save(sample);
        return sample;
    }

    private void applySampleRequest(OhdpLabTaskSample sample, LabDto.SampleRequest request) {
        sample.setSampleCode(request.getSampleCode());
        sample.setFactorName(request.getFactorName());
        sample.setFactorId(request.getFactorId());
        sample.setSampleCount(request.getSampleCount() != null ? request.getSampleCount() : 1);
        sample.setSampleDate(request.getSampleDate());
        sample.setSampleType(request.getSampleType());
        sample.setNotes(request.getNotes());
    }

    private LabDto.TaskResponse toTaskResponse(OhdpLabTask task) {
        return LabDto.TaskResponse.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .projectId(task.getProjectId())
                .taskStatus(task.getTaskStatus())
                .issuerName(task.getIssuerName())
                .issuedDate(task.getIssuedDate())
                .receiverName(task.getReceiverName())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .reviewerName(task.getReviewerName())
                .reviewNote(task.getReviewNote())
                .notes(task.getNotes())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private LabDto.TaskDetailResponse toTaskDetailResponse(OhdpLabTask task) {
        return LabDto.TaskDetailResponse.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .projectId(task.getProjectId())
                .taskStatus(task.getTaskStatus())
                .issuerName(task.getIssuerName())
                .issuedDate(task.getIssuedDate())
                .receiverName(task.getReceiverName())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .reviewerName(task.getReviewerName())
                .reviewNote(task.getReviewNote())
                .notes(task.getNotes())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private LabDto.SampleResponse toSampleResponse(OhdpLabTaskSample sample) {
        return LabDto.SampleResponse.builder()
                .id(sample.getId())
                .taskId(sample.getTaskId())
                .sampleCode(sample.getSampleCode())
                .factorName(sample.getFactorName())
                .factorId(sample.getFactorId())
                .sampleCount(sample.getSampleCount())
                .sampleDate(sample.getSampleDate())
                .sampleType(sample.getSampleType())
                .notes(sample.getNotes())
                .createdAt(sample.getCreatedAt())
                .updatedAt(sample.getUpdatedAt())
                .build();
    }
}
