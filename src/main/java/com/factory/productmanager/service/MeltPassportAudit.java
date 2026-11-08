package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Business values only: no live entities, generated row IDs, permissions or recursive event history. */
@Component
@RequiredArgsConstructor
class MeltPassportAudit {
    private final JsonMapper json;

    String snapshot(MeltPassport m, MeltPassportRequest.MagnesiumInput magnesium) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("meltDate", m.getMeltDate()); values.put("furnaceNumber", m.getFurnaceNumber());
        values.put("ingotType", m.getIngotType()); values.put("primaryBatch", m.getPrimaryBatch());
        values.put("recycledBatch", m.getRecycledBatch()); values.put("primaryWeight", m.getPrimaryWeight());
        values.put("recycledWeight", m.getRecycledWeight()); values.put("injectionOperator", m.getInjectionOperator());
        values.put("notes", m.getNotes()); values.put("additionalNotes", m.getAdditionalNotes());
        values.put("process", MeltProcessMapper.toData(m.getProcess())); values.put("magnesium", magnesium);
        values.put("magnesiumResult", m.getMagnesiumResult()); values.put("magnesiumCalculatedAt", m.getMagnesiumCalculatedAt());
        values.put("materials", m.getMaterials().stream().map(l -> new MeltPassportResponse.MaterialLine(
                l.getMaterial().getId(), l.getMaterialName(), l.getCategory(), l.getQuantity(), l.getUnit(),
                l.getBaseQuantity(), l.getBaseUnit(), l.getAddedAt(), l.getBatchNumber(), l.getNotes())).toList());
        values.put("pieceRefs", m.getPieces().stream().map(MeltPassportPiece::getPieceRef).sorted().toList());
        values.put("checks", m.getChecks().stream().sorted(Comparator.comparing(c -> c.getKind().name()))
                .map(c -> new MeltPassportRequest.CheckLine(c.getKind(),c.getStatus(),c.getNotes())).toList());
        return json.writeValueAsString(canonical(read(json.writeValueAsString(values))));
    }

    // Keep decimal precision and ignore scale-only differences (e.g. 70 versus 70.00000000).
    private Object canonical(JsonNode node) {
        if (node.isNumber()) return node.decimalValue().stripTrailingZeros();
        if (node.isObject()) {
            Map<String,Object> result = new TreeMap<>();
            node.properties().forEach(e -> result.put(e.getKey(),canonical(e.getValue())));
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>(); node.forEach(n -> result.add(canonical(n))); return result;
        }
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        return node.asString();
    }

    JsonNode read(String value) {
        return value == null ? null : json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
    }
}
