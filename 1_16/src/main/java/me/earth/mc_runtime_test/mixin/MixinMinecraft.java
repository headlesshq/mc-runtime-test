package me.earth.mc_runtime_test.mixin;

import me.earth.mc_runtime_test.McRuntimeTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Nullable public ClientLevel level;
    @Shadow @Nullable public Screen screen;
    @Shadow @Nullable private IntegratedServer singleplayerServer;
    @Shadow private volatile boolean running;

    @Unique
    private boolean mcRuntimeTest$startedLoadingSPWorld = false;
    @Unique
    private boolean mcRuntimeTest$worldCreationStarted = false;

    @Shadow
    public abstract @Nullable Overlay getOverlay();

    @Shadow public abstract void setScreen(@Nullable Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void setScreenHook(Screen screen, CallbackInfo ci) {
        if (!McRuntimeTest.screenHook()) {
            return;
        }

        if (screen instanceof ErrorScreen) {
            running = false;
            throw new RuntimeException("Error Screen " + screen);
        } else if (screen instanceof DeathScreen && player != null) {
            player.respawn();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickHook(CallbackInfo ci) {
        if (!McRuntimeTest.tickHook()) {
            return;
        }

        if (getOverlay() == null) {
            if (!mcRuntimeTest$startedLoadingSPWorld && getOverlay() == null) {
                setScreen(CreateWorldScreen.create(new SelectWorldScreen(new TitleScreen())));
                mcRuntimeTest$startedLoadingSPWorld = true;
            } else if (!mcRuntimeTest$worldCreationStarted && screen instanceof ICreateWorldScreen) {
                ((ICreateWorldScreen) screen).invokeOnCreate();
                mcRuntimeTest$worldCreationStarted = true;
            }
        } else {
            LOGGER.info("Waiting for overlay to disappear...");
        }

        if (player != null && level != null) {
            if (screen == null) {
                if (!level.getChunk(SectionPos.blockToSectionCoord((int) player.getX()), SectionPos.blockToSectionCoord((int) player.getZ())).isEmpty()) {
                    if (player.tickCount < 100) {
                        LOGGER.info("Waiting " + (100 - player.tickCount) + " ticks before testing...");
                    } else {
                        LOGGER.info("Test successful!");
                        assertTrue(true);
                        running = false;
                    }
                } else {
                    LOGGER.info("Players chunk not yet loaded, " + player + ": cores: " + Runtime.getRuntime().availableProcessors()
                            + ", server running: " + (singleplayerServer == null ? "null" : singleplayerServer.isRunning()));
                }
            } else {
                LOGGER.info("Screen not yet null: " + screen);
            }
        } else {
            LOGGER.info("Waiting for player to load...");
        }
    }

}
