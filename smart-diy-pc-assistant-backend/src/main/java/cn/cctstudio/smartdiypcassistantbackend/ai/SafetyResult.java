package cn.cctstudio.smartdiypcassistantbackend.ai;

public record SafetyResult(SafetyLevel level, String message) {

    public boolean blocked() {
        return level == SafetyLevel.BLOCK;
    }
}
