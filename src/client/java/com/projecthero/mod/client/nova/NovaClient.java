package com.projecthero.mod.client.nova;

import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.network.NovaActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.15.13: Nova's client side -- the uniform layer, the HUD, the world effects (beam, shield, Gravity Well), the
 * Worldmind outlines, the Centurion's renderer, and the H / double-tap-jump requests (sent from
 * {@code ProjectHeroModClient}).
 */
public final class NovaClient {
	private NovaClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new NovaSuitLayer(playerRenderer));
			}
		});
		HudRenderCallback.EVENT.register(NovaHud::render);
		NovaEffectsRenderer.init();
		NovaWorldmindClient.init();
		NovaCenturionRenderer.initialize();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NovaWorldmindClient.reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				NovaWorldmindClient.reset();
			}
		});
	}

	/** Whether plain H belongs to Nova for this player (he carries the Nova Force). */
	public static boolean ownsH(LocalPlayer player) {
		return Nova.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new NovaActionPayload(NovaActionPayload.Action.TOGGLE_SUIT));
	}

	/** The double-tap jump in the air while suited (the server re-validates). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return !player.onGround() && Nova.suited(player);
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new NovaActionPayload(NovaActionPayload.Action.TOGGLE_FLIGHT));
	}

	/** How close to a first-person Nova's camera his own effect particles may spawn. */
	private static final double CAMERA_CLEARANCE = 1.5;

	/**
	 * v0.15.15: should this server particle packet be dropped? True only for a Nova looking through his own eyes, for
	 * Nova's gold / cyan dust, end rods and flashes whose spawn cloud reaches within {@link #CAMERA_CLEARANCE} of the
	 * camera (his aura, the Overload, the suit-up and the Force Field are spawned round his body -- from inside they
	 * filled the view). Directional bursts ({@code count == 0}) fly outward and are kept.
	 */
	public static boolean hideNearCamera(ParticleOptions options, double x, double y, double z, int count, float spread) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || count <= 0 || !Nova.hasPower(player) || !mc.options.getCameraType().isFirstPerson()
				|| mc.getCameraEntity() != player) {
			return false;
		}
		if (!novaParticle(options)) {
			return false;
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		double reach = CAMERA_CLEARANCE + spread * 0.5;
		return cam.distanceToSqr(x, y, z) < reach * reach;
	}

	private static boolean novaParticle(ParticleOptions options) {
		if (options.getType() == ParticleTypes.FLASH || options.getType() == ParticleTypes.END_ROD) {
			return true;
		}
		if (options instanceof DustParticleOptions d) {
			return same(d, Nova.GOLD) || same(d, Nova.GOLD_BIG) || same(d, Nova.CYAN);
		}
		return false;
	}

	private static boolean same(DustParticleOptions a, DustParticleOptions b) {
		return a.getColor().distanceSquared(b.getColor()) < 1.0e-4f && Math.abs(a.getScale() - b.getScale()) < 1.0e-3f;
	}
}
