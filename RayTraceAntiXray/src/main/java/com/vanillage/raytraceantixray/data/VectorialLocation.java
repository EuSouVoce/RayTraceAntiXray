package com.vanillage.raytraceantixray.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

public final class VectorialLocation {
    private final Reference<World> world;
    private final Vector vector;
    private final Vector direction;

    public VectorialLocation(final World world, final Vector vector, final Vector direction) {
        this.world = new WeakReference<>(world);
        this.vector = vector;
        this.direction = direction;
    }

    public VectorialLocation(final VectorialLocation location) {
        this.world = location.world;
        this.vector = location.getVector().clone();
        this.direction = location.getDirection().clone();
    }

    public VectorialLocation(final Location location) {
        this(location.getWorld(), location.toVector(), location.getDirection());
    }

    public World getWorld() {
        return this.world.get();
    }

    public Vector getVector() {
        return this.vector;
    }

    public Vector getDirection() {
        return this.direction;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object)
            return true;
        if (object == null || this.getClass() != object.getClass())
            return false;
        final VectorialLocation that = (VectorialLocation) object;
        return Objects.equals(this.vector, that.vector) && Objects.equals(this.direction, that.direction)
                && Objects.equals(this.world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.world, this.vector, this.direction);
    }

}
