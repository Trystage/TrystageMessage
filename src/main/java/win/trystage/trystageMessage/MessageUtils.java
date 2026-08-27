package win.trystage.trystageMessage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息处理工具，负责检查玩家消息是否违规。
 * 包含冷却、重复（含相似度）、广告词、敏感词检测。
 * 内存管理由外部调用 clearPlayerData() 或 clearAll() 处理。
 */
public class MessageUtils {

    private final ConfigManager config;
    private final PermissionChecker permissionChecker;

    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessageContent = new ConcurrentHashMap<>();

    // 重复检测窗口（毫秒）
    private static final long REPEAT_WINDOW_MS = 60 * 1000L;

    // 相似度阈值：最大长度 -> 最低相似度
    // 5 字 -> 0.8，10 字 -> 0.5，线性插值，两端限幅
    private static final double THRESHOLD_SLOPE = -0.06;     // 每增加1字，阈值下降0.06
    private static final double THRESHOLD_INTERCEPT = 1.1;   // 阈值 = 1.1 - 0.06 * len
    private static final double THRESHOLD_MIN = 0.3;
    private static final double THRESHOLD_MAX = 0.8;

    public MessageUtils(ConfigManager config, PermissionChecker permissionChecker) {
        this.config = config;
        this.permissionChecker = permissionChecker;
    }

    public String checkMessage(UUID playerId, String playerName, String message) {
        // 1. 冷却检查（如果有 bypass 权限则跳过）
        int cooldown = config.getCooldownSeconds();
        if (cooldown > 0 && !permissionChecker.hasPermission(playerId, "tmsg.bypass.cooldown")) {
            Long lastTime = lastMessageTime.get(playerId);
            if (lastTime != null) {
                long now = System.currentTimeMillis();
                if (now - lastTime < cooldown * 1000L) {
                    return config.getCooldownMessage(playerName, message);
                }
            }
        }

        // 2. 重复消息检查（包括相似度）
        Long lastTime = lastMessageTime.get(playerId);
        String lastMsg = lastMessageContent.get(playerId);
        if (lastMsg != null && lastTime != null) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastTime;
            if (elapsed < REPEAT_WINDOW_MS) {
                // 计算相似度
                double similarity = calculateSimilarity(message, lastMsg);
                // 动态阈值
                int maxLen = Math.max(message.length(), lastMsg.length());
                double threshold = THRESHOLD_INTERCEPT + THRESHOLD_SLOPE * maxLen;
                // 限制范围
                if (threshold > THRESHOLD_MAX) threshold = THRESHOLD_MAX;
                if (threshold < THRESHOLD_MIN) threshold = THRESHOLD_MIN;

                if (similarity >= threshold) {
                    return config.getDuplicateMessage(playerName, message);
                }
            }
        }

        // 3. 广告词检查
        // ---- 长度限制（反广告/刷屏） ----
        int length = message.length();
        if (length > 200) {
            return config.getAdvertMessage(playerName, message);
        }
        if (length > 50) {
            int cjkCount = 0;   // 中日韩字符
            int wideCount = 0;  // 其他非ASCII字符（俄语、法语、西语等）
            for (char c : message.toCharArray()) {
                if (c >= 0x4E00 && c <= 0x9FA5) {
                    cjkCount++;
                } else if (c > 255) {
                    wideCount++;
                }
            }
            // CJK超过50 或 其他宽字符超过150 则拦截
            if (cjkCount > 50 || wideCount > 150) {
                return config.getAdvertMessage(playerName, message);
            }
        }

        for (String word : config.getAdvertWords()) {
            if (message.toLowerCase().contains(word.toLowerCase())) {
                return config.getAdvertMessage(playerName, message);
            }
        }

        // 4. 敏感词检查（遍历所有类型）
        for (ConfigManager.SensitiveEntry entry : config.getSensitiveEntries()) {
            for (String word : entry.words) {
                if (message.toLowerCase().contains(word.toLowerCase())) {
                    return config.getSensitiveMessage(playerName, message, entry.type, entry.reason);
                }
            }
        }

        // 全部通过 → 更新记录
        lastMessageTime.put(playerId, System.currentTimeMillis());
        lastMessageContent.put(playerId, message);
        return null;
    }

    /**
     * 计算两个字符串的相似度（基于编辑距离归一化）
     * @return 0~1 之间的值，1 表示完全相同
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null && s2 == null) return 1.0;
        if (s1 == null || s2 == null) return 0.0;
        if (s1.isEmpty() && s2.isEmpty()) return 1.0;
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        return (maxLen == 0) ? 1.0 : (1.0 - (double) distance / maxLen);
    }

    /**
     * 计算 Levenshtein 编辑距离（优化版，使用滚动数组）
     */
    private int levenshteinDistance(String a, String b) {
        int m = a.length();
        int n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;

        // 只保留两行
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            // 交换
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[n];
    }

    /**
     * 移除玩家的缓存数据（玩家退出时调用，防止内存泄漏）
     */
    public void clearPlayerData(UUID playerId) {
        lastMessageTime.remove(playerId);
        lastMessageContent.remove(playerId);
    }

    /**
     * 清除所有缓存（插件卸载或重载时调用）
     */
    public void clearAll() {
        lastMessageTime.clear();
        lastMessageContent.clear();
    }

    // 可选：获取当前缓存大小（调试用）
    public int getCacheSize() {
        return lastMessageTime.size();
    }
}