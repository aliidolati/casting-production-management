package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:security_credential_race;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "app.upload.dir=./target/test-uploads/security-credential-race"})
class UserCredentialConcurrencyIntegrationTest {
    @Autowired EntityManagerFactory factory;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;

    @Test void staleProfileWriteCannotRestorePasswordHashFromBeforeReset() {
        UUID id;
        try(var seed=factory.createEntityManager()) {
            seed.getTransaction().begin();
            var u=new User();u.setUsername("synthetic-credential-"+UUID.randomUUID());
            u.setFullName("کاربر ساختگی");u.setRole(Role.OPERATOR);u.setPassword(encoder.encode("Original@123"));
            seed.persist(u);seed.getTransaction().commit();id=u.getId();
        }
        try(var stale=factory.createEntityManager()) {
            stale.getTransaction().begin();
            var profile=stale.find(User.class,id);
            users.resetPasswordByAdmin(id,"Replacement@456");
            profile.setDepartment("ویرایش مستقل پروفایل");
            stale.getTransaction().commit();
            try(var current=factory.createEntityManager()) {
                var saved=current.find(User.class,id);
                assertTrue(encoder.matches("Replacement@456",saved.getPassword()));
                assertEquals("ویرایش مستقل پروفایل",saved.getDepartment());
            }
        } finally {
            try(var cleanup=factory.createEntityManager()) {
                cleanup.getTransaction().begin();cleanup.remove(cleanup.find(User.class,id));cleanup.getTransaction().commit();
            }
        }
    }
}
