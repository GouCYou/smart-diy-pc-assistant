package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MemoryExtractor {

    private static final List<String> BRANDS = List.of(
            "华硕", "ROG", "微星", "技嘉", "七彩虹", "影驰", "索泰", "蓝宝石", "迪兰",
            "铭瑄", "映众", "华擎", "利民", "九州风神", "海盗船", "金士顿", "芝奇",
            "长城", "航嘉", "振华", "海韵", "酷冷至尊", "追风者", "联力", "乔思伯",
            "先马", "爱国者", "西数", "三星", "铠侠", "致态"
    );

    private static final List<String> POSITIVE_BRAND_HINTS = List.of(
            "喜欢", "偏好", "偏爱", "优先", "倾向", "想要", "认准", "只要", "信任"
    );

    private static final List<String> NEGATIVE_BRAND_HINTS = List.of(
            "不要", "别用", "不想", "排斥", "避开", "不考虑", "不买", "讨厌", "拉黑", "拒绝"
    );

    public UserMemoryPatch extract(String message) {
        String text = normalize(message);
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return new UserMemoryPatch("", "", "", "", "", "", "", "", "");
        }
        BrandMemory brandMemory = extractBrands(text);
        return new UserMemoryPatch(
                extractBudget(text, lowerText),
                extractUsage(text, lowerText),
                extractCpuPlatform(text, lowerText),
                extractGpuPlatform(text, lowerText),
                String.join("、", brandMemory.preferred()),
                String.join("、", brandMemory.rejected()),
                extractAppearance(text, lowerText),
                extractNoise(text, lowerText),
                extractUpgradePlan(text)
        );
    }

    private String extractBudget(String text, String lowerText) {
        if (containsAny(text, "预算无限", "预算无上限", "不差钱", "预算不封顶")) {
            return "预算充足，不设固定上限";
        }
        Integer budget = explicitBudget(text);
        List<String> notes = new ArrayList<>();
        if (budget != null) {
            notes.add("常见预算约 ¥" + budget);
        }
        if (containsAny(text, "性价比", "省钱", "能省则省", "别太贵")) {
            notes.add("重视性价比");
        }
        if (containsAny(lowerText, "一步到位", "拉满", "顶配")) {
            notes.add("愿意为一步到位加预算");
        }
        return String.join("；", notes);
    }

    private String extractUsage(String text, String lowerText) {
        List<String> usages = new ArrayList<>();
        if (containsAny(text, "游戏", "打游戏", "3A", "电竞", "网游", "2K", "4K", "高刷")) {
            usages.add("游戏");
        }
        if (containsAny(lowerText, "生产力", "剪辑", "渲染", "建模", "设计", "cad", "cuda", "ai", "深度学习", "视频", "直播")) {
            usages.add("生产力/创作");
        }
        if (containsAny(text, "办公", "学习", "上网", "影音", "看视频", "看电影")) {
            usages.add("办公/影音");
        }
        if (usages.isEmpty()) {
            return "";
        }
        return "主要用于" + String.join("、", usages);
    }

    private String extractCpuPlatform(String text, String lowerText) {
        if (containsAny(lowerText, "不要 amd cpu", "不要amd处理器", "不考虑amd cpu", "不想用amd cpu")) {
            return "不考虑 AMD CPU";
        }
        if (containsAny(text, "不要英特尔", "不要Intel", "不考虑英特尔", "不考虑Intel")) {
            return "不考虑 Intel CPU";
        }
        if (containsAny(lowerText, "x3d")) {
            return "偏 AMD 游戏 CPU 平台";
        }
        if (containsAny(lowerText, "amd cpu", "amd处理器", "锐龙", "ryzen")) {
            return "偏 AMD CPU 平台";
        }
        if (containsAny(lowerText, "intel cpu", "intel处理器", "英特尔", "酷睿", "core ")) {
            return "偏 Intel CPU 平台";
        }
        return "";
    }

    private String extractGpuPlatform(String text, String lowerText) {
        if (containsAny(lowerText, "不要 n 卡", "不要n卡", "不要英伟达", "不考虑英伟达", "不想用英伟达")) {
            return "不考虑 NVIDIA GPU";
        }
        if (containsAny(lowerText, "不要 a 卡", "不要a卡", "不要amd显卡", "不考虑amd显卡")) {
            return "不考虑 AMD GPU";
        }
        if (containsAny(lowerText, "nvidia", "英伟达", "n卡", "rtx", "cuda", "nvenc")) {
            return containsAny(lowerText, "cuda", "ai", "深度学习", "生产力")
                    ? "偏 NVIDIA GPU，重视 CUDA/生产力生态"
                    : "偏 NVIDIA GPU";
        }
        if (containsAny(lowerText, "amd显卡", "a卡", "radeon", "rx ")) {
            return "偏 AMD/Radeon GPU";
        }
        if (containsAny(lowerText, "intel显卡", "arc显卡", "intel arc")) {
            return "可接受 Intel Arc GPU";
        }
        return "";
    }

    private BrandMemory extractBrands(String text) {
        LinkedHashSet<String> preferred = new LinkedHashSet<>();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        for (String brand : BRANDS) {
            int index = indexOfIgnoreCase(text, brand);
            while (index >= 0) {
                String window = surroundingText(text, index, brand.length(), 10);
                if (containsAny(window, NEGATIVE_BRAND_HINTS)) {
                    rejected.add(brand);
                } else if (containsAny(window, POSITIVE_BRAND_HINTS)) {
                    preferred.add(brand);
                }
                index = indexOfIgnoreCase(text, brand, index + brand.length());
            }
        }
        preferred.removeAll(rejected);
        return new BrandMemory(preferred, rejected);
    }

    private String extractAppearance(String text, String lowerText) {
        List<String> notes = new ArrayList<>();
        if (containsAny(lowerText, "不要rgb", "不喜欢rgb", "无光", "少灯", "别带灯")) {
            notes.add("偏无光/少灯");
        } else if (containsAny(lowerText, "rgb", "灯效", "光污染")) {
            notes.add("喜欢 RGB 灯效");
        }
        if (containsAny(text, "白色", "纯白", "海景房", "白色海景房") || lowerText.contains("white")) {
            notes.add("偏白色/海景房外观");
        } else if (containsAny(text, "黑色", "全黑") || lowerText.contains("black")) {
            notes.add("偏黑色外观");
        }
        if (containsAny(lowerText, "itx", "小机箱", "小体积", "小钢炮")) {
            notes.add("偏小体积");
        }
        if (containsAny(text, "简约", "低调", "朴素")) {
            notes.add("偏简约低调");
        }
        return String.join("；", notes);
    }

    private String extractNoise(String text, String lowerText) {
        if (containsAny(text, "静音", "安静", "低噪", "噪音小", "不要吵")) {
            return "偏静音低噪";
        }
        if (containsAny(lowerText, "噪音无所谓", "不在乎噪音", "能接受吵", "吵一点也行")) {
            return "可接受一定噪音";
        }
        return "";
    }

    private String extractUpgradePlan(String text) {
        if (!containsAny(text, "以后", "后续", "将来", "未来", "过两年", "明年", "升级", "扩展")) {
            return "";
        }
        List<String> targets = new ArrayList<>();
        if (containsAny(text, "显卡", "GPU")) {
            targets.add("显卡");
        }
        if (containsAny(text, "CPU", "处理器")) {
            targets.add("CPU");
        }
        if (containsAny(text, "内存")) {
            targets.add("内存");
        }
        if (containsAny(text, "硬盘", "固态", "SSD", "存储")) {
            targets.add("存储");
        }
        if (targets.isEmpty()) {
            return containsAny(text, "升级", "扩展") ? "计划后续升级/扩展" : "";
        }
        return "计划后续升级/扩展" + String.join("、", targets);
    }

    private String normalize(String message) {
        String text = message == null ? "" : message.trim();
        return text.replaceAll("https?://\\S+", "")
                .replaceAll("(?i)sku\\s*[:：]?\\s*[A-Za-z0-9_-]+", "")
                .replaceAll("京东链接\\s*[:：]?\\s*\\S+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Integer explicitBudget(String text) {
        Matcher numberMatcher = Pattern.compile("(\\d{4,5})\\s*(元|块|预算)?").matcher(text);
        if (numberMatcher.find()) {
            return Integer.parseInt(numberMatcher.group(1));
        }
        Matcher kiloMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[kK]").matcher(text);
        if (kiloMatcher.find()) {
            return (int) Math.round(Double.parseDouble(kiloMatcher.group(1)) * 1000);
        }
        if (containsAny(text, "一万五", "1万5", "1.5万")) {
            return 15000;
        }
        if (containsAny(text, "一万", "1万")) {
            return 10000;
        }
        if (containsAny(text, "两万", "二万", "2万")) {
            return 20000;
        }
        if (containsAny(text, "八千")) {
            return 8000;
        }
        if (containsAny(text, "六千")) {
            return 6000;
        }
        if (containsAny(text, "五千")) {
            return 5000;
        }
        Matcher decimalWan = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*万").matcher(text);
        if (decimalWan.find()) {
            return (int) Math.round(Double.parseDouble(decimalWan.group(1)) * 10000);
        }
        Matcher wanQian = Pattern.compile("(\\d)万(\\d)千?").matcher(text);
        if (wanQian.find()) {
            return Integer.parseInt(wanQian.group(1)) * 10000 + Integer.parseInt(wanQian.group(2)) * 1000;
        }
        return null;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int indexOfIgnoreCase(String text, String keyword) {
        return indexOfIgnoreCase(text, keyword, 0);
    }

    private int indexOfIgnoreCase(String text, String keyword, int fromIndex) {
        return text.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT), fromIndex);
    }

    private String surroundingText(String text, int index, int length, int radius) {
        int start = Math.max(0, index - radius);
        int end = Math.min(text.length(), index + length + radius);
        return text.substring(start, end);
    }

    private record BrandMemory(Set<String> preferred, Set<String> rejected) {
    }
}
