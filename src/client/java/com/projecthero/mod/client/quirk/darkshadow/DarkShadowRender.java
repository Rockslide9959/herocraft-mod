package com.projecthero.mod.client.quirk.darkshadow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.darkshadow.DarkShadowCombat;
import com.projecthero.mod.quirk.darkshadow.DarkShadowConfig;
import com.projecthero.mod.quirk.data.QuirkState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Quirk revamp: Dark Shadow in the world, for every viewer. The creature is a GeckoLib model drawn without
 * an entity ({@link DarkShadowCreature}), placed every frame from its user's interpolated position and the synced Quirk
 * state -- so it never lags, never needs spawning, saving, chunk loading or dimension handling, and every client sees the
 * same thing (the server only ever decides what it does).
 *
 * <ul>
 *   <li><b>Rest:</b> it grows out of its user's upper back, a little to the left, faces where they look (lagging a
 *       little), sways like a snake ({@code idle}); in bright light it is small and hides behind the shoulder
 *       ({@code timid}). Its size follows the synced darkness, smoothed here.</li>
 *   <li><b>Reaching moves</b> (Strike, Dark Fang, Grab, Fetch, its berserk hits): the whole body is carried out toward the
 *       target and a shadow tendril stretches from the back to its root, with after-image smears along the way.</li>
 *   <li><b>Scout:</b> it flies the synced path out and back on its own wings, tethered by the tendril.</li>
 *   <li><b>Shield:</b> it coils round its user, the wings closed in front.</li>
 *   <li><b>Berserk:</b> towering ({@link DarkShadowConfig.Berserk#size}), spiked, red-eyed, thrashing.</li>
 *   <li><b>Black Ankh:</b> not drawn here (it is the armour, {@link DarkShadowAnkhLayer}) unless a move sends it out.</li>
 *   <li><b>Hidden (N, or Shift+V Recall):</b> it flinches, folds up and pours back into the back with a puff of shadow,
 *       then is gone (synced, so every viewer sees it hidden); N again and it boils back out ({@code summon}).</li>
 * </ul>
 * First person, your own shadow shows only while it is out of the way of the camera (reaching, scouting, berserk). It sheds
 * faint dark motes off its outline (client particles).
 */
public final class DarkShadowRender {
	public static final ResourceLocation TENDRIL = ProjectHeroMod.id("textures/entity/quirk/dark_shadow/tendril.png");
	private static final Map<Integer, DarkShadowCreature.Shape> SHAPES = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> MOTES = new ConcurrentHashMap<>();
	private static DarkShadowCreature.CreatureRenderer renderer;

	private DarkShadowRender() {
	}

	static void reset() {
		SHAPES.clear();
		MOTES.clear();
	}

	static DarkShadowCreature.Shape shape(Player p) {
		return SHAPES.computeIfAbsent(p.getId(), k -> new DarkShadowCreature.Shape("dark_shadow"));
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Ticks since the synced clip started. */
	static float animAge(Player p, QuirkState s, float partial) {
		return p.level().getGameTime() - s.animStart + partial;
	}

	/** How far it is carried out to the reach point (0..1) for the playing move. */
	static float extension(Player p, QuirkState s, float partial) {
		if (DarkShadow.on(p, DarkShadow.V_GRAB) || DarkShadow.running(p, DarkShadow.V_FANG) || DarkShadow.val(p, DarkShadow.V_FETCH) == 1L) {
			return 1f;
		}
		float age = animAge(p, s, partial);
		int len = switch (s.animId) {
			case DarkShadow.ANIM_STRIKE -> 12;
			case DarkShadow.ANIM_FRENZY_HIT -> 9;
			case DarkShadow.ANIM_FETCH -> 12;
			case DarkShadow.ANIM_FANG -> 20;
			case DarkShadow.ANIM_GRAB, DarkShadow.ANIM_SLAM -> 10;
			default -> 0;
		};
		if (len == 0 || age < 0f || age >= len) {
			return 0f;
		}
		float out = Math.max(1.5f, len * 0.25f);
		float hold = len * 0.55f;
		if (age < out) {
			return ease(age / out);
		}
		if (age < hold) {
			return 1f;
		}
		return 1f - ease((age - hold) / (len - hold));
	}

	/** Which clip plays now. */
	static String clipFor(Player p, QuirkState s, float partial, float darkness) {
		float age = animAge(p, s, partial);
		if (DarkShadow.berserk(p)) {
			return "berserk";
		}
		if (DarkShadow.recalled(p)) {
			return "recall";
		}
		if (DarkShadow.on(p, DarkShadow.V_SCOUT)) {
			return "scout";
		}
		if (DarkShadow.shielding(p)) {
			return "shield";
		}
		if (s.animId == DarkShadow.ANIM_SLAM && age < 11f) {
			return "slam";
		}
		if (DarkShadow.on(p, DarkShadow.V_GRAB)) {
			return "grab";
		}
		if (DarkShadow.running(p, DarkShadow.V_FANG)) {
			return "bite";
		}
		float len = switch (s.animId) {
			case DarkShadow.ANIM_STRIKE, DarkShadow.ANIM_FRENZY_HIT -> 12f;
			case DarkShadow.ANIM_FETCH -> 14f;
			case DarkShadow.ANIM_NOVA -> 18f;
			case DarkShadow.ANIM_SUMMON -> 14f;
			case DarkShadow.ANIM_CALM, DarkShadow.ANIM_FLARE -> 28f;
			case DarkShadow.ANIM_GRAB -> 12f;
			default -> 0f;
		};
		if (age >= 0f && age < len) {
			return switch (s.animId) {
				case DarkShadow.ANIM_STRIKE, DarkShadow.ANIM_FRENZY_HIT -> "strike";
				case DarkShadow.ANIM_FETCH -> "fetch";
				case DarkShadow.ANIM_NOVA -> "nova";
				case DarkShadow.ANIM_SUMMON -> "summon";
				case DarkShadow.ANIM_GRAB -> "grab";
				default -> "calm";
			};
		}
		return darkness < 0.3f ? "timid" : "idle";
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		if (renderer == null) {
			renderer = new DarkShadowCreature.CreatureRenderer();
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			if (!DarkShadow.is(p) || p.isInvisible() || p.isSpectator() || p.distanceToSqr(cam) > 128 * 128) {
				continue;
			}
			QuirkState s = Quirks.peek(p);
			if (s == null) {
				continue;
			}
			boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			try {
				renderOne(ctx.matrixStack(), ctx.consumers(), p, s, partial, cam, firstPerson);
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.warn("[ProjectHero] Dark Shadow render failed", e);
				SHAPES.remove(p.getId());
			}
		}
	}

	private static Vec3 yawDir(float yawDeg) {
		float r = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	private static Vec3 leftOf(Vec3 fwd) {
		return new Vec3(fwd.z, 0, -fwd.x);
	}

	private static float yawOf(Vec3 d) {
		return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
	}

	private static Entity entity(Player p, long idPlus1) {
		if (idPlus1 <= 0L) {
			return null;
		}
		Entity e = p.level().getEntity((int) (idPlus1 - 1L));
		return e != null && e.isAlive() ? e : null;
	}

	private static void renderOne(PoseStack pose, MultiBufferSource buffers, Player p, QuirkState s, float partial, Vec3 cam, boolean firstPerson) {
		Minecraft mc = Minecraft.getInstance();
		DarkShadowCreature.Shape sh = shape(p);
		long now = p.level().getGameTime();
		float t = now + partial;
		Vec3 pos = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float headYaw = p.getViewYRot(partial);
		float pitch = p.getViewXRot(partial);
		Vec3 fwd = yawDir(bodyYaw);
		Vec3 left = leftOf(fwd);
		boolean berserk = DarkShadow.berserk(p);
		float darkness = DarkShadow.darkness(p);
		float age = animAge(p, s, partial);

		// ---- what it is doing
		String clip = clipFor(p, s, partial, darkness);
		boolean ankh = DarkShadow.ankh(p);
		float ext = extension(p, s, partial);
		boolean scouting = DarkShadow.on(p, DarkShadow.V_SCOUT);
		if (ankh && ext <= 0.02f && !scouting && !"nova".equals(clip)) {
			return; // it is the armour now
		}
		if (DarkShadow.recalled(p) && (s.animId != DarkShadow.ANIM_RECALL || age > 12f)) {
			return; // hidden (N / Shift+V): tucked away once the 0.6 s fold-up has played -- and gone for good if another
			// clip id (the Light Flare, which works while hidden) replaced it, so it never pops back out un-summoned
		}

		// ---- its size (smoothed per frame, time-based)
		float targetSize = DarkShadow.size(darkness, berserk);
		if ("calm".equals(clip)) {
			targetSize *= 0.7f; // it shrinks from the light
		}
		long nanos = System.nanoTime();
		float dt = sh.lastFrameNanos == 0L ? 1f : Mth.clamp((nanos - sh.lastFrameNanos) / 5.0e7f, 0f, 3f); // in ticks
		sh.lastFrameNanos = nanos;
		sh.size = sh.size < 0f ? targetSize : sh.size + (targetSize - sh.size) * Mth.clamp(0.12f * dt, 0f, 1f);
		float size = sh.size;

		// ---- where its root goes, and which way it faces
		float crouch = p.isCrouching() ? -0.22f : 0f;
		Vec3 back = pos.add(0, 1.22 + crouch, 0).subtract(fwd.scale(0.22));
		Vec3 root = back.add(left.scale(0.34 + 0.16 * size)).subtract(fwd.scale(0.12 * size));
		float wantYaw = headYaw;
		Vec3 target = null;
		Entity grabbed = entity(p, s.val(DarkShadow.V_GRAB));
		if (grabbed != null) {
			target = grabbed.getPosition(partial).add(0, grabbed.getBbHeight() * 0.55, 0);
		} else if (ext > 0f) {
			target = DarkShadow.reach(p);
		}
		Vec3 restHead = root.add(0, 1.85 * size, 0).add(yawDir(headYaw).scale(0.55 * size));
		if (target != null && ext > 0f) {
			Vec3 d = target.subtract(restHead);
			double len = d.length();
			double maxLen = 16.0;
			if (len > maxLen) {
				d = d.scale(maxLen / len);
				len = maxLen;
			}
			// stop short so the head (not the root) arrives, beak first
			Vec3 stop = len > 0.8 ? d.subtract(d.normalize().scale(0.55 * size)) : d;
			root = root.add(stop.scale(ext));
			wantYaw = yawOf(target.subtract(back));
		}
		if (scouting) {
			Vec3 to = DarkShadow.scoutTarget(p);
			if (to != null) {
				DarkShadowConfig.Moves m = DarkShadowConfig.get().moves;
				float sa = now - s.val(DarkShadow.V_SCOUT) + partial;
				Vec3 home = root;
				Vec3 at;
				if (sa < m.scoutOutTicks) {
					at = home.lerp(to, ease(sa / m.scoutOutTicks));
					wantYaw = yawOf(to.subtract(home));
				} else if (sa < m.scoutOutTicks + m.scoutHoverTicks) {
					at = to.add(0, 0.25 * Mth.sin(t * 0.4f), 0);
					wantYaw = headYaw + 25f * Mth.sin(t * 0.15f);
				} else {
					at = to.lerp(home, ease((sa - m.scoutOutTicks - m.scoutHoverTicks) / m.scoutOutTicks));
					wantYaw = yawOf(home.subtract(to)) + 180f;
				}
				root = at.subtract(0, 0.9 * size, 0);
			}
		}
		if ("shield".equals(clip)) {
			root = pos.add(0, 0.55 + crouch, 0).subtract(fwd.scale(0.35));
			wantYaw = bodyYaw;
			size = Math.max(size, 0.85f);
		}
		if (berserk) {
			root = back.subtract(fwd.scale(0.3)).add(left.scale(0.3));
			wantYaw = headYaw + 35f * Mth.sin(t * 0.17f) + 15f * Mth.sin(t * 0.53f);
		}
		boolean show = !firstPerson || ext > 0.15f || scouting || berserk;
		if (!show) {
			return;
		}
		// facing: it turns after its user, a beat behind
		if (Float.isNaN(sh.yaw)) {
			sh.yaw = wantYaw;
		}
		sh.yaw = sh.yaw + Mth.wrapDegrees(wantYaw - sh.yaw) * Mth.clamp(0.25f * dt, 0f, 1f);

		// ---- the look bone: at the target, else where its user looks
		float lookYaw = Mth.wrapDegrees(headYaw - sh.yaw);
		float lookPitch = pitch * 0.7f;
		if (target != null) {
			Vec3 headAt = root.add(0, 1.85 * size, 0);
			Vec3 d = target.subtract(headAt);
			lookYaw = Mth.wrapDegrees(yawOf(d) - sh.yaw);
			lookPitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		}
		if (berserk || scouting || "calm".equals(clip)) {
			lookYaw *= 0.3f;
			lookPitch *= 0.3f;
		}
		sh.lookYaw = Mth.clamp(lookYaw, -75f, 75f) * Mth.DEG_TO_RAD;
		sh.lookPitch = Mth.clamp(lookPitch, -50f, 60f) * Mth.DEG_TO_RAD;
		sh.berserk = berserk;
		sh.clip = clip;
		sh.clipKey = s.animStart;
		// close to the camera (your own, in third person) it thins out so it never hides you
		double camDist = root.add(0, size, 0).distanceTo(cam);
		sh.alpha = (int) (255 * Mth.clamp((camDist - 0.8) / (2.2 * Math.max(1f, size * 0.6f)), 0.35, 1.0));

		// ---- the tendril from the back to its root when it is carried out
		int light = light(p, root.add(0, 1.2 * size, 0));
		if (root.distanceToSqr(back) > 0.3 * 0.3) {
			drawTendril(pose, buffers, back, root.add(0, 0.15 * size, 0), cam, light, Math.max(0.12f, 0.2f * size), t);
		}
		// ---- the creature
		pose.pushPose();
		pose.translate(root.x - cam.x, root.y - cam.y, root.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - sh.yaw));
		pose.scale(size, size, size);
		renderer.render(pose, sh, buffers, null, null, light, partial);
		pose.popPose();

		// ---- the motes it sheds (and the after-image smear of a lunge), once a tick
		Long last = MOTES.get(p.getId());
		if ((last == null || last != now) && !mc.isPaused() && now % 2 == 0) {
			MOTES.put(p.getId(), now);
			var rnd = p.getRandom();
			int n = berserk ? 2 : 1;
			for (int i = 0; i < n; i++) {
				Vec3 m = root.add((rnd.nextDouble() - 0.5) * 0.9 * size, rnd.nextDouble() * 2.0 * size, (rnd.nextDouble() - 0.5) * 0.9 * size);
				mote(p, firstPerson, cam, DarkShadowCombat.SHADOW, m.x, m.y, m.z, 0, 0.015, 0);
			}
			// v0.16.7 (user) N hide / show: shadow streams INTO the back as it tucks away, and bursts out of it as it emerges
			if ("recall".equals(clip)) {
				for (int i = 0; i < 4; i++) {
					Vec3 m = root.add((rnd.nextDouble() - 0.5) * 1.2 * size, rnd.nextDouble() * 2.0 * size, (rnd.nextDouble() - 0.5) * 1.2 * size);
					Vec3 v = back.subtract(m).scale(0.12);
					mote(p, firstPerson, cam, DarkShadowCombat.SHADOW_BIG, m.x, m.y, m.z, v.x, v.y, v.z);
				}
			} else if ("summon".equals(clip) && age < 8f) {
				for (int i = 0; i < 4; i++) {
					Vec3 v = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() * 0.8, rnd.nextDouble() - 0.5).scale(0.18);
					mote(p, firstPerson, cam, DarkShadowCombat.SHADOW_BIG, back.x, back.y, back.z, v.x, v.y, v.z);
				}
			}
			if (ext > 0.1f && ext < 0.95f) {
				Vec3 a = back, b = root.add(0, 1.2 * size, 0);
				for (int i = 0; i < 4; i++) {
					Vec3 m = a.lerp(b, rnd.nextDouble());
					mote(p, firstPerson, cam, DarkShadowCombat.SHADOW_BIG, m.x, m.y, m.z, 0, 0, 0);
				}
			}
		}
	}

	/**
	 * One mote. v0.16.7 (user): in first person your own shadow's motes never spawn right at the camera (within 1.6 blocks
	 * of it they would be screen-filling blotches -- the lunge smear runs from your back, past your eyes).
	 */
	private static void mote(Player p, boolean firstPerson, Vec3 cam, net.minecraft.core.particles.ParticleOptions o,
			double x, double y, double z, double vx, double vy, double vz) {
		if (firstPerson && cam.distanceToSqr(x, y, z) < 1.6 * 1.6) {
			return;
		}
		p.level().addParticle(o, x, y, z, vx, vy, vz);
	}

	private static int light(Player p, Vec3 at) {
		int packed = LevelRenderer.getLightColor(p.level(), BlockPos.containing(at));
		// never pitch black: its violet edges always show a little
		int block = Math.max(LightTexture.block(packed), 6);
		return LightTexture.pack(block, LightTexture.sky(packed));
	}

	/** A sagging, writhing ribbon-tube of shadow from {@code a} to {@code b}: 8 segments of crossed quads. */
	private static void drawTendril(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, Vec3 cam, int light, float thick, float t) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TENDRIL));
		int n = 10;
		Vec3 d = b.subtract(a);
		double len = d.length();
		Vec3 side = new Vec3(d.z, 0, -d.x);
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3[] pts = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			float k = (float) i / n;
			double sag = Math.sin(k * Math.PI) * Math.min(1.2, len * 0.12);
			double wr = Math.sin(k * Math.PI) * 0.15 * Math.sin(t * 0.5 + k * 9.0);
			pts[i] = a.lerp(b, k).add(0, sag * 0.35, 0).add(side.scale(wr));
		}
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < n; i++) {
			float k0 = (float) i / n, k1 = (float) (i + 1) / n;
			float w0 = thick * (1.15f - 0.45f * k0), w1 = thick * (1.15f - 0.45f * k1);
			Vec3 p0 = pts[i], p1 = pts[i + 1];
			Vec3 dir = p1.subtract(p0);
			Vec3 n1 = dir.cross(new Vec3(0, 1, 0));
			if (n1.lengthSqr() < 1.0e-6) {
				n1 = new Vec3(1, 0, 0);
			}
			n1 = n1.normalize();
			Vec3 n2 = dir.cross(n1).normalize();
			for (Vec3 nn : new Vec3[] { n1, n2 }) {
				quad(vc, last, p0.add(nn.scale(w0)), p0.subtract(nn.scale(w0)), p1.subtract(nn.scale(w1)), p1.add(nn.scale(w1)), k0, k1, light);
			}
		}
		pose.popPose();
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose last, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float v0, float v1, int light) {
		Vector3f nrm = new Vector3f(0, 1, 0);
		vert(vc, last, a, 0f, v0, light, nrm);
		vert(vc, last, b, 1f, v0, light, nrm);
		vert(vc, last, c, 1f, v1, light, nrm);
		vert(vc, last, d, 0f, v1, light, nrm);
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose last, Vec3 at, float u, float v, int light, Vector3f n) {
		vc.addVertex(last, (float) at.x, (float) at.y, (float) at.z).setColor(255, 255, 255, 235).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n.x, n.y, n.z);
	}

}
