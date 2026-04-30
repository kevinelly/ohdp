package com.dsf.ohdp.controller;

import com.dsf.ohdp.dto.LabDto;
import com.dsf.ohdp.service.OhdpLabService;
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

/**
 * @author kevin
 * @description 实验室任务单管理接口
 */
@Tag(name = "OHDP-Lab")
@RestController
@RequestMapping("/api/ohdp/lab")
@RequiredArgsConstructor
public class OhdpLabController {

    private final OhdpLabService labService;

    // -------------------------------------------------------------------------
    // Task endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/tasks")
    public Page<LabDto.TaskResponse> listTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        LabDto.TaskQuery query = new LabDto.TaskQuery();
        query.setProjectId(projectId);
        query.setTaskStatus(taskStatus);
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return labService.findTaskPage(query);
    }

    @GetMapping("/tasks/{id}")
    public LabDto.TaskDetailResponse getTask(@PathVariable Long id) {
        return labService.findTaskById(id);
    }

    @PostMapping("/tasks")
    public LabDto.TaskResponse createTask(@Valid @RequestBody LabDto.TaskRequest request) {
        return labService.createTask(request);
    }

    @PutMapping("/tasks/{id}")
    public LabDto.TaskResponse updateTask(
            @PathVariable Long id,
            @Valid @RequestBody LabDto.TaskUpdateRequest request) {
        return labService.updateTask(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public void deleteTask(@PathVariable Long id) {
        labService.deleteTask(id);
    }

    @PostMapping("/tasks/{id}/receive")
    public LabDto.TaskResponse receiveTask(
            @PathVariable Long id,
            @Valid @RequestBody LabDto.ReceiveRequest request) {
        return labService.receiveTask(id, request);
    }

    @PostMapping("/tasks/{id}/submit")
    public LabDto.TaskResponse submitTask(@PathVariable Long id) {
        return labService.submitTask(id);
    }

    @PostMapping("/tasks/{id}/review")
    public LabDto.TaskResponse reviewTask(
            @PathVariable Long id,
            @Valid @RequestBody LabDto.ReviewRequest request) {
        return labService.reviewTask(id, request);
    }

    // -------------------------------------------------------------------------
    // Sample endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/tasks/{taskId}/samples")
    public List<LabDto.SampleResponse> listSamples(@PathVariable Long taskId) {
        return labService.listSamples(taskId);
    }

    @PostMapping("/tasks/{taskId}/samples")
    public LabDto.SampleResponse addSample(
            @PathVariable Long taskId,
            @Valid @RequestBody LabDto.SampleRequest request) {
        return labService.addSample(taskId, request);
    }

    @PutMapping("/task-samples/{sampleId}")
    public LabDto.SampleResponse updateSample(
            @PathVariable Long sampleId,
            @Valid @RequestBody LabDto.SampleRequest request) {
        return labService.updateSample(sampleId, request);
    }

    @DeleteMapping("/task-samples/{sampleId}")
    public void deleteSample(@PathVariable Long sampleId) {
        labService.deleteSample(sampleId);
    }
}
