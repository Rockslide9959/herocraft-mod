package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.chromastone.ChromastoneKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Chromastone's client kit. v0.17.2 revamp (user: "delete all the alien models and only use the skins i provided please
 * no extra things untill i say"): the crown / shoulder / back crystal spikes and the glowing eye / chest cubes are gone -- he is
 * ONLY the player model in the user's skin. Effects (world pass, {@link RecalFx}): the bolt, the prism / full-spectrum rainbow
 * beams, the refraction rays, the Prism Shield's hexagon of hard light, the Absorb Stance and Redirect shells, the UV flare's
 * glow; ribbons, rings and blasts come from the server as QuirkFx. The HUD shows the Charge meter (flashing when Overloading).
 */
public final class ChromastoneClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/chromastone.png");

	public static final int VIOLET = 0xFFC860FF;
	public static final int WHITE = 0xFFFFF4FF;
	public static final int METER = 0xFFC060F0;

	private static boolean worldPass;

	// ---------------------------------------------------------------- third-person poses
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.2f, 0, 0, 0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] PRISM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.55f, 0.25f, 0, -1.55f, -0.25f, 0, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0),
			QuirkKeyframes.f(12, -1.55f, 0.25f, 0, -1.55f, -0.25f, 0, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0), QuirkKeyframes.rest(16) };
	// v0.17.2 revamp: Redirect -- palms forward, braced; the return -- a palm thrust
	private static final float[][] REDIRECT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.4f, 0.4f, 0, -1.4f, -0.4f, 0, 0.15f, 0, -0.15f, 0, 0, 0.15f, 0, 0, 0),
			QuirkKeyframes.f(28, -1.4f, 0.4f, 0, -1.4f, -0.4f, 0, 0.15f, 0, -0.15f, 0, 0, 0.15f, 0, 0, 0), QuirkKeyframes.rest(32) };
	private static final float[][] RETURN = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0, 0, -0.4f, 0, 0, 0.1f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] REFRACT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.25f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(10, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.25f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] TAKEOFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.4f, 0.4f, 0, -0.4f, 0, 0, 0.1f, 0, 0, 0.1f, 0, 0, -0.2f),
			QuirkKeyframes.rest(10) };
	private static final float[][] LAND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.2f, 0, 0.4f, 0.2f, 0, -0.4f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.rest(8) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.7f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.6f),
			QuirkKeyframes.f(8, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.7f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.6f), QuirkKeyframes.rest(12) };
	// v0.17.2 revamp: Radiant Burst -- curled in, then thrown wide open as the light pours out of him
	private static final float[][] RADIANT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.2f, 0, 1.9f, -0.2f, 0, -1.9f, -0.35f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.35f),
			QuirkKeyframes.f(12, -0.2f, 0, 1.9f, -0.2f, 0, -1.9f, -0.35f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.35f), QuirkKeyframes.rest(18) };
	private static final float[][] STANCE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.25f),
			QuirkKeyframes.f(96, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.25f), QuirkKeyframes.rest(100) };
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(10, -1.5f, -0.2f, 0, -1.5f, 0.2f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] FLARE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0.5f, 0, -0.6f, -0.5f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(5, -2.6f, -0.4f, 0.5f, -2.6f, 0.4f, -0.5f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(12) };
	private static final float[][] RELEASE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.3f, 0, 1.7f, -0.3f, 0, -1.7f, -0.3f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.3f), QuirkKeyframes.rest(14) };
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, -0.4f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.5f),
			QuirkKeyframes.f(8, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.4f), QuirkKeyframes.rest(16) };
	private static final float[][] SPECTRUM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, -0.3f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(14, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, -0.35f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(17, -1.6f, 0.2f, 0, -1.6f, -0.2f, 0, 0.15f, 0, 0.2f, 0, 0.1f, -0.1f, 0, -0.1f, 0f),
			QuirkKeyframes.f(28, -1.6f, 0.2f, 0, -1.6f, -0.2f, 0, 0.15f, 0, 0.2f, 0, 0.1f, -0.1f, 0, -0.1f, 0f), QuirkKeyframes.rest(32) };

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -15, 10, 0, 0.05f, 0.15f, -0.3f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PALMS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(12, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(28, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OUT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -10, 0, 0, -0.2f, 0.1f),
			FirstPersonClip.f(6, -20, -45, 30, -0.35f, 0.1f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 40, 15, 0, 0.1f, -0.4f, 0.25f),
			FirstPersonClip.f(10, 40, 15, 0, 0.1f, -0.4f, 0.25f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STANCE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 25, -0.3f, 0.05f, 0.1f),
			FirstPersonClip.f(96, -20, -40, 25, -0.3f, 0.05f, 0.1f), FirstPersonClip.f(100, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -10, 35, 0, 0.25f, 0, -0.1f),
			FirstPersonClip.f(5, -70, -20, 20, -0.15f, 0.45f, 0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPECTRUM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 25, -0.3f, 0.05f, 0.1f),
			FirstPersonClip.f(14, -22, -42, 26, -0.31f, 0.06f, 0.1f), FirstPersonClip.f(17, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(28, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();

	public ChromastoneClient() {
		if (!worldPass) {
			worldPass = true;
			WorldRenderEvents.AFTER_ENTITIES.register(ChromastoneClient::renderWorld);
			// v0.17.2 revamp: the Prism Bolt -- a short streak of violet light along its flight
			OmnitrixRenderers.projectileLook("chromastone_bolt", (e, partial, pose, buffers) -> {
				Vec3 v = e.getDeltaMovement();
				Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();
				Vec3 c = new Vec3(0, e.getBbHeight() * 0.5, 0);
				RecalFx.beam(pose, buffers, c.subtract(dir.scale(0.9)), c, 0.18f, WHITE, VIOLET, 1f);
			});
		}
	}

	public static void register() {
		AlienClients.register(Aliens.CHROMASTONE.id(), new ChromastoneClient());
	}

	/** Three pinks off his own skin (the hands): base, mid, tip. */
	private static final float[] PINK_DARK = AlienHorns.pixel(20, 7, 64), PINK = AlienHorns.pixel(12, 7, 64), PINK_LIGHT = AlienHorns.pixel(12, 6, 64);

	/**
	 * v0.17.2 (user: "Give him a pink spike on his head, 2 on his chest where it shows in like the reference and also 2 coming out of
	 * his back, make it the same colour of the pink on his skin model"): crystal spikes in his skin's own pinks -- one up off the
	 * crown, two forward-and-out off the top of the chest by the shoulders, two back-and-out between the shoulder blades.
	 */
	@Override
	public void renderExtras(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
			net.minecraft.client.player.AbstractClientPlayer p, net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model, float partial,
			float age) {
		var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN));
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.translate(0f, -8f / 16f, -1f / 16f);
			AlienHorns.spike(pose, vc, light, 4.2f, 1.8f, -12f, 0f, PINK_DARK, PINK, PINK_LIGHT);
			pose.popPose();
		});
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 2.6f / 16f, 1.0f / 16f, -2.3f / 16f);
				AlienHorns.spike(pose, vc, light, 4.6f, 1.8f, 58f, side * 14f, PINK_DARK, PINK, PINK_LIGHT);
				pose.popPose();
				pose.pushPose();
				pose.translate(side * 2.2f / 16f, 2.6f / 16f, 1.6f / 16f);
				AlienHorns.spike(pose, vc, light, 3.8f, 1.6f, -50f, side * 26f, PINK_DARK, PINK, PINK_LIGHT);
				pose.popPose();
			}
		});
	}

	/** v0.17.2 (user: "raise our omnitrix badge slightly"): a pixel higher than the default, where the skin's own symbol was. */
	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.05f, 3.2f };
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int f = Omnitrix.state(p).kitFlags;
		float[][] clip = switch (animId) {
			case ChromastoneKit.ANIM_BOLT -> BOLT;
			case ChromastoneKit.ANIM_PRISM -> PRISM;
			case ChromastoneKit.ANIM_REDIRECT -> (f & ChromastoneKit.F_REDIRECT) != 0 || ticks < 4 ? REDIRECT : null;
			case ChromastoneKit.ANIM_RETURN -> RETURN;
			case ChromastoneKit.ANIM_REFRACT -> REFRACT;
			case ChromastoneKit.ANIM_TAKEOFF -> TAKEOFF;
			case ChromastoneKit.ANIM_LAND -> LAND;
			case ChromastoneKit.ANIM_DASH -> DASH;
			case ChromastoneKit.ANIM_RADIANT -> RADIANT;
			case ChromastoneKit.ANIM_STANCE -> (f & ChromastoneKit.F_STANCE) != 0 || ticks < 6 ? STANCE : null;
			case ChromastoneKit.ANIM_SHIELD -> SHIELD;
			case ChromastoneKit.ANIM_FLARE -> FLARE;
			case ChromastoneKit.ANIM_RELEASE -> RELEASE;
			case ChromastoneKit.ANIM_OVERLOAD -> OVERLOAD;
			case ChromastoneKit.ANIM_SPECTRUM -> SPECTRUM;
			default -> null;
		};
		if (clip != null && QuirkKeyframes.play(m, clip, ticks, 1f)) {
			return true;
		}
		if ((f & ChromastoneKit.F_FLYING) != 0 && !p.onGround()) {
			// hovering on his own light: upright, arms a little out
			m.rightLeg.xRot = 0.1f;
			m.leftLeg.xRot = 0.15f;
			m.rightArm.zRot = 0.3f + Mth.sin(age * 0.08f) * 0.04f;
			m.leftArm.zRot = -0.3f - Mth.sin(age * 0.08f) * 0.04f;
			return true;
		}
		return false;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case ChromastoneKit.ANIM_BOLT, ChromastoneKit.ANIM_RETURN -> FP_POINT;
			case ChromastoneKit.ANIM_PRISM, ChromastoneKit.ANIM_SHIELD -> FP_PALMS;
			case ChromastoneKit.ANIM_REDIRECT -> FP_GUARD;
			case ChromastoneKit.ANIM_REFRACT, ChromastoneKit.ANIM_RELEASE, ChromastoneKit.ANIM_RADIANT, ChromastoneKit.ANIM_OVERLOAD -> FP_OUT;
			case ChromastoneKit.ANIM_DASH, ChromastoneKit.ANIM_TAKEOFF -> FP_RUN;
			case ChromastoneKit.ANIM_STANCE -> FP_STANCE;
			case ChromastoneKit.ANIM_FLARE -> FP_FLASH;
			case ChromastoneKit.ANIM_SPECTRUM -> FP_SPECTRUM;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the HUD: the Charge meter

	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int c = ChromastoneKit.charge(p);
		int f = Omnitrix.state(p).kitFlags;
		boolean stance = (f & ChromastoneKit.F_STANCE) != 0;
		boolean over = (f & ChromastoneKit.F_OVER) != 0;
		boolean full = c >= ChromastoneKit.SPECTRUM_MIN;
		int col = over ? ((p.tickCount / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF60C0) : stance ? 0xFFF0C0FF : full ? 0xFFE080FF : METER;
		OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable(over ? "hud.projecthero.omnitrix.chromastone.overload" : "hud.projecthero.omnitrix.chromastone.charge"),
				c / (float) ChromastoneKit.MAX_CHARGE, col, String.valueOf(c), full ? 0xFFF4D0FF : 0xFFE2E2E2);
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	// ---------------------------------------------------------------- the world pass

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!ChromastoneKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			boolean fp = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			float now = pl.level().getGameTime() + partial;
			float t = now - st.animStart;
			float sc = pl.getScale();
			Vec3 f = pl.getViewVector(partial);
			Vec3 right = f.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 eye = pl.getEyePosition(partial);
			Vec3 feet = pl.getPosition(partial);
			Vec3 chest = fp ? eye.add(f.scale(0.7)).add(0, -0.35, 0) : feet.add(0, 1.15 * sc, 0).add(f.scale(0.3 * sc));
			switch (st.animId) {
				case ChromastoneKit.ANIM_PRISM -> {
					Vec3 end = RecalFx.traceEnd(pl, eye, f, 28.0);
					RecalFx.rainbow(pose, buffers, chest.subtract(cam), end.subtract(cam), 0.35f, 0.18f, RecalFx.fade(t, 12f), now);
				}
				case ChromastoneKit.ANIM_REFRACT, ChromastoneKit.ANIM_RADIANT, ChromastoneKit.ANIM_RETURN -> {
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), chest.subtract(cam), 0.7f, PyroDraw.dim(0xFFFFFFFF, RecalFx.fade(t, 10f)));
				}
				case ChromastoneKit.ANIM_FLARE -> {
					float k = RecalFx.fade(t - 4f, 8f);
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), eye.subtract(cam), 1f + Math.max(0f, t - 4f) * 1.2f, PyroDraw.dim(0xFFB890FF, k));
				}
				case ChromastoneKit.ANIM_SPECTRUM -> {
					if (t < ChromastoneKit.SPECTRUM_WINDUP) {
						// the colours gather into his chest
						float g = t / ChromastoneKit.SPECTRUM_WINDUP;
						for (int i = 0; i < 7; i++) {
							double a = now * 0.3 + i * Math.PI * 2 / 7;
							double d = 1.6 * (1 - g) + 0.2;
							Vec3 at = chest.add(right.scale(Math.cos(a) * d)).add(0, Math.sin(a) * d, 0);
							PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at.subtract(cam), 0.25f, PyroDraw.dim(RecalFx.SPECTRUM[i], 0.6f + g * 0.4f));
						}
						PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), chest.subtract(cam), 0.3f + g * 0.6f, PyroDraw.dim(0xFFFFFFFF, g));
					} else {
						float k = RecalFx.fade(t - ChromastoneKit.SPECTRUM_WINDUP, 13f);
						Vec3 end = RecalFx.traceEnd(pl, eye, f, ChromastoneKit.SPECTRUM_RANGE);
						RecalFx.rainbow(pose, buffers, chest.subtract(cam), end.subtract(cam), 1.3f, 0.65f, k, now);
					}
				}
				default -> {
				}
			}
			int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(eye));
			if ((st.kitFlags & ChromastoneKit.F_STANCE) != 0 && !fp) {
				// the absorb stance: a faceted crystal shell, pulsing
				int a = 0x50 + (int) (0x20 * Mth.sin(now * 0.3f));
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), 0.18f, (a << 24) | 0xD890FF, light);
			}
			if ((st.kitFlags & ChromastoneKit.F_REDIRECT) != 0 && !fp) {
				int a = 0x40 + (int) (0x30 * Mth.sin(now * 0.8f));
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), 0.1f, (a << 24) | 0xFFFFFF, light);
			}
			if ((st.kitFlags & ChromastoneKit.F_SHIELD) != 0) {
				// the Prism Shield: a hexagon of hard light standing in front of him, edges cycling through the spectrum
				Vec3 flat = new Vec3(f.x, 0, f.z);
				flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
				Vec3 side = flat.cross(new Vec3(0, 1, 0)).normalize();
				Vec3 c = feet.add(0, 1.0 * sc, 0).add(flat.scale(fp ? 1.4 : 1.0 * sc));
				float r = 1.25f * sc;
				for (int i = 0; i < 6; i++) {
					double a0 = i * Math.PI / 3 + Math.PI / 6, a1 = (i + 1) * Math.PI / 3 + Math.PI / 6;
					Vec3 p0 = c.add(side.scale(Math.cos(a0) * r)).add(0, Math.sin(a0) * r, 0);
					Vec3 p1 = c.add(side.scale(Math.cos(a1) * r)).add(0, Math.sin(a1) * r, 0);
					int col = RecalFx.SPECTRUM[(i + (int) (now * 0.2f)) % RecalFx.SPECTRUM.length];
					RecalFx.beam(pose, buffers, p0.subtract(cam), p1.subtract(cam), 0.12f, WHITE, col, 0.85f);
				}
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), c.subtract(cam), r * 1.1f, PyroDraw.dim(0xFFE0C8FF, 0.35f));
			}
		}
	}
}
