package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Big Chill's client kit. v0.17.2 revamp (user: "delete all the alien models and only use the skins i provided please no
 * extra things untill i say"): no wings, antennae or eye cubes any more -- he is ONLY the player model in one of the user's two
 * skins: {@code big_chill_cloaked} (wings wrapped round him, his normal look) or {@code big_chill} (wings open) while the kit's
 * synced {@link BigChillKit#F_OPEN} is set (flying, diving, breathing, storming, the ultimate, a moment after any attack). The
 * server holds the flag open for 1.5 s after a move, so the swap never flickers, and marks it with a soft frost ring. Phased or
 * cloaked he is see-through. Effects (world pass, {@link RecalFx}): the held breath's cone, the ice shard, Absolute Zero's dome,
 * the blizzard's bands, the frost armour shell and the ice shells on frozen creatures; rings / shards / decals come from the
 * server as QuirkFx.
 */
public final class BigChillClient implements AlienClientKit {
	private static final ResourceLocation SKIN_CLOAKED = ProjectHeroMod.id("textures/entity/omnitrix/big_chill_cloaked.png");
	private static final ResourceLocation SKIN_OPEN = ProjectHeroMod.id("textures/entity/omnitrix/big_chill.png");

	public static final int ICE_CORE = 0xFFF0FCFF;
	public static final int ICE_GLOW = 0xFF6AC8FF;

	private static boolean worldPass;

	// ---------------------------------------------------------------- third-person poses
	private static final float[][] BREATH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.25f, 0, 0, 0, 0, 0, 0, 0, -0.25f),
			QuirkKeyframes.f(50, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.25f, 0, 0, 0, 0, 0, 0, 0, -0.25f), QuirkKeyframes.rest(54) };
	private static final float[][] SHARD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, -0.1f, 0, 0.2f, 0, 0, 0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] GRIP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, 0.2f, 0, -1.5f, -0.2f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(12, -1.4f, 0.2f, 0, -1.4f, -0.2f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(16) };
	// v0.17.2 revamp: Shatter -- a fist closed hard in front of him
	private static final float[][] SHATTER = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.9f, -0.3f, 0.3f, 0.3f, 0, 0, 0.2f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(6, -1.2f, 0.3f, 0, 0.3f, 0, 0, 0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(12) };
	private static final float[][] TAKEOFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 0.8f, -0.3f, 0, -0.8f, -0.1f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.2f),
			QuirkKeyframes.rest(10) };
	private static final float[][] LAND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.2f, 0, 0.5f, 0.2f, 0, -0.5f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.rest(8) };
	private static final float[][] PHASE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, -0.4f, -0.6f, 0, 0.4f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.rest(12) };
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.7f, 0, 0.3f, -2.7f, 0, -0.3f, -0.1f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.2f),
			QuirkKeyframes.f(10, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.7f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.6f),
			QuirkKeyframes.f(44, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.7f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.6f), QuirkKeyframes.rest(48) };
	private static final float[][] FIELD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.6f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
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
	// v0.17.2 revamp: the Ambush -- both arms flung out of the cloak and down on the target
	private static final float[][] AMBUSH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.8f, 0, 0.5f, -2.8f, 0, -0.5f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(12) };

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_BREATH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 25, -20, 10, -0.1f, -0.25f, 0.1f),
			FirstPersonClip.f(50, 25, -20, 10, -0.1f, -0.25f, 0.1f), FirstPersonClip.f(54, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -35, 15, 0, 0.1f, 0.15f, -0.3f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_GRIP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(12, -28, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FIST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 20, 0, 0.12f, 0.12f, -0.35f),
			FirstPersonClip.f(6, 10, 10, 0, 0.05f, -0.05f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
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
			// v0.17.2 revamp: the Ice Shard -- a spinning spike of ice along its flight
			OmnitrixRenderers.projectileLook("big_chill_shard", (e, partial, pose, buffers) -> {
				Vec3 v = e.getDeltaMovement();
				Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();
				Vec3 base = dir.scale(-0.35).add(0, e.getBbHeight() * 0.5, 0);
				RecalFx.spike(pose, buffers, base, dir, 0.12f, 0.7f, 0xFFE8F8FF, 0xFF8ACCF0, (e.tickCount + partial) * 0.4f);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, e.getBbHeight() * 0.5, 0), 0.35f, PyroDraw.dim(ICE_GLOW, 0.6f));
			});
		}
	}

	public static void register() {
		AlienClients.register(Aliens.BIG_CHILL.id(), new BigChillClient());
	}

	@Override
	public ResourceLocation skin() {
		return SKIN_CLOAKED;
	}

	/** v0.17.2 revamp: wings open -> the uncloaked skin; otherwise the cloaked one (his normal look). */
	@Override
	public ResourceLocation skin(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & BigChillKit.F_OPEN) != 0 ? SKIN_OPEN : SKIN_CLOAKED;
	}

	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		int f = Omnitrix.state(p).kitFlags;
		if ((f & BigChillKit.F_PHASE) != 0) {
			return 95;
		}
		return (f & BigChillKit.F_CLOAK) != 0 ? 70 : 255;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case BigChillKit.ANIM_BREATH -> (Omnitrix.state(p).kitFlags & BigChillKit.F_BREATH) != 0 || ticks < 4 ? BREATH : null;
			case BigChillKit.ANIM_SHARD -> SHARD;
			case BigChillKit.ANIM_GRIP -> GRIP;
			case BigChillKit.ANIM_SHATTER -> SHATTER;
			case BigChillKit.ANIM_TAKEOFF -> TAKEOFF;
			case BigChillKit.ANIM_LAND -> LAND;
			case BigChillKit.ANIM_PHASE -> PHASE;
			case BigChillKit.ANIM_DIVE -> DIVE;
			case BigChillKit.ANIM_FIELD -> FIELD;
			case BigChillKit.ANIM_CLOAK -> CLOAK_POSE;
			case BigChillKit.ANIM_ARMOUR -> ARMOUR;
			case BigChillKit.ANIM_BLIZZARD -> BLIZZARD;
			case BigChillKit.ANIM_ZERO -> ZERO;
			case BigChillKit.ANIM_AMBUSH -> AMBUSH;
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
			case BigChillKit.ANIM_SHATTER, BigChillKit.ANIM_AMBUSH -> FP_FIST;
			case BigChillKit.ANIM_ARMOUR -> FP_OUT;
			case BigChillKit.ANIM_TAKEOFF, BigChillKit.ANIM_DIVE -> FP_UP;
			case BigChillKit.ANIM_PHASE, BigChillKit.ANIM_CLOAK -> FP_WRAP;
			case BigChillKit.ANIM_FIELD, BigChillKit.ANIM_LAND -> FP_DOWN;
			case BigChillKit.ANIM_BLIZZARD -> FP_STORM;
			case BigChillKit.ANIM_ZERO -> FP_ZERO;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		var r = p.getRandom();
		// accents only: a snowflake in the breath, the blizzard's flurries
		if ((s.kitFlags & BigChillKit.F_BREATH) != 0 && r.nextInt(3) == 0) {
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
			float now = pl.level().getGameTime() + partial;
			float t = now - st.animStart;
			float sc = pl.getScale();
			Vec3 f = pl.getViewVector(partial);
			Vec3 eye = pl.getEyePosition(partial);
			Vec3 mouth = fp ? eye.add(f.scale(0.6)).add(0, -0.25, 0) : eye.add(f.scale(0.3 * sc)).add(0, -0.12 * sc, 0);
			Vec3 feet = pl.getPosition(partial);
			// the held Freezing Breath: a cone whose reach grows as it is held
			if ((st.kitFlags & BigChillKit.F_BREATH) != 0) {
				long start = st.readyAt(BigChillKit.ID + "." + BigChillKit.BREATH_START);
				float held = start > 0 ? now - start : t;
				float k = Mth.clamp(held / 2f, 0f, 1f) * 0.9f;
				float len = (float) BigChillKit.breathReach(held);
				RecalFx.cone(pose, buffers, mouth.subtract(cam), f, len * 0.3f, len, ICE_CORE, ICE_GLOW, k);
				for (int i = 0; i < 6; i++) {
					float s = ((now * 0.35f + i / 6f) % 1f);
					Vec3 at = mouth.add(f.scale(s * len)).add(Mth.sin(i * 2.1f + now) * s * 0.8, Mth.cos(i * 1.7f + now) * s * 0.6, 0);
					PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at.subtract(cam), 0.18f + s * 0.25f, PyroDraw.dim(ICE_CORE, k * 0.7f));
				}
			}
			if (st.animId == BigChillKit.ANIM_ZERO) {
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
					PyroDraw.sphere(pose, PyroDraw.additive(buffers), r, PyroDraw.dim(ICE_CORE, k * 0.35f), PyroDraw.dim(ICE_GLOW, k * 0.25f), 0.05f, u,
							LightTexture.FULL_BRIGHT);
					pose.popPose();
				}
			}
			if ((st.kitFlags & BigChillKit.F_BLIZZARD) != 0) {
				// the snowstorm: bands of driving snow wheeling round him at three heights
				for (int i = 0; i < 3; i++) {
					float r = 3f + i * 1.3f + Mth.sin(now * 0.2f + i) * 0.3f;
					pose.pushPose();
					Vec3 c = feet.add(0, 0.4 + i * 0.8, 0).subtract(cam);
					pose.translate(c.x, c.y, c.z);
					pose.mulPose(Axis.YP.rotation(now * (0.15f + i * 0.05f)));
					pose.mulPose(Axis.ZP.rotation(Mth.sin(now * 0.1f + i * 2) * 0.12f));
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
