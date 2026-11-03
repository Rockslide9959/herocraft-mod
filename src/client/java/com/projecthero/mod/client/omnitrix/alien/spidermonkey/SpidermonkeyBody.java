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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 * The per-player state is dropped when he turns back, leaves view, or the client disconnects.
 */
public final class SpidermonkeyBody {
	private static final int N = 7;
	/** Link lengths and thicknesses in model pixels, base to tip. */
	private static final float[] LEN = { 3.3f, 3.2f, 3.0f, 2.8f, 2.5f, 2.2f, 1.9f };
	private static final float[] WID = { 1.4f, 1.3f, 1.2f, 1.1f, 1.0f, 0.9f, 0.8f };
	/** Rest directions (model space: x = his left... y down, z behind him), idle: low out behind, up, hooked over at the tip. */
	private static final float[][] REST_IDLE = { { 0, 0.75f, 1 }, { 0, 0.2f, 1 }, { 0, -0.5f, 1 }, { 0, -1, 0.45f }, { 0, -1, -0.1f }, { 0, -0.6f, -0.8f },
			{ 0, 0.3f, -1 } };
	/** ...and walking: trailing out further, the hook opened up. */
	private static final float[][] REST_WALK = { { 0, 0.6f, 1 }, { 0, 0.15f, 1 }, { 0, -0.15f, 1 }, { 0, -0.45f, 1 }, { 0, -0.85f, 0.6f }, { 0, -1, 0 },
			{ 0, -0.5f, -0.8f } };
	/** The tail's root on the body bone (pixels): the base of his back. */
	private static final float ROOT_Y = 11.0f, ROOT_Z = 2.0f;
	/** No link comes nearer his front than this (pixels, model z): just behind the back / the backs of the legs. */
	private static final float BEHIND = 2.7f;

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static boolean registered;

	private static ModelPart lowerRight, lowerLeft;

	private SpidermonkeyBody() {
	}

	private static final class State {
		final Vec3[] pos = new Vec3[N + 1];
		final Vec3[] prev = new Vec3[N + 1];
		final Vec3[] last = new Vec3[N + 1];
		boolean init;
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

	// ---------------------------------------------------------------- model space <-> world space

	/** The render scale of his body: the scale attribute times the player renderer's own 0.9375. */
	private static float scale(Player p) {
		return p.getScale() * 0.9375f;
	}

	/** Model pixels -> world, as LivingEntityRenderer places the model (yaw = body yaw, base = his feet). */
	private static Vec3 toWorld(Player p, Vec3 base, float yaw, double mx, double my, double mz) {
		double s = scale(p);
		double x = -mx / 16.0 * s, y = -(my / 16.0 - 1.501) * s, z = mz / 16.0 * s;
		double a = Math.toRadians(180.0 - yaw), c = Math.cos(a), sn = Math.sin(a);
		return base.add(x * c + z * sn, y, -x * sn + z * c);
	}

	/** World -> model pixels (the inverse of {@link #toWorld}). */
	private static Vec3 toModel(Player p, Vec3 base, float yaw, Vec3 w) {
		double s = scale(p);
		Vec3 d = w.subtract(base);
		double a = Math.toRadians(180.0 - yaw), c = Math.cos(a), sn = Math.sin(a);
		double x = d.x * c - d.z * sn, z = d.x * sn + d.z * c;
		return new Vec3(-x / s * 16.0, (-d.y / s + 1.501) * 16.0, z / s * 16.0);
	}

	private static Vec3 base(Player p, float partial) {
		Vec3 b = partial >= 1f ? p.position() : p.getPosition(partial);
		return p.isCrouching() ? b.add(0, p.getScale() * -2.0 / 16.0, 0) : b;
	}

	/** The tail root in model pixels for his stance (the body bone leans forward 0.5 rad and drops 3.2 px when he crouches). */
	private static Vec3 rootModel(Player p) {
		if (!p.isCrouching()) {
			return new Vec3(0, ROOT_Y, ROOT_Z);
		}
		float c = Mth.cos(0.5f), s = Mth.sin(0.5f);
		return new Vec3(0, 3.2f + ROOT_Y * c - ROOT_Z * s, ROOT_Y * s + ROOT_Z * c);
	}

