package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.LabDto;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * @author kevin
 * @description 实验室任务单服务接口
 */
public interface OhdpLabService {

    // Task operations
    Page<LabDto.TaskResponse> findTaskPage(LabDto.TaskQuery query);

    LabDto.TaskDetailResponse findTaskById(Long id);

    LabDto.TaskResponse createTask(LabDto.TaskRequest request);

    LabDto.TaskResponse updateTask(Long id, LabDto.TaskUpdateRequest request);

    void deleteTask(Long id);

    LabDto.TaskResponse receiveTask(Long id, LabDto.ReceiveRequest request);

    LabDto.TaskResponse submitTask(Long id);

    LabDto.TaskResponse reviewTask(Long id, LabDto.ReviewRequest request);

    // Sample operations
    List<LabDto.SampleResponse> listSamples(Long taskId);

    LabDto.SampleResponse addSample(Long taskId, LabDto.SampleRequest request);

    LabDto.SampleResponse updateSample(Long sampleId, LabDto.SampleRequest request);

    void deleteSample(Long sampleId);
}
