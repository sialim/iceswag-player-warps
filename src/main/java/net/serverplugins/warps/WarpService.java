package net.serverplugins.warps;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

final class WarpService {
    private final WarpStore store;
    private WarpSettings settings;

    WarpService(WarpStore store, WarpSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    WarpSettings settings() {
        return settings;
    }

    void reload(WarpSettings replacement) {
        settings = replacement;
    }

    int limit(Player player) {
        return player.hasPermission("playerwarps.unlimited") ? -1 : settings.rank(player::hasPermission).limit();
    }

    long owned(Player player) {
        return store.all().values().stream().filter(warp -> warp.owner().equals(player.getUniqueId())).count();
    }

    void create(Player player, String input) throws IOException {
        require(player, "playerwarps.create");
        String name = settings.validateName(input);
        Map<String, PlayerWarp> warps = new HashMap<>(store.all());
        if (warps.containsKey(name)) {
            throw new WarpProblem("name-taken", "warp", name);
        }
        int limit = limit(player);
        if (limit >= 0 && owned(player) >= limit) {
            throw new WarpProblem("limit-reached", "limit", limit);
        }
        ensureDestination(player.getLocation());
        warps.put(name, PlayerWarp.at(name, player));
        store.replace(warps);
    }

    void delete(Player player, String input) throws IOException {
        PlayerWarp warp = ownedWarp(player, input);
        delete(warp.name());
    }

    void delete(String input) throws IOException {
        String name = find(input).name();
        Map<String, PlayerWarp> warps = new HashMap<>(store.all());
        warps.remove(name);
        store.replace(warps);
    }

    void move(Player player, String input) throws IOException {
        PlayerWarp warp = ownedWarp(player, input);
        ensureDestination(player.getLocation());
        Map<String, PlayerWarp> warps = new HashMap<>(store.all());
        warps.put(warp.name(), PlayerWarp.at(warp.name(), player));
        store.replace(warps);
    }

    void rename(Player player, String input, String replacement) throws IOException {
        PlayerWarp warp = ownedWarp(player, input);
        String name = settings.validateName(replacement);
        Map<String, PlayerWarp> warps = new HashMap<>(store.all());
        if (warps.containsKey(name)) {
            throw new WarpProblem("name-taken", "warp", name);
        }
        warps.remove(warp.name());
        warps.put(name, warp.renamed(name));
        store.replace(warps);
    }

    Location destination(Player player, String input, Server server) {
        require(player, "playerwarps.use");
        PlayerWarp warp = find(input);
        if (!visible(player, warp)) {
            throw new WarpProblem("private-warp", "warp", warp.name());
        }
        Location destination = warp.location(server);
        ensureDestination(destination);
        return destination;
    }

    List<PlayerWarp> list(Player player, boolean ownOnly) {
        return store.all().values().stream()
                .filter(warp -> ownOnly ? warp.owner().equals(player.getUniqueId()) : visible(player, warp))
                .sorted(Comparator.comparing(PlayerWarp::name)).toList();
    }

    int size() {
        return store.all().size();
    }

    private PlayerWarp find(String input) {
        PlayerWarp warp = store.all().get(WarpSettings.normalize(input));
        if (warp == null) {
            throw new WarpProblem("warp-not-found", "warp", WarpSettings.normalize(input));
        }
        return warp;
    }

    private PlayerWarp ownedWarp(Player player, String input) {
        require(player, "playerwarps.manage");
        PlayerWarp warp = find(input);
        if (!warp.owner().equals(player.getUniqueId())) {
            throw new WarpProblem("not-owner", "warp", warp.name());
        }
        return warp;
    }

    private boolean visible(Player player, PlayerWarp warp) {
        return settings.publicWarps() || warp.owner().equals(player.getUniqueId())
                || player.hasPermission("playerwarps.admin");
    }

    private void ensureDestination(Location location) {
        if (!settings.worldEnabled(location.getWorld().getName())) {
            throw new WarpProblem("world-disabled", "world", location.getWorld().getName());
        }
        if (location.getY() < location.getWorld().getMinHeight() || location.getY() >= location.getWorld().getMaxHeight()
                || !location.getWorld().getWorldBorder().isInside(location)) {
            throw new WarpProblem("outside-world", "world", location.getWorld().getName());
        }
    }

    private static void require(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            throw new WarpProblem("no-permission", "permission", permission);
        }
    }
}
