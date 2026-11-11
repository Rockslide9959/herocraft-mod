package com.projecthero.mod.client.undead;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: a launched piece of Undead, in its owner's own skin: the twisted arm spinning fist-first like a drill, the head
 * tumbling (card and all), or a fingertip. Remembers where it last drew the piece so it can burst from exactly there.
 */
public class UndeadPartRenderer extends EntityRenderer<UndeadPartEntity> {
	/** Entity id -> the piece's own space (blocks) in the world, last frame; plus skin and model for the burst. */
	record Last(Matrix4f matrix, ResourceLocation tex, boolean slim, int part) {
	}

	static final Map<Integer, Last> LAST = new ConcurrentHashMap<>();

	private final PlayerModel<Player> wide;
	private final PlayerModel<Player> slimModel;

	public UndeadPartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.15f;
		this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	static Player owner(UndeadPartEntity e) {
		Entity o = e.getOwner();
		return o instanceof Player p ? p : null;
	}

	@Override
	public void render(UndeadPartEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Player owner = owner(e);
		ResourceLocation tex = owner != null ? UndeadRender.skin(owner) : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		boolean slim = owner != null && UndeadRender.slim(owner);
		float age = e.tickCount + partial;
		Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
		if (e.isEjectPiece()) {
			UndeadEjectRender.piece(e, partial, pose, buffers, light, tex, slim); // v0.17.3: an end / the torso an ejection left
			super.render(e, yaw, partial, pose, buffers, light);
			return;
		}
		if (e.isLeftBehind()) {
			leftBehind(e, partial, pose, buffers, light, tex, slim, cam);
			super.render(e, yaw, partial, pose, buffers, light);
			return;
		}
		pose.pushPose();
		float ry = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		float rx = Mth.lerp(partial, e.xRotO, e.getXRot());
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		boolean along = e.style() == UndeadPartEntity.STYLE_PUNCH || e.isSword() || e.isSplinter() || e.isHand() || e.isRib();
		if (along) {
			// v0.17.3: aligned to where it is going (yaw and pitch from the motion, eased frame to frame); once it lies on the
			// ground it settles flat
			float[] yp = travel(e, ry, rx);
			ry = yp[0];
			rx = yp[1];
		}
		pose.mulPose(Axis.YP.rotationDegrees(ry));
		pose.mulPose(Axis.XP.rotationDegrees(-rx));
		if (e.isFinger()) {
			finger(e, pose, buffers, light, tex, slim, age);
			pose.popPose();
			return;
		}
		if (e.isSplinter() || e.isSword() || e.isRib()) {
			if (e.isRib()) {
				UndeadRibShotFx.spike(pose, buffers, light); // v0.17.3 tap V: a rib fired out of his chest
			} else if (e.isSplinter()) {
				UndeadBoneSwordRender.splinter(pose, buffers, light);
			} else {
				// the thrown Bone Sword, point first: sword space has the blade toward -z and +y down
				pose.mulPose(Axis.YP.rotationDegrees(180f));
				pose.mulPose(Axis.ZP.rotationDegrees(180f));
				pose.translate(0f, 0f, 10f / 16f);
				UndeadBoneSwordRender.mesh(pose, buffers, light, age, false);
			}
			LAST.put(e.getId(), new Last(new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose()), tex, slim, -1));
			pose.popPose();
			return;
		}
		if (e.isHand()) {
			hand(e, pose, buffers, light, tex, slim, age, cam);
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
			return;
		}
		PlayerModel<Player> model = slim ? this.slimModel : this.wide;
		int part = e.bodyPart();
		if (e.style() == UndeadPartEntity.STYLE_DRILL || e.style() == UndeadPartEntity.STYLE_PUNCH) {
			// fist first along the flight (the drill spinning about it; the thrown arm just a slight roll), the shoulder trailing
			if (e.style() == UndeadPartEntity.STYLE_PUNCH) {
				pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.35f) * 8f));
			} else if (!e.stuck()) {
				pose.mulPose(Axis.ZP.rotationDegrees(age * 47f));
			}
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			float[] b = UndeadTexels.box(part, slim);
			pose.translate(-(b[0] + b[3] * 0.5f) / 16f, -(b[1] + b[4] * 0.5f) / 16f, 0f);
		} else {
			// the head: tumbling while it flies, still once it lies
			if (e.getDeltaMovement().lengthSqr() > 0.01) {
				pose.mulPose(Axis.XP.rotationDegrees(age * 24f));
			}
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			pose.translate(0f, 4f / 16f, 0f);
		}
		ModelPart base = UndeadRender.part(model, part);
		ModelPart over = UndeadRender.overlay(model, part);
		base.resetPose();
		over.resetPose();
		base.x = base.y = base.z = 0f;
		over.x = over.y = over.z = 0f;
		base.visible = true;
		over.visible = true;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
		base.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		over.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		if (part == UndeadState.HEAD) {
			UndeadRender.card(pose, buffers, light, 0f);
		}
		LAST.put(e.getId(), new Last(new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose()), tex, slim, part));
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	/** Entity id -> {yaw, pitch, last frame nanos}: the eased travel direction of a thrown arm / sword / shard. */
	private static final Map<Integer, float[]> TRAVEL = new ConcurrentHashMap<>();

	/**
	 * The yaw / pitch (entity convention: yaw = atan2(x, z), pitch up positive) of {@code e}'s motion, eased toward it frame by
	 * frame; still or lying on the ground it keeps its heading and settles level. Falls back to the synced rotation.
	 */
	private static float[] travel(UndeadPartEntity e, float ry, float rx) {
		long t = System.nanoTime();
		float[] st = TRAVEL.computeIfAbsent(e.getId(), k -> new float[] { ry, rx, 0f });
		float dt = st[2] == 0f ? 1f : Math.min(1f, (t / 1.0e6f - st[2]) / 50f); // in ticks
		st[2] = t / 1.0e6f;
		Vec3 v = e.getDeltaMovement();
		float ty = st[0], tx = st[1];
		if (v.lengthSqr() > 0.004 && !e.stuck()) {
			double flat = Math.sqrt(v.x * v.x + v.z * v.z);
			ty = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			tx = (float) (Mth.atan2(v.y, flat) * Mth.RAD_TO_DEG);
			if (e.tickCount <= 1) {
				st[0] = ty;
				st[1] = tx;
			}
		} else if (!e.stuck()) {
			tx = 0f; // lying there: flat on the ground
		}
		float k = 1f - (float) Math.pow(0.45, dt);
		st[0] = st[0] + Mth.wrapDegrees(ty - st[0]) * k;
		st[1] = st[1] + (tx - st[1]) * k;
		if (TRAVEL.size() > 256) {
			TRAVEL.clear();
		}
		return new float[] { st[0], st[1] };
	}

	/**
	 * v0.17.3 ({@link UndeadPartEntity#leaveBehind}): a piece left behind where it came off -- drawn exactly where that slice sat
	 * on his standing body (the entity is at his feet). For {@link UndeadPartEntity#LEFT_STAND} it stands perfectly still; over
	 * {@link UndeadPartEntity#LEFT_FALL} it tips over about its lowest point while dropping to the ground (a leg splays back and
	 * out to its side, an arm falls out to its side, the body and head fall back), with a small bounce as it lands; then it erodes
	 * away pixel by pixel over {@link UndeadPartEntity#LEFT_DISSOLVE} ({@link UndeadRegrowStepped#remnant}).
	 */
	private void leftBehind(UndeadPartEntity e, float partial, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim,
			Vec3 cam) {
		int part = e.bodyPart();
		if (part < 0) {
			return;
		}
		float age = e.leftAge(partial);
		float dissolve = UndeadPartEntity.dissolveProgress(age);
		if (dissolve >= 1f) {
			return;
		}
		float from = e.fromRow(), to = e.toRow();
		boolean leg = part == UndeadState.RIGHT_LEG || part == UndeadState.LEFT_LEG;
		boolean arm = part == UndeadState.RIGHT_ARM || part == UndeadState.LEFT_ARM;
		boolean legless = e.style() == UndeadPartEntity.STYLE_LEFT_BODY_LEGLESS;
		float t = UndeadPartEntity.fallProgress(age);
		float fall = t * t; // speeds up like a real topple
		float land = UndeadPartEntity.LEFT_STAND + UndeadPartEntity.LEFT_FALL;
		float settle = t >= 1f ? Mth.sin(Mth.clamp((age - land) / 4f, 0f, 1f) * Mth.PI) * 4f : 0f; // a small bounce on landing
		Matrix4f stand = UndeadRender.standing(Vec3.ZERO, 180f, slim, part);
		// the slice's lowest point (its far end; the neck for the head): what it tips about, and how high it has to drop
		float[] b = UndeadTexels.box(part, slim);
		float lowY = part == UndeadState.HEAD ? b[1] + b[4] - from : b[1] + to;
		org.joml.Vector3f low = stand.transformPosition(new org.joml.Vector3f((b[0] + b[3] * 0.5f) / 16f, lowY / 16f, (b[2] + b[5] * 0.5f) / 16f));
		float sit = legless ? -0.70f : 0f; // no legs under it: the body sits on the ground
		float drop = Math.max(0f, low.y + sit) * fall;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot()));
		pose.translate(0f, sit - drop, 0f);
		pose.translate(low.x, low.y, low.z);
		// in this frame +z is behind him and +x is his right
		if (leg) {
			pose.mulPose(Axis.XP.rotationDegrees(70f * fall - settle));
			pose.mulPose(Axis.ZP.rotationDegrees((part == UndeadState.RIGHT_LEG ? -1f : 1f) * 24f * fall));
		} else if (arm) {
			float side = part == UndeadState.RIGHT_ARM ? -1f : 1f;
			pose.mulPose(Axis.ZP.rotationDegrees(side * (86f * fall - settle)));
			pose.mulPose(Axis.XP.rotationDegrees(12f * fall));
		} else {
			pose.mulPose(Axis.XP.rotationDegrees(84f * fall - settle));
		}
		pose.translate(-low.x, -low.y, -low.z);
		pose.mulPose(stand);
		UndeadRegrowStepped.remnant(pose, buffers, light, tex, slim, part, from, to, dissolve);
		LAST.put(e.getId(), new Last(new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose()), tex, slim, part));
		pose.popPose();
	}

	/** The rows of the arm that are the hand (model px in the arm's own space: the arm runs -2..10). */
	static final float HAND_TOP = 6f, HAND_BOTTOM = 10f;

	/**
	 * v0.17.3 Shift+R, Parts Bullet: Hand: the hand's own skin texels (the last four rows of the arm, sleeve layer too), fingers
	 * first along the flight with a slight roll, a raw flesh face at the wrist where it tore off. The pose is already turned to
	 * the travel direction (+z ahead).
	 */
	private void hand(UndeadPartEntity e, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float age, Vec3 cam) {
		int seg = e.bodyPart();
		int arm = seg == UndeadState.LEFT_HAND ? UndeadState.LEFT_ARM : UndeadState.RIGHT_ARM;
		if (e.inFlight()) {
			pose.mulPose(Axis.ZP.rotationDegrees(age * 18f));
		}
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.scale(-1f, -1f, 1f);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		float[] b = UndeadTexels.box(arm, slim);
		pose.translate(-(b[0] + b[3] * 0.5f) / 16f, -(HAND_TOP + HAND_BOTTOM) * 0.5f / 16f, -(b[2] + b[5] * 0.5f) / 16f);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
		PoseStack.Pose last = pose.last();
		for (UndeadTexels.Texel t : UndeadTexels.of(arm, slim)) {
			if (t.y < HAND_TOP || t.y > HAND_BOTTOM + 1.5f) {
				continue;
			}
			float[][] p = UndeadRender.cornersPublic(t, 0f, 0f);
			float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
			Matrix4f m = last.pose();
			for (int i = 0; i < 4; i++) {
				float uu = (i == 1 || i == 2) ? u1 : u0;
				float vv = (i >= 2) ? v1 : v0;
				vc.addVertex(m, p[i][0] / 16f, Math.max(HAND_TOP - (t.overlay ? 0.25f : 0f), p[i][1]) / 16f, p[i][2] / 16f).setColor(255, 255, 255, 255)
						.setUv(uu, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, t.nx, t.ny, t.nz);
			}
		}
		// the torn wrist: a raw face across the top of the hand
		VertexConsumer flesh = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.FLESH));
		Matrix4f m = last.pose();
		float x0 = b[0] / 16f, x1 = (b[0] + b[3]) / 16f, z0 = b[2] / 16f, z1 = (b[2] + b[5]) / 16f, y = (HAND_TOP + 0.02f) / 16f;
		float[][] q = { { x0, z0, 0, 0 }, { x1, z0, 1, 0 }, { x1, z1, 1, 1 }, { x0, z1, 0, 1 } };
		for (float[] v : q) {
			flesh.addVertex(m, v[0], y, v[1]).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
					.setNormal(last, 0, -1, 0);
		}
		for (int i = q.length - 1; i >= 0; i--) {
			float[] v = q[i];
			flesh.addVertex(m, v[0], y, v[1]).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
					.setNormal(last, 0, 1, 0);
		}
		LAST.put(e.getId(), new Last(new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose()), tex, slim, -2 - arm));
	}

	/** A fingertip: a 1x1x2.5 px stub of the hand's skin (red-hot for the Crimson Bullet). */
	private void finger(UndeadPartEntity e, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float age) {
		int u = slim ? 48 : 49, v = 18;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		float s = e.style() == UndeadPartEntity.STYLE_CRIMSON ? 1.8f : 1f;
		pose.mulPose(Axis.ZP.rotationDegrees(age * 30f));
		org.joml.Vector3f[] c = new org.joml.Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new org.joml.Vector3f(((i & 1) == 0 ? -0.6f : 0.6f) * s / 16f, ((i & 2) == 0 ? -0.6f : 0.6f) * s / 16f, ((i & 4) == 0 ? -1.4f : 1.4f) * s / 16f);
		}
		UndeadRender.cubePublic(vc, pose.last(), c, (u + 0.1f) / 64f, (v + 0.1f) / 64f, (u + 0.9f) / 64f, (v + 0.9f) / 64f, light);
		if (e.style() != UndeadPartEntity.STYLE_FINGER) {
			// a red glow round the bullet
			VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
			float r = (e.style() == UndeadPartEntity.STYLE_CRIMSON ? 3.2f : 1.6f) / 16f;
			float len = r * 2.4f;
			Matrix4f m = pose.last().pose();
			for (int k = 0; k < 3; k++) {
				float a = k * Mth.PI / 3f;
				float cx = Mth.cos(a) * r, cy = Mth.sin(a) * r;
				glowQuad(glow, m, -cx, -cy, cx, cy, len, 0.95f, 0.1f, 0.1f, 0.55f);
			}
		}
	}

	private static void glowQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float len, float r, float g, float b, float a) {
		vc.addVertex(m, x0, y0, -len).setColor(r, g, b, 0f);
		vc.addVertex(m, x1, y1, -len).setColor(r, g, b, 0f);
		vc.addVertex(m, x1, y1, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x0, y0, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x0, y0, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, len * 0.3f).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, -len).setColor(r, g, b, 0f);
		vc.addVertex(m, x0, y0, -len).setColor(r, g, b, 0f);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(UndeadPartEntity e) {
		Player owner = owner(e);
		return owner instanceof AbstractClientPlayer acp ? acp.getSkin().texture() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
	}

	/** The piece is gone: a launched arm or head bursts into pixels where it was last drawn; a finger sprinkles a few. */
	static void removed(UndeadPartEntity e) {
		Last last = LAST.remove(e.getId());
		TRAVEL.remove(e.getId());
		if (e.isLeftBehind()) {
			return; // v0.17.3: it has already dissolved away (leaveBehind): nothing bursts
		}
		if (e.isSplinter() || e.isSword() || e.isRib()) {
			// bone bits and blood
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (Minecraft.getInstance().level != null) {
				UndeadSwordFx.shardHit(c, e.isSword());
			}
			return;
		}
		Player owner = owner(e);
		ResourceLocation tex = owner != null ? UndeadRender.skin(owner) : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		if (e.isHand()) {
			// v0.17.3: a spent hand crumbles into a handful of its own skin's pixels
			boolean left = e.bodyPart() == UndeadState.LEFT_HAND;
			PixelCubes.sprinkle(tex, left ? 37 : 45, left ? 62 : 30, e.position().add(0, e.getBbHeight() * 0.5, 0), 10, 0xFFFFFFFF);
			return;
		}
		if (e.isFinger()) {
			PixelCubes.sprinkle(tex, owner != null && UndeadRender.slim(owner) ? 48 : 49, 18, e.position(), e.style() == UndeadPartEntity.STYLE_CRIMSON ? 8 : 4,
					e.style() == UndeadPartEntity.STYLE_FINGER ? 0xFFFFFFFF : 0xFFFF7070);
			return;
		}
		if (last != null && Minecraft.getInstance().level != null) {
			PixelCubes.burst(last.tex(), last.slim(), last.part(), last.matrix(), Vec3.ZERO, 0xFFFFFFFF, 0.8f);
		}
	}
}
