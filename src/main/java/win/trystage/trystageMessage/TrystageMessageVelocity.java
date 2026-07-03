package win.trystage.trystageMessage;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

@Plugin(
        id = "trystagemessage",
        name = "TrystageMessageVelocity",
        version = "1.0-SNAPSHOT",
        description = "Spamming, advertising blocker",
        url = "www.trystage.win",
        authors = {"TrystageBedwars"}
)
public class TrystageMessageVelocity {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer proxyServer;

    @Inject
    @DataDirectory
    private Path dataDirectory;

    private ConfigManager configManager;
    private MessageUtils messageUtils;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 初始化配置管理器
        configManager = new ConfigManager(dataDirectory);
        logger.info("Config loaded.");

        // 初始化消息工具
        PermissionChecker checker = (uuid, perm) -> {
            Optional<Player> opt = proxyServer.getPlayer(uuid);
            return opt.map(p -> p.hasPermission(perm)).orElse(false);
        };
        messageUtils = new MessageUtils(configManager, checker);
        logger.info("messageUtils initialized.");

        // 注册重载指令
        CommandManager commandManager = proxyServer.getCommandManager();
        commandManager.register(
                commandManager.metaBuilder("tmsg").build(),
                (SimpleCommand) invocation -> {
                    CommandSource source = invocation.source();
                    if (!source.hasPermission("tmsg.reload")) {
                        source.sendMessage(Component.text("§cYou don't have permission to reload."));
                        return;
                    }
                    configManager.reload();
                    String msg = "&aSuccessfully reloaded config";
                    source.sendMessage(Component.text(msg.replace('&', '§')));
                }
        );
        logger.info("Registered /tmsg reload command.");

        logger.info("TrystageMessageVelocity plugin enabled.");
    }

    /**
     * 监听玩家发送的聊天消息（非命令）
     */
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        String playerName = event.getPlayer().getUsername();
        String message = event.getMessage();

        // 执行消息检查
        String result = messageUtils.checkMessage(
                event.getPlayer().getUniqueId(),
                playerName,
                message
        );

        if (result != null) {
            // 违规：取消消息并发送提示
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(Component.text(result.replace('&', '§')));
            logger.info("Blocked message from {}: {}", playerName, message);
        }
    }

    /**
     * 玩家断开时清理缓存，防止内存泄漏
     */
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        if (messageUtils != null) {
            messageUtils.clearPlayerData(event.getPlayer().getUniqueId());
        }
    }
}