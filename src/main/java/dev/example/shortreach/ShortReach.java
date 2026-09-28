package dev.example.shortreach;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class ShortReach extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey modKey;
    private BukkitTask task;
    private BossBar bar;
    private int totalSeconds;
    private int secondsLeft;
    private boolean active;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        modKey = new NamespacedKey(this, "short_reach");
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("shortreach");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        // Clean up leftovers from a crash / restart mid-event
        Bukkit.getOnlinePlayers().forEach(this::clear);
    }

    @Override
    public void onDisable() {
        stop(false);
    }

    // ---------- core logic ----------

    private void start(int minutes) {
        stop(false);
        totalSeconds = minutes * 60;
        secondsLeft = totalSeconds;
        active = true;

        bar = Bukkit.createBossBar("Short Reach", BarColor.RED, BarStyle.SOLID);
        Bukkit.getOnlinePlayers().forEach(p -> {
            apply(p);
            bar.addPlayer(p);
        });

        task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                stop(true);
                return;
            }
            bar.setTitle("Short Reach - " + format(secondsLeft) + " left");
            bar.setProgress(Math.max(0.0, Math.min(1.0, (double) secondsLeft / totalSeconds)));
        }, 20L, 20L);

        bar.setTitle("Short Reach - " + format(secondsLeft) + " left");
        bar.setProgress(1.0);
        Bukkit.broadcast(Component.text("Short Reach is active for " + minutes
                + " minutes! Your reach is greatly reduced.", NamedTextColor.RED));
    }

    private void stop(boolean announce) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        boolean was = active;
        active = false;
        Bukkit.getOnlinePlayers().forEach(this::clear);
        if (announce && was) {
            Bukkit.broadcast(Component.text("Short Reach has ended. Reach is back to normal.",
                    NamedTextColor.GREEN));
        }
    }

    private void apply(Player p) {
        setRange(p, Attribute.BLOCK_INTERACTION_RANGE, getConfig().getDouble("block-range", 1.0));
        setRange(p, Attribute.ENTITY_INTERACTION_RANGE, getConfig().getDouble("entity-range", 1.0));
    }

    private void setRange(Player p, Attribute attr, double target) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(modKey);
        double delta = target - inst.getBaseValue();
        inst.addTransientModifier(new AttributeModifier(modKey, delta, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void clear(Player p) {
        for (Attribute attr : new Attribute[]{Attribute.BLOCK_INTERACTION_RANGE, Attribute.ENTITY_INTERACTION_RANGE}) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst != null) inst.removeModifier(modKey);
        }
    }

    private static String format(int s) {
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ---------- events ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (active) {
            apply(p);
            if (bar != null) bar.addPlayer(p);
        } else {
            clear(p);
        }
    }

    // ---------- command ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                int minutes = getConfig().getInt("duration-minutes", 30);
                if (args.length > 1) {
                    try {
                        minutes = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Component.text("Minutes must be a number.", NamedTextColor.RED));
                        return true;
                    }
                }
                start(minutes);
            }
            case "stop" -> {
                if (!active) {
                    sender.sendMessage(Component.text("Short Reach is not active.", NamedTextColor.YELLOW));
                } else {
                    stop(true);
                }
            }
            case "reload" -> {
                reloadConfig();
                if (active) Bukkit.getOnlinePlayers().forEach(this::apply);
                sender.sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(active
                    ? "Short Reach is active: " + format(secondsLeft) + " left."
                    : "Short Reach is not active. Use /shortreach start [minutes]",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("start", "stop", "status", "reload");
        return List.of();
    }
}
