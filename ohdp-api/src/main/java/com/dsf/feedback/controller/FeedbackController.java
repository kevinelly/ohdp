package com.dsf.feedback.controller;

import com.dsf.feedback.dto.FeedbackDto;
import com.dsf.feedback.model.FeedbackStatus;
import com.dsf.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Feedback API")
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public FeedbackDto.TicketDetail create(@Valid @RequestBody FeedbackDto.CreateRequest request) {
        return feedbackService.create(request);
    }

    @GetMapping
    public Page<FeedbackDto.TicketSummary> list(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return feedbackService.list(scope, status, keyword, page, size);
    }

    @GetMapping("/summary")
    public FeedbackDto.SummaryResponse summary(@RequestParam(defaultValue = "mine") String scope) {
        return feedbackService.summary(scope);
    }

    @GetMapping("/{id}")
    public FeedbackDto.TicketDetail get(@PathVariable Long id) {
        return feedbackService.get(id);
    }

    @PutMapping("/{id}/handling")
    public FeedbackDto.TicketDetail updateHandling(
            @PathVariable Long id, @Valid @RequestBody FeedbackDto.HandlingRequest request) {
        return feedbackService.updateHandling(id, request);
    }

    @PostMapping("/{id}/comments")
    public FeedbackDto.TicketDetail comment(
            @PathVariable Long id, @Valid @RequestBody FeedbackDto.CommentRequest request) {
        return feedbackService.comment(id, request);
    }

    @PostMapping("/{id}/transitions")
    public FeedbackDto.TicketDetail transition(
            @PathVariable Long id, @Valid @RequestBody FeedbackDto.TransitionRequest request) {
        return feedbackService.transition(id, request);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FeedbackDto.AttachmentResponse upload(
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return feedbackService.upload(id, file);
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FeedbackService.Download download = feedbackService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(download.fileName()))
                .contentLength(download.fileSize())
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .body(download.resource());
    }

    @DeleteMapping("/attachments/{id}")
    public void deleteAttachment(@PathVariable Long id) {
        feedbackService.deleteAttachment(id);
    }

    private String attachment(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
