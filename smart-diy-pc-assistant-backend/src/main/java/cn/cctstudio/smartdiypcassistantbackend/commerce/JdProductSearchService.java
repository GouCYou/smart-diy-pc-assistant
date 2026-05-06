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

    private static final int SEARCH_LIMIT = 36;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";
    private static final Pattern BOARD_CHIPSET_PATTERN = Pattern.compile("\\b[ABHXZ]\\d{3,4}[A-Z]{0,3}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CPU_VARIANT_PATTERN = Pattern.compile("\\b(?:I[3579]|U[579]|ULTRA\\s*[579])[-\\s]?\\d{4,5}[A-Z]{0,3}\\b|\\b\\d{4,5}(?:X3D2|X3D|XT|X|F|G)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WATTAGE_PATTERN = Pattern.compile("\\b(\\d{3,4})\\s*W\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_CAPACITY_PATTERN = Pattern.compile("\\b(\\d{1,3})\\s*G(?:B)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_PAIR_PATTERN = Pattern.compile("(\\d{1,3})\\s*G(?:B)?\\s*[xX×*]\\s*2", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_REVERSE_PAIR_PATTERN = Pattern.compile("2\\s*[xX×*]\\s*(\\d{1,3})\\s*G(?:B)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_SPEED_PATTERN = Pattern.compile("\\b(3200|3600|4800|5200|5600|6000|6200|6400|6800|7200|7600|8000|8400)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORAGE_CAPACITY_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(TB|T|GB|G)\\b", Pattern.CASE_INSENSITIVE);

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
                .orElse(toProductSnapshot(part, "", 0, "", "", "", ""));
    }

    public Optional<JdProduct> bestAvailableForPart(HardwarePart part) {
        return bestAvailableForPart(part, "");
    }

    public Optional<JdProduct> bestAvailableForPart(HardwarePart part, String preferredColor) {
        return bestAvailableForPart(part, preferredColor, false);
    }

    public Optional<JdProduct> bestAvailableForPart(HardwarePart part, String preferredColor, boolean allowCpuBundleFallback) {
        return bestAvailableForPart(part, preferredColor, allowCpuBundleFallback, "", "");
    }

    public Optional<JdProduct> bestAvailableForPart(
            HardwarePart part,
            String preferredColor,
            boolean allowCpuBundleFallback,
            String preferredCaseStyle,
            String preferredCoolerType
    ) {
        return availableProductsForPart(part, preferredColor, allowCpuBundleFallback, preferredCaseStyle, preferredCoolerType)
                .stream()
                .findFirst();
    }

    public List<JdProduct> availableProductsForPart(
            HardwarePart part,
            String preferredColor,
            boolean allowCpuBundleFallback,
            String preferredCaseStyle,
            String preferredCoolerType
    ) {
        String keyword = part.getModel() + " " + typeKeyword(part.getType());
        return searchProductsForPart(keyword, part, allowCpuBundleFallback).stream()
                .filter(product -> part.getId().equals(product.hardwarePartId()))
                .filter(this::isAvailableProduct)
                .filter(product -> hasPlausibleProductPrice(part, product))
                .sorted(Comparator.comparingInt((JdProduct product) -> productRank(part, product, preferredColor, preferredCaseStyle, preferredCoolerType)).reversed())
                .toList();
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
            HardwarePart matchedPart = matchProductPart(title, type, candidates);
            if (matchedPart == null) {
                continue;
            }
            if (!matchesSelectedVariant(title, matchedPart)) {
                continue;
            }
            if (!matchesStandardModelForProduct(title, matchedPart)) {
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
                        : matchProductPart(title, type, candidates);
                if (matchedPart == null || !matchesStandardModelForProduct(title, matchedPart)) {
                    continue;
                }
                if (!matchesSelectedVariant(title, matchedPart)) {
                    continue;
                }
                if (!matchesCoolerIntent(title, matchedPart)) {
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
        if (part.getType() == HardwarePartType.CASE) {
            addCaseKeywords(keywords, part);
            addKeyword(keywords, originalKeyword);
            return keywords;
        }
        if (part.getType() == HardwarePartType.COOLER) {
            addCoolerKeywords(keywords, part);
            addKeyword(keywords, originalKeyword);
            return keywords;
        }
        if (part.getType() == HardwarePartType.MEMORY) {
            addMemoryKeywords(keywords, part);
            addKeyword(keywords, originalKeyword);
            return keywords;
        }
        if (part.getType() == HardwarePartType.STORAGE) {
            addStorageKeywords(keywords, part);
            addKeyword(keywords, originalKeyword);
            return keywords;
        }
        if (part.getType() == HardwarePartType.PSU) {
            addPsuKeywords(keywords, part);
            addKeyword(keywords, originalKeyword);
            return keywords;
        }
        addKeyword(keywords, originalKeyword);
        addKeyword(keywords, part.getModel() + " " + typeKeyword(part.getType()));
        String modelKey = modelMatcher.extractModel(part.getModel(), part.getType());
        if (!StringUtils.hasText(modelKey)) {
            modelKey = part.getModel();
        }
        addKeyword(keywords, modelKey + " " + typeKeyword(part.getType()));
        if (part.getType() == HardwarePartType.CPU) {
            addKeyword(keywords, modelKey + " 处理器");
            addKeyword(keywords, modelKey + " 盒装 处理器");
            if (part.getModel().toUpperCase(Locale.ROOT).contains("RYZEN")) {
                addKeyword(keywords, "AMD " + modelKey);
                addKeyword(keywords, "锐龙 " + modelKey);
                addKeyword(keywords, "AMD 锐龙 " + extractPrimaryModelToken(modelKey) + " 盒装CPU");
                addKeyword(keywords, "锐龙 " + extractPrimaryModelToken(modelKey) + " 台式机处理器");
            }
            if (part.getModel().toUpperCase(Locale.ROOT).contains("CORE")) {
                addKeyword(keywords, "Intel " + modelKey);
                addKeyword(keywords, "酷睿 " + modelKey);
                addKeyword(keywords, "英特尔 酷睿 " + modelKey + " 盒装CPU");
                addKeyword(keywords, modelKey + " 台式机处理器");
            }
        } else if (part.getType() == HardwarePartType.MOTHERBOARD) {
            addKeyword(keywords, modelKey + " 主板");
            addMotherboardKeywords(keywords, part, modelKey);
        } else if (part.getType() == HardwarePartType.GPU) {
            addKeyword(keywords, modelKey + " 显卡");
            addGpuKeywords(keywords, modelKey);
        } else if (part.getType() == HardwarePartType.CASE) {
            addCaseKeywords(keywords, part);
        } else if (part.getType() == HardwarePartType.COOLER) {
            addCoolerKeywords(keywords, part);
        }
        return keywords;
    }

    private void addMotherboardKeywords(List<String> keywords, HardwarePart part, String modelKey) {
        String chipset = extractBoardChipset(part.getModel());
        if (!StringUtils.hasText(chipset)) {
            chipset = modelKey;
        }
        String memoryType = StringUtils.hasText(part.getMemoryType()) ? part.getMemoryType() : "";
        String formFactor = StringUtils.hasText(part.getFormFactor()) ? part.getFormFactor() : "";
        addKeyword(keywords, joinKeyword(chipset, memoryType, "主板"));
        addKeyword(keywords, joinKeyword(chipset, formFactor, "台式机主板"));
        addKeyword(keywords, joinKeyword(part.getSocketType(), chipset, "主板"));
    }

    private void addGpuKeywords(List<String> keywords, String modelKey) {
        String normalized = modelKey.toUpperCase(Locale.ROOT);
        addKeyword(keywords, modelKey + " 台式机显卡 全新");
        if (normalized.startsWith("RTX") || normalized.startsWith("GTX")) {
            addKeyword(keywords, "英伟达 " + modelKey + " 显卡 全新");
            addKeyword(keywords, "NVIDIA " + modelKey + " 显卡");
        } else if (normalized.startsWith("RX")) {
            addKeyword(keywords, "AMD " + modelKey + " 显卡 全新");
            addKeyword(keywords, "Radeon " + modelKey + " 显卡");
        } else if (normalized.startsWith("ARC")) {
            addKeyword(keywords, "英特尔 " + modelKey + " 显卡");
        }
    }

    private void addCaseKeywords(List<String> keywords, HardwarePart part) {
        String model = part.getModel().toUpperCase(Locale.ROOT);
        String formFactor = part.getFormFactor() == null ? "" : part.getFormFactor().toUpperCase(Locale.ROOT);
        if (model.contains("E-ATX") || formFactor.contains("E-ATX")) {
            addKeyword(keywords, "E-ATX 机箱");
            addKeyword(keywords, "EATX 全塔机箱");
            addKeyword(keywords, "全塔机箱");
            addKeyword(keywords, "大机箱");
            addKeyword(keywords, "大塔机箱");
            addKeyword(keywords, "大中塔风道机箱");
            addKeyword(keywords, "E-ATX 海景房机箱");
            addGenericCaseKeywords(keywords);
            return;
        }
        if (model.contains("M-ATX") || formFactor.contains("M-ATX")) {
            addKeyword(keywords, "M-ATX 机箱");
            addKeyword(keywords, "MATX 小机箱");
            addKeyword(keywords, "M-ATX 小机箱");
            addKeyword(keywords, "MATX 海景房机箱");
            addKeyword(keywords, "M-ATX 台式机机箱");
            addKeyword(keywords, "MATX 风道机箱");
            addKeyword(keywords, "紧凑机箱");
            addKeyword(keywords, "小机箱");
            addKeyword(keywords, "海景房机箱");
            addGenericCaseKeywords(keywords);
            return;
        }
        if (model.contains("ITX") || formFactor.contains("ITX")) {
            addKeyword(keywords, "ITX 小机箱");
            addKeyword(keywords, "迷你机箱");
            addKeyword(keywords, "小钢炮机箱");
            addKeyword(keywords, "ITX 迷你海景房机箱");
            addKeyword(keywords, "A4 ITX 机箱");
            addGenericCaseKeywords(keywords);
            return;
        }
        addKeyword(keywords, "ATX 机箱");
        addKeyword(keywords, "中塔机箱");
        addKeyword(keywords, "台式机中塔机箱");
        addKeyword(keywords, "ATX 中塔机箱");
        addKeyword(keywords, "ATX 风道机箱");
        addKeyword(keywords, "ATX 海景房机箱");
        addKeyword(keywords, "中塔海景房机箱");
        addKeyword(keywords, "网孔中塔机箱");
        addKeyword(keywords, "风道机箱");
        addKeyword(keywords, "海景房机箱");
        addGenericCaseKeywords(keywords);
    }

    private void addGenericCaseKeywords(List<String> keywords) {
        addKeyword(keywords, "电脑机箱");
        addKeyword(keywords, "台式机机箱");
        addKeyword(keywords, "主机箱");
    }

    private void addCoolerKeywords(List<String> keywords, HardwarePart part) {
        String model = part.getModel().toUpperCase(Locale.ROOT);
        if (model.contains("360")) {
            addKeyword(keywords, "360 水冷");
            addKeyword(keywords, "360 一体式水冷");
            addKeyword(keywords, "CPU散热器 水冷 360");
            addKeyword(keywords, "360 一体水冷 CPU");
            return;
        }
        if (model.contains("280")) {
            addKeyword(keywords, "280 水冷");
            addKeyword(keywords, "280 一体式水冷");
            addKeyword(keywords, "CPU散热器 水冷 280");
            addKeyword(keywords, "280 一体水冷 CPU");
            return;
        }
        if (model.contains("240")) {
            addKeyword(keywords, "240 水冷");
            addKeyword(keywords, "240 一体式水冷");
            addKeyword(keywords, "CPU散热器 水冷 240");
            addKeyword(keywords, "240 一体水冷 CPU");
            return;
        }
        if (model.contains("DUAL TOWER")) {
            addKeyword(keywords, "双塔 风冷 散热器");
            addKeyword(keywords, "CPU 双塔散热器");
            addKeyword(keywords, "双塔 CPU 风冷");
            addKeyword(keywords, "双塔风冷 CPU散热器");
            return;
        }
        if (model.contains("LOW PROFILE")) {
            addKeyword(keywords, "下压式 散热器");
            addKeyword(keywords, "ITX CPU 散热器");
            addKeyword(keywords, "下压式 CPU 风冷");
            return;
        }
        addKeyword(keywords, "塔式 风冷 散热器");
        addKeyword(keywords, "CPU 风冷散热器");
        addKeyword(keywords, "单塔 CPU 散热器");
        addKeyword(keywords, "塔式风冷 CPU散热器");
    }

    private void addMemoryKeywords(List<String> keywords, HardwarePart part) {
        String text = partText(part);
        String type = text.contains("DDR4") ? "DDR4" : "DDR5";
        int capacity = extractMemoryCapacityGb(text);
        String speed = extractMemorySpeed(text);
        if (capacity > 0 && StringUtils.hasText(speed)) {
            addKeyword(keywords, capacity + "GB " + type + " " + speed + " 全新 台式机内存");
            addKeyword(keywords, capacity + "GB " + type + " " + speed + " 台式机内存");
            addKeyword(keywords, capacity + "G " + type + " " + speed + " 内存条");
            if (capacity % 2 == 0) {
                addKeyword(keywords, type + " " + speed + " " + (capacity / 2) + "Gx2 全新 套条");
                addKeyword(keywords, type + " " + speed + " " + (capacity / 2) + "Gx2 套条");
                addKeyword(keywords, (capacity / 2) + "Gx2 " + type + " " + speed + " 内存");
            }
            addKeyword(keywords, capacity + "GB " + type + " 台式机内存");
            addKeyword(keywords, type + " " + speed + " 台式机内存");
            addKeyword(keywords, type + " " + speed + " 游戏内存");
            addKeyword(keywords, capacity + "GB 内存条 台式机");
        }
        if (capacity > 0) {
            addKeyword(keywords, capacity + "GB " + type + " 台式机内存");
            addKeyword(keywords, capacity + "G " + type + " 内存条");
            if (capacity % 2 == 0) {
                addKeyword(keywords, type + " " + (capacity / 2) + "Gx2 套条");
            }
        }
        addKeyword(keywords, type + " 台式机内存");
        addKeyword(keywords, type + " 内存条");
    }

    private void addStorageKeywords(List<String> keywords, HardwarePart part) {
        String text = partText(part);
        String capacity = extractStorageCapacityToken(text);
        boolean pcie5 = containsAnyCompact(text, "PCIE50", "PCIE5", "GEN5");
        boolean pcie4 = containsAnyCompact(text, "PCIE40", "PCIE4", "GEN4");
        String genLabel = pcie5 ? "PCIe 5.0" : pcie4 ? "PCIe 4.0" : "PCIe";
        if (StringUtils.hasText(capacity)) {
            addKeyword(keywords, displayStorageCapacity(capacity) + " " + genLabel + " NVMe 固态硬盘");
            addKeyword(keywords, displayStorageCapacity(capacity) + " M.2 " + genLabel + " SSD");
            return;
        }
        addKeyword(keywords, genLabel + " NVMe 固态硬盘");
    }

    private void addPsuKeywords(List<String> keywords, HardwarePart part) {
        String text = partText(part);
        int wattage = extractWattage(part, text);
        String wattageLabel = wattage > 0 ? wattage + "W" : "";
        boolean atx31 = containsAnyCompact(text, "ATX31", "PCIE51");
        boolean atx30 = containsAnyCompact(text, "ATX30", "PCIE50");
        boolean bronze = text.contains("BRONZE") || text.contains("铜牌");
        boolean gold = text.contains("GOLD") || text.contains("金牌");
        String cert = bronze ? "铜牌" : gold ? "金牌" : "";
        String standard = atx31 ? "ATX 3.1" : atx30 ? "ATX 3.0" : "";
        addKeyword(keywords, joinKeyword(wattageLabel, standard, cert, "电源"));
        if (StringUtils.hasText(cert)) {
            addKeyword(keywords, joinKeyword(wattageLabel, cert, "全模组电源"));
        }
        addKeyword(keywords, joinKeyword(wattageLabel, "电脑电源"));
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

    private int productRank(HardwarePart part, JdProduct product, String preferredColor, String preferredCaseStyle, String preferredCoolerType) {
        int score = 0;
        if (StringUtils.hasText(product.image())) {
            score += 12;
        }
        if (StringUtils.hasText(product.shopName())) {
            score += 4;
        }
        String color = productColor(product.title());
        if (StringUtils.hasText(preferredColor)) {
            if (preferredColor.equals(color)) {
                score += 120;
            } else if (!StringUtils.hasText(color)) {
                score += 18;
            } else {
                score -= 45;
            }
        }
        int referencePrice = referencePrice(part);
        int productPrice = product.price() == null ? 0 : product.price();
        if (productPrice > 0) {
            score -= Math.min(140, Math.abs(productPrice - referencePrice) / 18);
            if (productPrice > referencePrice * 1.25) {
                score -= Math.min(180, (productPrice - Math.round(referencePrice * 1.25f)) / 10);
            }
            if (productPrice <= referencePrice * 1.18) {
                score += 18;
            }
            if (productPrice <= referencePrice && productPrice >= referencePrice * 0.45) {
                score += 10;
            }
        }
        String title = product.title().toUpperCase(Locale.ROOT);
        String modelKey = modelMatcher.extractModel(part.getModel(), part.getType());
        if (StringUtils.hasText(modelKey) && title.contains(modelKey.toUpperCase(Locale.ROOT))) {
            score += 35;
        }
        score += productQualityScore(part, product.title());
        score += caseStyleProductScore(part, product.title(), preferredCaseStyle);
        score += coolerProductScore(part, product.title(), preferredCoolerType);
        return score;
    }

    private int productQualityScore(HardwarePart part, String title) {
        if (part == null || !StringUtils.hasText(title)) {
            return 0;
        }
        return switch (part.getType()) {
            case MEMORY -> memoryProductQualityScore(part, title);
            case STORAGE -> storageProductQualityScore(part, title);
            case PSU -> psuProductQualityScore(title);
            case COOLER -> coolerProductQualityScore(part, title);
            default -> 0;
        };
    }

    private int memoryProductQualityScore(HardwarePart part, String title) {
        String text = partText(part);
        int score = 0;
        String targetSpeed = extractMemorySpeed(text);
        if (StringUtils.hasText(targetSpeed)) {
            int target = parseNumber(targetSpeed);
            int titleSpeed = firstMemorySpeed(title);
            if (target > 0 && titleSpeed == target) {
                score += 55;
            } else if (target > 0 && target <= 6400 && titleSpeed >= 6800) {
                score -= 45;
            }
        }
        if (containsAnyText(title, "c28", "cl28", "c30", "cl30", "低时序", "低延迟")) {
            score += 55;
        }
        if (containsAnyText(title, "海力士", "hynix", "a-die", "adie", "a die")) {
            score += 45;
        }
        return score;
    }

    private int storageProductQualityScore(HardwarePart part, String title) {
        String text = partText(part);
        if (containsAnyCompact(text, "PCIE50", "PCIE5", "GEN5")
                && containsAnyCompact(title, "PCIE50", "PCIE5", "GEN5")) {
            return 90;
        }
        return 0;
    }

    private int psuProductQualityScore(String title) {
        int score = 0;
        int certRank = psuCertificationRank(title);
        if (certRank >= 4) {
            score += 100;
        } else if (certRank == 3) {
            score += 30;
        }
        if (containsAnyText(title, "全模组", "全模", "full modular", "full-modular")) {
            score += 45;
        }
        if (containsAnyCompact(title, "ATX31", "PCIE51", "12V2X6")) {
            score += 35;
        }
        if (containsAnyText(title, "海韵", "seasonic", "振华", "super flower", "长城", "great wall", "华硕", "asus", "海盗船", "corsair", "酷冷至尊", "coolermaster", "先马", "fsp")) {
            score += 22;
        }
        return score;
    }

    private int coolerProductQualityScore(HardwarePart part, String title) {
        int score = 0;
        String text = partText(part);
        if (containsAnyCompact(text, "360") && containsAnyCompact(title, "360")) {
            score += 55;
        }
        if (containsAnyText(title, "钛坦", "titan", "lg600", "旗舰", "高性能", "厚排", "水泵", "泵", "lcd", "ips")) {
            score += 110;
        }
        if (containsAnyText(title, "tcomas", "a080", "a090", "la300")) {
            score -= 50;
        }
        return score;
    }

    private boolean hasPlausibleProductPrice(HardwarePart part, JdProduct product) {
        int price = product.price() == null ? 0 : product.price();
        if (price <= 0) {
            return false;
        }
        int reference = Math.max(100, referencePrice(part));
        int maxPrice = switch (part.getType()) {
            case CPU -> isCpuMotherboardBundleTitle(product.title())
                    ? Math.round(reference * 2.25f) + 1500
                    : Math.round(reference * 1.9f) + 700;
            case GPU -> Math.round(reference * 1.85f) + 1500;
            case MEMORY -> Math.round(reference * 2.4f) + 1000;
            case STORAGE -> Math.round(reference * 1.9f) + 1000;
            case MOTHERBOARD, PSU, CASE, COOLER -> Math.round(reference * 2.1f) + 700;
        };
        return price <= maxPrice;
    }

    private int caseStyleProductScore(HardwarePart part, String title, String preferredCaseStyle) {
        if (part.getType() != HardwarePartType.CASE || !StringUtils.hasText(preferredCaseStyle) || !StringUtils.hasText(title)) {
            return 0;
        }
        String normalized = preferredCaseStyle.toUpperCase(Locale.ROOT);
        if ("PANORAMA".equals(normalized)) {
            if (containsAnyText(title, "海景房", "全景", "270°", "270度", "玻璃侧透", "双面玻璃")) {
                return 110;
            }
            if (containsAnyText(title, "网孔", "Mesh", "风道")) {
                return -20;
            }
        }
        if ("TOWER".equals(normalized)) {
            if (containsAnyText(title, "中塔", "风道", "网孔", "Mesh", "Airflow", "散热")) {
                return 100;
            }
            if (containsAnyText(title, "海景房", "全景")) {
                return -22;
            }
        }
        if ("SILENT".equals(normalized)) {
            if (containsAnyText(title, "静音", "隔音", "低噪")) {
                return 120;
            }
        }
        return 0;
    }

    private int coolerProductScore(HardwarePart part, String title, String preferredCoolerType) {
        if (part.getType() != HardwarePartType.COOLER || !StringUtils.hasText(preferredCoolerType) || !StringUtils.hasText(title)) {
            return 0;
        }
        String normalized = preferredCoolerType.toUpperCase(Locale.ROOT);
        if ("AIO".equals(normalized)) {
            return containsAnyText(title, "水冷", "一体水", "AIO") ? 100 : -80;
        }
        if ("AIR".equals(normalized)) {
            return containsAnyText(title, "风冷", "塔式", "双塔", "下压") ? 95 : title.contains("水冷") ? -90 : 0;
        }
        if ("QUIET".equals(normalized)) {
            return containsAnyText(title, "静音", "低噪", "PWM", "温控") ? 70 : 0;
        }
        return 0;
    }

    private int referencePrice(HardwarePart part) {
        if (part.getPrice() != null && part.getPrice() > 0) {
            return part.getPrice();
        }
        return estimatedPartPrice(part);
    }

    private int estimatedPartPrice(HardwarePart part) {
        String model = part.getModel().toUpperCase(Locale.ROOT);
        return switch (part.getType()) {
            case CPU -> estimatedCpuPrice(model);
            case GPU -> estimatedGpuPrice(model);
            case MOTHERBOARD -> estimatedMotherboardPrice(model);
            case MEMORY -> estimatedMemoryPrice(model);
            case STORAGE -> estimatedStoragePrice(model);
            case PSU -> estimatedPsuPrice(model);
            case CASE -> estimatedCasePrice(model);
            case COOLER -> estimatedCoolerPrice(model);
        };
    }

    private int estimatedCpuPrice(String model) {
        if (model.contains("9950X3D2")) return 6500;
        if (model.contains("9950X3D")) return 5200;
        if (model.contains("9950X")) return 4300;
        if (model.contains("9900X3D")) return 3800;
        if (model.contains("9900X")) return 3200;
        if (model.contains("9850X3D")) return 3600;
        if (model.contains("9800X3D")) return 3400;
        if (model.contains("9700X")) return 1900;
        if (model.contains("9600X")) return 1400;
        if (model.contains("7800X3D")) return 2500;
        if (model.contains("7500F")) return 900;
        if (model.contains("5700X3D")) return 1000;
        if (model.contains("5600")) return 600;
        if (model.contains("CORE ULTRA 9")) return 4200;
        if (model.contains("CORE ULTRA 7")) return 2800;
        if (model.contains("CORE ULTRA 5")) return 1800;
        if (model.contains("14700")) return 2400;
        if (model.contains("14600")) return 1500;
        if (model.contains("13490")) return 1100;
        if (model.contains("12400")) return 750;
        return 1500;
    }

    private int estimatedGpuPrice(String model) {
        if (model.contains("RTX 5090")) return 22000;
        if (model.contains("RTX 5080")) return 9000;
        if (model.contains("RTX 5070 TI")) return 6200;
        if (model.contains("RTX 5070")) return 4800;
        if (model.contains("RTX 5060 TI")) return 3000;
        if (model.contains("RTX 5060")) return 2300;
        if (model.contains("RTX 5050")) return 1600;
        if (model.contains("RTX 4070 SUPER")) return 4300;
        if (model.contains("RTX 4070")) return 3400;
        if (model.contains("RTX 4060 TI")) return 2600;
        if (model.contains("RTX 4060")) return 2100;
        if (model.contains("RX 9070 XT")) return 5200;
        if (model.contains("RX 9070")) return 4300;
        if (model.contains("RX 9060 XT")) return 2600;
        if (model.contains("RX 7800 XT")) return 3500;
        if (model.contains("RX 7700 XT")) return 2900;
        return 2500;
    }

    private int estimatedMotherboardPrice(String model) {
        if (model.contains("X870E")) return 2500;
        if (model.contains("X870")) return 1800;
        if (model.contains("B850M")) return 900;
        if (model.contains("B850")) return 1100;
        if (model.contains("B650E")) return 1200;
        if (model.contains("B650M")) return 700;
        if (model.contains("B650")) return 900;
        if (model.contains("Z890")) return 2000;
        if (model.contains("B860M")) return 800;
        if (model.contains("B860")) return 950;
        if (model.contains("H810")) return 550;
        if (model.contains("Z790")) return 1600;
        if (model.contains("B760M")) return 700;
        if (model.contains("B760")) return 850;
        if (model.contains("B660M")) return 550;
        if (model.contains("B550M")) return 550;
        return 800;
    }

    private int estimatedMemoryPrice(String model) {
        if (model.contains("64GB") && model.contains("DDR5")) return 4200;
        if (model.contains("64GB")) return 3200;
        if (model.contains("32GB") && model.contains("DDR5") && model.contains("7200")) return 2600;
        if (model.contains("32GB") && model.contains("DDR5") && model.contains("6400")) return 2300;
        if (model.contains("32GB") && model.contains("DDR5")) return 2100;
        if (model.contains("16GB") && model.contains("DDR5")) return 1100;
        if (model.contains("32GB") && model.contains("DDR4")) return 1200;
        if (model.contains("16GB") && model.contains("DDR4")) return 700;
        return 1500;
    }

    private int estimatedStoragePrice(String model) {
        if (model.contains("PCIE 5.0") && model.contains("2TB")) return 2200;
        if (model.contains("PCIE 5.0") && model.contains("1TB")) return 1050;
        if (model.contains("4TB")) return 3000;
        if (model.contains("2TB")) return 1350;
        if (model.contains("1TB")) return 700;
        return 900;
    }

    private int estimatedPsuPrice(String model) {
        if (model.contains("1000W")) return 900;
        if (model.contains("850W") && model.contains("ATX 3.1")) return 650;
        if (model.contains("750W") && model.contains("ATX 3.1")) return 520;
        if (model.contains("650W") && model.contains("ATX 3.1")) return 420;
        if (model.contains("850W")) return 580;
        if (model.contains("750W")) return 450;
        if (model.contains("650W") && model.contains("BRONZE")) return 280;
        if (model.contains("650W")) return 360;
        return 400;
    }

    private int estimatedCasePrice(String model) {
        if (model.contains("E-ATX")) return 700;
        if (model.contains("ITX")) return 450;
        if (model.contains("M-ATX")) return 300;
        return 350;
    }

    private int estimatedCoolerPrice(String model) {
        if (model.contains("360")) return 520;
        if (model.contains("280")) return 430;
        if (model.contains("240")) return 300;
        if (model.contains("DUAL TOWER")) return 190;
        if (model.contains("LOW PROFILE")) return 160;
        return 100;
    }

    private String productColor(String title) {
        String text = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (containsAnyText(text, "白色", "纯白", "雪", "white", "吹雪", "银白", "皓月", "冰川")) {
            return "WHITE";
        }
        if (containsAnyText(text, "黑色", "雅黑", "曜黑", "暗黑", "black", "碳黑", "夜黑", "黑武士", "黑化")) {
            return "BLACK";
        }
        return "";
    }

    private boolean containsAnyText(String text, String... keywords) {
        String source = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAvailableProduct(String title, Integer price, String link, String skuId) {
        return StringUtils.hasText(title)
                && price != null
                && price > 0
                && StringUtils.hasText(link)
                && StringUtils.hasText(skuId)
                && !isForbiddenProductTitle(title);
    }

    private boolean isForbiddenProductTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        String normalized = title.toUpperCase(Locale.ROOT);
        return containsAnyText(title, "二手", "拆机", "翻新", "准新", "95新", "99新", "成色", "矿卡", "库存机", "闲置")
                || normalized.contains("USED")
                || normalized.contains("REFURBISHED");
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
                .timeout(6_000)
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
                    && !title.contains("主机")
                    && !isNotebookComputerTitle(title);
            case MEMORY -> (title.contains("内存") || normalizedTitle.contains("DDR"))
                    && !isForbiddenMemoryTitle(title);
            case STORAGE -> title.contains("固态") || title.contains("硬盘") || normalizedTitle.contains("SSD") || normalizedTitle.contains("NVME");
            case PSU -> (title.contains("电源") || normalizedTitle.contains(" PSU"))
                    && !title.contains("电源线")
                    && !title.contains("适配器")
                    && !title.contains("充电器")
                    && !title.contains("服务器")
                    && !title.contains("笔记本");
            case CASE -> title.contains("机箱")
                    && !title.contains("机箱风扇")
                    && !title.contains("风扇套装");
            case COOLER -> (title.contains("散热器") || title.contains("风冷") || title.contains("水冷") || normalizedTitle.contains("CPU"))
                    && !title.contains("机箱风扇")
                    && !title.contains("风扇套装");
        };
    }

    private boolean isNotebookComputerTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        String normalized = title.toUpperCase(Locale.ROOT);
        return title.contains("笔记本")
                || title.contains("游戏本")
                || title.contains("全能本")
                || title.contains("轻薄本")
                || normalized.contains("THINKBOOK")
                || normalized.contains("THINKPAD")
                || normalized.contains("Y7000")
                || normalized.contains("Y9000");
    }

    private boolean matchesCoolerIntent(String title, HardwarePart part) {
        if (part == null || part.getType() != HardwarePartType.COOLER || !StringUtils.hasText(title)) {
            return true;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        String model = part.getModel().toUpperCase(Locale.ROOT);
        boolean fanOnly = title.contains("机箱风扇")
                || title.contains("风扇套装")
                || (title.contains("散热风扇") && !title.contains("CPU") && !title.contains("散热器"));
        if (fanOnly) {
            return false;
        }
        if (model.contains("360")) {
            return title.contains("水冷") && normalizedTitle.contains("360");
        }
        if (model.contains("280")) {
            return title.contains("水冷") && normalizedTitle.contains("280");
        }
        if (model.contains("240")) {
            return title.contains("水冷") && normalizedTitle.contains("240");
        }
        if (model.contains("DUAL TOWER")) {
            return !title.contains("水冷")
                    && (title.contains("双塔") || title.contains("风冷") || title.contains("散热器"));
        }
        if (model.contains("LOW PROFILE")) {
            return title.contains("下压") || title.contains("下吹") || normalizedTitle.contains("ITX");
        }
        return !title.contains("水冷")
                && (title.contains("塔式") || title.contains("风冷") || title.contains("散热器") || normalizedTitle.contains("CPU"));
    }

    private boolean matchesCaseIntent(String title, HardwarePart part) {
        if (part == null || part.getType() != HardwarePartType.CASE || !StringUtils.hasText(title)) {
            return true;
        }
        if (!title.contains("机箱") || title.contains("机箱风扇") || title.contains("风扇套装")) {
            return false;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        String compactTitle = compact(title);
        String text = partText(part);
        boolean targetEatx = text.contains("E-ATX") || compact(text).contains("EATX");
        boolean targetMatx = text.contains("M-ATX") || compact(text).contains("MATX");
        boolean targetItx = text.contains("ITX");
        boolean titleEatx = containsAnyCompact(title, "EATX") || title.contains("全塔") || title.contains("大塔") || title.contains("大机箱");
        boolean titleMatx = containsAnyCompact(title, "MATX") || title.contains("紧凑") || title.contains("小机箱") || title.contains("小型机箱");
        boolean titleItx = normalizedTitle.contains("ITX") || normalizedTitle.contains("MINI") || title.contains("迷你") || title.contains("小钢炮");
        if (targetEatx) {
            return titleEatx || title.contains("兼容E-ATX") || title.contains("支持E-ATX");
        }
        if (targetMatx) {
            return titleMatx || title.contains("兼容M-ATX") || title.contains("支持M-ATX");
        }
        if (targetItx) {
            return titleItx;
        }
        return (compactTitle.contains("ATX")
                || title.contains("中塔")
                || title.contains("海景房")
                || title.contains("风道")
                || title.contains("台式机机箱")
                || title.contains("电脑机箱")
                || title.contains("主机箱"))
                && !titleEatx
                && !titleMatx
                && !titleItx;
    }

    private boolean matchesMemoryIntent(String title, HardwarePart part) {
        if (part == null || part.getType() != HardwarePartType.MEMORY || !StringUtils.hasText(title)) {
            return true;
        }
        if (isForbiddenMemoryTitle(title)) {
            return false;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        String text = partText(part);
        boolean targetDdr4 = text.contains("DDR4");
        boolean targetDdr5 = text.contains("DDR5");
        if (targetDdr4 && !normalizedTitle.contains("DDR4")) {
            return false;
        }
        if (targetDdr5 && !normalizedTitle.contains("DDR5")) {
            return false;
        }
        boolean laptopMemory = title.contains("笔记本") || normalizedTitle.contains("SODIMM") || normalizedTitle.contains("SO-DIMM");
        boolean targetLaptop = text.contains("SODIMM") || text.contains("SO-DIMM") || text.contains("笔记本");
        if (laptopMemory && !targetLaptop) {
            return false;
        }
        int capacity = extractMemoryCapacityGb(text);
        if (capacity > 0 && !titleContainsMemoryCapacity(title, capacity)) {
            return false;
        }
        if (capacity > 0 && titleMentionsDifferentMemoryCapacity(title, capacity)) {
            return false;
        }
        if (capacity > 0 && !matchesMemoryPackageIntent(title, text, capacity)) {
            return false;
        }
        String speed = extractMemorySpeed(text);
        return !StringUtils.hasText(speed)
                || normalizedTitle.contains(speed)
                || titleContainsCompatibleMemorySpeed(title, speed);
    }

    private boolean matchesMemoryPackageIntent(String title, String targetText, int capacity) {
        boolean targetKit = targetText.contains("X2") || targetText.contains("套条");
        if (!targetKit || capacity <= 0) {
            return true;
        }
        int stickCapacity = capacity / 2;
        if (stickCapacity <= 0) {
            return true;
        }
        if (titleMentionsMemoryPair(title, stickCapacity)
                || containsAnyText(title, capacity + "GB套条", capacity + "G套条", capacity + "GB 套条", capacity + "G 套条")) {
            return true;
        }
        if (containsAnyText(title, "单条", "单根")
                || containsAnyCompact(title, stickCapacity + "GDDR", stickCapacity + "GBDDR")) {
            return false;
        }
        return false;
    }

    private boolean titleMentionsMemoryPair(String title, int stickCapacity) {
        return containsAnyCompact(
                title,
                stickCapacity + "GX2",
                stickCapacity + "GBX2",
                stickCapacity + "G2",
                stickCapacity + "GB2"
        ) || containsAnyText(title, stickCapacity + "G*2", stickCapacity + "GB*2", stickCapacity + "G×2", stickCapacity + "GB×2", "2条套条");
    }

    private boolean isForbiddenMemoryTitle(String title) {
        if (isForbiddenProductTitle(title)) {
            return true;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT).replace(" ", "");
        boolean ecc = normalizedTitle.contains("ECC")
                && !normalizedTitle.contains("NONECC")
                && !normalizedTitle.contains("NON-ECC");
        return ecc
                || normalizedTitle.contains("RDIMM")
                || normalizedTitle.contains("RECC")
                || title.contains("服务器内存")
                || title.contains("服务器专用")
                || title.contains("工作站内存")
                || title.contains("专用条");
    }

    private boolean titleContainsCompatibleMemorySpeed(String title, String targetSpeed) {
        int target = parseNumber(targetSpeed);
        if (target <= 0) {
            return true;
        }
        Matcher matcher = MEMORY_SPEED_PATTERN.matcher(title == null ? "" : title);
        while (matcher.find()) {
            int speed = parseNumber(matcher.group(1));
            if (speed > 0 && Math.abs(speed - target) <= 800) {
                return true;
            }
        }
        return false;
    }

    private int parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int firstMemorySpeed(String value) {
        Matcher matcher = MEMORY_SPEED_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            int speed = parseNumber(matcher.group(1));
            if (speed >= 3000 && speed <= 10000) {
                return speed;
            }
        }
        return 0;
    }

    private boolean matchesStorageIntent(String title, HardwarePart part) {
        if (part == null || part.getType() != HardwarePartType.STORAGE || !StringUtils.hasText(title)) {
            return true;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        String text = partText(part);
        boolean targetNvme = text.contains("NVME") || text.contains("M.2");
        if (targetNvme && !(normalizedTitle.contains("NVME") || normalizedTitle.contains("M.2") || compactTitleHas(title, "M2"))) {
            return false;
        }
        if (targetNvme && (title.contains("机械硬盘") || title.contains("移动硬盘") || normalizedTitle.contains("SATA"))) {
            return false;
        }
        String capacity = extractStorageCapacityToken(text);
        if (StringUtils.hasText(capacity) && !titleContainsStorageCapacity(title, capacity)) {
            return false;
        }
        boolean targetPcie5 = containsAnyCompact(text, "PCIE50", "PCIE5", "GEN5");
        boolean targetPcie4 = containsAnyCompact(text, "PCIE40", "PCIE4", "GEN4");
        if (targetPcie5) {
            return containsAnyCompact(title, "PCIE50", "PCIE5", "GEN5");
        }
        if (targetPcie4 && containsAnyCompact(title, "PCIE50", "PCIE5", "GEN5")) {
            return false;
        }
        return !targetPcie4 || containsAnyCompact(title, "PCIE40", "PCIE4", "GEN4");
    }

    private boolean matchesPsuIntent(String title, HardwarePart part) {
        if (part == null || part.getType() != HardwarePartType.PSU || !StringUtils.hasText(title)) {
            return true;
        }
        if (!matchesProductTypeTitle(title, HardwarePartType.PSU, false)) {
            return false;
        }
        String normalizedTitle = title.toUpperCase(Locale.ROOT);
        String text = partText(part);
        int wattage = extractWattage(part, text);
        if (wattage > 0 && !titleContainsWattage(title, wattage)) {
            return false;
        }
        boolean titleHasCert = title.contains("金牌")
                || title.contains("铜牌")
                || title.contains("白金")
                || title.contains("钛金")
                || normalizedTitle.contains("GOLD")
                || normalizedTitle.contains("BRONZE")
                || normalizedTitle.contains("PLATINUM")
                || normalizedTitle.contains("TITANIUM");
        int targetCert = psuCertificationRank(text);
        int titleCert = psuCertificationRank(title);
        if (targetCert > 0 && titleHasCert && titleCert < targetCert) {
            return false;
        }
        boolean targetAtx31 = containsAnyCompact(text, "ATX31", "PCIE51");
        boolean targetAtx30 = containsAnyCompact(text, "ATX30", "PCIE50");
        boolean titleHasAtxStandard = containsAnyCompact(title, "ATX31", "ATX30", "PCIE51", "PCIE50", "12V2X6", "12VHPWR");
        if (targetAtx31 && titleHasAtxStandard && !containsAnyCompact(title, "ATX31", "PCIE51", "12V2X6")) {
            return false;
        }
        return !targetAtx30 || !titleHasAtxStandard || containsAnyCompact(title, "ATX30", "PCIE50", "12VHPWR", "12V2X6");
    }

    private int psuCertificationRank(String value) {
        String text = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (text.contains("TITANIUM") || text.contains("钛金")) {
            return 5;
        }
        if (text.contains("PLATINUM") || text.contains("白金")) {
            return 4;
        }
        if (text.contains("GOLD") || text.contains("金牌")) {
            return 3;
        }
        if (text.contains("BRONZE") || text.contains("铜牌")) {
            return 1;
        }
        return 0;
    }

    private HardwarePart matchProductPart(String title, HardwarePartType type, List<HardwarePart> candidates) {
        if (usesIntentMatching(type)) {
            for (HardwarePart candidate : candidates) {
                if (matchesGenericIntent(title, candidate)) {
                    return candidate;
                }
            }
        }
        return modelMatcher.matchPart(title, type, candidates)
                .filter(part -> !usesIntentMatching(part.getType()) || matchesGenericIntent(title, part))
                .orElse(null);
    }

    private boolean matchesStandardModelForProduct(String title, HardwarePart part) {
        if (usesIntentMatching(part.getType())) {
            return matchesGenericIntent(title, part);
        }
        return modelMatcher.matchesPartModel(title, part);
    }

    private boolean usesIntentMatching(HardwarePartType type) {
        return type == HardwarePartType.MEMORY
                || type == HardwarePartType.STORAGE
                || type == HardwarePartType.PSU
                || type == HardwarePartType.CASE
                || type == HardwarePartType.COOLER;
    }

    private boolean matchesGenericIntent(String title, HardwarePart part) {
        return switch (part.getType()) {
            case MEMORY -> matchesMemoryIntent(title, part);
            case STORAGE -> matchesStorageIntent(title, part);
            case PSU -> matchesPsuIntent(title, part);
            case CASE -> matchesCaseIntent(title, part);
            case COOLER -> matchesCoolerIntent(title, part);
            default -> true;
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

    private String partText(HardwarePart part) {
        if (part == null) {
            return "";
        }
        return (part.getModel()
                + " " + part.getSpecs()
                + " " + part.getMemoryType()
                + " " + part.getFormFactor()
                + " " + part.getSupportedFormFactors()
                + " " + part.getWattage() + "W").toUpperCase(Locale.ROOT);
    }

    private String joinKeyword(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    private String extractPrimaryModelToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\b\\d{4,5}[A-Z0-9]*\\b", Pattern.CASE_INSENSITIVE).matcher(value);
        if (matcher.find()) {
            return matcher.group();
        }
        return value;
    }

    private String extractBoardChipset(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        Matcher matcher = BOARD_CHIPSET_PATTERN.matcher(value.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group() : "";
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
    }

    private boolean compactTitleHas(String title, String token) {
        return compact(title).contains(compact(token));
    }

    private boolean containsAnyCompact(String value, String... tokens) {
        String compactValue = compact(value);
        for (String token : tokens) {
            if (StringUtils.hasText(token) && compactValue.contains(compact(token))) {
                return true;
            }
        }
        return false;
    }

    private int extractWattage(HardwarePart part, String text) {
        if (part != null && part.getWattage() != null && part.getWattage() > 0) {
            return part.getWattage();
        }
        Matcher matcher = WATTAGE_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean titleContainsWattage(String title, int wattage) {
        return containsAnyCompact(title, wattage + "W") || title.contains(wattage + "瓦");
    }

    private int extractMemoryCapacityGb(String text) {
        Matcher matcher = MEMORY_CAPACITY_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String extractMemorySpeed(String text) {
        Matcher matcher = MEMORY_SPEED_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean titleContainsMemoryCapacity(String title, int capacityGb) {
        int selectedCapacity = selectedMemoryCapacityGb(title);
        if (selectedCapacity > 0) {
            return selectedCapacity == capacityGb;
        }
        String selectedVariant = selectedMemoryVariantText(title);
        if (StringUtils.hasText(selectedVariant) && MEMORY_CAPACITY_PATTERN.matcher(selectedVariant).find()) {
            return titleContainsMemoryCapacityToken(selectedVariant, capacityGb);
        }
        return titleContainsMemoryCapacityToken(title, capacityGb);
    }

    private boolean titleContainsMemoryCapacityToken(String title, int capacityGb) {
        String normalized = title.toUpperCase(Locale.ROOT)
                .replace("×", "X")
                .replace("*", "X")
                .replaceAll("\\s+", "");
        if (normalized.contains(capacityGb + "GB") || normalized.contains(capacityGb + "G")) {
            return true;
        }
        if (capacityGb % 2 != 0) {
            return false;
        }
        int half = capacityGb / 2;
        return normalized.contains(half + "GBX2")
                || normalized.contains(half + "GX2")
                || normalized.contains("2X" + half + "GB")
                || normalized.contains("2X" + half + "G")
                || normalized.contains(half + "GB套条")
                || normalized.contains(half + "G套条");
    }

    private String selectedMemoryVariantText(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        int bracket = Math.max(title.lastIndexOf('】'), title.lastIndexOf(']'));
        if (bracket >= 0 && bracket + 1 < title.length()) {
            return title.substring(bracket + 1);
        }
        return "";
    }

    private boolean titleMentionsDifferentMemoryCapacity(String title, int capacityGb) {
        int selectedCapacity = selectedMemoryCapacityGb(title);
        if (selectedCapacity > 0) {
            return selectedCapacity != capacityGb;
        }
        Matcher matcher = MEMORY_CAPACITY_PATTERN.matcher(title == null ? "" : title);
        while (matcher.find()) {
            int value = parseNumber(matcher.group(1));
            if (value > 0
                    && value != capacityGb
                    && value * 2 != capacityGb
                    && !isMemorySpeedValue(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMemorySpeedValue(int value) {
        return value == 3200 || value == 3600 || value == 4800 || value == 5200
                || value == 5600 || value == 6000 || value == 6200 || value == 6400
                || value == 6800 || value == 7200 || value == 7600 || value == 8000
                || value == 8400;
    }

    private int selectedMemoryCapacityGb(String title) {
        if (!StringUtils.hasText(title)) {
            return 0;
        }
        int selectedPairCapacity = 0;
        int selectedPairPosition = -1;
        Matcher pairMatcher = MEMORY_PAIR_PATTERN.matcher(title);
        while (pairMatcher.find()) {
            int stickCapacity = parseNumber(pairMatcher.group(1));
            if (stickCapacity > 0 && pairMatcher.start() >= selectedPairPosition) {
                selectedPairCapacity = stickCapacity * 2;
                selectedPairPosition = pairMatcher.start();
            }
        }
        Matcher reversePairMatcher = MEMORY_REVERSE_PAIR_PATTERN.matcher(title);
        while (reversePairMatcher.find()) {
            int stickCapacity = parseNumber(reversePairMatcher.group(1));
            if (stickCapacity > 0 && reversePairMatcher.start() >= selectedPairPosition) {
                selectedPairCapacity = stickCapacity * 2;
                selectedPairPosition = reversePairMatcher.start();
            }
        }
        if (selectedPairCapacity > 0) {
            return selectedPairCapacity;
        }
        int selectedSingleCapacity = 0;
        Matcher singleMatcher = MEMORY_CAPACITY_PATTERN.matcher(title);
        while (singleMatcher.find()) {
            int capacity = parseNumber(singleMatcher.group(1));
            if (capacity > 0) {
                selectedSingleCapacity = capacity;
            }
        }
        return selectedSingleCapacity;
    }

    private String extractStorageCapacityToken(String text) {
        Matcher matcher = STORAGE_CAPACITY_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String token = normalizeCapacityToken(matcher.group(1), matcher.group(2));
            if (StringUtils.hasText(token)) {
                return token;
            }
        }
        return "";
    }

    private boolean titleContainsStorageCapacity(String title, String expectedToken) {
        Matcher matcher = STORAGE_CAPACITY_PATTERN.matcher(title == null ? "" : title);
        while (matcher.find()) {
            if (expectedToken.equalsIgnoreCase(normalizeCapacityToken(matcher.group(1), matcher.group(2)))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCapacityToken(String number, String unit) {
        if (!StringUtils.hasText(number) || !StringUtils.hasText(unit)) {
            return "";
        }
        String cleanNumber = number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
        String normalizedUnit = unit.toUpperCase(Locale.ROOT).startsWith("T") ? "T" : "G";
        return cleanNumber + normalizedUnit;
    }

    private String displayStorageCapacity(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        if (token.endsWith("T")) {
            return token.substring(0, token.length() - 1) + "TB";
        }
        if (token.endsWith("G")) {
            return token.substring(0, token.length() - 1) + "GB";
        }
        return token;
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
