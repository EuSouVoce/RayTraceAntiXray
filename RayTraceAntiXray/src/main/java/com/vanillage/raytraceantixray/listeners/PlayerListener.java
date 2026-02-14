package com.vanillage.raytraceantixray.listeners;

import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.data.PlayerData;
import com.vanillage.raytraceantixray.tasks.UpdateBukkitRunnable;
import com.vanillage.raytraceantixray.util.BukkitUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

/**
 * Player lifecycle listener that creates and tears down per-player anti-xray runtime state.
 * <p>
 * The quit path is intentionally null-safe because disconnect and plugin-disable flows can race.
 */
public final class PlayerListener implements Listener {
    private final RayTraceAntiXray plugin;

    public PlayerListener(final RayTraceAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        try {
            if (this.plugin.tryCreatePlayerDataFor(player) == null)
                return;

            if (BukkitUtil.IS_FOLIA) {
                event.getPlayer().getScheduler().runAtFixedRate(this.plugin,
                        new UpdateBukkitRunnable(this.plugin, event.getPlayer()), null, 1L, this.plugin.getUpdateTicks());
            }
        } catch (final Throwable t) {
            player.kick(Component.text(
                    "RayTraceAntiXray encountered an error for your connection, please contact server administrators: "
                            + t.getMessage()));
            if (t instanceof Exception) {
                this.plugin.getLogger().log(Level.SEVERE,
                        "Exception raised while creating data for \"" + player + "\" during player join", t);
            } else {
                throw t;
            }
        }
    }

    /**
     * Detaches packet handler and removes player state.
     * <p>
     * Detach is guarded because state may already be partially cleaned up by concurrent shutdown.
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final PlayerData data = this.plugin.getPlayerData().get(event.getPlayer().getUniqueId());
        if (data != null) {
            if (data.getPacketHandler() != null) {
                data.getPacketHandler().detach();
            }
            this.plugin.getPlayerData().remove(event.getPlayer().getUniqueId(), data);
        }
    }

}
