package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * پایه‌ی تست‌های یکپارچه‌ی وظایف: برنامه‌ی کامل (Spring + Security + JPA) روی H2 اجرا می‌شود
 * و هر تست داخل یک تراکنش rollback می‌شود؛ پس تست‌ها مستقل از هم‌اند.
 * ورود واقعی از /api/auth/login انجام می‌شود تا زنجیره‌ی JWT هم در تست‌ها باشد.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class TaskTestBase {

    static final String PASSWORD = "Test@12345";

    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected TaskRepository taskRepository;
    @Autowired protected ProjectRepository projectRepository;
    @Autowired protected PieceDefinitionRepository definitionRepository;
    @Autowired protected PieceRepository pieceRepository;
    @Autowired protected SystemNotificationRepository notificationRepository;

    protected User admin;
    protected User manager;
    protected User otherManager;
    protected User worker;
    protected User otherWorker;
    /** کاربر عادی که دسترسی «دریافت وظیفه» ندارد */
    protected User outsider;
    protected User disabledWorker;

    @Autowired protected com.factory.productmanager.security.JwtService testJwt;
    private final Map<UUID, String> tokens = new HashMap<>();
    private final Map<UUID, String> tokenStates = new HashMap<>();

    @BeforeEach
    void seedUsers() {
        tokens.clear(); tokenStates.clear();
        String s = UUID.randomUUID().toString().substring(0, 8);
        admin = newUser("t_admin_" + s, Role.MANAGER, Set.of());
        manager = newUser("t_mgr_" + s, Role.OPERATOR, Set.of(Permission.MANAGE_TASKS, Permission.RECEIVE_TASKS));
        otherManager = newUser("t_mgr2_" + s, Role.OPERATOR, Set.of(Permission.MANAGE_TASKS, Permission.RECEIVE_TASKS));
        worker = newUser("t_w1_" + s, Role.OPERATOR, Set.of(Permission.RECEIVE_TASKS));
        otherWorker = newUser("t_w2_" + s, Role.OPERATOR, Set.of(Permission.RECEIVE_TASKS));
        outsider = newUser("t_out_" + s, Role.OPERATOR, Set.of());
        disabledWorker = newUser("t_dis_" + s, Role.OPERATOR, Set.of(Permission.RECEIVE_TASKS));
        disabledWorker.setDisabled(true);
        userRepository.saveAndFlush(disabledWorker);
    }

    protected User newUser(String username, Role role, Set<Permission> permissions) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setFullName("کاربر " + username);
        user.setRole(role);
        user.setPermissions(new HashSet<>(permissions));
        return userRepository.saveAndFlush(user);
    }

    protected String token(User user) {
        // Ordinary calls use a fresh login after grant/role changes. Tests of old JWTs keep their token explicitly.
        String state = testJwt.credentialStamp(userRepository.findById(user.getId()).orElseThrow());
        if (!state.equals(tokenStates.put(user.getId(), state))) tokens.remove(user.getId());
        return tokens.computeIfAbsent(user.getId(), id -> {
            try {
                String body = mvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", PASSWORD))))
                        .andReturn().getResponse().getContentAsString();
                return JsonPath.read(body, "$.token");
            } catch (Exception e) {
                throw new IllegalStateException("login failed for " + user.getUsername(), e);
            }
        });
    }

    protected ResultActions call(MockHttpServletRequestBuilder builder, User as, Object body) throws Exception {
        if (as != null) {
            builder.header("Authorization", "Bearer " + token(as));
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    protected ResultActions call(MockHttpServletRequestBuilder builder, User as) throws Exception {
        return call(builder, as, null);
    }

    // ---- ساخت داده ----

    protected static String dueIn(long days) {
        return LocalDateTime.now().plusDays(days).withSecond(0).withNano(0).toString();
    }

    protected Map<String, Object> taskBody(String title, User assignee, TaskPriority priority, String due) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("assigneeId", assignee.getId());
        body.put("priority", priority.name());
        if (due != null) {
            body.put("dueDate", due);
        }
        return body;
    }

    /** وظیفه را از طریق API می‌سازد و شناسه‌اش را برمی‌گرداند */
    protected String createTask(User creator, User assignee, String title, TaskPriority priority, String due) throws Exception {
        String response = call(post("/api/tasks"), creator, taskBody(title, assignee, priority, due))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    protected Task reloadTask(String id) {
        taskRepository.flush();
        return taskRepository.findDetailedById(UUID.fromString(id)).orElseThrow();
    }

    /** یک پروژه + یک طرح قطعه + یک قطعه برای تست ارتباط وظیفه با قطعه */
    protected Piece newPiece(String projectCode, String pieceName, int number) {
        Project project = new Project();
        project.setCode(projectCode);
        project.setName("پروژه " + projectCode);
        project = projectRepository.saveAndFlush(project);

        PieceDefinition definition = new PieceDefinition();
        definition.setProject(project);
        definition.setRowNum(1);
        definition.setPieceName(pieceName);
        definition = definitionRepository.saveAndFlush(definition);

        Piece piece = new Piece();
        piece.setPieceDefinition(definition);
        piece.setPieceNumber(number);
        return pieceRepository.saveAndFlush(piece);
    }

    protected static List<String> ids(String json, String path) {
        return JsonPath.read(json, path);
    }
}
