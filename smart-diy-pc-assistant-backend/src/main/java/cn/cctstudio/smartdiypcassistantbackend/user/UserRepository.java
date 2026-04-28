package cn.cctstudio.smartdiypcassistantbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickname);

    List<User> findByNicknameIn(Collection<String> nicknames);

    List<User> findTop20ByEnabledTrueAndNicknameContainingIgnoreCaseOrderByCreatedAtDesc(String nickname);

    List<User> findTop20ByEnabledTrueOrderByCreatedAtDesc();

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<User> findByUsernameOrEmail(String username, String email);
}
