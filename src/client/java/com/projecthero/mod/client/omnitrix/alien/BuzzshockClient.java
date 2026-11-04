package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.buzzshock.BuzzshockCopyRenderer;
import com.projecthero.mod.client.omnitrix.alien.buzzshock.BuzzshockFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Buzzshock's client kit (user: "Also build buzzshock please heres the skin:
 * https://www.planetminecraft.com/skin/buzzshock-ben-10-5321154/" ... "remove the pixel omnitrix from his skin model and replace it
 * with our omnitrix badge please"). The user's skin on the player body at his tiny size (x0.4), its painted chest Omnitrix painted out
 * ({@code scratchpad/ben10_v0171/gen_buzzshock.js}) and the mod's badge on his chest in its place -- the classic series wears it there.
 * Over the skin a flickering full-bright pass ({@code buzzshock_glow.png}): the green vent on his head, his eyes and the lights on his
 * limbs, and his white stripe faintly -- he is living electricity. His bolts, the Spark Bomb and the crackle round him are
 * {@link BuzzshockFx}; his copies {@link BuzzshockCopyRenderer} (the same body, glow and badge).
 */
public final class BuzzshockClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/buzzshock.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/buzzshock_glow.png");
	/** His body scale (the kit's stat), for a copy whose owner is out of sight. */
	public static final float SIZE = 0.4f;
	/** The chest badge (body-bone pixels): where the skin painted it (jacket texels 23..24 x 37..38), just proud of the jacket layer. */
	public static final float[] BADGE = { 0f, 2.0f, -2.3f, 2.6f };

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.BUZZSHOCK.id(), new BuzzshockClient());
		if (!registered) {
			registered = true;
			EntityRendererRegistry.register(BuzzshockKit.COPY, BuzzshockCopyRenderer::new);
			BuzzshockFx.register();
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body x, body y, rLeg x, lLeg x)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, 0);
	}

	/** Lightning Bolt: the right arm thrown out at it. */
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), f(2, -1.7f, 0.1f, 0, 0.3f, 0, -0.3f, 0.1f, -0.2f, 0, 0), f(6, -1.6f, 0.1f, 0, 0.3f, 0, -0.3f, 0.1f, -0.2f, 0, 0),
			QuirkKeyframes.rest(10) };
	/** Chain Lightning: both arms out, then swept across. */
	private static final float[][] CHAIN = { QuirkKeyframes.rest(0), f(3, -1.6f, 0.4f, 0, -1.6f, -0.4f, 0, 0.1f, 0, 0, 0), f(8, -1.6f, -0.3f, 0, -1.6f, -0.9f, 0, 0.1f, -0.4f, 0, 0),
			QuirkKeyframes.rest(14) };
	/** Static Touch: both palms shoved forward. */
	private static final float[][] TOUCH = { QuirkKeyframes.rest(0), f(2, -1.5f, -0.25f, 0, -1.5f, 0.25f, 0, 0.3f, 0, -0.3f, 0.3f), f(6, -1.5f, -0.25f, 0, -1.5f, 0.25f, 0, 0.3f, 0, -0.3f, 0.3f),
			QuirkKeyframes.rest(10) };
	/** Spark Bomb: an overarm lob. */
	private static final float[][] BOMB = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.2f, 0.4f, 0, -0.2f, -0.2f, 0, 0, 0), f(6, -0.6f, -0.2f, 0, 0.2f, 0, -0.1f, 0.3f, 0, 0, 0),
			QuirkKeyframes.rest(11) };
	/** Spark Zip / take-off: arms flung back, body pitched forward. */
	private static final float[][] ZIP = { QuirkKeyframes.rest(0), f(2, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.5f, 0, 0.6f, 0.6f), f(8, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.5f, 0, 0.6f, 0.6f),
			QuirkKeyframes.rest(12) };
	/** Overload: arms and legs flung wide, crackling. */
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), f(2, -0.3f, 0, 0.6f, -0.3f, 0, -0.6f, 0.3f, 0, 0, 0),
			f(5, 0, 0, 1.5f, 0, 0, -1.5f, -0.3f, 0, 0.4f, -0.4f), f(12, 0, 0, 1.5f, 0, 0, -1.5f, -0.3f, 0, 0.4f, -0.4f), QuirkKeyframes.rest(16) };
	/** Power Line Ride: diving in, arms ahead. */
	private static final float[][] LINE = { QuirkKeyframes.rest(0), f(2, -2.9f, 0, 0.1f, -2.9f, 0, -0.1f, 0.4f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] SPLIT = { QuirkKeyframes.rest(0), f(3, 0, 0, 1.2f, 0, 0, -1.2f, -0.25f, 0, 0.3f, -0.3f), f(7, 0, 0, 1.0f, 0, 0, -1.0f, -0.2f, 0, 0.25f, -0.25f),
			QuirkKeyframes.rest(11) };
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), f(3, -0.8f, 0, -0.5f, -0.8f, 0, 0.5f, 0.35f, 0, 0, 0), f(9, -0.8f, 0, -0.5f, -0.8f, 0, 0.5f, 0.35f, 0, 0, 0),
			QuirkKeyframes.rest(13) };
	/** Megawatt Swarm: both arms thrown up. */
	private static final float[][] SWARM = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.25f, 0, 0, 0), f(14, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.25f, 0, 0, 0),
			QuirkKeyframes.rest(20) };
	/** Megavolt: points the swarm at it, then a clenched-fist flex as it goes off. */
	private static final float[][] MEGAVOLT = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.1f, 0, 0.2f, 0, -0.3f, 0.1f, -0.2f, 0, 0), f(8, -1.6f, -0.1f, 0, 0.2f, 0, -0.3f, 0.1f, -0.2f, 0, 0),
			f(11, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, 0.2f, 0, 0, 0), QuirkKeyframes.rest(18) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.1f, 0, 0, 0), QuirkKeyframes.rest(6) };

	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 5, 0, 0.05f, 0.1f, -0.25f),
			FirstPersonClip.f(8, -20, 5, 0, 0.05f, 0.1f, -0.25f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -15, 0, 0, 0.05f, 0.1f, -0.3f),
			FirstPersonClip.f(8, -15, 0, 0, 0.05f, 0.1f, -0.3f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 0, 0, 0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(12, -70, 0, 0, 0.05f, 0.5f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// v0.17.2 (user: "remove the pixel omnitrix from his skin model and replace it with our omnitrix badge please"): where the skin
		// painted it, on his chest
		return BADGE;
	}

	/** The glow's strength {@code age} ticks in: a steady hum with quick flickers, never fully dark. */
	public static float flicker(float age, int seed) {
		float k = 0.78f + 0.12f * Mth.sin(age * 0.7f + seed) + 0.1f * Mth.sin(age * 2.3f + seed * 3);
		long tick = (long) age;
		if (((tick * 2654435761L + seed * 40503L) >>> 7) % 23 == 0) {
			k = 1.2f; // a crackle
		}
		return Mth.clamp(k, 0.45f, 1.2f);
	}

	/** The full-bright electricity pass over an already posed model. */
	public static void glow(PoseStack pose, MultiBufferSource buffers, HumanoidModel<?> model, float age, int seed, boolean wet) {
		float k = flicker(age, seed) * (wet ? 0.35f : 1f);
		int c = Mth.clamp((int) (255 * Math.min(1f, k)), 0, 255);
		int argb = 0xFF000000 | (c << 16) | (c << 8) | c;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		boolean wet = (Omnitrix.state(p).kitFlags & BuzzshockKit.F_WET) != 0;
		glow(pose, buffers, model, age, p.getId(), wet);
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		// a little crackle round him now and then (more while flying)
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return; // never a spark in your own eyes
		}
		var s = Omnitrix.state(p);
		boolean fly = (s.kitFlags & BuzzshockKit.F_FLY) != 0;
		if (p.getRandom().nextInt(fly ? 2 : 7) == 0 && (s.kitFlags & BuzzshockKit.F_WET) == 0) {
			float sc = p.getScale();
			p.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.5 * sc,
					p.getY() + p.getRandom().nextDouble() * p.getBbHeight(), p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.5 * sc, 0, 0.02, 0);
		}
	}

	/** The pose for {@code animId}, {@code ticks} in -- shared with the copies, which split and swarm with him. */
	public static boolean posed(HumanoidModel<?> m, int animId, float ticks) {
		float[][] clip = switch (animId) {
			case BuzzshockKit.ANIM_BOLT -> BOLT;
			case BuzzshockKit.ANIM_CHAIN -> CHAIN;
			case BuzzshockKit.ANIM_TOUCH -> TOUCH;
			case BuzzshockKit.ANIM_BOMB -> BOMB;
			case BuzzshockKit.ANIM_ZIP -> ZIP;
			case BuzzshockKit.ANIM_OVERLOAD -> OVERLOAD;
			case BuzzshockKit.ANIM_LINE -> LINE;
			case BuzzshockKit.ANIM_SPLIT -> SPLIT;
			case BuzzshockKit.ANIM_MERGE -> MERGE;
			case BuzzshockKit.ANIM_SWARM -> SWARM;
			case BuzzshockKit.ANIM_MEGAVOLT -> MEGAVOLT;
			case BuzzshockKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		if (posed(m, animId, ticks)) {
			return true;
		}
		if ((Omnitrix.state(p).kitFlags & BuzzshockKit.F_FLY) != 0 && !p.onGround()) {
			// flying: a little spark streaking along, arms swept back, legs trailing
			m.rightArm.xRot = 0.5f;
			m.leftArm.xRot = 0.5f;
			m.rightArm.zRot = 0.35f;
			m.leftArm.zRot = -0.35f;
			m.rightLeg.xRot = 0.3f + Mth.sin(age * 0.3f) * 0.1f;
			m.leftLeg.xRot = 0.3f - Mth.sin(age * 0.3f) * 0.1f;
			return true;
		}
		return false;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case BuzzshockKit.ANIM_BOLT, BuzzshockKit.ANIM_CHAIN, BuzzshockKit.ANIM_BOMB, BuzzshockKit.ANIM_MEGAVOLT -> FP_THROW;
			case BuzzshockKit.ANIM_TOUCH, BuzzshockKit.ANIM_OVERLOAD -> FP_BOTH;
			case BuzzshockKit.ANIM_SWARM -> FP_UP;
			default -> null;
		};
	}
}
