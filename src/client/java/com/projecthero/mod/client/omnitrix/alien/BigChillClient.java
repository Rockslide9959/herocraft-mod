package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic version): Big Chill's client kit -- the player body in his deep-blue skin with the pale
 * chest, two big moth wings off the shoulders (folded down his back like a cloak, spread and beating in flight, wrapped right
 * round him while cloaked), two antennae and glowing green eyes. Phased he is see-through. The freezing breath cone, the ice
 * shard, the nova / dive rings, the blizzard's swirling bands, Absolute Zero's dome and the ice shells on frozen creatures are
 * rendered in the world pass ({@link RecalFx}).
 */
public final class BigChillClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/big_chill.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/big_chill_extras.png");
	// extras sheet rectangles (scratchpad/recal_v0172/big_chill/gen_big_chill.js)
	private static final float[] WING = { 0, 0, 32, 32 };
	private static final float[] ANTENNA = { 32, 0, 34, 8 };
	private static final float[] TIP = { 34, 0, 38, 4 };
	private static final float[] EYE = { 40, 0, 44, 4 };
	private static final float[] CLOAK = { 0, 32, 16, 48 };

	public static final int ICE_CORE = 0xFFF0FCFF;
	public static final int ICE_GLOW = 0xFF6AC8FF;

	private static boolean worldPass;

	// ---------------------------------------------------------------- third-person poses
	private static final float[][] BREATH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.25f, 0, 0, 0, 0, 0, 0, 0, -0.25f),
			QuirkKeyframes.f(12, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.25f, 0, 0, 0, 0, 0, 0, 0, -0.25f), QuirkKeyframes.rest(16) };
	private static final float[][] SHARD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.2f, 0, 0, 0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] GRIP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, 0.2f, 0, -1.5f, -0.2f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(12, -1.4f, 0.2f, 0, -1.4f, -0.2f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(16) };
	private static final float[][] NOVA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.3f, 0, 1.7f, -0.3f, 0, -1.7f, -0.2f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] TAKEOFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 0.8f, -0.3f, 0, -0.8f, -0.1f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.2f),
			QuirkKeyframes.rest(10) };
	private static final float[][] PHASE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, -0.4f, -0.6f, 0, 0.4f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.rest(12) };
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.7f, 0, 0.3f, -2.7f, 0, -0.3f, -0.1f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.2f),
			QuirkKeyframes.f(10, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.7f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.6f),
			QuirkKeyframes.f(44, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.7f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.6f), QuirkKeyframes.rest(48) };
	private static final float[][] FLOOR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.6f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			QuirkKeyframes.f(10, -0.5f, 0, 0.6f, -0.5f, 0, -0.6f, 0.6f, 0, 0, 0, 0, 0, 0, 0, 0.4f), QuirkKeyframes.rest(16) };
	private static final float[][] CLOAK_POSE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0.6f, 0, -0.9f, -0.6f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.rest(10) };
	private static final float[][] ARMOUR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.2f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(8, 0, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0), QuirkKeyframes.rest(12) };
	private static final float[][] BLIZZARD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.2f, 0, 1.4f, -0.2f, 0, -1.4f, -0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(76, -0.2f, 0, 1.4f, -0.2f, 0, -1.4f, -0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.2f), QuirkKeyframes.rest(80) };
	private static final float[][] ZERO = { QuirkKeyframes.rest(0), QuirkKeyframes.f(5, 0.5f, 0, 0.2f, 0.5f, 0, -0.2f, 0.5f, 0, 0.3f, 0, 0, 0.3f, 0, 0, 0.4f),
			QuirkKeyframes.f(19, 0.6f, 0, 0.2f, 0.6f, 0, -0.2f, 0.6f, 0, 0.3f, 0, 0, 0.3f, 0, 0, 0.5f),
			QuirkKeyframes.f(22, -0.5f, 0, 1.8f, -0.5f, 0, -1.8f, -0.3f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.3f),
			QuirkKeyframes.f(30, -0.5f, 0, 1.8f, -0.5f, 0, -1.8f, -0.3f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.3f), QuirkKeyframes.rest(34) };

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_BREATH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 25, -20, 10, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(12, 25, -20, 10, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -35, 15, 0, 0.1f, 0.15f, -0.3f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_GRIP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(12, -28, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OUT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -10, 0, 0, -0.2f, 0.1f),
			FirstPersonClip.f(6, -20, -45, 30, -0.35f, 0.1f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -50, -25, 25, -0.2f, 0.35f, 0.1f),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WRAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 45, 0, 0.3f, 0.05f, 0),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 30, 10, 0, 0.05f, -0.35f, 0.15f),
			FirstPersonClip.f(10, 30, 10, 0, 0.05f, -0.35f, 0.15f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STORM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -45, 30, -0.35f, 0.1f, 0.1f),
			FirstPersonClip.f(76, -20, -45, 30, -0.35f, 0.1f, 0.1f), FirstPersonClip.f(80, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ZERO = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 30, 20, 0, 0.1f, -0.3f, 0.15f),
			FirstPersonClip.f(19, 32, 22, 0, 0.12f, -0.32f, 0.15f), FirstPersonClip.f(22, -20, -45, 30, -0.35f, 0.1f, 0.1f),
			FirstPersonClip.f(34, 0, 0, 0, 0, 0, 0)).both();

	public BigChillClient() {
		if (!worldPass) {
			worldPass = true;
			WorldRenderEvents.AFTER_ENTITIES.register(BigChillClient::renderWorld);
		}
	}

	public static void register() {
		AlienClients.register(Aliens.BIG_CHILL.id(), new BigChillClient());
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		int f = Omnitrix.state(p).kitFlags;
		if ((f & BigChillKit.F_PHASE) != 0) {
			return 95;
		}
		return (f & BigChillKit.F_CLOAK) != 0 ? 70 : 255;
	}

	private static float u(float[] r, int i) {
		return r[i] / 64f;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		int f = Omnitrix.state(p).kitFlags;
		boolean flying = (f & (BigChillKit.F_FLYING | BigChillKit.F_DIVING)) != 0 && !p.onGround();
		boolean cloak = (f & BigChillKit.F_CLOAK) != 0;
		boolean ghost = (f & BigChillKit.F_PHASE) != 0 || cloak;
		int tint = ghost ? 0x80FFFFFF : 0xFFFFFFFF;
		RenderType type = ghost ? RenderType.entityTranslucent(EXTRAS) : RenderType.entityCutoutNoCull(EXTRAS);
		AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 2.5f / 16f, 1f / 16f, 2.2f / 16f);
				float flap = flying ? Mth.sin(age * 0.3f) * 0.3f : 0f;
				// round about y: folded back along the spine / spread out / wrapped round the front
				float yaw = cloak ? side * 1.5f : flying ? -side * (0.45f + flap) : -side * 1.3f;
				float lift = flying ? -side * 0.35f : -side * 0.08f;
				pose.mulPose(Axis.YP.rotation(yaw));
				pose.mulPose(Axis.ZP.rotation(lift));
				float x0 = side < 0 ? -13f : 0f, x1 = side < 0 ? 0f : 13f;
				// the upper wing (forewing) and the lower one, as two plates; the inside face shows the pale cloak colour
				AlienParts.box(pose, buffers.getBuffer(type), x0, -3f, -0.25f, x1, 9f, 0.25f, u(WING, 0), u(WING, 1), u(WING, 2), u(WING, 3), tint, light);
				float l0 = side < 0 ? -10f : 0f, l1 = side < 0 ? 0f : 10f;
				AlienParts.box(pose, buffers.getBuffer(type), l0, 9f, -0.2f, l1, 17f, 0.2f, u(WING, 0), u(WING, 1), u(WING, 2), u(WING, 3), tint, light);
				AlienParts.box(pose, buffers.getBuffer(type), x0 * 0.9f, -2.5f, 0.26f, x1 * 0.9f, 15f, 0.3f, u(CLOAK, 0), u(CLOAK, 1), u(CLOAK, 2), u(CLOAK, 3),
						tint, light);
				pose.popPose();
			}
		});
		AlienParts.on(pose, model.head, () -> {
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 1.8f / 16f, -8f / 16f, -2f / 16f);
				pose.mulPose(Axis.ZP.rotation(side * (0.35f + Mth.sin(age * 0.12f + side) * 0.05f)));
				pose.mulPose(Axis.XP.rotation(-0.25f));
				AlienParts.box(pose, buffers.getBuffer(type), -0.4f, -7f, -0.4f, 0.4f, 0f, 0.4f, u(ANTENNA, 0), u(ANTENNA, 1), u(ANTENNA, 2), u(ANTENNA, 3), tint,
						light);
				AlienParts.box(pose, buffers.getBuffer(type), -1.2f, -8.5f, -0.5f, 1.2f, -6.5f, 0.5f, u(TIP, 0), u(TIP, 1), u(TIP, 2), u(TIP, 3), tint, light);
				pose.popPose();
			}
			var eyes = buffers.getBuffer(RenderType.eyes(EXTRAS));
			AlienParts.box(pose, eyes, -3.1f, -6.1f, -4.1f, -0.9f, -4.9f, -4.02f, u(EYE, 0), u(EYE, 1), u(EYE, 2), u(EYE, 3), 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
			AlienParts.box(pose, eyes, 0.9f, -6.1f, -4.1f, 3.1f, -4.9f, -4.02f, u(EYE, 0), u(EYE, 1), u(EYE, 2), u(EYE, 3), 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case BigChillKit.ANIM_BREATH -> BREATH;
			case BigChillKit.ANIM_SHARD -> SHARD;
			case BigChillKit.ANIM_GRIP -> GRIP;
			case BigChillKit.ANIM_NOVA -> NOVA;
			case BigChillKit.ANIM_TAKEOFF -> TAKEOFF;
			case BigChillKit.ANIM_PHASE -> PHASE;
			case BigChillKit.ANIM_DIVE -> DIVE;
			case BigChillKit.ANIM_FLOOR -> FLOOR;
			case BigChillKit.ANIM_CLOAK -> CLOAK_POSE;
			case BigChillKit.ANIM_ARMOUR -> ARMOUR;
			case BigChillKit.ANIM_BLIZZARD -> BLIZZARD;
			case BigChillKit.ANIM_ZERO -> ZERO;
			default -> null;
		};
		if (clip != null && QuirkKeyframes.play(m, clip, ticks, 1f)) {
			return true;
		}
		int f = Omnitrix.state(p).kitFlags;
		if ((f & BigChillKit.F_CLOAK) != 0) {
			// arms folded in under the wrapped wings
			m.rightArm.xRot = -0.9f;
			m.leftArm.xRot = -0.9f;
			m.rightArm.yRot = 0.6f;
			m.leftArm.yRot = -0.6f;
			return true;
		}
		if ((f & BigChillKit.F_FLYING) != 0 && !p.onGround()) {
			// a ghost's drift: legs together and hanging, arms loose
			m.rightLeg.xRot = 0.2f;
			m.leftLeg.xRot = 0.25f;
			m.rightArm.xRot = 0.15f + Mth.sin(age * 0.1f) * 0.05f;
			m.leftArm.xRot = 0.15f - Mth.sin(age * 0.1f) * 0.05f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			return true;
		}
		return false;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case BigChillKit.ANIM_BREATH -> FP_BREATH;
			case BigChillKit.ANIM_SHARD -> FP_THROW;
			case BigChillKit.ANIM_GRIP -> FP_GRIP;
			case BigChillKit.ANIM_NOVA, BigChillKit.ANIM_ARMOUR -> FP_OUT;
			case BigChillKit.ANIM_TAKEOFF, BigChillKit.ANIM_DIVE -> FP_UP;
			case BigChillKit.ANIM_PHASE, BigChillKit.ANIM_CLOAK -> FP_WRAP;
			case BigChillKit.ANIM_FLOOR -> FP_DOWN;
			case BigChillKit.ANIM_BLIZZARD -> FP_STORM;
			case BigChillKit.ANIM_ZERO -> FP_ZERO;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		var r = p.getRandom();
		// accents: a snowflake off the breath, the blizzard's flurries
		if (s.animId == BigChillKit.ANIM_BREATH && p.level().getGameTime() - s.animStart < 12 && r.nextInt(2) == 0) {
			Vec3 f = p.getLookAngle();
			Vec3 at = p.getEyePosition().add(f.scale(2 + r.nextDouble() * 5));
			p.level().addParticle(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, f.x * 0.2, f.y * 0.2, f.z * 0.2);
		}
		if ((s.kitFlags & BigChillKit.F_BLIZZARD) != 0 && r.nextInt(2) == 0) {
			double a = r.nextDouble() * Math.PI * 2, d = 2 + r.nextDouble() * 4;
			p.level().addParticle(ParticleTypes.SNOWFLAKE, p.getX() + Math.cos(a) * d, p.getY() + r.nextDouble() * 2.5, p.getZ() + Math.sin(a) * d, -Math.sin(a) * 0.3,
					0, Math.cos(a) * 0.3);
		}
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
		boolean any = false;
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!BigChillKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			any = true;
			boolean fp = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			float t = pl.level().getGameTime() + partial - st.animStart;
			float sc = pl.getScale();
			Vec3 f = pl.getViewVector(partial);
			Vec3 eye = pl.getEyePosition(partial);
			Vec3 mouth = fp ? eye.add(f.scale(0.6)).add(0, -0.25, 0) : eye.add(f.scale(0.3 * sc)).add(0, -0.12 * sc, 0);
			Vec3 feet = pl.getPosition(partial);
			switch (st.animId) {
				case BigChillKit.ANIM_BREATH -> {
					float k = RecalFx.fade(t - 1f, 12f);
					float grow = Mth.clamp(t / 4f, 0.2f, 1f);
					float len = (float) BigChillKit.BREATH_RANGE * grow;
					RecalFx.cone(pose, buffers, mouth.subtract(cam), f, 2.4f * grow, len, ICE_CORE, ICE_GLOW, k * 0.9f);
					// a few drifting ice crystals inside the stream
					for (int i = 0; i < 6; i++) {
						float s = ((t * 0.35f + i / 6f) % 1f);
						Vec3 at = mouth.add(f.scale(s * len)).add(Mth.sin(i * 2.1f + t) * s * 0.8, Mth.cos(i * 1.7f + t) * s * 0.6, 0);
						PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at.subtract(cam), 0.18f + s * 0.25f, PyroDraw.dim(ICE_CORE, k * 0.7f));
					}
				}
				case BigChillKit.ANIM_SHARD -> {
					float k = RecalFx.fade(t, 5f);
					Vec3 end = RecalFx.traceEnd(pl, eye, f, 20.0);
					Vec3 head = mouth.lerp(end, Mth.clamp(t / 3f, 0f, 1f));
					RecalFx.beam(pose, buffers, mouth.subtract(cam), head.subtract(cam), 0.12f, ICE_CORE, ICE_GLOW, k);
					RecalFx.spike(pose, buffers, head.subtract(cam), f, 0.12f, 0.5f, 0xFFE8F8FF, 0xFF8ACCF0, t * 0.2f);
				}
				case BigChillKit.ANIM_NOVA -> {
					float k = RecalFx.fade(t, 12f);
					RecalFx.ring(pose, buffers, feet.add(0, 0.3, 0).subtract(cam), 0.6f + Math.min(t, 12f) * 0.42f, 0.9f, ICE_GLOW, k);
				}
				case BigChillKit.ANIM_ZERO -> {
					if (t < BigChillKit.ZERO_WINDUP) {
						// the cold drawn in: a ring closing on him
						float g = t / BigChillKit.ZERO_WINDUP;
						RecalFx.ring(pose, buffers, feet.add(0, 0.8, 0).subtract(cam), (float) BigChillKit.ZERO_RADIUS * (1f - g) + 0.6f, 1.6f, ICE_GLOW, 0.3f + g * 0.6f);
					} else {
						float u = t - BigChillKit.ZERO_WINDUP;
						float k = RecalFx.fade(u, 14f);
						float r = Math.min(1f, u / 5f) * (float) BigChillKit.ZERO_RADIUS;
						pose.pushPose();
						Vec3 c = feet.add(0, 0.5, 0).subtract(cam);
						pose.translate(c.x, c.y, c.z);
						PyroDraw.sphere(pose, PyroDraw.additive(buffers), r, PyroDraw.dim(ICE_CORE, k * 0.35f), PyroDraw.dim(ICE_GLOW, k * 0.25f), 0.05f, u, LightTexture.FULL_BRIGHT);
						pose.popPose();
						RecalFx.ring(pose, buffers, feet.add(0, 0.1, 0).subtract(cam), r, 0.6f, ICE_CORE, k);
					}
				}
				default -> {
				}
			}
			if ((st.kitFlags & BigChillKit.F_BLIZZARD) != 0) {
				// the snowstorm: bands of driving snow wheeling round him at three heights
				float time = pl.level().getGameTime() + partial;
				for (int i = 0; i < 3; i++) {
					float r = 3f + i * 1.3f + Mth.sin(time * 0.2f + i) * 0.3f;
					pose.pushPose();
					Vec3 c = feet.add(0, 0.4 + i * 0.8, 0).subtract(cam);
					pose.translate(c.x, c.y, c.z);
					pose.mulPose(Axis.YP.rotation(time * (0.15f + i * 0.05f)));
					pose.mulPose(Axis.ZP.rotation(Mth.sin(time * 0.1f + i * 2) * 0.12f));
					PyroDraw.band(pose, PyroDraw.additive(buffers), r, 0.35f, PyroDraw.dim(ICE_GLOW, 0.35f), 0xFF000000);
					pose.popPose();
				}
			}
			if ((st.kitFlags & BigChillKit.F_ARMOUR) != 0 && !fp) {
				int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(eye));
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), 0.12f, 0x58B8E8FF, light);
			}
		}
		if (!any) {
			return;
		}
		// the ice shells on everything a Big Chill froze solid (ticksFrozen is synced)
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof LivingEntity le && BigChillKit.shelled(le) && le.distanceToSqr(cam) < 64 * 64 && !(le == mc.player && mc.options.getCameraType().isFirstPerson())) {
				Vec3 at = le.getPosition(partial);
				int light = LevelRenderer.getLightColor(le.level(), le.blockPosition());
				RecalFx.shell(pose, buffers, cam, le.getBoundingBox().move(at.subtract(le.position())), 0.15f, 0x90C8F0FF, light);
			}
		}
	}
}
