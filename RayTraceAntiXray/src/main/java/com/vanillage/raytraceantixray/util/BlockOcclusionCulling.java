package com.vanillage.raytraceantixray.util;

import java.util.function.Consumer;

public final class BlockOcclusionCulling {
    private static final IntArrayConsumer INCREASE_X = c -> c[0]++;
    private static final IntArrayConsumer DECREASE_X = c -> c[0]--;
    private static final IntArrayConsumer INCREASE_Y = c -> c[1]++;
    private static final IntArrayConsumer DECREASE_Y = c -> c[1]--;
    private static final IntArrayConsumer INCREASE_Z = c -> c[2]++;
    private static final IntArrayConsumer DECREASE_Z = c -> c[2]--;
    private static final IntArrayConsumer[] NEARBY_BLOCKS_X_PLANE_Y_POS_Z_POS = new IntArrayConsumer[] { BlockOcclusionCulling.INCREASE_Y,
            BlockOcclusionCulling.INCREASE_Z, BlockOcclusionCulling.DECREASE_Y };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_X_PLANE_Y_POS_Z_NEG = new IntArrayConsumer[] { BlockOcclusionCulling.INCREASE_Y,
            BlockOcclusionCulling.DECREASE_Z, BlockOcclusionCulling.DECREASE_Y };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_X_PLANE_Y_NEG_Z_POS = new IntArrayConsumer[] { BlockOcclusionCulling.DECREASE_Y,
            BlockOcclusionCulling.INCREASE_Z, BlockOcclusionCulling.INCREASE_Y };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_X_PLANE_Y_NEG_Z_NEG = new IntArrayConsumer[] { BlockOcclusionCulling.DECREASE_Y,
            BlockOcclusionCulling.DECREASE_Z, BlockOcclusionCulling.INCREASE_Y };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Y_PLANE_Z_POS_X_POS = new IntArrayConsumer[] { BlockOcclusionCulling.INCREASE_Z,
            BlockOcclusionCulling.INCREASE_X, BlockOcclusionCulling.DECREASE_Z };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Y_PLANE_Z_POS_X_NEG = new IntArrayConsumer[] { BlockOcclusionCulling.INCREASE_Z,
            BlockOcclusionCulling.DECREASE_X, BlockOcclusionCulling.DECREASE_Z };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Y_PLANE_Z_NEG_X_POS = new IntArrayConsumer[] { BlockOcclusionCulling.DECREASE_Z,
            BlockOcclusionCulling.INCREASE_X, BlockOcclusionCulling.INCREASE_Z };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Y_PLANE_Z_NEG_X_NEG = new IntArrayConsumer[] { BlockOcclusionCulling.DECREASE_Z,
            BlockOcclusionCulling.DECREASE_X, BlockOcclusionCulling.INCREASE_Z };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Z_PLANE_X_POS_Y_POS = new IntArrayConsumer[] { BlockOcclusionCulling.INCREASE_Y,
            BlockOcclusionCulling.INCREASE_X, BlockOcclusionCulling.DECREASE_Y /* INCREASE_X, INCREASE_Y, DECREASE_X */ };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Z_PLANE_X_POS_Y_NEG = new IntArrayConsumer[] { BlockOcclusionCulling.DECREASE_Y,
            BlockOcclusionCulling.INCREASE_X, BlockOcclusionCulling.INCREASE_Y /* INCREASE_X, DECREASE_Y, DECREASE_X */ };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Z_PLANE_X_NEG_Y_POS = new IntArrayConsumer[] { BlockOcclusionCulling.INCREASE_Y,
            BlockOcclusionCulling.DECREASE_X, BlockOcclusionCulling.DECREASE_Y /* DECREASE_X, INCREASE_Y, INCREASE_X */ };
    private static final IntArrayConsumer[] NEARBY_BLOCKS_Z_PLANE_X_NEG_Y_NEG = new IntArrayConsumer[] { BlockOcclusionCulling.DECREASE_Y,
            BlockOcclusionCulling.DECREASE_X, BlockOcclusionCulling.INCREASE_Y /* DECREASE_X, DECREASE_Y, INCREASE_X */ };
    private final BlockIteratorFactory blockIteratorFactory;
    private final BlockOcclusionGetter blockOcclusionGetter;
    private final boolean frustumCullingEnabled;

