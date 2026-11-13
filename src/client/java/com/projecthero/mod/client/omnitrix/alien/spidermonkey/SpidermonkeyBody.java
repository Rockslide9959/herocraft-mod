package com.projecthero.mod.client.omnitrix.alien.spidermonkey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.render.SpringTail;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.spidermonkey.SpidermonkeyKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "give spider-monkey a tail aswell please, make spidermonkeys arms slim and add in his second pair of arms since
 * he has 4 arms and 2 legs, dont make the bottom arms stand out like fourarms does make spider-monkeys arms fit better and look more
 * sleek almost please"; the tail like Humungousaur's: "make it move in relation to how the player moves, dont let it go through
 * blocks or inside [his] body"): Spidermonkey's second pair of arms and his tail.
 * <ul>
 *   <li><b>Lower arms</b>: slim (3 px, a touch thinner and shorter than the main pair, the same skin) on the body bone just under the
 *       shoulders at the sides of the ribcage, flush with the body. They follow the main arm of their side a beat behind (eased
 *       toward it every frame), hanging a little further back so the two pairs read as a fitted set: walk swing, punches, webs,
 *       climbing, the ceiling hang.</li>
 *   <li><b>Tail</b>: a 7-segment spring chain per player in WORLD space, ticked on the client. Each link springs toward a rest
 *       shape in his body's frame (low behind him, curling up into a hook at the tip; straighter and swaying with the walk cycle),
 *       so it swings when he turns and moves, and goes loose and streams behind him on a web swing / zip. Every link is kept
 *       behind his back and legs and pushed out of any block's collision box. Drawn in the model's space (the render matrix's
 *       inverse turns the world points back), the first link from the body bone's own tail root; never in first person (the
 *       body is not drawn there).</li>
 * </ul>
 * The per-player state is dropped when he turns back, leaves view, or the client disconnects. v0.17.3: the chain itself (its tick,
 * reaction rules, body and block rules, world-to-model drawing) is the shared {@link SpringTail}, which the Saiyan tail uses too.
 */
public final class SpidermonkeyBody {
	private static final int N = 7;
	/** Link lengths and thicknesses in model pixels, base to tip. */
	private static final float[] LEN = { 3.3f, 3.2f, 3.0f, 2.8f, 2.5f, 2.2f, 1.9f };
	private static final float[] WID = { 1.4f, 1.3f, 1.2f, 1.1f, 1.0f, 0.9f, 0.8f };
	/** Rest directions (model space: x = his left... y down, z behind him), idle: low out behind, up, hooked over at the tip. */
	private static final float[][] REST_IDLE = SpringTail.MONKEY_IDLE;
	/** ...and walking: trailing out further, the hook opened up. */
	private static final float[][] REST_WALK = SpringTail.MONKEY_WALK;
	/** The tail's root on the body bone (pixels): the base of his back. */
	private static final float ROOT_Y = 11.0f, ROOT_Z = 2.0f;
	/** No link comes nearer his front than this (pixels, model z): just behind the back / the backs of the legs. */
	private static final float BEHIND = 2.7f;

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Only for the rest shape (its link lengths), never ticked. */
	private static final SpringTail SHAPE = new SpringTail(LEN);
	private static boolean registered;

	private static ModelPart lowerRight, lowerLeft;

	private SpidermonkeyBody() {
	}

