package com.vanillage.raytraceantixray.config;

import space.arim.dazzleconf.engine.liaison.SubSection;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable runtime configuration loaded through DazzleConf.
 */
public interface PluginConfig {

    default @SubSection Settings settings() {
        return Defaults.SETTINGS;
    }

    default Map<String, @SubSection WorldSettings> worldSettings() {
        return Defaults.WORLD_SETTINGS_MAP;
    }

    static PluginConfig defaults() {
        return Defaults.ROOT;
    }

    interface Settings {
        default boolean debug() {
            return false;
        }

        default @SubSection AntiXray antiXray() {
            return Defaults.SETTINGS_ANTI_XRAY;
        }

        interface AntiXray {
            default long updateTicks() {
                return 2L;
            }

            default long msPerRayTraceTick() {
                return 50L;
            }

            default int rayTraceThreads() {
                return 2;
            }

            default boolean alignRayTraceTicksTo20() {
                return false;
            }

            default @SubSection RayTraceTicksByGamemode rayTraceTicksByGamemode() {
                return Defaults.RAYTRACE_TICKS_BY_GAMEMODE;
            }

            interface RayTraceTicksByGamemode {
                default int survival() {
                    return 4;
                }

                default int adventure() {
                    return 4;
                }

                default int creative() {
                    return 4;
                }

                default int spectator() {
                    return 8;
                }
            }
        }
    }

    interface WorldSettings {
        default @SubSection AntiXray antiXray() {
            return Defaults.WORLD_ANTI_XRAY;
        }

        interface AntiXray {
            default boolean rayTrace() {
                return true;
            }

            default boolean rayTraceThirdPerson() {
                return false;
            }

            default double rayTraceDistance() {
                return 120.0;
            }

            default int maxRayTraceBlockCountPerChunk() {
                return 100;
            }

            default boolean rehideBlocks() {
                return false;
            }

            default double rehideDistance() {
                return Double.POSITIVE_INFINITY;
            }

            default List<String> bypassRehideBlocks() {
                return Collections.emptyList();
            }

            default List<String> rayTraceBlocks() {
                return Collections.emptyList();
            }
        }
    }

    final class Defaults {
        private Defaults() {
        }

        private static final Settings.AntiXray.RayTraceTicksByGamemode RAYTRACE_TICKS_BY_GAMEMODE = new Settings.AntiXray.RayTraceTicksByGamemode() {
        };
        private static final Settings.AntiXray SETTINGS_ANTI_XRAY = new Settings.AntiXray() {
        };
        private static final Settings SETTINGS = new Settings() {
        };
        private static final WorldSettings.AntiXray WORLD_ANTI_XRAY = new WorldSettings.AntiXray() {
        };
        private static final WorldSettings WORLD_SETTINGS = new WorldSettings() {
        };
        private static final Map<String, WorldSettings> WORLD_SETTINGS_MAP = Map.of("default", Defaults.WORLD_SETTINGS);
        private static final PluginConfig ROOT = new PluginConfig() {
        };
    }
}
