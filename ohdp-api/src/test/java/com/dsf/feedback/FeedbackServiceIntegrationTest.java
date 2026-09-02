package com.dsf.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dsf.auth.entity.PermissionType;
import com.dsf.auth.entity.SysPermission;
import com.dsf.auth.entity.SysRole;
import com.dsf.auth.entity.SysRolePermission;
import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.SysUserRole;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysPermissionRepository;
import com.dsf.auth.repository.SysRolePermissionRepository;
import com.dsf.auth.repository.SysRoleRepository;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.auth.repository.SysUserRoleRepository;
import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.exception.BusinessException;
import com.dsf.common.model.RecordStatus;
import com.dsf.feedback.dto.FeedbackDto;
import com.dsf.feedback.model.FeedbackCategory;
import com.dsf.feedback.model.FeedbackPriority;
import com.dsf.feedback.model.FeedbackStatus;
import com.dsf.feedback.model.FeedbackUrgency;
import com.dsf.feedback.service.FeedbackService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FeedbackServiceIntegrationTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysRolePermissionRepository rolePermissionRepository;

    private SysUser submitter;
    private SysUser otherUser;
    private SysUser admin;

    @BeforeEach
    void setUp() {
        submitter = createUser("feedback-user-" + suffix(), UserRole.USER);
        otherUser = createUser("feedback-other-" + suffix(), UserRole.USER);
        admin = createUser("feedback-admin-" + suffix(), UserRole.ADMIN);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void automaticallyAcceptsAndCreatesTraceableTimeline() {
        loginAs(submitter);
        FeedbackDto.TicketDetail result = feedbackService.create(new FeedbackDto.CreateRequest(
                "希望增加报告批量核对提示",
                "当前需要逐份打开报告，期望在报告管理显示待核对项目和异常原因。",
                FeedbackCategory.FEATURE,
                FeedbackUrgency.NORMAL,
                "报告管理",
                "/",
                "PROJECT",
                "OHDP-TEST-001"));

        assertThat(result.ticket().ticketNo()).startsWith("FK-");
        assertThat(result.ticket().status()).isEqualTo(FeedbackStatus.ACCEPTED);
        assertThat(result.events()).extracting(FeedbackDto.EventResponse::eventLabel)
                .containsExactly("提交意见", "系统自动采纳");
        assertThat(result.events().get(1).operatorName()).isEqualTo("系统");
        assertThat(result.ticket().unread()).isFalse();
    }

    @Test
    void protectsOwnerDataAndCompletesHandlerWorkflowWithAttachment() {
        loginAs(submitter);
        FeedbackDto.TicketDetail created = feedbackService.create(new FeedbackDto.CreateRequest(
                "CT 案例筛选结果需要保留",
                "切换到帮助后返回 CT 页面，期望保留刚才的案例状态筛选条件。",
                FeedbackCategory.UI_UX,
                FeedbackUrgency.URGENT,
                "CT 质量控制",
                "/",
                "RADIOLOGY_CASE",
                "CT-LOCAL-001"));
        Long ticketId = created.ticket().id();

        MockMultipartFile file = new MockMultipartFile(
                "file", "复现截图.png", "image/png", "fake-png-for-local-test".getBytes(StandardCharsets.UTF_8));
        FeedbackDto.AttachmentResponse uploaded = feedbackService.upload(ticketId, file);
        assertThat(uploaded.originalName()).isEqualTo("复现截图.png");
        assertThat(uploaded.sha256()).hasSize(64);

        loginAs(otherUser);
        assertThatThrownBy(() -> feedbackService.get(ticketId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看");

        loginAs(admin);
        FeedbackDto.TicketDetail scheduled = feedbackService.updateHandling(ticketId,
                new FeedbackDto.HandlingRequest(null, true, FeedbackPriority.HIGH, LocalDate.now().plusDays(3), "2026.9"));
        assertThat(scheduled.ticket().assigneeName()).isEqualTo(admin.getUsername());
        assertThat(scheduled.ticket().plannedVersion()).isEqualTo("2026.9");
        feedbackService.comment(ticketId, new FeedbackDto.CommentRequest("内部已定位为页面状态恢复逻辑问题", true));
        feedbackService.transition(ticketId, new FeedbackDto.TransitionRequest(
                FeedbackStatus.IN_PROGRESS, "开始修复并补充回归测试", null));
        FeedbackDto.TicketDetail awaitingConfirmation = feedbackService.transition(ticketId,
                new FeedbackDto.TransitionRequest(FeedbackStatus.PENDING_CONFIRMATION,
                        "已完成本地回归测试", "已保留 CT 页面筛选条件，并验证帮助页往返。"));
        assertThat(awaitingConfirmation.ticket().status()).isEqualTo(FeedbackStatus.PENDING_CONFIRMATION);

        loginAs(submitter);
        FeedbackDto.TicketDetail ownerView = feedbackService.get(ticketId);
        assertThat(ownerView.events()).noneMatch(FeedbackDto.EventResponse::internalOnly);
        assertThat(ownerView.resolutionSummary()).contains("保留 CT 页面筛选条件");
        FeedbackDto.TicketDetail closed = feedbackService.transition(ticketId,
                new FeedbackDto.TransitionRequest(FeedbackStatus.CLOSED, "已验证，结果符合预期", null));
        assertThat(closed.ticket().status()).isEqualTo(FeedbackStatus.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidSummaryScopeAndAssignmentToUserWithoutHandlingPermission() {
        loginAs(submitter);
        FeedbackDto.TicketDetail created = feedbackService.create(new FeedbackDto.CreateRequest(
                "检测流程意见需要跟踪",
                "希望提交后能够在意见中心持续查看处理进度和最终结果。",
                FeedbackCategory.FEATURE,
                FeedbackUrgency.NORMAL,
                "项目工作台",
                "/",
                "PROJECT",
                "OHDP-TEST-002"));

        loginAs(admin);
        assertThatThrownBy(() -> feedbackService.summary("unexpected"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的意见范围");
        assertThatThrownBy(() -> feedbackService.updateHandling(created.ticket().id(),
                new FeedbackDto.HandlingRequest(otherUser.getId(), false, FeedbackPriority.NORMAL,
                        LocalDate.now().plusDays(5), "2026.10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有意见处理权限");

        SysUser authorizedHandler = createUser("feedback-handler-" + suffix(), UserRole.USER);
        grantPermission(authorizedHandler, FeedbackService.HANDLE);
        FeedbackDto.TicketDetail assigned = feedbackService.updateHandling(created.ticket().id(),
                new FeedbackDto.HandlingRequest(authorizedHandler.getId(), false, FeedbackPriority.NORMAL,
                        LocalDate.now().plusDays(5), "2026.10"));
        assertThat(assigned.ticket().assigneeName()).isEqualTo(authorizedHandler.getUsername());
    }

    private void grantPermission(SysUser user, String permissionCode) {
        String suffix = suffix();
        SysRole role = new SysRole();
        role.setRoleCode("feedback-role-" + suffix);
        role.setRoleName("意见处理测试角色");
        role.setStatus(RecordStatus.ACTIVE);
        role = roleRepository.save(role);

        SysPermission permission = permissionRepository.findByPermCodeAndDeletedFalse(permissionCode)
                .orElseGet(() -> {
                    SysPermission item = new SysPermission();
                    item.setPermCode(permissionCode);
                    item.setPermName("意见处理");
                    item.setPermType(PermissionType.BUTTON);
                    item.setSortNo(1);
                    item.setStatus(RecordStatus.ACTIVE);
                    return permissionRepository.save(item);
                });

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);

        SysRolePermission rolePermission = new SysRolePermission();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(permission.getId());
        rolePermissionRepository.save(rolePermission);
    }

    private SysUser createUser(String username, UserRole role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash("not-used-in-service-test");
        user.setRole(role);
        user.setStatus(RecordStatus.ACTIVE);
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    private void loginAs(SysUser user) {
        AuthContext.set(AuthenticatedUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .tokenHash("local-test")
                .build());
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
