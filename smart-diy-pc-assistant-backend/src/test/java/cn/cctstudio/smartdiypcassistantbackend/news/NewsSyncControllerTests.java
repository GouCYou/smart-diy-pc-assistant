package cn.cctstudio.smartdiypcassistantbackend.news;
import cn.cctstudio.smartdiypcassistantbackend.file.QiniuService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NewsSyncControllerTests {
 @Test void requiresPrivateCredentialBeforeAnyReadOrWrite(){var sources=mock(ImportedNewsRepository.class);var sync=new NewsSyncController("secret",sources,mock(NewsRepository.class),mock(NewsService.class),mock(QiniuService.class));assertThrows(Exception.class,()->sync.state(""));verifyNoInteractions(sources);}
 @Test void cleanupKeepsOwnershipRecordWhenObjectDeletionFails(){var sources=mock(ImportedNewsRepository.class);var news=mock(NewsRepository.class);var service=mock(NewsService.class);var media=mock(QiniuService.class);var old=new ImportedNews("https://www.ithome.com/1/016/053.htm",LocalDateTime.now().minusMonths(2));old.newsId=1L;old.mediaKeys="news-import/owned.jpg";when(sources.findByPublishedAtBefore(any())).thenReturn(List.of(old));doThrow(new RuntimeException("storage unavailable")).when(media).deleteNewsImage(anyString());new NewsSyncController("secret",sources,news,service,media).cleanup("secret");verify(sources,never()).delete(any());verify(service,never()).delete(any());}
 @Test void cleanupDeletesOwnedObjectsAndDatabaseRow(){var sources=mock(ImportedNewsRepository.class);var news=mock(NewsRepository.class);var service=mock(NewsService.class);var media=mock(QiniuService.class);var old=new ImportedNews("https://www.ithome.com/1/016/053.htm",LocalDateTime.now().minusMonths(2));old.newsId=1L;old.mediaKeys="news-import/owned.jpg";when(sources.findByPublishedAtBefore(any())).thenReturn(List.of(old));when(news.existsById(1L)).thenReturn(true);new NewsSyncController("secret",sources,news,service,media).cleanup("secret");verify(media).deleteNewsImage("news-import/owned.jpg");verify(service).delete(1L);verify(sources).delete(old);}
}
