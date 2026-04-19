package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IntentRuleService {

    public Optional<IntentMatch> match(String message) {
        String text = normalize(message);
        if (text.length() > 18) {
            return Optional.empty();
        }
        if (matches(text, List.of("你好", "您好", "hi", "hello", "哈喽", "在吗"))) {
            return Optional.of(new IntentMatch(
                    "chitchat",
                    "你好，我是智能 DIY 装机助手。你可以直接告诉我预算、用途、屏幕分辨率和外观偏好，我来帮你整理配置思路。"
            ));
        }
        if (matches(text, List.of("谢谢", "好的", "行", "明白了", "收到", "ok"))) {
            return Optional.of(new IntentMatch(
                    "chitchat",
                    "收到。如果还要继续调整，可以直接告诉我预算、用途、显卡偏好或想优化的配件。"
            ));
        }
        if (matches(text, List.of("可以", "就这样", "行吧", "那就这个", "确定", "用这套"))) {
            return Optional.of(new IntentMatch(
                    "preference_record",
                    "我已理解你想采用当前方案。后续会把配置单保存和收藏能力接到这里，现在可以继续告诉我要不要调整配件或预算。"
            ));
        }
        if (matches(text, List.of("再见", "拜拜", "先这样", "结束", "没了", "下次聊"))) {
            return Optional.of(new IntentMatch(
                    "chitchat",
                    "好的，这次先到这里。下次需要装机、升级或检查兼容性时，直接来问我就行。"
            ));
        }
        if (containsAny(text, List.of("上次那套", "之前那套", "历史方案", "我的收藏", "旧配置", "上回那个", "上回那台"))) {
            return Optional.of(new IntentMatch(
                    "history_recall",
                    "我会优先从左侧对话记录里帮你找之前的方案。你也可以直接点开某条历史对话继续调整。"
            ));
        }
        if (containsAny(text, List.of("兼容吗", "能装吗", "能直接买吗", "有问题吗", "会冲突吗", "能点亮吗"))) {
            return Optional.of(new IntentMatch(
                    "compatibility_check",
                    "这是兼容性检查请求。请把完整配置发给我，我会重点看主板接口、显卡长度、电源功率、散热限高和 BIOS 风险。"
            ));
        }
        if (containsAny(text, List.of("重新配", "再来一套", "换个方案", "重配", "重来", "再推荐一次"))) {
            return Optional.of(new IntentMatch(
                    "recommendation_retry",
                    "可以重新推荐。请补充预算、主要用途、是否包含显示器，以及有没有外观或品牌偏好。"
            ));
        }
        return Optional.empty();
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase();
    }

    private boolean matches(String text, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> text.equals(keyword.toLowerCase()));
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> text.contains(keyword.toLowerCase()));
    }

    public record IntentMatch(String intent, String reply) {
    }
}
