package com.dsf.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dsf.auth.entity.SysUser;
import com.dsf.auth.entity.UserRole;
import com.dsf.auth.repository.SysUserRepository;
import com.dsf.common.model.RecordStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "dsf.auth.enabled=true",
        "dsf.auth.bootstrap-admin-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackControllerIntegrationTest {

    private static final String PASSWORD = "Feedback@Test2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SysUser submitter;
    private SysUser otherUser;
    private SysUser admin;
    private String submitterToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        submitter = createUser("feedback-http-user-" + suffix(), UserRole.USER);
        otherUser = createUser("feedback-http-other-" + suffix(), UserRole.USER);
        admin = createUser("feedback-http-admin-" + suffix(), UserRole.ADMIN);
        submitterToken = login(submitter.getUsername());
        otherToken = login(otherUser.getUsername());
        adminToken = login(admin.getUsername());
    }

    @Test
    void enforcesAuthenticationAndCreateValidation() throws Exception {
        mockMvc.perform(get("/api/feedback"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("missing bearer token"));

        mockMvc.perform(post("/api/feedback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(submitterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "短",
                                "description", "太短",
                                "category", "FEATURE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void executesAuthenticatedWorkflowAndProtectsOtherUsers() throws Exception {
        long ticketId = createTicket(submitterToken, "接口意见全流程测试");

        mockMvc.perform(get("/api/feedback/{id}", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权查看该意见"));

        mockMvc.perform(get("/api/feedback")
                        .queryParam("scope", "all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(submitterToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权查看处理工作台"));

        mockMvc.perform(get("/api/feedback/summary")
                        .queryParam("scope", "invalid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的意见范围"));

        mockMvc.perform(put("/api/feedback/{id}/handling", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assigneeId", otherUser.getId(), "assignToMe", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("指定用户没有意见处理权限"));

        mockMvc.perform(put("/api/feedback/{id}/handling", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assignToMe", true, "priority", "HIGH", "plannedVersion", "2026.10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.assigneeName").value(admin.getUsername()))
                .andExpect(jsonPath("$.ticket.priorityLabel").value("高"));

        mockMvc.perform(post("/api/feedback/{id}/transitions", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(submitterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStatus", "CLOSED", "comment", "尝试提前关闭"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前状态不能转为“已关闭”"));
    }

    @Test
    void rejectsUnsupportedAttachmentTypes() throws Exception {
        long ticketId = createTicket(submitterToken, "附件边界校验测试");
        MockMultipartFile file = new MockMultipartFile(
                "file", "unsafe.exe", "application/octet-stream",
                "not-an-allowed-file".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/feedback/{id}/attachments", ticketId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(submitterToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的附件扩展名"));
    }

    private long createTicket(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/feedback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "description", "用于验证意见收集模块的登录鉴权、数据隔离和状态流转。",
                                "category", "FEATURE",
                                "urgency", "NORMAL",
                                "sourceModule", "项目工作台",
                                "sourcePage", "/"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.ticketNo").value(org.hamcrest.Matchers.startsWith("FK-")))
                .andExpect(jsonPath("$.ticket.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("ticket").path("id").asLong();
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "clientType", "TEST",
                                "rememberMe", false))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(response);
        return body.path("token").asText();
    }

    private SysUser createUser(String username, UserRole role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(RecordStatus.ACTIVE);
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
