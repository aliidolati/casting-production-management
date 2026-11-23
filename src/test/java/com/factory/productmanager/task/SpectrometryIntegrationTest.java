package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.SpectrometryDto.Batch;
import com.factory.productmanager.model.dto.SpectrometryDto.Input;
import com.factory.productmanager.model.dto.SpectrometryDto.Update;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.repository.SpectrometryRepository;
import com.factory.productmanager.service.SpectrometryService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:spectrometry;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SpectrometryIntegrationTest extends SpectrometryApiContract {}

@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class SpectrometryApiContract extends TaskTestBase {
    private static final String PATH = "/api/spectrometry";
    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);

    @Autowired private SpectrometryService service;
    @Autowired private SpectrometryRepository spectrometry;

    private Piece piece;
    private String projectCode;
    private UUID projectId;

    @BeforeEach
    void seedPiece() {
        projectCode = "SP%" + UUID.randomUUID().toString().substring(0, 8);
        piece = newPiece(projectCode, "=قطعه, \"آزمون\"", 9);
        projectId = piece.getPieceDefinition().getProject().getId();
        piece.setArchivedAt(LocalDateTime.now());
        piece = pieceRepository.saveAndFlush(piece);
    }

    @Test
    void savesAnAtomicBatchAndReplaysTheReceiptEvenAfterAnIndividualTestIsDeleted() throws Exception {
        var requestId = UUID.randomUUID();
        var batch = new Batch(requestId, List.of(input(BASE, "0.1234567891"), input(BASE.plusDays(4), "0.8")));
        call(post(PATH + "/batch"), admin, batch).andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedCount").value(2)).andExpect(jsonPath("$.replayed").value(false));
        call(post(PATH + "/batch"), admin, batch).andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
        call(post(PATH + "/batch"), admin, new Batch(requestId, List.of(input(BASE, "0.12"))))
                .andExpect(status().isConflict());

        var body = call(get(PATH).param("projectCode", projectCode).param("ascending", "true"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].testDate").value(BASE.toString()))
                .andExpect(jsonPath("$.items[0].mg").value("0.1234567891"))
                .andExpect(jsonPath("$.items[0].zn").value("0"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.items[0].id");
        call(delete(PATH + "/" + id).param("version", "0"), admin).andExpect(status().isNoContent());
        call(post(PATH + "/batch"), admin, batch).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        call(get(PATH).param("projectCode", projectCode), admin).andExpect(jsonPath("$.totalElements").value(1));
        var persistedPiece = pieceRepository.findById(piece.getId()).orElseThrow();
        assertNotNull(persistedPiece.getArchivedAt());
        assertEquals(PieceStage.NOT_STARTED, persistedPiece.getCurrentStage());
    }

    @Test
    void rejectsIncompleteOutOfRangeAndOverpreciseRowsWithoutPartialWrites() throws Exception {
        for (String mg : List.of("-1", "100.0000000001", "0.00000000001")) {
            call(post(PATH + "/batch"), admin, batch(input(BASE, "0.1"), input(BASE, mg)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ردیف 2 · Mg")));
        }
        call(post(PATH + "/batch"), admin, batch(input(BASE, "0.1"), input(BASE, null)))
                .andExpect(status().isBadRequest());
        call(post(PATH + "/batch"), admin,
                batch(input(BASE, "0.1"), input(UUID.randomUUID(), BASE, "0.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ردیف 2 · قطعه پیدا نشد"));
        call(get(PATH).param("projectCode", projectCode), admin).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void concurrentRequestsWithTheSameAccountAndKeyWriteOneBatch() throws Exception {
        var batch = batch(input(BASE, "0.15"), input(BASE, "0.2"));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var attempt = (java.util.concurrent.Callable<Boolean>) () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return service.saveBatch(admin.getUsername(), batch).replayed();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        call(get(PATH).param("projectCode", projectCode), admin).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void historyIsPagedButChartAndCsvUseAllMatchingRowsIncludingTheSameDate() throws Exception {
        var piece19 = new Piece();
        piece19.setPieceDefinition(piece.getPieceDefinition());
        piece19.setPieceNumber(19);
        piece19 = pieceRepository.saveAndFlush(piece19);
        call(post(PATH + "/batch"), admin,
                batch(input(BASE, "0.1111111111"), input(BASE, "0.2"), input(BASE.plusDays(8), "0.3"),
                        input(piece19.getId(), BASE, "0.5")))
                .andExpect(status().isCreated());

        call(get(PATH).param("projectCode", projectCode).param("pieceNumber", "۹")
                        .param("pieceName", "قطعه").param("size", "1").param("dateFrom", BASE.toString())
                        .param("dateTo", BASE.plusDays(8).toString()), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].testDate").value(BASE.plusDays(8).toString()));
        call(get(PATH + "/points").param("projectCode", projectCode).param("pieceNumber", "9")
                        .param("dateTo", BASE.toString()), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].testDate").value(BASE.toString()))
                .andExpect(jsonPath("$[1].testDate").value(BASE.toString()));
        call(get(PATH).param("dateFrom", BASE.plusDays(1).toString()).param("dateTo", BASE.toString()), admin)
                .andExpect(status().isBadRequest());

        String csv = call(get(PATH + "/csv").param("projectCode", projectCode).param("pieceNumber", "9")
                        .param("dateTo", BASE.toString()), admin)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"spectrometry.csv\""))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(3, csv.lines().count());
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("0.1111111111"));
        assertTrue(csv.contains("\"'=قطعه, \"\"آزمون\"\"\""));
    }

    @Test
    void grantsAreIndependentAndOptimisticUpdatesDoNotOverwriteNewerValues() throws Exception {
        var viewer = newUser("spect-view-" + UUID.randomUUID(), Role.OPERATOR, Set.of(Permission.VIEW_SPECTROMETRY));
        var creator = newUser("spect-create-" + UUID.randomUUID(), Role.ADMIN, Set.of(Permission.CREATE_SPECTROMETRY_TEST));
        var editor = newUser("spect-edit-" + UUID.randomUUID(), Role.OPERATOR, Set.of(Permission.EDIT_SPECTROMETRY_TEST));
        var deleter = newUser("spect-delete-" + UUID.randomUUID(), Role.ADMIN, Set.of(Permission.DELETE_SPECTROMETRY_TEST));
        call(get(PATH), outsider).andExpect(status().isForbidden());
        call(get(PATH + "/points"), creator).andExpect(status().isForbidden());
        call(get(PATH + "/csv"), outsider).andExpect(status().isForbidden());
        call(get("/api/dashboard/overview"), viewer).andExpect(status().isForbidden());
        call(get(PATH + "/pieces").param("definitionId", piece.getPieceDefinition().getId().toString()), creator)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].archivedAt").exists());
        call(get(PATH + "/pieces").param("definitionId", piece.getPieceDefinition().getId().toString()), viewer)
                .andExpect(status().isForbidden());
        var batch = batch(input(BASE, "0.01"));
        call(post(PATH + "/batch"), viewer, batch).andExpect(status().isForbidden());
        call(post(PATH + "/batch"), creator, batch).andExpect(status().isCreated());
        var body = call(get(PATH).param("projectCode", projectCode), viewer).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.items[0].id");
        var update = new Update(0L, input(BASE.plusDays(1), "0.7"));
        call(put(PATH + "/" + id), creator, update).andExpect(status().isForbidden());
        call(put(PATH + "/" + id), editor, update).andExpect(status().isNoContent());
        call(put(PATH + "/" + id), editor, update).andExpect(status().isConflict());
        call(get(PATH).param("projectCode", projectCode), viewer).andExpect(jsonPath("$.items[0].mg").value("0.7"));
        call(delete(PATH + "/" + id).param("version", "1"), editor).andExpect(status().isForbidden());
        call(delete(PATH + "/" + id).param("version", "0"), deleter).andExpect(status().isConflict());
        call(delete(PATH + "/" + id).param("version", "1"), deleter).andExpect(status().isNoContent());
        assertFalse(spectrometry.existsById(UUID.fromString(id)));
    }

    @Test
    void catalogDeletionPreviewPreservesAssociatedTestResults() throws Exception {
        call(post(PATH + "/batch"), admin, batch(input(BASE, "0.17"))).andExpect(status().isCreated());
        String url = "/api/projects/" + projectId;
        String response = call(get(url + "/deletion-preview"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.impacts[?(@.key == 'SpectrometryTest')].count").value(org.hamcrest.Matchers.contains(1)))
                .andReturn().getResponse().getContentAsString();
        String confirmation = JsonPath.read(response, "$.confirmationToken");
        call(delete(url).param("confirmationToken", confirmation), admin)
                .andExpect(status().isConflict());
        call(get(PATH).param("projectCode", projectCode), admin).andExpect(jsonPath("$.totalElements").value(1));
    }

    private Batch batch(Input... inputs) {
        return new Batch(UUID.randomUUID(), List.of(inputs));
    }

    private Input input(LocalDate date, String mg) {
        return input(piece.getId(), date, mg);
    }

    private Input input(UUID id, LocalDate date, String mg) {
        return new Input(id, date, new BigDecimal("92.01"), new BigDecimal("6.77"),
                mg == null ? null : new BigDecimal(mg), new BigDecimal("0.01"), new BigDecimal("0.002"),
                new BigDecimal("0.003"), BigDecimal.ZERO, new BigDecimal("0.13"));
    }
}
