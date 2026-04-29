package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BuildConfigRepository extends JpaRepository<BuildConfig, Long> {

    List<BuildConfig> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<BuildConfig> findByUserIdAndSourceOrderByUpdatedAtDesc(Long userId, BuildConfigSource source);

    List<BuildConfig> findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(Long userId);

    List<BuildConfig> findByUserIdAndFavoriteTrueAndSourceOrderByUpdatedAtDesc(Long userId, BuildConfigSource source);

    List<BuildConfig> findByIdIn(Collection<Long> ids);

    Optional<BuildConfig> findByIdAndUserId(Long id, Long userId);

    boolean existsBySerialNumber(String serialNumber);
}
