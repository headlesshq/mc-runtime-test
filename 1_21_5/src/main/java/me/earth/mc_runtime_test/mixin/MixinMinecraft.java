package me.earth.mc_runtime_test.mixin;

import me.earth.mc_runtime_test.McGameTestRunner;
import me.earth.mc_runtime_test.McRuntimeTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.MultipleTestTracker;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
    @Unique
    private MultipleTestTracker mcRuntimeTest$testTracker = null;

    @Shadow
    public abstract @Nullable Overlay getOverlay();

    @Shadow
    public abstract void setScreen(@Nullable Screen screen);

    @Shadow(aliases = {"disconnect", "disconnectWithProgressScreen"})
    public abstract void disconnect();

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void setScreenHook(Screen screen, CallbackInfo ci) {
        if (!McRuntimeTest.screenHook()) {
            return;
        }

        if (screen instanceof ErrorScreen) {
            mcRuntime$stop();
            throw new RuntimeException("Error Screen " + screen);
        } else if (screen instanceof DeathScreen && player != null) {
            player.respawn();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickHook(CallbackInfo ci) throws ExecutionException, InterruptedException, TimeoutException {
        if (!McRuntimeTest.tickHook()) {
            return;
        }

        if (getOverlay() == null) {
            if (!mcRuntimeTest$startedLoadingSPWorld && getOverlay() == null) {
                CreateWorldScreen.openFresh(Minecraft.class.cast(this), null);
                mcRuntimeTest$startedLoadingSPWorld = true;
            } else if (!mcRuntimeTest$worldCreationStarted && screen instanceof ICreateWorldScreen createWorldScreen) {
                createWorldScreen.invokeOnCreate();
                mcRuntimeTest$worldCreationStarted = true;
            }
        } else {
            LOGGER.info("Waiting for overlay to disappear...");
        }

        // TODO: detect SinglePlayerServer crash where game freezes?
        if (player != null && level != null) {
            if (screen == null) {
                if (!level.getChunk(SectionPos.blockToSectionCoord(player.getBlockX()), SectionPos.blockToSectionCoord(player.getBlockZ())).isEmpty()) {
                    if (player.tickCount < 100) {
                        LOGGER.info("Waiting " + (100 - player.tickCount) + " ticks before testing...");
                    } else if (mcRuntimeTest$testTracker == null) {
                        if (McRuntimeTest.RUN_GAME_TESTS) {
                            LOGGER.info("Running game tests...");
                            mcRuntimeTest$testTracker = McGameTestRunner.runGameTests(player.getUUID(), Objects.requireNonNull(singleplayerServer));
                            if (mcRuntimeTest$testTracker == null) {
                                mcRuntimeTest$testTracker = new MultipleTestTracker();
                                LOGGER.info("No tests found, Successfully finished.");
                                mcRuntime$stop();
                            }
                        } else {
                            LOGGER.info("Successfully finished.");
                            mcRuntime$stop();
                        }
                    } else if (mcRuntimeTest$testTracker.isDone()) {
                        if (mcRuntimeTest$testTracker.getFailedRequiredCount() > 0
                            || mcRuntimeTest$testTracker.getFailedOptionalCount() > 0 && McRuntimeTest.GAME_TESTS_FAIL_ON_OPTIONAL) {
                            System.exit(-1);
                        }

                        mcRuntime$stop();
                    } else {
                        LOGGER.info("Waiting for GameTest: " + mcRuntimeTest$testTracker.getProgressBar());
                    }
                } else {
                    LOGGER.info("Players chunk not yet loaded, " + player + ": cores: " + Runtime.getRuntime().availableProcessors()
                            + ", server running: " + (singleplayerServer == null ? "null" : singleplayerServer.isRunning()));
                }
            } else {
                LOGGER.info("Screen not yet null: " + screen);
                if (McRuntimeTest.CLOSE_ANY_SCREEN || McRuntimeTest.CLOSE_CREATE_WORLD_SCREEN && screen instanceof CreateWorldScreen) {
                    LOGGER.info("Closing screen");
                    setScreen(null);
                }
            }
        } else {
            LOGGER.info("Waiting for player to load...");
        }
    }

    @Unique
    private void mcRuntime$stop() {
        IntegratedServer server = singleplayerServer;
        if (server != null) {
            server.halt(true);
            disconnect();
        }

        running = false;
    }

}
