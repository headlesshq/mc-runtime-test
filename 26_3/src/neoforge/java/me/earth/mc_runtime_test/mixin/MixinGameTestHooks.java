package me.earth.mc_runtime_test.mixin;

import me.earth.mc_runtime_test.McRuntimeTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows the test harness to run GameTests in installed Forge/NeoForge clients. */
@Mixin(targets = "net.neoforged.neoforge.gametest.GameTestHooks", remap = false)
public abstract class MixinGameTestHooks {
    @Inject(method = "isGametestEnabled", at = @At("HEAD"), cancellable = true)
    private static void enableRuntimeTests(CallbackInfoReturnable<Boolean> cir) {
        // Both loaders gate their normal GameTest ticker behind development
        // mode. The enableGameTest property alone cannot override production.
        if (McRuntimeTest.RUN_GAME_TESTS) {
            cir.setReturnValue(true);
        }
    }
}
