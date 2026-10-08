package com.projecthero.mod.client.ironman;

import java.util.UUID;

import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.15.9 Sentry Mode, client side: the renderer registration, the per-draw hand-off to {@code SuperheroArmorRenderer}
 * (how far the standing suit's back is open, and whether its lights are off) and the owner's small HUD block.
 *
 * <p>v0.15.15 (user requests):
 * <ul>
 *   <li>The back opens as two DOORS -- {@link #doorClip}: each bone's front half is drawn untouched, its back half split
 *       down the middle and each panel swung out on a hinge at the side of the back. Nothing caves in at the front.</li>
 *   <li>The player being walked out of / into the suit ({@link IronManSentryEntity#stepperId}): their body is locked to
 *       the suit's facing ({@link #tick}) and their arms held a little out like the open suit's ({@link #applyStepPose}),
 *       so the shell closes onto the right parts.</li>
 *   <li>The HUD shows the mode's energy drain and when it went Defensive on its own.</li>
 * </ul>
 */
public final class IronManSentryClient {
	/** The back panels swing this far open (degrees) when fully open. */
	public static final float OPEN_DEG = 78f;
	/** The open suit's arm roll (degrees, matches {@code IronManSentryEntity#animate}'s open pose)... */
	public static final float OPEN_ARM_ROLL = 14f;
	/** ...and pitch. */
	public static final float OPEN_ARM_PITCH = -6f;

	/** Set while a sentry's armour stand is being drawn: how far its back is open (degrees, 0 = shut). Render thread. */
	public static float openDeg;
	/** Set while a powered-down sentry is being drawn: no glow pass. Render thread. */
	public static boolean dark;
	/** Set while a sentry is being drawn: its eyes / reactor flash (0..1) during the mode-switch flourish. Render thread. */
	public static float flash;

	private static IronManSentryEntity cached;
	private static long cachedAt = Long.MIN_VALUE;

	private IronManSentryClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(IronManEntityTypes.SENTRY, IronManSentryRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	// ------------------------------------------------------------------ the back doors

	/**
	 * The vertices of {@code quad} for one part of a bone with its back open: {@code part} 0 = the front half (z below the
	 * bone's middle), drawn as it is; -1 / +1 = the back panel on that side, clipped to its quarter and swung {@code deg}
	 * out about a vertical hinge at the side of the back (x = the bone's edge, z = its middle). Null = nothing of the
	 * quad in that part. Cut faces keep their texture (UVs re-interpolated along the cut edge).
	 */
	public static IronManBraceletClient.Vert[] doorClip(GeoQuad quad, float[] bounds, int part, float deg) {
		GeoVertex[] vs = quad.vertices();
		IronManBraceletClient.Vert[] v = new IronManBraceletClient.Vert[vs.length];
		for (int i = 0; i < vs.length; i++) {
			v[i] = new IronManBraceletClient.Vert(vs[i].position().x(), vs[i].position().y(), vs[i].position().z(),
					vs[i].texU(), vs[i].texV());
		}
		float zCut = (bounds[2] + bounds[3]) * 0.5f;
		float xCut = (bounds[0] + bounds[1]) * 0.5f;
		if (part == 0) {
			return clip(v, 2, zCut, false); // the front (-z) stays put
		}
		v = clip(v, 2, zCut, true);
		if (v == null) {
			return null;
		}
		v = clip(v, 0, xCut, part > 0);
		if (v == null) {
			return null;
		}
		float hingeX = part > 0 ? bounds[1] : bounds[0];
		double a = Math.toRadians(part > 0 ? deg : -deg);
		float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
		for (int i = 0; i < v.length; i++) {
			float dx = v[i].x() - hingeX, dz = v[i].z() - zCut;
			v[i] = new IronManBraceletClient.Vert(hingeX + dx * cos + dz * sin, v[i].y(), zCut - dx * sin + dz * cos,
					v[i].u(), v[i].v());
		}
		return v;
	}

	/** The normal of a quad in {@code part} (see {@link #doorClip}). */
	public static float[] doorNormal(float[] bounds, int part, float deg, float nx, float ny, float nz) {
		if (part == 0) {
			return new float[] { nx, ny, nz };
		}
		double a = Math.toRadians(part > 0 ? deg : -deg);
		float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
		return new float[] { nx * cos + nz * sin, ny, -nx * sin + nz * cos };
	}

	private static float coord(IronManBraceletClient.Vert v, int axis) {
		return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
	}

	/**
	 * Clip a quad (an axis-aligned rectangle) to one side of the plane {@code axis = cut}: vertices past it slide onto
	 * it, their UVs interpolated towards the partner vertex across the cut. Null if no vertex is on the kept side.
	 */
	static IronManBraceletClient.Vert[] clip(IronManBraceletClient.Vert[] in, int axis, float cut, boolean keepGreater) {
		boolean any = false;
		boolean[] keep = new boolean[in.length];
		for (int i = 0; i < in.length; i++) {
			float c = coord(in[i], axis);
			keep[i] = keepGreater ? c >= cut - 1.0e-4f : c <= cut + 1.0e-4f;
			any |= keep[i];
		}
		if (!any) {
			return null;
		}
		IronManBraceletClient.Vert[] out = new IronManBraceletClient.Vert[in.length];
		for (int i = 0; i < in.length; i++) {
			if (keep[i]) {
				out[i] = in[i];
				continue;
			}
			IronManBraceletClient.Vert v = in[i];
			IronManBraceletClient.Vert partner = null;
			for (int j = 0; j < in.length; j++) {
				if (keep[j] && same(in[j], v, axis)) {
					partner = in[j];
					break;
				}
			}
			float u = v.u(), t = v.v();
			if (partner != null) {
				float pc = coord(partner, axis), vc = coord(v, axis);
				if (Math.abs(vc - pc) > 1.0e-6f) {
					float k = (cut - pc) / (vc - pc);
					u = partner.u() + (u - partner.u()) * k;
					t = partner.v() + (t - partner.v()) * k;
				}
			}
			out[i] = new IronManBraceletClient.Vert(axis == 0 ? cut : v.x(), v.y(), axis == 2 ? cut : v.z(), u, t);
		}
		return out;
	}

	/** Do {@code a} and {@code b} share the two coordinates other than {@code axis}? */
	private static boolean same(IronManBraceletClient.Vert a, IronManBraceletClient.Vert b, int axis) {
		for (int k = 0; k < 3; k++) {
			if (k != axis && Math.abs(coord(a, k) - coord(b, k)) > 1.0e-4f) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ the player walking out / in

	/** Is this step kind one where the player's body is locked to the suit (walking out, walking in, closing)? */
	private static boolean locked(int kind) {
		return kind == IronManSentryEntity.STEP_OUT || kind == IronManSentryEntity.STEP_IN
				|| kind == IronManSentryEntity.STEP_WRAP;
	}

	/** The sentry walking {@code player} out of / into it right now, if any (render / client thread). */
	public static IronManSentryEntity stepping(Player player) {
		IronManSentryEntity s = STEPPERS.get(player.getId());
		return s != null && !s.isRemoved() && s.stepperId() == player.getId() && locked(s.stepKind()) ? s : null;
	}

	/** Player entity id -> the sentry walking them, rebuilt every client tick. */
	private static final java.util.Map<Integer, IronManSentryEntity> STEPPERS = new java.util.HashMap<>();

	/** End of every client tick: lock each stepping player's body to their suit's facing. */
	private static void tick() {
		Minecraft mc = Minecraft.getInstance();
		STEPPERS.clear();
		if (mc.level == null) {
			return;
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof IronManSentryEntity s) || s.stepperId() < 0 || !locked(s.stepKind())) {
				continue;
			}
			STEPPERS.put(s.stepperId(), s);
			if (mc.level.getEntity(s.stepperId()) instanceof LivingEntity le) {
				float yaw = s.getYRot();
				le.yBodyRot = yaw;
				le.yBodyRotO = yaw;
				if (Math.abs(Mth.wrapDegrees(le.yHeadRot - yaw)) > 60f) {
					le.yHeadRot = yaw + Mth.clamp(Mth.wrapDegrees(le.yHeadRot - yaw), -60f, 60f);
				}
			}
		}
	}

	/**
	 * Called from {@code HumanoidModelMixin}: a player being walked out of / into their suit holds their arms a little
	 * out -- the open suit's own pose ({@link #OPEN_ARM_ROLL}, {@link #OPEN_ARM_PITCH}) -- keeping a trace of the walk's
	 * arm swing, so the closing shell meets the arms where they are.
	 */
	public static void applyStepPose(Player player, HumanoidModel<?> model) {
		IronManSentryEntity s = stepping(player);
		if (s == null) {
			return;
		}
		float roll = OPEN_ARM_ROLL * Mth.DEG_TO_RAD;
		float pitch = OPEN_ARM_PITCH * Mth.DEG_TO_RAD;
		boolean closing = s.stepKind() == IronManSentryEntity.STEP_WRAP;
		float keep = closing ? 0f : 0.25f;
		model.rightArm.xRot = model.rightArm.xRot * keep + pitch;
		model.leftArm.xRot = model.leftArm.xRot * keep + pitch;
		model.rightArm.yRot = 0f;
		model.leftArm.yRot = 0f;
		model.rightArm.zRot = roll;
		model.leftArm.zRot = -roll;
		if (closing) {
			model.rightLeg.xRot = 0f;
			model.leftLeg.xRot = 0f;
			model.rightLeg.zRot = 0f;
			model.leftLeg.zRot = 0f;
			model.head.xRot = 0f;
			model.head.yRot = 0f;
			model.hat.copyFrom(model.head);
		}
	}

	// ------------------------------------------------------------------ HUD

	/** The local player's own sentry, if one is loaded on this client (rescanned once a tick). */
	public static IronManSentryEntity mine() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			cached = null;
			return null;
		}
		long now = mc.level.getGameTime();
		if (now != cachedAt) {
			cachedAt = now;
			cached = null;
			UUID me = mc.player.getUUID();
			double best = Double.MAX_VALUE;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof IronManSentryEntity s && !s.isRemoved() && me.equals(s.ownerId())) {
					double d = s.distanceToSqr(mc.player);
					if (d < best) {
						best = d;
						cached = s;
					}
				}
			}
		}
		return cached;
	}

	/**
	 * The owner's status block while one of their suits stands as a sentry (drawn by {@code IronManHud} when no helmet is
	 * worn): a chip with the mode (v0.15.15: "auto" when it went Defensive on its own), then the suit's energy (with the
	 * mode's drain a second) and integrity Gauges. Returns the next y.
	 */
	public static int renderHud(GuiGraphics g, Font font, int x, int y, int w) {
		IronManSentryEntity s = mine();
		if (s == null) {
			return y;
		}
		IronManSuit suit = s.suit();
		float cap = suit == null ? 0f : suit.energyCapacity();
		float maxInt = IronManEnergy.maxIntegrity(s.suitId());
		float ef = cap <= 0f ? 0f : IronManUiLayout.clamp01(s.energy() / cap);
		float inf = maxInt <= 0f ? 0f : IronManUiLayout.clamp01(s.integrity() / maxInt);
		String head = Component.translatable(s.autoDefending() ? "hud.projecthero.ironman.sentry_auto" : "hud.projecthero.ironman.sentry",
				IronManSentryEntity.modeName(s.mode())).getString();
		if (!s.powered()) {
			head = Component.translatable("hud.projecthero.ironman.sentry_offline").getString();
		}
		IronManGui.chip(g, font, x, y, w, head, s.powered() ? (s.autoDefending() ? IronManGui.ORANGE : IronManGui.CYAN) : IronManGui.RED);
		y += 13;
		g.fill(x, y - 1, x + w, y + 2 * IronManUiLayout.GAUGE_ROW_H, 0x8C050B12);
		String energyText = Math.round(ef * 100f) + "%";
		float drain = IronManSentryEntity.drainPerSecond(s.mode());
		if (drain > 0f && s.powered()) {
			energyText = Component.translatable("hud.projecthero.ironman.sentry_drain", energyText,
					Math.round(drain * 100f)).getString();
		}
		IronManGui.gauge(g, font, x + 2, y, w - 4, Component.translatable("hud.projecthero.ironman.sentry_energy").getString(),
				IronManGui.TEXT_DIM, energyText, IronManGui.TEXT, ef, ef > 0.2f ? IronManGui.CYAN : IronManGui.ORANGE);
		y += IronManUiLayout.GAUGE_ROW_H;
		IronManGui.gauge(g, font, x + 2, y, w - 4, Component.translatable("hud.projecthero.ironman.sentry_integrity").getString(),
				IronManGui.TEXT_DIM, Math.round(inf * 100f) + "%", IronManGui.TEXT, inf, inf > 0.25f ? IronManGui.GREEN : IronManGui.RED);
		y += IronManUiLayout.GAUGE_ROW_H + 2;
		return y;
	}
}
