package com.cappleapple.boundednotfree.command;

import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class WorldLayoutCommands {
    private WorldLayoutCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("worldlayout")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info").executes(context -> {
                    LayoutRuntime.sendInfo(context.getSource());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("validate").executes(context -> {
                    LayoutRuntime.sendValidation(context.getSource());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("compat").executes(context -> {
                    LayoutRuntime.sendCompatibility(context.getSource());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("preview").executes(context -> {
                    LayoutRuntime.exportPreview(context.getSource());
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
