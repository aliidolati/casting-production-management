package com.factory.productmanager.task;

import com.factory.productmanager.config.DataSeeder;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.model.dto.UpdateDescriptionRequest;
import com.factory.productmanager.security.*;
import com.factory.productmanager.service.PieceService;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityRegressionIntegrationTest extends TaskTestBase {
    @Autowired EntityManager em;
    @Autowired PieceService pieces;
    @Autowired DataSeeder seeder;
    @Autowired JwtService jwt;
    @Autowired CustomUserDetailsService details;
    @Value("${app.jwt.secret}") String testKey;

    private String freshLogin(String username,String password) throws Exception {
        var body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",username,"password",password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body,"$.token");
    }
    private void me(String token,int expected) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().is(expected));
    }
    @Test void administratorResetRevokesEveryExistingTokenAndNewPasswordWorks() throws Exception {
        String first=token(outsider),second=freshLogin(outsider.getUsername(),PASSWORD);
        call(patch("/api/users/"+outsider.getId()+"/password"),admin,Map.of("newPassword","Replacement@6789"))
                .andExpect(status().isNoContent());
        em.flush();em.clear();me(first,401);me(second,401);
        me(freshLogin(outsider.getUsername(),"Replacement@6789"),200);
    }
    @Test void ownPasswordChangeRevokesOldTokenAndRejectsOldPassword() throws Exception {
        String old=token(worker);
        call(patch("/api/users/me/password"),worker,Map.of("currentPassword",PASSWORD,"newPassword","Changed@6789"))
                .andExpect(status().isNoContent());
        em.flush();em.clear();me(old,401);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",worker.getUsername(),"password",PASSWORD))))
                .andExpect(status().isUnauthorized());
        me(freshLogin(worker.getUsername(),"Changed@6789"),200);
    }
    @Test void resettingToSamePasswordStillRevokesOldToken() throws Exception {
        String old=token(outsider);
        call(patch("/api/users/"+outsider.getId()+"/password"),admin,Map.of("newPassword",PASSWORD)).andExpect(status().isNoContent());
        em.flush();em.clear();me(old,401);me(freshLogin(outsider.getUsername(),PASSWORD),200);
    }
    @Test void reusedUsernameCannotResurrectDeletedAccountsTokenOrInheritAdminRole() throws Exception {
        String old=token(outsider),username=outsider.getUsername();UUID original=outsider.getId();
        call(delete("/api/users/"+original),admin).andExpect(status().isNoContent());em.flush();em.clear();me(old,401);
        var replacement=newUser(username,Role.MANAGER,Set.of());assertNotEquals(original,replacement.getId());
        mvc.perform(get("/api/users").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        me(freshLogin(username,PASSWORD),200);
    }
    @Test void legacyTokenWithCorrectSignatureIsRejected() throws Exception {
        var key=Keys.hmacShaKeyFor(testKey.getBytes(StandardCharsets.UTF_8));
        String old=Jwts.builder().subject(outsider.getUsername()).claim("role","MANAGER").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+60_000)).signWith(key).compact();
        me(old,401);
    }
    @Test void databaseRoleAndPermissionRevocationStillOverrideTokenClaims() throws Exception {
        String claimedAdmin=jwt.generateToken(details.loadUserById(worker.getId()),"MANAGER");
        mvc.perform(get("/api/users").header("Authorization","Bearer "+claimedAdmin)).andExpect(status().isForbidden());
        var p=newPiece("SEC-"+UUID.randomUUID(),"قطعه امنیتی",1);
        worker.getPermissions().add(Permission.MANAGE_PIECES);userRepository.saveAndFlush(worker);
        String old=token(worker);
        call(patch("/api/users/"+worker.getId()+"/permissions"),admin,Map.of("permissions",List.of("RECEIVE_TASKS"))).andExpect(status().isOk());
        mvc.perform(patch("/api/pieces/"+p.getId()+"/description").header("Authorization","Bearer "+old)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"ممنوع\"}")).andExpect(status().isUnauthorized());
    }
    @Test void startupPreservesEmptyAndExplicitOperatorPermissions() {
        var empty=Set.copyOf(outsider.getPermissions());
        var granted=Set.copyOf(worker.getPermissions());
        seeder.run();seeder.run();em.flush();em.clear();
        assertEquals(empty,userRepository.findById(outsider.getId()).orElseThrow().getPermissions());
        assertEquals(granted,userRepository.findById(worker.getId()).orElseThrow().getPermissions());
    }
    @Test void descriptionRequiresManagePiecesAtBothHttpAndServiceBoundaries() throws Exception {
        var p=newPiece("SEC-"+UUID.randomUUID(),"قطعه امنیتی",1);
        for(var actor:List.of(outsider,worker)) call(patch("/api/pieces/"+p.getId()+"/description"),actor,
                Map.of("description","بدون مجوز")).andExpect(status().isForbidden());
        var request=new UpdateDescriptionRequest();request.setDescription("بدون مجوز");
        assertThrows(AccessDeniedException.class,()->pieces.updateDescription(p.getId(),request,outsider.getUsername()));
        assertNull(pieceRepository.findById(p.getId()).orElseThrow().getDescription());
    }
    @Test void adminAndExplicitPieceManagerCanUpdateDescriptionButArchivedPieceIsProtected() throws Exception {
        var p=newPiece("SEC-"+UUID.randomUUID(),"قطعه امنیتی",1);
        worker.getPermissions().add(Permission.MANAGE_PIECES);userRepository.saveAndFlush(worker);
        call(patch("/api/pieces/"+p.getId()+"/description"),worker,Map.of("description","ویرایش مجاز"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("ویرایش مجاز"));
        call(patch("/api/pieces/"+p.getId()+"/description"),admin,Map.of("description","ویرایش مدیر")).andExpect(status().isOk());
        call(delete("/api/pieces/"+p.getId()),admin).andExpect(status().isNoContent());
        call(patch("/api/pieces/"+p.getId()+"/description"),admin,Map.of("description","ممنوع پس از بایگانی")).andExpect(status().isBadRequest());
    }
}
