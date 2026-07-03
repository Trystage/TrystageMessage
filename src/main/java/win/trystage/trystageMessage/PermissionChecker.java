package win.trystage.trystageMessage;

import java.util.UUID;

@FunctionalInterface
public interface PermissionChecker {
    boolean hasPermission(UUID playerId, String permission);
}