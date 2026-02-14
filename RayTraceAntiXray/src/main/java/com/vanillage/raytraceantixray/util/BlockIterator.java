package com.vanillage.raytraceantixray.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

// Amanatides, J., & Woo, A. A Fast Voxel Traversal Algorithm for Ray Tracing. http://www.cse.yorku.ca/~amana/research/grid.pdf.
public final class BlockIterator implements Iterator<int[]> {
    private int x;
    private int y;
    private int z;
    private int stepX;
    private int stepY;
    private int stepZ;
    private double tMax;
    private double tMaxX;
    private double tMaxY;
    private double tMaxZ;
    private double tDeltaX;
    private double tDeltaY;
    private double tDeltaZ;
    private int[] ref = new int[3]; // This implementation always returns ref or refSwap to avoid garbage. Can
                                    // easily be changed if needed.
    private int[] refSwap = new int[3];
    private int[] next;

    public BlockIterator(final double startX, final double startY, final double startZ, final double endX, final double endY, final double endZ) {
        this.initialize(startX, startY, startZ, endX, endY, endZ);
    }

    public BlockIterator(final int x, final int y, final int z, final double startX, final double startY, final double startZ, final double endX, final double endY,
            final double endZ) {
        this.initialize(x, y, z, startX, startY, startZ, endX, endY, endZ);
    }

    public BlockIterator(final double startX, final double startY, final double startZ, final double directionX, final double directionY,
            final double directionZ, final double distance) {
        this.initialize(startX, startY, startZ, directionX, directionY, directionZ, distance);
    }

    public BlockIterator(final int x, final int y, final int z, final double startX, final double startY, final double startZ, final double directionX,
            final double directionY, final double directionZ, final double distance) {
        this.initialize(x, y, z, startX, startY, startZ, directionX, directionY, directionZ, distance);
    }

    public BlockIterator(final double startX, final double startY, final double startZ, final double directionX, final double directionY,
            final double directionZ, final double distance, final boolean normalized) {
        if (normalized) {
            this.initializeNormalized(startX, startY, startZ, directionX, directionY, directionZ, distance);
        } else {
            this.initialize(startX, startY, startZ, directionX, directionY, directionZ, distance);
        }
    }

    public BlockIterator(final int x, final int y, final int z, final double startX, final double startY, final double startZ, final double directionX,
            final double directionY, final double directionZ, final double distance, final boolean normalized) {
        if (normalized) {
            this.initializeNormalized(x, y, z, startX, startY, startZ, directionX, directionY, directionZ, distance);
        } else {
            this.initialize(x, y, z, startX, startY, startZ, directionX, directionY, directionZ, distance);
        }
    }

    public BlockIterator initialize(final double startX, final double startY, final double startZ, final double endX, final double endY,
            final double endZ) {
        return this.initialize(BlockIterator.floor(startX), BlockIterator.floor(startY), BlockIterator.floor(startZ), startX, startY, startZ, endX, endY, endZ);
    }

    public BlockIterator initialize(final int x, final int y, final int z, final double startX, final double startY, final double startZ, final double endX,
            final double endY, final double endZ) {
        double directionX = endX - startX;
        double directionY = endY - startY;
        double directionZ = endZ - startZ;
        final double distance = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        final double fixedDistance = distance == 0. ? Double.NaN : distance;
        directionX /= fixedDistance;
        directionY /= fixedDistance;
        directionZ /= fixedDistance;
        return this.initializeNormalized(x, y, z, startX, startY, startZ, directionX, directionY, directionZ, distance);
    }

    public BlockIterator initialize(final double startX, final double startY, final double startZ, final double directionX, final double directionY,
            final double directionZ, final double distance) {
        return this.initialize(BlockIterator.floor(startX), BlockIterator.floor(startY), BlockIterator.floor(startZ), startX, startY, startZ, directionX, directionY,
                directionZ, distance);
    }

    public BlockIterator initialize(final int x, final int y, final int z, final double startX, final double startY, final double startZ, double directionX,
            double directionY, double directionZ, final double distance) {
        final double signum = Math.signum(distance);
        directionX *= signum;
        directionY *= signum;
        directionZ *= signum;
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);

        if (length == 0.) {
            length = Double.NaN;
        }