    public BlockOcclusionCulling(final BlockIteratorFactory blockIteratorFactory, final BlockOcclusionGetter blockOcclusionGetter,
            final boolean frustumCullingEnabled) {
        this.blockIteratorFactory = blockIteratorFactory;
        this.blockOcclusionGetter = blockOcclusionGetter;
        this.frustumCullingEnabled = frustumCullingEnabled;
    }

    public boolean isVisible(final int x, final int y, final int z, final double vectorX, final double vectorY, final double vectorZ, final double directionX,
            final double directionY, final double directionZ) {
        final double centerX = x + 0.5;
        final double centerY = y + 0.5;
        final double centerZ = z + 0.5;
        final double differenceX = vectorX - centerX;
        final double differenceY = vectorY - centerY;
        final double differenceZ = vectorZ - centerZ;
        return this.isVisible(x, y, z, centerX, centerY, centerZ, differenceX, differenceY, differenceZ,
                differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ, directionX,
                directionY, directionZ);
    }

    public boolean isVisible(final int x, final int y, final int z, final double centerX, final double centerY, final double centerZ, final double differenceX,
            final double differenceY, final double differenceZ, final double distanceSquared, final double directionX, final double directionY,
            final double directionZ) {
        if (this.frustumCullingEnabled && (differenceX - directionX) * directionX + (differenceY - directionY) * directionY
                + (differenceZ - directionZ) * directionZ > 0.) { // Should actually be (difference - Math.sqrt(3.) *
                                                                  // direction / 2.) * direction.
            return false;
        }

        final double distance = Math.sqrt(distanceSquared);
        final double fixedDistance = distance == 0. ? Double.NaN : distance;
        final BlockIterator blockIterator = this.blockIteratorFactory.getBlockIterator(x, y, z, centerX, centerY, centerZ,
                differenceX / fixedDistance, differenceY / fixedDistance, differenceZ / fixedDistance, distance);
        int[] ray;

        while ((ray = blockIterator.calculateNext()) != null) {
            final int rayX = ray[0];
            final int rayY = ray[1];
            final int rayZ = ray[2];

            if (this.blockOcclusionGetter.isOccludingRay(rayX, rayY, rayZ)
                    && this.checkNearbyBlocks(x, y, z, ray, rayX, rayY, rayZ, differenceX, differenceY, differenceZ)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkNearbyBlocks(final int x, final int y, final int z, final int[] ray, int rayX, int rayY, int rayZ, final double differenceX,
            final double differenceY, final double differenceZ) {
        IntArrayConsumer[] nearbyBlocks;
        IntArrayConsumer increase;
        IntArrayConsumer decrease;
        final double absDifferenceX = Math.abs(differenceX);
        final double absDifferenceY = Math.abs(differenceY);
        final double absDifferenceZ = Math.abs(differenceZ);
        double rayDifferenceX = rayX - x;
        double rayDifferenceY = rayY - y;
        double rayDifferenceZ = rayZ - z;

        if (absDifferenceX > absDifferenceY) {
            if (absDifferenceZ > absDifferenceX) {
                final double factor = BlockOcclusionCulling.divide(differenceZ, rayDifferenceZ);
                rayDifferenceX = BlockOcclusionCulling.multiply(factor, rayDifferenceX) - differenceX;
                rayDifferenceY = BlockOcclusionCulling.multiply(factor, rayDifferenceY) - differenceY;

                if (rayDifferenceX > 0.) {
                    if (rayDifferenceY > 0.) {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_NEG_Y_NEG;
                    } else {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_NEG_Y_POS;
                    }
                } else {
                    if (rayDifferenceY > 0.) {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_POS_Y_NEG;
                    } else {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_POS_Y_POS;
                    }
                }

                if (differenceZ > 0.) {
                    increase = BlockOcclusionCulling.DECREASE_Z;
                    decrease = BlockOcclusionCulling.INCREASE_Z;
                } else {
                    increase = BlockOcclusionCulling.INCREASE_Z;
                    decrease = BlockOcclusionCulling.DECREASE_Z;
                }
            } else {
                final double factor = BlockOcclusionCulling.divide(differenceX, rayDifferenceX);
                rayDifferenceY = BlockOcclusionCulling.multiply(factor, rayDifferenceY) - differenceY;
                rayDifferenceZ = BlockOcclusionCulling.multiply(factor, rayDifferenceZ) - differenceZ;

                if (rayDifferenceY > 0.) {
                    if (rayDifferenceZ > 0.) {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_X_PLANE_Y_NEG_Z_NEG;
                    } else {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_X_PLANE_Y_NEG_Z_POS;
                    }
                } else {
                    if (rayDifferenceZ > 0.) {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_X_PLANE_Y_POS_Z_NEG;
                    } else {
                        nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_X_PLANE_Y_POS_Z_POS;
                    }
                }

                if (differenceX > 0.) {
                    increase = BlockOcclusionCulling.DECREASE_X;
                    decrease = BlockOcclusionCulling.INCREASE_X;
                } else {
                    increase = BlockOcclusionCulling.INCREASE_X;
                    decrease = BlockOcclusionCulling.DECREASE_X;
                }
            }
        } else if (absDifferenceY > absDifferenceZ) {
            final double factor = BlockOcclusionCulling.divide(differenceY, rayDifferenceY);
            rayDifferenceZ = BlockOcclusionCulling.multiply(factor, rayDifferenceZ) - differenceZ;
            rayDifferenceX = BlockOcclusionCulling.multiply(factor, rayDifferenceX) - differenceX;

            if (rayDifferenceZ > 0.) {
                if (rayDifferenceX > 0.) {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Y_PLANE_Z_NEG_X_NEG;
                } else {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Y_PLANE_Z_NEG_X_POS;
                }
            } else {
                if (rayDifferenceX > 0.) {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Y_PLANE_Z_POS_X_NEG;
                } else {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Y_PLANE_Z_POS_X_POS;
                }
            }

            if (differenceY > 0.) {
                increase = BlockOcclusionCulling.DECREASE_Y;
                decrease = BlockOcclusionCulling.INCREASE_Y;
            } else {
                increase = BlockOcclusionCulling.INCREASE_Y;
                decrease = BlockOcclusionCulling.DECREASE_Y;
            }
        } else {
            final double factor = BlockOcclusionCulling.divide(differenceZ, rayDifferenceZ);
            rayDifferenceX = BlockOcclusionCulling.multiply(factor, rayDifferenceX) - differenceX;
            rayDifferenceY = BlockOcclusionCulling.multiply(factor, rayDifferenceY) - differenceY;

            if (rayDifferenceX > 0.) {
                if (rayDifferenceY > 0.) {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_NEG_Y_NEG;
                } else {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_NEG_Y_POS;
                }
            } else {
                if (rayDifferenceY > 0.) {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_POS_Y_NEG;
                } else {
                    nearbyBlocks = BlockOcclusionCulling.NEARBY_BLOCKS_Z_PLANE_X_POS_Y_POS;
                }
            }

            if (differenceZ > 0.) {
                increase = BlockOcclusionCulling.DECREASE_Z;
                decrease = BlockOcclusionCulling.INCREASE_Z;
            } else {
                increase = BlockOcclusionCulling.INCREASE_Z;
                decrease = BlockOcclusionCulling.DECREASE_Z;
            }
        }

        for (int step = 0; step < nearbyBlocks.length; step++) {
            nearbyBlocks[step].accept(ray);

            if (this.blockOcclusionGetter.isOccludingNearby(ray[0], ray[1], ray[2])) {
                continue;
            }

            increase.accept(ray);
            rayX = ray[0];
            rayY = ray[1];
            rayZ = ray[2];

            if (rayX == x && rayY == y && rayZ == z || !this.blockOcclusionGetter.isOccludingNearby(rayX, rayY, rayZ)) {
                return false;
            }

            decrease.accept(ray);
        }

        return true;
    }

    private static double divide(final double dividend, final double divisor) {
        return (divisor == 0. && !Double.isNaN(dividend) ? Math.copySign(1., dividend) : dividend) / divisor;
    }

    private static double multiply(final double factor1, final double factor2) {
        return (factor2 == 0. ? Math.signum(factor1) : factor1) * factor2;
    }

    @FunctionalInterface
    private interface IntArrayConsumer extends Consumer<int[]> {

    }

    @FunctionalInterface
    public interface BlockIteratorFactory {
        BlockIterator getBlockIterator(int x, int y, int z, double startX, double startY, double startZ,
                double directionX, double directionY, double directionZ, double distance);
    }

    @FunctionalInterface
    public interface BlockOcclusionGetter {
        boolean isOccluding(int x, int y, int z);

        default boolean isOccludingRay(final int x, final int y, final int z) {
            return this.isOccluding(x, y, z);
        }

        default boolean isOccludingNearby(final int x, final int y, final int z) {
            return this.isOccluding(x, y, z);
        }
    }
}
