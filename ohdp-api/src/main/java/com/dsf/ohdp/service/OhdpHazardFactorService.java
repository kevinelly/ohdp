package com.dsf.ohdp.service;

import com.dsf.ohdp.dto.HazardFactorDto;
import java.util.List;
import org.springframework.data.domain.Page;

public interface OhdpHazardFactorService {

    // HazardFactor CRUD
    Page<HazardFactorDto.HazardFactorResponse> pageHazardFactors(
            String keyword, String factorCategory, int page, int size);

    HazardFactorDto.HazardFactorDetailResponse getHazardFactor(Long id);

    HazardFactorDto.HazardFactorResponse createHazardFactor(HazardFactorDto.HazardFactorRequest request);

    HazardFactorDto.HazardFactorResponse updateHazardFactor(Long id, HazardFactorDto.HazardFactorRequest request);

    void deleteHazardFactor(Long id);

    List<HazardFactorDto.HazardFactorResponse> listAllHazardFactors();

    // HazardFactorLimit CRUD
    List<HazardFactorDto.HazardFactorLimitResponse> listLimits(Long factorId);

    HazardFactorDto.HazardFactorLimitResponse addLimit(Long factorId, HazardFactorDto.HazardFactorLimitRequest request);

    HazardFactorDto.HazardFactorLimitResponse updateLimit(Long limitId, HazardFactorDto.HazardFactorLimitRequest request);

    void deleteLimit(Long limitId);

    // HazardFactorMethod CRUD
    List<HazardFactorDto.HazardFactorMethodResponse> listMethods(Long factorId);

    HazardFactorDto.HazardFactorMethodResponse addMethod(Long factorId, HazardFactorDto.HazardFactorMethodRequest request);

    void deleteMethod(Long id);

    // SDS CRUD
    Page<HazardFactorDto.SdsResponse> pageSds(String keyword, Long factorId, int page, int size);

    HazardFactorDto.SdsResponse createSds(HazardFactorDto.SdsRequest request);

    HazardFactorDto.SdsResponse updateSds(Long id, HazardFactorDto.SdsRequest request);

    void deleteSds(Long id);
}
