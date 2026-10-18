package com.projecthero.mod.client.miraculous.catnoir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.catnoir.CataclysmFxPayload;
import com.projecthero.mod.miraculous.catnoir.CatNoir;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: CATACLYSM as it looks in the show -- real geometry, no enchantment-style wrap.
 * <ul>
 *   <li><b>The hand</b> ({@link #renderHand}, third person for every viewer and first person): churning dark matter
 *       clinging to the fist (an animated box over the hand only) and, in and around the open palm, a ball of
 *       crackling darkness -- camera-facing billboards of swirling near-black smoke over a soft dark halo, a sickly
 *       green-black rim glow, flickering black/violet lightning arcs (segmented ribbons, re-forked every 2 ticks) and
 *       green/violet sparks. It forms over the call ({@link CatNoir#ANIM_CATACLYSM_CALL}): sparks pull in, the ball
 *       swells and throws off a ring of smoke. First person is the same, compact around the fist.</li>
 *   <li><b>A block touch</b> ({@link CataclysmFxPayload}): every crumbling block blackens from the side facing the
 *       touch -- veins racing ahead of a blotchy black front, then grey fracture lines -- as decals on its open faces
 *       (8 stages of {@code cataclysm_corrode.png}, timed to the server's rings), then falls apart into charcoal
 *       chunks (voxel debris that tumble, drop and shrink to nothing) and black dust.</li>
 *   <li><b>A creature touch</b>: it turns dark and cracks ({@link CataclysmTouchLayer}), the armour that shattered
 *       bursts into grey-black chunks and dust, and a dark surge bursts from the point of contact.</li>
 * </ul>
 */
public final class CataclysmFx {
	private static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/miraculous/cat_noir/" + name + ".png");
	}

	static final ResourceLocation SMOKE = tex("cataclysm_smoke");
	static final ResourceLocation CORE = tex("cataclysm_core");
	static final ResourceLocation GLOW = tex("cataclysm_glow");
	static final ResourceLocation ARC = tex("cataclysm_arc");
	static final ResourceLocation SPARK = tex("cataclysm_spark");
	static final ResourceLocation HAND = tex("cataclysm_hand");
	static final ResourceLocation HAND_GLOW = tex("cataclysm_hand_glow");
	static final ResourceLocation CORRODE = tex("cataclysm_corrode");
	static final ResourceLocation CORRODE_GLOW = tex("cataclysm_corrode_glow");
	static final ResourceLocation ASH = tex("cataclysm_ash");
	static final ResourceLocation ENTITY_DARK = tex("cataclysm_entity_dark");
	static final ResourceLocation ENTITY_CRACKS = tex("cataclysm_entity_cracks");

	private static final int FB = LightTexture.FULL_BRIGHT;
	/** How long a touched creature stays dark (ticks), and when it starts to fade. */
	static final int DARK_TICKS = 70, DARK_FADE = 45;
	private static final int BURST_TICKS = 16;
	private static final int MAX_DEBRIS = 2400;
	private static final float GRAVITY = 0.045f;

	private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.03f, 0.02f, 0.04f), 0.6f);
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.16f, 0.34f, 0.08f), 0.4f);
	private static final BlockParticleOption ASH_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState());
	private static final BlockParticleOption GREY_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState());

	private static final class Crumble {
		final BlockPos origin;
		final long start;
		final List<BlockPos> blocks;
		final int[] rings;
		final boolean[] fell;
		final int last;

		Crumble(BlockPos origin, long start, List<BlockPos> blocks) {
			this.origin = origin;
			this.start = start;
			this.blocks = blocks;
			this.rings = new int[blocks.size()];
			this.fell = new boolean[blocks.size()];
			int max = 0;
			for (int i = 0; i < rings.length; i++) {
				rings[i] = (int) Math.round(Math.sqrt(blocks.get(i).distSqr(origin)));
				max = Math.max(max, rings[i]);
			}
			this.last = max * CatNoirConfig.CATACLYSM_STEP_TICKS + CatNoirConfig.CATACLYSM_CRACK_TICKS;
		}
	}

	/** A charcoal chunk: where it started, how it flies, how big, its tint (fading to black). */
	private record Debris(double x, double y, double z, double vx, double vy, double vz, float size, long born, int life, float ax,
			float ay, float az, float spin, int tint) {
	}

	/** The dark surge where a touch landed. */
	private record Burst(Vec3 at, long start, float scale, int seed) {
	}

	private static final List<Crumble> CRUMBLES = new ArrayList<>();
	private static final List<Debris> DEBRIS = new ArrayList<>();
	private static final List<Burst> BURSTS = new ArrayList<>();
	private static final Map<Integer, Long> DARK = new HashMap<>();
	private static ClientLevel stateLevel;

	private CataclysmFx() {
	}

	/** Everything here belongs to one level: a new world (or dimension) starts clean. */
	private static void sync(ClientLevel level) {
		if (level != stateLevel) {
			stateLevel = level;
			CRUMBLES.clear();
			DEBRIS.clear();
			BURSTS.clear();
			DARK.clear();
		}
	}

	// ================================================================ the touch (network)

	public static void receive(CataclysmFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		sync(level);
		long now = level.getGameTime();
		if (p.entityId() >= 0) {
			Entity e = level.getEntity(p.entityId());
			DARK.put(p.entityId(), now);
			if (e != null) {
				BURSTS.add(new Burst(e.position().add(0, e.getBbHeight() * 0.55, 0), now, 0.9f + e.getBbWidth() * 0.5f, p.entityId()));
				armourDust(level, e, p.armourMask(), now);
			}
			return;
		}
		// the burst on the face he touched: the block's middle pulled toward the nearest player (the toucher)
		Vec3 c = Vec3.atCenterOf(p.origin());
		Player near = level.getNearestPlayer(c.x, c.y, c.z, 8.0, false);
		Vec3 at = c;
		if (near != null) {
			Vec3 to = near.getEyePosition().subtract(c);
			at = c.add(to.normalize().scale(0.62));
		}
		BURSTS.add(new Burst(at, now, 1.1f, p.origin().hashCode()));
		if (!p.blocks().isEmpty()) {
			CRUMBLES.add(new Crumble(p.origin(), now, new ArrayList<>(p.blocks())));
		}
	}

	/** The shattered armour bursts into grey chunks that blacken as they fall, and grey-black dust. */
	private static void armourDust(ClientLevel level, Entity e, int mask, long now) {
		float[] at = { 0.88f, 0.62f, 0.36f, 0.08f, 0.5f };
		float w = e.getBbWidth(), h = e.getBbHeight();
		for (int i = 0; i < at.length; i++) {
			if ((mask & (1 << i)) == 0) {
				continue;
			}
			for (int k = 0; k < 16; k++) {
				double x = e.getX() + (level.random.nextDouble() - 0.5) * w * 0.9;
				double y = e.getY() + h * at[i] + (level.random.nextDouble() - 0.5) * 0.25;
				double z = e.getZ() + (level.random.nextDouble() - 0.5) * w * 0.9;
				double dx = x - e.getX(), dz = z - e.getZ();
				addDebris(x, y, z, dx * 0.12, 0.04 + level.random.nextDouble() * 0.08, dz * 0.12, 0.05f + level.random.nextFloat() * 0.09f, now,
						16 + level.random.nextInt(14), 0xFFA8A8AE, level);
				if (k % 3 == 0) {
					level.addParticle(GREY_DUST, x, y, z, 0, 0, 0);
					level.addParticle(ASH_DUST, x, y, z, 0, 0, 0);
				}
			}
		}
	}

	private static void addDebris(double x, double y, double z, double vx, double vy, double vz, float size, long born, int life, int tint,
			ClientLevel level) {
		if (DEBRIS.size() >= MAX_DEBRIS) {
			return;
		}
		Vector3f axis = new Vector3f(level.random.nextFloat() - 0.5f, level.random.nextFloat() - 0.5f, level.random.nextFloat() - 0.5f);
		if (axis.lengthSquared() < 1.0e-4f) {
			axis.set(0, 1, 0);
		}
		axis.normalize();
		DEBRIS.add(new Debris(x, y, z, vx, vy, vz, size, born, life, axis.x, axis.y, axis.z, (level.random.nextFloat() - 0.5f) * 0.6f, tint));
	}

	/** Each client tick: blocks that just fell break into chunks and dust; darkened creatures smoke; old effects go. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			return;
		}
		sync(level);
		long now = level.getGameTime();
		for (Iterator<Crumble> it = CRUMBLES.iterator(); it.hasNext();) {
			Crumble c = it.next();
			long age = now - c.start;
			int per = c.blocks.size() <= 60 ? 5 : c.blocks.size() <= 160 ? 2 : 1;
			for (int i = 0; i < c.rings.length; i++) {
				if (c.fell[i]) {
					continue;
				}
				long fallAt = (long) c.rings[i] * CatNoirConfig.CATACLYSM_STEP_TICKS + CatNoirConfig.CATACLYSM_CRACK_TICKS;
				if (age < fallAt) {
					continue;
				}
				c.fell[i] = true;
				BlockPos b = c.blocks.get(i);
				for (int k = 0; k < per; k++) {
					double x = b.getX() + 0.2 + level.random.nextDouble() * 0.6;
					double y = b.getY() + 0.2 + level.random.nextDouble() * 0.6;
					double z = b.getZ() + 0.2 + level.random.nextDouble() * 0.6;
					addDebris(x, y, z, (level.random.nextDouble() - 0.5) * 0.07, level.random.nextDouble() * 0.05, (level.random.nextDouble() - 0.5) * 0.07,
							0.1f + level.random.nextFloat() * 0.15f, now, 14 + level.random.nextInt(12), 0xFF38383C, level);
				}
				for (int k = 0; k < 3; k++) {
					level.addParticle(ASH_DUST, b.getX() + level.random.nextDouble(), b.getY() + level.random.nextDouble(), b.getZ() + level.random.nextDouble(),
							0, 0, 0);
				}
				if (level.random.nextInt(3) == 0) {
					level.addParticle(ParticleTypes.LARGE_SMOKE, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 0, 0.02, 0);
				}
			}
			if (age > c.last + 4) {
				it.remove();
			}
		}
		DEBRIS.removeIf(d -> now - d.born() > d.life());
		BURSTS.removeIf(b -> now - b.start() > BURST_TICKS);
		for (Iterator<Map.Entry<Integer, Long>> it = DARK.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> en = it.next();
			long age = now - en.getValue();
			Entity e = level.getEntity(en.getKey());
			if (age > DARK_TICKS || e == null) {
				it.remove();
				continue;
			}
			if (age < DARK_FADE && !e.isInvisible()) {
				double x = e.getX() + (level.random.nextDouble() - 0.5) * e.getBbWidth();
				double y = e.getY() + level.random.nextDouble() * e.getBbHeight();
				double z = e.getZ() + (level.random.nextDouble() - 0.5) * e.getBbWidth();
				level.addParticle(BLACK, x, y, z, 0, 0.03, 0);
				if (level.random.nextInt(3) == 0) {
					level.addParticle(ASH_DUST, x, y, z, 0, 0, 0);
				}
				if (level.random.nextInt(4) == 0) {
					level.addParticle(GREEN, x, y, z, 0, 0.02, 0);
				}
			}
		}
	}

	// ================================================================ the touched creature (CataclysmTouchLayer)

	/** How dark creature {@code id} is now: {darkness, cracks}, each 0..1, or null if it isn't. */
	static float[] darkness(int id, float partial) {
		Long at = DARK.get(id);
		ClientLevel level = Minecraft.getInstance().level;
		if (at == null || level == null) {
			return null;
		}
		float age = level.getGameTime() - at + partial;
		if (age < 0f || age > DARK_TICKS) {
			return null;
		}
		float fade = 1f - Mth.clamp((age - DARK_FADE) / (DARK_TICKS - DARK_FADE), 0f, 1f);
		float dark = smooth(Mth.clamp(age / 7f, 0f, 1f)) * fade;
		float cracks = smooth(Mth.clamp((age - 3f) / 8f, 0f, 1f)) * fade;
		return new float[] { dark, cracks };
	}

	// ================================================================ the charged hand

	/** How far the energy has formed (0..1): it swells over the call, full the rest of the charge. */
	static float grow(Player player, float partial) {
		float age = callAge(player, partial);
		return age < 0f ? 1f : smooth(Mth.clamp((age - 1f) / 9f, 0f, 1f));
	}

	/** Ticks into the call animation, or -1 when it isn't running. */
	static float callAge(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || s.animId != CatNoir.ANIM_CATACLYSM_CALL || player.level() == null) {
			return -1f;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		return age >= 0f && age < CatNoirPose.length(CatNoir.ANIM_CATACLYSM_CALL) ? age : -1f;
	}

	/**
	 * The charged hand on {@code arm} (a part posed this frame): the dark matter on the fist, then the crackling ball at
	 * the open palm, billboarded to the camera (first person: to the view).
	 */
	public static void renderHand(PoseStack pose, MultiBufferSource buffers, Player player, ModelPart arm, boolean right, boolean slim, float ageInTicks,
			float partial, boolean firstPerson) {
		float grow = grow(player, partial);
		float call = callAge(player, partial);
		float age = ageInTicks;
		pose.pushPose();
		arm.translateAndRotate(pose);
		drawFist(pose, buffers, age, slim, right, grow);
		float cx = right ? (slim ? -0.5f : -1f) : (slim ? 0.5f : 1f);
		pose.translate(cx / 16f, (firstPerson ? 11.5f : 12.2f) / 16f, (firstPerson ? -0.5f : 0f) / 16f);
		Matrix4f m = pose.last().pose();
		float sc = new Vector3f(m.m00(), m.m01(), m.m02()).length();
		Quaternionf rot = firstPerson ? new Quaternionf() : new Quaternionf(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		PoseStack bill = new PoseStack();
		bill.last().pose().set(m).set3x3(new Matrix3f().set(rot).scale(sc));
		bill.last().normal().set(new Matrix3f().set(rot));
		if (firstPerson) {
			// the view looks down the arm: bring the ball out in front of the fist, toward the eye
			bill.translate(-0.03f, 0.11f, 0.1f);
		}
		pose.popPose();
		drawEnergy(bill.last(), buffers, firstPerson ? 0.26f : 0.46f, grow, call, age, player.getId(), firstPerson);
	}

	/** Churning dark matter clinging to the fist only (the last 4 pixels of the arm and just past the knuckles). */
	private static void drawFist(PoseStack pose, MultiBufferSource buffers, float age, boolean slim, boolean right, float grow) {
		float x0 = right ? (slim ? -2f : -3f) : -1f, x1 = right ? 1f : (slim ? 2f : 3f);
		float g = 0.5f;
		float[] lo = { (x0 - g) / 16f, 6.6f / 16f, (-2f - g) / 16f }, hi = { (x1 + g) / 16f, 10.7f / 16f, (2f + g) / 16f };
		int frame = ((int) (age * 0.5f)) & 7;
		float u0 = frame / 8f, u1 = (frame + 1) / 8f;
		PoseStack.Pose p = pose.last();
		int a = Math.round(235 * grow);
		box(buffers.getBuffer(RenderType.entityTranslucent(HAND)), p, lo, hi, u0, u1, FastColor.ARGB32.color(a, 255, 255, 255), FB, false);
		float pulse = (0.65f + 0.35f * Mth.sin(age * 0.45f)) * grow;
		int c = Math.round(95 * pulse);
		box(buffers.getBuffer(RenderType.eyes(HAND_GLOW)), p, lo, hi, u0, u1, FastColor.ARGB32.color(255, c, c, c), FB, true);
	}

	/**
	 * The ball of crackling darkness, on a camera-facing pose ({@code p}: x right, y up, +z toward the viewer).
	 * Drawn grouped by render type (a buffer switch flushes the last one): halo and smoke, black arc cores, then the
	 * additive rim glow, violet arc glow and sparks.
	 */
	private static void drawEnergy(PoseStack.Pose p, MultiBufferSource buffers, float size, float grow, float call, float age, int seed,
			boolean firstPerson) {
		float s = size * (0.35f + 0.65f * grow);
		float pulse = 0.7f + 0.3f * Mth.sin(age * 0.5f + seed);
		// ---- the sickly green-black edge (alpha blended, so it stays dark green against a bright sky)
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLOW));
		quad(vc, p, 0f, 0f, -0.05f * s, s * 2.0f, -age * 0.04f, 0f, 0f, 1f, 1f,
				FastColor.ARGB32.color(Math.round(230 * grow * (0.8f + 0.2f * pulse)), 90, 120, 80), FB);
		// ---- the soft dark halo
		vc = buffers.getBuffer(RenderType.entityTranslucent(CORE));
		quad(vc, p, 0f, 0f, -0.04f * s, s * 1.35f, 0f, 0f, 0f, 1f, 1f, FastColor.ARGB32.color(Math.round(235 * grow), 255, 255, 255), FB);
		// ---- swirling smoke wisps (farthest first)
		vc = buffers.getBuffer(RenderType.entityTranslucent(SMOKE));
		int wisps = firstPerson ? 4 : 6;
		for (int i = 0; i < wisps; i++) {
			float h = hash(seed * 7 + i * 13);
			float speed = (0.05f + 0.1f * hash(seed + i * 31)) * (i % 2 == 0 ? 1f : -1f);
			float rot = h * Mth.TWO_PI + age * speed;
			float orbit = s * 0.12f;
			float ox = Mth.cos(age * 0.11f + i * 2.1f) * orbit, oy = Mth.sin(age * 0.13f + i * 1.7f) * orbit;
			float ws = s * (0.68f + 0.3f * hash(seed * 3 + i * 5));
			int frame = ((int) (age * 0.4f) + i * 3) & 7;
			float fu = (frame & 3) / 4f, fv = (frame >> 2) / 2f;
			int a = Math.round((firstPerson ? 215 : 250) * grow);
			quad(vc, p, ox, oy, (-0.03f + 0.012f * i) * s, ws, rot, fu, fv, fu + 0.25f, fv + 0.5f, FastColor.ARGB32.color(a, 255, 255, 255), FB);
		}
		// ---- forming: a ring of smoke thrown off as the ball swells
		if (call >= 5f && call < 17f) {
			float k = (call - 5f) / 12f;
			float e = 1f - (1f - k) * (1f - k);
			int a = Math.round(200 * (1f - k));
			for (int i = 0; i < 8; i++) {
				float ang = i * Mth.TWO_PI / 8f + hash(seed + i) * 0.5f;
				float r = s * (0.3f + 1.1f * e);
				int frame = (i * 3 + (int) call) & 7;
				float fu = (frame & 3) / 4f, fv = (frame >> 2) / 2f;
				quad(vc, p, Mth.cos(ang) * r, Mth.sin(ang) * r, 0.02f * s, s * (0.35f + 0.3f * k), ang + age * 0.1f, fu, fv, fu + 0.25f, fv + 0.5f,
						FastColor.ARGB32.color(a, 255, 255, 255), FB);
			}
		}
		// ---- lightning: black cores
		List<float[]> arcs = arcs(seed, age, s, grow, firstPerson ? 3 : 5);
		vc = buffers.getBuffer(RenderType.entityTranslucent(ARC));
		for (float[] arc : arcs) {
			ribbon(vc, p, arc, 0.05f * s, s * 0.035f, FastColor.ARGB32.color(235, 6, 0, 14), FB, false);
		}
		// ---- additive: a faint green light on the rim (it shows in the dark)
		vc = buffers.getBuffer(RenderType.eyes(GLOW));
		int g = Math.round(60 * pulse * grow * (firstPerson ? 0.75f : 1f));
		quad2(vc, p, 0f, 0f, 0.03f * s, s * 2.1f, age * 0.05f, 0f, 0f, 1f, 1f, FastColor.ARGB32.color(255, g, g, g), FB);
		// ---- additive: violet glow round the arcs
		vc = buffers.getBuffer(RenderType.eyes(ARC));
		for (float[] arc : arcs) {
			ribbon(vc, p, arc, 0.045f * s, s * 0.13f, FastColor.ARGB32.color(255, 70, 18, 120), FB, true);
		}
		// ---- additive: sparks (pulled in while it forms)
		vc = buffers.getBuffer(RenderType.eyes(SPARK));
		int n = firstPerson ? 5 : 8;
		float pull = grow < 1f ? Mth.lerp(grow, 2.2f, 1f) : 1f;
		for (int i = 0; i < n; i++) {
			float ang = hash(seed * 11 + i * 7) * Mth.TWO_PI + age * (0.04f + 0.05f * hash(i + 99)) * (i % 2 == 0 ? 1 : -1);
			float r = s * (0.42f + 0.32f * hash(seed + i * 17)) * pull;
			float tw = Math.max(0f, Mth.sin(age * 0.9f + i * 2.3f + seed));
			float ss = s * 0.08f * tw;
			if (ss < 0.002f) {
				continue;
			}
			int col = i % 3 == 0 ? FastColor.ARGB32.color(255, 150, 60, 230) : FastColor.ARGB32.color(255, 110, 230, 80);
			quad2(vc, p, Mth.cos(ang) * r, Mth.sin(ang) * r, 0.06f * s, ss, age * 0.2f, 0f, 0f, 1f, 1f, col, FB);
		}
	}

	/**
	 * Lightning arcs in the billboard's plane: jagged polylines (x, y pairs) out from near the middle, re-forked every
	 * 2 ticks, some skipping a beat (the flicker).
	 */
	private static List<float[]> arcs(int seed, float age, float s, float grow, int count) {
		List<float[]> out = new ArrayList<>();
		int beat = (int) (age / 2f);
		for (int a = 0; a < count; a++) {
			int h0 = seed * 977 + beat * 131 + a * 61;
			if (hash(h0) < 0.25f) {
				continue; // this one skips a beat
			}
			int segs = 6;
			float ang = hash(h0 + 1) * Mth.TWO_PI;
			float r0 = s * 0.12f, r1 = s * (0.55f + 0.35f * hash(h0 + 2)) * (0.5f + 0.5f * grow);
			float[] pts = new float[(segs + 1) * 2];
			for (int i = 0; i <= segs; i++) {
				float t = i / (float) segs;
				float r = Mth.lerp(t, r0, r1);
				float jitter = i == 0 ? 0f : (hash(h0 + 10 + i) - 0.5f) * 0.7f;
				float aa = ang + jitter + t * (hash(h0 + 3) - 0.5f) * 1.2f;
				pts[i * 2] = Mth.cos(aa) * r;
				pts[i * 2 + 1] = Mth.sin(aa) * r;
			}
			out.add(pts);
			// a short fork off the middle
			if (hash(h0 + 4) < 0.5f) {
				int at = 2 + (int) (hash(h0 + 5) * 3);
				float fx = pts[at * 2], fy = pts[at * 2 + 1];
				float fa = (float) Math.atan2(fy, fx) + (hash(h0 + 6) < 0.5f ? 0.7f : -0.7f);
				float[] fork = new float[8];
				fork[0] = fx;
				fork[1] = fy;
				for (int i = 1; i < 4; i++) {
					float rr = s * 0.09f * i;
					float j = (hash(h0 + 20 + i) - 0.5f) * 0.8f;
					fork[i * 2] = fx + Mth.cos(fa + j) * rr;
					fork[i * 2 + 1] = fy + Mth.sin(fa + j) * rr;
				}
				out.add(fork);
			}
		}
		return out;
	}

	// ================================================================ the world: corrosion, debris, bursts

	public static void renderWorld(WorldRenderContext ctx) {
		ClientLevel level = ctx.world();
		MultiBufferSource buffers = ctx.consumers();
		if (level == null || buffers == null) {
			return;
		}
		sync(level);
		if (CRUMBLES.isEmpty() && DEBRIS.isEmpty() && BURSTS.isEmpty()) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		float now = level.getGameTime() + partial;
		if (!CRUMBLES.isEmpty()) {
			renderCorrosion(pose, buffers, level, cam, now, false);
			renderCorrosion(pose, buffers, level, cam, now, true);
		}
		if (!DEBRIS.isEmpty()) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(ASH));
			for (Debris d : DEBRIS) {
				float t = now - d.born();
				if (t < 0f || t > d.life()) {
					continue;
				}
				float k = t / d.life();
				double x = d.x() + d.vx() * t, y = d.y() + d.vy() * t - 0.5 * GRAVITY * t * t, z = d.z() + d.vz() * t;
				float sz = d.size() * (float) Math.pow(1f - k, 0.6);
				if (sz < 0.004f) {
					continue;
				}
				pose.pushPose();
				pose.translate(x - cam.x, y - cam.y, z - cam.z);
				pose.mulPose(new Quaternionf().rotationAxis(d.spin() * t, d.ax(), d.ay(), d.az()));
				float h = sz / 2f;
				int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
				float dk = Mth.clamp(k * 1.6f, 0f, 1f);
				int r = Math.round(FastColor.ARGB32.red(d.tint()) * (1f - dk * 0.75f)), gg = Math.round(FastColor.ARGB32.green(d.tint()) * (1f - dk * 0.75f)),
						b = Math.round(FastColor.ARGB32.blue(d.tint()) * (1f - dk * 0.75f));
				int a = Math.round(255 * Mth.clamp((1f - k) * 3f, 0f, 1f));
				box(vc, pose.last(), new float[] { -h, -h, -h }, new float[] { h, h, h }, 0f, 1f, FastColor.ARGB32.color(a, r, gg, b), light, false);
				pose.popPose();
			}
		}
		if (!BURSTS.isEmpty()) {
			Quaternionf rot = ctx.camera().rotation();
			for (Burst b : BURSTS) {
				float age = now - b.start();
				if (age < 0f || age > BURST_TICKS) {
					continue;
				}
				pose.pushPose();
				pose.translate(b.at().x - cam.x, b.at().y - cam.y, b.at().z - cam.z);
				pose.mulPose(rot);
				drawBurst(pose.last(), buffers, b.scale(), age / BURST_TICKS, age, b.seed());
				pose.popPose();
			}
		}
	}

	/** The corrosion decals on every crumbling block's open faces (or their faint green glow, the second pass). */
	private static void renderCorrosion(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 cam, float now, boolean glow) {
		VertexConsumer vc = buffers.getBuffer(glow ? RenderType.eyes(CORRODE_GLOW) : RenderType.entityTranslucent(CORRODE));
		PoseStack.Pose p = pose.last();
		float lead = 3f; // the corrosion creeps in a little before the server's crack clock
		float dur = CatNoirConfig.CATACLYSM_CRACK_TICKS + lead;
		for (Crumble c : CRUMBLES) {
			float age = now - c.start;
			Vec3 o = Vec3.atCenterOf(c.origin);
			for (int i = 0; i < c.rings.length; i++) {
				BlockPos b = c.blocks.get(i);
				float k = (age - (c.rings[i] * CatNoirConfig.CATACLYSM_STEP_TICKS - lead)) / dur;
				if (k < 0f || c.fell[i]) {
					continue;
				}
				BlockState st = level.getBlockState(b);
				if (st.isAir()) {
					continue;
				}
				int stage = Mth.clamp((int) (k * 8f), 0, 7);
				Vec3 toO = o.subtract(Vec3.atCenterOf(b));
				for (Direction d : Direction.values()) {
					BlockPos n = b.relative(d);
					if (level.getBlockState(n).isSolidRender(level, n)) {
						continue;
					}
					int light = glow ? FB : LevelRenderer.getLightColor(level, n);
					int col = glow ? FastColor.ARGB32.color(255, 60, 60, 60) : 0xFFFFFFFF;
					face(vc, p, b, cam, d, toO, stage, col, light, glow, c.origin.hashCode() ^ b.hashCode());
				}
			}
		}
	}

	/** One decal: face {@code d} of block {@code b}, its bottom edge (the stage's contact edge) turned toward the touch. */
	private static void face(VertexConsumer vc, PoseStack.Pose p, BlockPos b, Vec3 cam, Direction d, Vec3 toO, int stage, int col, int light,
			boolean twoSided, int h) {
		Vector3f n = new Vector3f(d.getStepX(), d.getStepY(), d.getStepZ());
		Vector3f u, v;
		switch (d.getAxis()) {
			case Y -> {
				u = new Vector3f(1, 0, 0);
				v = new Vector3f(0, 0, 1);
			}
			case X -> {
				u = new Vector3f(0, 0, 1);
				v = new Vector3f(0, 1, 0);
			}
			default -> {
				u = new Vector3f(1, 0, 0);
				v = new Vector3f(0, 1, 0);
			}
		}
		Vector3f o = new Vector3f((float) toO.x, (float) toO.y, (float) toO.z);
		Vector3f[] downs = { new Vector3f(v).negate(), new Vector3f(v), new Vector3f(u).negate(), new Vector3f(u) };
		int pick = 0;
		if (o.lengthSquared() < 0.01f) {
			pick = Math.floorMod(h, 4);
		} else {
			float best = -9f;
			for (int i = 0; i < 4; i++) {
				float dot = downs[i].dot(o);
				if (dot > best + 1.0e-3f) {
					best = dot;
					pick = i;
				}
			}
		}
		Vector3f down = downs[pick];
		Vector3f right = pick < 2 ? new Vector3f(u) : new Vector3f(v);
		if ((h & 4) != 0) {
			right.negate(); // a mirrored variant, so neighbouring faces don't repeat
		}
		float cx = (float) (b.getX() + 0.5 - cam.x) + n.x * 0.5035f, cy = (float) (b.getY() + 0.5 - cam.y) + n.y * 0.5035f,
				cz = (float) (b.getZ() + 0.5 - cam.z) + n.z * 0.5035f;
		float u0 = stage / 8f, u1 = (stage + 1) / 8f;
		float[][] st = { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
		float[][] pos = new float[4][3];
		for (int i = 0; i < 4; i++) {
			float s = st[i][0] - 0.5f, t = st[i][1] - 0.5f;
			pos[i][0] = cx + right.x * s * 1.004f + down.x * t * 1.004f;
			pos[i][1] = cy + right.y * s * 1.004f + down.y * t * 1.004f;
			pos[i][2] = cz + right.z * s * 1.004f + down.z * t * 1.004f;
		}
		for (int i = 0; i < 4; i++) {
			vert(vc, p, pos[i][0], pos[i][1], pos[i][2], Mth.lerp(st[i][0], u0, u1), st[i][1], col, light, n.x, n.y, n.z);
		}
		if (twoSided) {
			for (int i = 3; i >= 0; i--) {
				vert(vc, p, pos[i][0], pos[i][1], pos[i][2], Mth.lerp(st[i][0], u0, u1), st[i][1], col, light, n.x, n.y, n.z);
			}
		}
	}

	/** The dark surge where a touch landed: a black flash, smoke thrown outward, arcs crawling out, a green flare. */
	private static void drawBurst(PoseStack.Pose p, MultiBufferSource buffers, float scale, float k, float age, int seed) {
		float e = 1f - (1f - k) * (1f - k);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(CORE));
		quad(vc, p, 0f, 0f, -0.05f, scale * (0.7f + 0.9f * e), 0f, 0f, 0f, 1f, 1f, FastColor.ARGB32.color(Math.round(230 * (1f - k) * (1f - k)), 255, 255, 255),
				FB);
		vc = buffers.getBuffer(RenderType.entityTranslucent(SMOKE));
		for (int i = 0; i < 10; i++) {
			float ang = i * Mth.TWO_PI / 10f + hash(seed + i) * 0.6f;
			float r = scale * (0.15f + 0.85f * e) * (0.7f + 0.3f * hash(seed * 3 + i));
			int frame = (i * 5 + (int) (age * 0.5f)) & 7;
			float fu = (frame & 3) / 4f, fv = (frame >> 2) / 2f;
			quad(vc, p, Mth.cos(ang) * r, Mth.sin(ang) * r, -0.02f + i * 0.003f, scale * (0.45f + 0.35f * k), ang + age * 0.08f, fu, fv, fu + 0.25f, fv + 0.5f,
					FastColor.ARGB32.color(Math.round(230 * (1f - k)), 255, 255, 255), FB);
		}
		if (k < 0.7f) {
			List<float[]> arcs = arcs(seed, age, scale * (0.8f + 0.8f * e), 1f, 6);
			vc = buffers.getBuffer(RenderType.entityTranslucent(ARC));
			for (float[] arc : arcs) {
				ribbon(vc, p, arc, 0.03f, scale * 0.03f, FastColor.ARGB32.color(235, 6, 0, 14), FB, false);
			}
			vc = buffers.getBuffer(RenderType.eyes(ARC));
			for (float[] arc : arcs) {
				ribbon(vc, p, arc, 0.035f, scale * 0.11f, FastColor.ARGB32.color(255, 70, 18, 120), FB, true);
			}
		}
		vc = buffers.getBuffer(RenderType.eyes(GLOW));
		int g = Math.round(110 * (1f - k));
		quad2(vc, p, 0f, 0f, 0.04f, scale * (1.0f + 1.2f * e), 0f, 0f, 0f, 1f, 1f, FastColor.ARGB32.color(255, g, g, g), FB);
	}

	// ================================================================ geometry

	/** A square of side {@code size} centred on (x, y, z) in a camera-facing pose, turned {@code rot} about the view axis. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float size, float rot, float u0, float v0, float u1,
			float v1, int col, int light) {
		float h = size / 2f, c = Mth.cos(rot) * h, s = Mth.sin(rot) * h;
		// corners (-h,-h) (h,-h) (h,h) (-h,h) rotated; counter-clockwise seen from +z
		vert(vc, p, x - c + s, y - s - c, z, u0, v1, col, light, 0, 0, 1);
		vert(vc, p, x + c + s, y + s - c, z, u1, v1, col, light, 0, 0, 1);
		vert(vc, p, x + c - s, y + s + c, z, u1, v0, col, light, 0, 0, 1);
		vert(vc, p, x - c - s, y - s + c, z, u0, v0, col, light, 0, 0, 1);
	}

	/** {@link #quad}, both windings (for the culled additive render type). */
	private static void quad2(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float size, float rot, float u0, float v0, float u1,
			float v1, int col, int light) {
		quad(vc, p, x, y, z, size, rot, u0, v0, u1, v1, col, light);
		float h = size / 2f, c = Mth.cos(rot) * h, s = Mth.sin(rot) * h;
		vert(vc, p, x - c - s, y - s + c, z, u0, v0, col, light, 0, 0, 1);
		vert(vc, p, x + c - s, y + s + c, z, u1, v0, col, light, 0, 0, 1);
		vert(vc, p, x + c + s, y + s - c, z, u1, v1, col, light, 0, 0, 1);
		vert(vc, p, x - c + s, y - s - c, z, u0, v1, col, light, 0, 0, 1);
	}

	/** A flat ribbon along polyline {@code pts} (x, y pairs) at depth {@code z}, {@code width} across (the arc texture's v). */
	private static void ribbon(VertexConsumer vc, PoseStack.Pose p, float[] pts, float z, float width, int col, int light, boolean twoSided) {
		int n = pts.length / 2;
		for (int i = 0; i + 1 < n; i++) {
			float ax = pts[i * 2], ay = pts[i * 2 + 1], bx = pts[i * 2 + 2], by = pts[i * 2 + 3];
			float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < 1.0e-5f) {
				continue;
			}
			// taper toward the tip
			float wa = width * 0.5f * (1f - 0.6f * i / (n - 1f)), wb = width * 0.5f * (1f - 0.6f * (i + 1) / (n - 1f));
			float px = -dy / len, py = dx / len;
			float[][] q = { { ax - px * wa, ay - py * wa, 0, 1 }, { bx - px * wb, by - py * wb, 1, 1 }, { bx + px * wb, by + py * wb, 1, 0 },
					{ ax + px * wa, ay + py * wa, 0, 0 } };
			for (float[] v : q) {
				vert(vc, p, v[0], v[1], z, v[2], v[3], col, light, 0, 0, 1);
			}
			if (twoSided) {
				for (int k = 3; k >= 0; k--) {
					vert(vc, p, q[k][0], q[k][1], z, q[k][2], q[k][3], col, light, 0, 0, 1);
				}
			}
		}
	}

	/** An axis-aligned box from {@code lo} to {@code hi}, each face showing u0..u1 x 0..1 of the texture. */
	private static void box(VertexConsumer vc, PoseStack.Pose p, float[] lo, float[] hi, float u0, float u1, int col, int light, boolean twoSided) {
		float x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
		float[][][] faces = {
				{ { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 }, { 0, 0, -1 } },
				{ { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 }, { x0, y0, z1 }, { 0, 0, 1 } },
				{ { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 }, { x0, y0, z0 }, { -1, 0, 0 } },
				{ { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 }, { 1, 0, 0 } },
				{ { x0, y0, z1 }, { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { 0, -1, 0 } },
				{ { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 }, { 0, 1, 0 } } };
		float[][] uv = { { u0, 1 }, { u0, 0 }, { u1, 0 }, { u1, 1 } };
		for (float[][] f : faces) {
			float[] nn = f[4];
			for (int i = 0; i < 4; i++) {
				vert(vc, p, f[i][0], f[i][1], f[i][2], uv[i][0], uv[i][1], col, light, nn[0], nn[1], nn[2]);
			}
			if (twoSided) {
				for (int i = 3; i >= 0; i--) {
					vert(vc, p, f[i][0], f[i][1], f[i][2], uv[i][0], uv[i][1], col, light, nn[0], nn[1], nn[2]);
				}
			}
		}
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int col, int light, float nx, float ny,
			float nz) {
		vc.addVertex(p, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	private static float hash(int x) {
		int h = x * 374761393 + 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}
}