	/**
	 * v0.17.2 (user: "make spidermonkeys webs shoot out from his tail, thats how it works in the show"): where his rendered tail tip
	 * is in the world this frame -- the last link of the simulated chain, interpolated like the render; before the tail has ticked,
	 * the idle curl's tip (the server's {@link SpidermonkeyKit#tailTip} uses the same rest shape). Every web line starts here.
	 */
	public static Vec3 tailTip(Player p, float partial) {
		State st = STATES.get(p.getUUID());
		if (st != null && st.init) {
			Vec3 tip = st.last[N].lerp(st.pos[N], partial);
			// a stale chain (he teleported): fall back to the rest shape
			if (tip.distanceToSqr(p.getPosition(partial)) < 9.0) {
				return tip;
			}
		}
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 base = base(p, partial);
		Vec3 at = rootModel(p);
		for (int i = 1; i <= N; i++) {
			float[] d = REST_IDLE[i - 1];
			at = at.add(new Vec3(d[0], d[1], d[2]).normalize().scale(LEN[i - 1]));
		}
		return toWorld(p, base, yaw, at.x, at.y, at.z);
	}

	// ---------------------------------------------------------------- the tail's tick

	/** One client tick of {@code p}'s tail (from the kit's tick). */
	public static void tick(AbstractClientPlayer p) {
		State st = STATES.computeIfAbsent(p.getUUID(), id -> new State());
		float yaw = p.yBodyRot;
		Vec3 base = base(p, 1f);
		Vec3 rm = rootModel(p);
		Vec3 root = toWorld(p, base, yaw, rm.x, rm.y, rm.z);
		double s = scale(p) / 16.0;
		OmnitrixState os = Omnitrix.state(p);
		float walk = Mth.clamp(p.walkAnimation.speed() * 1.6f, 0f, 1f);
		float phase = p.walkAnimation.position() * 0.6662f;
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
		if (!st.init || st.pos[0].distanceToSqr(root) > 9.0) {
			// (re)start in the rest shape
			Vec3 at = rm;
			st.pos[0] = root;
			for (int i = 1; i <= N; i++) {
				float[] d = REST_IDLE[i - 1];
				Vec3 dir = new Vec3(d[0], d[1], d[2]).normalize().scale(LEN[i - 1]);
				at = at.add(dir);
				st.pos[i] = toWorld(p, base, yaw, at.x, at.y, at.z);
			}
			for (int i = 0; i <= N; i++) {
				st.prev[i] = st.pos[i];
				st.last[i] = st.pos[i];
			}
			st.init = true;
		}
		for (int i = 0; i <= N; i++) {
			st.last[i] = st.pos[i];
		}
		if (hook != null) {
			// v0.17.2: on a line the tail rides along with him (a 44 b/s zip would otherwise leave it streaming behind)
			Vec3 shift = root.subtract(st.pos[0]);
			for (int i = 1; i <= N; i++) {
				st.pos[i] = st.pos[i].add(shift);
				st.prev[i] = st.prev[i].add(shift);
			}
		}
		st.pos[0] = root;
		st.prev[0] = root;
		float k = loose ? 0.07f : 0.32f;
		double grav = (loose ? 0.012 : 0.004) * scale(p);
		for (int i = 1; i <= N; i++) {
			Vec3 v = st.pos[i].subtract(st.prev[i]).scale(loose ? 0.9 : 0.72);
			st.prev[i] = st.pos[i];
			st.pos[i] = st.pos[i].add(v).add(0, -grav, 0);
		}
		Level level = p.level();
		for (int iter = 0; iter < 3; iter++) {
			Vec3 parentModel = toModel(p, base, yaw, st.pos[0]);
			for (int i = 1; i <= N; i++) {
				if (iter == 0) {
					// spring toward the rest shape, built on the parent link where it is now
					float[] a = REST_IDLE[i - 1], b = REST_WALK[i - 1];
					float f = (i - 1f) / (N - 1f);
					double sway = Mth.sin(phase - i * 0.55f) * walk * 0.55f * (0.3f + f) + Mth.sin(age * 0.07f + i * 0.6f) * 0.12f * (1f - walk) * f;
					Vec3 dir = new Vec3(Mth.lerp(walk, a[0], b[0]) + sway, Mth.lerp(walk, a[1], b[1]), Mth.lerp(walk, a[2], b[2])).normalize();
					if (spin != 0f) {
						dir = dir.yRot(-spin);
					}
					float kk = k;
					if (hook != null) {
						// up his back first (model y is down), the last links turned toward the hook
						Vec3 h = toModel(p, base, yaw, hook).subtract(parentModel);
						h = h.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : h.normalize();
						dir = h.add(0, -2.2 * (1f - f), 0.35 * (1f - f)).normalize();
						kk = 0.5f;
					}
					Vec3 targetModel = parentModel.add(dir.scale(LEN[i - 1]));
					Vec3 target = toWorld(p, base, yaw, targetModel.x, targetModel.y, targetModel.z);
					st.pos[i] = st.pos[i].add(target.subtract(st.pos[i]).scale(kk));
				}
				// keep the link's length
				Vec3 d = st.pos[i].subtract(st.pos[i - 1]);
				double len = d.length();
				double want = LEN[i - 1] * s;
				st.pos[i] = len < 1e-6 ? st.pos[i - 1].add(0, -want, 0) : st.pos[i - 1].add(d.scale(want / len));
				// never inside his own back or legs (the whip spins him -- the body bone turns with it, so skip it then)
				Vec3 m = toModel(p, base, yaw, st.pos[i]);
				if (spin == 0f && m.z < BEHIND && m.y > -4 && m.y < 25 && Math.abs(m.x) < 6.5) {
					m = new Vec3(m.x, m.y, BEHIND);
					st.pos[i] = toWorld(p, base, yaw, m.x, m.y, m.z);
				} else if (hook != null && m.z < 4.4 && m.y > -9 && m.y <= -4 && Math.abs(m.x) < 4.6) {
					// v0.17.2: reaching up on a line -- behind his head, not through it
					m = new Vec3(m.x, m.y, 4.4);
					st.pos[i] = toWorld(p, base, yaw, m.x, m.y, m.z);
				}
				// never through a block
				st.pos[i] = outOfBlocks(level, st.pos[i], st.pos[i - 1]);
				parentModel = toModel(p, base, yaw, st.pos[i]);
			}
		}
	}

