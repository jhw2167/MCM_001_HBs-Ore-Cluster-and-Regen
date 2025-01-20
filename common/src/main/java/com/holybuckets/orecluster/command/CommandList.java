package com.holybuckets.orecluster.command;

//Project imports

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.client.Messager;
import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.orecluster.LoggerProject;
import com.holybuckets.orecluster.core.OreClusterInterface;
import com.holybuckets.orecluster.model.OreClusterInfo;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class CommandList {

    public static final String CLASS_ID = "010";
    private static final String PREFIX = "hbOreClusters";

    public static void register() {
        CommandRegistry.register(LocateClusters::register);
    }

    //1. Locate Clusters
    private static class LocateClusters
    {
        private static LiteralArgumentBuilder<CommandSourceStack> register()
        {
            return Commands.literal(PREFIX)
                .then(Commands.literal("locateClusters")
                .executes(context -> execute( context )) );
        }

        private static int execute(CommandContext<CommandSourceStack> context)
        {
            OreClusterInterface interfacer = OreClusterInterface.getInstance();
            if(interfacer == null)
                return 1;

            Messager messager = Messager.getInstance();

            try {
                ServerPlayer player = context.getSource().getPlayerOrException();
                BlockPos playerPos = player.blockPosition();
                List<OreClusterInfo> data = interfacer.locateOreClusters(
                    player.level(), playerPos, null, 5);

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

    }
    //END COMMAND




    //2. Locate Ore

    /*
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(PREFIX)
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
