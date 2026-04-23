package cn.cctstudio.smartdiypcassistantbackend.user;

import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.user.dto.UpdateProfileRequest;
import cn.cctstudio.smartdiypcassistantbackend.user.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final MediaUrlService mediaUrlService;

    public UserProfileService(UserRepository userRepository, MediaUrlService mediaUrlService) {
        this.userRepository = userRepository;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.nickname(), mediaUrlService.normalizeKey(request.avatarKey()), request.bio());
        return toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "鐧诲綍鐘舵€佸凡澶辨晥"));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarKey(),
                mediaUrlService.toPublicUrl(user.getAvatarKey()),
                user.getBio(),
                user.getRole().name()
        );
    }
}
