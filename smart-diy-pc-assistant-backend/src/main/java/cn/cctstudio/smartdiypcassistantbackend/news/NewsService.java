package cn.cctstudio.smartdiypcassistantbackend.news;

import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsRequest;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NewsService {

    private final NewsRepository newsRepository;

    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public List<NewsResponse> listPublished() {
        return newsRepository.findTop20ByStatusOrderByCreatedAtDesc(NewsStatus.PUBLISHED)
                .stream()
                .map(NewsResponse::from)
                .toList();
    }

    public List<NewsResponse> listAll() {
        return newsRepository.findAll()
                .stream()
                .map(NewsResponse::from)
                .toList();
    }

    @Transactional
    public NewsResponse create(NewsRequest request) {
        News news = new News(request.title(), request.summary(), request.content(), request.coverUrl(), request.status());
        return NewsResponse.from(newsRepository.save(news));
    }

    @Transactional
    public NewsResponse update(Long id, NewsRequest request) {
        News news = newsRepository.findById(id).orElseThrow(() -> new BusinessException("新闻不存在"));
        news.update(request.title(), request.summary(), request.content(), request.coverUrl(), request.status());
        return NewsResponse.from(news);
    }
}
