package com.factory.productmanager.controller;
import com.factory.productmanager.service.RecordEditService;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.RecordEditEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.UUID;
@RestController @RequiredArgsConstructor @RequestMapping("/api")
public class RecordEditController {
 private final RecordEditService service;
 @PatchMapping("/notes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void note(@PathVariable UUID id,@Valid @RequestBody PieceNoteRequest r,Authentication a){service.note(id,r,a.getName());}
 @PatchMapping("/welding-marks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void mark(@PathVariable UUID id,@Valid @RequestBody WeldingMapMarkRequest r,Authentication a){service.mark(id,r,a.getName());}
 @PatchMapping("/heat-treatments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void heat(@PathVariable UUID id,@Valid @RequestBody UpdatePieceHeatTreatmentRequest r,Authentication a){service.heat(id,r,a.getName());}
 @PatchMapping("/melt-passports/materials/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void material(@PathVariable UUID id,@Valid @RequestBody MeltMaterialRequest r,Authentication a){service.material(id,r,a.getName());}
 @GetMapping("/record-edits/{kind}/{id}")
 public PageResponse<RecordEditEvent> history(@PathVariable String kind,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,Authentication a){return service.history(kind,id,a.getName(),page,size);}
}
