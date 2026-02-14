package com.vanillage.raytraceantixray.util;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;

public class NetworkUtil {

    private static Field field_serverGamePacketListenerImpl_connection = null;

    private static Field field_regionizedServer_connections = null;
    private static Object regionizedServerInstance = null;

    // not public in spigot
    public static Connection getConnection(final ServerGamePacketListenerImpl listener) {
        try {
            if (NetworkUtil.field_serverGamePacketListenerImpl_connection == null) {
                Field f = null;
                Class<?> clazz = ServerGamePacketListenerImpl.class;
                do {
                    for (final Field check : clazz.getDeclaredFields()) {
                        if (check.getType().isAssignableFrom(Connection.class)) {
                            f = check;
                            break;
                        }
                    }
                } while (f == null && (clazz = clazz.getSuperclass()) != null);
                f.setAccessible(true);
                NetworkUtil.field_serverGamePacketListenerImpl_connection = f;
            }
            return (Connection) NetworkUtil.field_serverGamePacketListenerImpl_connection.get(listener);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Error while getting network connection", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Iterable<Connection> getConnections() {
        if (BukkitUtil.IS_FOLIA) {
            try {
                if (NetworkUtil.field_regionizedServer_connections == null || NetworkUtil.regionizedServerInstance == null) {
                    final ClassLoader scl = Bukkit.getServer().getClass().getClassLoader();
                    final Class<?> regionizedServerClazz = scl.loadClass("io.papermc.paper.threadedregions.RegionizedServer");
                    final Field connectionsField = regionizedServerClazz.getDeclaredField("connections");
                    connectionsField.setAccessible(true);

                    final Method getInstanceMethod = regionizedServerClazz.getDeclaredMethod("getInstance");
                    getInstanceMethod.setAccessible(true);
                    final Object instance = getInstanceMethod.invoke(null);

                    NetworkUtil.field_regionizedServer_connections = connectionsField;
                    NetworkUtil.regionizedServerInstance = instance;
                }
                return (Iterable<Connection>) NetworkUtil.field_regionizedServer_connections.get(NetworkUtil.regionizedServerInstance);
            } catch (final Exception e) {
                throw new RuntimeException("Could not resolve regionized server connections field", e);
            }
        } else {
            return MinecraftServer.getServer().getConnection().getConnections();
        }
    }

    public static Channel getChannelOrThrow(final Connection connection) {
        return Objects.requireNonNull(connection.channel,
                "Channel is null for address: " + connection.getRemoteAddress());
    }

    public static Connection getServerConnectionOrThrow(final InetAddress address) {
        return Objects.requireNonNull(NetworkUtil.getServerConnection(address), "Connection not found for address: " + address);
    }

    public static Connection getServerConnection(final InetAddress address) {
        for (final Connection c : NetworkUtil.getConnections()) {
            if (c.getRemoteAddress() instanceof final InetSocketAddress addr && addr.getAddress() == address) {
                return c;
            }
        }
        return null;
    }

}
