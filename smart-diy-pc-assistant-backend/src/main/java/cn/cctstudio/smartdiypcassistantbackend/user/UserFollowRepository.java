package cn.cctstudio.smartdiypcassistantbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    long countByFollowerId(Long followerId);

    long countByFollowingId(Long followingId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    List<UserFollow> findByFollowerIdOrderByCreatedAtDesc(Long followerId);

    List<UserFollow> findByFollowingIdOrderByCreatedAtDesc(Long followingId);
}
