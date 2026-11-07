package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Separate committed transactions are essential here: an enclosing test rollback would hide failures. */
@SpringBootTest(properties="logging.level.com.factory.productmanager.config.DataSeeder=OFF")
class MeltPassportConcurrencyIntegrationTest {
    @Autowired MeltPassportService service;
    @MockitoSpyBean PieceService pieceService;
    @Autowired PieceRepository pieces;
    @Autowired PieceDefinitionRepository definitions;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired PieceStageHistoryRepository history;
    @Autowired TaskRepository tasks;
    @Autowired PlatformTransactionManager txManager;
    @MockitoSpyBean MeltPassportRepository passports;
    String author;
    String reviewer;
    String administrator;
    List<UUID> ids;

    @BeforeEach void fixture() {
        reset(pieceService,passports);
        String suffix=UUID.randomUUID().toString();author="melt-author-"+suffix;reviewer="melt-review-"+suffix;administrator="melt-admin-"+suffix;
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            for(String username:List.of(author,reviewer,administrator)) {
                var u=new User();u.setUsername(username);u.setFullName(username);u.setPassword("synthetic-test-hash");u.setRole(Role.OPERATOR);
                if(username.equals(administrator)) u.setRole(Role.MANAGER);
                u.setPermissions(new HashSet<>(username.equals(reviewer)?Set.of(Permission.APPROVE_MELT_PASSPORT):Set.of()));users.save(u);
            }
            var project=new Project();project.setCode("MELT-TX-"+suffix);project.setName("Synthetic melt transactions");projects.save(project);
            var def=new PieceDefinition();def.setProject(project);def.setRowNum(1);def.setPieceName("Synthetic casting");definitions.save(def);
            ids=new ArrayList<>();
            for(int n=1;n<=2;n++){var p=new Piece();p.setPieceDefinition(def);p.setPieceNumber(n);p.setCurrentStage(PieceStage.CASTING);ids.add(pieces.save(p).getId());}
        });
        ids=ids.stream().sorted().toList();
    }
    @AfterEach void clearSpy() { reset(pieceService,passports); }

    MeltPassportResponse pending() {
        var r=new MeltPassportRequest();r.setVersion(0L);r.setPieceIds(ids);r.setMeltDate(LocalDate.of(2026,10,18));r.setFurnaceNumber(BigDecimal.ONE);
        r.setIngotType("Synthetic");r.setPrimaryWeight(new BigDecimal("70"));r.setRecycledWeight(new BigDecimal("30"));r.setInjectionOperator("Synthetic operator");
        r.setChecks(Arrays.stream(MeltCheckKind.values()).map(k -> new MeltPassportRequest.CheckLine(k,MeltCheckStatus.ACCEPTED,null)).toList());
        var m=service.create(r,author);return service.submit(m.id(),new MeltDecisionRequest(m.version(),null),author);
    }

    @Test void twoConcurrentApprovalsMoveEachPieceExactlyOnce() throws Exception {
        var m=pending();var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> approve=()->{start.await();try{service.approve(m.id(),new MeltDecisionRequest(m.version(),null),reviewer);return true;}
                catch(com.factory.productmanager.exception.DuplicateResourceException|IllegalStateException e){return false;}};
            var one=executor.submit(approve);var two=executor.submit(approve);start.countDown();
            assertEquals(1,(one.get(20,TimeUnit.SECONDS)?1:0)+(two.get(20,TimeUnit.SECONDS)?1:0));
            assertEquals(MeltPassportStatus.APPROVED,service.get(m.id(),author).status());
            for(UUID id:ids){assertNotEquals(PieceStage.CASTING,pieces.findById(id).orElseThrow().getCurrentStage());assertEquals(1,history.findByPieceIdOrderByChangedAtDesc(id).size());}
            assertEquals(1,service.get(m.id(),author).events().stream().filter(e -> e.action().equals("APPROVED")).count());
            new TransactionTemplate(txManager).executeWithoutResult(tx -> assertTrue(tasks.findByWorkflowIdAndTaskType(m.id(),TaskType.MELT_PASSPORT_APPROVAL).stream().allMatch(t -> t.getStatus()==TaskStatus.COMPLETED)));
        } finally {executor.shutdownNow();}
    }

    @Test void exceptionAfterFirstPieceMoveRollsBackApprovalAllPiecesDatesAndAudit() {
        var m=pending();
        doThrow(new IllegalStateException("synthetic second-piece failure")).when(pieceService).advanceByMeltApproval(ids.get(1),m.meltDate(),reviewer);
        assertThrows(IllegalStateException.class,()->service.approve(m.id(),new MeltDecisionRequest(m.version(),null),reviewer));
        verify(pieceService).advanceByMeltApproval(ids.get(0),m.meltDate(),reviewer);
        var after=service.get(m.id(),author);assertEquals(MeltPassportStatus.PENDING_APPROVAL,after.status());assertNull(after.approvedAt());assertNull(after.approvedBy());
        assertEquals(m.version(),after.version());assertTrue(after.events().stream().noneMatch(e -> e.action().equals("APPROVED")));
        new TransactionTemplate(txManager).executeWithoutResult(tx -> assertEquals(TaskStatus.TODO,tasks.findByWorkflowIdAndTaskType(m.id(),TaskType.MELT_PASSPORT_APPROVAL).get(0).getStatus()));
        for(UUID id:ids){var p=pieces.findById(id).orElseThrow();assertEquals(PieceStage.CASTING,p.getCurrentStage());assertNull(p.getProcessDate());assertTrue(history.findByPieceIdOrderByChangedAtDesc(id).isEmpty());}
    }

    @Test void stalePieceCannotOverwriteApprovalMovement() {
        var m=pending();Piece stale=pieces.findById(ids.get(0)).orElseThrow();
        service.approve(m.id(),new MeltDecisionRequest(m.version(),null),reviewer);
        stale.setArchivedBy("synthetic stale update");
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->pieces.saveAndFlush(stale));
        assertNotEquals(PieceStage.CASTING,pieces.findById(ids.get(0)).orElseThrow().getCurrentStage());
    }

    @Test void changedPieceConditionsPreventApprovalWithoutPartialState() {
        var m=pending();
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {var p=pieces.findById(ids.get(1)).orElseThrow();p.setArchivedAt(java.time.LocalDateTime.now());});
        assertThrows(IllegalStateException.class,()->service.approve(m.id(),new MeltDecisionRequest(m.version(),null),reviewer));
        assertEquals(MeltPassportStatus.PENDING_APPROVAL,service.get(m.id(),author).status());
        assertEquals(PieceStage.CASTING,pieces.findById(ids.get(0)).orElseThrow().getCurrentStage());
    }

    MeltPassportCorrectionRequest correction(MeltPassportResponse m,String note) {
        var r=new MeltPassportRequest();r.setVersion(m.version());r.setPieceIds(ids);r.setMeltDate(m.meltDate());r.setFurnaceNumber(BigDecimal.valueOf(m.furnaceNumber()));
        r.setIngotType(m.ingotType());r.setPrimaryWeight(m.primaryWeight());r.setRecycledWeight(m.recycledWeight());r.setInjectionOperator(m.injectionOperator());r.setChecks(m.checks());r.setNotes(note);
        return new MeltPassportCorrectionRequest(r,null,false);
    }

    @Test void twoConcurrentCorrectionsRejectStaleVersionAndKeepOneAudit() throws Exception {
        var m=pending();m=service.approve(m.id(),new MeltDecisionRequest(m.version(),null),reviewer);
        m=service.get(m.id(),administrator);
        final var approved=m;var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> correct=()->{start.await();try { service.correct(approved.id(),correction(approved,Thread.currentThread().getName()),administrator);return true; }
                catch(com.factory.productmanager.exception.DuplicateResourceException e) {return false;} };
            var first=executor.submit(correct);var second=executor.submit(correct);start.countDown();
            assertEquals(1,(first.get(20,TimeUnit.SECONDS)?1:0)+(second.get(20,TimeUnit.SECONDS)?1:0));
            var after=service.get(m.id(),administrator);assertEquals(1,after.events().stream().filter(e->e.action().equals("ADMIN_CORRECTED")).count());
            assertEquals(m.approvedAt(),after.approvedAt());
            for(UUID id:ids) assertEquals(1,history.findByPieceIdOrderByChangedAtDesc(id).size());
        } finally {executor.shutdownNow();}
    }

    @Test void failedCorrectionRollsBackParentMaterialsAndAuditInCommittedTransactions() {
        var m=pending();m=service.approve(m.id(),new MeltDecisionRequest(m.version(),null),reviewer);
        var request=correction(m,"must roll back");
        final UUID id=m.id();
        doThrow(new IllegalStateException("synthetic flush failure")).when(passports).flush();
        assertThrows(IllegalStateException.class,()->service.correct(id,request,administrator));
        reset(passports);
        var after=service.get(id,administrator);assertNull(after.notes());assertEquals(m.ingotType(),after.ingotType());
        assertEquals(m.version(),after.version());assertEquals(m.events().size(),after.events().size());
    }
    @Test void failedCorrectionRollsBackNewMembershipAndPieceMovement() {
        var pending = pending();
        service.approve(pending.id(),new MeltDecisionRequest(pending.version(),null),reviewer);
        var approved = service.get(pending.id(),administrator);
        UUID addedId = new TransactionTemplate(txManager).execute(tx -> {
            var added = new Piece();
            added.setPieceDefinition(pieces.findById(ids.get(0)).orElseThrow().getPieceDefinition());
            added.setPieceNumber(3);
            added.setCurrentStage(PieceStage.CASTING);
            return pieces.saveAndFlush(added).getId();
        });
        var data = correction(approved,"must roll back").data();
        var selected = new ArrayList<>(ids);
        selected.add(addedId);
        data.setPieceIds(selected);
        var request = new MeltPassportCorrectionRequest(data,null,false,PieceStage.RADIOGRAPHY,null);
        doThrow(new IllegalStateException("synthetic flush failure")).when(passports).flush();
        assertThrows(IllegalStateException.class,() -> service.correct(approved.id(),request,administrator));
        reset(passports);

        var after = service.get(approved.id(),administrator);
        assertEquals(approved.version(),after.version());
        assertEquals(approved.pieces(),after.pieces());
        assertEquals(approved.events(),after.events());
        assertTrue(service.forPiece(addedId,administrator).isEmpty());
        assertEquals(PieceStage.CASTING,pieces.findById(addedId).orElseThrow().getCurrentStage());
        assertTrue(history.findByPieceIdOrderByChangedAtDesc(addedId).isEmpty());
    }

}
