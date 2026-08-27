package win.trystage.trystageMessage;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;

public class TrystageMessageBukkit extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private MessageUtils messageUtils;

    @Override
    public void onEnable() {
        Path dataDir = getDataFolder().toPath();
        configManager = new ConfigManager(dataDir);
        PermissionChecker checker = (uuid, perm) -> {
            Player player = Bukkit.getPlayer(uuid);
            return player != null && player.hasPermission(perm);
        };
        messageUtils = new MessageUtils(configManager, checker);

        // 注册指令
        getCommand("tmsg").setExecutor(this::onCommand);

        // 注册事件
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("TrystageMessage enabled.");
    }

    @Override
    public void onDisable() {
        if (messageUtils != null) {
            messageUtils.clearAll();
        }
        getLogger().info("TrystageMessage disabled.");
    }

    // 指令处理
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tmsg.reload")) {
            sender.sendMessage("§cYou don't have permission to reload.");
            return true;
        }
        configManager.reload();
        String msg = "&aSuccessfully reloaded config";
        sender.sendMessage(msg.replace('&', '§'));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String message = event.getMessage();
        UUID playerId = player.getUniqueId();

        String result = messageUtils.checkMessage(playerId, playerName, message);
        if (result != null) {
            event.setCancelled(true);
            player.sendMessage(result.replace('&', '§'));
            getLogger().info("Blocked message from " + playerName + ": " + message);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String fullCommand = event.getMessage(); // 完整命令（含 /）

        String result = messageUtils.checkMessage(player.getUniqueId(), player.getName(), fullCommand);
        if (result != null) {
            event.setCancelled(true);
            player.sendMessage(Component.text(result.replace('&', '§')).content());
        }
    }

    // 玩家退出清理
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (messageUtils != null) {
            messageUtils.clearPlayerData(event.getPlayer().getUniqueId());
        }
    }
}