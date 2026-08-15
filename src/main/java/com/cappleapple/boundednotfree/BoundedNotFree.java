package com.cappleapple.boundednotfree;

import com.cappleapple.boundednotfree.command.WorldLayoutCommands;
import com.cappleapple.boundednotfree.config.BootstrapConfig;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BoundedNotFree.MOD_ID)
public final class BoundedNotFree {
    public static final String MOD_ID = "boundednotfree";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BoundedNotFree(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BootstrapConfig.SPEC);
        NeoForge.EVENT_BUS.register(LayoutRuntime.class);
        NeoForge.EVENT_BUS.register(WorldLayoutCommands.class);
    }
}
