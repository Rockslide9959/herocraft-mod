package com.projecthero.mod.client.omnitrix.alien.eyeguy;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.alien.eyeguy.EyeGuyKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Eye Guy's beams, drawn: each is a straight additive band (a wide soft glow in the beam's colour round a thin white-hot core)
 * from the eye that fired it to where it struck, flaring on and fading out over its carrier's short life, with a glowing spot at each
 * end. The plain beams leave his eyes; each Laser Volley beam leaves one of six eyes round his body (shoulders, chest, forearms); the
 * Chest Eye Beam leaves the great eye in his chest, and while it charges a ball of light swells there. Each beam has an opaque band of its
 * colour inside the glow, so it reads as green / ice blue / orange even against a bright sky (additive alone washes out to white). The
 * Chest Eye Beam is drawn here from his synced pose (its 36-block reach would put a carrier entity outside low entity render distances).
 */
public final class EyeGuyFx {
	private EyeGuyFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("eye_guy_beam", (e, partial, pose, buffers) -> beam(e, partial, pose, buffers, -1));
		for (int i = 0; i < EyeGuyKit.VOLLEY_EYES; i++) {
			int eye = i;
			OmnitrixRenderers.projectileLook("eye_guy_volley_" + i, (e, partial, pose, buffers) -> beam(e, partial, pose, buffers, eye));
		}
		WorldRenderEvents.AFTER_ENTITIES.register(EyeGuyFx::world);
	}

	private static boolean ownView(Player p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	/** {@code p}'s body frame: forward and right on the ground plane (body yaw). */
	private static Vec3[] frame(Player p, float partial) {
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		return new Vec3[] { new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)) };
	}

	/** Where a beam leaves him: his eyes (-1), body eye 0..5, or the chest eye (99). */
	public static Vec3 origin(Player p, float partial, int eye) {
		if (ownView(p)) {
			// in his own view, from just under the screen's centre (the eyes are behind the camera)
			Vec3 look = p.getViewVector(partial);
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
			double side = eye < 0 || eye == 99 ? 0 : (eye % 2 == 0 ? 0.35 : -0.35);
			double down = eye == 99 ? 0.45 : eye < 0 ? 0.12 : 0.3;
			return p.getEyePosition(partial).add(look.scale(0.9)).add(right.scale(side)).subtract(0, down, 0);
		}
		float s = p.getScale();
		Vec3 base = p.getPosition(partial);
		Vec3[] fr = frame(p, partial);
		Vec3 fwd = fr[0], right = fr[1];
		if (eye < 0) {
			return p.getEyePosition(partial).add(p.getViewVector(partial).scale(0.3 * s));
		}
		if (eye == 99) {
			return base.add(0, 1.1 * s, 0).add(fwd.scale(0.2 * s));
		}
		// the body eyes: left / right chest, the shoulders, the forearms (+x is his right)
		double[][] at = { { 0.14, 1.28, 0.17 }, { -0.14, 1.28, 0.17 }, { 0.36, 1.42, 0.05 }, { -0.36, 1.42, 0.05 }, { 0.38, 0.92, 0.1 }, { -0.38, 0.92, 0.1 } };
		double[] q = at[Math.max(0, Math.min(5, eye))];
		return base.add(right.scale(q[0] * s)).add(0, q[1] * s, 0).add(fwd.scale(q[2] * s));
	}

	private static void beam(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, int eye) {
		if (!(e.getOwner() instanceof Player owner)) {
			return;
		}
		Vec3 from = origin(owner, partial, eye);
		Vec3 to = e.getPosition(partial);
		float age = e.tickCount + partial;
		float life = eye == 99 ? 14f : 8f;
		// flares on in a tick, holds, and fades
		float k = age < 1f ? age : Mth.clamp(1f - (age - life * 0.4f) / (life * 0.6f), 0f, 1f);
		if (k <= 0f) {
			return;
		}
		float w = e.size() * (eye == 99 ? 1f + 0.15f * Mth.sin(age * 2.1f) : 1f) * (ownView(owner) ? 0.6f : 1f);
		int rgb = e.colour() & 0xFFFFFF;
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		Vec3 c = BrainstormFx.cam();
		List<Vec3> pts = List.of(from.subtract(c), from.lerp(to, 0.5).subtract(c), to.subtract(c));
		bands(pose, buffers, pts, w, rgb, k);
		pose.popPose();
		orb(pose, buffers, e, partial, to, w * 2.2f, rgb, k, age);
		if (!ownView(owner)) {
			orb(pose, buffers, e, partial, from, w * 1.3f, rgb, k, age);
		}
	}

	/** The beam's layers (camera-relative points): a wide soft glow, an opaque band of the colour, a white-hot core. */
	private static void bands(PoseStack pose, MultiBufferSource buffers, List<Vec3> pts, float w, int rgb, float k) {
		VertexConsumer vc = PyroDraw.additive(buffers);
		PyroDraw.ribbon(pose, vc, pts, s -> w * 3.0, s -> PyroDraw.dim(rgb, k * 0.3f));
		PyroDraw.ribbon(pose, vc, pts, s -> w * 1.8, s -> PyroDraw.dim(rgb, k * 0.35f));
		if (k > 0.3f) {
			// opaque, unlit and back-face culled (the beacon beam type: an entity type shades the band dark, a no-cull one z-fights its two faces)
			PyroDraw.ribbon(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.beaconBeam(com.projecthero.mod.client.mutation.MutationRender.WHITE, false)), pts,
					s -> w * 1.0 * k, s -> 0xFF000000 | rgb);
		}
		// the core a hair nearer the camera than the band (same depth would z-fight)
		PyroDraw.ribbon(pose, vc, pts.stream().map(q -> q.scale(0.99)).toList(), s -> w * 0.35, s -> PyroDraw.dim(0xFFFFFF, k));
	}

	/** A glowing ball at world point {@code at} (pose at the carrier). */
	private static void orb(PoseStack pose, MultiBufferSource buffers, AlienProjectileEntity e, float partial, Vec3 at, float r, int rgb, float k, float age) {
		Vec3 here = e.getPosition(partial);
		pose.pushPose();
		pose.translate(at.x - here.x, at.y - here.y, at.z - here.z);
		PyroDraw.sphere(pose, PyroDraw.additive(buffers), r, PyroDraw.dim(0xFFFFFF, k), PyroDraw.dim(rgb, k * 0.8f), 0.1f, age, PyroDraw.FULL);
		pose.popPose();
	}

	/** The chest eye's charge: a ball of light swelling in his chest, motes drawn in to it. */
	private static void world(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (var pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !EyeGuyKit.isEyeGuy(p) || p.isInvisible()) {
				continue;
			}
			megaBeam(context, p, partial);
			float k = EyeGuyKit.megaCharge(p, partial);
			if (k <= 0f) {
				continue;
			}
			PoseStack pose = context.matrixStack();
			Vec3 at = origin(p, partial, 99);
			Vec3 cam = BrainstormFx.cam();
			float age = p.tickCount + partial;
			float r = (ownView(p) ? 0.05f + 0.1f * k : 0.08f + 0.17f * k) * (1f + Mth.sin(age * 1.6f) * 0.08f);
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			PyroDraw.sphere(pose, PyroDraw.additive(context.consumers()), r, 0xFFFFFFF0, PyroDraw.dim(EyeGuyKit.MEGA, 0.9f), 0.12f, age, PyroDraw.FULL);
			pose.popPose();
			// rays converging on it
			java.util.Random rnd = new java.util.Random(p.getId() * 31L + p.level().getGameTime() / 2);
			for (int i = 0; i < 2 + (int) (k * 4); i++) {
				Vec3 out = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize().scale(ownView(p) ? 0.5 : 1.0 + rnd.nextDouble());
				List<Vec3> pts = List.of(at.add(out).subtract(cam), at.subtract(cam));
				PyroDraw.ribbon(pose, PyroDraw.additive(context.consumers()), pts, s -> 0.03 + 0.03 * s, s -> PyroDraw.dim(EyeGuyKit.MEGA, (float) (0.2 + 0.6 * s) * k));
			}
		}
	}

	/** The Chest Eye Beam (14 ticks from the ANIM_MEGA pose): from the chest eye along his look, out to the first block (36 blocks). */
	private static void megaBeam(WorldRenderContext context, AbstractClientPlayer p, float partial) {
		var st = com.projecthero.mod.omnitrix.Omnitrix.state(p);
		if (st.animId != EyeGuyKit.ANIM_MEGA) {
			return;
		}
		float age = p.level().getGameTime() + partial - st.animStart;
		float life = 14f;
		if (age < 0f || age > life) {
			return;
		}
		float k = age < 1f ? Math.max(age, 0.2f) : Mth.clamp(1f - (age - life * 0.4f) / (life * 0.6f), 0f, 1f);
		if (k <= 0f) {
			return;
		}
		Vec3 from = origin(p, partial, 99);
		Vec3 eye = p.getEyePosition(partial);
		Vec3 dir = p.getViewVector(partial);
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.scale(EyeGuyKit.MEGA_RANGE)),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		Vec3 to = hit.getLocation();
		float w = 0.55f * (1f + 0.15f * Mth.sin(age * 2.1f)) * (ownView(p) ? 0.6f : 1f);
		PoseStack pose = context.matrixStack();
		Vec3 cam = BrainstormFx.cam();
		List<Vec3> pts = new java.util.ArrayList<>();
		for (int i = 0; i <= 8; i++) {
			pts.add(from.lerp(to, i / 8.0).subtract(cam));
		}
		pose.pushPose();
		bands(pose, context.consumers(), pts, w, EyeGuyKit.MEGA, k);
		pose.popPose();
		for (Vec3 at : new Vec3[] { to, from }) {
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			PyroDraw.sphere(pose, PyroDraw.additive(context.consumers()), at == to ? w * 2.2f : w * 1.2f, PyroDraw.dim(0xFFFFFF, k),
					PyroDraw.dim(EyeGuyKit.MEGA, k * 0.8f), 0.1f, age, PyroDraw.FULL);
			pose.popPose();
		}
	}
}
