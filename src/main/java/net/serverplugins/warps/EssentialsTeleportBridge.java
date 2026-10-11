package net.serverplugins.warps;

import com.earth2me.essentials.AsyncTeleport;
import com.earth2me.essentials.User;
import java.util.concurrent.CompletableFuture;
import net.ess3.api.IEssentials;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;

final class EssentialsTeleportBridge implements WarpTeleporter {
    private final Server server;

    EssentialsTeleportBridge(Server server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Player player, Location destination) {
        Plugin plugin = server.getPluginManager().getPlugin("Essentials");
        if (!(plugin instanceof IEssentials essentials) || !plugin.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Essentials teleportation is unavailable."));
        }
        return teleport(essentials, player, destination);
    }

    static CompletableFuture<Boolean> teleport(IEssentials essentials, Player player, Location destination) {
        User user = essentials.getUser(player);
        if (user == null || user.isJailed() || user.isFreeze()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Teleportation is currently restricted."));
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        AsyncTeleport teleport = user.getAsyncTeleport();
        teleport.setTpType(AsyncTeleport.TeleportType.NORMAL);
        teleport.teleport(destination.clone(), null, TeleportCause.COMMAND, future);
        return future;
    }
}
