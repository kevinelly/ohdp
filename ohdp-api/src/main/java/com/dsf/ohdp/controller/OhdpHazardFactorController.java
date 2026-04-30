package com.dsf.ohdp.controller;

import com.dsf.ohdp.dto.HazardFactorDto;
import com.dsf.ohdp.service.OhdpHazardFactorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OHDP-HazardFactor")
@RestController
@RequestMapping("/api/ohdp")
@RequiredArgsConstructor
public class OhdpHazardFactorController {

    private final OhdpHazardFactorService hazardFactorService;

    // -------------------------------------------------------------------------
    // HazardFactor endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/hazard-factors")
    public Page<HazardFactorDto.HazardFactorResponse> pageHazardFactors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String factorCategory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return hazardFactorService.pageHazardFactors(keyword, factorCategory, page, size);
    }

    @GetMapping("/hazard-factors/all")
    public List<HazardFactorDto.HazardFactorResponse> listAllHazardFactors() {
        return hazardFactorService.listAllHazardFactors();
    }

    @GetMapping("/hazard-factors/{id}")
    public HazardFactorDto.HazardFactorDetailResponse getHazardFactor(@PathVariable Long id) {
        return hazardFactorService.getHazardFactor(id);
    }

    @PostMapping("/hazard-factors")
    public HazardFactorDto.HazardFactorResponse createHazardFactor(
            @Valid @RequestBody HazardFactorDto.HazardFactorRequest request) {
        return hazardFactorService.createHazardFactor(request);
    }

    @PutMapping("/hazard-factors/{id}")
    public HazardFactorDto.HazardFactorResponse updateHazardFactor(
            @PathVariable Long id,
            @Valid @RequestBody HazardFactorDto.HazardFactorRequest request) {
        return hazardFactorService.updateHazardFactor(id, request);
    }

    @DeleteMapping("/hazard-factors/{id}")
    public void deleteHazardFactor(@PathVariable Long id) {
        hazardFactorService.deleteHazardFactor(id);
    }

    // -------------------------------------------------------------------------
    // HazardFactorLimit endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/hazard-factors/{factorId}/limits")
    public List<HazardFactorDto.HazardFactorLimitResponse> listLimits(@PathVariable Long factorId) {
        return hazardFactorService.listLimits(factorId);
    }

    @PostMapping("/hazard-factors/{factorId}/limits")
    public HazardFactorDto.HazardFactorLimitResponse addLimit(
            @PathVariable Long factorId,
            @Valid @RequestBody HazardFactorDto.HazardFactorLimitRequest request) {
        return hazardFactorService.addLimit(factorId, request);
    }

    @PutMapping("/hazard-factor-limits/{limitId}")
    public HazardFactorDto.HazardFactorLimitResponse updateLimit(
            @PathVariable Long limitId,
            @Valid @RequestBody HazardFactorDto.HazardFactorLimitRequest request) {
        return hazardFactorService.updateLimit(limitId, request);
    }

    @DeleteMapping("/hazard-factor-limits/{limitId}")
    public void deleteLimit(@PathVariable Long limitId) {
        hazardFactorService.deleteLimit(limitId);
    }

    // -------------------------------------------------------------------------
    // HazardFactorMethod endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/hazard-factors/{factorId}/methods")
    public List<HazardFactorDto.HazardFactorMethodResponse> listMethods(@PathVariable Long factorId) {
        return hazardFactorService.listMethods(factorId);
    }

    @PostMapping("/hazard-factors/{factorId}/methods")
    public HazardFactorDto.HazardFactorMethodResponse addMethod(
            @PathVariable Long factorId,
            @Valid @RequestBody HazardFactorDto.HazardFactorMethodRequest request) {
        return hazardFactorService.addMethod(factorId, request);
    }

    @DeleteMapping("/hazard-factor-methods/{id}")
    public void deleteMethod(@PathVariable Long id) {
        hazardFactorService.deleteMethod(id);
    }

    // -------------------------------------------------------------------------
    // SDS endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/sds")
    public Page<HazardFactorDto.SdsResponse> pageSds(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long factorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return hazardFactorService.pageSds(keyword, factorId, page, size);
    }

    @PostMapping("/sds")
    public HazardFactorDto.SdsResponse createSds(@Valid @RequestBody HazardFactorDto.SdsRequest request) {
        return hazardFactorService.createSds(request);
    }

    @PutMapping("/sds/{id}")
    public HazardFactorDto.SdsResponse updateSds(
            @PathVariable Long id,
            @Valid @RequestBody HazardFactorDto.SdsRequest request) {
        return hazardFactorService.updateSds(id, request);
    }

    @DeleteMapping("/sds/{id}")
    public void deleteSds(@PathVariable Long id) {
        hazardFactorService.deleteSds(id);
    }
}
