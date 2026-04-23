package cn.cctstudio.smartdiypcassistantbackend.community;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigService;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentRequest;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostRequest;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class CommunityService {

    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityPostLikeRepository postLikeRepository;
    private final CommunityPostFavoriteRepository postFavoriteRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final BuildConfigService buildConfigService;
    private final MediaUrlService mediaUrlService;

    public CommunityService(
            CommunityPostRepository postRepository,
            CommunityCommentRepository commentRepository,
            CommunityPostLikeRepository postLikeRepository,
            CommunityPostFavoriteRepository postFavoriteRepository,
            CommunityCommentLikeRepository commentLikeRepository,
            UserRepository userRepository,
            BuildConfigService buildConfigService,
            MediaUrlService mediaUrlService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postFavoriteRepository = postFavoriteRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userRepository = userRepository;
        this.buildConfigService = buildConfigService;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponse> list(Long userId) {
        return postRepository.findByVisibleTrueOrderByCreatedAtDesc().stream()
                .map(post -> toPostResponse(post, userId, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityPostResponse detail(Long userId, Long id) {
        CommunityPost post = findVisiblePost(id);
        return toPostResponse(post, userId, true);
    }

    @Transactional
    public CommunityPostResponse create(Long userId, CommunityPostRequest request) {
        List<String> imageKeys = cleanImages(request.imageKeys());
        Long buildConfigId = request.buildConfigId();
        if (buildConfigId != null) {
            buildConfigService.get(userId, buildConfigId);
        }
        CommunityPost post = postRepository.save(new CommunityPost(
                userId,
                request.content().trim(),
                serialize(imageKeys),
                buildConfigId
        ));
        return toPostResponse(post, userId, true);
    }

    @Transactional
    public CommunityPostResponse toggleLike(Long userId, Long postId) {
        CommunityPost post = findVisiblePost(postId);
        postLikeRepository.findByPostIdAndUserId(postId, userId)
                .ifPresentOrElse(postLikeRepository::delete, () -> postLikeRepository.save(new CommunityPostLike(postId, userId)));
        return toPostResponse(post, userId, false);
    }

    @Transactional
    public CommunityPostResponse toggleFavorite(Long userId, Long postId) {
        CommunityPost post = findVisiblePost(postId);
        postFavoriteRepository.findByPostIdAndUserId(postId, userId)
                .ifPresentOrElse(postFavoriteRepository::delete, () -> postFavoriteRepository.save(new CommunityPostFavorite(postId, userId)));
        return toPostResponse(post, userId, false);
    }

    @Transactional
    public CommunityCommentResponse createComment(Long userId, Long postId, CommunityCommentRequest request) {
        findVisiblePost(postId);
        List<String> imageKeys = cleanImages(request.imageKeys());
        Long buildConfigId = request.buildConfigId();
        if (buildConfigId != null) {
            buildConfigService.get(userId, buildConfigId);
        }
        CommunityComment comment = commentRepository.save(new CommunityComment(
                postId,
                userId,
                request.content().trim(),
                serialize(imageKeys),
                buildConfigId
        ));
        return toCommentResponse(comment, userId);
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentResponse> listComments(Long userId, Long postId) {
        findVisiblePost(postId);
        return commentRepository.findByPostIdAndVisibleTrueOrderByCreatedAtAsc(postId).stream()
                .map(comment -> toCommentResponse(comment, userId))
                .toList();
    }

    @Transactional
    public CommunityCommentResponse toggleCommentLike(Long userId, Long commentId) {
        CommunityComment comment = commentRepository.findById(commentId)
                .filter(CommunityComment::isVisible)
                .orElseThrow(() -> new BusinessException("Comment not found"));
        commentLikeRepository.findByCommentIdAndUserId(commentId, userId)
                .ifPresentOrElse(commentLikeRepository::delete, () -> commentLikeRepository.save(new CommunityCommentLike(commentId, userId)));
        return toCommentResponse(comment, userId);
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponse> listAdmin() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(post -> toPostResponse(post, 0L, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentResponse> listAdminComments(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(comment -> toCommentResponse(comment, 0L))
                .toList();
    }

    @Transactional
    public void deletePost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new BusinessException("Post not found");
        }
        commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
                .forEach(comment -> commentLikeRepository.deleteByCommentId(comment.getId()));
        commentRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        postFavoriteRepository.deleteByPostId(postId);
        postRepository.deleteById(postId);
    }

    @Transactional
    public void deleteOwnPost(Long userId, Long postId) {
        CommunityPost post = findVisiblePost(postId);
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException("Only the author can delete this post");
        }
        deletePost(postId);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new BusinessException("Comment not found");
        }
        commentLikeRepository.deleteByCommentId(commentId);
        commentRepository.deleteById(commentId);
    }

    @Transactional
    public void deleteOwnComment(Long userId, Long commentId) {
        CommunityComment comment = commentRepository.findById(commentId)
                .filter(CommunityComment::isVisible)
                .orElseThrow(() -> new BusinessException("Comment not found"));
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException("Only the author can delete this comment");
        }
        deleteComment(commentId);
    }

    private CommunityPost findVisiblePost(Long id) {
        return postRepository.findByIdAndVisibleTrue(id)
                .orElseThrow(() -> new BusinessException("Post not found"));
    }

    private CommunityPostResponse toPostResponse(CommunityPost post, Long userId, boolean withComments) {
        BuildConfigResponse buildConfig = post.getBuildConfigId() == null ? null : buildConfigService.getPublic(post.getBuildConfigId());
        List<CommunityCommentResponse> comments = withComments
                ? commentRepository.findByPostIdAndVisibleTrueOrderByCreatedAtAsc(post.getId()).stream()
                .map(comment -> toCommentResponse(comment, userId))
                .toList()
                : List.of();
        return new CommunityPostResponse(
                post.getId(),
                author(post.getUserId()),
                post.getContent(),
                mediaUrlService.toPublicUrls(deserialize(post.getImageUrls())),
                buildConfig,
                postLikeRepository.countByPostId(post.getId()),
                commentRepository.countByPostIdAndVisibleTrue(post.getId()),
                postFavoriteRepository.countByPostId(post.getId()),
                userId != null && userId > 0 && postLikeRepository.existsByPostIdAndUserId(post.getId(), userId),
                userId != null && userId > 0 && postFavoriteRepository.existsByPostIdAndUserId(post.getId(), userId),
                post.getCreatedAt(),
                comments
        );
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment, Long userId) {
        BuildConfigResponse buildConfig = comment.getBuildConfigId() == null ? null : buildConfigService.getPublic(comment.getBuildConfigId());
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getPostId(),
                author(comment.getUserId()),
                comment.getContent(),
                mediaUrlService.toPublicUrls(deserialize(comment.getImageUrls())),
                buildConfig,
                commentLikeRepository.countByCommentId(comment.getId()),
                userId != null && userId > 0 && commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId),
                comment.getCreatedAt()
        );
    }

    private AuthorResponse author(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new AuthorResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        mediaUrlService.toPublicUrl(user.getAvatarKey())
                ))
                .orElse(new AuthorResponse(userId, "User", "User", ""));
    }

    private List<String> cleanImages(List<String> imageKeys) {
        List<String> cleaned = mediaUrlService.normalizeKeys(imageKeys);
        if (cleaned.size() > 9) {
            throw new BusinessException("At most 9 images are allowed");
        }
        return cleaned;
    }

    private String serialize(List<String> imageKeys) {
        return String.join("\n", imageKeys);
    }

    private List<String> deserialize(String imageKeys) {
        if (!StringUtils.hasText(imageKeys)) {
            return List.of();
        }
        return Arrays.stream(imageKeys.split("\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
