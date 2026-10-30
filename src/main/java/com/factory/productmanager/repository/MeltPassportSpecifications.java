package com.factory.productmanager.repository;

import com.factory.productmanager.model.dto.MeltPassportFilter;
import com.factory.productmanager.model.entity.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

public final class MeltPassportSpecifications {
    private MeltPassportSpecifications() {}
    public static Specification<MeltPassport> matching(MeltPassportFilter f) {
        return (root, query, cb) -> {
            var p = new ArrayList<Predicate>();
            if (f.getDateFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("meltDate"), f.getDateFrom()));
            if (f.getDateTo() != null) p.add(cb.lessThanOrEqualTo(root.get("meltDate"), f.getDateTo()));
            if (f.getFurnaceNumber() != null) p.add(cb.equal(root.get("furnaceNumber"), f.getFurnaceNumber()));
            if (f.getStatus() != null) p.add(cb.equal(root.get("status"), f.getStatus()));
            if (f.getIngotType() != null && !f.getIngotType().isBlank()) p.add(cb.equal(root.get("ingotType"), f.getIngotType().trim()));
            if (f.getOperator() != null && !f.getOperator().isBlank()) p.add(cb.equal(root.get("injectionOperator"), f.getOperator().trim()));
            if (f.getPieceId() != null) {
                var sub = query.subquery(Integer.class);
                var link = sub.from(MeltPassportPiece.class);
                sub.select(cb.literal(1)).where(cb.equal(link.get("passport"), root), cb.equal(link.get("piece").get("id"), f.getPieceId()));
                p.add(cb.exists(sub));
            }
            if (f.getMaterialId() != null) {
                var sub = query.subquery(Integer.class);
                var line = sub.from(MeltConsumption.class);
                sub.select(cb.literal(1)).where(cb.equal(line.get("passport"), root), cb.equal(line.get("material").get("id"), f.getMaterialId()));
                p.add(cb.exists(sub));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
