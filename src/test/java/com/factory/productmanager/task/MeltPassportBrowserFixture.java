package com.factory.productmanager.task;

import com.factory.productmanager.ProductManagerApplication;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.model.dto.MeltPassportRequest;
import com.factory.productmanager.service.MeltPassportService;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.nio.file.*;

/** Optional local browser smoke fixture; always uses an isolated synthetic H2 database and test uploads. */
public class MeltPassportBrowserFixture {
    public static void main(String[] ignored) throws Exception {
        var context=SpringApplication.run(ProductManagerApplication.class,
                "--server.address=127.0.0.1", "--server.port=18084",
                "--spring.datasource.url=jdbc:h2:mem:melt-browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.jpa.open-in-view=false", "--spring.flyway.enabled=false",
                "--app.upload.dir=./target/test-uploads/melt-browser", "--app.tasks.reminders.enabled=false",
                "--logging.level.root=WARN", "--logging.level.com.factory.productmanager.config.DataSeeder=OFF");
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
            var users=context.getBean(UserRepository.class);var encoder=context.getBean(PasswordEncoder.class);
            for(String username:List.of("melt-ui-operator","melt-ui-reviewer","melt-ui-admin")) {
                var u=new User();u.setUsername(username);u.setPassword(encoder.encode("Synthetic-Melt-Only1"));u.setFullName(username.equals("melt-ui-operator")?"اپراتور آزمایشی":"کنترل کیفیت آزمایشی");u.setRole(Role.OPERATOR);
                u.setPermissions(new HashSet<>(username.endsWith("reviewer")?Set.of(Permission.APPROVE_MELT_PASSPORT,Permission.VIEW_DASHBOARD):Set.of(Permission.ADVANCE_PIECE_STAGE,Permission.MOVE_PIECE_STAGE,Permission.RECEIVE_TASKS)));
                if(username.endsWith("admin")) u.setRole(Role.MANAGER);
                users.save(u);
            }
            var assigner = new User();
            assigner.setUsername("melt-ui-assigner");
            assigner.setPassword(encoder.encode("Synthetic-Melt-Only1"));
            assigner.setFullName("تخصیص‌دهنده آزمایشی");
            assigner.setRole(Role.OPERATOR);
            assigner.setPermissions(new HashSet<>(Set.of(Permission.ASSIGN_MELT_OPERATOR)));
            users.save(assigner);
            var project=new Project();project.setCode("SMOKE-MELT");project.setName("پروژه ساختگی آزمون ذوب");context.getBean(ProjectRepository.class).save(project);
            var def=new PieceDefinition();def.setProject(project);def.setRowNum(1);def.setPieceName("بدنه آزمایشی");context.getBean(PieceDefinitionRepository.class).save(def);
            for(int n=1;n<=4;n++){var p=new Piece();p.setPieceDefinition(def);p.setPieceNumber(n);p.setCurrentStage(n==4?PieceStage.ROUGH_MACHINING:PieceStage.CASTING);context.getBean(PieceRepository.class).save(p);}
            var legacyPiece=context.getBean(PieceRepository.class).findAll().stream().filter(p -> p.getPieceNumber()==3).findFirst().orElseThrow();
            var request=new MeltPassportRequest();request.setVersion(0L);request.setPieceIds(List.of(legacyPiece.getId()));
            var legacy=context.getBean(MeltPassportService.class).create(request,"melt-ui-operator");
            var passport=context.getBean(MeltPassportRepository.class).findById(legacy.id()).orElseThrow();
            passport.getProcess().setTimeAt610(new java.math.BigDecimal("15.75"));
            var material=new MeltMaterial();material.setName("ماده قدیمی آزمایشی");material.setNormalizedName("ماده قدیمی آزمایشی");material.setCategory("اصلاح‌کننده");material.setActive(true);
            context.getBean(MeltMaterialRepository.class).save(material);
        });
        Files.writeString(Path.of("target/melt-browser-ready.txt"),Long.toString(ProcessHandle.current().pid()));
        System.out.println("SYNTHETIC_MELT_BROWSER_READY");
    }
}
