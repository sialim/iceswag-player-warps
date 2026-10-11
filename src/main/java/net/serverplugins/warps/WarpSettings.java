package net.serverplugins.warps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

record WarpSettings(boolean claimCommands, boolean publicWarps, int maximumNameLength, int pageSize,
                    Set<String> disabledWorlds, boolean commandFeedback, List<Rank> ranks) {
    private static final Set<String> RESERVED = Set.of("set", "create", "delete", "remove", "move", "rename",
            "list", "mine", "status", "help", "reload");

    static WarpSettings load(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("At least one rank is required.");
        }
        List<Rank> ranks = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(name);
            if (rank == null) {
                throw new IllegalArgumentException("Invalid rank: " + name);
            }
            String permission = rank.getString("permission", "playerwarps.rank." + name);
            int limit = rank.getInt("warp-limit", 0);
            if (permission.isBlank() || limit < -1) {
                throw new IllegalArgumentException("Invalid permission or warp-limit for " + name + ".");
            }
            ranks.add(new Rank(name, permission, rank.getInt("priority"), limit));
        }
        ranks.sort(Comparator.comparingInt(Rank::priority).reversed().thenComparing(Rank::name));
        Set<String> disabled = config.getStringList("warps.disabled-worlds").stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        return new WarpSettings(config.getBoolean("commands.claim-essentials-warp-commands", true),
                config.getBoolean("warps.public", true),
                Math.clamp(config.getInt("warps.maximum-name-length", 32), 1, 64),
                Math.clamp(config.getInt("warps.page-size", 12), 1, 50), disabled,
                config.getBoolean("messages.command-feedback", false), List.copyOf(ranks));
    }

    Rank rank(Predicate<String> permissions) {
        return ranks.stream().filter(rank -> permissions.test(rank.permission())).findFirst()
                .orElse(new Rank("member", "", Integer.MIN_VALUE, 0));
    }

    String validateName(String input) {
        String name = normalize(input);
        if (name.length() > maximumNameLength || !name.matches("[a-z0-9][a-z0-9_-]*")
                || name.matches("[0-9]+") || RESERVED.contains(name)) {
            throw new IllegalArgumentException("Use a non-reserved name of up to " + maximumNameLength
                    + " letters, numbers, underscores, or hyphens.");
        }
        return name;
    }

    boolean worldEnabled(String name) {
        return !disabledWorlds.contains(name.toLowerCase(Locale.ROOT));
    }

    static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    record Rank(String name, String permission, int priority, int limit) {
    }
}
