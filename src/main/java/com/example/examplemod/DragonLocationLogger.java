package com.example.examplemod;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple CSV logger for recording Ender Dragon positions.
 */
public class DragonLocationLogger {
    private static final Logger LOGGER = LogManager.getLogger("ExampleMod/DragonLocationLogger");
    private final AtomicBoolean loggingEnabled = new AtomicBoolean(false);
    private final Path logFile = FMLPaths.GAMEDIR.get().resolve("dragon_positions.csv");
    private BufferedWriter writer;

    public boolean isLoggingEnabled() {
        return loggingEnabled.get();
    }

    public Path getLogFile() {
        return logFile;
    }

    public synchronized void startLogging() {
        if (loggingEnabled.compareAndSet(false, true)) {
            openWriterIfNeeded(true);
            LOGGER.info("Ender dragon position logging enabled -> {}", logFile.toAbsolutePath());
        }
    }

    public synchronized void stopLogging() {
        if (loggingEnabled.compareAndSet(true, false)) {
            closeWriter();
            LOGGER.info("Ender dragon position logging disabled");
        }
    }

    public synchronized void logPosition(World world, EnderDragonEntity dragon) {
        if (!loggingEnabled.get() || world == null || world.isRemote()) {
            return;
        }

        if (writer == null) {
            openWriterIfNeeded(false);
            if (writer == null) {
                LOGGER.warn("Failed to open dragon position log file; disabling logging.");
                loggingEnabled.set(false);
                return;
            }
        }

        try {
            long tick = world.getGameTime();
            double x = dragon.getPosX();
            double y = dragon.getPosY();
            double z = dragon.getPosZ();
            String line = String.format(Locale.ROOT, "%d,%.5f,%.5f,%.5f", tick, x, y, z);
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException ioException) {
            LOGGER.error("Failed to write dragon position entry", ioException);
            loggingEnabled.set(false);
            closeWriter();
        }
    }

    private void openWriterIfNeeded(boolean ensureHeader) {
        if (writer != null) {
            return;
        }
        try {
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
            boolean fileExists = Files.exists(logFile);
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (ensureHeader && !fileExists) {
                writer.write("tick,x,y,z");
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ioException) {
            LOGGER.error("Failed to open dragon position log file {}", logFile.toAbsolutePath(), ioException);
            writer = null;
        }
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ioException) {
            LOGGER.warn("Error while closing dragon position log file", ioException);
        } finally {
            writer = null;
        }
    }
}

