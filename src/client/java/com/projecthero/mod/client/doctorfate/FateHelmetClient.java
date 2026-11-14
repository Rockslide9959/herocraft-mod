package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.network.FateSpellFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 the Helmet of Fate on the client, outside the suit layer:
 * <ul>
 *   <li><b>The summon</b> ({@link FateSpellFxPayload#SUMMON}): the helmet itself flies from where it lay (or out of the ankh portal
 *   beside him) along a rising golden arc, spinning, into his raised hand at chest height; {@link FateSpellFxClient} draws its
 *   golden trail and the ankh round it from the same path ({@link #pathPoint}).</li>
 *   <li><b>First person</b>: the put-on sequence reads from inside the helmet -- it rises into view in your hands, is lifted high
 *   with the ankh flaring behind it, then comes down over your eyes (the view narrows to the helmet's eye slit in gold and opens
 *   again with a flash). Taking it off plays the same backwards and the helmet sinks out of view as it turns to gold.</li>
 * </ul>
 */
public final class FateHelmetClient {
	private record Flight(Vec3 from, int entity, double start, int life, boolean fromWhereItLay) {
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();

	private FateHelmetClient() {
	}

	static void reset() {
		FLIGHTS.clear();
	}

	static void receive(FateSpellFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (FLIGHTS.size() > 16) {
			FLIGHTS.remove(0);
		}
		FLIGHTS.add(new Flight(new Vec3(p.x(), p.y(), p.z()), p.entity(), mc.level.getGameTime(), p.life(), p.size() > 0.5f));
	}

	/** Where the summoned helmet arrives: in front of the chest of {@code e} (its hands meet it there). */
	static Vec3 catchPoint(Entity e, float partial) {
		Vec3 base = e.getPosition(partial);
		float yaw = (e instanceof Player p ? Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
		return base.add(-Mth.sin(yaw) * 0.5, e.getBbHeight() * 0.62, Mth.cos(yaw) * 0.5);
	}

	/**
	 * The summon path at {@code f} 0..1: a rising arc from {@code from} to {@code to} (higher the farther it flies), eased so it
	 * leaps off, races, and slows into the hand.
	 */
	static Vec3 pathPoint(Vec3 from, Vec3 to, double f) {
		double e = f < 0.5 ? 4 * f * f * f : 1 - Math.pow(-2 * f + 2, 3) / 2;
		double lift = Math.min(6.0, 1.2 + from.distanceTo(to) * 0.18);
		Vec3 mid = from.lerp(to, 0.5).add(0, lift, 0);
		Vec3 a = from.lerp(mid, e);
		Vec3 b = mid.lerp(to, e);
		return a.lerp(b, e);
	}

	/** The live flights, for the trail ({@link FateSpellFxClient}). */
	static void forEachFlight(float partial, FlightVisitor visitor) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		double now = mc.level.getGameTime() + partial;
		for (Flight fl : FLIGHTS) {
			Entity e = mc.level.getEntity(fl.entity);
			if (e == null) {
				continue;
			}
			double f = Mth.clamp((now - fl.start) / fl.life, 0.0, 1.0);
			visitor.visit(fl.from, catchPoint(e, partial), f, fl.fromWhereItLay);
		}
	}

	interface FlightVisitor {
		void visit(Vec3 from, Vec3 to, double f, boolean fromWhereItLay);
	}

	/** The flying helmets themselves (world render, after the entities). */
	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (FLIGHTS.isEmpty() || mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext();) {
			Flight fl = it.next();
			double age = now - fl.start;
			Entity e = mc.level.getEntity(fl.entity);
			if (e == null || age > fl.life + 1 || age < -40) {
				it.remove();
				continue;
			}
			double f = Mth.clamp(age / fl.life, 0.0, 1.0);
			if (f >= 1.0) {
				continue; // in his hands now: the suit layer draws it
			}
			if (e == mc.player && mc.options.getCameraType().isFirstPerson() && f > 0.8) {
				continue; // right in front of the camera: the first-person overlay takes over
			}
			Vec3 to = catchPoint(e, partial);
			Vec3 at = pathPoint(fl.from, to, f);
			Vec3 ahead = pathPoint(fl.from, to, Math.min(1.0, f + 0.02));
			float travelYaw = (float) Math.toDegrees(Math.atan2(-(ahead.x - at.x), ahead.z - at.z));
			float bodyYaw = e instanceof Player p ? Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) : e.getYRot();
			// it spins as it flies and turns to face the way he faces as it comes in
			float spin = (float) (age * 38.0);
			float yaw = Mth.rotLerp((float) smooth((f - 0.65) / 0.35), travelYaw + spin, bodyYaw);
			float alpha = (float) Math.min(1.0, (fl.fromWhereItLay ? 0.6 : 0.0) + f * 6.0);
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
			pose.mulPose(Axis.XP.rotationDegrees((float) (Math.sin(age * 0.5) * 8.0)));
			pose.translate(0, -0.25, 0);
			pose.scale(-1f, -1f, 1f);
			FateSuitLayer.renderHelmetAlone(pose, buffers, LightTexture.FULL_BRIGHT, alpha, 0.55f);
			pose.popPose();
		}
	}

	private static double smooth(double t) {
		t = Mth.clamp(t, 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}

	// ---------------------------------------------------------------- first person

	/** The put-on / lift-off seen from inside (HUD). */
	static void renderFirstPerson(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson() || !DoctorFate.hasPower(p)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		FateHelmetAnim a = FateHelmetAnim.of(p, partial);
		float t = DoctorFate.helmetSequenceAge(p, partial);
		if (t <= -999f || DoctorFate.possessed(p)) {
			return;
		}
		var s = DoctorFate.peek(p);
		int W = g.guiWidth();
		int H = g.guiHeight();
		boolean on = s.helmetGoingOn;
		// the helmet's face (the suit skin's head front + hat front), drawn big
		if (a.held() && a.alpha() > 0.01f) {
			// where its face is on screen, by the sequence's own phases (FateHelmetAnim): centre height and size, in screen heights
			float cyK;
			float sizeK;
			if (on) {
				if (t < 6f) { // formed in the hands, rising into view from below
					float k = FateHelmetAnim.smooth(t / 6f);
					cyK = Mth.lerp(k, 1.15f, 0.78f);
					sizeK = 0.46f;
				} else if (t < 14f) { // raised high: up to the top of the view
					float k = FateHelmetAnim.smooth((t - 6f) / 8f);
					cyK = Mth.lerp(k, 0.78f, 0.05f);
					sizeK = Mth.lerp(k, 0.46f, 0.55f);
				} else if (t < 20f) { // held there, the ankh flaring
					cyK = 0.05f - 0.02f * Mth.sin((t - 14f) / 6f * Mth.PI);
					sizeK = 0.55f;
				} else { // brought down over the eyes: it grows until its eye slits are your view
					float k = FateHelmetAnim.easeOut((t - 20f) / 7f);
					sizeK = Mth.lerp(k, 0.55f, 3.4f);
					cyK = Mth.lerp(k, 0.05f, 0.5f + sizeK * 0.07f);
				}
			} else {
				if (t < 14f) { // lifted off: it shrinks away up out of the eyes
					float k = FateHelmetAnim.smooth((t - 6f) / 8f);
					sizeK = Mth.lerp(k, 3.4f, 0.55f);
					cyK = Mth.lerp(k, 0.5f + 3.4f * 0.07f, 0.05f);
				} else if (t < 20f) { // brought down in front of the chest
					float k = FateHelmetAnim.smooth((t - 14f) / 6f);
					cyK = Mth.lerp(k, 0.05f, 0.8f);
					sizeK = Mth.lerp(k, 0.55f, 0.46f);
				} else { // sinking out of view as it turns to gold
					float k = (t - 20f) / 4f;
					cyK = Mth.lerp(k, 0.8f, 1.05f);
					sizeK = 0.46f;
				}
			}
			float size = H * sizeK;
			float cy = H * cyK;
			int x = Math.round(W / 2f - size / 2f);
			int y = Math.round(cy - size / 2f);
			int sz = Math.round(size);
			RenderSystem.enableBlend();
			float fade = on && t > 25f ? Math.max(0f, 1f - (t - 25f) / 2f) : 1f; // the eye-slit bands take over as it settles
			g.setColor(1f, 1f, 1f, Mth.clamp(a.alpha() * fade, 0f, 1f));
			g.blit(FateSuitLayer.SUIT, x, y, sz, sz, 8f, 8f, 8, 8, 64, 64);
			g.blit(FateSuitLayer.SUIT, x, y, sz, sz, 40f, 8f, 8, 8, 64, 64);
			if (a.glow() > 0.01f) {
				g.setColor(1f, 0.8f, 0.3f, a.glow() * 0.45f);
				g.blit(FateSuitLayer.SUIT, x, y, sz, sz, 40f, 8f, 8, 8, 64, 64);
			}
			g.setColor(1f, 1f, 1f, 1f);
			RenderSystem.disableBlend();
		}
		// the moment it covers the eyes: the view closes to the helmet's eye slit, then opens with a gold flash
		float close = 0f;
		if (on && t >= 23f && t < 31f) {
			close = t < 27f ? (t - 23f) / 4f : 1f - (t - 27f) / 4f;
		} else if (!on && t >= 2f && t < 10f) {
			close = t < 6f ? (t - 2f) / 4f : 1f - (t - 6f) / 4f;
		}
		close = Mth.clamp(close, 0f, 1f);
		if (close > 0.01f) {
			int band = Math.round(H * 0.5f * close * 0.82f);
			int dark = ((int) (close * 235) << 24) | 0x0A0700;
			g.fill(0, 0, W, band, dark);
			g.fill(0, H - band, W, H, dark);
			int gold = ((int) (close * 200) << 24) | 0xFFC93A;
			g.fill(0, band, W, band + 2, gold);
			g.fill(0, H - band - 2, W, H - band, gold);
		}
		float flash = on ? 1f - Math.abs(t - 28f) / 3f : 0f;
		if (flash > 0f) {
			g.fill(0, 0, W, H, ((int) (Mth.clamp(flash, 0f, 1f) * 90) << 24) | 0xFFE9A0);
		}
	}
}
