package cn.cctstudio.smartdiypcassistantbackend.hardware;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HardwarePartRepository extends JpaRepository<HardwarePart, Long> {

    boolean existsByTypeAndModelIgnoreCase(HardwarePartType type, String model);

    Optional<HardwarePart> findByTypeAndModelIgnoreCase(HardwarePartType type, String model);

    long deleteByBrandIsNotNullAndBrandNot(String brand);

    List<HardwarePart> findByEnabledTrue();

    List<HardwarePart> findByTypeAndEnabledTrueOrderByPriceAsc(HardwarePartType type);

    List<HardwarePart> findByTypeAndEnabledTrueAndModelContainingIgnoreCaseOrderByPriceAsc(HardwarePartType type, String keyword);

    List<HardwarePart> findByIdIn(Collection<Long> ids);
}
