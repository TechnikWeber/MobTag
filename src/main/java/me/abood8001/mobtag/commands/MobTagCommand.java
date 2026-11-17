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

    private static final List<String> TOGGLE_TARGETS = Arrays.asList("mobs", "players", "damage", "targetbar");
    private static final List<String> SHOW_ARGS = Arrays.asList("always", "looking", "combat");
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

        if (sub.equals("duration")) {
            return handleDuration(sender, args, cfg, prefix);
        }

        if (sub.equals("style") || sub.equals("size") || sub.equals("show")) {
            return handleLook(sender, sub, args, cfg, prefix);
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
            case "targetbar" -> {
                if (!cfg.isTargetBarEnabled()) {
                    player.sendMessage(prefix + TextUtil.colorize("&cThe target bar is disabled on this server."));
                    return true;
                }
                boolean on = !plugin.getToggleManager().isTargetBarOn(uid);
                plugin.getToggleManager().setTargetBar(uid, on);
                nowHidden = !on;
                feature = "Target bar";
            }
            default -> {
                player.sendMessage(prefix + TextUtil.colorize("&fUsage: &e/mobtag toggle <mobs|players|damage|targetbar>"));
                return true;
            }
        }

        String message = nowHidden ? cfg.getMsgToggleOff() : cfg.getMsgToggleOn();
        player.sendMessage(prefix + TextUtil.colorize(message.replace("{feature}", feature)));
        plugin.refreshViewer(player);
        return true;
    }

    /** /mobtag duration [seconds] - how long a tag stays after your last hit. */
    private boolean handleDuration(CommandSender sender, String[] args, MobTagConfig cfg, String prefix) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgPlayersOnly()));
            return true;
        }
        if (!player.hasPermission("mobtag.toggle")) {
            sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgNoPermission()));
            return true;
        }
        Integer value = null;
        if (args.length >= 2) {
            try {
                value = Integer.parseInt(args[1].replace("s", ""));
            } catch (NumberFormatException ignored) {
                value = null;
            }
        }
        List<String> options = new ArrayList<>();
        for (int d : cfg.getDurations()) options.add(String.valueOf(d));
        if (value == null || !cfg.getDurations().contains(value)) {
            player.sendMessage(prefix + TextUtil.colorize("&fDuration: &e"
                    + me.abood8001.mobtag.gui.MobTagGUI.durationName(
                            plugin.getToggleManager().effectiveDuration(player.getUniqueId()))
                    + " &7| options: &f" + String.join(", ", options)));
            return true;
        }
        plugin.getToggleManager().setDuration(player.getUniqueId(), value);
        player.sendMessage(prefix + TextUtil.colorize("&aTags now stay "
                + me.abood8001.mobtag.gui.MobTagGUI.durationName(value) + " after your last hit."));
        plugin.refreshViewer(player);
        return true;
    }

    /** /mobtag style|size|show [value] - the same choices as the menu, handy on Bedrock. */
    private boolean handleLook(CommandSender sender, String sub, String[] args, MobTagConfig cfg, String prefix) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgPlayersOnly()));
            return true;
        }
        if (!player.hasPermission("mobtag.toggle")) {
            sender.sendMessage(prefix + TextUtil.colorize(cfg.getMsgNoPermission()));
            return true;
        }
        UUID uid = player.getUniqueId();
        var toggles = plugin.getToggleManager();
        String value = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;

        switch (sub) {
            case "style" -> {
                if (value == null || !cfg.getStyles().containsKey(value)) {
                    player.sendMessage(prefix + TextUtil.colorize("&fDesign: &e" + toggles.effectiveStyle(uid).id()
                            + " &7| options: &f" + String.join(", ", cfg.getStyles().keySet())));
                    return true;
                }
                toggles.setStyle(uid, value);
                player.sendMessage(prefix + TextUtil.colorize("&aDesign set to " + cfg.getStyles().get(value).name()));
            }
            case "size" -> {
                if (value == null || !cfg.getSizes().containsKey(value)) {
                    player.sendMessage(prefix + TextUtil.colorize("&fSize: &e" + toggles.effectiveSize(uid).id()
                            + " &7| options: &f" + String.join(", ", cfg.getSizes().keySet())));
                    return true;
                }
                toggles.setSize(uid, value);
                player.sendMessage(prefix + TextUtil.colorize("&aSize set to " + cfg.getSizes().get(value).name()));
            }
            default -> {
                String mode = value == null ? null : switch (value) {
                    case "always", "all" -> "ALL";
                    case "looking", "looking_at" -> "LOOKING_AT";
                    case "combat", "damager" -> "DAMAGER";
                    default -> null;
                };
                if (mode == null) {
                    player.sendMessage(prefix + TextUtil.colorize("&fWhen: &e"
                            + me.abood8001.mobtag.gui.MobTagGUI.showName(toggles.effectiveShow(uid))
                            + " &7| options: &f" + String.join(", ", SHOW_ARGS)));
                    return true;
                }
                toggles.setShow(uid, mode);
                player.sendMessage(prefix + TextUtil.colorize("&aTags now shown: "
                        + me.abood8001.mobtag.gui.MobTagGUI.showName(mode)));
            }
        }
        plugin.refreshViewer(player);
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
            if (sender.hasPermission("mobtag.toggle")) {
                options.add("toggle");
                options.add("style");
                options.add("size");
                options.add("show");
                options.add("duration");
            }
            if (sender.hasPermission("mobtag.admin")) options.add("admin");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission("mobtag.toggle")) {
                options.addAll(TOGGLE_TARGETS);
            } else if (args[0].equalsIgnoreCase("style") && sender.hasPermission("mobtag.toggle")) {
                options.addAll(plugin.getMobTagConfig().getStyles().keySet());
            } else if (args[0].equalsIgnoreCase("size") && sender.hasPermission("mobtag.toggle")) {
                options.addAll(plugin.getMobTagConfig().getSizes().keySet());
            } else if (args[0].equalsIgnoreCase("duration") && sender.hasPermission("mobtag.toggle")) {
                for (int d : plugin.getMobTagConfig().getDurations()) options.add(String.valueOf(d));
            } else if (args[0].equalsIgnoreCase("show") && sender.hasPermission("mobtag.toggle")) {
                options.addAll(SHOW_ARGS);
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("mobtag.admin")) {
                options.addAll(ADMIN_SUBS);
            }
        }

        if (options.isEmpty()) return Collections.emptyList();
        // Bukkit does not filter a custom completer's output, so do it here.
        return StringUtil.copyPartialMatches(args[args.length - 1], options, new ArrayList<>());
    }
}
