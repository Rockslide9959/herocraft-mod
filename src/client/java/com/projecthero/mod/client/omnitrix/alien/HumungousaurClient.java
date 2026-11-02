package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.humungousaur.HumungousaurKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Humungousaur's client kit (basic version) -- the player body in his tan hide (cream belly plates, black shorts) with a
 * thick tail and back plates; at the biggest size step the plates become bone spikes and the tail grows a club. Rendered effects
 * (world pass): the shockwave rings of the Ground Pound / Dino Leap landing / Titanic Slam, the Tail Sweep's arc, the Primal Roar's
 * rings, the Grow / Shrink burst; the Boulder Toss flies as a real chunk of ground. Dust particles are accents only.
 */
public final class HumungousaurClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/humungousaur.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/humungousaur_extras.png");
	private static final float S = 1f / 64f;
	private static final int DUST = 0xB58A6A44;

	// f(tick, rArm xyz, lArm xyz, body x y, rLeg xyz, lLeg xyz, head x)
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.0f, 0.4f, 0.6f, 0, 0, 0, 0, 0.45f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(4, -1.65f, -0.15f, 0, 0.3f, 0, 0, 0.15f, -0.4f, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] UPPER = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.2f, 0, 0, 0, 0.35f, 0.3f, -0.3f, 0, 0, 0.3f, 0, 0, 0.2f),
			QuirkKeyframes.f(6, -2.9f, -0.2f, 0, 0.3f, 0, 0, -0.2f, -0.3f, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(12) };
	private static final float[][] POUND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -0.9f, 0, 0, -0.9f, 0, 0, 0.7f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f),
			QuirkKeyframes.f(14, -0.9f, 0, 0, -0.9f, 0, 0, 0.7f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f), QuirkKeyframes.rest(20) };
	private static final float[][] TAIL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.1f, -0.3f, 0, -1.1f, 0.2f, 1.6f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(7, -0.3f, 0, 1.1f, -0.3f, 0, -1.1f, 0.2f, 4.7f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(10, 0, 0, 0.3f, 0, 0, -0.3f, 0, 6.2832f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, 0.5f),
			QuirkKeyframes.f(6, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, 0.5f),
			QuirkKeyframes.f(9, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, 0.5f),
			QuirkKeyframes.f(12, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, 0.5f),
			QuirkKeyframes.f(15, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, 0.5f),
			QuirkKeyframes.f(18, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, 0.5f), QuirkKeyframes.rest(24) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.3f, 0, -0.7f, 0, 0, -0.7f, 0, 0, -0.2f),
			QuirkKeyframes.f(20, -2.4f, 0, 0.3f, -2.4f, 0, -0.3f, -0.1f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.3f), QuirkKeyframes.rest(26) };
	private static final float[][] LAND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, 0.5f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.2f),
			QuirkKeyframes.rest(12) };
	private static final float[][] TOSS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.5f, 0, -0.4f, 0, 0, -0.4f, 0, 0, 0.3f),
			QuirkKeyframes.f(8, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.25f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(11, -1.2f, 0, 0, -1.2f, 0, 0, 0.35f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0.1f), QuirkKeyframes.rest(16) };
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(7, -2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.2f, 0.8f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(10, -1.0f, 0, 0, -1.0f, 0, 0, 0.3f, -0.6f, -0.3f, 0, 0, 0.2f, 0, 0, 0.1f), QuirkKeyframes.rest(15) };
	private static final float[][] GROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.6f, 0, 1.3f, -0.6f, 0, -1.3f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.35f),
			QuirkKeyframes.f(14, -0.7f, 0, 1.4f, -0.7f, 0, -1.4f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.35f), QuirkKeyframes.rest(20) };
	private static final float[][] SHRINK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.5f, 0.5f, 0, -0.5f, -0.5f, 0, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.rest(12) };
	private static final float[][] ROAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.9f, 0.4f, 0, -0.9f, -0.3f, 0, -0.2f, 0, 0.15f, 0.2f, 0, -0.15f, -0.6f),
			QuirkKeyframes.f(18, 0.4f, 0, 1.0f, 0.4f, 0, -1.0f, -0.3f, 0, -0.2f, 0, 0.15f, 0.2f, 0, -0.15f, -0.6f), QuirkKeyframes.rest(24) };
	private static final float[][] SLAM_UP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, -0.5f, 0, 0, -0.5f, 0, 0, -0.3f),
			QuirkKeyframes.f(60, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, -0.5f, 0, 0, -0.5f, 0, 0, -0.3f) };
	private static final float[][] SLAM = { QuirkKeyframes.f(0, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, -0.5f, 0, 0, -0.5f, 0, 0, -0.3f),
			QuirkKeyframes.f(3, -0.8f, 0, 0, -0.8f, 0, 0, 0.8f, 0, -0.7f, 0, 0.25f, -0.7f, 0, -0.25f, 0.3f),
			QuirkKeyframes.f(14, -0.8f, 0, 0, -0.8f, 0, 0, 0.8f, 0, -0.7f, 0, 0.25f, -0.7f, 0, -0.25f, 0.3f), QuirkKeyframes.rest(22) };

	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 10, -10, 0, 0, -0.1f, 0.15f),
			FirstPersonClip.f(4, -20, 25, 0, 0.15f, 0.08f, -0.35f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_UPPER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, 15, 0, 0.1f, -0.35f, 0f),
			FirstPersonClip.f(6, -75, 15, 0, 0.1f, 0.5f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 5, 0, 0.05f, 0.55f, 0.2f),
			FirstPersonClip.f(7, 30, 10, 0, 0.05f, -0.4f, -0.1f), FirstPersonClip.f(14, 30, 10, 0, 0.05f, -0.4f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -10, -35, -20, -0.25f, 0f, 0f),
			FirstPersonClip.f(8, -10, 40, 20, 0.3f, 0f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(18, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 20, 10, 0, 0.05f, -0.3f, 0.1f),
			FirstPersonClip.f(8, -75, 10, 0, 0.05f, 0.55f, 0.15f), FirstPersonClip.f(11, -10, 20, 0, 0.12f, 0.1f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_FLEX = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -50, -20, -30, -0.1f, 0.3f, 0f),
			FirstPersonClip.f(14, -50, -20, -30, -0.1f, 0.3f, 0f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, 10, 10, 0.1f, 0.55f, 0f),
			FirstPersonClip.f(60, -70, 10, 10, 0.1f, 0.55f, 0f)).both();

	private static boolean registered;

	private HumungousaurClient() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
		AlienClients.register(Aliens.HUMUNGOUSAUR.id(), new HumungousaurClient());
		if (!registered) {
			registered = true;
			OmnitrixRenderers.projectileLook("humungousaur_boulder", HumungousaurClient::boulder);
			WorldRenderEvents.AFTER_ENTITIES.register(HumungousaurClient::renderWorld);
		}
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// ---------------------------------------------------------------- body extras

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		int stage = HumungousaurKit.stage(p);
		float sway = Mth.sin(age * 0.1f) * 6f + Mth.cos(p.walkAnimation.position(partial) * 0.6f) * 8f * p.walkAnimation.speed(partial);
		AlienParts.on(pose, model.body, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			// the tail: three tapering segments off the base of his back, swaying
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 1.6f / 16f);
			pose.mulPose(Axis.XP.rotationDegrees(-62f));
			pose.mulPose(Axis.YP.rotationDegrees(sway));
			float[] w = { 3.2f, 2.5f, 1.8f };
			float[] len = { 5.5f, 5.0f, 4.5f };
			for (int i = 0; i < 3; i++) {
				AlienParts.box(pose, vc, -w[i], -w[i] * 0.9f, 0f, w[i], w[i] * 0.9f, len[i] + 0.3f, 0f, 0f, 8 * S, 8 * S, 0xFFFFFFFF, light);
				pose.translate(0, 0, len[i] / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(-10f));
				pose.mulPose(Axis.YP.rotationDegrees(sway * 0.6f));
			}
			if (stage >= 2) {
				// the tail club
				AlienParts.box(pose, vc, -2.4f, -2.2f, -0.5f, 2.4f, 2.2f, 3.2f, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
			}
			pose.popPose();
			// back plates down his spine; bone spikes once he has grown
			for (int i = 0; i < 4; i++) {
				float y = 1.0f + i * 2.6f;
				float h = stage >= 2 ? 3.2f - i * 0.3f : stage == 1 ? 2.0f : 1.2f;
				float u0 = stage >= 2 ? 16 * S : 8 * S;
				AlienParts.box(pose, vc, -0.8f, y, 2.0f, 0.8f, y + 1.8f, 2.0f + h, u0, 0f, u0 + 4 * S, 4 * S, 0xFFFFFFFF, light);
			}
		});
		if (stage >= 2) {
			// spikes on his forearms too
			for (var arm : new net.minecraft.client.model.geom.ModelPart[] { model.rightArm, model.leftArm }) {
				AlienParts.on(pose, arm, () -> {
					VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
					AlienParts.box(pose, vc, -0.6f, 4.0f, 2.0f, 0.6f, 5.4f, 4.2f, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
				});
			}
		}
		// a heavy brow ridge
		AlienParts.on(pose, model.head, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			AlienParts.box(pose, vc, -4.3f, -6.6f, -4.6f, 4.3f, -5.4f, -3.8f, 8 * S, 0f, 12 * S, 4 * S, 0xFFFFFFFF, light);
		});
	}

	// ---------------------------------------------------------------- poses

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		float[][] clip = switch (animId) {
			case HumungousaurKit.ANIM_PUNCH -> PUNCH;
			case HumungousaurKit.ANIM_UPPER -> UPPER;
			case HumungousaurKit.ANIM_POUND -> POUND;
			case HumungousaurKit.ANIM_TAIL -> TAIL;
			case HumungousaurKit.ANIM_CHARGE -> CHARGE;
			case HumungousaurKit.ANIM_LEAP -> (s.kitFlags & HumungousaurKit.F_LEAP) != 0 || ticks < 20 ? LEAP : null;
			case HumungousaurKit.ANIM_LAND -> LAND;
			case HumungousaurKit.ANIM_TOSS -> TOSS;
			case HumungousaurKit.ANIM_GRAB -> GRAB;
			case HumungousaurKit.ANIM_GROW -> GROW;
			case HumungousaurKit.ANIM_SHRINK -> SHRINK;
			case HumungousaurKit.ANIM_ROAR -> ROAR;
			case HumungousaurKit.ANIM_SLAM_UP -> (s.kitFlags & HumungousaurKit.F_SLAM) != 0 ? SLAM_UP : null;
			case HumungousaurKit.ANIM_SLAM -> SLAM;
			default -> null;
		};
		// the slam's rise holds its last frame until the dive
		float at = clip == SLAM_UP ? Math.min(ticks, 59f) : ticks;
		boolean played = clip != null && QuirkKeyframes.play(m, clip, at, 1f);
		if (!played) {
			// a heavy, hunched stance: arms a little out
			m.rightArm.zRot += 0.12f;
			m.leftArm.zRot -= 0.12f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case HumungousaurKit.ANIM_PUNCH -> FP_PUNCH;
			case HumungousaurKit.ANIM_UPPER -> FP_UPPER;
			case HumungousaurKit.ANIM_POUND, HumungousaurKit.ANIM_SLAM, HumungousaurKit.ANIM_LAND -> FP_SLAM;
			case HumungousaurKit.ANIM_TAIL -> FP_SWEEP;
			case HumungousaurKit.ANIM_CHARGE, HumungousaurKit.ANIM_LEAP -> FP_RUN;
			case HumungousaurKit.ANIM_TOSS, HumungousaurKit.ANIM_GRAB -> FP_THROW;
			case HumungousaurKit.ANIM_GROW, HumungousaurKit.ANIM_SHRINK, HumungousaurKit.ANIM_ROAR -> FP_FLEX;
			case HumungousaurKit.ANIM_SLAM_UP -> FP_RAISE;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & HumungousaurKit.F_CHARGE) != 0 && p.onGround()) {
			// dust kicked up behind the stampede
			BlockState under = p.level().getBlockState(p.blockPosition().below());
			if (!under.isAir()) {
				var r = p.getRandom();
				p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX() + (r.nextDouble() - 0.5) * p.getBbWidth(), p.getY() + 0.1,
						p.getZ() + (r.nextDouble() - 0.5) * p.getBbWidth(), 0, 0.1, 0);
			}
		}
	}

	@Override
	public float[] badge() {
		// on the chest, above the belly plates
		return new float[] { 0f, 3.5f, -2.05f, 3.4f };
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
			if (!HumungousaurKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			float t = pl.level().getGameTime() - st.animStart + partial;
			int stage = HumungousaurKit.stage(pl);
			Vec3 feet = pl.getPosition(partial);
			switch (st.animId) {
				case HumungousaurKit.ANIM_POUND -> {
					if (t >= 6 && t < 22) {
						shockwave(feet, (float) (HumungousaurKit.POUND_RADIUS + 1.5 * stage), t - 6, 16f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_LAND -> {
					if (t < 14) {
						shockwave(feet, (float) (HumungousaurKit.LAND_RADIUS + stage), t, 14f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_SLAM -> {
					if ((st.kitFlags & HumungousaurKit.F_SLAM) == 0 && pl.onGround() && t < 24) {
						shockwave(feet, (float) (HumungousaurKit.SLAM_RADIUS + 2 * stage), t, 24f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_TAIL -> {
					if (t < 12) {
						sweep(pl, feet, (float) (HumungousaurKit.TAIL_RADIUS + stage), t, 12f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_ROAR -> {
					if (t < 18) {
						roar(pl, partial, (float) (HumungousaurKit.ROAR_RANGE + stage * 2), t, 18f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_GROW, HumungousaurKit.ANIM_SHRINK -> {
					if (t < 12) {
						shockwave(feet, 1.5f + pl.getBbWidth() * 2f, t, 12f, cam, pose, buffers);
					}
				}
				default -> {
				}
			}
		}
	}

	/**
	 * A shockwave: a flat ring of dust racing out along the ground to {@code radius}, with a low wall of churned earth standing up at
	 * its front.
	 */
	private static void shockwave(Vec3 at, float radius, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float k = Mth.clamp(t / (life * 0.75f), 0f, 1f);
		float fade = t > life * 0.5f ? Math.max(0f, 1f - (t - life * 0.5f) / (life * 0.5f)) : 1f;
		float r = radius * (float) Math.sin(k * Math.PI * 0.5);
		Vec3 o = at.subtract(cam);
		int a = (int) (0xB5 * fade);
		int dust = (a << 24) | (DUST & 0xFFFFFF);
		int clear = DUST & 0xFFFFFF;
		pose.pushPose();
		pose.translate(o.x, o.y + 0.06, o.z);
		VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		PyroDraw.annulus(pose, tr, Math.max(0f, r - 1.4f), r, clear, dust, 0f);
		pose.translate(0, 0.35 * fade, 0);
		VertexConsumer tr2 = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		PyroDraw.band(pose, tr2, r, 0.35f * fade, dust, clear);
		pose.popPose();
	}

	/** The Tail Sweep: a pale arc whipping round him at hip height. */
	private static void sweep(Player pl, Vec3 at, float radius, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float fade = 1f - t / life;
		Vec3 o = at.subtract(cam);
		pose.pushPose();
		pose.translate(o.x, o.y + pl.getBbHeight() * 0.3, o.z);
		VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		int a = (int) (0x90 * fade);
		PyroDraw.band(pose, tr, radius * 0.85f, 0.25f, (a << 24) | 0xE8DCC0, 0x00E8DCC0);
		pose.popPose();
	}

	/** The Primal Roar: rings of shaken air rolling out ahead of his jaws. */
	private static void roar(Player pl, float partial, float range, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 mouth = pl.getEyePosition(partial).add(0, -0.15 * pl.getScale(), 0);
		Vec3 dir = pl.getViewVector(partial);
		float fade = t > life * 0.6f ? Math.max(0f, 1f - (t - life * 0.6f) / (life * 0.4f)) : 1f;
		float front = t * 2.2f;
		Vec3 o = mouth.subtract(cam);
		pose.pushPose();
		pose.translate(o.x, o.y, o.z);
		PyroDraw.alignY(pose, dir);
		VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		for (float s = front % 1.4f; s <= Math.min(front, range); s += 1.4f) {
			float k = s / range;
			int a = (int) (0x80 * fade * (1f - 0.7f * k));
			pose.pushPose();
			pose.translate(0, s, 0);
			PyroDraw.band(pose, tr, 0.5f + 4.5f * k, 0.12f, (a << 24) | 0xF0E8D8, 0x00F0E8D8);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The Boulder Toss in flight: a tumbling chunk of the ground. */
	private static void boulder(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		var blocks = Minecraft.getInstance().getBlockRenderer();
		int light = LevelRenderer.getLightColor(e.level(), e.blockPosition());
		float age = e.tickCount + partial;
		float s = e.size() * 1.4f;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(Axis.XP.rotationDegrees(age * 18f));
		pose.mulPose(Axis.ZP.rotationDegrees(age * 11f));
		pose.scale(s, s, s);
		pose.translate(-0.5, -0.5, -0.5);
		blocks.renderSingleBlock(Blocks.COARSE_DIRT.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.translate(0.2, 0.55, 0.15);
		pose.scale(0.6f, 0.6f, 0.6f);
		blocks.renderSingleBlock(Blocks.COBBLESTONE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
