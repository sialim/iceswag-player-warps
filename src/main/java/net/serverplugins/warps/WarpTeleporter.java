package net.serverplugins.warps;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

interface WarpTeleporter {
    CompletableFuture<Boolean> teleport(Player player, Location destination);
}
