package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.JdProductResponse;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class JdProductSearchService {

    private static final int SEARCH_LIMIT = 20;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";
    private static final Pattern BOARD_CHIPSET_PATTERN = Pattern.compile("\\b[ABHXZ]\\d{3,4}[A-Z]{0,3}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CPU_VARIANT_PATTERN = Pattern.compile("\\b(?:I[3579]|U[579]|ULTRA\\s*[579])[-\\s]?\\d{4,5}[A-Z]{0,3}\\b|\\b\\d{4,5}(?:X3D2|X3D|XT|X|F|G)?\\b", Pattern.CASE_INSENSITIVE);

    private final HardwarePartRepository hardwarePartRepository;
    private final HardwareModelMatcher modelMatcher;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();

    public JdProductSearchService(HardwarePartRepository hardwarePartRepository, HardwareModelMatcher modelMatcher, ObjectMapper objectMapper) {
        this.hardwarePartRepository = hardwarePartRepository;
        this.modelMatcher = modelMatcher;
        this.objectMapper = objectMapper;
    }

    public List<JdProductResponse> search(String keyword, HardwarePartType type) {
        return search(keyword, type, null);
    }

    public List<JdProductResponse> search(String keyword, HardwarePartType type, Long partId) {
        if (partId != null) {
            return hardwarePartRepository.findById(partId)
                    .map(part -> searchProductsForPart(keyword, part).stream().map(this::toResponse).toList())
                    .orElse(List.of());
        }
        return searchProducts(keyword, type).stream().map(this::toResponse).toList();
    }

    public JdProduct bestForPart(HardwarePart part) {
        return bestAvailableForPart(part)
                .orElse(toProductSnapshot(part, "", part.getPrice(), "", "", "", ""));
    }

    public Optional<JdProduct> bestAvailableForPart(HardwarePart part) {
        String keyword = part.getModel() + " " + typeKeyword(part.getType());
        return searchProductsForPart(keyword, part, false).stream()
                .filter(product -> part.getId().equals(product.hardwarePartId()))
                .filter(this::isAvailableProduct)
                .findFirst();
    }

    public List<JdProduct> searchProductsForPart(String keyword, HardwarePart part) {
        return searchProductsForPart(keyword, part, true);
    }

    private List<JdProduct> searchProductsForPart(String keyword, HardwarePart part, boolean allowBundleFallback) {
        String cleanKeyword = StringUtils.hasText(keyword) ? keyword.trim() : part.getModel() + " " + typeKeyword(part.getType());
        String cacheKey = "PART:" + part.getId() + ":" + (allowBundleFallback ? "BUNDLE:" : "SINGLE:") + cleanKeyword.toLowerCase(Locale.ROOT);
        CacheItem cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.products();
        }
        List<String> candidateKeywords = productKeywords(cleanKeyword, part);
        List<JdProduct> products = collectProductsForPart(candidateKeywords, part, true, false);
        if (products.isEmpty()) {
            products = collectProductsForPart(candidateKeywords, part, false, false);
        }
        if (products.isEmpty() && allowBundleFallback && part.getType() == HardwarePartType.CPU) {
            products = collectProductsForPart(candidateKeywords, part, false, true);
        }
        products = products.stream().limit(SEARCH_LIMIT).toList();
        cache.put(cacheKey, new CacheItem(products, Instant.now().plus(CACHE_TTL)));
        return products;
    }

    private List<JdProduct> collectProductsForPart(List<String> candidateKeywords, HardwarePart part, boolean strictCategory, boolean allowCpuBundle) {
        List<JdProduct> products = new ArrayList<>();
        for (String candidateKeyword : candidateKeywords) {
            try {
                products.addAll(fetchProducts(candidateKeyword, part.getType(), List.of(part), strictCategory, allowCpuBundle).stream()
                        .filter(product -> part.getId().equals(product.hardwarePartId()))
                        .toList());
            } catch (IOException | RuntimeException ignored) {
                // JD occasionally returns a risk-control page or malformed data for one keyword.
                // Keep successful results from the other keyword variants instead of blanking the picker.
            }
            products = deduplicate(products);
            if (products.size() >= SEARCH_LIMIT) {
                break;
            }
        }
        return products;
    }

    public List<JdProduct> searchProducts(String keyword, HardwarePartType type) {
        String cleanKeyword = StringUtils.hasText(keyword) ? keyword.trim() : typeKeyword(type);
        if (!StringUtils.hasText(cleanKeyword)) {
            return List.of();
        }
        String cacheKey = (type == null ? "ALL" : type.name()) + ":" + cleanKeyword.toLowerCase(Locale.ROOT);
        CacheItem cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.products();
        }
        List<JdProduct> products;
        try {
            List<HardwarePart> candidates = hardwarePartRepository.findByEnabledTrue().stream()
                    .filter(part -> type == null || part.getType() == type)
                    .sorted(Comparator.comparing(HardwarePart::getType).thenComparing(HardwarePart::getModel))
                    .toList();
            products = fetchProducts(cleanKeyword, type, candidates, true, false);
            if (products.isEmpty()) {
                products = fetchProducts(cleanKeyword, type, candidates, false, false);
            }
        } catch (IOException | RuntimeException ignored) {
            products = List.of();
        }
        cache.put(cacheKey, new CacheItem(products, Instant.now().plus(CACHE_TTL)));
        return products;
    }

    private List<JdProduct> fetchProducts(String keyword, HardwarePartType type, List<HardwarePart> candidates, boolean strictCategory, boolean allowCpuBundle) throws IOException {
        List<JdProduct> products = fetchHotSearchProducts(keyword, type, candidates, strictCategory, allowCpuBundle);
        if (!products.isEmpty()) {
            return products;
        }
        return fetchDesktopSearchProducts(keyword, type, candidates, allowCpuBundle);
    }

    private List<JdProduct> fetchDesktopSearchProducts(String keyword, HardwarePartType type, List<HardwarePart> candidates, boolean allowCpuBundle) throws IOException {
        String url = "https://search.jd.com/Search?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&enc=utf-8";
        Document document = connect(url, "https://www.jd.com/").get();
        if (isVerificationPage(document)) {
            return List.of();
        }
        List<Element> items = new ArrayList<>(document.select("li.gl-item"));
        if (items.isEmpty()) {
            items.addAll(document.select("div.gl-i-wrap"));
        }
        List<JdProduct> result = new ArrayList<>();
        for (Element item : items) {
            if (result.size() >= SEARCH_LIMIT) {
                break;
            }
            String skuId = firstText(item.attr("data-sku"), item.selectFirst("[data-sku]") == null ? "" : item.selectFirst("[data-sku]").attr("data-sku"));
            String title = item.select(".p-name em, .p-name a, .p-name").text().replace("  ", " ").trim();
            if (!StringUtils.hasText(title)) {
                continue;
            }
            if (!matchesProductTypeTitle(title, type, allowCpuBundle)) {
                continue;
            }
            HardwarePart matchedPart = modelMatcher.matchPart(title, type, candidates).orElse(null);
            if (matchedPart == null) {
                continue;
            }
            if (!matchesSelectedVariant(title, matchedPart)) {
                continue;
            }
            String priceText = item.select(".p-price i").text().trim();
            Integer price = parsePrice(priceText);
            String image = normalizeImage(firstText(
                    attr(item, ".p-img img", "data-lazy-img"),
                    attr(item, ".p-img img", "src"),
                    attr(item, "img", "data-lazy-img"),
                    attr(item, "img", "src")
            ));
            String link = normalizeLink(firstText(
                    attr(item, ".p-img a", "href"),
                    attr(item, ".p-name a", "href"),
                    skuId.isBlank() ? "" : "https://item.jd.com/" + skuId + ".html"
            ));
            if (skuId.isBlank()) {
                skuId = extractSkuId(link);
            }
            if (!isAvailableProduct(title, price, link, skuId)) {
                continue;
            }
            result.add(toProductSnapshot(matchedPart, title, price, image, link, skuId, ""));
        }
        return result;
    }

    private List<JdProduct> fetchHotSearchProducts(String keyword, HardwarePartType type, List<HardwarePart> candidates, boolean strictCategory, boolean allowCpuBundle) throws IOException {
        String url = "https://re.jd.com/search?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        Document document = connect(url, "https://re.jd.com/").get();
        if (isVerificationPage(document)) {
            return List.of();
        }
        String configJson = extractConfigsJson(document);
        if (!StringUtils.hasText(configJson)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(configJson);
        List<JdProduct> result = new ArrayList<>();
        for (JsonNode module : root.path("moduleInfo")) {
            String moduleId = module.path("id").asText("");
            if (!"goods-pc-filter".equals(moduleId) && !"goods-pc-search".equals(moduleId)) {
                continue;
            }
            for (JsonNode item : module.path("data")) {
                if (result.size() >= SEARCH_LIMIT) {
                    return result;
                }
                String skuId = item.path("id").asText("");
                String title = item.path("title").asText("").replaceAll("\\s+", " ").trim();
                if (!StringUtils.hasText(skuId) || !StringUtils.hasText(title)) {
                    continue;
                }
                if (!matchesProductTypeTitle(title, type, allowCpuBundle)) {
                    continue;
                }
                String cid3 = item.path("cid3").asText("");
                if (strictCategory && StringUtils.hasText(cid3) && !matchesCategory(type, cid3, allowCpuBundle)) {
                    continue;
                }
                HardwarePart matchedPart = candidates.size() == 1
                        ? candidates.get(0)
                        : modelMatcher.matchPart(title, type, candidates).orElse(null);
                if (matchedPart == null || !modelMatcher.matchesPartModel(title, matchedPart)) {
                    continue;
                }
                if (!matchesSelectedVariant(title, matchedPart)) {
                    continue;
                }
                String image = normalizeImage(item.path("imageUrl").asText(""));
                String link = normalizeLink(firstText(item.path("landUrl").asText(""), "https://item.jd.com/" + skuId + ".html"));
                String shopName = item.path("shopName").asText("");
                Integer price = extractPrice(item);
                if (!isAvailableProduct(title, price, link, skuId)) {
                    continue;
                }
                result.add(toProductSnapshot(matchedPart, title, price, image, link, skuId, shopName));
            }
        }
        return result;
    }

    private List<String> productKeywords(String originalKeyword, HardwarePart part) {
        List<String> keywords = new ArrayList<>();
        addKeyword(keywords, originalKeyword);
        addKeyword(keywords, part.getModel() + " " + typeKeyword(part.getType()));
        String modelKey = modelMatcher.extractModel(part.getModel(), part.getType());
        if (!StringUtils.hasText(modelKey)) {
            modelKey = part.getModel();
        }
        addKeyword(keywords, modelKey + " " + typeKeyword(part.getType()));
        if (part.getType() == HardwarePartType.CPU) {
            addKeyword(keywords, modelKey + " 处理器");
            if (part.getModel().toUpperCase(Locale.ROOT).contains("RYZEN")) {
                addKeyword(keywords, "AMD " + modelKey);
                addKeyword(keywords, "锐龙 " + modelKey);
            }
            if (part.getModel().toUpperCase(Locale.ROOT).contains("CORE")) {
                addKeyword(keywords, "Intel " + modelKey);
                addKeyword(keywords, "酷睿 " + modelKey);
            }
        } else if (part.getType() == HardwarePartType.MOTHERBOARD) {
            addKeyword(keywords, modelKey + " 主板");
        } else if (part.getType() == HardwarePartType.GPU) {
            addKeyword(keywords, modelKey + " 显卡");
        }
        return keywords;
    }

    private void addKeyword(List<String> keywords, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String clean = keyword.trim().replaceAll("\\s+", " ");
        boolean exists = keywords.stream().anyMatch(item -> item.equalsIgnoreCase(clean));
        if (!exists) {
            keywords.add(clean);
        }
    }

    private List<JdProduct> deduplicate(List<JdProduct> products) {
        Map<String, JdProduct> deduplicated = new java.util.LinkedHashMap<>();
        for (JdProduct product : products) {
            String key = StringUtils.hasText(product.skuId()) ? product.skuId() : product.title();
            deduplicated.putIfAbsent(key, product);
        }
        return deduplicated.values().stream().toList();
    }

    private boolean isAvailableProduct(JdProduct product) {
        return product != null && isAvailableProduct(product.title(), product.price(), product.link(), product.skuId());
    }

    private boolean isAvailableProduct(String title, Integer price, String link, String skuId) {
        return StringUtils.hasText(title)
                && price != null
                && price > 0
                && StringUtils.hasText(link)
                && StringUtils.hasText(skuId);
    }

    private JdProduct toProductSnapshot(HardwarePart part, String title, Integer price, String image, String link, String skuId, String shopName) {
        return new JdProduct(
                title,
                price == null ? 0 : price,
                image,
                link,
                skuId,
                shopName,
                part.getModel(),
                part.getId(),
                part.getType(),
                part.getPerformanceScore(),
                part.getSocketType(),
                part.getMemoryType(),
                part.getFormFactor(),
                part.getWattage(),
                part.getGpuLengthMm(),
                part.getCoolerHeightMm(),
                part.getSupportedFormFactors(),
                part.getSpecs()
        );
    }

    private JdProductResponse toResponse(JdProduct product) {
        return new JdProductResponse(
                product.title(),
                product.price(),
                product.image(),
                product.link(),
                product.skuId(),
                product.shopName(),
                product.matchedModel(),
                product.hardwarePartId(),
                product.type(),
                product.performanceScore(),
                product.socketType(),
                product.memoryType(),
                product.formFactor(),
                product.wattage(),
                product.gpuLengthMm(),
                product.coolerHeightMm(),
                product.supportedFormFactors(),
                product.specs()
        );
    }

    private Connection connect(String url, String referrer) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(referrer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .timeout(10_000)
                .ignoreHttpErrors(true);
    }

    private boolean isVerificationPage(Document document) {
        String title = document.title();
        return title.contains("验证") || document.text().contains("京东验证") || document.text().contains("JDR_shields");
    }

    private String extractConfigsJson(Document document) {
        for (Element script : document.select("script")) {
            String html = script.html();
            int start = html.indexOf("var configs = ");
            if (start < 0) {
                continue;
            }
            start += "var configs = ".length();
            int end = html.indexOf("; </script>", start);
            if (end < 0) {
                end = html.indexOf("; var", start);
            }
            if (end < 0) {
                end = html.lastIndexOf(';');
            }
            if (end > start) {
                return html.substring(start, end).trim();
            }
        }
        return "";
    }

    private boolean matchesCategory(HardwarePartType type, String cid3, boolean allowCpuBundle) {
        if (type == null || !StringUtils.hasText(cid3)) {
            return true;
        }
        if (allowCpuBundle && type == HardwarePartType.CPU && "17466".equals(cid3)) {
            return true;
        }
        String expected = switch (type) {
            case CPU -> "678";
            case MOTHERBOARD -> "681";
            case GPU -> "679";
            case MEMORY -> "680";
            case STORAGE -> "11303";
            case PSU -> "691";
            case CASE -> "687";
            case COOLER -> "682";
        };
        return expected.equals(cid3);
    }

    private boolean matchesProductTypeTitle(String title, HardwarePartType type, boolean allowCpuBundle) {
        if (type == null || !StringUtils.hasText(title)) {
            return true;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        return switch (type) {
            case CPU -> (allowCpuBundle && isCpuMotherboardBundleTitle(title))
                    || ((normalizedTitle.contains("CPU")
                    || title.contains("处理器")
                    || title.contains("盒装")
                    || title.contains("散片"))
                    && !BOARD_CHIPSET_PATTERN.matcher(normalizedTitle).find()
                    && !title.contains("主板")
                    && !title.contains("板U")
                    && !title.contains("套装")
                    && !title.contains("整机")
                    && !title.contains("主机"));
            case MOTHERBOARD -> title.contains("主板")
                    && !title.contains("CPU套装")
                    && !title.contains("主板+CPU")
                    && !title.contains("板U")
                    && !title.contains("套装");
            case GPU -> (title.contains("显卡") || normalizedTitle.contains("RTX") || normalizedTitle.contains("GTX") || normalizedTitle.contains("RX"))
                    && !title.contains("整机")
                    && !title.contains("主机");
            case MEMORY -> title.contains("内存") || normalizedTitle.contains("DDR");
            case STORAGE -> title.contains("固态") || title.contains("硬盘") || normalizedTitle.contains("SSD") || normalizedTitle.contains("NVME");
            case PSU -> title.contains("电源") || normalizedTitle.contains(" PSU");
            case CASE -> title.contains("机箱");
            case COOLER -> title.contains("散热") || title.contains("风冷") || title.contains("水冷");
        };
    }

    private boolean isCpuMotherboardBundleTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        return title.contains("主板CPU套装")
                || title.contains("主板+CPU")
                || title.contains("板U")
                || (title.contains("主板") && title.contains("CPU") && title.contains("套装"))
                || (normalizedTitle.contains("CPU+") && BOARD_CHIPSET_PATTERN.matcher(normalizedTitle).find());
    }

    private boolean matchesSelectedVariant(String title, HardwarePart part) {
        if (part == null || part.getType() != HardwarePartType.CPU || !StringUtils.hasText(title)) {
            return true;
        }
        String targetModel = modelMatcher.extractModel(part.getModel(), HardwarePartType.CPU);
        String normalizedTarget = normalizeVariantToken(targetModel);
        if (!StringUtils.hasText(normalizedTarget)) {
            return true;
        }
        List<String> variants = new ArrayList<>();
        Matcher matcher = CPU_VARIANT_PATTERN.matcher(title);
        while (matcher.find()) {
            String normalized = normalizeVariantToken(matcher.group());
            if (StringUtils.hasText(normalized)) {
                variants.add(normalized);
            }
        }
        if (variants.size() <= 1) {
            return variants.isEmpty() || variants.contains(normalizedTarget);
        }
        return normalizedTarget.equals(variants.get(variants.size() - 1));
    }

    private String normalizeVariantToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
    }

    private String typeKeyword(HardwarePartType type) {
        if (type == null) {
            return "电脑硬件";
        }
        return switch (type) {
            case CPU -> "CPU";
            case MOTHERBOARD -> "主板";
            case GPU -> "显卡";
            case MEMORY -> "内存";
            case STORAGE -> "固态硬盘";
            case PSU -> "电源";
            case CASE -> "机箱";
            case COOLER -> "散热器";
        };
    }

    private String attr(Element parent, String selector, String attr) {
        Element element = parent.selectFirst(selector);
        return element == null ? "" : element.attr(attr);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Integer parsePrice(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String clean = value.replaceAll("[^0-9.]", "");
        if (!StringUtils.hasText(clean)) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(clean));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Integer extractPrice(JsonNode item) {
        Integer direct = parsePrice(firstText(
                item.path("price").asText(""),
                item.path("jdPrice").asText(""),
                item.path("salePrice").asText(""),
                item.path("pPrice").asText(""),
                item.path("realPrice").asText("")
        ));
        if (direct > 0) {
            return direct;
        }
        for (String field : List.of("price", "jdPrice", "salePrice", "pPrice", "realPrice")) {
            JsonNode value = item.findValue(field);
            Integer parsed = parsePrice(value == null ? "" : value.asText(""));
            if (parsed > 0) {
                return parsed;
            }
        }
        return 0;
    }

    private String normalizeImage(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String image = value.trim();
        if (image.startsWith("//")) {
            return "https:" + image;
        }
        if (image.startsWith("/")) {
            return "https://img10.360buyimg.com" + image;
        }
        if (image.startsWith("jfs/")) {
            return "https://img10.360buyimg.com/n1/" + image;
        }
        return image;
    }

    private String normalizeLink(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String link = value.trim();
        if (link.startsWith("//")) {
            return "https:" + link;
        }
        if (link.startsWith("/")) {
            return "https://item.jd.com" + link;
        }
        return link;
    }

    private String extractSkuId(String link) {
        if (!StringUtils.hasText(link)) {
            return "";
        }
        int slash = link.lastIndexOf('/');
        int dot = link.lastIndexOf('.');
        if (slash >= 0 && dot > slash) {
            return link.substring(slash + 1, dot);
        }
        return "";
    }

    private record CacheItem(List<JdProduct> products, Instant expiresAt) {
    }
}
