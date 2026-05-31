package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "jd_product_snapshot")
public class JdProductSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long hardwarePartId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private HardwarePartType type;

    @Column(nullable = false, length = 80)
    private String skuId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false)
    private Integer price;

    @Column(length = 500)
    private String image;

    @Column(length = 500)
    private String link;

    @Column(length = 120)
    private String shopName;

    @Column(length = 120)
    private String matchedModel;

    @Column(nullable = false)
    private boolean available;

    @Column(nullable = false, length = 30)
    private String source;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawKeyword;

    @Column(nullable = false)
    private LocalDateTime capturedAt;

    protected JdProductSnapshot() {
    }

    public JdProductSnapshot(
            Long hardwarePartId,
            HardwarePartType type,
            String skuId,
            String title,
            Integer price,
            String image,
            String link,
            String shopName,
            String matchedModel,
            boolean available,
            String source,
            String rawKeyword,
            LocalDateTime capturedAt
    ) {
        this.hardwarePartId = hardwarePartId;
        this.type = type;
        this.skuId = clean(skuId, 80);
        this.title = clean(title, 240);
        this.price = price == null ? 0 : Math.max(price, 0);
        this.image = clean(image, 500);
        this.link = clean(link, 500);
        this.shopName = clean(shopName, 120);
        this.matchedModel = clean(matchedModel, 120);
        this.available = available;
        this.source = clean(source, 30);
        this.rawKeyword = rawKeyword == null ? "" : rawKeyword.trim();
        this.capturedAt = capturedAt == null ? LocalDateTime.now() : capturedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getHardwarePartId() {
        return hardwarePartId;
    }

    public HardwarePartType getType() {
        return type;
    }

    public String getSkuId() {
        return skuId == null ? "" : skuId;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public Integer getPrice() {
        return price == null ? 0 : price;
    }

    public String getImage() {
        return image == null ? "" : image;
    }

    public String getLink() {
        return link == null ? "" : link;
    }

    public String getShopName() {
        return shopName == null ? "" : shopName;
    }

    public String getMatchedModel() {
        return matchedModel == null ? "" : matchedModel;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getSource() {
        return source == null ? "" : source;
    }

    public String getRawKeyword() {
        return rawKeyword == null ? "" : rawKeyword;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
