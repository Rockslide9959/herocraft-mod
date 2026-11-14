package com.projecthero.mod.client.doctorfate;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateChaos;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.DoctorFateItems;
import com.projecthero.mod.doctorfate.network.FateActionPayload;
import com.projecthero.mod.doctorfate.network.FateBargainPayload;
import com.projecthero.mod.doctorfate.network.FateFxPayload;
import com.projecthero.mod.doctorfate.network.FatePossessPayload;
import com.projecthero.mod.doctorfate.network.FateServePayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: the client side, called once from {@code ProjectHeroModClient}: the HUD, the suit / helmet / amulet layer
 * and the Cloak of Destiny, the Avatar of Order and Ankh Shield in the world, the Tomb Guardian's renderer, the bargain and "serve
 * Order" screens, Sight of Nabu, and Nabu's hand on the controls:
 * <ul>
 *   <li><b>Pull</b> (50+): the aim drifts gently toward the nearest creature of Chaos (you can fight it).</li>
 *   <li><b>Taking hold</b> (75+): now and then the movement keys go dead for a moment.</li>
 *   <li><b>Possession</b>: the server's stream drives the body (the Ghostfreak pattern); Space presses are sent as struggles.</li>
 * </ul>
 * H puts the Helmet of Fate on / takes it off (user, v0.17.3: "like the Nova ability"; {@code ProjectHeroModClient} asks
 * {@link #ownsH}, Shift+H still opens the power wheel) and N does nothing: every other move goes through the ability slots. Flight
 * is double-tap Space ({@code ProjectHeroModClient.handleDoubleJump} asks {@link #wantsFlightToggle}).
 */
public final class DoctorFateClient {
	private static long possessUntil;
	private static long lastPossessMs;
	private static float pForward, pStrafe, pYaw, pPitch;
	private static boolean pUp, pDown;
	private static int lagTicks;
	private static int lagWait = 120;
	private static FateBargainPayload queuedBargain;
	private static long queuedAt;

	private DoctorFateClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(DoctorFateHud::render);
		HudRenderCallback.EVENT.register(FateHelmetClient::renderFirstPerson); // v0.17.4 the put-on / lift-off from inside the helmet
		EntityRendererRegistry.register(com.projecthero.mod.doctorfate.helmet.FateHelmet.ENTITY, FateHelmetRenderer::new); // v0.17.4
		WorldRenderEvents.AFTER_ENTITIES.register(FateHelmetClient::render); // v0.17.4 the summoned helmet flying in
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new FateSuitLayer(playerRenderer));
			}
		});
		FlowingCapeLayer.register(FateCloakLayer::new);
		EntityRendererRegistry.register(DoctorFateItems.TOMB_GUARDIAN, TombGuardianRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(FateWorldRender::render);
		ClientPlayNetworking.registerGlobalReceiver(FateBargainPayload.TYPE, (payload, context) -> openBargain(payload));
		ClientPlayNetworking.registerGlobalReceiver(FateServePayload.TYPE,
				(payload, context) -> Minecraft.getInstance().setScreen(new FateServeScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(FateFxPayload.TYPE, (payload, context) -> {
			if (payload.kind() == FateFxPayload.MIND_REPLY) {
				FateMindscapeScreen.receive(payload.data()); // v0.17.3 the Tower of Fate mindscape
			} else {
				FateFx.receive(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.doctorfate.network.FateSpellFxPayload.TYPE,
				(payload, context) -> FateSpellFxClient.receive(payload)); // v0.17.3 effects pass
		ClientPlayNetworking.registerGlobalReceiver(FatePossessPayload.TYPE, (payload, context) -> {
			possessUntil = payload.until();
			lastPossessMs = System.currentTimeMillis();
			pForward = payload.forward();
			pStrafe = payload.strafe();
			pUp = payload.up();
			pDown = payload.down();
			pYaw = payload.yaw();
			pPitch = payload.pitch();
		});
		// v0.17.4 round 2: the trial in Nabu's library while he holds the body
		ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.doctorfate.network.FateTrialPayload.TYPE,
				(payload, context) -> FateLibrary.receive(payload));
		FateLibrary.init();
		ClientTickEvents.START_CLIENT_TICK.register(DoctorFateClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(DoctorFateClient::endTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	static void reset() {
		possessUntil = 0L;
		lagTicks = 0;
		queuedBargain = null;
		FateFx.reset();
		FateSpellFxClient.reset();
		FateHelmetClient.reset();
	}

	/** A bargain opens at once, or as soon as whatever screen is open closes (if it's still in time). */
	private static void openBargain(FateBargainPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen == null || mc.screen instanceof FateBargainScreen) {
			mc.setScreen(new FateBargainScreen(payload));
		} else {
			queuedBargain = payload;
			queuedAt = System.currentTimeMillis();
		}
	}

	/** This client's player is being driven by Nabu right now (the stream is the lease). */
	public static boolean localPossessed() {
		Minecraft mc = Minecraft.getInstance();
		return possessUntil > 0L && mc.player != null && System.currentTimeMillis() - lastPossessMs < 500L;
	}

	/** Whether plain H belongs to Doctor Fate for this player (he serves Nabu), like Nova's. */
	public static boolean ownsH(LocalPlayer player) {
		return player != null && DoctorFate.hasPower(player);
	}

	/** H: v0.17.4 the helmet off (to the item) or summoned from anywhere (the server checks Possession and the lockout and says why). */
	public static void pressH() {
		ClientPlayNetworking.send(FateActionPayload.of(FateActionPayload.Action.TOGGLE_HELMET));
	}

	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && !player.onGround() && DoctorFate.hasPower(player) && !DoctorFate.possessed(player)
				&& (DoctorFate.wearsHelmet(player) || DoctorFate.isFlying(player));
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(FateActionPayload.of(FateActionPayload.Action.TOGGLE_FLIGHT));
	}

	/** {@code KeyboardInput.tick} (mixin): Nabu's keys while possessed; dead keys for a moment while he's taking hold. */
	public static void input(Input in) {
		if (localPossessed()) {
			in.forwardImpulse = Mth.clamp(pForward, -1f, 1f);
			in.leftImpulse = Mth.clamp(pStrafe, -1f, 1f);
			in.up = pForward > 0.05f;
			in.down = pForward < -0.05f;
			in.left = pStrafe > 0.05f;
			in.right = pStrafe < -0.05f;
			in.jumping = pUp;
			in.shiftKeyDown = pDown;
			return;
		}
		if (lagTicks > 0) {
			in.forwardImpulse = 0f;
			in.leftImpulse = 0f;
			in.up = in.down = in.left = in.right = false;
		}
	}

	/** Before the keys are read: while possessed only Space counts (each press a struggle). */
	private static void startTick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (queuedBargain != null && mc.screen == null) {
			if (System.currentTimeMillis() - queuedAt < queuedBargain.seconds() * 1000L - 1500L) {
				mc.setScreen(new FateBargainScreen(queuedBargain));
			}
			queuedBargain = null;
		}
		if (!localPossessed() || FateLibrary.showing()) {
			return; // v0.17.4 round 2: in Nabu's library the library takes the keys (Space is its willpower push)
		}
		int presses = 0;
		while (mc.options.keyJump.consumeClick()) {
			presses++;
		}
		for (int i = 0; i < Math.min(presses, 3); i++) {
			ClientPlayNetworking.send(FateActionPayload.of(FateActionPayload.Action.STRUGGLE));
			FateFx.struggled(mc.level.getGameTime());
		}
		KeyMapping.releaseAll();
		for (KeyMapping k : mc.options.keyMappings) {
			while (k.consumeClick()) {
				// swallowed
			}
		}
		mc.player.setSprinting(false);
	}

	private static void endTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		FateFx.tick(mc);
		if (localPossessed()) {
			if (mc.screen instanceof FateBargainScreen) {
				mc.setScreen(null); // the server dropped the offer when Nabu took over
			}
			queuedBargain = null;
			p.setYRot(pYaw);
			p.setXRot(Mth.clamp(pPitch, -90f, 90f));
			p.setYHeadRot(pYaw);
			return;
		}
		if (!DoctorFate.wearsHelmet(p) || com.projecthero.mod.doctorfate.DoctorFateMindscape.silenced(p)) {
			lagTicks = 0; // v0.17.3: the Silence from the Tower of Fate: no Pull, no lag
			return;
		}
		float d = DoctorFate.dominion(p);
		// Pull: the aim drifts toward the nearest creature of Chaos
		if (d >= DoctorFateConfig.TIER_PULL && mc.screen == null) {
			LivingEntity chaos = nearestChaos(mc, p);
			if (chaos != null) {
				Vec3 to = chaos.getBoundingBox().getCenter().subtract(p.getEyePosition());
				float yaw = (float) (Mth.atan2(-to.x, to.z) * (180.0 / Math.PI));
				float pitch = (float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * (180.0 / Math.PI));
				float step = DoctorFateConfig.PULL_DEG_PER_TICK * (d >= DoctorFateConfig.TIER_HOLD ? 1.4f : 1f);
				p.setYRot(Mth.approachDegrees(p.getYRot(), yaw, step));
				p.setXRot(Mth.clamp(Mth.approachDegrees(p.getXRot(), pitch, step * 0.6f), -90f, 90f));
			}
		}
		// Taking hold: the movement keys lag a moment now and then
		if (lagTicks > 0) {
			lagTicks--;
		}
		if (d >= DoctorFateConfig.TIER_HOLD) {
			if (--lagWait <= 0) {
				lagTicks = 5 + p.getRandom().nextInt(6);
				lagWait = 100 + p.getRandom().nextInt(140);
			}
		} else {
			lagTicks = 0;
		}
	}

	private static LivingEntity nearestChaos(Minecraft mc, LocalPlayer p) {
		double r = DoctorFateConfig.PULL_RANGE;
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && DoctorFateChaos.isChaos(e))) {
			double dd = e.distanceToSqr(p);
			if (dd < bestD && dd <= r * r) {
				best = e;
				bestD = dd;
			}
		}
		return best;
	}

	/** Taking hold's lag is on right now (the HUD flickers its edge). */
	public static boolean lagging() {
		return lagTicks > 0;
	}
}
