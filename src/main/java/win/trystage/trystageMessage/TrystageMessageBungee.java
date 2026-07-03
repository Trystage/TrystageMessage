package win.trystage.trystageMessage;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.event.EventHandler;

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
        messageUtils = new MessageUtils(configManager);

        // 注册指令
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand());

        // 注册事件监听
        getProxy().getPluginManager().registerListener(this, this);

        getLogger().info("TrystageMessageVelocity enabled.");
    }

    @Override
    public void onDisable() {
        if (messageUtils != null) {
            messageUtils.clearAll();
        }
        getLogger().info("TrystageMessageVelocity disabled.");
    }

    @EventHandler
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