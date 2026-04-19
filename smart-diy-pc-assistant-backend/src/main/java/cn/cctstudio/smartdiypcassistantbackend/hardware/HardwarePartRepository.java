package cn.cctstudio.smartdiypcassistantbackend.hardware;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HardwarePartRepository extends JpaRepository<HardwarePart, Long> {

    boolean existsByBrandAndModel(String brand, String model);

    Optional<HardwarePart> findByBrandAndModel(String brand, String model);

    List<HardwarePart> findByEnabledTrue();

    List<HardwarePart> findByTypeAndEnabledTrueOrderByPriceAsc(HardwarePartType type);

    List<HardwarePart> findByTypeAndEnabledTrueAndModelContainingIgnoreCaseOrderByPriceAsc(HardwarePartType type, String keyword);

    List<HardwarePart> findByTypeAndEnabledTrueAndBrandContainingIgnoreCaseOrderByPriceAsc(HardwarePartType type, String keyword);

    List<HardwarePart> findByIdIn(Collection<Long> ids);
}
