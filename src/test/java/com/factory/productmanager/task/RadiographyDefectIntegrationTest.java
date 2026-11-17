package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.RadiographyDefectRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RadiographyDefectIntegrationTest extends TaskTestBase {
    @Autowired RadiographyDefectRepository defects;
    @Autowired EntityManager entityManager;
    Piece piece;
    UUID lineId;

    @BeforeEach void seedReport() throws Exception {
        piece = newPiece("RD" + UUID.randomUUID().toString().substring(0, 8), "Synthetic RT", 1);
        piece.setCurrentStage(PieceStage.RADIOGRAPHY);
        piece.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
        piece.setProcessDate(LocalDate.of(2026, 10, 1));
        pieceRepository.saveAndFlush(piece);
        String template = call(post("/api/piece-definitions/" + piece.getPieceDefinition().getId() + "/radiography-templates"),
                admin, Map.of("name", "Synthetic", "lines", List.of(Map.of("courseCode", "C1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String report = call(put("/api/pieces/" + piece.getId() + "/radiography-reports"), admin,
                Map.of("templateId", (String) JsonPath.read(template, "$.id"), "lines", List.of(Map.of(
                        "templateLineId", (String) JsonPath.read(template, "$.lines[0].id"), "result", "RS"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        lineId = UUID.fromString(JsonPath.read(report, "$.lines[0].id"));
    }

    private String create(Map<String, Object> fields) throws Exception {
        var payload = new LinkedHashMap<String, Object>(fields);
        payload.put("reportLineId", lineId);
        return call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), admin, payload)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test void switchesBetweenSecondDefectReshootAndNoReviewWithoutChangingCourseOrPiece() throws Exception {
        String body = create(Map.of("defectType", "F.M.M", "secondDefect", Map.of("defectType", "CR")));
        String id = JsonPath.read(body, "$.id");
        String childId = JsonPath.read(body, "$.secondDefect.id");
        call(patch("/api/radiography-defects/" + id), admin, Map.of("defectType", "F.M.M",
                "correctiveAction", "THICKNESS_MEASUREMENT", "reshootOk", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reshootOk").value(true))
                .andExpect(jsonPath("$.secondDefect").isEmpty())
                .andExpect(jsonPath("$.correctiveActionLabel").value("ضخامت مجاز"))
                .andExpect(jsonPath("$.chainText").value(endsWith("ضخامت مجاز → ریشوت → OK")));
        defects.flush(); entityManager.clear();
        assertThat(defects.findById(UUID.fromString(childId))).isEmpty();
        call(get("/api/pieces/" + piece.getId() + "/radiography-reports"), admin)
                .andExpect(jsonPath("$[0].lines[0].result").value("RS"))
                .andExpect(jsonPath("$[0].lines[0].defects[0].reshootOk").value(true));
        Piece unchanged = pieceRepository.findById(piece.getId()).orElseThrow();
        assertThat(unchanged.getRadiographySubStage()).isEqualTo(RadiographySubStage.INTERPRETATION);
        assertThat(unchanged.getProcessDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        call(patch("/api/radiography-defects/" + id), admin,
                Map.of("defectType", "F.M.M", "secondDefect", Map.of("defectType", "S.P")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reshootOk").value(false))
                .andExpect(jsonPath("$.secondDefect.defectType").value("S.P"));
        call(patch("/api/radiography-defects/" + id), admin, Map.of("defectType", "F.M.M", "reshootOk", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secondDefect").isEmpty())
                .andExpect(jsonPath("$.reshootOk").value(false));
    }

    @Test void rejectsBothReviewOutcomes() throws Exception {
        call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), admin,
                Map.of("reportLineId", lineId, "defectType", "CR", "reshootOk", true,
                        "secondDefect", Map.of("defectType", "S.P"))).andExpect(status().isBadRequest());
    }

    @Test void acceptsAllThirteenTypesAndOtherInBothPositions() throws Exception {
        List<String> expected = List.of("F.M.M", "F.M.L", "F.M", "S.P", "S.C", "CR", "G.H", "G.P",
                "VT", "C.S", "M.R", "ماسه ریزی", "ابعادی");
        assertThat(DefectTypes.STANDARD).containsExactlyElementsOf(expected);
        for (String type : expected) {
            String body = create(Map.of("defectType", type, "secondDefect", Map.of("defectType", type)));
            assertThat((String) JsonPath.read(body, "$.defectType")).isEqualTo(type);
            assertThat((String) JsonPath.read(body, "$.secondDefect.defectType")).isEqualTo(type);
            assertThat(DefectTypes.requireStoredType(type, null)).isEqualTo(type);
        }
        String body = create(Map.of("defectType", "OTHER", "defectTypeCustom", "عیب <آزمایشی>",
                "secondDefect", Map.of("defectType", "OTHER", "defectTypeCustom", "عیب دوم دلخواه")));
        assertThat((String) JsonPath.read(body, "$.defectTypeCustom")).isEqualTo("عیب <آزمایشی>");
        assertThat((String) JsonPath.read(body, "$.secondDefect.defectTypeCustom")).isEqualTo("عیب دوم دلخواه");
    }

    @Test void rejectsRemovedCodesAndMissingOtherDescription() throws Exception {
        for (String type : List.of("Sh.P", "Sh.C", "FMM", "FML", "GPR", "GPE", "OTHER")) {
            call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), admin,
                    Map.of("reportLineId", lineId, "defectType", type)).andExpect(status().isBadRequest());
        }
    }

    @Test void reshootIsNotAcceptedOutsideReportOrWithoutPermission() throws Exception {
        call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), admin,
                Map.of("course", "C1", "defectType", "CR", "reshootOk", true)).andExpect(status().isBadRequest());
        call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), worker,
                Map.of("reportLineId", lineId, "defectType", "CR", "reshootOk", true)).andExpect(status().isForbidden());
    }

    @Test void nullLegacyReshootFlagReadsAsFalse() throws Exception {
        String body = create(Map.of("defectType", "CR"));
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        defects.findById(id).orElseThrow().setReshootOk(null);
        defects.flush(); entityManager.clear();
        call(get("/api/pieces/" + piece.getId() + "/radiography-defects"), admin)
                .andExpect(jsonPath("$[0].reshootOk").value(false))
                .andExpect(jsonPath("$[0].chainText").value("CR"));
    }

    @Test void newCatalogWorksInWeldingMapDispatchAndDashboard() throws Exception {
        var definition = piece.getPieceDefinition();
        definition.setDrawingImagePath("synthetic-drawing-not-read.png");
        definitionRepository.saveAndFlush(definition);
        call(post("/api/pieces/" + piece.getId() + "/welding-marks"), admin,
                Map.of("xPercent", 0.1, "yPercent", 0.2, "defectType", "F.M"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.defectType").value("F.M"));
        create(Map.of("defectType", "S.C", "reshootOk", true));
        call(get("/api/dashboard/radiography-defect-report").param("projectId", definition.getProject().getId().toString())
                        .param("defectTypes", "S.C"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].defectType").value("S.C"))
                .andExpect(jsonPath("$.rows[0].defectCount").value(1));
        call(post("/api/pieces/" + piece.getId() + "/welding-station"), admin, Map.of("defectType", "CR"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currentStage").value("WELDING"));
        call(get("/api/pieces/" + piece.getId() + "/welding-dispatches"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].defectType").value("CR"));
    }

    @Test void textPositionsRoundTripWithReshootBeforeWeldingAndKeepNumericInputCompatible() throws Exception {
        String body = create(Map.of("defectType", "F.M.M", "defectClass", "T1", "welded", true, "reshootOk", true,
                "positions", List.of(Map.of("startCm", " L4 "), Map.of("startCm", "L5", "endCm", "L7"),
                        Map.of("startCm", 12, "endCm", 20))));
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.chainText")).isEqualTo("(L4)(L5-L7)(12-20) F.M.M T1 → ریشوت → جوش → OK");
        defects.flush(); entityManager.clear();
        var saved = defects.findById(UUID.fromString(id)).orElseThrow();
        assertThat(saved.getStartCm()).isEqualTo("L4");
        assertThat(saved.getLocationCm()).isEqualTo("L4");
        assertThat(saved.getPositions().get(2).getStartCm()).isEqualTo("12");
        call(get("/api/pieces/" + piece.getId() + "/radiography-reports"), admin)
                .andExpect(jsonPath("$[0].lines[0].defects[0].positions[1].endCm").value("L7"));
        call(patch("/api/radiography-defects/" + id), admin,
                Map.of("defectType", "F.M.M", "welded", true, "reshootOk", true,
                        "correctiveAction", "THICKNESS_MEASUREMENT", "positions", List.of(Map.of("startCm", "L9", "endCm", " "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.positions[0].endCm").value("L9"))
                .andExpect(jsonPath("$.chainText").value("(L9) F.M.M → ریشوت → ضخامت مجاز → جوش → OK"));
    }

    @Test void standaloneTextLocationIsPreservedInPieceDefects() throws Exception {
        call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), admin,
                Map.of("course", "C1", "defectType", "CR", "locationCm", " L4 ", "leadsToWelding", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.locationCm").value("L4"))
                .andExpect(jsonPath("$.positions[0].startCm").value("L4"));
        defects.flush(); entityManager.clear();
        call(get("/api/pieces/" + piece.getId() + "/radiography-defects"), admin)
                .andExpect(jsonPath("$[0].startCm").value("L4"));
    }

    @Test void rejectsEmptyDuplicateAndOversizedTextPositions() throws Exception {
        for (var positions : List.of(List.of(Map.of("startCm", " ")),
                List.of(Map.of("startCm", "L4"), Map.of("startCm", " L4 ", "endCm", "L4")),
                List.of(Map.of("startCm", "L".repeat(256))))) {
            call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), admin,
                    Map.of("reportLineId", lineId, "defectType", "CR", "positions", positions))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void textLabelsKeepExactIdentityWithoutNumericOrLexicalOrdering() throws Exception {
        String body = create(Map.of("defectType", "CR", "positions", List.of(
                Map.of("startCm", "L9", "endCm", "L2"), Map.of("startCm", "01"), Map.of("startCm", "1"),
                Map.of("startCm", "A-B", "endCm", "C"), Map.of("startCm", "A", "endCm", "B-C"))));
        assertThat((List<?>) JsonPath.read(body, "$.positions")).hasSize(5);
        assertThat((String) JsonPath.read(body, "$.chainText")).startsWith("(L9-L2)(01)(1)");
    }

    @Test void freeTextKeepsItsFormattingAndCanBeEditedWithoutRecordingWelding() throws Exception {
        String text = "  L4 → ریشوت\nجوش در شرح <نمونه>  ";
        String body = create(Map.of("entryMode", "FREE_TEXT", "freeText", text));
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        defects.flush();
        entityManager.clear();
        var stored = defects.findById(id).orElseThrow();
        assertThat(stored.getFreeText()).isEqualTo(text);
        assertThat(stored.getEntryMode()).isEqualTo(RadiographyDefectEntryMode.FREE_TEXT);
        assertThat(stored.getDefectType()).isNull();
        assertThat(stored.getStatus()).isNull();
        assertThat(stored.isWelded()).isFalse();
        assertThat(stored.isWeldingConfirmed()).isFalse();
        assertThat(stored.getPositions()).isEmpty();

        call(get("/api/pieces/" + piece.getId() + "/radiography-defects"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].freeText").value(text))
                .andExpect(jsonPath("$[0].chainText").value(text))
                .andExpect(jsonPath("$[0].course").value("C1"));

        String route = "/api/radiography-defects/" + id;
        call(patch(route), admin, Map.of("entryMode", "FREE_TEXT", "freeText", "اصلاح شرح", "defectType", "F.M.L"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.freeText").value("اصلاح شرح"))
                .andExpect(jsonPath("$.defectType").value("F.M.L"));
        call(patch(route), admin, Map.of("entryMode", "STRUCTURED", "defectType", "CR"))
                .andExpect(status().isBadRequest());
        call(patch(route + "/confirm-welding"), admin).andExpect(status().isBadRequest());
    }

    @Test void freeTextRejectsBlankTextStructuredFieldsAndStandaloneRegistration() throws Exception {
        String route = "/api/pieces/" + piece.getId() + "/radiography-defects";
        call(post(route), admin, Map.of("reportLineId", lineId, "entryMode", "FREE_TEXT", "freeText", "  \n "))
                .andExpect(status().isBadRequest());
        call(post(route), admin, Map.of("reportLineId", lineId, "entryMode", "FREE_TEXT", "freeText", "شرح", "status", "NEEDS_WELDING"))
                .andExpect(status().isBadRequest());
        call(post(route), admin, Map.of("entryMode", "FREE_TEXT", "freeText", "شرح"))
                .andExpect(status().isBadRequest());
        call(post(route), admin, Map.of("reportLineId", lineId, "entryMode", "FREE_TEXT", "freeText", "x".repeat(4001)))
                .andExpect(status().isBadRequest());
    }

    @Test void dashboardGroupsAndSearchesTextWithoutCountingItAsWeldingOrApproval() throws Exception {
        create(Map.of("defectType", "F.M.L"));
        create(Map.of("entryMode", "FREE_TEXT", "defectType", "F.M.L", "freeText", "L4 ریشوت"));
        create(Map.of("entryMode", "FREE_TEXT", "defectType", "F.M.L", "freeText", "L5 ریشوت"));
        create(Map.of("entryMode", "FREE_TEXT", "freeText", "بدون رده"));
        String projectId = piece.getPieceDefinition().getProject().getId().toString();
        String report = "/api/dashboard/radiography-defect-report";

        call(get(report).param("projectId", projectId).param("defectTypes", "F.M.L"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].defectCount").value(3))
                .andExpect(jsonPath("$.rows[0].piecesWithDefect").value(1))
                .andExpect(jsonPath("$.rows[0].pieces[0].approvedCount").value(1))
                .andExpect(jsonPath("$.rows[0].pieces[0].needsWeldingCount").value(0))
                .andExpect(jsonPath("$.rows[0].pieces[0].freeTextDefects.length()").value(2));
        call(get(report).param("projectId", projectId).param("textQuery", " l4 "), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].defectCount").value(1))
                .andExpect(jsonPath("$.rows[0].pieces[0].freeTextDefects[0].course").value("C1"))
                .andExpect(jsonPath("$.rows[0].pieces[0].freeTextDefects[0].text").value("L4 ریشوت"));
        call(get(report).param("projectId", projectId).param("defectTypes", "UNCATEGORIZED_FREE_TEXT"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].defectType").value("عیوب متنی بدون دسته‌بندی"));
        call(get(report).param("projectId", projectId).param("textQuery", "ریشوت").param("weldingConfirmed", "false"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows").isEmpty());
    }
}
