package com.factory.productmanager.model.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.factory.productmanager.model.entity.MeltNozzleLock;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalTime;
@Getter @Setter
public class MeltProcessData {
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal superheatTemperature;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal argonInjectionDuration;
 @JsonProperty(access=JsonProperty.Access.READ_ONLY) private BigDecimal timeAt610;
 @JsonFormat(pattern="HH:mm", lenient=OptBoolean.FALSE) private LocalTime timeAt610Clock;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal spiralTest;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal pouringTemperature;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal pressureHoldingTime;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") @DecimalMax(value="100",message="درصد نمی‌تواند بیش از ۱۰۰ باشد") private BigDecimal argonHumidityPercent;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal returnedMeltWeight;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal rotaryFlow;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal rotaryDuration;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal rotarySpeed;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal rotaryTemperature;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal fluxInjectionDuration;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal lanceOutletDiameter;
 @DecimalMin(value="0",message="مقدار منفی مجاز نیست") @Digits(integer=16,fraction=8,message="حداکثر ۱۶ رقم صحیح و ۸ رقم اعشار مجاز است") private BigDecimal injectedCoverallWeight;
 private LocalTime injectionStartTime;
 private MeltNozzleLock nozzleLock;
 private Boolean nozzleFull;
 @Size(max=255,message="طول یا تعداد داده باید حداکثر {max} باشد") private String rptSampleStatus;
 @Size(max=255,message="طول یا تعداد داده باید حداکثر {max} باشد") private String runnerPipeStatus;
 @Size(max=255,message="طول یا تعداد داده باید حداکثر {max} باشد") private String lanceNumber;
 @Size(max=255,message="طول یا تعداد داده باید حداکثر {max} باشد") private String injectedCoverallType;
}
