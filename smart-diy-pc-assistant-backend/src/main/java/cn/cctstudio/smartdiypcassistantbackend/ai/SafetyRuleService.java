package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.stereotype.Service;

@Service
public class SafetyRuleService {

    public SafetyResult check(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "短接启动", "魔改供电", "拆保险", "硬改电压", "刷串号", "改sn", "改 SN", "伪造保修", "洗白矿卡", "绕过售后", "骗保", "虚标参数", "假装全新")) {
            return new SafetyResult(
                    SafetyLevel.BLOCK,
                    "这个请求涉及危险改装、欺诈或规避售后，我不能提供相关操作方法。可以继续帮你做合规的装机、兼容性检查或二手硬件避坑。"
            );
        }
        if (containsAny(text, "电源型号不明", "额定不清", "额定多少也不清楚", "先凑合用", "老电源", "老电源继续顶", "杂牌电源", "来源不明", "二手电源", "二手配件来源不明", "bios版本不明", "bios版本不确定", "不刷bios", "直接点亮", "老主板配新u", "显卡长度未知", "机箱限高未知", "主板接口不明")) {
            return new SafetyResult(
                    SafetyLevel.URGENT,
                    "检测到高不确定性硬件风险：需要先确认电源型号/额定功率、BIOS 版本、机箱限高或显卡长度等关键信息，不能直接给出可购买结论。"
            );
        }
        if (containsAny(text, "极限超频", "降压拉满", "解锁功耗墙", "满血烤机", "预算严重失衡", "塞进小机箱", "随便装", "风冷顶着内存", "侧板盖不上", "矿卡", "二手显卡")) {
            return new SafetyResult(
                    SafetyLevel.WARN,
                    "检测到潜在硬件风险：后续建议会保守处理，并提醒你重点确认散热、电源、尺寸、BIOS 或二手来源。"
            );
        }
        return new SafetyResult(SafetyLevel.PASS, "");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
