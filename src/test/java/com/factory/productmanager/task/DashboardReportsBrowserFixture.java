package com.factory.productmanager.task;

import com.factory.productmanager.ProductManagerApplication;
import com.factory.productmanager.model.dto.SendToWeldingRequest;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.PieceService;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class DashboardReportsBrowserFixture {
    public static void main(String[] ignored) throws Exception {
        var context = SpringApplication.run(ProductManagerApplication.class,
                "--server.address=127.0.0.1", "--server.port=18085",
                "--spring.datasource.url=jdbc:h2:mem:dashboard-reports-browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.jpa.open-in-view=false",
                "--spring.flyway.enabled=false", "--app.upload.dir=./target/test-uploads/dashboard-reports-browser",
                "--app.tasks.reminders.enabled=false", "--logging.level.root=WARN",
                "--logging.level.com.factory.productmanager.config.DataSeeder=OFF");
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(transaction -> {
            var user = new User();
            user.setUsername("reports-ui-admin");
            user.setPassword(context.getBean(PasswordEncoder.class).encode("Synthetic-Reports-Only1"));
            user.setFullName("کاربر ساختگی گزارش‌ها");
            user.setRole(Role.ADMIN);
            context.getBean(UserRepository.class).save(user);
            var project = new Project();
            project.setCode("SMOKE-REPORTS");
            project.setName("پروژه ساختگی گزارش‌ها");
            context.getBean(ProjectRepository.class).save(project);
            var definition = new PieceDefinition();
            definition.setProject(project);
            definition.setRowNum(1);
            definition.setPieceName("بدنه آزمایشی");
            context.getBean(PieceDefinitionRepository.class).save(definition);
            var pieces = context.getBean(PieceRepository.class);
            var welding = new Piece();
            welding.setPieceDefinition(definition);
            welding.setPieceNumber(1);
            welding.setCurrentStage(PieceStage.ROUGH_MACHINING);
            pieces.saveAndFlush(welding);
            var request = new SendToWeldingRequest();
            request.setDefectType("OTHER");
            request.setDefectTypeCustom("عیب آزمایشی");
            request.setNotes("توضیح اولیه");
            context.getBean(PieceService.class).sendToWeldingStation(welding.getId(), request, user.getUsername());
            var tensile = new Piece();
            tensile.setPieceDefinition(definition);
            tensile.setPieceNumber(2);
            tensile.setCurrentStage(PieceStage.TENSILE);
            pieces.save(tensile);
            var test = new TensileTest();
            test.setPiece(tensile);
            test.setTestIndex(1);
            for (int index = 1; index <= 2; index++) {
                var sample = new TensileTestSample();
                sample.setTensileTest(test);
                sample.setSampleIndex(index);
                sample.setSampleLabel("R" + index);
                sample.setUts(new BigDecimal(index == 1 ? "250.25" : "260.50"));
                sample.setYs(new BigDecimal(index == 1 ? "200" : "300"));
                sample.setEl(new BigDecimal(index == 1 ? "4" : "5"));
                test.getSamples().add(sample);
            }
            context.getBean(TensileTestRepository.class).save(test);
            var radiography = new Piece();
            radiography.setPieceDefinition(definition);
            radiography.setPieceNumber(3);
            radiography.setCurrentStage(PieceStage.RADIOGRAPHY);
            radiography.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
            radiography.setProcessDate(LocalDate.now());
            pieces.save(radiography);
            var subDate = new PieceSubStageProcessDate();
            subDate.setPiece(radiography);
            subDate.setKind(SubStageKind.RADIOGRAPHY);
            subDate.setSubStage("SHOT");
            subDate.setProcessDate(LocalDate.now().minusDays(1));
            context.getBean(PieceSubStageProcessDateRepository.class).save(subDate);
        });
        Files.writeString(Path.of("target/dashboard-reports-browser-ready.txt"), Long.toString(ProcessHandle.current().pid()));
    }
}
