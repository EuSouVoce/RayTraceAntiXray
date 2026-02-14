package com.vanillage.raytraceantixray.listeners;

import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.antixray.ChunkPacketBlockControllerAntiXray;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class WorldListener implements Listener {
    private final RayTraceAntiXray plugin;

    public WorldListener(final RayTraceAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(final WorldInitEvent event) {
        WorldListener.handleLoad(this.plugin, event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(final WorldUnloadEvent e) {
        WorldListener.handleUnload(this.plugin, e.getWorld());
    }

    public static void handleLoad(final RayTraceAntiXray plugin, final World world) {
        if (plugin.isEnabled(world)) {
            final var antiXrayConfig = plugin.getWorldAntiXray(world);
            final boolean rayTraceThirdPerson = antiXrayConfig.rayTraceThirdPerson();
            final double rayTraceDistance = Math.max(antiXrayConfig.rayTraceDistance(), 0.0d);
            final boolean rehideBlocks = antiXrayConfig.rehideBlocks();
            final double rehideDistance = Math.max(antiXrayConfig.rehideDistance(), 0.0d);
            final int maxRayTraceBlockCountPerChunk = Math.max(antiXrayConfig.maxRayTraceBlockCountPerChunk(), 0);
            final List<String> rayTraceBlocks = antiXrayConfig.rayTraceBlocks().stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toList());
            final List<String> bypassRehideBlocks = antiXrayConfig.bypassRehideBlocks().stream()
                .filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
            final ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            final ChunkPacketBlockControllerAntiXray controller = new ChunkPacketBlockControllerAntiXray(
                    plugin,
                    ((CraftWorld) world).getHandle().chunkPacketBlockController,
                    rayTraceThirdPerson,
                    rayTraceDistance,
                    rehideBlocks,
                    rehideDistance,
                    maxRayTraceBlockCountPerChunk,
                    rayTraceBlocks.isEmpty() ? null : rayTraceBlocks,
                    bypassRehideBlocks.isEmpty() ? null : bypassRehideBlocks,
                    serverLevel,
                    MinecraftServer.getServer().executor);

            try {
                final Field field = Level.class.getDeclaredField("chunkPacketBlockController");
                field.setAccessible(true);
                field.set(serverLevel, controller);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void handleUnload(final RayTraceAntiXray plugin, final World w) {
        if (((CraftWorld) w).getHandle().chunkPacketBlockController instanceof ChunkPacketBlockControllerAntiXray) {
            final ChunkPacketBlockController oldController = ((ChunkPacketBlockControllerAntiXray) ((CraftWorld) w)
                    .getHandle().chunkPacketBlockController).getOldController();

            try {
                final Field field = Level.class.getDeclaredField("chunkPacketBlockController");
                field.setAccessible(true);
                field.set(((CraftWorld) w).getHandle(), oldController);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
