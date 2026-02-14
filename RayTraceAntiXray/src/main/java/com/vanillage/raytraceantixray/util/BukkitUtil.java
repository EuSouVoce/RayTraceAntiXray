package com.vanillage.raytraceantixray.util;

import org.bukkit.Bukkit;

import java.io.PrintWriter;
import java.io.StringWriter;

public class BukkitUtil {

    public static final boolean IS_FOLIA = BukkitUtil.isFolia();

    public static String stacktraceToString(final Throwable msg) {
        final var sw = new StringWriter();
        try (var pw = new PrintWriter(sw)) {
            msg.printStackTrace(pw);
        }
        return sw.toString();
    }

    private static boolean isFolia() {
        return BukkitUtil.classForName(Bukkit.getServer().getClass().getClassLoader(),
                "io.papermc.paper.threadedregions.RegionizedServer") != null;
    }

    private static Class<?> classForName(final ClassLoader ldr, final String name) {
        try {
            return ldr.loadClass(name);
        } catch (final Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void sneakyThrow(final Throwable t) throws T {
        throw (T) t;
    }

}
