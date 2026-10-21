package cn.cctstudio.smartdiypcassistantbackend.news;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.file.QiniuService;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
@RestController @RequestMapping("/api/internal/news-sync")
public class NewsSyncController {
 private final String key;private final ImportedNewsRepository sources;private final NewsRepository news;private final NewsService service;private final QiniuService media;
 public NewsSyncController(@Value("${app.agent.news-sync-key:}") String key,ImportedNewsRepository sources,NewsRepository news,NewsService service,QiniuService media){this.key=key;this.sources=sources;this.news=news;this.service=service;this.media=media;}
 private void check(String given){if(key.isBlank()||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 private LocalDateTime cutoff(){return LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusMonths(1);}
 @GetMapping("/state") public ApiResponse<?> state(@RequestHeader(value="X-News-Sync-Key",defaultValue="") String given){check(given);return ApiResponse.ok(Map.of("urls",sources.findAll().stream().map(v->v.sourceUrl).toList(),"cutoff",cutoff().toString()));}
 public record Image(String url,String mime,String data){}
 public record Article(String sourceUrl,String title,String content,String cover,LocalDateTime publishedAt,List<Image> images){}
 @PostMapping("/article") public synchronized ApiResponse<?> article(@RequestHeader(value="X-News-Sync-Key",defaultValue="") String given,@RequestBody Article body){
  check(given);
  if(body.sourceUrl()==null||!body.sourceUrl().matches("https://www\\.ithome\\.com/\\d+/\\d+/\\d+\\.htm")||body.title()==null||body.title().isBlank()||body.title().length()>120||body.content()==null||body.content().length()>100000||body.publishedAt()==null||body.publishedAt().isBefore(cutoff())||body.publishedAt().isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1))||body.images()==null||body.images().size()>24)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(sources.existsBySourceUrl(body.sourceUrl()))return ApiResponse.ok(Map.of("skipped",true));
  // Persist ownership before upload, so a later failure leaves a cleanup record.
  ImportedNews record=sources.saveAndFlush(new ImportedNews(body.sourceUrl(),body.publishedAt()));
  List<String> keys=new ArrayList<>();String content=body.content(),cover="";
  try {
   for(Image image:body.images()){
    if(image.data().length()>7000000)throw new IllegalArgumentException();
    var uploaded=media.uploadNewsImage(Base64.getDecoder().decode(image.data()),image.mime());keys.add(uploaded.key());record.mediaKeys=String.join("\n",keys);sources.saveAndFlush(record);
    content=content.replace(image.url(),uploaded.url());if(image.url().equals(body.cover()))cover=uploaded.key();
   }
   var created=service.create(new NewsRequest(body.title(),"","硬件资讯",content,cover,NewsStatus.PUBLISHED));record.newsId=created.id();sources.saveAndFlush(record);
   var row=news.findById(record.newsId).orElseThrow();row.setPublishedAt(body.publishedAt());news.save(row);
   return ApiResponse.ok(Map.of("id",record.newsId));
  } catch(RuntimeException e){record.publishedAt=cutoff().minusDays(1);sources.save(record);throw e;}
 }
 @PostMapping("/cleanup") public synchronized ApiResponse<?> cleanup(@RequestHeader(value="X-News-Sync-Key",defaultValue="") String given){check(given);int deleted=0,failed=0;
  for(var row:sources.findByPublishedAtBefore(cutoff())){try{
   for(String object:row.mediaKeys.split("\n"))if(!object.isBlank())media.deleteNewsImage(object);
   if(row.newsId!=null && news.existsById(row.newsId))service.delete(row.newsId);
   sources.delete(row);deleted++;
  }catch(RuntimeException e){failed++;}}
  return ApiResponse.ok(Map.of("deleted",deleted,"failed",failed));
 }
}
