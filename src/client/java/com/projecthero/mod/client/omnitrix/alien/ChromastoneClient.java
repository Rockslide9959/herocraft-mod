package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
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
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic version): Chromastone's client kit -- the player body in his faceted violet crystal
 * skin, crystal spikes on his crown, shoulders and back, and a glow in his chest and eyes that brightens with the stored
 * charge (the HUD's Charge meter shows it too). The bolts, the prism / full-spectrum rainbow beams, the refraction rays, the
 * spike ring, the absorb-stance crystal shell and the light burst are rendered in the world pass ({@link RecalFx}).
 */
public final class ChromastoneClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/chromastone.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/chromastone_extras.png");
	// extras sheet rectangles (scratchpad/recal_v0172/chromastone/gen_chromastone.js)
	private static final float[] SPIKE = { 0, 0, 8, 8 };
	private static final float[] SPIKE_D = { 8, 0, 16, 8 };
	private static final float[] GLOW = { 16, 0, 24, 8 };

	public static final int VIOLET = 0xFFC860FF;
	public static final int WHITE = 0xFFFFF4FF;
	public static final int METER = 0xFFC060F0;

	private static boolean worldPass;

	// ---------------------------------------------------------------- third-person poses
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.2f, 0, 0, 0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] PRISM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.55f, 0.25f, 0, -1.55f, -0.25f, 0, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0),
			QuirkKeyframes.f(12, -1.55f, 0.25f, 0, -1.55f, -0.25f, 0, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0), QuirkKeyframes.rest(16) };
	private static final float[][] SHARDS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.5f, -0.6f, 0, -1.5f, 0.6f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.5f, 0.3f, 0, -1.5f, -0.3f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] REFRACT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.25f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(10, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.25f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0.3f, 0, -0.9f, 0, 0, 0.4f, 0, 0, -0.2f),
			QuirkKeyframes.f(12, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0.3f, 0, -0.9f, 0, 0, 0.4f, 0, 0, -0.2f), QuirkKeyframes.rest(16) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.7f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.6f),
			QuirkKeyframes.f(8, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.7f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.6f), QuirkKeyframes.rest(12) };
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.6f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(14) };
	private static final float[][] SPIKES = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 0.6f, -0.4f, 0, -0.6f, 0.4f, 0, 0, 0, 0.2f, 0, 0, -0.2f, 0.3f),
			QuirkKeyframes.f(6, 0.2f, 0, 1.2f, 0.2f, 0, -1.2f, -0.2f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.2f), QuirkKeyframes.rest(16) };
	private static final float[][] STANCE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.25f),
			QuirkKeyframes.f(96, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.25f), QuirkKeyframes.rest(100) };
	private static final float[][] FLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0.5f, 0, -0.6f, -0.5f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(5, -2.6f, -0.4f, 0.5f, -2.6f, 0.4f, -0.5f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(12) };
	private static final float[][] RELEASE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.3f, 0, 1.7f, -0.3f, 0, -1.7f, -0.3f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.3f), QuirkKeyframes.rest(14) };
	private static final float[][] SPECTRUM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, -0.3f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(14, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, -0.35f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(17, -1.6f, 0.2f, 0, -1.6f, -0.2f, 0, 0.15f, 0, 0.2f, 0, 0.1f, -0.1f, 0, -0.1f, 0f),
			QuirkKeyframes.f(28, -1.6f, 0.2f, 0, -1.6f, -0.2f, 0, 0.15f, 0, 0.2f, 0, 0.1f, -0.1f, 0, -0.1f, 0f), QuirkKeyframes.rest(32) };

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -15, 10, 0, 0.05f, 0.15f, -0.3f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PALMS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(12, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FAN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, -20, 0, -0.1f, 0.1f, -0.2f),
			FirstPersonClip.f(5, -20, 30, 0, 0.2f, 0.1f, -0.2f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OUT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -10, 0, 0, -0.2f, 0.1f),
			FirstPersonClip.f(6, -20, -45, 30, -0.35f, 0.1f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 40, 15, 0, 0.1f, -0.4f, 0.25f),
			FirstPersonClip.f(10, 40, 15, 0, 0.1f, -0.4f, 0.25f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(7, 35, 15, 0, 0.1f, -0.35f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
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
		}
	}

	public static void register() {
		AlienClients.register(Aliens.CHROMASTONE.id(), new ChromastoneClient());
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static float u(float[] r, int i) {
		return r[i] / 64f;
	}

	/** One crystal spike on the current bone: a tall cube, tilted, the lit facet texture (or the darker one). */
	private static void spike(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float rx, float rz, float w, float h, boolean dark, int light) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.ZP.rotation(rz));
		pose.mulPose(Axis.XP.rotation(rx));
		pose.mulPose(Axis.YP.rotation(0.785f));
		float[] r = dark ? SPIKE_D : SPIKE;
		AlienParts.box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS)), -w / 2, -h, -w / 2, w / 2, 0, w / 2, u(r, 0), u(r, 1), u(r, 2), u(r, 3),
				0xFFFFFFFF, light);
		// the tip, narrower
		AlienParts.box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS)), -w / 4, -h - h * 0.4f, -w / 4, w / 4, -h, w / 4, u(SPIKE, 0), u(SPIKE, 1),
				u(SPIKE, 2), u(SPIKE, 3), 0xFFFFFFFF, light);
		pose.popPose();
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		float charge = ChromastoneKit.charge(p) / (float) ChromastoneKit.MAX_CHARGE;
		AlienParts.on(pose, model.head, () -> {
			// the crown of crystals
			spike(pose, buffers, 0f, -8f, 0f, 0f, 0f, 2.2f, 4.5f, false, light);
			spike(pose, buffers, -2.5f, -8f, 0.5f, 0.1f, -0.45f, 1.8f, 3.2f, true, light);
			spike(pose, buffers, 2.5f, -8f, 0.5f, 0.1f, 0.45f, 1.8f, 3.2f, true, light);
			spike(pose, buffers, 0f, -7.5f, 3f, 0.6f, 0f, 1.6f, 2.8f, true, light);
			// the eyes glow with the charge
			int g = PyroDraw.dim(0xFFFFFFFF, 0.25f + charge * 0.75f);
			var eyes = buffers.getBuffer(RenderType.eyes(EXTRAS));
			AlienParts.box(pose, eyes, -3.0f, -6.0f, -4.1f, -2.0f, -5.0f, -4.02f, u(GLOW, 0), u(GLOW, 1), u(GLOW, 2), u(GLOW, 3), g, LightTexture.FULL_BRIGHT);
			AlienParts.box(pose, eyes, 2.0f, -6.0f, -4.1f, 3.0f, -5.0f, -4.02f, u(GLOW, 0), u(GLOW, 1), u(GLOW, 2), u(GLOW, 3), g, LightTexture.FULL_BRIGHT);
		});
		AlienParts.on(pose, model.body, () -> {
			// the back ridge
			spike(pose, buffers, 0f, 2f, 2f, 1.1f, 0f, 2f, 3.5f, false, light);
			spike(pose, buffers, 0f, 5.5f, 2f, 1.2f, 0f, 1.8f, 3f, true, light);
			spike(pose, buffers, 0f, 9f, 2f, 1.3f, 0f, 1.5f, 2.4f, false, light);
			// the chest crystal glows with the charge (the Omnitrix badge sits above it)
			if (charge > 0.02f) {
				int g = PyroDraw.dim(0xFFFFFFFF, charge);
				AlienParts.box(pose, buffers.getBuffer(RenderType.eyes(EXTRAS)), -2f, 6f, -2.12f, 2f, 10f, -2.04f, u(GLOW, 0), u(GLOW, 1), u(GLOW, 2), u(GLOW, 3),
						g, LightTexture.FULL_BRIGHT);
			}
		});
		AlienParts.on(pose, model.rightArm, () -> {
			spike(pose, buffers, -1f, -2f, 0f, 0f, -0.6f, 1.8f, 3.2f, false, light);
			spike(pose, buffers, -2.5f, 1f, 0f, 0f, -1.1f, 1.4f, 2.2f, true, light);
		});
		AlienParts.on(pose, model.leftArm, () -> {
			spike(pose, buffers, 1f, -2f, 0f, 0f, 0.6f, 1.8f, 3.2f, false, light);
			spike(pose, buffers, 2.5f, 1f, 0f, 0f, 1.1f, 1.4f, 2.2f, true, light);
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case ChromastoneKit.ANIM_BOLT -> BOLT;
			case ChromastoneKit.ANIM_PRISM -> PRISM;
			case ChromastoneKit.ANIM_SHARDS -> SHARDS;
			case ChromastoneKit.ANIM_REFRACT -> REFRACT;
			case ChromastoneKit.ANIM_LEAP -> LEAP;
			case ChromastoneKit.ANIM_DASH -> DASH;
			case ChromastoneKit.ANIM_SLAM -> SLAM;
			case ChromastoneKit.ANIM_SPIKES -> SPIKES;
			case ChromastoneKit.ANIM_STANCE -> (Omnitrix.state(p).kitFlags & ChromastoneKit.F_STANCE) != 0 || ticks < 6 ? STANCE : null;
			case ChromastoneKit.ANIM_FLASH -> FLASH;
			case ChromastoneKit.ANIM_RELEASE -> RELEASE;
			case ChromastoneKit.ANIM_SPECTRUM -> SPECTRUM;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case ChromastoneKit.ANIM_BOLT -> FP_POINT;
			case ChromastoneKit.ANIM_PRISM -> FP_PALMS;
			case ChromastoneKit.ANIM_SHARDS -> FP_FAN;
			case ChromastoneKit.ANIM_REFRACT, ChromastoneKit.ANIM_RELEASE, ChromastoneKit.ANIM_SPIKES -> FP_OUT;
			case ChromastoneKit.ANIM_LEAP, ChromastoneKit.ANIM_DASH -> FP_RUN;
			case ChromastoneKit.ANIM_SLAM -> FP_SLAM;
			case ChromastoneKit.ANIM_STANCE -> FP_STANCE;
			case ChromastoneKit.ANIM_FLASH -> FP_FLASH;
			case ChromastoneKit.ANIM_SPECTRUM -> FP_SPECTRUM;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		if (ChromastoneKit.charge(p) >= 80 && r.nextInt(8) == 0) {
			p.level().addParticle(ParticleTypes.END_ROD, p.getX() + (r.nextDouble() - 0.5) * 0.7, p.getY() + 0.5 + r.nextDouble() * 1.4,
					p.getZ() + (r.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
		}
	}

	// ---------------------------------------------------------------- the HUD: the Charge meter

	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int c = ChromastoneKit.charge(p);
		boolean stance = (Omnitrix.state(p).kitFlags & ChromastoneKit.F_STANCE) != 0;
		boolean full = c >= ChromastoneKit.SPECTRUM_MIN;
		int col = stance ? 0xFFF0C0FF : full ? 0xFFE080FF : METER;
		OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.chromastone.charge"), c / (float) ChromastoneKit.MAX_CHARGE, col,
				String.valueOf(c), full ? 0xFFF4D0FF : 0xFFE2E2E2);
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
			Vec3 hand = fp ? eye.add(f.scale(0.7)).add(0, -0.25, 0).add(right.scale(0.25)) : eye.add(f.scale(0.55 * sc)).add(0, -0.35 * sc, 0).add(right.scale(-0.3 * sc));
			Vec3 chest = fp ? eye.add(f.scale(0.7)).add(0, -0.35, 0) : feet.add(0, 1.15 * sc, 0).add(f.scale(0.3 * sc));
			switch (st.animId) {
				case ChromastoneKit.ANIM_BOLT -> {
					Vec3 end = RecalFx.traceEnd(pl, eye, f, 24.0);
					RecalFx.beam(pose, buffers, hand.subtract(cam), end.subtract(cam), 0.13f, WHITE, VIOLET, RecalFx.fade(t, 6f));
				}
				case ChromastoneKit.ANIM_PRISM -> {
					Vec3 end = RecalFx.traceEnd(pl, eye, f, 28.0);
					RecalFx.rainbow(pose, buffers, chest.subtract(cam), end.subtract(cam), 0.35f, 0.18f, RecalFx.fade(t, 12f), now);
				}
				case ChromastoneKit.ANIM_SHARDS -> {
					float k = RecalFx.fade(t, 7f);
					for (int i = -2; i <= 2; i++) {
						double a = Math.toRadians(i * 8.0);
						Vec3 d = new Vec3(f.x * Math.cos(a) - f.z * Math.sin(a), f.y, f.x * Math.sin(a) + f.z * Math.cos(a));
						Vec3 end = RecalFx.traceEnd(pl, eye, d, 14.0);
						Vec3 head = hand.lerp(end, Mth.clamp(t / 3f, 0f, 1f));
						RecalFx.beam(pose, buffers, hand.subtract(cam), head.subtract(cam), 0.08f, WHITE, RecalFx.SPECTRUM[(i + 2) * 3 % 7], k);
						RecalFx.spike(pose, buffers, head.subtract(cam), d, 0.1f, 0.45f, 0xFFE8B8FF, 0xFF9040C0, t * 0.3f);
					}
				}
				case ChromastoneKit.ANIM_REFRACT -> {
					float k = RecalFx.fade(t, 12f);
					for (int i = 0; i < 5; i++) {
						long id = st.readyAt(ChromastoneKit.ID + ".ray." + i);
						Entity e = id > 0 ? pl.level().getEntity((int) (id - 1)) : null;
						if (e != null) {
							Vec3 to = e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0);
							RecalFx.beam(pose, buffers, chest.subtract(cam), to.subtract(cam), 0.16f, WHITE, RecalFx.SPECTRUM[i % 7], k);
						}
					}
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), chest.subtract(cam), 0.6f, PyroDraw.dim(0xFFFFFFFF, k));
				}
				case ChromastoneKit.ANIM_SLAM -> {
					RecalFx.ring(pose, buffers, feet.add(0, 0.1, 0).subtract(cam), 0.5f + Math.min(t, 10f) * 0.4f, 0.5f, VIOLET, RecalFx.fade(t - 4f, 10f));
				}
				case ChromastoneKit.ANIM_SPIKES -> {
					// a ring of crystal spikes bursting up, then sinking away
					float rise = Mth.clamp((t - 3f) / 3f, 0f, 1f) * Mth.clamp((24f - t) / 6f, 0f, 1f);
					if (rise > 0f) {
						for (int i = 0; i < 14; i++) {
							double a = i * Math.PI * 2 / 14 + st.animStart % 7;
							double d = 2.0 + (i % 3) * 1.1;
							Vec3 base = feet.add(Math.cos(a) * d, -0.2, Math.sin(a) * d);
							Vec3 tilt = new Vec3(Math.cos(a) * 0.35, 1, Math.sin(a) * 0.35);
							RecalFx.spike(pose, buffers, base.subtract(cam), tilt, 0.28f, (1.2f + (i % 2) * 0.6f) * rise, 0xFFE0A8FF, 0xFF7A2EA0, (float) a);
						}
					}
				}
				case ChromastoneKit.ANIM_FLASH -> {
					float k = RecalFx.fade(t - 4f, 8f);
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), eye.subtract(cam), 1f + Math.max(0f, t - 4f) * 1.2f, PyroDraw.dim(0xFFFFFFFF, k));
				}
				case ChromastoneKit.ANIM_RELEASE -> {
					float k = RecalFx.fade(t - 3f, 12f);
					RecalFx.ring(pose, buffers, feet.add(0, 0.8, 0).subtract(cam), 0.6f + Math.max(0f, t - 3f) * 0.6f, 1.2f, VIOLET, k);
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), chest.subtract(cam), 1.2f, PyroDraw.dim(VIOLET, k));
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
			if (st.animId == ChromastoneKit.ANIM_DASH && t < 10f && !fp) {
				Vec3 c = feet.add(0, 0.9 * sc, 0);
				RecalFx.cone(pose, buffers, c.add(f.scale(0.4)).subtract(cam), f.scale(-1), 0.6f * sc, 2.6f * sc, WHITE, VIOLET, RecalFx.fade(t, 10f) * 0.7f);
			}
			if ((st.kitFlags & ChromastoneKit.F_STANCE) != 0 && !fp) {
				// the absorb stance: a faceted crystal shell, pulsing
				int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(eye));
				int a = 0x50 + (int) (0x20 * Mth.sin(now * 0.3f));
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), 0.18f, (a << 24) | 0xD890FF, light);
			}
		}
	}
}
