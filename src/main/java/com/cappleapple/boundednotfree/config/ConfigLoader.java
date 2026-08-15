package com.cappleapple.boundednotfree.config;

import com.cappleapple.boundednotfree.BoundedNotFree;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ConfigLoader {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve(BoundedNotFree.MOD_ID);
    private static final Path FILE = DIRECTORY.resolve("world-layout.json");

    private ConfigLoader() {}

    public static Loaded load() throws IOException {
        Files.createDirectories(DIRECTORY);
        if (Files.notExists(FILE)) writeDefault();
        byte[] bytes = Files.readAllBytes(FILE);
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            LayoutConfig config = GSON.fromJson(reader, LayoutConfig.class);
            if (config == null) throw new IOException("world-layout.json contains JSON null");
            if (config.schemaVersion != 1) throw new IOException("Unsupported world-layout schemaVersion " + config.schemaVersion + "; this build supports schema 1");
            return new Loaded(config, sha256(bytes), FILE);
        } catch (RuntimeException exception) {
            throw new IOException("Could not parse " + FILE + ": " + exception.getMessage(), exception);
        }
    }

    private static void writeDefault() throws IOException {
        try (var input = ConfigLoader.class.getResourceAsStream("/default-world-layout.json")) {
            if (input == null) throw new IOException("Packaged default-world-layout.json is missing");
            Files.copy(input, FILE);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Loaded(LayoutConfig config, String hash, Path path) {}
}