	/** {@code pt} pushed out of any block collision box it is in, along the shortest way out. */
	private static Vec3 outOfBlocks(Level level, Vec3 pt, Vec3 parent) {
		for (int pass = 0; pass < 2; pass++) {
			BlockPos bp = BlockPos.containing(pt);
			BlockState bs = level.getBlockState(bp);
			if (bs.isAir()) {
				return pt;
			}
			VoxelShape shape = bs.getCollisionShape(level, bp);
			if (shape.isEmpty()) {
				return pt;
			}
			boolean moved = false;
			for (AABB box : shape.toAabbs()) {
				AABB b = box.move(bp).inflate(0.03);
				if (!b.contains(pt)) {
					continue;
				}
				double[] out = { b.minX - pt.x, b.maxX - pt.x, b.minY - pt.y, b.maxY - pt.y, b.minZ - pt.z, b.maxZ - pt.z };
				int best = 0;
				for (int j = 1; j < 6; j++) {
					if (Math.abs(out[j]) < Math.abs(out[best])) {
						best = j;
					}
				}
				pt = switch (best / 2) {
					case 0 -> new Vec3(pt.x + out[best], pt.y, pt.z);
					case 1 -> new Vec3(pt.x, pt.y + out[best], pt.z);
					default -> new Vec3(pt.x, pt.y, pt.z + out[best]);
				};
				moved = true;
				break;
			}
			if (!moved) {
				return pt;
			}
		}
		return pt;
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
		pts[0] = root;
		boolean sim = false;
		if (st != null && st.init) {
			// world -> this render's model space: the inverse of the pose (camera-relative world, no camera rotation, in the level)
			Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
			Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
			Vec3 r0 = st.last[0].lerp(st.pos[0], partial).subtract(cam);
			Vector3f check = inv.transformPosition(new Vector3f((float) r0.x, (float) r0.y, (float) r0.z));
			if (check.distance(root) < 0.5f) {
				sim = true;
				for (int i = 1; i <= N; i++) {
					Vec3 w = st.last[i].lerp(st.pos[i], partial).subtract(cam);
					pts[i] = inv.transformPosition(new Vector3f((float) w.x, (float) w.y, (float) w.z));
				}
			}
		}
		if (!sim) {
			// not in the level (the inventory screen) or not ticked yet: the idle curl
			Vector3f at = new Vector3f(root);
			for (int i = 1; i <= N; i++) {
				float[] d = REST_IDLE[i - 1];
				at = new Vector3f(at).add(new Vector3f(d[0], d[1], d[2]).normalize(LEN[i - 1] / 16f));
				pts[i] = at;
			}
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
