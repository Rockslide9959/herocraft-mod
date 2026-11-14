package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
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
import com.projecthero.mod.omnitrix.alien.arctiguana.ArctiguanaKit;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
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
 * v0.17.3 Arctiguana's client kit (user: "make aliens based on their abilities and stuff they do in the show please, replace the pixel
 * omnitrix on their skin with our omnitrix badge please"). The user's skin on the player body, its painted wrist Omnitrix painted out
 * ({@code scratchpad/omnitrix_new/gen_skins.js}) and the mod's badge in its place on the outside of his left wrist; a long tapering
 * reptile tail ({@link TubeTail}, his skin's blues); cold breath misting from his mouth now and then; the Frost Armour's ice shell; and
 * the block of ice round everything he froze solid (Big Chill's shell, drawn here too so it shows without a Big Chill in view).
 */
public final class ArctiguanaClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/arctiguana.png");
	/** The wrist badge: the left arm's outer side (+x), at the skin's painted symbol (rows 59..61 = arm y 6..8). */
	public static final float[] BADGE = { 3.3f, 7.0f, 0f, 3.0f, 1f };
	private static final TubeTail TAIL = new TubeTail("arctiguana_tail", 7, 0.16, 2.1f, 0.45f, 10.5f, 1.7f);
	private static final int ICE_GLOW = 0x9AE0FF;

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.ARCTIGUANA.id(), new ArctiguanaClient());
		if (!registered) {
			registered = true;
			WorldRenderEvents.AFTER_ENTITIES.register(ArctiguanaClient::renderWorld);
			// the Ice Shard Spit: a spinning spike of ice, Big Chill's look
			OmnitrixRenderers.projectileLook("arctiguana_shard", (e, partial, pose, buffers) -> {
				Vec3 v = e.getDeltaMovement();
				Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();
				Vec3 base = dir.scale(-0.3).add(0, e.getBbHeight() * 0.5, 0);
				RecalFx.spike(pose, buffers, base, dir, 0.1f, 0.6f, 0xFFE8F8FF, 0xFF6AC8F0, (e.tickCount + partial) * 0.4f);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, e.getBbHeight() * 0.5, 0), 0.3f, PyroDraw.dim(ICE_GLOW, 0.6f));
			});
			// v0.17.4 Shard Fan: the same spikes, a little smaller
			OmnitrixRenderers.projectileLook("arctiguana_fan", (e, partial, pose, buffers) -> {
				Vec3 v = e.getDeltaMovement();
				Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();
				Vec3 base = dir.scale(-0.25).add(0, e.getBbHeight() * 0.5, 0);
				RecalFx.spike(pose, buffers, base, dir, 0.08f, 0.5f, 0xFFE8F8FF, 0xFF6AC8F0, (e.tickCount + partial) * 0.4f);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, e.getBbHeight() * 0.5, 0), 0.25f, PyroDraw.dim(ICE_GLOW, 0.5f));
			});
			// v0.17.4 Ice Decoy: his body in translucent ice
			net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(ArctiguanaKit.DECOY,
					com.projecthero.mod.client.omnitrix.alien.arctiguana.ArctiguanaDecoyRenderer::new);
		}
	}

	// ---------------------------------------------------------------- v0.17.4 poses
	/** Wall Cling (held): both hands up the wall, knees bent and tucked, head tipped back to look up it. */
	private static final float[] CLING_HELD = g(0, -2.7f, 0.2f, 0.25f, -2.5f, -0.2f, -0.25f, -0.1f, 0, -0.9f, -0.4f, -0.3f, 0.3f);
	/** Ice Path (held): hunched low over the ground ahead, breathing the ice out in front of his feet. */
	private static final float[] PATH_HELD = g(0, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.55f, 0, -0.35f, 0.35f, 0.5f, 0.7f);
	/** Blizzard (held): arms flung out and head back as the storm whirls round him. */
	private static final float[] BLIZZARD_HELD = g(0, -0.6f, 0, 1.3f, -0.6f, 0, -1.3f, -0.2f, 0, -0.1f, 0.1f, -0.45f, 0.2f);
	/** Shard Fan: a snap of the head, spraying shards across the front. */
	private static final float[][] FAN = { rest(0), g(2, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, 0.35f, 0.5f, 0, 0, 0.35f, 0.1f),
			g(5, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.3f, -0.5f, 0, 0, 0.3f, 0.1f), g(8, 0.25f, 0, 0.25f, 0.25f, 0, -0.25f, 0.25f, 0, 0, 0, 0.2f, 0.05f), rest(12) };
	/** Ice Pillar: a fist driven down at the ground, sending the ice up under it. */
	private static final float[][] PILLAR = { rest(0), g(3, -2.7f, 0, 0.2f, 0.3f, 0, -0.3f, -0.2f, 0, 0, 0, -0.2f, 0.2f),
			g(6, 0.5f, 0, 0.3f, 0.3f, 0, -0.3f, 0.5f, 0, -0.4f, 0.4f, 0.3f, 0.8f), g(12, 0.4f, 0, 0.3f, 0.3f, 0, -0.3f, 0.4f, 0, -0.35f, 0.35f, 0.25f, 0.6f), rest(18) };
	/** Shatter: both fists clenched and snapped in, as every block of ice round him cracks. */
	private static final float[][] SHATTER = { rest(0), g(3, -0.5f, 0, 1.3f, -0.5f, 0, -1.3f, -0.2f, 0, 0, 0, -0.2f, 0.1f),
			g(6, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.3f, 0, -0.2f, 0.2f, 0.25f, 0.4f), g(12, -1.2f, -0.8f, 0, -1.2f, 0.8f, 0, 0.25f, 0, -0.2f, 0.2f, 0.2f, 0.3f), rest(18) };
	/** Ice Decoy: a quick step aside from where the statue forms, arms in. */
	private static final float[][] DECOY = { rest(0), g(3, -0.6f, 0.4f, 0.3f, 0.6f, 0, -0.5f, 0.2f, 0.6f, -0.4f, 0.3f, 0, 0.4f), rest(10) };
	/** Frozen Tail Spike: twisting away and driving the iced tail out ahead past his side. */
	private static final float[][] SPIKE = { rest(0), g(2, 0.5f, 0, 0.6f, 0.3f, 0, -0.4f, 0.4f, -0.9f, -0.3f, 0.3f, 0.2f, 0.4f),
			g(5, 0.2f, 0, 0.3f, -0.4f, 0, -0.3f, 0.3f, 0.6f, -0.3f, 0.3f, 0.1f, 0.3f), g(10, 0.2f, 0, 0.3f, -0.3f, 0, -0.3f, 0.25f, 0.4f, -0.25f, 0.25f, 0.1f, 0.2f),
			rest(15) };

	private static final FirstPersonClip FP_CLING = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -70, 10, 0, 0.05f, 0.55f, 0.0f), fp(4000, -70, 10, 0, 0.05f, 0.55f, 0.0f))
			.both();
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, 34, -10, 0, -0.06f, -0.34f, 0.12f), fp(4000, 34, -10, 0, -0.06f, -0.34f, 0.12f))
			.both();

	/** The local player's jump key last tick (Wall Cling's leap off). */
	private static boolean jumpWas;

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x, crouch)
	private static float[] g(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx, float hx, float crouch) {
		return QuirkKeyframes.fc(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, hx, crouch);
	}

	private static float[] rest(float t) {
		return QuirkKeyframes.restC(t);
	}

	private static float[] at(float t, float[] frame) {
		float[] o = frame.clone();
		o[0] = t;
		return o;
	}

	/** Ice Breath: head thrown forward, chest out, arms swept back and claws spread; held for the 1.5 s of it. */
	private static final float[] BREATH_HELD = g(0, 0.55f, 0, 0.55f, 0.55f, 0, -0.55f, 0.28f, 0, -0.25f, 0.3f, 0.2f, 0.2f);
	private static final float[][] BREATH = { rest(0), g(3, 0.3f, 0, 0.4f, 0.3f, 0, -0.4f, -0.2f, 0, 0, 0, -0.4f, 0), at(6, BREATH_HELD), at(30, BREATH_HELD), rest(36) };
	/** The spit: a snap of the head and shoulders. */
	private static final float[][] SPIT = { rest(0), g(2, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, 0.35f, 0, 0, 0, 0.35f, 0.1f), g(5, 0.25f, 0, 0.25f, 0.25f, 0, -0.25f, 0.2f, 0, 0, 0, 0.15f, 0.05f),
			rest(10) };
	/** Frost Wall: both claws swept up from the ground, raising it. */
	private static final float[][] WALL = { rest(0), g(3, 0.4f, 0, 0.2f, 0.4f, 0, -0.2f, 0.35f, 0, -0.3f, 0.3f, 0.2f, 0.6f), g(8, -2.4f, 0, 0.25f, -2.4f, 0, -0.25f, -0.15f, 0, 0, 0, -0.2f, 0),
			g(14, -2.2f, 0, 0.3f, -2.2f, 0, -0.3f, -0.1f, 0, 0, 0, -0.1f, 0), rest(20) };
	/** Deep Freeze: a crouch as the cold gathers, then flung open, everything leaving him at once. */
	private static final float[][] FREEZE = { rest(0), g(3, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, 0.4f, 0, -0.4f, 0.4f, 0.3f, 0.8f),
			g(6, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.35f, 0, 0, 0, -0.45f, 0), g(16, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.3f, 0, 0, 0, -0.4f, 0), rest(24) };
	/** Ice Slide: crouched low on the glide, one foot forward, claws out for balance (held while the slide lasts). */
	private static final float[] SLIDE_HELD = g(0, -0.3f, 0, 1.1f, 0.2f, 0, -1.2f, 0.45f, 0.5f, -0.6f, 0.5f, 0.15f, 1f);
	private static final float[][] SLIDE = { rest(0), at(3, SLIDE_HELD), at(400, SLIDE_HELD) };
	private static final float[][] SLIDE_END = { at(0, SLIDE_HELD), rest(7) };
	/** Tail Whip: the whole body spins (bodySpin), arms tucked, crouched to bring the tail round low. */
	private static final float[][] WHIP = { rest(0), g(2, 0.3f, 0, 0.7f, 0.3f, 0, -0.7f, 0.3f, 0, -0.2f, 0.2f, 0.1f, 0.5f), g(8, 0.3f, 0, 0.8f, 0.3f, 0, -0.8f, 0.3f, 0, -0.2f, 0.2f, 0.1f, 0.5f),
			rest(13) };
	/** Frost Armour: arms crossed over his chest as the plates form, then thrown down. */
	private static final float[][] ARMOUR = { rest(0), g(3, -1.3f, -0.9f, 0, -1.3f, 0.9f, 0, 0.2f, 0, 0, 0, 0.25f, 0.2f), g(8, -1.3f, -0.9f, 0, -1.3f, 0.9f, 0, 0.2f, 0, 0, 0, 0.25f, 0.2f),
			g(11, 0.3f, 0, 0.6f, 0.3f, 0, -0.6f, -0.2f, 0, 0, 0, -0.2f, 0), rest(16) };
	/** Frost Claws: a quick raking swipe. */
	private static final float[][] CLAW = { rest(0), g(2, -1.6f, -0.5f, 0.3f, 0.2f, 0, -0.2f, 0.15f, -0.4f, -0.2f, 0.2f, 0, 0), rest(7) };

	private static float[] fp(float t, float pitch, float yaw, float roll, float x, float y, float z) {
		return FirstPersonClip.f(t, pitch, yaw, roll, x, y, z);
	}

	private static final FirstPersonClip FP_BREATH = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, 34, -10, 0, -0.06f, -0.34f, 0.12f), fp(30, 34, -10, 0, -0.06f, -0.34f, 0.12f),
			fp(36, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 22, 0, 0, 0, -0.2f, 0.08f), fp(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WALL = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, 30, 10, 0, 0.05f, -0.3f, 0.1f), fp(8, -44, 10, 0, 0.06f, 0.3f, -0.1f),
			fp(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FREEZE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, 30, 0, 0, 0, -0.3f, 0.1f), fp(6, -20, -44, -30, -0.26f, 0.16f, -0.1f),
			fp(16, -20, -40, -28, -0.24f, 0.14f, -0.1f), fp(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLIDE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -10, -36, -20, -0.3f, 0.06f, 0f), fp(400, -10, -36, -20, -0.3f, 0.06f, 0f)).both();
	private static final FirstPersonClip FP_SLIDE_END = FirstPersonClip.of(fp(0, -10, -36, -20, -0.3f, 0.06f, 0f), fp(7, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WHIP = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, 30, 30, 0, 0.2f, -0.3f, 0.1f), fp(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ARMOUR = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -30, 40, 0, -0.1f, 0.1f, -0.1f), fp(8, -30, 40, 0, -0.1f, 0.1f, -0.1f),
			fp(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CLAW = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -20, 30, 20, 0.2f, 0.1f, -0.3f), fp(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	@Override
	public int badgeBone() {
		return BADGE_LEFT_ARM;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		Minecraft client = Minecraft.getInstance();
		if (p == client.player) {
			// v0.17.4 Wall Cling: Jump leaps off the wall, away from it and up (the grip lets go at once on this side; the server sees him leave)
			boolean jump = client.options.keyJump.isDown();
			if (jump && !jumpWas && (s.kitFlags & ArctiguanaKit.F_CLING) != 0) {
				Vec3 out = ArctiguanaKit.wallNormal(p);
				if (out != null) {
					p.setDeltaMovement(out.scale(0.75).add(0, 0.62, 0));
					ArctiguanaKit.clientLeapAt = p.level().getGameTime();
				}
			}
			jumpWas = jump;
		}
		boolean sliding = (s.kitFlags & ArctiguanaKit.F_SLIDE) != 0;
		TAIL.tick(p, sliding ? 0.8f : 0f, 0f);
		// cold breath: a little frosty puff now and then, and a steady mist while the breath runs
		Minecraft mc = Minecraft.getInstance();
		boolean fp = p == mc.player && mc.options.getCameraType().isFirstPerson();
		if (!fp && (p.tickCount % 28 == 0 || p.tickCount % 28 == 3)) {
			Vec3 m = ArctiguanaKit.mouth(p);
			Vec3 f = p.getLookAngle().scale(0.04);
			p.level().addParticle(ParticleTypes.WHITE_ASH, m.x, m.y, m.z, f.x, 0.005, f.z);
			p.level().addParticle(ParticleTypes.SNOWFLAKE, m.x, m.y, m.z, f.x * 0.6, -0.002, f.z * 0.6);
		}
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		TAIL.render(pose, buffers, light, p, model, partial);
	}

	@Override
	public float bodySpin(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.animId != ArctiguanaKit.ANIM_WHIP) {
			return 0f;
		}
		float t = p.level().getGameTime() + partial - s.animStart;
		if (t < 0f || t > 10f) {
			return 0f;
		}
		float k = Mth.clamp((t - 1f) / 8f, 0f, 1f);
		return 360f * (1f - (1f - k) * (1f - k)); // one quick turn, slowing at the end
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		boolean held = false;
		if ((flags & ArctiguanaKit.F_SLIDE) != 0) {
			QuirkKeyframes.apply(m, SLIDE_HELD, 1f);
			held = true;
		}
		if ((flags & ArctiguanaKit.F_CLING) != 0) {
			QuirkKeyframes.apply(m, CLING_HELD, Mth.clamp(ticks / 3f, 0f, 1f));
			m.rightArm.xRot += Mth.sin(age * 0.4f) * 0.08f * p.walkAnimation.speed() * 4f; // hand over hand as he climbs
			m.leftArm.xRot -= Mth.sin(age * 0.4f) * 0.08f * p.walkAnimation.speed() * 4f;
			return true;
		}
		if ((flags & ArctiguanaKit.F_BREATH) != 0) {
			QuirkKeyframes.apply(m, BREATH_HELD, Mth.clamp(ticks / 4f, 0f, 1f));
			return true;
		}
		if ((flags & ArctiguanaKit.F_PATH) != 0) {
			QuirkKeyframes.apply(m, PATH_HELD, Mth.clamp(ticks / 4f, 0f, 1f));
			return true;
		}
		if ((flags & ArctiguanaKit.F_BLIZZARD) != 0) {
			QuirkKeyframes.apply(m, BLIZZARD_HELD, Mth.clamp(ticks / 4f, 0f, 1f));
			m.body.yRot += Mth.sin(age * 0.5f) * 0.15f;
			return true;
		}
		float[][] clip = switch (animId) {
			case ArctiguanaKit.ANIM_BREATH -> BREATH;
			case ArctiguanaKit.ANIM_SPIT -> SPIT;
			case ArctiguanaKit.ANIM_WALL -> WALL;
			case ArctiguanaKit.ANIM_FREEZE -> FREEZE;
			case ArctiguanaKit.ANIM_SLIDE -> (flags & ArctiguanaKit.F_SLIDE) != 0 ? SLIDE : null;
			case ArctiguanaKit.ANIM_SLIDE_END -> SLIDE_END;
			case ArctiguanaKit.ANIM_WHIP -> WHIP;
			case ArctiguanaKit.ANIM_ARMOUR -> ARMOUR;
			case ArctiguanaKit.ANIM_CLAW -> CLAW;
			case ArctiguanaKit.ANIM_FAN -> FAN;
			case ArctiguanaKit.ANIM_PILLAR -> PILLAR;
			case ArctiguanaKit.ANIM_SHATTER -> SHATTER;
			case ArctiguanaKit.ANIM_DECOY -> DECOY;
			case ArctiguanaKit.ANIM_SPIKE -> SPIKE;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f) || held;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case ArctiguanaKit.ANIM_BREATH -> FP_BREATH;
			case ArctiguanaKit.ANIM_SPIT -> FP_SPIT;
			case ArctiguanaKit.ANIM_WALL -> FP_WALL;
			case ArctiguanaKit.ANIM_FREEZE -> FP_FREEZE;
			case ArctiguanaKit.ANIM_SLIDE -> FP_SLIDE;
			case ArctiguanaKit.ANIM_SLIDE_END -> FP_SLIDE_END;
			case ArctiguanaKit.ANIM_WHIP -> FP_WHIP;
			case ArctiguanaKit.ANIM_ARMOUR -> FP_ARMOUR;
			case ArctiguanaKit.ANIM_CLAW -> FP_CLAW;
			case ArctiguanaKit.ANIM_FAN -> FP_SPIT;
			case ArctiguanaKit.ANIM_PILLAR, ArctiguanaKit.ANIM_SHATTER -> FP_FREEZE;
			case ArctiguanaKit.ANIM_CLING -> FP_CLING;
			case ArctiguanaKit.ANIM_PATH, ArctiguanaKit.ANIM_BLIZZARD -> FP_DOWN;
			case ArctiguanaKit.ANIM_SPIKE, ArctiguanaKit.ANIM_DECOY -> FP_WHIP;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the world pass: the Frost Armour shell, the frozen blocks

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		boolean any = false, bigChill = false;
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl) || !Omnitrix.state(pl).transformed()) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			bigChill |= BigChillKit.ID.equals(st.active);
			if (!ArctiguanaKit.ID.equals(st.active)) {
				continue;
			}
			any = true;
			boolean fp = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			if ((st.kitFlags & ArctiguanaKit.F_ARMOUR) != 0 && !fp) {
				Vec3 feet = pl.getPosition(partial);
				int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(pl.getEyePosition(partial)));
				float shimmer = 0.1f + 0.02f * Mth.sin((pl.tickCount + partial) * 0.3f);
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), shimmer, 0x60C8F0FF, light);
			}
		}
		if (!any || bigChill) {
			return; // (a Big Chill in view draws the frozen shells himself)
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof LivingEntity le && BigChillKit.shelled(le) && le.distanceToSqr(cam) < 64 * 64 && !(le == mc.player && mc.options.getCameraType().isFirstPerson())) {
				Vec3 at = le.getPosition(partial);
				int light = LevelRenderer.getLightColor(le.level(), le.blockPosition());
				RecalFx.shell(pose, buffers, cam, le.getBoundingBox().move(at.subtract(le.position())), 0.15f, 0x90C8F0FF, light);
			}
		}
	}
}
