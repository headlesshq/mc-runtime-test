package me.earth.mc_runtime_test;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Similar to running the "/test runall" command.
 */
public class McGameTestRunner {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Basically what happens in {@link TestCommand} when "runall" is used.
     * The client checks the result and shuts down after the tests finish.
     *
     * @param playerUUID the uuid of the player.
     * @param server     the server to run the tests on.
     */
    public static @Nullable MultipleTestTracker runGameTests(UUID playerUUID, MinecraftServer server) throws ExecutionException, InterruptedException, TimeoutException {
        return server.submit(() -> {
            Player player = Objects.requireNonNull(server.getPlayerList().getPlayer(playerUUID));
            ServerLevel level = (ServerLevel) player.level();
            // GameTestRunner.clearMarkers(level);

            AtomicReference<Optional<MultipleTestTracker>> result = new AtomicReference<>();
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            dispatcher.register(Commands.literal("test").executes(ctx -> {
                String instance = System.getProperty("McRuntimeGameTestInstance");
                // Named tests can run in a fresh world without preplaced test
                // blocks. Missing registry entries must fail rather than skip.
                Collection<Holder.Reference<GameTestInstance>> testFunctions = instance == null
                    ? TestFinder.builder().allNearby(ctx).findTests().toList()
                    : List.of(server.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE)
                        .getOrThrow(ResourceKey.create(Registries.TEST_INSTANCE, Identifier.parse(instance))));
                LOGGER.info("TestFunctions: " + testFunctions);
                if (testFunctions.size() < McRuntimeTest.MIN_GAME_TESTS_TO_FIND) {
                    LOGGER.error("Failed to find the minimum amount of gametests, expected " + McRuntimeTest.MIN_GAME_TESTS_TO_FIND + ", but found " + testFunctions.size());
                    throw new IllegalStateException("Failed to find the minimum amount of gametests, expected " + McRuntimeTest.MIN_GAME_TESTS_TO_FIND + ", but found " + testFunctions.size());
                } else if (testFunctions.isEmpty()) {
                    result.set(Optional.empty());
                    return Command.SINGLE_SUCCESS;
                }

                GameTestBatchFactory.TestDecorator testDecorator = (reference, serverLevel) -> Stream.of(
                        new GameTestInfo(reference, Rotation.NONE, serverLevel, RetryOptions.noRetries())
                );
                // In 26.3, batch creation and the runner take the server.
                Collection<GameTestBatch> batches = GameTestBatchFactory.divideIntoBatches(testFunctions, testDecorator, ctx.getSource().getServer());


                FailedTestTracker.forgetFailedTests();

                GameTestRunner.Builder builder = GameTestRunner.Builder.fromBatches(batches, server);
                if (instance != null) {
                    builder.newStructureSpawner(new StructureGridSpawner(
                        dimension -> player.blockPosition().offset(0, 4, 8), 1, false));
                }
                GameTestRunner gameTestRunner = builder.build();
                gameTestRunner.start();

                MultipleTestTracker multipleTestTracker = new MultipleTestTracker(gameTestRunner.getTestInfos());
                multipleTestTracker.addFailureListener(gameTestInfo -> {
                    LOGGER.error("Test failed: " + gameTestInfo);
                    if (gameTestInfo.getError() != null) {
                        LOGGER.error(String.valueOf(gameTestInfo), gameTestInfo.getError());
                    }
                    // The client handles failure after the batch completes, so
                    // the integrated server can save before the process exits.
                });

                result.set(Optional.of(multipleTestTracker));
                return Command.SINGLE_SUCCESS;
            }));

            try {
                dispatcher.execute("test", server.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }

            return result.get().orElse(null);
        }).get(60, TimeUnit.SECONDS);
    }

}