	private static final class State {
		final SpringTail tail = new SpringTail(LEN);
		/** The lower arms' eased rotations (x, y, z right, then left) and when they were last eased (age, ticks). */
		final float[] arms = new float[6];
		float armsAge = Float.NaN;
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ModelPart root = AlienParts.bake(64, 64, r -> {
			// the skin's own (slim-laid) arm UVs and sleeve layer; 12 rows tall on the sheet so the hands stay, squeezed to ~9.5 px
			// long, ~2.8 px wide and 3 px deep: thinner than the main pair, never bulkier
			r.addOrReplaceChild("lower_right", CubeListBuilder.create().texOffs(40, 16).addBox(-2, -2, -2, 3, 12, 4, new CubeDeformation(-0.1f, -1.25f, -0.5f))
					.texOffs(40, 32).addBox(-2, -2, -2, 3, 12, 4, new CubeDeformation(0.1f, -1.0f, -0.3f)), PartPose.ZERO);
			r.addOrReplaceChild("lower_left", CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, 3, 12, 4, new CubeDeformation(-0.1f, -1.25f, -0.5f))
					.texOffs(48, 48).addBox(-1, -2, -2, 3, 12, 4, new CubeDeformation(0.1f, -1.0f, -0.3f)), PartPose.ZERO);
		});
		lowerRight = root.getChild("lower_right");
		lowerLeft = root.getChild("lower_left");
		ClientTickEvents.END_CLIENT_TICK.register(SpidermonkeyBody::prune);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> STATES.clear());
	}

	private static boolean shown(Player p) {
		return Aliens.SPIDERMONKEY.id().equals(AlienClients.shown(p));
	}

	/** Drops the state of anyone no longer in view as Spidermonkey (turned back, gone, another world). */
	private static void prune(Minecraft mc) {
		if (STATES.isEmpty()) {
			return;
		}
		if (mc.level == null) {
			STATES.clear();
			return;
		}
		STATES.keySet().removeIf(id -> {
			Player p = mc.level.getPlayerByUUID(id);
			return p == null || !shown(p);
		});
	}

	/** The tail root in model pixels for his stance (the body bone leans forward 0.5 rad and drops 3.2 px when he crouches). */
	private static Vec3 rootModel(Player p) {
		return SpringTail.rootModel(p, ROOT_Y, ROOT_Z);
	}

	/**
	 * v0.17.2 (user: "make spidermonkeys webs shoot out from his tail, thats how it works in the show"): where his rendered tail tip
	 * is in the world this frame -- the last link of the simulated chain, interpolated like the render; before the tail has ticked,
	 * the idle curl's tip (the server's {@link SpidermonkeyKit#tailTip} uses the same rest shape). Every web line starts here.
	 */
	public static Vec3 tailTip(Player p, float partial) {
		State st = STATES.get(p.getUUID());
		if (st != null && st.tail.init) {
			Vec3 tip = st.tail.last[N].lerp(st.tail.pos[N], partial);
			// a stale chain (he teleported): fall back to the rest shape
			if (tip.distanceToSqr(p.getPosition(partial)) < 9.0) {
				return tip;
			}
		}
		return SHAPE.restTip(p, partial, rootModel(p), REST_IDLE);
	}

	// ---------------------------------------------------------------- the tail's tick

	/** One client tick of {@code p}'s tail (from the kit's tick). */
	public static void tick(AbstractClientPlayer p) {
		State st = STATES.computeIfAbsent(p.getUUID(), id -> new State());
		float yaw = p.yBodyRot;
		Vec3 base = SpringTail.base(p, 1f);
		Vec3 rm = rootModel(p);
		OmnitrixState os = Omnitrix.state(p);
		float walk = SpringTail.walk(p);
		float phase = SpringTail.phase(p);
		// web swing / zip / swing kick / falling: loose, streaming behind him
		boolean loose = (os.kitFlags & (SpidermonkeyKit.F_SWING | SpidermonkeyKit.F_ZIP | SpidermonkeyKit.F_AIR | SpidermonkeyKit.F_KICK)) != 0
				|| p.getDeltaMovement().y < -0.6 || p.isFallFlying();
		// the tail whip: the body spins once (SpidermonkeyClient's WHIP clip), the tail sweeps round with it
		float spin = 0f;
		if (os.animId == SpidermonkeyKit.ANIM_WHIP) {
			float t = p.level().getGameTime() - os.animStart;
			spin = t < 2 ? t / 2f * 1.6f : t < 5 ? 1.6f + (t - 2) / 3f * 3.1f : t < 8 ? 4.7f + (t - 5) / 3f * 1.58f : 0f;
		}
		float age = p.tickCount;
		// v0.17.2 (user: "make spidermonkeys webs shoot out from his tail"): on a swing / zip line the tail reaches up his back and
		// toward the hook (the line runs on from its tip), instead of streaming behind him through the line
		Vec3 hook = SpidermonkeyKit.lineMode(p) != 0 ? SpidermonkeyKit.hook(p) : null;
		if (hook != null) {
			loose = false;
		}
		final float fspin = spin;
		// v0.17.2: on a line the tail rides along with him (a 44 b/s zip would otherwise leave it streaming behind)
		st.tail.tick(p, yaw, base, rm, REST_IDLE, hook != null, loose, hook != null ? 0.5f : loose ? 0.07f : 0.32f, (i, parentModel) -> {
			Vec3 dir = SpringTail.restDir(REST_IDLE, REST_WALK, i, N, walk, phase, age);
			if (fspin != 0f) {
				dir = dir.yRot(-fspin);
			}
			if (hook != null) {
				// up his back first (model y is down), the last links turned toward the hook
				float f = (i - 1f) / (N - 1f);
				Vec3 h = SpringTail.toModel(p, base, yaw, hook).subtract(parentModel);
				h = h.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : h.normalize();
				dir = h.add(0, -2.2 * (1f - f), 0.35 * (1f - f)).normalize();
			}
			return dir;
		}, (i, m) -> {
			// never inside his own back or legs (the whip spins him -- the body bone turns with it, so skip it then)
			Vec3 kept = fspin == 0f ? SpringTail.keepBehind(m, BEHIND) : null;
			if (kept == null && hook != null && m.z < 4.4 && m.y > -9 && m.y <= -4 && Math.abs(m.x) < 4.6) {
				// v0.17.2: reaching up on a line -- behind his head, not through it
				kept = new Vec3(m.x, m.y, 4.4);
			}
			return kept;
		});
	}

	// ---------------------------------------------------------------- drawing

	/** The lower arms and the tail, in the model's space (from {@code renderExtras}). */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age, ResourceLocation skin) {
		register();
		State st = STATES.get(p.getUUID());
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
		lowerArms(pose, vc, light, model, st, age);
		tail(pose, vc, light, p, model, partial, st);
	}

	private static float ease(float from, float to, float a) {
		return from + (to - from) * a;
	}

	private static void lowerArms(PoseStack pose, VertexConsumer vc, int light, PlayerModel<AbstractClientPlayer> model, State st, float age) {
		ModelPart ra = model.rightArm, la = model.leftArm, body = model.body;
		// the main arms' rotations in the body bone's frame (the lower pair hangs off the body), a little further back and tucked in
		float rx = (ra.xRot - body.xRot) * 0.92f + 0.3f, ry = (ra.yRot - body.yRot) * 0.8f, rz = ra.zRot * 0.7f + 0.03f;
		float lx = (la.xRot - body.xRot) * 0.92f + 0.3f, ly = (la.yRot - body.yRot) * 0.8f, lz = la.zRot * 0.7f - 0.03f;
		float[] a;
		if (st == null) {
			a = new float[] { rx, ry, rz, lx, ly, lz };
		} else {
			a = st.arms;
			float dt = Float.isNaN(st.armsAge) ? 100f : Mth.clamp(age - st.armsAge, 0f, 100f);
			st.armsAge = age;
			// a beat behind the main pair (~1 tick), so the four move as a set without moving as one
			float e = 1f - (float) Math.exp(-dt * 0.9f);
			float[] to = { rx, ry, rz, lx, ly, lz };
			for (int i = 0; i < 6; i++) {
				a[i] = ease(a[i], to[i], e);
			}
		}
		lowerRight.x = -5.0f;
		lowerRight.y = 4.6f;
		lowerRight.z = 0.6f;
		lowerRight.xRot = a[0];
		lowerRight.yRot = a[1];
		lowerRight.zRot = Math.max(a[2], 0.0f); // never into his side
		lowerLeft.x = 5.0f;
		lowerLeft.y = 4.6f;
		lowerLeft.z = 0.6f;
		lowerLeft.xRot = a[3];
		lowerLeft.yRot = a[4];
		lowerLeft.zRot = Math.min(a[5], 0.0f);
		AlienParts.on(pose, body, () -> {
			lowerRight.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			lowerLeft.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		});
	}

	private static void tail(PoseStack pose, VertexConsumer vc, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			State st) {
		ModelPart body = model.body;
		Matrix4f bone = new Matrix4f().translate(body.x / 16f, body.y / 16f, body.z / 16f).rotateZYX(body.zRot, body.yRot, body.xRot);
		Vector3f root = bone.transformPosition(new Vector3f(0, ROOT_Y / 16f, ROOT_Z / 16f));
		Vector3f[] pts = new Vector3f[N + 1];
		// world -> this render's model space; not in the level (the inventory screen) or not ticked yet: the idle curl
		if (st == null || !st.tail.modelPoints(pose, root, partial, pts, 0.5f, false)) {
			SHAPE.restPoints(root, REST_IDLE, pts);
		}
		// the skin's own blue (the main arm's outer side), the last links the darker shade of its front edge
		float u0 = 40.5f / 64f, u1 = 42.5f / 64f, v0 = 27.2f / 64f, v1 = 28.8f / 64f;
		float t0 = 46.2f / 64f, t1 = 46.8f / 64f;
		for (int i = 0; i < N; i++) {
			Vector3f a = pts[i], b = pts[i + 1];
			Vector3f d = new Vector3f(b).sub(a);
			float len = d.length();
			if (len < 1e-5f) {
				continue;
			}
			float w = WID[i] / 2f;
			float lenPx = len * 16f;
			pose.pushPose();
			pose.translate(a.x, a.y, a.z);
			pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, d.x / len, d.y / len, d.z / len));
			boolean tip = i >= N - 2;
			// a little longer than the link so the joints close over the bends
			AlienParts.box(pose, vc, -w, -w * 0.6f, -w, w, lenPx + w * 0.6f, w, tip ? t0 : u0, v0, tip ? t1 : u1, v1, 0xFFFFFFFF, light);
			pose.popPose();
		}
	}
}
