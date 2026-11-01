package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserRoleManagementIntegrationTest extends TaskTestBase {
    @Autowired EntityManager em;
    private Map<String,Object> edit(String name, Role role, String... permissions) {
        return Map.of("fullName", name, "role", role.name(), "permissions", List.of(permissions));
    }
    @Test void adminWithEveryGrantStillCannotManageAccountsOrReadTheirAudit() throws Exception {
        var limited = newUser("limited_" + UUID.randomUUID(), Role.ADMIN, EnumSet.allOf(Permission.class));
        for (var actor : List.of(limited, worker)) {
            call(get("/api/users"), actor).andExpect(status().isForbidden());
            call(patch("/api/users/"+actor.getId()), actor, edit("ارتقای ممنوع", Role.MANAGER)).andExpect(status().isForbidden());
            call(patch("/api/users/"+actor.getId()+"/permissions"), actor, Map.of("permissions", List.of())).andExpect(status().isForbidden());
            call(patch("/api/users/"+outsider.getId()+"/profile"), actor, Map.of("active", false)).andExpect(status().isForbidden());
            call(get("/api/users/"+outsider.getId()+"/access-history"), actor).andExpect(status().isForbidden());
            call(delete("/api/users/"+outsider.getId()), actor).andExpect(status().isForbidden());
        }
    }
    @Test void managerCanEditNameAndRoleAtomicallyAndAuditExcludesCredentials() throws Exception {
        UUID id = outsider.getId(); String username = outsider.getUsername(); String old = token(outsider);
        call(patch("/api/users/"+id), admin, edit("نام اصلاح‌شده", Role.ADMIN, "MANAGE_PROJECTS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value(username)).andExpect(jsonPath("$.id").value(id.toString()));
        em.flush(); em.clear();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer "+old)).andExpect(status().isUnauthorized());
        var audit = call(get("/api/users/"+id+"/access-history"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("ACCOUNT_UPDATED"))
                .andExpect(jsonPath("$.items[0].actorUsername").value(admin.getUsername()))
                .andReturn().getResponse().getContentAsString();
        assertFalse(audit.contains("password")); assertFalse(audit.contains(PASSWORD));
        assertEquals("نام اصلاح‌شده", userRepository.findById(id).orElseThrow().getFullName());
    }
    @Test void adminUsesSelectedGrantsAndManagerCanClearThem() throws Exception {
        var limited = newUser("limited_" + UUID.randomUUID(), Role.ADMIN, Set.of(Permission.MANAGE_PROJECTS));
        call(post("/api/projects"), limited, Map.of("code", "ADMIN-"+UUID.randomUUID(), "name", "پروژه ساختگی"))
                .andExpect(status().isCreated());
        call(get("/api/dashboard/stats"), limited).andExpect(status().isForbidden());
        call(patch("/api/users/"+limited.getId()+"/permissions"), admin, Map.of("permissions", List.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isEmpty());
    }
    @Test void lastActiveManagerCannotBeDemotedDisabledOrDeleted() throws Exception {
        for (var user : userRepository.findAll()) {
            if (user.getRole() == Role.MANAGER && !user.getId().equals(admin.getId())) user.setRole(Role.OPERATOR);
        }
        userRepository.flush();
        call(patch("/api/users/"+admin.getId()), admin, edit("مدیر", Role.ADMIN)).andExpect(status().isConflict());
        call(patch("/api/users/"+admin.getId()+"/profile"), admin, Map.of("active", false)).andExpect(status().isConflict());
        call(delete("/api/users/"+admin.getId()), admin).andExpect(status().isConflict());
        assertEquals(Role.MANAGER, userRepository.findById(admin.getId()).orElseThrow().getRole());
    }
    @Test void nameOnlyEditKeepsSessionAndInvalidInputDoesNotWriteAudit() throws Exception {
        var before = token(outsider);
        call(patch("/api/users/"+outsider.getId()), admin, edit("نام تازه", Role.OPERATOR))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer "+before)).andExpect(status().isOk());
        call(patch("/api/users/"+outsider.getId()), admin, edit(" ", Role.ADMIN)).andExpect(status().isBadRequest());
        call(get("/api/users/"+outsider.getId()+"/access-history"), admin).andExpect(jsonPath("$.totalElements").value(1));
    }
}
