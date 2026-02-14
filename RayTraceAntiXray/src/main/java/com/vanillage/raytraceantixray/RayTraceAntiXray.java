package com.vanillage.raytraceantixray;

import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.vanillage.raytraceantixray.antixray.ChunkPacketBlockControllerAntiXray;
import com.vanillage.raytraceantixray.commands.RayTraceAntiXrayTabExecutor;
import com.vanillage.raytraceantixray.data.ChunkBlocks;
import com.vanillage.raytraceantixray.data.PlayerData;
import com.vanillage.raytraceantixray.data.VectorialLocation;
import com.vanillage.raytraceantixray.listeners.PlayerListener;
import com.vanillage.raytraceantixray.listeners.WorldListener;
import com.vanillage.raytraceantixray.net.DuplexPacketHandler;
import com.vanillage.raytraceantixray.tasks.RayTraceCallable;
import com.vanillage.raytraceantixray.tasks.RayTraceTimerTask;
import com.vanillage.raytraceantixray.tasks.UpdateBukkitRunnable;
import com.vanillage.raytraceantixray.util.BukkitUtil;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import io.papermc.paper.configuration.WorldConfiguration.Anticheat.AntiXray;
import io.papermc.paper.configuration.type.EngineMode;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class RayTraceAntiXray extends JavaPlugin {
    // private volatile Configuration configuration;
    private volatile boolean running = false;
    private volatile boolean timingsEnabled = false;
    private final ConcurrentMap<ClientboundLevelChunkWithLightPacket, ChunkBlocks> packetChunkBlocksCache = new MapMaker()
            .weakKeys().makeMap();
    private final ConcurrentMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private ExecutorService executorService;
    private Timer timer;
    private long updateTicks = 1L;
    private boolean debug;
    // Set of messages and errors this plugin instance has nagged about.
    // Used to prevent console spam.
    private final Set<String> nagged = Collections.synchronizedSet(new HashSet<>());

    public static RayTraceAntiXray getInstance() {
        return JavaPlugin.getPlugin(RayTraceAntiXray.class);
    }

    @Override
    public void onEnable() {
        if (!new File(this.getDataFolder(), "README.txt").exists()) {
            this.saveResource("README.txt", false);
        }
        this.nagged.clear();

        this.saveDefaultConfig();
        final FileConfiguration config = this.getConfig();
        config.options().copyDefaults(true);
        this.reloadConfig();

        // saveConfig();
        // configuration = config;
        // Initialize stuff.

        this.running = true;
        // Use a combination of a tick thread (timer) and a ray trace thread pool.
        // The timer schedules tasks (a task per player) to the thread pool and ensures
        // a common and defined tick start and end time without overlap by waiting for
        // the thread pool to finish all tasks.
        // A scheduled thread pool with a task per player would also be possible but
        // then there's no common tick.
        this.executorService = Executors.newFixedThreadPool(
                Math.max(config.getInt("settings.anti-xray.ray-trace-threads"), 1),
                new ThreadFactoryBuilder().setThreadFactory(Executors.defaultThreadFactory())
                        .setNameFormat("RayTraceAntiXray raytrace thread %d").setDaemon(true).build());
        // Use a timer instead of a single thread scheduled executor because there is no
        // equivalent for the timer's schedule method.
        this.timer = new Timer("RayTraceAntiXray tick thread", true);
        this.timer.schedule(new RayTraceTimerTask(this), 0L,
                Math.max(config.getLong("settings.anti-xray.ms-per-ray-trace-tick"), 1L));
        this.updateTicks = Math.max(config.getLong("settings.anti-xray.update-ticks"), 1L);
        this.debug = config.getBoolean("settings.debug", false);

        if (!BukkitUtil.IS_FOLIA) {
            new UpdateBukkitRunnable(this).runTaskTimer(this, 0L, this.updateTicks);
        }

        // Register events.
        final PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(new WorldListener(this), this);
        pluginManager.registerEvents(new PlayerListener(this), this);

        // Handle reloads/plugin managers
        for (final World w : Bukkit.getWorlds())
            WorldListener.handleLoad(this, w);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            try {
                this.tryCreatePlayerDataFor(player);
            } catch (final Exception e) {
                this.getLogger().log(Level.SEVERE,
                        "Exception raised while creating data for \"" + player + "\" during plugin load", e);
            }
        }

        // registerCommands();
        this.getCommand("raytraceantixray").setExecutor(new RayTraceAntiXrayTabExecutor(this));
        this.getLogger().info(this.getPluginMeta().getDisplayName()+ " EuSouVoce's ThreadSafe fork | Enabled");}

    @SuppressWarnings("unused")
    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        // The server catches all throwables and may continue to run after disabling
        // this plugin.
        // We want to ensure as much as possible that everything is left behind in a
        // clean and defined state.
        // So the goal is to at least attempt to execute all critical sections of code,
        // regardless of what happens before.
        // Considering errors during error handling and JLS 11.1.3. Asynchronous
        // Exceptions, throwables could potentially be thrown anywhere (even between
        // blocks of code or statements?).
        // Thus the only way is to nest try-finally statements like this: try { try { }
        // finally { } } finally { }
        // According to the bytecode of nested try-catch statements in JVMS 3.12, all
        // nested try blocks are entered at the same time.
        // So we either reach the innermost try block, in which case all blocks will be
        // at least attempt to be executed, or no block is entered at all (e.g. in case
        // of a throwable being thrown before).
        // Both outcomes yield a defined state of this plugin.
        // A more intuitive way would be to nest inside of the finally clause like this:
        // try { } finally { try { } finally { } }
        // However, this doesn't provide the same guarantees as described above.
        // Additionally, we can add catch clauses to collect suppressed exceptions and
        // rethrow in the last finally clause.
        Throwable throwable = null;
        try {
            try {
                try {
                    try {
                        // Cleanup stuff.
                        try {
                            for (final Player p : Bukkit.getOnlinePlayers()) {
                                if (p.hasMetadata("NPC"))
                                    continue;
                                DuplexPacketHandler.detach(p, DuplexPacketHandler.NAME);
                            }
                        } catch (final Throwable t) {
                            if (throwable == null) {
                                throwable = t;
                            } else {
                                throwable.addSuppressed(t);
                            }
                        }
                    } catch (final Throwable t) {
                        if (throwable == null) {
                            throwable = t;
                        } else {
                            throwable.addSuppressed(t);
                        }
                    } finally {
                        this.running = false;
                        this.timer.cancel();
                    }
                } catch (final Throwable t) {
                    if (throwable == null) {
                        throwable = t;
                    } else {
                        throwable.addSuppressed(t);
                    }
                } finally {
                    this.executorService.shutdownNow();

                    try {
                        this.executorService.awaitTermination(1000L, TimeUnit.MILLISECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        BukkitUtil.sneakyThrow(e);
                    } finally {
                        try {
                            for (final World w : Bukkit.getWorlds()) {
                                WorldListener.handleUnload(this, w);
                            }
                        } catch (final Throwable t) {
                            if (throwable == null) {
                                throwable = t;
                            } else {
                                throwable.addSuppressed(t);
                            }
                        }
                    }
                }
            } catch (final Throwable t) {
                if (throwable == null) {
                    throwable = t;
                } else {
                    throwable.addSuppressed(t);
                }
            } finally {
                this.packetChunkBlocksCache.clear();
                this.playerData.clear();
            }
        } catch (final Throwable t) {
            if (throwable == null) {
                throwable = t;
            } else {
                throwable.addSuppressed(t);
            }
        } finally {
            if (throwable != null) {
                BukkitUtil.sneakyThrow(throwable);
            }
        }

        this.getLogger().info(this.getPluginMeta().getDisplayName() + " EuSouVoce's ThreadSafe fork | Disabled");
    }

    public void reload() {
        this.onDisable();
        this.onEnable();
        this.getLogger().info(this.getPluginMeta().getName() + " reloaded");
    }

    public void reloadChunks(final Iterable<Player> players) {
        for (final Player bp : players) {
            try {
                if (this.tryCreatePlayerDataFor(bp) == null)
                    continue;

                final ServerPlayer sp = ((CraftPlayer) bp).getHandle();
                final var playerChunkManager = sp.level().moonrise$getPlayerChunkLoader();
                playerChunkManager.removePlayer(sp);
                playerChunkManager.addPlayer(sp);
            } catch (final Exception e) {
                this.getLogger().log(Level.WARNING, "Failed to reloadChunks for: " + bp, e);
            }
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    public boolean isTimingsEnabled() {
        return this.timingsEnabled;
    }

    public void setTimingsEnabled(final boolean timingsEnabled) {
        this.timingsEnabled = timingsEnabled;
    }

    public ConcurrentMap<ClientboundLevelChunkWithLightPacket, ChunkBlocks> getPacketChunkBlocksCache() {
        return this.packetChunkBlocksCache;
    }

    public ConcurrentMap<UUID, PlayerData> getPlayerData() {
        return this.playerData;
    }

    public ExecutorService getExecutorService() {
        return this.executorService;
    }

    public long getUpdateTicks() {
        return this.updateTicks;
    }

    public boolean isEnabled(final World world) {
        final AntiXray antiXray = ((CraftWorld) world).getHandle().paperConfig().anticheat.antiXray;

        if (antiXray.enabled && antiXray.engineMode == EngineMode.HIDE) {
            final FileConfiguration config = this.getConfig();
            return config.getBoolean("world-settings." + world.getName() + ".anti-xray.ray-trace",
                    config.getBoolean("world-settings.default.anti-xray.ray-trace"));
        }

        return false;
    }

    public boolean validatePlayer(final Player player) {
        return !player.hasMetadata("NPC");
    }

    public PlayerData tryCreatePlayerDataFor(final Player player) {
        if (!this.validatePlayer(player))
            return null;

        return this.createPlayerDataFor(player, player.getEyeLocation());
    }

    /**
     * Creates or refreshes the per-player runtime state atomically.
     * <p>
     * The method is synchronized to prevent races between concurrent refresh triggers (packet,
     * movement/update tick, join handling) that could otherwise attach duplicate handlers or publish
     * partially initialized state.
     * <p>
     * If the provided location is missing/invalid, a fresh eye location is used as a safe fallback.
     */
    public synchronized PlayerData createPlayerDataFor(final Player player, final Location location) {
        Location effectiveLocation = location;
        if (effectiveLocation == null || effectiveLocation.getWorld() == null) {
            effectiveLocation = player.getEyeLocation();
        }

        final PlayerData playerData = new PlayerData(
                RayTraceAntiXray.getLocations(player, new VectorialLocation(effectiveLocation)));
        playerData.setCallable(new RayTraceCallable(this, playerData));

        final PlayerData oldData = this.getPlayerData().get(player.getUniqueId());

        if (oldData != null) {
            // already has data, use old network handler
            playerData.setPacketHandler(oldData.getPacketHandler());
        } else {
            // create new network handler
            playerData.setPacketHandler(new DuplexPacketHandler(this, player));
            try {
                playerData.getPacketHandler().attach(player);
            } catch (final Exception e) {
                throw new RuntimeException("Failed to attach packet handler to: " + player, e);
            }
        }

        this.getPlayerData().put(player.getUniqueId(), playerData);

        return playerData;
    }

    public static VectorialLocation[] getLocations(final Entity entity, final VectorialLocation location) {
        final World world = entity.getWorld();
        final ChunkPacketBlockController chunkPacketBlockController = ((CraftWorld) world)
                .getHandle().chunkPacketBlockController;

        if (chunkPacketBlockController instanceof ChunkPacketBlockControllerAntiXray
                && ((ChunkPacketBlockControllerAntiXray) chunkPacketBlockController).rayTraceThirdPerson) {
            final VectorialLocation thirdPersonFrontLocation = new VectorialLocation(location);
            thirdPersonFrontLocation.getDirection().multiply(-1.);
            return new VectorialLocation[] { location, RayTraceAntiXray.move(entity,
                    new VectorialLocation(world, location.getVector().clone(), location.getDirection().clone())),
                    RayTraceAntiXray.move(entity, thirdPersonFrontLocation) };
        }

        return new VectorialLocation[] { location };
    }

    private static VectorialLocation move(final Entity entity, final VectorialLocation location) {
        location.getVector().subtract(location.getDirection().clone().multiply(RayTraceAntiXray.getMaxZoom(entity, location, 4.)));
        return location;
    }

    private static double getMaxZoom(final Entity entity, final VectorialLocation location, double maxZoom) {
        final Vector vector = location.getVector();
        final Vec3 position = new Vec3(vector.getX(), vector.getY(), vector.getZ());
        final double positionX = position.x;
        final double positionY = position.y;
        final double positionZ = position.z;
        final Vector direction = location.getDirection();
        final double directionX = direction.getX();
        final double directionY = direction.getY();
        final double directionZ = direction.getZ();
        final ServerLevel serverLevel = ((CraftWorld) location.getWorld()).getHandle();
        final net.minecraft.world.entity.Entity handle = ((CraftEntity) entity).getHandle();

        // Logic copied from Minecraft client.
        for (int i = 0; i < 8; i++) {
            float cornerX = (float) ((i & 1) * 2 - 1);
            float cornerY = (float) ((i >> 1 & 1) * 2 - 1);
            float cornerZ = (float) ((i >> 2 & 1) * 2 - 1);
            cornerX *= 0.1f;
            cornerY *= 0.1f;
            cornerZ *= 0.1f;
            final Vec3 corner = position.add(cornerX, cornerY, cornerZ);
            final Vec3 cornerMoved = new Vec3(positionX - directionX * maxZoom + (double) cornerX,
                    positionY - directionY * maxZoom + (double) cornerY,
                    positionZ - directionZ * maxZoom + (double) cornerZ);
            final BlockHitResult result = serverLevel.clip(
                    new ClipContext(corner, cornerMoved, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, handle));

            if (result.getType() != HitResult.Type.MISS) {
                final double zoom = result.getLocation().distanceTo(position);

                if (zoom < maxZoom) {
                    maxZoom = zoom;
                }
            }
        }

        return maxZoom;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public void setDebug(final boolean debug) {
        this.debug = debug;
    }

    public void logDebugNagReminder() {
        if (!this.isDebug()) {
            this.getLogger().log(Level.WARNING, """
                    Duplicate errors will be ignored. Performance and efficacy might be affected if this error persists.
                    Consider enabling debugging to see every error like this.""");
        }
    }

    // These methods are for preventing console spam from less than critical errors.
    public boolean hasNagged(final Throwable msg) {
        return this.hasNagged(BukkitUtil.stacktraceToString(msg));
    }

    public boolean hasNagged(final String msg) {
        return this.nagged.contains(msg);
    }

    public boolean handleNag(final Throwable msg) {
        return this.handleNag(BukkitUtil.stacktraceToString(msg));
    }

    public boolean handleNag(final String nag) {
        return this.nagged.add(nag) || this.isDebug();
    }

    public static boolean hasController(final World world) {
        return ((CraftWorld) world)
                .getHandle().chunkPacketBlockController instanceof ChunkPacketBlockControllerAntiXray;
    }

}
