package cn.cctstudio.smartdiypcassistantbackend.ai;

import java.util.Locale;

public enum AiToolAction {
    DIRECT_ANSWER,
    GENERATE_BUILD,
    MODIFY_BUILD,
    REVIEW_BUILD,
    SUMMARIZE_NEWS,
    ASK_CLARIFICATION;

    public static AiToolAction from(String value) {
        if (value == null) {
            return DIRECT_ANSWER;
        }
        try {
            return AiToolAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DIRECT_ANSWER;
        }
    }

    public boolean startsBuildTask() {
        return this == GENERATE_BUILD || this == MODIFY_BUILD;
    }
}
