package com.factory.productmanager.model.dto;
import com.factory.productmanager.model.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Getter @Setter
public class MeltPassportRequest {
 @NotNull(message="این مقدار الزامی است") private Long version;
 @NotEmpty(message="حداقل یک قطعه انتخاب کنید") @Size(max=200,message="طول یا تعداد داده باید حداکثر {max} باشد") private List<@NotNull(message="این مقدار الزامی است") UUID> pieceIds;
 private LocalDate meltDate;
 // Keep the raw decimal until validation: Jackson otherwise truncates 3.5 into Integer 3.
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @DecimalMax(value="2147483647",message="شماره کوره خارج از محدوده مجاز است") @Digits(integer=10,fraction=0,message="شماره کوره باید عدد صحیح معتبر باشد") private BigDecimal furnaceNumber;
 @Size(max=160,message="طول یا تعداد داده باید حداکثر {max} باشد") private String ingotType;
 @Size(max=160,message="طول یا تعداد داده باید حداکثر {max} باشد") private String primaryBatch;
 @Size(max=160,message="طول یا تعداد داده باید حداکثر {max} باشد") private String recycledBatch;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal primaryWeight;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal recycledWeight;
 @Size(max=160,message="طول یا تعداد داده باید حداکثر {max} باشد") private String injectionOperator;
 @Size(max=4000,message="طول یا تعداد داده باید حداکثر {max} باشد") private String notes;
 @Size(max=4000,message="طول یا تعداد داده باید حداکثر {max} باشد") private String additionalNotes;
 @Valid private MeltProcessData process = new MeltProcessData();
 @Valid private MagnesiumInput magnesium = new MagnesiumInput();
 @NotNull(message="این مقدار الزامی است") @Size(max=200,message="طول یا تعداد داده باید حداکثر {max} باشد") private List<@NotNull(message="این مقدار الزامی است") @Valid MaterialLine> materials = new ArrayList<>();
 @NotNull(message="این مقدار الزامی است") @Size(max=6,message="طول یا تعداد داده باید حداکثر {max} باشد") private List<@NotNull(message="این مقدار الزامی است") @Valid CheckLine> checks = new ArrayList<>();
 @Getter @Setter public static class MagnesiumInput {
  @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal mg;
  @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal rotary;
  @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal at610;
  @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal ar;
  @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal b;
  @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal w;
 }
 public record MaterialLine(@NotNull(message="این مقدار الزامی است") UUID materialId,
  @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") BigDecimal quantity,
  @NotNull(message="این مقدار الزامی است") MeltMaterialUnit unit, LocalTime addedAt,
  @Size(max=160,message="طول یا تعداد داده باید حداکثر {max} باشد") String batchNumber, @Size(max=2000,message="طول یا تعداد داده باید حداکثر {max} باشد") String notes) {}
 public record CheckLine(@NotNull(message="این مقدار الزامی است") MeltCheckKind kind, @NotNull(message="این مقدار الزامی است") MeltCheckStatus status, @Size(max=2000,message="طول یا تعداد داده باید حداکثر {max} باشد") String notes) {}
}
