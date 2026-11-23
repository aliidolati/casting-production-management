package com.factory.productmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SpectrometryDto {
    private SpectrometryDto() {}

    public record Input(UUID pieceId, LocalDate testDate, BigDecimal al, BigDecimal si, BigDecimal mg,
                        BigDecimal cu, BigDecimal fe, BigDecimal mn, BigDecimal zn, BigDecimal ti) {}

    public record Batch(@NotNull UUID requestId, @NotEmpty List<@NotNull Input> rows) {}

    public record BatchResult(int savedCount, boolean replayed) {}

    public record Update(@NotNull @PositiveOrZero Long version, @NotNull @Valid Input result) {}

    public record Filters(
            @Size(max = 255) String projectCode,
            @Size(max = 255) String pieceName,
            @Size(max = 32) String pieceNumber,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {}

    public record PieceOption(UUID id, Integer pieceNumber, LocalDateTime archivedAt) {}

    // Serialize measurements as plain decimal strings so browser number conversion cannot alter the records.
    public record Row(UUID id, long version, UUID pieceId, UUID definitionId, UUID projectId, String projectCode,
                      String pieceName, int pieceNumber, LocalDate testDate, String al, String si, String mg,
                      String cu, String fe, String mn, String zn, String ti) {
        public Row(UUID id, long version, UUID pieceId, UUID definitionId, UUID projectId, String projectCode,
                   String pieceName, Integer pieceNumber, LocalDate testDate, BigDecimal al, BigDecimal si,
                   BigDecimal mg, BigDecimal cu, BigDecimal fe, BigDecimal mn, BigDecimal zn, BigDecimal ti) {
            this(id, version, pieceId, definitionId, projectId, projectCode, pieceName, pieceNumber, testDate,
                    plain(al), plain(si), plain(mg), plain(cu), plain(fe), plain(mn), plain(zn), plain(ti));
        }
    }

    public record Point(UUID id, String projectCode, String pieceName, int pieceNumber, LocalDate testDate,
                        String mg, String fe, String ti) {
        public Point(UUID id, String projectCode, String pieceName, Integer pieceNumber, LocalDate testDate,
                     BigDecimal mg, BigDecimal fe, BigDecimal ti) {
            this(id, projectCode, pieceName, pieceNumber, testDate, plain(mg), plain(fe), plain(ti));
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
