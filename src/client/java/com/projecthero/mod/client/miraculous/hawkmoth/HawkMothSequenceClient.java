package com.projecthero.mod.client.miraculous.hawkmoth;

import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.BROOCH;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.CANE;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.MASK;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.MASK_END;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.POSE;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.RISE;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.RISE_END;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.SWARM;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.SWARM_END;
import static com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.TOTAL;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of Hawk Moth's long transformation ({@link HawkMothSequence}) on the shared frame
 * ({@link MiraculousSequenceClient}): Nooroo drawn into the Brooch, the purple wave of light RISING from his feet with
 * the suit forming under it ({@code HawkMothSuitRender}'s rising order, {@link #rising}), a swirl of purple butterflies
 * circling and dissolving into the suit, the silver mask, the Cane materialising in his hand ({@link #caneShown}), and
 * the arms-wide pose under a burst of white and purple butterflies.
 */
public final class HawkMothSequenceClient {
	private static final Vector3f PURPLE = new Vector3f(0.6f, 0.36f, 0.84f);
	private static final Vector3f LILAC = new Vector3f(0.82f, 0.68f, 1.0f);
	private static final Vector3f SILVER = new Vector3f(0.86f, 0.88f, 0.94f);
	private static final int GLOW = 0x9A5CD6;
	private static final int CORE = 0xEBDDFF;
	private static final int BUTTERFLY_PURPLE = 0xFF9A5CD6;
	private static final int BUTTERFLY_LILAC = 0xFFC9A8F0;
	private static final float GOLDEN = 2.39996f;

	/** Pose keys: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
	private static final float[] STAND = { 0f, 0f, 0.1f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** A hand to the Brooch, head bowed to it. */
	private static final float[] BROOCH_P = { 0.35f, -1.25f, -0.65f, 0f, -0.1f, 0f, 0.05f, 0f, -0.05f, 0f };
	/** Palms open, low, as the wave starts at his feet ... */
	private static final float[] RISE_LOW = { -0.25f, -0.3f, 0.55f, -0.3f, -0.55f, 0f, 0.06f, 0f, -0.06f, 0f };
	/** ... the arms lifting with it. */
	private static final float[] RISE_HIGH = { -0.45f, -0.95f, 0.95f, -0.95f, -0.95f, 0f, 0.06f, 0f, -0.06f, 0f };
	/** Head bowed as the mask forms. */
	private static final float[] MASK_P = { 0.42f, -0.15f, 0.2f, -0.15f, -0.2f, 0f, 0.04f, 0f, -0.04f, 0.05f };
	/** The Cane in his right hand, the left behind his back. */
	private static final float[] CANE_P = { 0f, -0.62f, 0.05f, 0.45f, -0.12f, 0f, 0.06f, 0f, -0.06f, 0f };
	/** Arms flung wide. */
	private static final float[] WIDE = { -0.35f, -0.5f, 2.05f, -0.5f, -2.05f, 0f, 0.14f, 0f, -0.14f, -0.08f };

	private static final int[] AT = { 0, 8, RISE, RISE_END, MASK, CANE, POSE };
	private static final int[] BL = { 8, 6, 30, 8, 6, 6, 1 };
	private static final float[][] KEYS = { STAND, BROOCH_P, RISE_LOW, RISE_HIGH, MASK_P, CANE_P, WIDE };

	public static final MiraculousSequenceClient.Scene SCENE = new MiraculousSequenceClient.Scene() {
		@Override
		public float[] pose(Player p, MiraculousState s, float t) {
			return MiraculousSequenceClient.keyed(t, AT, BL, KEYS);
		}

		@Override
		public void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw) {
			HawkMothSequenceClient.fx(level, p, t, yaw);
		}

		@Override
		public float[][] cam() {
			return CAM;
		}

		@Override
		public void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
			HawkMothSequenceClient.world(ctx, p, s, t);
		}
	};

	private HawkMothSequenceClient() {
	}

	/** The long transformation is playing on him: the suit rises from the boots ({@code HawkMothSuitRender}'s rising order). */
	public static boolean rising(Player player) {
		return MiraculousSequenceClient.age(player, Kwami.NOOROO, 0f) >= 0f;
	}

	/** The Cane during the sequence: not until its beat (null = not playing: his own rule). */
	public static Boolean caneShown(Player player, float partial) {
		float t = MiraculousSequenceClient.age(player, Kwami.NOOROO, partial);
		return t < 0f ? null : t >= CANE;
	}

	// ---------------------------------------------------------------- the light show

	private static void fx(ClientLevel level, Player p, int t, float yaw) {
		RandomSource rnd = level.random;
		double h = p.getBbHeight();
		Vec3 feet = p.position();
		Vec3 fwd = MiraculousSequenceClient.forward(yaw);
		Vec3 right = MiraculousSequenceClient.right(fwd);
		Vec3 brooch = feet.add(0, h * 0.74, 0).add(fwd.scale(0.2));
		DustParticleOptions purple = new DustParticleOptions(PURPLE, 1.1f);
		DustParticleOptions lilac = new DustParticleOptions(LILAC, 0.8f);
		DustParticleOptions silver = new DustParticleOptions(SILVER, 0.7f);
		if (t < BROOCH) {
			// Nooroo drawn in: a slow purple streak circling down into the Brooch
			float k = t / (float) BROOCH;
			Vec3 from = brooch.add(fwd.scale(1.0 * (1 - k))).add(right.scale(0.7 * Math.cos(k * 5) * (1 - k))).add(0, 0.5 * (1 - k), 0);
			level.addParticle(purple, from.x, from.y, from.z, 0, 0, 0);
			level.addParticle(lilac, from.x, from.y, from.z, 0, 0, 0);
		} else if (t < RISE) {
			if (t == BROOCH) {
				level.addParticle(ParticleTypes.FLASH, brooch.x, brooch.y, brooch.z, 0, 0, 0);
			}
			level.addParticle(t % 2 == 0 ? lilac : purple, brooch.x, brooch.y, brooch.z, (rnd.nextDouble() - 0.5) * 0.1, (rnd.nextDouble() - 0.5) * 0.1,
					(rnd.nextDouble() - 0.5) * 0.1);
		} else if (t < RISE_END + 2) {
			// the wave front's glitter: purple motes and portal sparks climbing with it
			double y = HawkMothSequence.wave(t) * h;
			for (int i = 0; i < 3; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				level.addParticle(i == 0 ? lilac : purple, feet.x + Math.cos(a) * 0.5, feet.y + y, feet.z + Math.sin(a) * 0.5, 0, 0.02, 0);
			}
			if (t % 2 == 0) {
				double a = rnd.nextDouble() * Math.PI * 2;
				level.addParticle(ParticleTypes.PORTAL, feet.x + Math.cos(a) * 0.6, feet.y + y, feet.z + Math.sin(a) * 0.6, 0, 0.1, 0);
			}
		} else if (t >= MASK && t < MASK_END) {
			// the silver mask: a glint climbing from the jaw over the face
			float k = (t - MASK) / (float) (MASK_END - MASK);
			Vec3 face = feet.add(0, h * (0.76 + 0.24 * k), 0).add(fwd.scale(0.3));
			if (t % 2 == 0) {
				level.addParticle(silver, face.x + right.x * (rnd.nextDouble() - 0.5) * 0.4, face.y, face.z + right.z * (rnd.nextDouble() - 0.5) * 0.4,
						0, 0.01, 0);
			}
			if (t % 5 == 0) {
				level.addParticle(ParticleTypes.END_ROD, face.x, face.y + 0.05, face.z, 0, 0.02, 0);
			}
		} else if (t >= CANE && t < CANE + 6) {
			// the Cane gathers out of purple light, the length of it from his hand to the ground
			Vec3 hand = feet.add(0, h * 0.5, 0).add(fwd.scale(0.55)).add(right.scale(0.35));
			for (int i = 0; i < 5; i++) {
				double y = rnd.nextDouble() * (hand.y - feet.y);
				level.addParticle(i % 2 == 0 ? purple : silver, hand.x, feet.y + y, hand.z, 0, 0.01, 0);
			}
			if (t == CANE) {
				level.addParticle(ParticleTypes.FLASH, hand.x, hand.y, hand.z, 0, 0, 0);
			}
		} else if (t == POSE) {
			Vec3 c = feet.add(0, h * 0.7, 0);
			level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
			for (int i = 0; i < 18; i++) {
				level.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, (rnd.nextDouble() - 0.5) * 0.35, (rnd.nextDouble() - 0.2) * 0.3,
						(rnd.nextDouble() - 0.5) * 0.35);
			}
		}
	}

	private static void world(WorldRenderContext ctx, Player p, MiraculousState s, float t) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 feet = MiraculousSequenceClient.origin(s, t);
		double h = p.getBbHeight();
		Vec3 cam = ctx.camera().getPosition();
		if (t >= RISE - 2 && t < RISE_END + 8) {
			// the rising wave: a ring of purple light and a band glowing up off it, climbing him from the boots
			float fade = MiraculousSequenceClient.smooth((t - (RISE - 2)) / 6f) * (1f - MiraculousSequenceClient.smooth((t - RISE_END) / 8f));
			double y = HawkMothSequence.wave(t) * h;
			Vec3 c = feet.add(0, y, 0);
			float r = 0.5f + 0.05f * Mth.sin(t * 0.5f);
			MiraculousSequenceClient.ring(ctx, c, r - 0.18f, r + 0.22f, GLOW, 0.9f * fade);
			MiraculousSequenceClient.ring(ctx, c, r - 0.05f, r + 0.05f, CORE, 0.8f * fade);
			MiraculousSequenceClient.band(ctx, c.subtract(0, 0.45, 0), r - 0.02f, 0.55f, GLOW, 0.45f * fade);
			if (t < RISE + 14) {
				// and out across the ground where it began
				float k = (t - (RISE - 2)) / 16f;
				MiraculousSequenceClient.ring(ctx, feet.add(0, 0.03, 0), 0.4f + 1.5f * k, 0.6f + 1.7f * k, GLOW, 0.6f * (1f - k));
			}
		}
		VertexConsumer vc = null;
		if (t >= SWARM && t < SWARM_END) {
			// the swirl of purple butterflies, circling in and dissolving into the suit
			vc = ctx.consumers().getBuffer(RenderType.entityCutoutNoCull(ButterflyModel.WHITE));
			int n = 28;
			for (int i = 0; i < n; i++) {
				float born = SWARM + i * 1.4f;
				float k = Mth.clamp((t - born) / 26f, 0f, 1f);
				if (t < born || k >= 1f) {
					continue;
				}
				float a = i * GOLDEN + t * (0.14f + 0.02f * (i % 3));
				float r = Mth.lerp(k * k, 1.5f, 0.3f);
				double y = h * (0.15 + 0.8 * ((i * 7) % 11) / 11.0) + 0.1 * Mth.sin(t * 0.3f + i);
				Vec3 at = feet.add(Mth.cos(a) * r, y, Mth.sin(a) * r);
				float scale = 0.6f * (1f - k * k * k);
				float heading = -(a * Mth.RAD_TO_DEG) - 180f;
				ButterflyFx.one(ctx.matrixStack(), vc, mc, cam, at, heading, 10f, t * 1.7f + i, scale, i % 3 == 0 ? BUTTERFLY_LILAC : BUTTERFLY_PURPLE);
			}
		}
		if (t >= POSE && t < TOTAL + MiraculousSequenceClient.EASE_OUT) {
			// the burst: white and purple butterflies flying out from him
			if (vc == null) {
				vc = ctx.consumers().getBuffer(RenderType.entityCutoutNoCull(ButterflyModel.WHITE));
			}
			float age = t - POSE;
			Vec3 c = feet.add(0, h * 0.7, 0);
			for (int i = 0; i < 40; i++) {
				float a = i * GOLDEN;
				double up = ((i * 13) % 17) / 17.0 - 0.3;
				Vec3 dir = new Vec3(Mth.cos(a), up, Mth.sin(a)).normalize();
				float d = 0.3f + age * (0.07f + 0.02f * (i % 4));
				Vec3 at = c.add(dir.scale(d)).add(0, age * 0.02, 0);
				float scale = 0.55f * Mth.clamp(1f - (age - 18f) / 12f, 0f, 1f);
				if (scale <= 0.01f) {
					continue;
				}
				float heading = (float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
				ButterflyFx.one(ctx.matrixStack(), vc, mc, cam, at, heading, 20f, t * 2.1f + i, scale, i % 2 == 0 ? 0xFFFFFFFF : BUTTERFLY_PURPLE);
			}
		}
	}

	// ---------------------------------------------------------------- the camera

	private static final float[][] CAM = {
			{ 0, 2.2f, 0, 1.45f, 1.4f },
			{ 16, 1.3f, 15, 1.45f, 1.38f },
			{ 26, 1.7f, 20, 1.4f, 1.35f },
			{ 36, 3.1f, -30, 0.3f, 0.45f },
			{ 64, 3.4f, -70, 0.9f, 0.95f },
			{ 92, 3.3f, -50, 1.6f, 1.35f },
			{ 104, 1.45f, 0, 1.72f, 1.68f },
			{ 122, 1.5f, 10, 1.72f, 1.66f },
			{ 130, 2.4f, -35, 1.0f, 0.9f },
			{ 148, 2.8f, -20, 0.9f, 1.0f },
			{ 156, 4.4f, 0, 0.4f, 1.4f },
			{ TOTAL, 4.8f, 5, 0.4f, 1.45f },
	};
}
