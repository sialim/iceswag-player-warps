package net.serverplugins.warps;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.Command;

final class CommandBindings {
    private final PlayerWarpsPlugin plugin;
    private final Map<String, Command> previous = new HashMap<>();
    private final Map<String, Command> claimed = new HashMap<>();

    CommandBindings(PlayerWarpsPlugin plugin) {
        this.plugin = plugin;
    }

    void apply(boolean enabled) {
        restore();
        if (enabled) {
            bind("warp", "pwarp");
            bind("warps", "warps");
            bind("setwarp", "setwarp");
            bind("delwarp", "delwarp");
        }
        plugin.getServer().getOnlinePlayers().forEach(org.bukkit.entity.Player::updateCommands);
    }

    void restore() {
        Map<String, Command> commands = plugin.getServer().getCommandMap().getKnownCommands();
        for (var entry : claimed.entrySet()) {
            if (commands.get(entry.getKey()) == entry.getValue()) {
                Command replacement = previous.get(entry.getKey());
                if (replacement == null) {
                    commands.remove(entry.getKey());
                } else {
                    commands.put(entry.getKey(), replacement);
                }
            }
        }
        previous.clear();
        claimed.clear();
    }

    private void bind(String label, String target) {
        Command command = java.util.Objects.requireNonNull(plugin.getCommand(target));
        Map<String, Command> commands = plugin.getServer().getCommandMap().getKnownCommands();
        previous.put(label, commands.get(label));
        claimed.put(label, command);
        commands.put(label, command);
    }
}
