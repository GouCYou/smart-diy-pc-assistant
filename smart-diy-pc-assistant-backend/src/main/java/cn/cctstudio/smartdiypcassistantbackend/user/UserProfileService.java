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
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? user.getUsername()
                : request.nickname().trim();
        if (userRepository.existsByNicknameAndIdNot(nickname, user.getId())) {
            throw new BusinessException("昵称已存在");
        }
        String avatarKey = request.avatarKey() == null
                ? user.getAvatarKey()
                : mediaUrlService.normalizeKey(request.avatarKey());
        if (avatarKey != null && avatarKey.toLowerCase().contains(".gif")) {
            throw new BusinessException("头像不支持动图，请重新上传静态图片");
        }
        String backgroundKey = request.backgroundKey() == null
                ? user.getBackgroundImageKey()
                : mediaUrlService.normalizeKey(request.backgroundKey());
        user.updateProfile(
                request.nickname(),
                avatarKey,
                backgroundKey,
                request.bio(),
                request.gender(),
                request.birthday(),
                request.countryOrRegion(),
                request.province(),
                request.city(),
                request.profilePublic(),
                request.spacePublic()
        );
        return toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "登录状态已过期，请重新登录"));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarKey(),
                mediaUrlService.toPublicUrl(user.getAvatarKey()),
                user.getBackgroundImageKey(),
                mediaUrlService.toPublicUrl(user.getBackgroundImageKey()),
                user.getBio(),
                user.getGender(),
                user.getBirthday(),
                user.getCountryOrRegion(),
                user.getProvince(),
                user.getCity(),
                user.isProfilePublic(),
                user.isSpacePublic(),
                user.getRole().name()
        );
    }
}
