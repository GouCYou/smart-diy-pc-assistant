package cn.cctstudio.smartdiypcassistantbackend.news;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="news_import_source")
public class ImportedNews {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false,unique=true,length=500) String sourceUrl;
 Long newsId;
 @Column(nullable=false) LocalDateTime publishedAt;
 @Column(columnDefinition="text") String mediaKeys;
 protected ImportedNews(){}
 ImportedNews(String url,LocalDateTime date){sourceUrl=url;publishedAt=date;mediaKeys="";}
}
