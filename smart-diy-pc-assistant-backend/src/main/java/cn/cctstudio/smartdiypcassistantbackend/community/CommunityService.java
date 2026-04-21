package cn.cctstudio.smartdiypcassistantbackend.community;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigService;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentRequest;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostRequest;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CommunityService(
            CommunityPostRepository postRepository,
            CommunityCommentRepository commentRepository,
            CommunityPostLikeRepository postLikeRepository,
            CommunityPostFavoriteRepository postFavoriteRepository,
            CommunityCommentLikeRepository commentLikeRepository,
            UserRepository userRepository,
            BuildConfigService buildConfigService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postFavoriteRepository = postFavoriteRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userRepository = userRepository;
        this.buildConfigService = buildConfigService;
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
        List<String> imageUrls = cleanImages(request.imageUrls());
        Long buildConfigId = request.buildConfigId();
        if (buildConfigId != null) {
            buildConfigService.get(userId, buildConfigId);
        }
        CommunityPost post = postRepository.save(new CommunityPost(
                userId,
                request.content().trim(),
                serialize(imageUrls),
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
        List<String> imageUrls = cleanImages(request.imageUrls());
        Long buildConfigId = request.buildConfigId();
        if (buildConfigId != null) {
            buildConfigService.get(userId, buildConfigId);
        }
        CommunityComment comment = commentRepository.save(new CommunityComment(
                postId,
                userId,
                request.content().trim(),
                serialize(imageUrls),
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
                .orElseThrow(() -> new BusinessException("评论不存在"));
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
            throw new BusinessException("帖子不存在");
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
            throw new BusinessException("只能删除自己发布的帖子");
        }
        deletePost(postId);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new BusinessException("评论不存在");
        }
        commentLikeRepository.deleteByCommentId(commentId);
        commentRepository.deleteById(commentId);
    }

    @Transactional
    public void deleteOwnComment(Long userId, Long commentId) {
        CommunityComment comment = commentRepository.findById(commentId)
                .filter(CommunityComment::isVisible)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException("只能删除自己发布的评论");
        }
        deleteComment(commentId);
    }

    private CommunityPost findVisiblePost(Long id) {
        return postRepository.findByIdAndVisibleTrue(id)
                .orElseThrow(() -> new BusinessException("帖子不存在"));
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
                deserialize(post.getImageUrls()),
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
                deserialize(comment.getImageUrls()),
                buildConfig,
                commentLikeRepository.countByCommentId(comment.getId()),
                userId != null && userId > 0 && commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId),
                comment.getCreatedAt()
        );
    }

    private AuthorResponse author(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new AuthorResponse(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl()))
                .orElse(new AuthorResponse(userId, "用户", "用户", ""));
    }

    private List<String> cleanImages(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        List<String> cleaned = imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .toList();
        if (cleaned.size() > 9) {
            throw new BusinessException("最多只能添加 9 张图片");
        }
        return cleaned;
    }

    private String serialize(List<String> imageUrls) {
        return String.join("\n", imageUrls);
    }

    private List<String> deserialize(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(imageUrls.split("\\n"))
                .filter(url -> !url.isBlank())
                .toList();
    }
}
