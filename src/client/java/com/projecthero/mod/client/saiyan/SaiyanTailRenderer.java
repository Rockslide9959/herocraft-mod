package com.projecthero.mod.client.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.render.SpringTail;
import com.projecthero.mod.client.render.TubeMesh;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "Give Saiyans a brown monkey tail that changes colour when they transform"): a furry monkey tail on every
 * Saiyan, for every viewer.
 *
 * <p>v0.17.3 rework (user: "For the Saiyan tail please make it smooth and round like Spider-Monkey's tail and give it the same
 * reaction rules please"): the tail is now Spidermonkey's spring chain ({@link SpringTail}, shared code) -- 7 links in world space,
 * ticked on the client for every Saiyan in view, the same idle / walk rest shapes (low out behind, curling up into a hook; trailing
 * and swaying with the walk cycle when he moves, a slow idle sway standing), the same lag and swing when he turns or moves, loose and
 * streaming behind him when he falls fast, glides or flies, kept behind his back and legs and out of blocks. Crouching leans the root
 * with the body (Spidermonkey's rule; the old waist wrap is gone). Drawn as a smooth round tube ({@link TubeMesh}: a curve through
 * the links, 10-sided rings with real normals, a rounded tip) that tapers from the lower back to a slightly fuller fluffy tip,
 * with a soft fur mottle in its colours.
 *
 * <p>The colour follows the form, eased over about a second: brown in Base, gold as a Super Saiyan, silver in Ultra Instinct, red
 * under Kaioken; a transformed tail glows (light 13+). Drawn from {@code SaiyanLayer} (never in first person), in the model's space,
 * for the wide and slim models alike (their bodies are the same). The per-player state is client-only vectors and floats keyed by
 * UUID (no world or entity is held), pruned each tick and dropped on disconnect.
 */
public final class SaiyanTailRenderer {
	private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("projecthero", "textures/entity/saiyan/white.png");
	public static final int SEGMENTS = 7;
	/** Link lengths in model pixels, base to tip: Spidermonkey's, x0.9 (about 17 px of tail). */
	private static final float[] LEN = { 2.97f, 2.88f, 2.7f, 2.52f, 2.25f, 1.98f, 1.71f };
	/** The root on the body bone (pixels): the lower back, belt height, on the back face. */
	private static final float ROOT_Y = 10.6f, ROOT_Z = 2.0f;
	/** No link nearer his front than this (pixels, model z): the back face (z 2) plus the tube's radius. */
	private static final float BEHIND = 3.1f;
	/** Tube radius (pixels): at the root, the thinnest point (just before the tip) and the fluffy tip. */
	private static final float R_ROOT = 1.1f, R_NECK = 0.6f, R_TIP = 0.82f;
	/** The tail's colours by form (RGB). */
	public static final int BROWN = 0x6B4423;
	public static final int GOLD = 0xFFCF3A;
	public static final int SILVER = 0xCDD6E2;
	public static final int KAIOKEN = 0xC0352C;

	private static final class State {
		final SpringTail tail = new SpringTail(LEN);
		/** The eased colour (r, g, b) and the age it was last eased at. */
		final float[] col = new float[4];
		boolean colInit;
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
	/** Only for the rest shape (its link lengths), never ticked. */
	private static final SpringTail SHAPE = new SpringTail(LEN);

	private SaiyanTailRenderer() {
	}

	public static void reset() {
		STATES.clear();
	}

	/** The colour the tail is heading for. */
	public static int targetColour(Player p) {
		if (SaiyanFx.kaioken(p)) {
			return KAIOKEN;
		}
		SaiyanForm f = SaiyanFx.form(p);
		return f == SaiyanForm.ULTRA ? SILVER : f == SaiyanForm.SSJ ? GOLD : BROWN;
	}

	public static boolean shows(Player p) {
		return Saiyan.hasPower(p) && !p.isInvisible() && !p.isSpectator();
	}

	// ---------------------------------------------------------------- the tick

	/** Every client tick: each Saiyan's tail in view steps, everyone else's state is dropped. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (!STATES.isEmpty()) {
				STATES.clear();
			}
			return;
		}
		for (AbstractClientPlayer p : mc.level.players()) {
			if (shows(p)) {
				tick(p);
			}
		}
		if (!STATES.isEmpty()) {
			STATES.keySet().removeIf(id -> {
				Player p = mc.level.getPlayerByUUID(id);
				return p == null || !shows(p);
			});
		}
	}

	private static void tick(AbstractClientPlayer p) {
		State st = STATES.computeIfAbsent(p.getUUID(), id -> new State());
		float yaw = p.yBodyRot;
		Vec3 base = SpringTail.base(p, 1f);
		Vec3 rm = SpringTail.rootModel(p, ROOT_Y, ROOT_Z);
		float walk = SpringTail.walk(p);
		float phase = SpringTail.phase(p);
		float age = p.tickCount;
		// Spidermonkey's loose rule (falling fast, gliding; his web swings -> the Saiyan's flight): streaming behind him
		boolean loose = p.getDeltaMovement().y < -0.6 || p.isFallFlying() || Saiyan.isFlying(p);
		st.tail.tick(p, yaw, base, rm, SpringTail.MONKEY_IDLE, false, loose, loose ? 0.07f : 0.32f,
				(i, parentModel) -> SpringTail.restDir(SpringTail.MONKEY_IDLE, SpringTail.MONKEY_WALK, i, SEGMENTS, walk, phase, age),
				(i, m) -> SpringTail.keepBehind(m, BEHIND));
	}

	// ---------------------------------------------------------------- drawing

	/** Tube radius (pixels) at {@code t} (0 = root .. 1 = tip): tapering, then filling out into the fluffy tip. */
	static float radius(float t) {
		if (t < 0.78f) {
			float k = t / 0.78f;
			k = k * k * (3f - 2f * k);
			return Mth.lerp(k, R_ROOT, R_NECK);
		}
		float k = (t - 0.78f) / 0.22f;
		return Mth.lerp(Mth.sin(k * Mth.HALF_PI), R_NECK, R_TIP);
	}

	/** From SaiyanLayer. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, float partial,
			float age) {
		if (!shows(player) || !model.body.visible) {
			return;
		}
		State st = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		int target = targetColour(player);
		float[] e = st.col;
		float tr = ((target >> 16) & 255) / 255f, tg = ((target >> 8) & 255) / 255f, tb = (target & 255) / 255f;
		if (!st.colInit) {
			e[0] = tr;
			e[1] = tg;
			e[2] = tb;
			e[3] = age;
			st.colInit = true;
		}
		float dt = Mth.clamp(age - e[3], 0f, 10f);
		e[3] = age;
		float kc = 1f - (float) Math.pow(0.9, dt); // ~1 s to settle
		e[0] += (tr - e[0]) * kc;
		e[1] += (tg - e[1]) * kc;
		e[2] += (tb - e[2]) * kc;
		boolean glows = target != BROWN;
		int lit = glows ? LightTexture.pack(Math.max(LightTexture.block(light), 13), Math.max(LightTexture.sky(light), 13)) : light;

		// the root on the body bone (it leans and drops with the crouch), then the simulated links in this render's model space;
		// a pose the chain does not know (a leaning flight, a spin) keeps the links, moved onto the drawn root
		ModelPart body = model.body;
		Matrix4f bone = new Matrix4f().translate(body.x / 16f, body.y / 16f, body.z / 16f).rotateZYX(body.zRot, body.yRot, body.xRot);
		Vector3f root = bone.transformPosition(new Vector3f(0, ROOT_Y / 16f, ROOT_Z / 16f));
		Vector3f[] pts = new Vector3f[SEGMENTS + 1];
		if (!st.tail.modelPoints(pose, root, partial, pts, 1.5f, true)) {
			// not in the level (the inventory screen) or not ticked yet: the idle curl
			SHAPE.restPoints(root, SpringTail.MONKEY_IDLE, pts);
		}
		float cr = e[0], cg = e[1], cb = e[2];
		PoseStack.Pose last = pose.last();
		// smooth and round; the fur is a soft mottle in the vertex colours (fixed per spot, so it never flickers), the tip tuft lighter.
		// (A translucent fur shell was tried: against the sky it read as a grey outline, not fur.)
		TubeMesh.draw(last, buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), pts, 4, 10, 0.6f / 16f, t -> radius(t) / 16f,
				(t, side) -> argb(255, cr, cg, cb, 0.92f + 0.16f * tuft(t) + fur(t, side)), lit);
	}

	/** 0 along the tail, rising to 1 over the tip. */
	private static float tuft(float t) {
		return Mth.clamp((t - 0.78f) / 0.22f, 0f, 1f);
	}

	/** A gentle fixed mottle (+-0.06) for a vertex at {@code t} along the tail, {@code side} round it. */
	private static float fur(float t, float side) {
		int h = Math.round(t * 64f) * 73856093 ^ Math.round(side * 10f) % 10 * 19349663;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return ((h & 1023) / 1023f - 0.5f) * 0.12f;
	}

	private static int argb(int a, float r, float g, float b, float shade) {
		int ri = Mth.clamp((int) (r * shade * 255f), 0, 255);
		int gi = Mth.clamp((int) (g * shade * 255f), 0, 255);
		int bi = Mth.clamp((int) (b * shade * 255f), 0, 255);
		return a << 24 | ri << 16 | gi << 8 | bi;
	}
}
