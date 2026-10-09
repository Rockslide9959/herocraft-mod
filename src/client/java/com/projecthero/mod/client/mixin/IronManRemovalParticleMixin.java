package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15, user request: while the LOCAL player takes a suit off with C ({@code IronManSuitRemoval} -- the Mark 1 by
 * hand, Marks 2-7 retracting) and is looking through their own eyes, the server's sparks / puffs on their body landed a
 * few pixels from the camera and filled the view with huge crosses. Drop any particle burst centred within
 * {@value #NEAR} blocks of the camera for that player only, in first person only -- third person and every other viewer
 * still see them.
 */
@Mixin(ClientPacketListener.class)
public abstract class IronManRemovalParticleMixin {
	private static final double NEAR = 1.6;

	@Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
	private void projecthero$noRemovalSparksInYourFace(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.isSameThread() || mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
			return; // (the netty-thread pass only re-queues the packet onto the client thread)
		}
		IronManSuitFx fx = IronManSuitFx.of(mc.player);
		int kind = fx.poseKind();
		if (!IronManSuitFx.removalPose(kind)
				|| fx.poseAge(mc.level.getGameTime(), 0f) < 0f) {
			return;
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		if (cam.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) < NEAR * NEAR) {
			ci.cancel();
		}
	}
}
