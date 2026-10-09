package com.projecthero.mod.client.punisher;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.client.punisher.GunAnim.Kind;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.item.PunisherItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.16, first person: a proper gun rig for the Punisher's firearms in place of vanilla's lone floating item. Both
 * arms are drawn and actually hold the gun -- the shooting hand on the grip, the support hand on the handguard (cupping
 * the shooting hand for the pistol) -- each arm aimed from its shoulder at the grip point it holds, so whatever the gun
 * does the hands stay on it. On top of that:
 * <ul>
 *   <li><b>aim down sights</b>: the gun comes up to the centre of the view with its sights on the line of fire (the
 *       sniper goes all the way to the scope, where {@code ScopeOverlay} takes over and the gun is hidden);</li>
 *   <li><b>walk / sprint</b>: a weighted sway while walking; sprinting tips the gun across the chest, muzzle low and
 *       bouncing with the stride;</li>
 *   <li><b>recoil</b>: a kick back and up on every shot;</li>
 *   <li><b>reload</b>: the gun cants over, the support hand strips the magazine, fetches a fresh one, seats it with a
 *       jolt and slaps it home (one shell per cycle into the shotgun's port);</li>
 *   <li><b>Tactical Roll</b>: the gun is tucked in and down out of the way;</li>
 *   <li><b>Adrenaline</b>: the support hand comes up with a syringe and drives it down into the thigh.</li>
 * </ul>
 * Right-handed only; a left-handed main arm keeps vanilla's drawing.
 */
public final class GunFirstPerson {
	/** Per gun, model px: shooting-hand grip, support-hand grip, magazine well / loading port, rear + front sight. */
	private record Rig(Vector3f grip, Vector3f fore, Vector3f well, Vector3f rear, Vector3f front, float adsDist, float adsScale) {
	}

	/** Where the rear sight sits at the hip (camera space) and how far the barrel is turned in toward the crosshair. */
	private static final Vector3f HIP = new Vector3f(0.19f, -0.1f, -0.56f);
	/** First-person arms are slimmed so they sit in proportion with the (scaled-down) guns. */
	private static final float ARM_SCALE = 0.6f;
	private static final float HIP_YAW = 14f;

	private static final Rig PISTOL = new Rig(v(8, 5.6f, 11.6f), v(7.4f, 4.6f, 11.4f), v(8, 3.2f, 11.4f), v(8, 11.4f, 12.4f),
			v(8, 11.4f, 4.75f), 0.62f, 0.56f);
	// v0.15.16: the user's mesh rifle (rear sight / front sight / pistol grip / handguard / magwell from the OBJ)
	private static final Rig RIFLE = new Rig(v(8, 5.9f, 10.3f), v(8, 7.15f, 3.2f), v(8, 6.6f, 7.65f), v(8, 9.63f, 10.48f),
			v(8, 9.41f, -1.79f), 0.72f, 1.0f);
	private static final Rig SHOTGUN = new Rig(v(8, 5.2f, 11.6f), v(8, 6.2f, 3.2f), v(8, 6.4f, 8.0f), v(8, 9.4f, 9.0f),
			v(8, 10.3f, -0.6f), 0.52f, 0.68f);
	private static final Rig SNIPER = new Rig(v(8, 5.2f, 12.6f), v(8, 6.6f, 3.0f), v(8, 5.0f, 9.6f), v(8, 9.15f, 13.0f),
			v(8, 9.15f, 7.5f), 0.62f, 0.95f);

	/**
	 * Where each arm comes from, relative to the point its hand holds (camera space): from below, so the forearm rises up
	 * under the gun to the grip / handguard instead of cutting across it -- the shooting arm a little back and out to the
	 * right (under the stock), the support arm a little out to the left.
	 */
	private static final Vector3f RIGHT_FROM = new Vector3f(0.3f, -1.0f, 0.42f);
	private static final Vector3f LEFT_FROM = new Vector3f(-0.24f, -1.0f, 0.12f);

	/** The scope has taken over once the sniper is this far up -- the gun itself is not drawn past it. */
	public static final float SCOPE_IN = 0.92f;

	private GunFirstPerson() {
	}

	private static Vector3f v(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	private static Rig rig(Kind k) {
		return switch (k) {
			case PISTOL -> PISTOL;
			case SHOTGUN -> SHOTGUN;
			case SNIPER -> SNIPER;
			default -> RIFLE;
		};
	}

	/** Whether this rig draws {@code player}'s hands this frame (main hand holding a Punisher gun, right-handed). */
	public static boolean active(AbstractClientPlayer player) {
		return GunAnim.kind(player) != null && player.getMainArm() == HumanoidArm.RIGHT;
	}

	/** Draws the whole rig in place of vanilla's main-hand item. {@code pose} is camera space (+X right, +Y up, -Z ahead). */
	public static void render(AbstractClientPlayer player, float partialTick, ItemStack stack, float equipProgress, PoseStack pose,
			MultiBufferSource buffers, int light) {
		Minecraft mc = Minecraft.getInstance();
		Kind kind = GunAnim.kind(stack);
		if (kind == null) {
			return;
		}
		Rig rig = rig(kind);
		float ads = GunAnim.aim(player, partialTick);
		float sprint = GunAnim.sprint(player, partialTick);
		float rec = GunAnim.recoil(player, partialTick);
		float reload = GunAnim.reload(player, partialTick);
		float roll = GunAnim.rolling(player, partialTick);
		float stab = GunAnim.stab(player, partialTick);
		float time = player.tickCount + partialTick;

		BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());

		// ---- the gun's pose in camera space
		// the stack arrives with vanilla's own hand-space flip in it: work out hand positions relative to it
		Matrix4f baseInv = new Matrix4f(pose.last().pose()).invert();
		pose.pushPose();
		pose.translate(0f, equipProgress * -0.6f, 0f);
		// walking sway (on top of vanilla's view bob), damped when aiming
		float walk = Mth.lerp(partialTick, player.walkDistO, player.walkDist);
		float speed = Math.min(1f, (float) player.getDeltaMovement().horizontalDistance() * 5f);
		float sway = (1f - ads * 0.85f) * speed;
		pose.translate(Mth.sin(walk * (float) Math.PI) * 0.012f * sway, -Math.abs(Mth.cos(walk * (float) Math.PI)) * 0.01f * sway, 0f);
		// idle breathing
		pose.translate(0f, Mth.sin(time * 0.07f) * 0.004f * (1f - ads * 0.7f), 0f);
		// sprint: across the chest, muzzle low, bouncing with the stride
		if (sprint > 0f) {
			float bounce = Mth.sin(walk * (float) Math.PI * 2f) * 0.02f;
			pose.translate(-0.02f * sprint, (-0.1f + bounce) * sprint, 0.06f * sprint);
			pose.mulPose(Axis.YP.rotationDegrees(42f * sprint));
			pose.mulPose(Axis.ZP.rotationDegrees(14f * sprint));
			pose.mulPose(Axis.XP.rotationDegrees(-12f * sprint));
		}
		// recoil: back and muzzle-up
		float kickScale = kind == Kind.SNIPER || kind == Kind.SHOTGUN ? 1.6f : 1f;
		pose.translate(0f, 0.01f * rec * kickScale, 0.05f * rec * kickScale * (1f - ads * 0.4f));
		pose.mulPose(Axis.XP.rotationDegrees(5f * rec * kickScale));
		// reload: cant it over and bring it in
		float rw = reload >= 0f ? GunAnim.seg(reload, 0f, 0.12f) * (1f - GunAnim.seg(reload, 0.88f, 1f)) : 0f;
		if (rw > 0f) {
			float seat = kind == Kind.SHOTGUN ? 0f : bump(reload, 0.72f, 0.06f);
			pose.translate(-0.07f * rw, -0.05f * rw + seat * 0.025f, 0.02f * rw);
			pose.mulPose(Axis.ZP.rotationDegrees((kind == Kind.SHOTGUN ? -30f : 32f) * rw));
			pose.mulPose(Axis.XP.rotationDegrees(14f * rw));
		}
		// roll: tucked in and down
		float tw = roll >= 0f ? GunAnim.seg(roll, 0f, 0.15f) * (1f - GunAnim.seg(roll, 0.85f, 1f)) : 0f;
		if (tw > 0f) {
			pose.translate(0f, -0.28f * tw, 0.08f * tw);
			pose.mulPose(Axis.XP.rotationDegrees(-30f * tw));
		}
		// Adrenaline: the gun drops a little while the other hand works
		float sw = stab >= 0f ? GunAnim.seg(stab, 0f, 3f) * (1f - GunAnim.seg(stab, GunAnim.STAB_ANIM_TICKS - 4, GunAnim.STAB_ANIM_TICKS)) : 0f;
		if (sw > 0f) {
			pose.translate(0.03f * sw, -0.12f * sw, 0.03f * sw);
		}

		// hip <-> aim-down-sights
		float a = Math.min(ads, 1f - Math.max(rw, Math.max(tw, sw)));
		Vector3f[] adsT = sightTransform(rig, new Vector3f(0f, -0.004f, -rig.adsDist), 0f);
		Vector3f[] hipT = sightTransform(rig, HIP, HIP_YAW);
		Vector3f rot = new Vector3f(hipT[1]).lerp(adsT[1], a);
		Vector3f tr = new Vector3f(hipT[0]).lerp(adsT[0], a);
		float sc = rig.adsScale;
		pose.translate(tr.x, tr.y, tr.z);
		pose.mulPose(new Quaternionf().rotationXYZ(rot.x * Mth.DEG_TO_RAD, rot.y * Mth.DEG_TO_RAD, rot.z * Mth.DEG_TO_RAD));
		pose.scale(sc, sc, sc);
		pose.translate(-0.5f, -0.5f, -0.5f);
		Matrix4f gun = new Matrix4f(baseInv).mul(pose.last().pose());
		boolean scoped = kind == Kind.SNIPER && ads >= SCOPE_IN;
		if (!scoped) {
			pose.translate(0.5f, 0.5f, 0.5f);
			mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
			pose.translate(-0.5f, -0.5f, -0.5f);
			com.projecthero.mod.client.firearm.MuzzleFlash.draw(pose, buffers, kind, GunAnim.flash(player, partialTick), GunAnim.shotSeed(player));
		}
		pose.popPose();
		if (scoped) {
			return;
		}

		// ---- the hands
		Vector3f grip = at(gun, rig.grip);
		Vector3f support = at(gun, rig.fore);
		ItemStack prop = null;
		if (rw > 0f || reload >= 0f) {
			support = supportReload(kind, gun, rig, reload, support);
			if (kind != Kind.SHOTGUN && ((reload > 0.14f && reload < 0.36f) || (reload > 0.46f && reload < 0.72f))) {
				prop = new ItemStack(PunisherItems.GUN_MAGAZINE);
			}
		}
		if (stab >= 0f) {
			Vector3f s = stabHand(stab);
			float w = GunAnim.seg(stab, 0f, 2f) * (1f - GunAnim.seg(stab, GunAnim.STAB_ANIM_TICKS - 3, GunAnim.STAB_ANIM_TICKS));
			support = new Vector3f(support).lerp(s, w);
			if (stab < PunisherConfig.ADRENALINE_STAB_TICKS + 5) {
				prop = new ItemStack(PunisherItems.ADRENALINE_SYRINGE);
			}
		}
		PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
		drawArm(pose, renderer, buffers, light, player, true, grip, new Vector3f(grip).add(RIGHT_FROM), null);
		drawArm(pose, renderer, buffers, light, player, false, support, new Vector3f(support).add(LEFT_FROM), prop);
	}

	/**
	 * {translation (blocks), rotation (deg)} that puts the rear sight at {@code target} (camera space) with the sight line
	 * level and turned {@code yaw} degrees in toward the middle.
	 */
	private static Vector3f[] sightTransform(Rig rig, Vector3f target, float yaw) {
		Vector3f dir = new Vector3f(rig.front).sub(rig.rear); // front is further (more -Z)
		float pitch = (float) Math.atan2(dir.y, -dir.z);       // > 0: the front sight sits higher
		Quaternionf q = new Quaternionf().rotationXYZ(-pitch, yaw * Mth.DEG_TO_RAD, 0f);
		Vector3f rear = new Vector3f(rig.rear).div(16f).sub(0.5f, 0.5f, 0.5f).mul(rig.adsScale);
		q.transform(rear);
		Vector3f t = new Vector3f(target).sub(rear);
		return new Vector3f[] { t, new Vector3f(-pitch * Mth.RAD_TO_DEG, yaw, 0f) };
	}

	private static Vector3f at(Matrix4f gun, Vector3f px) {
		return gun.transformPosition(px.x / 16f, px.y / 16f, px.z / 16f, new Vector3f());
	}

	/** A soft 0 -> 1 -> 0 pulse centred on {@code c}, {@code w} wide. */
	private static float bump(float t, float c, float w) {
		float d = Math.abs(t - c) / w;
		return d >= 1f ? 0f : 1f - d * d;
	}

	/** Support hand through a reload: off the gun and back (magazine), or to the belt and into the port (shell). */
	private static Vector3f supportReload(Kind kind, Matrix4f gun, Rig rig, float r, Vector3f fore) {
		Vector3f well = at(gun, rig.well);
		Vector3f below = new Vector3f(well).add(-0.12f, -0.42f, 0.06f);
		Vector3f belt = new Vector3f(well).add(-0.22f, -0.55f, 0.14f);
		Vector3f under = new Vector3f(well).add(0f, -0.07f, 0f);
		if (kind == Kind.SHOTGUN) {
			return path(r, new float[] { 0f, 0.25f, 0.55f, 0.7f, 1f }, fore, belt, under, well, fore);
		}
		return path(r, new float[] { 0f, 0.12f, 0.36f, 0.46f, 0.6f, 0.72f, 0.82f, 1f },
				fore, well, below, belt, under, well, new Vector3f(well).add(0f, 0.02f, -0.03f), fore);
	}

	private static Vector3f path(float t, float[] keys, Vector3f... pts) {
		for (int i = 1; i < keys.length; i++) {
			if (t <= keys[i]) {
				float f = GunAnim.smooth((t - keys[i - 1]) / Math.max(1.0e-4f, keys[i] - keys[i - 1]));
				return new Vector3f(pts[i - 1]).lerp(pts[i], f);
			}
		}
		return new Vector3f(pts[pts.length - 1]);
	}

	/** Where the syringe hand is, {@code t} ticks into the stab (camera space). */
	private static Vector3f stabHand(float t) {
		int hit = PunisherConfig.ADRENALINE_STAB_TICKS;
		return path(t, new float[] { 0f, 5f, 7.5f, 9.5f, hit, hit + 4f, GunAnim.STAB_ANIM_TICKS },
				new Vector3f(-0.42f, -0.75f, -0.35f), new Vector3f(-0.22f, 0.02f, -0.5f), new Vector3f(-0.2f, 0.06f, -0.5f),
				new Vector3f(-0.16f, -0.95f, -0.3f), new Vector3f(-0.16f, -0.97f, -0.3f), new Vector3f(-0.55f, -0.7f, -0.35f),
				new Vector3f(-0.6f, -0.9f, -0.3f));
	}

	/**
	 * One arm, from its shoulder anchor toward {@code hand} so the fist lands on it. Model arms run down +Y from the
	 * shoulder pivot with the fist's middle 8 px along; the arm's front (-Z) is turned to face up.
	 */
	private static void drawArm(PoseStack pose, PlayerRenderer renderer, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			boolean right, Vector3f hand, Vector3f shoulder, ItemStack prop) {
		Vector3f d = new Vector3f(hand).sub(shoulder).normalize();
		Vector3f up = new Vector3f(0f, 1f, 0f);
		Vector3f front = new Vector3f(up).sub(new Vector3f(d).mul(up.dot(d)));
		if (front.lengthSquared() < 1.0e-6f) {
			front.set(0f, 0f, -1f);
		}
		front.normalize();
		Vector3f z = new Vector3f(front).negate();
		Vector3f x = new Vector3f(d).cross(z).normalize();
		Matrix3f basis = new Matrix3f(x, d, z);
		Quaternionf q = basis.getNormalizedRotation(new Quaternionf());
		float side = right ? -1f : 1f; // the fist's middle sits 1 px toward the arm's outer edge
		Vector3f fist = q.transform(new Vector3f(side / 16f, 0.5f, 0f)).mul(ARM_SCALE);
		Vector3f pivot = new Vector3f(hand).sub(fist);

		pose.pushPose();
		pose.translate(pivot.x, pivot.y, pivot.z);
		pose.mulPose(q);
		pose.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
		pose.pushPose();
		pose.translate(right ? 5f / 16f : -5f / 16f, -2f / 16f, 0f); // undo the arm part's own shoulder offset
		if (right) {
			renderer.renderRightHand(pose, buffers, light, player);
		} else {
			renderer.renderLeftHand(pose, buffers, light, player);
		}
		pose.popPose();
		if (prop != null) {
			pose.translate(side / 16f, 0.53f, 0f);
			if (prop.is(PunisherItems.ADRENALINE_SYRINGE)) {
				pose.mulPose(Axis.XP.rotationDegrees(180f));
				pose.scale(0.62f, 0.62f, 0.62f);
			} else {
				pose.translate(0f, 0.08f, -0.05f);
				pose.mulPose(Axis.XP.rotationDegrees(180f));
				pose.scale(0.7f, 0.7f, 0.7f);
			}
			Minecraft.getInstance().getItemRenderer().renderStatic(player, prop, ItemDisplayContext.NONE, false, pose, buffers,
					player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
		}
		pose.popPose();
	}
}
