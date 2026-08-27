package win.trystage.trystageMessage;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.UUID;

public class TrystageMessageBungee extends Plugin implements Listener {

    private ConfigManager configManager;
    private MessageUtils messageUtils;

    @Override
    public void onEnable() {
        // 配置文件路径（Bungee的data文件夹）
        Path dataDir = getDataFolder().toPath();
        configManager = new ConfigManager(dataDir);
        PermissionChecker checker = (uuid, perm) -> {
            ProxiedPlayer player = getProxy().getPlayer(uuid);
            return player != null && player.hasPermission(perm);
        };
        messageUtils = new MessageUtils(configManager, checker);

        // 注册指令
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand());

        // 注册事件监听
        getProxy().getPluginManager().registerListener(this, this);

        getLogger().info("TrystageMessage enabled.");
    }

    @Override
    public void onDisable() {
        if (messageUtils != null) {
            messageUtils.clearAll();
        }
        getLogger().info("TrystageMessage disabled.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(ChatEvent event) {
        if (event.isCommand()) return; // 只处理聊天消息
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String playerName = player.getDisplayName();
        String message = event.getMessage();
        UUID playerId = player.getUniqueId();

        String result = messageUtils.checkMessage(playerId, playerName, message);
        if (result != null) {
            event.setCancelled(true);
            player.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(result.replace('&', '§')));
            getLogger().info("Blocked message from " + playerName + ": " + message);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (messageUtils != null) {
            messageUtils.clearPlayerData(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(ChatEvent event) {
        // 只处理玩家发送的消息（不处理控制台等）
        if (!(event.getSender() instanceof Player)) return;

        // 判断是否为命令（以 / 开头）
        if (!event.isCommand()) return;

        Player player = (Player) event.getSender();
        String fullCommand = event.getMessage(); // 完整命令，如 "/msg Trystage4C01 hello"

        // 调用你的检查逻辑
        String result = messageUtils.checkMessage(player.getUniqueId(), player.getName(), fullCommand);

        if (result != null) {
            event.setCancelled(true); // 取消命令执行
            player.sendMessage(Component.text(result.replace('&', '§')).content());
        }
    }

    // 内部指令类
    private class ReloadCommand extends Command {
        ReloadCommand() {
            super("tmsg");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission("tmsg.reload")) {
                sender.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§cYou don't have permission to reload."));
                return;
            }
            configManager.reload();
            String msg = "&aSuccessfully reloaded config";
            sender.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg.replace('&', '§')));
        }
    }
}