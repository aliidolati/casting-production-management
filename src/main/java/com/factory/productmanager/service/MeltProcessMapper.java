package com.factory.productmanager.service;
import com.factory.productmanager.model.dto.MeltProcessData;
import com.factory.productmanager.model.entity.MeltProcess;
final class MeltProcessMapper {
 static MeltProcess toEntity(MeltProcessData source, MeltProcess previous) {
  MeltProcess result = new MeltProcess();
  // Legacy numbers have no established clock conversion and are never client-writable.
  result.setTimeAt610(previous == null ? null : previous.getTimeAt610());
  if (source == null) return result;
  result.setSuperheatTemperature(source.getSuperheatTemperature());
  result.setArgonInjectionDuration(source.getArgonInjectionDuration());
  result.setTimeAt610Clock(source.getTimeAt610Clock());
  result.setSpiralTest(source.getSpiralTest());
  result.setPouringTemperature(source.getPouringTemperature());
  result.setPressureHoldingTime(source.getPressureHoldingTime());
  result.setArgonHumidityPercent(source.getArgonHumidityPercent());
  result.setReturnedMeltWeight(source.getReturnedMeltWeight());
  result.setRotaryFlow(source.getRotaryFlow());
  result.setRotaryDuration(source.getRotaryDuration());
  result.setRotarySpeed(source.getRotarySpeed());
  result.setRotaryTemperature(source.getRotaryTemperature());
  result.setFluxInjectionDuration(source.getFluxInjectionDuration());
  result.setLanceOutletDiameter(source.getLanceOutletDiameter());
  result.setInjectedCoverallWeight(source.getInjectedCoverallWeight());
  result.setRptSampleStatus(source.getRptSampleStatus());
  result.setRunnerPipeStatus(source.getRunnerPipeStatus());
  result.setLanceNumber(source.getLanceNumber());
  result.setInjectedCoverallType(source.getInjectedCoverallType());
  result.setInjectionStartTime(source.getInjectionStartTime());
  result.setNozzleLock(source.getNozzleLock());
  result.setNozzleFull(source.getNozzleFull());
  return result;
 }
 static MeltProcessData toData(MeltProcess source) {
  MeltProcessData result = new MeltProcessData();
  if (source == null) return result;
  result.setSuperheatTemperature(source.getSuperheatTemperature());
  result.setArgonInjectionDuration(source.getArgonInjectionDuration());
  result.setTimeAt610(source.getTimeAt610());
  result.setTimeAt610Clock(source.getTimeAt610Clock());
  result.setSpiralTest(source.getSpiralTest());
  result.setPouringTemperature(source.getPouringTemperature());
  result.setPressureHoldingTime(source.getPressureHoldingTime());
  result.setArgonHumidityPercent(source.getArgonHumidityPercent());
  result.setReturnedMeltWeight(source.getReturnedMeltWeight());
  result.setRotaryFlow(source.getRotaryFlow());
  result.setRotaryDuration(source.getRotaryDuration());
  result.setRotarySpeed(source.getRotarySpeed());
  result.setRotaryTemperature(source.getRotaryTemperature());
  result.setFluxInjectionDuration(source.getFluxInjectionDuration());
  result.setLanceOutletDiameter(source.getLanceOutletDiameter());
  result.setInjectedCoverallWeight(source.getInjectedCoverallWeight());
  result.setRptSampleStatus(source.getRptSampleStatus());
  result.setRunnerPipeStatus(source.getRunnerPipeStatus());
  result.setLanceNumber(source.getLanceNumber());
  result.setInjectedCoverallType(source.getInjectedCoverallType());
  result.setInjectionStartTime(source.getInjectionStartTime());
  result.setNozzleLock(source.getNozzleLock());
  result.setNozzleFull(source.getNozzleFull());
  return result;
 }
}
