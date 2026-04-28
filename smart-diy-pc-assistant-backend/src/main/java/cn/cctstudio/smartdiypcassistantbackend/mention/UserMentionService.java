package cn.cctstudio.smartdiypcassistantbackend.mention;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.MentionResponse;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserMentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_\\-.]{1,64})");

    private final UserRepository userRepository;

    public UserMentionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findMentionedUsers(String content) {
        List<String> nicknames = extractNicknames(content);
        if (nicknames.isEmpty()) {
            return List.of();
        }
        Map<String, User> usersByNickname = new LinkedHashMap<>();
        userRepository.findByNicknameIn(nicknames).stream()
                .filter(User::isEnabled)
                .forEach(user -> usersByNickname.put(user.getNickname(), user));
        return nicknames.stream()
                .map(usersByNickname::get)
                .filter(user -> user != null)
                .toList();
    }

    public List<MentionResponse> toResponses(String content) {
        return findMentionedUsers(content).stream()
                .map(user -> new MentionResponse(user.getId(), user.getNickname()))
                .toList();
    }

    private List<String> extractNicknames(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Set<String> nicknames = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String nickname = matcher.group(1).trim();
            if (StringUtils.hasText(nickname)) {
                nicknames.add(nickname);
            }
        }
        return List.copyOf(nicknames);
    }
}
