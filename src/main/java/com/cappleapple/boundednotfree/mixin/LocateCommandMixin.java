package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.command.LocateSearch;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(LocateCommand.class)
abstract class LocateCommandMixin {
    @Redirect(method = "locateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"))
    private static Pair<BlockPos, Holder<Structure>> boundednotfree$boundStructureSearch(
            ChunkGenerator generator, ServerLevel level, HolderSet<Structure> structures,
            BlockPos origin, int radius, boolean skipKnownStructures) {
        return LocateSearch.findNearestStructure(generator, level, structures, origin, radius, skipKnownStructures);
    }

    @Redirect(method = "locateBiome", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;findClosestBiome3d(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;III)Lcom/mojang/datafixers/util/Pair;"))
    private static Pair<BlockPos, Holder<Biome>> boundednotfree$boundBiomeSearch(
            ServerLevel level, Predicate<Holder<Biome>> biomePredicate, BlockPos origin,
            int radius, int horizontalStep, int verticalStep) {
        return LocateSearch.findClosestBiome(level, biomePredicate, origin, radius, horizontalStep, verticalStep);
    }

    @Redirect(method = "locatePoi", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findClosestWithType(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"))
    private static Optional<Pair<Holder<PoiType>, BlockPos>> boundednotfree$boundPoiSearch(
            PoiManager manager, Predicate<Holder<PoiType>> poiPredicate, BlockPos origin,
            int radius, PoiManager.Occupancy occupancy, CommandSourceStack source,
            ResourceOrTagArgument.Result<PoiType> ignored) {
        return LocateSearch.findClosestPoi(source.getLevel(), manager, poiPredicate, origin, radius, occupancy);
    }
}
