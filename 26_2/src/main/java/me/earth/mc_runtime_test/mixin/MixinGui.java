package me.earth.mc_runtime_test.mixin;

import me.earth.mc_runtime_test.McRuntimeTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void setScreenHook(Screen screen, CallbackInfo ci) {
        if (!McRuntimeTest.screenHook()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (screen instanceof ErrorScreen) {
            mcRuntime$stop();
            throw new RuntimeException("Error Screen " + screen);
        } else if (screen instanceof DeathScreen && player != null) {
            player.respawn();
        }
    }

    @Unique
    private void mcRuntime$stop() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.halt(true);
            Minecraft.getInstance().disconnectWithProgressScreen();
        }

        Minecraft.getInstance().stop();
    }
}
