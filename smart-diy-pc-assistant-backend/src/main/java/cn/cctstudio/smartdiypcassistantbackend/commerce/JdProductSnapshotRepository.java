package cn.cctstudio.smartdiypcassistantbackend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JdProductSnapshotRepository extends JpaRepository<JdProductSnapshot, Long> {

    List<JdProductSnapshot> findTop240ByOrderByCapturedAtDesc();

    List<JdProductSnapshot> findTop80ByOrderByCapturedAtDesc();

    List<JdProductSnapshot> findTop20BySkuIdOrderByCapturedAtDesc(String skuId);

    Optional<JdProductSnapshot> findFirstBySkuIdOrderByCapturedAtDesc(String skuId);

    List<JdProductSnapshot> findByCapturedAtAfterOrderByCapturedAtAsc(LocalDateTime capturedAt);
}
