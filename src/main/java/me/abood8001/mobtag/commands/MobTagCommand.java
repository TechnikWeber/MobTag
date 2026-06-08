package me.abood8001.mobtag.commands;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MobTagCommand implements CommandExecutor, TabCompleter {

    private final MobTag plugin;

    public MobTagCommand(MobTag plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = MobTagManager.colorize(plugin.getMobTagConfig().getPrefix());

        // /mobtag  -> open GUI
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix + MobTagManager.colorize("&cOnly players can open the GUI. Use /mobtag admin reload."));
                return true;
            }
            plugin.getGUI().open(player);
            return true;
        }

        // /mobtag admin reload
        if (args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("mobtag.admin")) {
                sender.sendMessage(prefix + MobTagManager.colorize(plugin.getMobTagConfig().getMsgNoPermission()));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                plugin.reload();
                sender.sendMessage(prefix + MobTagManager.colorize(plugin.getMobTagConfig().getMsgReloadSuccess()));
            } else {
                sender.sendMessage(prefix + MobTagManager.colorize("&fUsage: &e/mobtag admin reload"));
            }
            return true;
        }

        sender.sendMessage(prefix + MobTagManager.colorize(plugin.getMobTagConfig().getMsgUnknownCommand()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("mobtag.admin")) out.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")
                && sender.hasPermission("mobtag.admin")) {
            out.add("reload");
        }
        return out.isEmpty() ? Collections.emptyList() : out;
    }
}