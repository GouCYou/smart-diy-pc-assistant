package cn.cctstudio.smartdiypcassistantbackend.user;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigService;
import cn.cctstudio.smartdiypcassistantbackend.community.CommunityService;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.news.NewsService;
import cn.cctstudio.smartdiypcassistantbackend.user.dto.UserSpaceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class UserSpaceService {

    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;
    private final CommunityService communityService;
    private final BuildConfigService buildConfigService;
    private final NewsService newsService;
    private final MediaUrlService mediaUrlService;

    public UserSpaceService(
            UserRepository userRepository,
            UserFollowRepository followRepository,
            CommunityService communityService,
            BuildConfigService buildConfigService,
            NewsService newsService,
            MediaUrlService mediaUrlService
    ) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.communityService = communityService;
        this.buildConfigService = buildConfigService;
        this.newsService = newsService;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public UserSpaceResponse getSpace(Long viewerId, Long profileUserId) {
        User user = userRepository.findById(profileUserId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        boolean mine = viewerId != null && Objects.equals(viewerId, profileUserId);
        boolean publicSpace = mine || user.isSpacePublic();
        boolean publicProfile = publicSpace && (mine || user.isProfilePublic());
        return new UserSpaceResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                mediaUrlService.toPublicUrl(user.getAvatarKey()),
                publicSpace ? mediaUrlService.toPublicUrl(user.getBackgroundImageKey()) : "",
                publicSpace ? user.getBio() : "",
                publicProfile ? user.getGender() : "",
                publicProfile ? user.getBirthday() : "",
                publicProfile ? user.getCountryOrRegion() : "",
                publicProfile ? user.getProvince() : "",
                publicProfile ? user.getCity() : "",
                user.isProfilePublic(),
                user.isSpacePublic(),
                mine,
                viewerId != null && viewerId > 0 && followRepository.existsByFollowerIdAndFollowingId(viewerId, profileUserId),
                publicSpace ? followRepository.countByFollowerId(profileUserId) : 0,
                publicSpace ? followRepository.countByFollowingId(profileUserId) : 0,
                publicSpace ? communityService.listByAuthor(viewerId, profileUserId) : List.of(),
                publicSpace ? buildConfigService.listFavoriteByUser(profileUserId) : List.of(),
                publicSpace ? communityService.listFavorites(viewerId, profileUserId) : List.of(),
                publicSpace ? newsService.listFavorites(viewerId, profileUserId) : List.of()
        );
    }

    @Transactional
    public UserSpaceResponse toggleFollow(Long viewerId, Long targetUserId) {
        if (viewerId == null || viewerId <= 0) {
            throw new BusinessException(401, "请先登录");
        }
        if (Objects.equals(viewerId, targetUserId)) {
            throw new BusinessException("不能关注自己");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new BusinessException("用户不存在");
        }
        followRepository.findByFollowerIdAndFollowingId(viewerId, targetUserId)
                .ifPresentOrElse(followRepository::delete, () -> followRepository.save(new UserFollow(viewerId, targetUserId)));
        return getSpace(viewerId, targetUserId);
    }
}
