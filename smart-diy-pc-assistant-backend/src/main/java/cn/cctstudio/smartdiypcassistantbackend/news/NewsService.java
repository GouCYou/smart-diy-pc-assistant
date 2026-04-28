package cn.cctstudio.smartdiypcassistantbackend.news;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.mention.UserMentionService;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsCommentRequest;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsCommentResponse;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsRequest;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsResponse;
import cn.cctstudio.smartdiypcassistantbackend.notification.NotificationService;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserAccessService;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class NewsService {

    private final NewsRepository newsRepository;
    private final NewsLikeRepository newsLikeRepository;
    private final NewsFavoriteRepository newsFavoriteRepository;
    private final NewsCommentRepository newsCommentRepository;
    private final NewsCommentLikeRepository newsCommentLikeRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final NotificationService notificationService;
    private final UserMentionService userMentionService;
    private final MediaUrlService mediaUrlService;

    public NewsService(
            NewsRepository newsRepository,
            NewsLikeRepository newsLikeRepository,
            NewsFavoriteRepository newsFavoriteRepository,
            NewsCommentRepository newsCommentRepository,
            NewsCommentLikeRepository newsCommentLikeRepository,
            UserRepository userRepository,
            UserAccessService userAccessService,
            NotificationService notificationService,
            UserMentionService userMentionService,
            MediaUrlService mediaUrlService
    ) {
        this.newsRepository = newsRepository;
        this.newsLikeRepository = newsLikeRepository;
        this.newsFavoriteRepository = newsFavoriteRepository;
        this.newsCommentRepository = newsCommentRepository;
        this.newsCommentLikeRepository = newsCommentLikeRepository;
        this.userRepository = userRepository;
        this.userAccessService = userAccessService;
        this.notificationService = notificationService;
        this.userMentionService = userMentionService;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public List<NewsResponse> listPublished(Long userId, String sort) {
        return sortNews(newsRepository.findByStatusOrderByCreatedAtDesc(NewsStatus.PUBLISHED), sort)
                .stream()
                .limit(50)
                .map(news -> toNewsResponse(news, userId, false, "latest"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NewsResponse> listFavorites(Long viewerId, Long ownerId) {
        return newsFavoriteRepository.findByUserIdOrderByCreatedAtDesc(ownerId).stream()
                .map(NewsFavorite::getNewsId)
                .map(newsRepository::findById)
                .flatMap(Optional::stream)
                .filter(news -> news.getStatus() == NewsStatus.PUBLISHED)
                .map(news -> toNewsResponse(news, viewerId, false, "latest"))
                .toList();
    }

    @Transactional
    public NewsResponse detail(Long userId, Long id) {
        News news = findPublishedNews(id);
        news.increaseViewCount();
        return toNewsResponse(news, userId, true, "latest");
    }

    @Transactional(readOnly = true)
    public List<NewsResponse> listAll() {
        return newsRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(news -> toNewsResponse(news, null, false, "latest"))
                .toList();
    }

    @Transactional
    public NewsResponse create(NewsRequest request) {
        News news = new News(
                request.title(),
                createSummary(request.summary(), request.content(), request.title()),
                normalizeTag(request.tag()),
                request.content(),
                mediaUrlService.normalizeKey(request.coverKey()),
                request.status()
        );
        return toNewsResponse(newsRepository.save(news), null, false, "latest");
    }

    @Transactional
    public NewsResponse update(Long id, NewsRequest request) {
        News news = newsRepository.findById(id).orElseThrow(() -> new BusinessException("新闻不存在"));
        news.update(
                request.title(),
                createSummary(request.summary(), request.content(), request.title()),
                normalizeTag(request.tag()),
                request.content(),
                mediaUrlService.normalizeKey(request.coverKey()),
                request.status()
        );
        return toNewsResponse(news, null, false, "latest");
    }

    @Transactional
    public NewsResponse toggleLike(Long userId, Long newsId) {
        News news = findPublishedNews(newsId);
        newsLikeRepository.findByNewsIdAndUserId(newsId, userId)
                .ifPresentOrElse(newsLikeRepository::delete, () -> newsLikeRepository.save(new NewsLike(newsId, userId)));
        return toNewsResponse(news, userId, false, "latest");
    }

    @Transactional
    public NewsResponse toggleFavorite(Long userId, Long newsId) {
        News news = findPublishedNews(newsId);
        newsFavoriteRepository.findByNewsIdAndUserId(newsId, userId)
                .ifPresentOrElse(newsFavoriteRepository::delete, () -> newsFavoriteRepository.save(new NewsFavorite(newsId, userId)));
        return toNewsResponse(news, userId, false, "latest");
    }

    @Transactional
    public NewsCommentResponse createComment(Long userId, Long newsId, NewsCommentRequest request) {
        userAccessService.requireAvailable(userId);
        findPublishedNews(newsId);
        String content = request.content() == null ? "" : request.content().trim();
        List<String> imageKeys = cleanImages(request.imageKeys());
        if (!StringUtils.hasText(content) && imageKeys.isEmpty()) {
            throw new BusinessException("评论内容或图片不能为空");
        }
        NewsComment parentComment = resolveParentComment(newsId, request.parentCommentId());
        Long parentCommentId = parentComment == null ? null : parentComment.getId();
        NewsComment comment = newsCommentRepository.save(new NewsComment(newsId, userId, parentCommentId, content, serialize(imageKeys)));
        if (parentComment != null) {
            notificationService.notifyNewsCommentReply(userId, parentComment.getUserId(), newsId, comment.getId(), comment.getContent());
        }
        for (User user : userMentionService.findMentionedUsers(comment.getContent())) {
            notificationService.notifyNewsCommentMention(
                    userId,
                    user.getId(),
                    newsId,
                    comment.getId(),
                    comment.getContent()
            );
        }
        return toCommentResponse(comment, userId);
    }

    @Transactional(readOnly = true)
    public List<NewsCommentResponse> listComments(Long userId, Long newsId, String sort) {
        findPublishedNews(newsId);
        return sortComments(newsCommentRepository.findByNewsIdAndVisibleTrueOrderByCreatedAtAsc(newsId), sort)
                .stream()
                .map(comment -> toCommentResponse(comment, userId))
                .toList();
    }

    @Transactional
    public NewsCommentResponse toggleCommentLike(Long userId, Long commentId) {
        NewsComment comment = newsCommentRepository.findById(commentId)
                .filter(NewsComment::isVisible)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        findPublishedNews(comment.getNewsId());
        newsCommentLikeRepository.findByCommentIdAndUserId(commentId, userId)
                .ifPresentOrElse(newsCommentLikeRepository::delete, () -> newsCommentLikeRepository.save(new NewsCommentLike(commentId, userId)));
        return toCommentResponse(comment, userId);
    }

    @Transactional
    public void delete(Long id) {
        if (!newsRepository.existsById(id)) {
            throw new BusinessException("新闻不存在");
        }
        newsCommentRepository.findByNewsIdOrderByCreatedAtAsc(id)
                .forEach(comment -> newsCommentLikeRepository.deleteByCommentId(comment.getId()));
        newsCommentRepository.deleteByNewsId(id);
        newsLikeRepository.deleteByNewsId(id);
        newsFavoriteRepository.deleteByNewsId(id);
        newsRepository.deleteById(id);
    }

    private News findPublishedNews(Long id) {
        return newsRepository.findById(id)
                .filter(news -> news.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("新闻不存在"));
    }

    private NewsComment resolveParentComment(Long newsId, Long parentCommentId) {
        if (parentCommentId == null || parentCommentId <= 0) {
            return null;
        }
        NewsComment parent = newsCommentRepository.findById(parentCommentId)
                .filter(NewsComment::isVisible)
                .orElseThrow(() -> new BusinessException("回复的评论不存在"));
        if (!parent.getNewsId().equals(newsId)) {
            throw new BusinessException("回复的评论不属于当前新闻");
        }
        return parent;
    }

    private NewsResponse toNewsResponse(News news, Long userId, boolean withComments, String commentSort) {
        List<NewsCommentResponse> comments = withComments
                ? sortComments(newsCommentRepository.findByNewsIdAndVisibleTrueOrderByCreatedAtAsc(news.getId()), commentSort)
                .stream()
                .map(comment -> toCommentResponse(comment, userId))
                .toList()
                : List.of();
        return new NewsResponse(
                news.getId(),
                news.getTitle(),
                responseSummary(news),
                news.getTag(),
                news.getContent(),
                news.getCoverKey(),
                mediaUrlService.toPublicUrl(news.getCoverKey()),
                news.getViewCount(),
                newsLikeRepository.countByNewsId(news.getId()),
                newsCommentRepository.countByNewsIdAndVisibleTrue(news.getId()),
                newsFavoriteRepository.countByNewsId(news.getId()),
                userId != null && userId > 0 && newsLikeRepository.existsByNewsIdAndUserId(news.getId(), userId),
                userId != null && userId > 0 && newsFavoriteRepository.existsByNewsIdAndUserId(news.getId(), userId),
                news.getStatus(),
                news.getCreatedAt(),
                news.getUpdatedAt(),
                comments
        );
    }

    private NewsCommentResponse toCommentResponse(NewsComment comment, Long userId) {
        return new NewsCommentResponse(
                comment.getId(),
                comment.getNewsId(),
                author(comment.getUserId()),
                comment.getParentCommentId(),
                comment.getParentCommentId() == null ? null : newsCommentRepository.findById(comment.getParentCommentId())
                        .filter(NewsComment::isVisible)
                        .map(parent -> author(parent.getUserId()))
                        .orElse(null),
                comment.getContent(),
                userMentionService.toResponses(comment.getContent()),
                mediaUrlService.toPublicUrls(deserialize(comment.getImageUrls())),
                newsCommentLikeRepository.countByCommentId(comment.getId()),
                userId != null && userId > 0 && newsCommentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId),
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
                .orElse(new AuthorResponse(userId, "用户", "用户", ""));
    }

    private List<News> sortNews(List<News> news, String sort) {
        if ("likes".equalsIgnoreCase(sort)) {
            return news.stream()
                    .sorted(Comparator
                            .comparingLong((News item) -> newsLikeRepository.countByNewsId(item.getId()))
                            .thenComparing(News::getCreatedAt)
                            .reversed())
                    .toList();
        }
        return news;
    }

    private List<NewsComment> sortComments(List<NewsComment> comments, String sort) {
        if ("likes".equalsIgnoreCase(sort)) {
            return comments.stream()
                    .sorted(Comparator
                            .comparingLong((NewsComment item) -> newsCommentLikeRepository.countByCommentId(item.getId()))
                            .thenComparing(NewsComment::getCreatedAt)
                            .reversed())
                    .toList();
        }
        if ("latest".equalsIgnoreCase(sort) || !StringUtils.hasText(sort)) {
            return comments.stream()
                    .sorted(Comparator.comparing(NewsComment::getCreatedAt).reversed())
                    .toList();
        }
        return comments;
    }

    private String normalizeTag(String tag) {
        if (!StringUtils.hasText(tag)) {
            return "科技新闻";
        }
        return tag.trim();
    }

    private String createSummary(String summary, String content, String title) {
        if (StringUtils.hasText(summary)) {
            return summary.trim();
        }
        String plain = content == null ? "" : content
                .replaceAll("!\\[[^]]*]\\([^)]+\\)", " ")
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("[#>*_`~-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(plain)) {
            return title.trim();
        }
        String[] sentences = plain.split("(?<=[。！？!?\\.])");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String sentence : sentences) {
            String value = sentence.trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            builder.append(value);
            count++;
            if (count >= 3) {
                break;
            }
        }
        String result = builder.length() > 0 ? builder.toString() : plain;
        return clampSummary(result);
    }

    private String responseSummary(News news) {
        String summary = createSummary(null, news.getContent(), news.getTitle());
        return StringUtils.hasText(summary) ? summary : news.getSummary();
    }

    private String clampSummary(String summary) {
        if (summary.length() <= 480) {
            return summary;
        }
        String clipped = summary.substring(0, 480);
        int boundary = Math.max(
                Math.max(Math.max(clipped.lastIndexOf('。'), clipped.lastIndexOf('！')), clipped.lastIndexOf('？')),
                Math.max(Math.max(clipped.lastIndexOf('!'), clipped.lastIndexOf('?')), clipped.lastIndexOf('.'))
        );
        return boundary > 80 ? clipped.substring(0, boundary + 1) : clipped;
    }

    private List<String> cleanImages(List<String> imageKeys) {
        List<String> cleaned = mediaUrlService.normalizeKeys(imageKeys);
        if (cleaned.size() > 9) {
            throw new BusinessException("最多只能上传 9 张图片");
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
