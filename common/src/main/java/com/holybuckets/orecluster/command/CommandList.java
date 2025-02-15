package com.holybuckets.orecluster.command;

//Project imports

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.console.Messager;
import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.core.OreClusterInterface;
import com.holybuckets.orecluster.core.model.OreClusterInfo;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class CommandList {

    public static final String CLASS_ID = "010";
    private static final String PREFIX = "hbOreClusters";

    public static void register() {
        CommandRegistry.register(LocateClusters::noArgs);
        CommandRegistry.register(LocateClusters::limitCount);
        CommandRegistry.register(LocateClusters::limitCountSpecifyBlockType);
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
            OreClusterInterface interfacer = OreClusterInterface.getInstance();
            if(interfacer == null)
                return 1;

            Messager messager = Messager.getInstance();

            if(count == -1)
                count = 5;

            Block block = null;
            if(blockType != null && !blockType.isEmpty()) {
                block = HBUtil.BlockUtil.blockNameToBlock(blockType);
                if(block == null || block.equals(Blocks.AIR) ) {
                    messager.sendChat(source.getPlayer(), "Block type not found: " + blockType);
                    return 1;
                }
            }

            try {
                ServerPlayer player = source.getPlayerOrException();
                BlockPos playerPos = player.blockPosition();
                List<OreClusterInfo> data = interfacer.locateOreClusters(
                    player.level(), playerPos, block, count);

                for(OreClusterInfo cluster : data) {
                    messager.sendChat(player, formatClusterMessage(cluster));
                }
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
            String ore = HBUtil.BlockUtil.blockToString(cluster.oreType);
                ore = ore.substring( ore.lastIndexOf(":") + 1 );
            String pos = HBUtil.BlockUtil.positionToString( cluster.position );
            return "Cluster: " + ore + " at " + pos;
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




    //2. Locate Ore

    /*
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        return Commands.literal(PREFIX)
            .then(Commands.argument("command", StringArgumentType.string())
                .executes(context -> execute(context, ""))
            .then(Commands.argument("arg1", StringArgumentType.string())
                .executes(context -> execute(context, ""))
            .then(Commands.argument("arg2", StringArgumentType.string())
                .executes(context -> execute(context, ""))
            .then(Commands.argument("arg3", StringArgumentType.string())
                .executes(context -> execute(context, ""))
            .then(Commands.argument("arg4", StringArgumentType.string())
                .executes(context -> execute(context, "")))))))
            .executes(context -> execute(context, "")));

    }
    */



}
