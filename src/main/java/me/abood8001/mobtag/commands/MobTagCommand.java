package me.abood8001.mobtag.commands;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagConfig;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MobTagCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TOGGLE_TARGETS = Arrays.asList("mobs", "players", "damage");
    private static final List<String> ADMIN_SUBS = Arrays.asList("reload", "debug");

    private final MobTag plugin;

    public MobTagCommand(MobTag plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MobTagConfig cfg = plugin.getMobTagConfig();
        String prefix = TextUtil.colorize(cfg.getPrefix());

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix + TextUtil.colorize("&fUsage: &e/mobtag admin reload"));
                return true;
            }
            if (!player.hasPermission("mobtag.toggle")) {
                sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgNoPermission()));
                return true;
            }
            plugin.getGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("toggle")) {
            return handleToggle(sender, args, cfg, prefix);
        }

        if (sub.equals("admin")) {
            if (!sender.hasPermission("mobtag.admin")) {
                sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgNoPermission()));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                plugin.reload();
                sender.sendMessage(prefix + TextUtil.colorize(plugin.getMobTagConfig().getMsgReloadSuccess()));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
                sendDebug(sender, prefix);
                return true;
            }
            sender.sendMessage(prefix + TextUtil.colorize("&fUsage: &e/mobtag admin <reload|debug>"));
            return true;
        }

        sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgUnknownCommand()));
        return true;
    }

    private boolean handleToggle(CommandSender sender, String[] args, MobTagConfig cfg, String prefix) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgPlayersOnly()));
            return true;
        }
        if (!player.hasPermission("mobtag.toggle")) {
            sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgNoPermission()));
            return true;
        }

        UUID uid = player.getUniqueId();
        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "mobs";

        boolean nowHidden;
        String feature;
        switch (target) {
            case "mobs" -> {
                nowHidden = plugin.getToggleManager().toggleMobTags(uid);
                feature = "Mob health tags";
            }
            case "players" -> {
                if (!cfg.isPlayerTagsEnabled()) {
                    player.sendMessage(prefix + TextUtil.colorize("&cPlayer tags are disabled on this server."));
                    return true;
                }
                nowHidden = plugin.getToggleManager().togglePlayerTags(uid);
                feature = "Player health tags";
            }
            case "damage" -> {
                nowHidden = plugin.getToggleManager().toggleDamageIndicator(uid);
                feature = "Damage indicators";
            }
            default -> {
                player.sendMessage(prefix + TextUtil.colorize("&fUsage: &e/mobtag toggle <mobs|players|damage>"));
                return true;
            }
        }

        String message = nowHidden ? cfg.getMsgToggleOff() : cfg.getMsgToggleOn();
        player.sendMessage(prefix + TextUtil.colorize(message.replace("{feature}", feature)));
        return true;
    }

    private void sendDebug(CommandSender sender, String prefix) {
        var manager = plugin.getTagManager();
        var indicators = plugin.getIndicatorManager();
        MobTagConfig cfg = plugin.getMobTagConfig();

        sender.sendMessage(prefix + TextUtil.colorize("&bDebug"));
        sender.sendMessage(TextUtil.colorize("&7 Version: &f" + plugin.getPluginVersion()));
        sender.sendMessage(TextUtil.colorize("&7 Display mode: &f" + cfg.getDisplayMode()
                + " &7| Visibility: &f" + cfg.getVisibilityMode()));
        sender.sendMessage(TextUtil.colorize("&7 Active tags: &f" + manager.getTagCount()
                + " &7| Damage indicators: &f" + (indicators == null ? 0 : indicators.getActiveCount())));
        sender.sendMessage(TextUtil.colorize("&7 Entities scanned last tick: &f" + manager.getLastScanned()));
        sender.sendMessage(TextUtil.colorize(String.format(Locale.US,
                "&7 Last update loop: &f%.3f ms &7(every %d ticks)",
                manager.getLastLoopMillis(), cfg.getUpdateInterval())));
        sender.sendMessage(TextUtil.colorize("&7 Hooks: &fPacketEvents=" + plugin.isPacketEventsEnabled()
                + " PlaceholderAPI=" + plugin.isPapiEnabled()
                + " MythicMobs=" + plugin.isMythicMobsEnabled()
                + " MiniMessage=" + TextUtil.isMiniMessageAvailable()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("mobtag.toggle")) options.add("toggle");
            if (sender.hasPermission("mobtag.admin")) options.add("admin");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission("mobtag.toggle")) {
                options.addAll(TOGGLE_TARGETS);
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("mobtag.admin")) {
                options.addAll(ADMIN_SUBS);
            }
        }

        if (options.isEmpty()) return Collections.emptyList();
        // Bukkit does not filter a custom completer's output, so do it here.
        return StringUtil.copyPartialMatches(args[args.length - 1], options, new ArrayList<>());
    }
}