        directionX /= length;
        directionY /= length;
        directionZ /= length;
        return this.initializeNormalized(x, y, z, startX, startY, startZ, directionX, directionY, directionZ,
                Math.abs(distance));
    }

    public BlockIterator initializeNormalized(final double startX, final double startY, final double startZ, final double directionX,
            final double directionY, final double directionZ, final double distance) {
        return this.initializeNormalized(BlockIterator.floor(startX), BlockIterator.floor(startY), BlockIterator.floor(startZ), startX, startY, startZ, directionX,
                directionY, directionZ, Math.abs(distance));
    }

    public BlockIterator initializeNormalized(final int x, final int y, final int z, final double startX, final double startY, final double startZ,
            final double directionX, final double directionY, final double directionZ, final double distance) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.tMax = distance;
        this.stepX = directionX < 0. ? -1 : 1;
        this.stepY = directionY < 0. ? -1 : 1;
        this.stepZ = directionZ < 0. ? -1 : 1;
        this.tMaxX = directionX == 0. ? Double.POSITIVE_INFINITY : (x + (this.stepX + 1) / 2 - startX) / directionX;
        this.tMaxY = directionY == 0. ? Double.POSITIVE_INFINITY : (y + (this.stepY + 1) / 2 - startY) / directionY;
        this.tMaxZ = directionZ == 0. ? Double.POSITIVE_INFINITY : (z + (this.stepZ + 1) / 2 - startZ) / directionZ;
        this.tDeltaX = 1. / Math.abs(directionX);
        this.tDeltaY = 1. / Math.abs(directionY);
        this.tDeltaZ = 1. / Math.abs(directionZ);
        this.next = this.ref;
        this.ref[0] = x;
        this.ref[1] = y;
        this.ref[2] = z;
        return this;
    }

    public int[] calculateNext() {
        if (this.tMaxX < this.tMaxY) {
            if (this.tMaxZ < this.tMaxX) {
                if (this.tMaxZ <= this.tMax) {
                    this.z += this.stepZ;
                    // next = new int[] { x, y, z };
                    this.ref[0] = this.x;
                    this.ref[1] = this.y;
                    this.ref[2] = this.z;
                    this.tMaxZ += this.tDeltaZ;
                } else {
                    this.next = null;
                }
            } else {
                if (this.tMaxX <= this.tMax) {
                    if (this.tMaxZ == this.tMaxX) {
                        this.z += this.stepZ;
                        this.tMaxZ += this.tDeltaZ;
                    }

                    this.x += this.stepX;
                    // next = new int[] { x, y, z };
                    this.ref[0] = this.x;
                    this.ref[1] = this.y;
                    this.ref[2] = this.z;
                    this.tMaxX += this.tDeltaX;
                } else {
                    this.next = null;
                }
            }
        } else if (this.tMaxY < this.tMaxZ) {
            if (this.tMaxY <= this.tMax) {
                if (this.tMaxX == this.tMaxY) {
                    this.x += this.stepX;
                    this.tMaxX += this.tDeltaX;
                }

                this.y += this.stepY;
                // next = new int[] { x, y, z };
                this.ref[0] = this.x;
                this.ref[1] = this.y;
                this.ref[2] = this.z;
                this.tMaxY += this.tDeltaY;
            } else {
                this.next = null;
            }
        } else {
            if (this.tMaxZ <= this.tMax) {
                if (this.tMaxX == this.tMaxZ) {
                    this.x += this.stepX;
                    this.tMaxX += this.tDeltaX;
                }

                if (this.tMaxY == this.tMaxZ) {
                    this.y += this.stepY;
                    this.tMaxY += this.tDeltaY;
                }

                this.z += this.stepZ;
                // next = new int[] { x, y, z };
                this.ref[0] = this.x;
                this.ref[1] = this.y;
                this.ref[2] = this.z;
                this.tMaxZ += this.tDeltaZ;
            } else {
                this.next = null;
            }
        }

        return this.next;
    }

    @Override
    public boolean hasNext() {
        return this.next != null;
    }

    @Override
    public int[] next() {
        final int[] next = this.next;

        if (next == null) {
            throw new NoSuchElementException();
        }

        final int[] temp = this.ref;
        this.ref = this.refSwap;
        this.refSwap = temp;
        this.next = this.ref;
        this.calculateNext();
        return next;
    }

    private static int floor(final double value) {
        final int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }
}
