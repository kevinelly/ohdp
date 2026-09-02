package com.dsf.feedback.service;

import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.auth.service.rbac.RbacAccessService;
import com.dsf.common.exception.BusinessException;
import com.dsf.common.model.RecordStatus;
import com.dsf.feedback.config.FeedbackStorageProperties;
import com.dsf.feedback.dto.FeedbackDto;
import com.dsf.feedback.entity.FeedbackAttachment;
import com.dsf.feedback.entity.FeedbackEvent;
import com.dsf.feedback.entity.FeedbackReadState;
import com.dsf.feedback.entity.FeedbackTicket;
import com.dsf.feedback.model.FeedbackEventType;
import com.dsf.feedback.model.FeedbackPriority;
import com.dsf.feedback.model.FeedbackStatus;
import com.dsf.feedback.model.FeedbackUrgency;
import com.dsf.feedback.repository.FeedbackAttachmentRepository;
import com.dsf.feedback.repository.FeedbackEventRepository;
import com.dsf.feedback.repository.FeedbackReadStateRepository;
import com.dsf.feedback.repository.FeedbackTicketRepository;
import com.dsf.system.entity.SysLog;
import com.dsf.system.repository.SysLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    public static final String READ_ALL = "FEEDBACK:READ_ALL";
    public static final String HANDLE = "FEEDBACK:HANDLE";
    public static final String CLOSE = "FEEDBACK:CLOSE";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".webp", ".pdf", ".doc", ".docx", ".xls", ".xlsx");
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FeedbackTicketRepository ticketRepository;
    private final FeedbackEventRepository eventRepository;
    private final FeedbackAttachmentRepository attachmentRepository;
    private final FeedbackReadStateRepository readStateRepository;
    private final SysUserRepository userRepository;
    private final RbacAccessService rbacAccessService;
    private final FeedbackStorageProperties storageProperties;
    private final SysLogRepository logRepository;

    @Transactional
    public FeedbackDto.TicketDetail create(FeedbackDto.CreateRequest request) {
        AuthenticatedUser user = requireUser();
        FeedbackTicket ticket = new FeedbackTicket();
        ticket.setTicketNo("TMP-" + UUID.randomUUID());
        ticket.setTitle(clean(request.title()));
        ticket.setDescription(clean(request.description()));
        ticket.setCategory(request.category());
        ticket.setUrgency(request.urgency() == null ? FeedbackUrgency.NORMAL : request.urgency());
        ticket.setPriority(ticket.getUrgency() == FeedbackUrgency.URGENT ? FeedbackPriority.HIGH : FeedbackPriority.NORMAL);
        ticket.setStatus(FeedbackStatus.ACCEPTED);
        ticket.setSourceModule(cleanNullable(request.sourceModule()));
        ticket.setSourcePage(cleanNullable(request.sourcePage()));
        ticket.setRelatedType(cleanNullable(request.relatedType()));
        ticket.setRelatedCode(cleanNullable(request.relatedCode()));
        ticket.setSubmitterId(user.getUserId());
        ticket.setSubmitterName(user.getUsername());
        ticket = ticketRepository.saveAndFlush(ticket);
        ticket.setTicketNo("FK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format(Locale.ROOT, "%06d", ticket.getId()));
        ticket = ticketRepository.save(ticket);

        addEvent(ticket.getId(), FeedbackEventType.SUBMITTED, null, null,
                "提交意见：" + ticket.getTitle(), user, false);
        FeedbackEvent accepted = addEvent(ticket.getId(), FeedbackEventType.AUTO_ACCEPTED, null,
                FeedbackStatus.ACCEPTED, "系统已自动采纳并进入跟踪队列", systemUser(), false);
        markRead(ticket.getId(), user.getUserId(), accepted.getId());
        writeLog(user, "创建意见", ticket.getTicketNo(), "SUCCESS");
        return detailInternal(ticket, user, true);
    }

    @Transactional(readOnly = true)
    public Page<FeedbackDto.TicketSummary> list(
            String scope, FeedbackStatus status, String keyword, int page, int size) {
        AuthenticatedUser user = requireUser();
        String normalizedScope = scope == null ? "mine" : scope.trim().toLowerCase(Locale.ROOT);
        boolean canReadAll = canReadAll(user);
        if (!Set.of("mine", "assigned", "all").contains(normalizedScope)) {
            throw new BusinessException("不支持的意见范围");
        }
        if (!normalizedScope.equals("mine") && !canReadAll) {
            throw new BusinessException("无权查看处理工作台");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Specification<FeedbackTicket> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));
            if (normalizedScope.equals("mine")) {
                predicates.add(builder.equal(root.get("submitterId"), user.getUserId()));
            } else if (normalizedScope.equals("assigned")) {
                predicates.add(builder.equal(root.get("assigneeId"), user.getUserId()));
            }
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (StringUtils.hasText(keyword)) {
                String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("ticketNo")), value),
                        builder.like(builder.lower(root.get("title")), value),
                        builder.like(builder.lower(root.get("submitterName")), value)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return ticketRepository.findAll(specification, pageable).map(ticket -> toSummary(ticket, user));
    }

    @Transactional
    public FeedbackDto.TicketDetail get(Long id) {
        AuthenticatedUser user = requireUser();
        FeedbackTicket ticket = requireTicket(id);
        assertCanView(ticket, user);
        return detailInternal(ticket, user, true);
    }

    @Transactional(readOnly = true)
    public FeedbackDto.SummaryResponse summary(String scope) {
        AuthenticatedUser user = requireUser();
        List<FeedbackTicket> tickets = listAccessible(scope, user);
        long unread = tickets.stream().filter(ticket -> isUnread(ticket.getId(), user.getUserId())).count();
        return new FeedbackDto.SummaryResponse(
                tickets.size(), unread,
                count(tickets, FeedbackStatus.ACCEPTED), count(tickets, FeedbackStatus.IN_PROGRESS),
                count(tickets, FeedbackStatus.NEED_INFO), count(tickets, FeedbackStatus.PENDING_CONFIRMATION),
                count(tickets, FeedbackStatus.CLOSED));
    }

    @Transactional
    public FeedbackDto.TicketDetail updateHandling(Long id, FeedbackDto.HandlingRequest request) {
        AuthenticatedUser user = requireHandler();
        FeedbackTicket ticket = requireTicket(id);
        if (ticket.getStatus().isTerminal()) throw new BusinessException("已结束的意见不能修改处理安排");

        if (Boolean.TRUE.equals(request.assignToMe())) {
            ticket.setAssigneeId(user.getUserId());
            ticket.setAssigneeName(user.getUsername());
        } else if (request.assigneeId() != null) {
            SysUser assignee = userRepository.findByIdAndDeletedFalse(request.assigneeId())
                    .filter(item -> item.getStatus() == RecordStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException("指定处理人不存在或已停用"));
            AuthenticatedUser assigneeUser = AuthenticatedUser.builder()
                    .userId(assignee.getId())
                    .username(assignee.getUsername())
                    .role(assignee.getRole())
                    .build();
            if (!canHandle(assigneeUser)) {
                throw new BusinessException("指定用户没有意见处理权限");
            }
            ticket.setAssigneeId(assignee.getId());
            ticket.setAssigneeName(assignee.getUsername());
        }
        if (request.priority() != null) ticket.setPriority(request.priority());
        ticket.setPlannedCompletionDate(request.plannedCompletionDate());
        ticket.setPlannedVersion(cleanNullable(request.plannedVersion()));
        ticketRepository.save(ticket);
        addEvent(ticket.getId(), FeedbackEventType.HANDLING_UPDATED, null, null,
                "处理人：" + valueOrEmpty(ticket.getAssigneeName()) + "；优先级：" + ticket.getPriority().getLabel()
                        + "；计划完成：" + valueOrEmpty(ticket.getPlannedCompletionDate())
                        + "；计划版本：" + valueOrEmpty(ticket.getPlannedVersion()), user, true);
        writeLog(user, "更新意见处理安排", ticket.getTicketNo(), "SUCCESS");
        return detailInternal(ticket, user, true);
    }

    @Transactional
    public FeedbackDto.TicketDetail comment(Long id, FeedbackDto.CommentRequest request) {
        AuthenticatedUser user = requireUser();
        FeedbackTicket ticket = requireTicket(id);
        assertCanView(ticket, user);
        boolean internal = Boolean.TRUE.equals(request.internalOnly());
        if (internal && !canHandle(user)) throw new BusinessException("只有处理人员可以添加内部处理记录");
        addEvent(ticket.getId(), internal ? FeedbackEventType.INTERNAL_NOTE : FeedbackEventType.COMMENTED,
                null, null, clean(request.content()), user, internal);
        ticketRepository.save(ticket);
        writeLog(user, internal ? "添加内部处理记录" : "补充意见说明", ticket.getTicketNo(), "SUCCESS");
        return detailInternal(ticket, user, true);
    }

    @Transactional
    public FeedbackDto.TicketDetail transition(Long id, FeedbackDto.TransitionRequest request) {
        AuthenticatedUser user = requireUser();
        FeedbackTicket ticket = requireTicket(id);
        assertCanView(ticket, user);
        FeedbackStatus from = ticket.getStatus();
        FeedbackStatus target = request.targetStatus();
        Set<FeedbackStatus> allowed = allowedTransitions(ticket, user);
        if (!allowed.contains(target)) {
            throw new BusinessException("当前状态不能转为“" + target.getLabel() + "”");
        }
        boolean handler = canHandle(user);
        if (handler && (target == FeedbackStatus.CLOSED || target == FeedbackStatus.REJECTED) && !canClose(user)) {
            throw new BusinessException("无权关闭或退回意见");
        }
        if (Set.of(FeedbackStatus.NEED_INFO, FeedbackStatus.REJECTED).contains(target)
                && !StringUtils.hasText(request.comment())) {
            throw new BusinessException("该操作需要填写说明");
        }
        if (target == FeedbackStatus.PENDING_CONFIRMATION && !StringUtils.hasText(request.resolutionSummary())) {
            throw new BusinessException("提交用户确认前需要填写处理结果");
        }
        if (target == FeedbackStatus.IN_PROGRESS && from == FeedbackStatus.PENDING_CONFIRMATION
                && !StringUtils.hasText(request.comment())) {
            throw new BusinessException("重新处理需要填写原因");
        }
        if (handler && ticket.getAssigneeId() == null && target == FeedbackStatus.IN_PROGRESS) {
            ticket.setAssigneeId(user.getUserId());
            ticket.setAssigneeName(user.getUsername());
        }
        ticket.setStatus(target);
        if (StringUtils.hasText(request.resolutionSummary())) {
            ticket.setResolutionSummary(clean(request.resolutionSummary()));
        }
        if (target == FeedbackStatus.PENDING_CONFIRMATION) ticket.setResolvedAt(LocalDateTime.now());
        if (target == FeedbackStatus.CLOSED) ticket.setClosedAt(LocalDateTime.now());
        if (target == FeedbackStatus.IN_PROGRESS && from == FeedbackStatus.PENDING_CONFIRMATION) {
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
        }
        ticketRepository.save(ticket);
        String content = StringUtils.hasText(request.comment()) ? clean(request.comment())
                : "状态更新为“" + target.getLabel() + "”";
        addEvent(ticket.getId(), FeedbackEventType.STATUS_CHANGED, from, target, content, user, false);
        writeLog(user, "更新意见状态", ticket.getTicketNo() + ":" + from + "->" + target, "SUCCESS");
        return detailInternal(ticket, user, true);
    }

    @Transactional
    public FeedbackDto.AttachmentResponse upload(Long ticketId, MultipartFile file) {
        AuthenticatedUser user = requireUser();
        FeedbackTicket ticket = requireTicket(ticketId);
        assertCanView(ticket, user);
        if (ticket.getStatus().isTerminal()) throw new BusinessException("已结束的意见不能继续上传附件");
        validateFile(file);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        String extension = extension(originalName);
        String mediaType = file.getContentType() == null ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT);
        String storedName = UUID.randomUUID() + extension;
        Path root = storageRoot();
        Path directory = root.resolve(String.valueOf(ticketId)).resolve("attachments").normalize();
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw new BusinessException("附件存储路径不合法");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException("附件保存失败：" + exception.getMessage());
        }

        FeedbackAttachment attachment = new FeedbackAttachment();
        attachment.setTicketId(ticketId);
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setStoragePath(target.toString());
        attachment.setMediaType(mediaType);
        attachment.setFileSize(file.getSize());
        attachment.setSha256(sha256(target));
        attachment.setUploaderId(user.getUserId());
        attachment.setUploaderName(user.getUsername());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment = attachmentRepository.save(attachment);
        addEvent(ticketId, FeedbackEventType.ATTACHMENT_ADDED, null, null,
                "上传附件：" + originalName, user, false);
        writeLog(user, "上传意见附件", ticket.getTicketNo(), "SUCCESS");
        return toAttachment(attachment, user);
    }

    @Transactional(readOnly = true)
    public Download download(Long attachmentId) {
        AuthenticatedUser user = requireUser();
        FeedbackAttachment attachment = requireAttachment(attachmentId);
        FeedbackTicket ticket = requireTicket(attachment.getTicketId());
        assertCanView(ticket, user);
        Path path = verifiedPath(attachment.getStoragePath());
        if (!Files.isRegularFile(path)) throw new BusinessException("附件文件不存在");
        return new Download(new FileSystemResource(path), attachment.getOriginalName(), attachment.getMediaType(), attachment.getFileSize());
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        AuthenticatedUser user = requireUser();
        FeedbackAttachment attachment = requireAttachment(attachmentId);
        FeedbackTicket ticket = requireTicket(attachment.getTicketId());
        assertCanView(ticket, user);
        if (ticket.getStatus().isTerminal()) throw new BusinessException("已结束的意见不能删除附件");
        if (!attachment.getUploaderId().equals(user.getUserId()) && !canHandle(user)) {
            throw new BusinessException("只能删除自己上传的附件");
        }
        Path path = verifiedPath(attachment.getStoragePath());
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new BusinessException("附件删除失败：" + exception.getMessage());
        }
        attachment.setDeleted(true);
        attachmentRepository.save(attachment);
        addEvent(ticket.getId(), FeedbackEventType.ATTACHMENT_REMOVED, null, null,
                "删除附件：" + attachment.getOriginalName(), user, false);
        writeLog(user, "删除意见附件", ticket.getTicketNo(), "SUCCESS");
    }

    private FeedbackDto.TicketDetail detailInternal(FeedbackTicket ticket, AuthenticatedUser user, boolean updateRead) {
        boolean handler = canHandle(user);
        List<FeedbackEvent> allEvents = eventRepository.findByTicketIdAndDeletedFalseOrderByIdAsc(ticket.getId());
        List<FeedbackDto.EventResponse> events = allEvents.stream()
                .filter(event -> handler || !Boolean.TRUE.equals(event.getInternalOnly()))
                .map(this::toEvent)
                .toList();
        List<FeedbackDto.AttachmentResponse> attachments = attachmentRepository
                .findByTicketIdAndDeletedFalseOrderByUploadedAtDesc(ticket.getId()).stream()
                .map(value -> toAttachment(value, user)).toList();
        if (updateRead) {
            allEvents.stream().max(Comparator.comparing(FeedbackEvent::getId))
                    .ifPresent(last -> markRead(ticket.getId(), user.getUserId(), last.getId()));
        }
        return new FeedbackDto.TicketDetail(
                toSummary(ticket, user), ticket.getDescription(), ticket.getSourcePage(), ticket.getRelatedType(),
                ticket.getResolutionSummary(), ticket.getResolvedAt(), ticket.getClosedAt(), events, attachments,
                allowedTransitions(ticket, user).stream().toList(), handler, canClose(user), isOwner(ticket, user));
    }

    private Set<FeedbackStatus> allowedTransitions(FeedbackTicket ticket, AuthenticatedUser user) {
        FeedbackStatus status = ticket.getStatus();
        if (status.isTerminal()) return Set.of();
        EnumSet<FeedbackStatus> result = EnumSet.noneOf(FeedbackStatus.class);
        if (canHandle(user)) {
            switch (status) {
                case ACCEPTED -> result.addAll(Set.of(FeedbackStatus.IN_PROGRESS, FeedbackStatus.NEED_INFO, FeedbackStatus.REJECTED));
                case IN_PROGRESS -> result.addAll(Set.of(FeedbackStatus.NEED_INFO, FeedbackStatus.PENDING_CONFIRMATION));
                case NEED_INFO -> result.add(FeedbackStatus.IN_PROGRESS);
                case PENDING_CONFIRMATION -> result.addAll(Set.of(FeedbackStatus.IN_PROGRESS, FeedbackStatus.CLOSED));
                default -> { }
            }
        }
        if (isOwner(ticket, user)) {
            switch (status) {
                case ACCEPTED -> result.add(FeedbackStatus.WITHDRAWN);
                case NEED_INFO -> result.add(FeedbackStatus.ACCEPTED);
                case PENDING_CONFIRMATION -> result.addAll(Set.of(FeedbackStatus.CLOSED, FeedbackStatus.IN_PROGRESS));
                default -> { }
            }
        }
        return result;
    }

    private List<FeedbackTicket> listAccessible(String scope, AuthenticatedUser user) {
        String normalized = scope == null ? "mine" : scope.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("mine", "assigned", "all").contains(normalized)) {
            throw new BusinessException("不支持的意见范围");
        }
        if (normalized.equals("mine")) {
            return ticketRepository.findAll((root, query, builder) -> builder.and(
                    builder.isFalse(root.get("deleted")), builder.equal(root.get("submitterId"), user.getUserId())));
        }
        if (!canReadAll(user)) throw new BusinessException("无权查看处理工作台");
        if (normalized.equals("assigned")) {
            return ticketRepository.findAll((root, query, builder) -> builder.and(
                    builder.isFalse(root.get("deleted")), builder.equal(root.get("assigneeId"), user.getUserId())));
        }
        return ticketRepository.findAll((root, query, builder) -> builder.isFalse(root.get("deleted")));
    }

    private FeedbackDto.TicketSummary toSummary(FeedbackTicket value, AuthenticatedUser user) {
        return new FeedbackDto.TicketSummary(
                value.getId(), value.getTicketNo(), value.getTitle(), value.getCategory(), value.getCategory().getLabel(),
                value.getUrgency(), value.getUrgency().getLabel(), value.getPriority(), value.getPriority().getLabel(),
                value.getStatus(), value.getStatus().getLabel(), value.getSourceModule(), value.getRelatedCode(),
                value.getSubmitterId(), value.getSubmitterName(), value.getAssigneeId(), value.getAssigneeName(),
                value.getPlannedCompletionDate(), value.getPlannedVersion(), isUnread(value.getId(), user.getUserId()),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private FeedbackDto.EventResponse toEvent(FeedbackEvent value) {
        return new FeedbackDto.EventResponse(
                value.getId(), value.getEventType(), value.getEventType().getLabel(), value.getFromStatus(),
                value.getToStatus(), value.getContent(), value.getOperatorName(), Boolean.TRUE.equals(value.getInternalOnly()),
                value.getCreatedAt());
    }

    private FeedbackDto.AttachmentResponse toAttachment(FeedbackAttachment value, AuthenticatedUser user) {
        return new FeedbackDto.AttachmentResponse(
                value.getId(), value.getOriginalName(), value.getMediaType(), value.getFileSize(), value.getSha256(),
                value.getUploaderName(), value.getUploadedAt(), value.getUploaderId().equals(user.getUserId()) || canHandle(user));
    }

    private FeedbackEvent addEvent(Long ticketId, FeedbackEventType type, FeedbackStatus from, FeedbackStatus to,
                                   String content, AuthenticatedUser operator, boolean internalOnly) {
        FeedbackEvent event = new FeedbackEvent();
        event.setTicketId(ticketId);
        event.setEventType(type);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setContent(cleanNullable(content));
        event.setOperatorId(operator.getUserId());
        event.setOperatorName(operator.getUsername());
        event.setInternalOnly(internalOnly);
        return eventRepository.save(event);
    }

    private void markRead(Long ticketId, Long userId, Long eventId) {
        FeedbackReadState state = readStateRepository.findByTicketIdAndUserIdAndDeletedFalse(ticketId, userId)
                .orElseGet(FeedbackReadState::new);
        state.setTicketId(ticketId);
        state.setUserId(userId);
        state.setLastReadEventId(eventId);
        state.setReadAt(LocalDateTime.now());
        readStateRepository.save(state);
    }

    private boolean isUnread(Long ticketId, Long userId) {
        Long lastEventId = eventRepository.findTopByTicketIdAndDeletedFalseOrderByIdDesc(ticketId)
                .map(FeedbackEvent::getId).orElse(0L);
        Long lastRead = readStateRepository.findByTicketIdAndUserIdAndDeletedFalse(ticketId, userId)
                .map(FeedbackReadState::getLastReadEventId).orElse(0L);
        return lastEventId > lastRead;
    }

    private void assertCanView(FeedbackTicket ticket, AuthenticatedUser user) {
        if (isOwner(ticket, user) || canReadAll(user)) return;
        throw new BusinessException("无权查看该意见");
    }

    private boolean isOwner(FeedbackTicket ticket, AuthenticatedUser user) {
        return ticket.getSubmitterId().equals(user.getUserId());
    }

    private boolean canReadAll(AuthenticatedUser user) {
        return user.getRole() == UserRole.ADMIN || rbacAccessService.hasPermission(user, READ_ALL)
                || rbacAccessService.hasPermission(user, HANDLE);
    }

    private boolean canHandle(AuthenticatedUser user) {
        return user.getRole() == UserRole.ADMIN || rbacAccessService.hasPermission(user, HANDLE);
    }

    private boolean canClose(AuthenticatedUser user) {
        return user.getRole() == UserRole.ADMIN || rbacAccessService.hasPermission(user, CLOSE);
    }

    private AuthenticatedUser requireHandler() {
        AuthenticatedUser user = requireUser();
        if (!canHandle(user)) throw new BusinessException("无权处理意见");
        return user;
    }

    private AuthenticatedUser requireUser() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) throw new BusinessException("请先登录");
        return user;
    }

    private AuthenticatedUser systemUser() {
        return AuthenticatedUser.builder().userId(0L).username("系统").role(UserRole.ADMIN).build();
    }

    private FeedbackTicket requireTicket(Long id) {
        return ticketRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException("意见不存在"));
    }

    private FeedbackAttachment requireAttachment(Long id) {
        return attachmentRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException("附件不存在"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择非空文件");
        if (file.getSize() > storageProperties.getMaxUploadBytes()) throw new BusinessException("附件超过允许大小");
        String name = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        if (name.contains("..")) throw new BusinessException("附件名称不合法");
        String ext = extension(name);
        if (!ALLOWED_EXTENSIONS.contains(ext)) throw new BusinessException("不支持的附件扩展名");
        String media = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MEDIA_TYPES.contains(media)) throw new BusinessException("不支持的附件类型：" + media);
    }

    private Path storageRoot() {
        return Path.of(storageProperties.getBaseDir()).toAbsolutePath().normalize();
    }

    private Path verifiedPath(String value) {
        Path root = storageRoot();
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new BusinessException("附件路径越界");
        return path;
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream stream = new DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException("附件校验失败：" + exception.getMessage());
        }
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0 || name.length() - index > 10) return "";
        return name.substring(index).toLowerCase(Locale.ROOT);
    }

    private long count(List<FeedbackTicket> tickets, FeedbackStatus status) {
        return tickets.stream().filter(ticket -> ticket.getStatus() == status).count();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanNullable(String value) {
        String result = clean(value);
        return result.isEmpty() ? null : result;
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "未设置" : value.toString();
    }

    private void writeLog(AuthenticatedUser user, String operation, String params, String resultType) {
        SysLog log = new SysLog();
        log.setUserName(user.getUsername());
        log.setOperation(operation);
        log.setMethod("FeedbackService");
        log.setParams(params);
        log.setResultType(resultType);
        log.setModule("意见反馈");
        logRepository.save(log);
    }

    public record Download(Resource resource, String fileName, String mediaType, long fileSize) {
    }
}
