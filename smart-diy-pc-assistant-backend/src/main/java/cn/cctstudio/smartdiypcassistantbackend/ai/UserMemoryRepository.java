package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    Optional<UserMemory> findByUserId(Long userId);
}
