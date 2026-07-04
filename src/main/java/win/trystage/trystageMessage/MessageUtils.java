package win.trystage.trystageMessage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息处理工具，负责检查玩家消息是否违规。
 * 包含冷却、重复、广告词、敏感词检测。
 * 内存管理由外部调用 clearPlayerData() 或 clearAll() 处理。
 */
public class MessageUtils {

    private final ConfigManager config;
    private final PermissionChecker permissionChecker;

    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessageContent = new ConcurrentHashMap<>();

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

        // 2. 重复消息检查
        String lastMsg = lastMessageContent.get(playerId);
        if (lastMsg != null && lastMsg.equalsIgnoreCase(message)) {
            return config.getDuplicateMessage(playerName, message);
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