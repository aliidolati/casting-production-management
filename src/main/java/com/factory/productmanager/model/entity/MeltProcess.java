package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalTime;
@Embeddable @Getter @Setter
public class MeltProcess {
 @Column(precision=24,scale=8) private BigDecimal superheatTemperature;
 @Column(precision=24,scale=8) private BigDecimal argonInjectionDuration;
 @Column(precision=24,scale=8) private BigDecimal timeAt610;
 @Column(name="time_at610_clock") private LocalTime timeAt610Clock;
 @Column(precision=24,scale=8) private BigDecimal spiralTest;
 @Column(precision=24,scale=8) private BigDecimal pouringTemperature;
 @Column(precision=24,scale=8) private BigDecimal pressureHoldingTime;
 @Column(precision=24,scale=8) private BigDecimal argonHumidityPercent;
 @Column(precision=24,scale=8) private BigDecimal returnedMeltWeight;
 @Column(precision=24,scale=8) private BigDecimal rotaryFlow;
 @Column(precision=24,scale=8) private BigDecimal rotaryDuration;
 @Column(precision=24,scale=8) private BigDecimal rotarySpeed;
 @Column(precision=24,scale=8) private BigDecimal rotaryTemperature;
 @Column(precision=24,scale=8) private BigDecimal fluxInjectionDuration;
 @Column(precision=24,scale=8) private BigDecimal lanceOutletDiameter;
 @Column(precision=24,scale=8) private BigDecimal injectedCoverallWeight;
 @Column private LocalTime injectionStartTime;
 @Enumerated(EnumType.STRING) @Column(length=16,columnDefinition="varchar(16)") private MeltNozzleLock nozzleLock;
 private Boolean nozzleFull;
 @Column(length=255) private String rptSampleStatus;
 @Column(length=255) private String runnerPipeStatus;
 @Column(length=255) private String lanceNumber;
 @Column(length=255) private String injectedCoverallType;
}
