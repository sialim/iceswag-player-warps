package net.serverplugins.warps;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

record PlayerWarp(String name, UUID owner, String ownerName, UUID world, double x, double y, double z,
                  float yaw, float pitch) {
    PlayerWarp {
        if (name == null || owner == null || ownerName == null || world == null
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Invalid warp data.");
        }
    }

    static PlayerWarp at(String name, Player player) {
        Location location = player.getLocation();
        return new PlayerWarp(name, player.getUniqueId(), player.getName(), location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    PlayerWarp renamed(String replacement) {
        return new PlayerWarp(replacement, owner, ownerName, world, x, y, z, yaw, pitch);
    }

    Location location(Server server) {
        World destination = server.getWorld(world);
        if (destination == null) {
            throw new WarpProblem("world-unavailable", "warp", name);
        }
        return new Location(destination, x, y, z, yaw, pitch);
    }
}
