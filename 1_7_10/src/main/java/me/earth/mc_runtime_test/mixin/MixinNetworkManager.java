package me.earth.mc_runtime_test.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    @Shadow
    @Final
    private static Logger logger;

    @Inject(method = "scheduleOutboundPacket", at = @At("HEAD"))
    protected void scheduleOutboundPacketHook(Packet packet, GenericFutureListener<?>[] futureListeners, CallbackInfo ci) {
        logger.info("Sending packet: {}", packet.getClass().getName());
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    protected void channelRead0Hook(ChannelHandlerContext context, Packet packet, CallbackInfo ci) {
        logger.info("Packet received: {}", packet.getClass().getName());
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void exceptionCaughtHook(ChannelHandlerContext context, Throwable throwable, CallbackInfo ci) {
        logger.error("Exception caught", throwable);
    }

}
