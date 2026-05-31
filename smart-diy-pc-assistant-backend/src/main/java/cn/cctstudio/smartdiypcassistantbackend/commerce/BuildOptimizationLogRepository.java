package cn.cctstudio.smartdiypcassistantbackend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildOptimizationLogRepository extends JpaRepository<BuildOptimizationLog, Long> {

    List<BuildOptimizationLog> findTop12ByUserIdOrderByCreatedAtDesc(Long userId);
}
