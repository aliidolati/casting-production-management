package com.factory.productmanager.model.dto;
import com.factory.productmanager.model.entity.MeltPassportStatus;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
@Getter @Setter
public class MeltPassportFilter {
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate dateFrom;
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate dateTo;
 private Integer furnaceNumber;
 private UUID pieceId;
 private String ingotType;
 private UUID materialId;
 private String operator;
 private MeltPassportStatus status;
}
