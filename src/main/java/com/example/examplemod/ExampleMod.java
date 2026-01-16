package com.example.examplemod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("examplemod")
public class ExampleMod
{
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();
    public static final DragonLocationLogger DRAGON_LOGGER = new DragonLocationLogger();

    static {
        try {
            MixinBootstrap.init();
            Mixins.addConfiguration("examplemod.mixins.json");
            LOGGER.info("Initialized Sponge Mixin and loaded examplemod.mixins.json");
        } catch (Throwable throwable) {
            LOGGER.error("Failed to bootstrap Sponge Mixin", throwable);
        }
    }

    private final AtomicBoolean worldGreetingSent = new AtomicBoolean(false);
    private static volatile DragonTargetSnapshot lastDragonTarget;

    public ExampleMod() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        // some preinit code
        LOGGER.info("HELLO FROM PREINIT");
        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // do something that can only be done on the client
        LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));
    }
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("HELLO from server starting");
        CommandDispatcher<CommandSource> dispatcher = event.getServer().getCommandManager().getDispatcher();
        dispatcher.register(Commands.literal("dragonlog")
                .requires(source -> source.hasPermissionLevel(2))
                .then(Commands.literal("start").executes(context -> {
                    DRAGON_LOGGER.startLogging();
                    context.getSource().sendFeedback(new StringTextComponent(
                            "Ender dragon location logging enabled."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").executes(context -> {
                    DRAGON_LOGGER.stopLogging();
                    context.getSource().sendFeedback(new StringTextComponent(
                            "Ender dragon location logging disabled."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("target").executes(context -> sendDragonTarget(context.getSource())))
                .then(Commands.literal("get").executes(context -> sendLogFilePath(context.getSource())))
                .then(Commands.literal("status").executes(context -> reportLoggerStatus(context.getSource())))
                .executes(context -> reportLoggerStatus(context.getSource())));
    }

    private static int reportLoggerStatus(CommandSource source) {
        boolean enabled = DRAGON_LOGGER.isLoggingEnabled();
        source.sendFeedback(new StringTextComponent(
                "Ender dragon location logging is " + (enabled ? "ON" : "OFF") + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendLogFilePath(CommandSource source) {
        String path = DRAGON_LOGGER.getLogFile().toAbsolutePath().toString();
        source.sendFeedback(new StringTextComponent("Dragon log file -> " + path), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendDragonTarget(CommandSource source) {
        DragonTargetSnapshot snapshot = lastDragonTarget;
        if (snapshot == null) {
            source.sendFeedback(new StringTextComponent("No dragon target data recorded yet."), false);
            return 0;
        }

        if (snapshot.target == null) {
            source.sendFeedback(new StringTextComponent(
                    "Dragon target is currently undefined (phase does not expose one). Tick " + snapshot.tick), false);
            return 0;
        }

        String message = String.format(Locale.ROOT,
                "Dragon target [%s @ tick %d] -> (%.2f, %.2f, %.2f)",
                snapshot.dimensionKey, snapshot.tick, snapshot.target.x, snapshot.target.y, snapshot.target.z);
        source.sendFeedback(new StringTextComponent(message), false);
        return Command.SINGLE_SUCCESS;
    }

    public static void updateDragonTarget(EnderDragonEntity dragon, @Nullable Vector3d target) {
        if (dragon == null || dragon.world.isRemote()) {
            return;
        }
        RegistryKey<World> dimensionKey = dragon.world.func_234923_W_();
        ResourceLocation dimensionName = dimensionKey.func_240901_a_();
        lastDragonTarget = new DragonTargetSnapshot(dragon.world.getGameTime(),
                dimensionName.toString(), target);
    }

    private static class DragonTargetSnapshot {
        private final long tick;
        private final String dimensionKey;
        @Nullable
        private final Vector3d target;

        private DragonTargetSnapshot(long tick, String dimensionKey, @Nullable Vector3d target) {
            this.tick = tick;
            this.dimensionKey = dimensionKey;
            this.target = target;
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player.getEntityWorld().isRemote()) {
            return;
        }

        if (worldGreetingSent.compareAndSet(false, true)) {
            player.sendMessage(new StringTextComponent("ExampleMod says hi! Enjoy these starter diamonds."), player.getUniqueID());
            ItemStack diamonds = new ItemStack(Items.DIAMOND, 3);
            boolean added = player.inventory.addItemStackToInventory(diamonds);
            if (!added) {
                player.dropItem(diamonds, false);
            }
            LOGGER.info("Gave {} a welcome package", player.getName().getString());
        }
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
            LOGGER.info("HELLO from Register Block");
        }
    }
}
