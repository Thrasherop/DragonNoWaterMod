package com.example.examplemod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.text.StringTextComponent;
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
