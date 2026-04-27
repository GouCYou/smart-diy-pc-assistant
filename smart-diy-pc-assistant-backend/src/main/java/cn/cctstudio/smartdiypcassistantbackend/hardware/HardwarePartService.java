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
            parts = merged.values().stream().toList();
        }
        return parts.stream()
                .sorted(partComparator())
                .map(HardwarePartResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HardwarePartResponse> listAdmin() {
        return hardwarePartRepository.findAll().stream()
                .sorted(partComparator())
                .map(HardwarePartResponse::from)
                .toList();
    }

    @Transactional
    public HardwarePartResponse create(HardwarePartRequest request) {
        if (hardwarePartRepository.existsByTypeAndModelIgnoreCase(request.type(), request.model().trim())) {
            throw new BusinessException("标准型号已存在");
        }
        HardwarePart part = new HardwarePart(request.type(), request.model().trim(), safeNumber(request.price()), request.performanceScore());
        apply(part, request);
        return HardwarePartResponse.from(hardwarePartRepository.save(part));
    }

    @Transactional
    public HardwarePartResponse update(Long id, HardwarePartRequest request) {
        HardwarePart part = hardwarePartRepository.findById(id)
                .orElseThrow(() -> new BusinessException("硬件不存在"));
        hardwarePartRepository.findByTypeAndModelIgnoreCase(request.type(), request.model().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("标准型号已存在");
                });
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
        String supportedFormFactors = request.supportedFormFactors();
        if (request.type() == HardwarePartType.CASE && !StringUtils.hasText(supportedFormFactors)) {
            supportedFormFactors = request.formFactor();
        }
        int performanceScore = hasPerformanceScore(request.type()) ? safeNumber(request.performanceScore()) : 0;
        part.update(
                request.type(),
                request.model().trim(),
                safeNumber(request.price()),
                performanceScore,
                request.socketType(),
                request.memoryType(),
                request.formFactor(),
                request.wattage(),
                0,
                0,
                supportedFormFactors,
                request.specs(),
                request.enabled() == null || request.enabled()
        );
    }

    private int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasPerformanceScore(HardwarePartType type) {
        return type == HardwarePartType.CPU
                || type == HardwarePartType.GPU
                || type == HardwarePartType.MEMORY
                || type == HardwarePartType.STORAGE;
    }

    private Comparator<HardwarePart> partComparator() {
        return Comparator.comparing(HardwarePart::getType)
                .thenComparing(Comparator.comparingInt(this::generationRank).reversed())
                .thenComparing(Comparator.comparingInt(HardwarePart::getPerformanceScore).reversed())
                .thenComparing(HardwarePart::getModel);
    }

    private int generationRank(HardwarePart part) {
        String model = part.getModel().toUpperCase();
        return switch (part.getType()) {
            case CPU -> cpuRank(model);
            case GPU -> gpuRank(model);
            case MOTHERBOARD -> motherboardRank(model);
            case MEMORY -> memoryRank(model);
            case STORAGE -> storageRank(model);
            case PSU -> psuRank(model);
            case CASE, COOLER -> 0;
        };
    }

    private int cpuRank(String model) {
        if (model.contains("CORE ULTRA")) {
            return model.contains("PLUS") ? 2600 : 2500;
        }
        if (model.contains("9950X3D2") || model.contains("9850X3D")) {
            return 2450;
        }
        if (model.contains("9950X3D") || model.contains("9900X3D") || model.contains("9800X3D")) {
            return 2400;
        }
        if (model.contains("RYZEN 9 99") || model.contains("RYZEN 7 97") || model.contains("RYZEN 5 96")) {
            return 2300;
        }
        if (model.contains("7800X3D")) {
            return 1800;
        }
        if (model.contains("14")) {
            return 1700;
        }
        if (model.contains("13")) {
            return 1600;
        }
        if (model.contains("12")) {
            return 1500;
        }
        return 0;
    }

    private int gpuRank(String model) {
        if (model.contains("RTX 50")) {
            return 2600;
        }
        if (model.contains("RX 90")) {
            return 2500;
        }
        if (model.contains("ARC B")) {
            return 2300;
        }
        if (model.contains("RTX 40") || model.contains("RX 7")) {
            return 1800;
        }
        return 0;
    }

    private int motherboardRank(String model) {
        if (model.contains("Z890") || model.contains("X870") || model.contains("X670")) {
            return 2600;
        }
        if (model.contains("B860") || model.contains("B850") || model.contains("Z790")) {
            return 2400;
        }
        if (model.contains("B650") || model.contains("B760")) {
            return 1800;
        }
        if (model.contains("B550")) {
            return 1200;
        }
        if (model.contains("H810")) {
            return 900;
        }
        return 0;
    }

    private int memoryRank(String model) {
        if (model.contains("DDR5")) {
            return 2000;
        }
        if (model.contains("DDR4")) {
            return 1000;
        }
        return 0;
    }

    private int storageRank(String model) {
        if (model.contains("PCIE 5.0")) {
            return 2000;
        }
        if (model.contains("PCIE 4.0")) {
            return 1500;
        }
        return 0;
    }

    private int psuRank(String model) {
        if (model.contains("ATX 3.1")) {
            return 2000;
        }
        if (model.contains("ATX 3.0")) {
            return 1800;
        }
        return 0;
    }
}
