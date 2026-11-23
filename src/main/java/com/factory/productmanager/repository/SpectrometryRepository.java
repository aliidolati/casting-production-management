package com.factory.productmanager.repository;

import com.factory.productmanager.model.dto.SpectrometryDto.Point;
import com.factory.productmanager.model.dto.SpectrometryDto.Row;
import com.factory.productmanager.model.entity.SpectrometryTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public interface SpectrometryRepository extends JpaRepository<SpectrometryTest, UUID> {
    String SOURCE = """
            from SpectrometryTest t join t.piece p join p.pieceDefinition d join d.project pr
            where (:projectCode is null or lower(pr.code) like :projectCode escape '!')
              and (:pieceName is null or lower(d.pieceName) like :pieceName escape '!')
              and (:pieceNumber is null or p.pieceNumber = :pieceNumber)
              and (:dateFrom is null or t.testDate >= :dateFrom)
              and (:dateTo is null or t.testDate <= :dateTo)
            """;
    String ROW = """
            select new com.factory.productmanager.model.dto.SpectrometryDto$Row(
                t.id, t.version, p.id, d.id, pr.id, pr.code, d.pieceName, p.pieceNumber, t.testDate,
                t.al, t.si, t.mg, t.cu, t.fe, t.mn, t.zn, t.ti)
            """;

    @Query(value = ROW + SOURCE, countQuery = "select count(t) " + SOURCE)
    Page<Row> history(String projectCode, String pieceName, Integer pieceNumber,
                      LocalDate dateFrom, LocalDate dateTo, Pageable pageable);

    @Query("""
            select new com.factory.productmanager.model.dto.SpectrometryDto$Point(
                t.id, pr.code, d.pieceName, p.pieceNumber, t.testDate, t.mg, t.fe, t.ti)
            """ + SOURCE + " order by t.testDate, t.id")
    List<Point> points(String projectCode, String pieceName, Integer pieceNumber,
                       LocalDate dateFrom, LocalDate dateTo);

    @Query(ROW + SOURCE)
    Stream<Row> export(String projectCode, String pieceName, Integer pieceNumber,
                       LocalDate dateFrom, LocalDate dateTo, Sort sort);
}
