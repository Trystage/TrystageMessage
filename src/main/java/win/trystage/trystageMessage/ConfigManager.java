package win.trystage.trystageMessage;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
public class ConfigManager {

    // 配置数据
    private final Path configPath;
    private final Yaml yaml;
    private Map<String, Object> config;

    // ============ 构造器 ============
    public ConfigManager(Path dataDirectory) {
        this.configPath = dataDirectory.resolve("config.yml");
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);
        load();
    }

    // ============ 加载 / 保存 ============
    @SuppressWarnings("unchecked")
    private void load() {
        try {
            Files.createDirectories(configPath.getParent());
            if (!Files.exists(configPath)) {
                config = createDefault();
                save();
                System.out.println("Default config.yml created.");
                return;
            }

            try (Reader reader = Files.newBufferedReader(configPath)) {
                config = yaml.load(reader);
                if (config == null || config.isEmpty()) {
                    config = createDefault();
                    save();
                    System.out.println("Config file was empty, default created.");
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load config.yml" + e);
            config = createDefault();
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            yaml.dump(config, writer);
        } catch (IOException e) {
            System.out.println("Failed to save config.yml" + e);
        }
    }

    public void reload() {
        load();
        System.out.println("Config reloaded.");
    }

    // ============ 默认配置 ============
    private Map<String, Object> createDefault() {
        Map<String, Object> map = new LinkedHashMap<>();

        // 通用设置
        map.put("cooldown-seconds", 3);

        // ---- 广告词列表 ----
        map.put("advert-words", Arrays.asList("buy", "rank", "discount", "shop", "store", "purchase"));

        // ---- 广告拦截 ----
        map.put("advert-message",
                "&6&m-----------------------------------------\n" +
                        "&cAdvertising is against the rules. You will receive a punishment on the server if you attempt to advertise.\n" +
                        "&6&m-----------------------------------------");

        // ---- 重复消息 ----
        map.put("duplicate-message",
                "&6&m-----------------------------------------\n" +
                        "&cYou cannot say the same message twice!\n" +
                        "&6&m-----------------------------------------");

        // ---- 冷却 ----
        map.put("cooldown-message",
                "&6&m-----------------------------------------\n" +
                        "&cYou can only chat once every {sec} seconds! Ranked users bypass this restriction!\n" +
                        "&6&m-----------------------------------------");

        // ---- 敏感词（列表结构，每个条目含 type, words, reason） ----
        List<Map<String, Object>> sensitiveList = new ArrayList<>();

        // 条目1: 成人内容
        Map<String, Object> adult = new LinkedHashMap<>();
        adult.put("type", "adult");
        adult.put("words", Arrays.asList("hentai", "变态"));
        adult.put("reason", "it contains inappropriate content with adult themes.");
        sensitiveList.add(adult);

        // 条目2: 暴力/仇恨
        Map<String, Object> violence = new LinkedHashMap<>();
        violence.put("type", "violence");
        violence.put("words", Arrays.asList("nmsl", "cnm"));
        violence.put("reason", "it involves encouraging violence or hatred towards other players.");
        sensitiveList.add(violence);

        map.put("sensitive", sensitiveList);

        // ---- 敏感词通用消息（使用 {reason} 占位符） ----
        map.put("sensitive-message",
                "&6&m-----------------------------------------\n" +
                        "&cWe blocked your comment \"{message}\" because {reason}.\n" +
                        "&6&m-----------------------------------------");

        return map;
    }

    // ============ 基础 Getter ============
    public int getCooldownSeconds() {
        return (int) config.getOrDefault("cooldown-seconds", 3);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAdvertWords() {
        Object obj = config.get("advert-words");
        if (obj instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object o : (List<?>) obj) {
                if (o instanceof String) {
                    result.add((String) o);
                }
            }
            return result;
        }
        return Arrays.asList("buy", "rank", "discount", "shop", "store");
    }
    public String getAdvertMessageRaw() {
        return (String) config.getOrDefault("advert-message", "");
    }

    public String getDuplicateMessageRaw() {
        return (String) config.getOrDefault("duplicate-message", "");
    }

    public String getCooldownMessageRaw() {
        return (String) config.getOrDefault("cooldown-message", "");
    }

    public String getSensitiveMessageRaw() {
        return (String) config.getOrDefault("sensitive-message", "");
    }

    // ============ 带占位符替换的 Getter ============

    /**
     * 通用占位符替换：{player} {message} {type} {reason}
     */
    private String replace(String raw, String player, String message, String type, String reason) {
        if (raw == null) return "";
        String r = raw;
        if (player != null) r = r.replace("{player}", player);
        if (message != null) r = r.replace("{message}", message);
        if (type != null) r = r.replace("{type}", type);
        if (reason != null) r = r.replace("{reason}", reason);
        return r;
    }

    public String getAdvertMessage(String player, String message) {
        return replace(getAdvertMessageRaw(), player, message, null, null);
    }

    public String getDuplicateMessage(String player, String message) {
        return replace(getDuplicateMessageRaw(), player, message, null, null);
    }

    public String getCooldownMessage(String player, String message) {
        return replace(getCooldownMessageRaw(), player, message, null, null);
    }

    public String getSensitiveMessage(String player, String message, String type, String reason) {
        return replace(getSensitiveMessageRaw(), player, message, type, reason);
    }

    // ============ 敏感词条目（安全解析） ============

    /**
     * 敏感词条目的数据容器
     */
    public static class SensitiveEntry {
        public final String type;
        public final List<String> words;
        public final String reason;

        public SensitiveEntry(String type, List<String> words, String reason) {
            this.type = type;
            this.words = words;
            this.reason = reason;
        }
    }

    /**
     * 获取所有敏感词条目（安全遍历，无强制转换风险）
     */
    public List<SensitiveEntry> getSensitiveEntries() {
        List<SensitiveEntry> result = new ArrayList<>();
        Object obj = config.get("sensitive");
        if (!(obj instanceof List)) return result;

        List<?> list = (List<?>) obj;
        for (Object item : list) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) item;

            // type
            Object typeObj = map.get("type");
            if (!(typeObj instanceof String)) continue;
            String type = (String) typeObj;

            // words (必须为 List)
            Object wordsObj = map.get("words");
            List<String> words = new ArrayList<>();
            if (wordsObj instanceof List) {
                for (Object w : (List<?>) wordsObj) {
                    if (w instanceof String) {
                        words.add((String) w);
                    }
                }
            }
            if (words.isEmpty()) continue; // 无有效词则跳过

            // reason (可为空)
            Object reasonObj = map.get("reason");
            String reason = (reasonObj instanceof String) ? (String) reasonObj : "";

            result.add(new SensitiveEntry(type, words, reason));
        }
        return result;
    }
}