package com.cappleapple.boundednotfree.compatcheck;

import com.cappleapple.boundednotfree.config.LayoutConfig;
import com.cappleapple.boundednotfree.compat.GenesisPreviewWorker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.cappleapple.boundednotfree.compatcheck.PreviewCompatibilityChecks.*;

public final class GenesisClientChecks {
    private static int phase;
    private static int waitTicks;
    private static Screen genesis;
    private static long started = System.nanoTime();

    static void register() { NeoForge.EVENT_BUS.register(GenesisClientChecks.class); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (phase == 99) return;
            check(System.nanoTime() - started < 180_000_000_000L, "Client test timed out");
            if (phase == 0 && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                if (!ModList.get().isLoaded("genesis")) {
                    recordResult("PASS: client without Genesis");
                    System.out.println("BNF_CLIENT_WITHOUT_GENESIS_PASS");
                    finish(minecraft);
                    return;
                }
                write(fixture());
                phase = 1;
                CreateWorldScreen.openFresh(minecraft, minecraft.screen);
            } else if (phase == 1 && minecraft.screen instanceof CreateWorldScreen parent) {
                parent.getUiState().setSeed("123456789");
                genesis = (Screen)Class.forName("net.alkeari.genesis.client.screen.GenesisScreen")
                        .getConstructor(CreateWorldScreen.class, WorldCreationContext.class)
                        .newInstance(parent, parent.getUiState().getSettings());
                phase = 2;
                minecraft.setScreen(genesis);
            } else if (phase == 2 && ++waitTicks > 40) {
                verifyFixture(true);
                Object worker = workers()[0];
                Object layout = get(worker, "boundednotfree$layout");
                check(((com.cappleapple.boundednotfree.runtime.PreviewLayout)layout).plan().seed()
                        == (123456789L ^ fixture().dimensions.get("minecraft:overworld").layoutSalt), "Wrong preview seed");
                try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    screenshot.writeToFile(minecraft.gameDirectory.toPath().resolve("genesis-preview.png"));
                }
                ((EditBox)get(genesis, "seedField")).setValue("987654321");
                invoke(genesis, "applySeedFromField");
                verifyFixture(false);
                check(get(workers()[0], "boundednotfree$layout") != layout, "Seed change retained stale plan");
                check(((com.cappleapple.boundednotfree.runtime.PreviewLayout)get(workers()[0], "boundednotfree$layout"))
                        .plan().seed() == (987654321L ^ fixture().dimensions.get("minecraft:overworld").layoutSalt), "Seed was not refreshed");

                LayoutConfig changed = fixture();
                changed.dimensions.get("minecraft:overworld").customLayoutSeed = 42L;
                changed.dimensions.get("minecraft:overworld").outsideMode = "OCEAN";
                changed.dimensions.get("minecraft:overworld").outsideSelectors.add("minecraft:deep_ocean");
                write(changed);
                invoke(genesis, "startPreviewThread");
                check(sample(workers()[0], 500, -64).equals("minecraft:deep_ocean"), "Config was not reloaded");
                check(((com.cappleapple.boundednotfree.runtime.PreviewLayout)get(workers()[0], "boundednotfree$layout"))
                        .plan().seed() == 42L, "Custom seed was ignored");

                LayoutConfig filters = fixture();
                var filterDimension = filters.dimensions.get("minecraft:overworld");
                filterDimension.rimEnabled = false;
                filterDimension.biomeLayout = "VANILLA";
                filterDimension.biomeFilterMode = "WHITELIST";
                filterDimension.biomeFilter.add("minecraft:desert");
                filterDimension.fallbackBiome = "minecraft:desert";
                write(filters);
                invoke(genesis, "startPreviewThread");
                check(sample(workers()[0], 96, -64).equals("minecraft:desert"), "Biome filter was ignored");

                LayoutConfig reservations = fixture();
                var reservationDimension = reservations.dimensions.get("minecraft:overworld");
                reservationDimension.rimEnabled = false;
                reservationDimension.biomeLayout = "VANILLA";
                var rule = new LayoutConfig.BiomeRule();
                rule.selector = "minecraft:mushroom_fields";
                rule.minInstances = 1;
                rule.maxDistance = 10000;
                rule.maxEdgeDistance = 10000;
                rule.minArea = 4096;
                reservationDimension.requiredBiomes.add(rule);
                write(reservations);
                invoke(genesis, "startPreviewThread");
                var reservationPlan = ((com.cappleapple.boundednotfree.runtime.PreviewLayout)get(workers()[0], "boundednotfree$layout")).plan();
                check(!reservationPlan.biomeReservations().isEmpty(), "Required biome was not reserved");
                var reservation = reservationPlan.biomeReservations().getFirst();
                check(sample(workers()[0], (int)reservation.x() + 96, (int)reservation.z() - 64)
                        .equals("minecraft:mushroom_fields"), "Required biome reservation was ignored");

                changed.dimensions.get("minecraft:overworld").enabled = false;
                write(changed);
                invoke(genesis, "startPreviewThread");
                check(((com.cappleapple.boundednotfree.runtime.PreviewLayout)get(workers()[0], "boundednotfree$layout"))
                        .plan() == null, "Disabled layout remained active");
                write(new LayoutConfig());
                invoke(genesis, "startPreviewThread");
                check(((com.cappleapple.boundednotfree.runtime.PreviewLayout)get(workers()[0], "boundednotfree$layout"))
                        .plan() == null, "Missing dimension remained active");
                invoke(genesis, "stopPreviewThread");
                check(get(genesis, "boundednotfree$layout") == null, "Closed preview retained its plan");
                recordResult("PASS: Genesis preview, seed/config refresh, filters, reservations, disabled/missing, cleanup");
                System.out.println("BNF_GENESIS_CLIENT_PASS boundary rim radial_bands worker_threads seed_refresh config_reload custom_seed filters reservations disabled missing cleanup");
                finish(minecraft);
            }
        } catch (Throwable failure) {
            recordResult("FAIL: " + failure);
            failure.printStackTrace();
            System.out.println("BNF_COMPAT_CHECK_FAILED");
            finish(minecraft);
        }
    }

    private static void verifyFixture(boolean requireCache) throws Exception {
        Object[] workers = workers();
        check(workers.length > 0, "Genesis did not create preview workers");
        for (Object worker : workers) {
            check(worker instanceof GenesisPreviewWorker, "Optional worker mixin was not applied");
            check(sample(worker, 96, -64).equals("minecraft:desert"), "Core radial band missing");
            check(sample(worker, 336, -64).equals("minecraft:frozen_peaks"), "Required rim missing");
            check(sample(worker, 500, -64).equals("minecraft:the_void"), "Outside void missing");
        }
        if (!requireCache) return;
        Object cache = get(genesis, "tileCache");
        String cached = (String)cache.getClass().getMethod("getName", int.class, int.class).invoke(cache, 31, -4);
        check(cached.equals("biome.minecraft.the_void"), "Genesis tile cache did not receive constrained samples: " + cached);
    }

    private static Object[] workers() throws Exception { return (Object[])get(genesis, "previewThreads"); }
    private static String sample(Object worker, int x, int z) throws Exception {
        Method method = worker.getClass().getDeclaredMethod("sampleBiome", int.class, int.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked") Holder<Biome> biome = (Holder<Biome>)method.invoke(worker, Math.floorDiv(x, 4), Math.floorDiv(z, 4));
        return biome.unwrapKey().orElseThrow().location().toString();
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void invoke(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(object);
    }
    private static void finish(Minecraft minecraft) {
        phase = 99;
        minecraft.stop();
    }
}