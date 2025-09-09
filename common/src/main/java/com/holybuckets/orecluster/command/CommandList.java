package com.holybuckets.orecluster.command;

//Project imports

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.core.OreClusterApi;
import com.holybuckets.orecluster.core.OreClusterManager;
import com.holybuckets.orecluster.core.model.OreClusterInfo;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CommandList {

    public static final String CLASS_ID = "010";
    private static final String PREFIX = "hbOreClusters";

    public static void register() {
        CommandRegistry.register(LocateClusters::noArgs);
        CommandRegistry.register(LocateClusters::limitCount);
        CommandRegistry.register(LocateClusters::limitCountSpecifyBlockType);
        CommandRegistry.register(GetConfig::noArgs);
        CommandRegistry.register(GetConfig::withConfigId);
        CommandRegistry.register(GetConfig::withConfigIdAndBiomes);
        CommandRegistry.register(AddCluster::register);
        CommandRegistry.register(TriggerRegen::register);
        CommandRegistry.register(HealthCheck::register);
    }

    //1. Locate Clusters
    private static class LocateClusters
    {
        // Register the base command with no arguments
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .executes(context -> execute(context.getSource(), -1, null)) // Default case (no args)
                );

        }

        // Register command with count argument
        private static LiteralArgumentBuilder<CommandSourceStack> limitCount() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int count = IntegerArgumentType.getInteger(context, "count");
                            return execute(context.getSource(), count, null);
                        })
                    )
            );
        }

        // Register command with both count and blockType OR just blockType
        private static LiteralArgumentBuilder<CommandSourceStack> limitCountSpecifyBlockType() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .then(Commands.argument("blockType", StringArgumentType.string())
                            .executes(context -> {
                                int count = IntegerArgumentType.getInteger(context, "count");
                                String blockType = StringArgumentType.getString(context, "blockType");
                                return execute(context.getSource(), count, blockType);
                            })
                        )
                    )
                    .then(Commands.argument("blockType", StringArgumentType.string())
                        .executes(context -> {
                            String blockType = StringArgumentType.getString(context, "blockType");
                            return execute(context.getSource(), -1, blockType);
                        })
                    )
            );
        }


        private static int execute(CommandSourceStack source, int count, String blockType)
        {
            OreClusterApi api = OreClusterApi.getInstance();
            if(api == null) {
                source.sendFailure(Component.literal("oreClusterApi not initialized at this time"));
                return 1;
            }



            if(count == -1)
                count = 5;

            Block block = null;
            if(blockType != null && !blockType.isEmpty()) {
                block = HBUtil.BlockUtil.blockNameToBlock(blockType);
                if(block == null || block.equals(Blocks.AIR) ) {
                    source.sendFailure(Component.literal("Invalid block type: " + blockType));
                    return 1;
                }
            }

            try {
                ServerPlayer player = source.getPlayerOrException();
                BlockPos playerPos = player.blockPosition();
                List<OreClusterInfo> data = api.locateOreClusters(
                    player.level(), playerPos, block, count);

                MutableComponent response = Component.literal("Found Clusters: ");
                for(OreClusterInfo cluster : data) {
                    response.append(formatClusterMessage(cluster));
                }

                source.sendSuccess(() -> response, true);
            }
            catch (Exception e) {
                LoggerProject.logError("010002", "Locate Clusters Command exception: ", e.getMessage());
                return 1;
            }

            LoggerProject.logDebug("010001", "Locate Clusters Command");
            return 0;
        }

        private static String formatClusterMessage(OreClusterInfo cluster)
        {
            String ore = HBUtil.BlockUtil.blockToString(cluster.oreType.getBlock());
                ore = ore.substring( ore.lastIndexOf(":") + 1 );
            String pos = HBUtil.BlockUtil.positionToString( cluster.position );
            return "\n" + ore + " at " + pos;
        }

        private static Object getArgument(CommandContext<CommandSourceStack> context, String name, Class<?> type)
        {
            try {
                return context.getArgument(name, type);
            } catch (Exception e) {
                return null;
            }
        }

    }
    //END COMMAND


    //2. GET config
    //Returns the general config
    private static class GetConfig
    {
        // Register the base command with no arguments
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("config")
                    .executes(context -> execute(context.getSource(), null, false))
                );
        }

        // Register command with configId argument
        private static LiteralArgumentBuilder<CommandSourceStack> withConfigId() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("config")
                    .then(Commands.argument("configId", StringArgumentType.string())
                        .executes(context -> {
                            String configId = StringArgumentType.getString(context, "configId");
                            return execute(context.getSource(), configId, false);
                        })
                    )
                );
        }

        // Register command with configId and showBiomes argument
        private static LiteralArgumentBuilder<CommandSourceStack> withConfigIdAndBiomes() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("config")
                    .then(Commands.argument("configId", StringArgumentType.string())
                    .then(Commands.argument("showBiomes", BoolArgumentType.bool())
                        .executes(context -> {
                            String configId = StringArgumentType.getString(context, "configId");
                            boolean showBiomes = BoolArgumentType.getBool(context, "showBiomes");
                            return execute(context.getSource(), configId, showBiomes);
                        })
                    ))
                );
        }


        private static int execute(CommandSourceStack source, String configId, boolean showBiomes) {
            try {
                OreClusterApi api = OreClusterApi.getInstance();
                if(api == null) {
                    source.sendFailure(Component.literal("oreClusterApi not initialized at this time"));
                    return 1;
                }

                JsonObject config = api.getConfig(configId, showBiomes);
                if(config == null) {
                    source.sendFailure(Component.literal("No config found" + (configId != null ? " for id: " + configId : "")));
                    return 1;
                }


                String header = config.getAsJsonPrimitive("header").getAsString();
                source.sendSystemMessage(Component.literal(header));
                for(JsonElement elem : config.getAsJsonArray("value"))
                {
                    JsonObject obj = elem.getAsJsonObject();
                    StringBuilder s = new StringBuilder( s(obj, "header") );
                    for(String key : obj.keySet()) {
                        if(key.equals("header")) continue;
                        s.append("\n  ").append(key).append(": ").append(s(obj, key));
                    }
                    s.append("\n");
                    source.sendSystemMessage(Component.literal(s.toString()));
                }
                source.sendSystemMessage(Component.literal("---\n"));

                MutableComponent response = Component.literal("command /hbOreClusters config terminated succesfully: \n");
                source.sendSuccess(() -> response, true);

            } catch (Exception e) {
                LoggerProject.logError("010003", "Get Config Command exception: " + e.getMessage());
                return 1;
            }

            LoggerProject.logDebug("010004", "Get Config Command executed successfully");
            return 0;
        }
    }


    //4. TRIGGER REGEN
    private static class TriggerRegen {
        private static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("triggerRegen")
                    .executes(context -> execute(context.getSource(), null, null))
                    .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                    .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                        .executes(context -> execute(
                            context.getSource(),
                            IntegerArgumentType.getInteger(context, "chunkX"),
                            IntegerArgumentType.getInteger(context, "chunkZ")
                        ))
                    )));
        }

        private static int execute(CommandSourceStack source, Integer chunkX, Integer chunkZ) {
            try {
                OreClusterApi api = OreClusterApi.getInstance();
                if(api == null) {
                    source.sendFailure(Component.literal("oreClusterApi not initialized at this time"));
                    return 1;
                }

                if(chunkX == null || chunkZ == null) {
                    // Global regen
                    api.triggerRegen();
                    source.sendSuccess(() -> Component.literal("Global regeneration triggered"), true);
                } else {
                    // Single chunk regen
                    ServerPlayer player = source.getPlayerOrException();
                    String chunkId = HBUtil.ChunkUtil.getId(chunkX, chunkZ);
                    try {
                        api.triggerRegen(player.level(), chunkId);
                        source.sendSuccess(() -> Component.literal("Regeneration triggered for chunk: " + chunkId), true);
                    } catch (Exception e) {
                        source.sendFailure(Component.literal("Failed to trigger regeneration for chunk: " + chunkId));
                        return 1;
                    }
                }

            } catch (Exception e) {
                LoggerProject.logError("010007", "Trigger Regen Command exception: " + e.getMessage());
                return 1;
            }

            LoggerProject.logDebug("010008", "Trigger Regen Command executed successfully");
            return 0;
        }
    }

    //3. ADD CLUSTER
    private static class AddCluster {
        private static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("addCluster")
                    .then(Commands.argument("clusterConfigId", StringArgumentType.string())
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(context -> execute(
                            context.getSource(),
                            StringArgumentType.getString(context, "clusterConfigId"),
                            IntegerArgumentType.getInteger(context, "x"),
                            IntegerArgumentType.getInteger(context, "y"),
                            IntegerArgumentType.getInteger(context, "z")
                        ))
                    )))));
        }

        private static int execute(CommandSourceStack source, String configId, int x, int y, int z) {
            try {
                OreClusterApi api = OreClusterApi.getInstance();
                if(api == null) {
                    source.sendFailure(Component.literal("oreClusterApi not initialized at this time"));
                    return 1;
                }

                ServerPlayer player = source.getPlayerOrException();
                BlockPos pos = new BlockPos(x, y, z);
                boolean success = api.addCluster(player.level(), configId, pos);

                if(success) {
                    source.sendSuccess(() -> Component.literal("Successfully added cluster"), true);
                } else {
                    source.sendFailure(Component.literal("Failed to add cluster, check logs for more info"));
                    return 1;
                }

            } catch (Exception e) {
                LoggerProject.logError("010005", "Add Cluster Command exception: " + e.getMessage());
                source.sendFailure(Component.literal("Failed to add cluster, check logs for more info"));
                return 1;
            }

            LoggerProject.logDebug("010006", "Add Cluster Command executed successfully");
            return 0;
        }
    }



    //5. HEALTH CHECK
    private static class HealthCheck {
        private static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("healthCheck")
                    .executes(context -> execute(context.getSource(), null))
                    .then(Commands.argument("dimensionId", StringArgumentType.string())
                        .executes(context -> execute(
                            context.getSource(),
                            StringArgumentType.getString(context, "dimensionId")
                        ))
                    ));
        }

        private static int execute(CommandSourceStack source, String dimensionId) {
            try {
                OreClusterApi api = OreClusterApi.getInstance();
                if(api == null) {
                    source.sendFailure(Component.literal("oreClusterApi not initialized at this time"));
                    return 1;
                }

                source.sendSuccess(() -> Component.literal("Health Check Results:"), true);

                // Get all available level IDs
                List<String> levelIds = GeneralConfig.getInstance().getLevels().keySet().stream().toList();

                if (dimensionId != null) {
                    // Try to get specific level
                    LevelAccessor level = HBUtil.LevelUtil.toLevel(HBUtil.LevelUtil.LevelNameSpace.SERVER, dimensionId);
                    if (level == null) {
                        source.sendFailure(Component.literal("Dimension ID not found: " + dimensionId));
                        source.sendSuccess(() -> Component.literal("Available dimensions: \n  " + String.join("\n  ", levelIds)), false);
                        return 1;
                    }
                    OreClusterManager manager = OreClusterManager.getManager(level);
                    JsonObject healthCheck = api.healthCheckStatistics(manager);
                    if (healthCheck != null) {
                        source.sendSuccess(() -> Component.literal("Statistics for dimension " + dimensionId + ":"), false);
                        source.sendSuccess(() -> Component.literal(healthCheck.toString()), false);
                        LoggerProject.logInfo("010012", healthCheck.toString());
                    }
                } else {
                    // Get stats for all levels
                    for (String levelId : levelIds) {
                        LevelAccessor level = HBUtil.LevelUtil.toLevel(HBUtil.LevelUtil.LevelNameSpace.SERVER, levelId);
                        if (level != null) {
                            OreClusterManager manager = OreClusterManager.getManager(level);
                            JsonObject healthCheck = api.healthCheckStatistics(manager);
                            if (healthCheck != null) {
                                source.sendSuccess(() -> Component.literal("Statistics for dimension " + levelId + ":"), false);
                                source.sendSuccess(() -> Component.literal(healthCheck.toString()), false);
                                LoggerProject.logInfo("010013", healthCheck.toString());
                            }
                        }
                    }
                }

                LoggerProject.logInfo("010011", "Health Check command completed successfully");

            } catch (Exception e) {
                LoggerProject.logError("010009", "Health Check Command exception: " + e.getMessage());
                return 1;
            }

            LoggerProject.logDebug("010010", "Health Check Command executed successfully");
            return 0;
        }
    }

    private static String s(JsonObject object, String property) {
        if(!object.has(property) || object.get(property) == null || !object.get(property).isJsonPrimitive()) {
            return "null";
        }
        return object.getAsJsonPrimitive(property).getAsString();
    }


}
//END CLASS COMMANDLIST
