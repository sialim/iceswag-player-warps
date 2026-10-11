package net.serverplugins.warps;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class WarpStore {
    private final Path file;
    private Map<String, PlayerWarp> warps;

    WarpStore(Path file) throws IOException {
        this.file = file;
        warps = read();
    }

    Map<String, PlayerWarp> all() {
        return Map.copyOf(warps);
    }

    void replace(Map<String, PlayerWarp> replacement) throws IOException {
        YamlConfiguration data = new YamlConfiguration();
        data.set("schema-version", 1);
        ConfigurationSection section = data.createSection("warps");
        for (PlayerWarp warp : replacement.values().stream().sorted(java.util.Comparator.comparing(PlayerWarp::name)).toList()) {
            ConfigurationSection entry = section.createSection(warp.name());
            entry.set("owner", warp.owner().toString());
            entry.set("owner-name", warp.ownerName());
            entry.set("world", warp.world().toString());
            entry.set("x", warp.x());
            entry.set("y", warp.y());
            entry.set("z", warp.z());
            entry.set("yaw", warp.yaw());
            entry.set("pitch", warp.pitch());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "warps-", ".tmp");
        try {
            Files.writeString(temporary, data.saveToString());
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            warps = Map.copyOf(replacement);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<String, PlayerWarp> read() throws IOException {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            YamlConfiguration data = new YamlConfiguration();
            data.load(file.toFile());
            if (data.getInt("schema-version", -1) != 1) {
                throw new IllegalArgumentException("Unsupported warp data version.");
            }
            ConfigurationSection section = data.getConfigurationSection("warps");
            if (section == null) {
                throw new IllegalArgumentException("Missing warp data.");
            }
            Map<String, PlayerWarp> loaded = new LinkedHashMap<>();
            for (String name : section.getKeys(false)) {
                if (!name.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
                    throw new IllegalArgumentException("Invalid stored warp name.");
                }
                ConfigurationSection entry = section.getConfigurationSection(name);
                if (entry == null) {
                    throw new IllegalArgumentException("Invalid warp entry.");
                }
                PlayerWarp warp = new PlayerWarp(name, UUID.fromString(entry.getString("owner", "")),
                        entry.getString("owner-name", ""), UUID.fromString(entry.getString("world", "")),
                        number(entry, "x"), number(entry, "y"), number(entry, "z"),
                        (float) number(entry, "yaw"), (float) number(entry, "pitch"));
                loaded.put(name, warp);
            }
            return Map.copyOf(loaded);
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IOException("Warp data is unreadable. Repair warps.yml before reloading.", exception);
        }
    }

    private static double number(ConfigurationSection entry, String key) {
        Object value = entry.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Missing numeric warp field: " + key);
        }
        return number.doubleValue();
    }
}
