package cn.cctstudio.smartdiypcassistantbackend.hardware;

import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartRequest;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HardwarePartService {

    private final HardwarePartRepository hardwarePartRepository;

    public HardwarePartService(HardwarePartRepository hardwarePartRepository) {
        this.hardwarePartRepository = hardwarePartRepository;
    }

    @Transactional(readOnly = true)
    public List<HardwarePartResponse> list(HardwarePartType type, String keyword) {
        List<HardwarePart> parts;
        if (type == null) {
            parts = hardwarePartRepository.findAll();
        } else if (!StringUtils.hasText(keyword)) {
            parts = hardwarePartRepository.findByTypeAndEnabledTrueOrderByPriceAsc(type);
        } else {
            Map<Long, HardwarePart> merged = new LinkedHashMap<>();
            hardwarePartRepository.findByTypeAndEnabledTrueAndModelContainingIgnoreCaseOrderByPriceAsc(type, keyword.trim())
                    .forEach(part -> merged.put(part.getId(), part));
            hardwarePartRepository.findByTypeAndEnabledTrueAndBrandContainingIgnoreCaseOrderByPriceAsc(type, keyword.trim())
                    .forEach(part -> merged.put(part.getId(), part));
            parts = merged.values().stream().toList();
        }
        return parts.stream()
                .sorted(Comparator.comparing(HardwarePart::getType).thenComparing(HardwarePart::getPrice))
                .map(HardwarePartResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HardwarePartResponse> listAdmin() {
        return hardwarePartRepository.findAll().stream()
                .sorted(Comparator.comparing(HardwarePart::getType).thenComparing(HardwarePart::getPrice))
                .map(HardwarePartResponse::from)
                .toList();
    }

    @Transactional
    public HardwarePartResponse create(HardwarePartRequest request) {
        HardwarePart part = new HardwarePart(request.type(), request.brand().trim(), request.model().trim(), request.price(), request.performanceScore());
        apply(part, request);
        return HardwarePartResponse.from(hardwarePartRepository.save(part));
    }

    @Transactional
    public HardwarePartResponse update(Long id, HardwarePartRequest request) {
        HardwarePart part = hardwarePartRepository.findById(id)
                .orElseThrow(() -> new BusinessException("硬件不存在"));
        apply(part, request);
        return HardwarePartResponse.from(part);
    }

    @Transactional
    public void delete(Long id) {
        if (!hardwarePartRepository.existsById(id)) {
            throw new BusinessException("硬件不存在");
        }
        hardwarePartRepository.deleteById(id);
    }

    private void apply(HardwarePart part, HardwarePartRequest request) {
        part.update(
                request.type(),
                request.brand().trim(),
                request.model().trim(),
                request.price(),
                request.performanceScore(),
                request.socketType(),
                request.memoryType(),
                request.formFactor(),
                request.wattage(),
                request.gpuLengthMm(),
                request.coolerHeightMm(),
                request.supportedFormFactors(),
                request.specs(),
                request.enabled() == null || request.enabled()
        );
    }
}
