package com.vanillage.raytraceantixray.commands;

import com.google.common.base.Stopwatch;
import com.vanillage.raytraceantixray.RayTraceAntiXray;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class RayTraceAntiXrayTabExecutor implements TabExecutor {
    private final RayTraceAntiXray plugin;

    public RayTraceAntiXrayTabExecutor(final RayTraceAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String label, final String[] args) {
        final LinkedList<String> completions = new LinkedList<>();

        if (args.length == 0) {
            if ("raytraceantixray".startsWith(label.toLowerCase(Locale.ROOT))) {
                completions.add("raytraceantixray");
            }
        } else if (command.getName().toLowerCase(Locale.ROOT).equals("raytraceantixray")) {
            if (args.length == 1) {
                if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings")
                        && "timings".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add("timings");
                }
                if (sender.hasPermission("raytraceantixray.command.reload")
                        && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add("reload");
                }
                if (sender.hasPermission("raytraceantixray.command.reloadchunks")
                        && "reloadchunks".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add("reloadchunks");
                }
            } else if (args[0].toLowerCase(Locale.ROOT).equals("timings")) {
                if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings")) {
                    if (args.length == 2) {
                        if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings.on")
                                && "on".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            completions.add("on");
                        }

                        if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings.off")
                                && "off".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            completions.add("off");
                        }
                    }
                }
            } else if (args[0].toLowerCase(Locale.ROOT).equals("reloadchunks")) {
                if (sender.hasPermission("raytraceantixray.command.reloadchunks")) {
                    if (args.length > 1) {
                        if (!args[1].equalsIgnoreCase("*")) {
                            completions.addAll(Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(n -> n.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                                    .toList());
                        }
                        if (args.length == 2 && "*".startsWith(args[1])) {
                            completions.add("*");
                        }
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (command.getName().toLowerCase(Locale.ROOT).equals("raytraceantixray")) {
            if (args.length == 0) {

            } else if (args[0].toLowerCase(Locale.ROOT).equals("timings")) {
                if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings")) {
                    if (args.length == 1) {
                        sender.sendMessage(Component.text()
                                .append(Component.text("Enable or disable timings."))
                                .appendNewline()
                                .append(Component.text(
                                        "This logs to console the average time that each raytrace tick takes, each second."))
                                .appendNewline()
                                .append(Component.text("Usage: ").append(Component.text("/" + label + " timings <on|off>", NamedTextColor.GOLD)))
                                .appendNewline()
                                .append(Component.text("Consider using spark profiler for more information: "))
                                .appendNewline()
                                .append(Component.text(
                                        "  /spark profiler start --interval 1 --thread .*RayTraceAntiXray.* --regex",
                                        NamedTextColor.GOLD)
                                        .hoverEvent(HoverEvent.showText(Component.text("click to suggest", NamedTextColor.GOLD)))
                                        .clickEvent(ClickEvent.suggestCommand(
                                                "/spark profiler start --interval 1 --thread .*RayTraceAntiXray.* --regex")))
                                .color(NamedTextColor.YELLOW));
                        return true;
                    } else if (args[1].toLowerCase(Locale.ROOT).equals("on")) {
                        if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings.on")) {
                            if (args.length == 2) {
                                this.plugin.setTimingsEnabled(true);
                                sender.sendMessage(Component.text("Timings turned on.", NamedTextColor.GOLD));
                                return true;
                            }
                        } else {
                            sender.sendMessage(Component.text("You don't have permission to modify timings.", NamedTextColor.RED));
                            return true;
                        }
                    } else if (args[1].toLowerCase(Locale.ROOT).equals("off")) {
                        if (sender.hasPermission("raytraceantixray.command.raytraceantixray.timings.off")) {
                            if (args.length == 2) {
                                this.plugin.setTimingsEnabled(false);
                                sender.sendMessage(Component.text("Timings turned off.", NamedTextColor.GOLD));
                                return true;
                            }
                        } else {
                            sender.sendMessage(Component.text("You don't have permission to modify timings.", NamedTextColor.RED));
                            return true;
                        }
                    }
                } else {
                    sender.sendMessage(Component.text("You don't have permission to modify timings.", NamedTextColor.RED));
                    return true;
                }
            } else if (args[0].toLowerCase(Locale.ROOT).equals("reload")) {
                if (sender.hasPermission("raytraceantixray.raytraceantixray.command.reload")) {
                    final Stopwatch w = Stopwatch.createStarted();
                    this.plugin.reload();
                    sender.sendMessage(Component.text("Reloaded in " + w.elapsed(TimeUnit.MILLISECONDS) + "ms", NamedTextColor.GOLD));
                } else {
                    sender.sendMessage(Component.text("You don't have permission to reload this plugin.", NamedTextColor.RED));
                }
                return true;
            } else if (args[0].toLowerCase(Locale.ROOT).equals("reloadchunks")) {
                if (sender.hasPermission("raytraceantixray.command.raytraceantixray.reloadchunks")) {
                    final HashSet<Player> players = new HashSet<>();
                    if (args.length > 1) {
                        if (args[1].equalsIgnoreCase("*")) {
                            players.addAll(Bukkit.getOnlinePlayers());
                        } else {
                            for (int i = 1; i < args.length; i++) {
                                final Player target = Bukkit.getPlayerExact(args[i]);
                                if (target == null) {
                                    sender.sendMessage(
                                            Component.text("No player by the name of \"" + args[i] + "\" was found.", NamedTextColor.GOLD));
                                    return true;
                                } else {
                                    players.add(target);
                                }
                            }
                        }
                    } else {
                        if (sender instanceof Player) {
                            players.add((Player) sender);
                        } else {
                            sender.sendMessage(Component.text("You must specify a player.", NamedTextColor.RED));
                            return false;
                        }
                    }
                    sender.sendMessage(Component.text("Reloading chunks...", NamedTextColor.GOLD));
                    this.plugin.reloadChunks(players);
                    return true;
                } else {
                    sender.sendMessage(Component.text("You don't have permission to reload this plugin.", NamedTextColor.RED));
                }
            }
        }

        return false;
    }
}
