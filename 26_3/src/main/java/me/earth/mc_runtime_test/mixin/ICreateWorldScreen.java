package me.earth.mc_runtime_test.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface ICreateWorldScreen {
    @Invoker("onCreate")
    void invokeOnCreate();

}
