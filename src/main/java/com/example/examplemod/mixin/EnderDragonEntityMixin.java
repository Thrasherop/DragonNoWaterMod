package com.example.examplemod.mixin;

import com.example.examplemod.ExampleMod;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {

    private static final Logger MIXIN_LOGGER = LogManager.getLogger("ExampleMod/EnderDragonMixin");
    @Unique
    private boolean examplemod$loggedTick;
    @Unique
    private boolean examplemod$loggedWaterOverride;

    // public boolean isPushedByWater() {
    //     if (!examplemod$loggedWaterOverride) {
    //         MIXIN_LOGGER.info("EnderDragonEntity mixin override in effect: isPushedByWater -> false");
    //         examplemod$loggedWaterOverride = true;
    //     }
    //     return false;
    // }

    @Inject(method = "livingTick()V", at = @At("RETURN"))
    private void examplemod$logInitialTick(CallbackInfo ci) {
        if (!examplemod$loggedTick) {
            MIXIN_LOGGER.info("EnderDragonEntity mixin is active (livingTick reached).");
            examplemod$loggedTick = true;
        }
        EnderDragonEntity dragon = (EnderDragonEntity)(Object) this;
        ExampleMod.DRAGON_LOGGER.logPosition(dragon.world, dragon);
    }
}

// to run:
// cd "C:\\Users\\ultra\\Desktop\\vscode\\minecraft decompiling\\forge_mdk_1.16.1"; powershell -NoProfile -ExecutionPolicy Bypass -File .\\runClient.ps1
