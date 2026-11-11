package com.projecthero.mod.client.saiyan.kit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.saiyan.SaiyanSounds;
import com.projecthero.mod.saiyan.kit.SaiyanSpiritBomb;
import com.projecthero.mod.saiyan.kit.SpiritBombState;
import com.projecthero.mod.saiyan.kit.network.SpiritBombFxPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: the Spirit Bomb as every viewer sees it. The held bomb (from the caster's synced {@link SpiritBombState}): a pale
 * core inside a glowing blue-white body with drifting light on its surface, three energy bands and three faint bloom shells,
 * with motes of energy streaming in from all round (capped at {@link #MOTE_CAP}). The thrown bomb (from
 * {@link SpiritBombFxPayload#FLY}) is the same ball moving. The impact: a blinding flash for anyone near, a dome of light,
 * a ground shockwave ring and a sky ring, lingering smoke; the camera shake is the shared tremor packet. Sounds are quiet,
 * low and rising (the user dislikes loud ones), played without distance roll-off at a volume this class sets by distance.
 * Also the pose: both arms straight overhead while rising and charging, swung forward for the throw.
 */
public final class SpiritBombFx {
	private SpiritBombFx() {
	}

	private record Fly(Vec3 pos, Vec3 vel, float radius, double at) {
	}

	private record Timed(Vec3 pos, float radius, float charge, double at) {
	}

	private static final class Mote {
		double x, y, z, vx, vy, vz;
		int age;
		final int owner;
		final boolean outward;

		Mote(int owner, boolean outward) {
			this.owner = owner;
			this.outward = outward;
		}
	}

	static final int MOTE_CAP = 160;

	private static final Map<Integer, Fly> FLIES = new HashMap<>();
	private static final List<Timed> BLASTS = new ArrayList<>();
	private static final List<Timed> FIZZLES = new ArrayList<>();
	private static final List<Mote> MOTES = new ArrayList<>();
	private static final Map<Integer, Long> NEXT_HUM = new HashMap<>();
	private static final RandomSource RNG = RandomSource.create();
	private static float flash;
	private static MultiBufferSource buffers;

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(SpiritBombFxPayload.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
		WorldRenderEvents.AFTER_ENTITIES.register(SpiritBombFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(SpiritBombFx::tick);
		HudRenderCallback.EVENT.register((gg, delta) -> hud(gg));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
	}

	static void reset() {
		FLIES.clear();
		BLASTS.clear();
		FIZZLES.clear();
		MOTES.clear();
		NEXT_HUM.clear();
		flash = 0f;
	}

	private static double now(float partial) {
		ClientLevel l = Minecraft.getInstance().level;
		return l == null ? 0.0 : l.getGameTime() + partial;
	}

	// ---------------------------------------------------------------- payloads

	static void receive(SpiritBombFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		double now = now(0f);
		switch (p.kind()) {
			case SpiritBombFxPayload.FLY -> FLIES.put(p.id(), new Fly(p.pos(), p.vel(), p.radius(), now));
			case SpiritBombFxPayload.IMPACT -> {
				// the nearest flight is the one that went off
				Integer gone = null;
				double best = Double.MAX_VALUE;
				for (Map.Entry<Integer, Fly> e : FLIES.entrySet()) {
					Fly f = e.getValue();
					double d = f.pos().add(f.vel().scale(now - f.at())).distanceToSqr(p.pos());
					if (d < best) {
						best = d;
						gone = e.getKey();
					}
				}
				if (gone != null && best < (p.radius() * 2.0 + 6.0) * (p.radius() * 2.0 + 6.0)) {
					FLIES.remove(gone);
				}
				BLASTS.add(new Timed(p.pos(), p.radius(), p.charge(), now));
				Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
				double dist = cam.distanceTo(p.pos());
				flash = Math.max(flash, (float) Mth.clamp(1.15 - dist / (p.radius() * 5.0 + 60.0), 0.0, 1.0));
				quiet(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), p.pos(), 0.4f, p.radius() * 10.0 + 120.0, 0.45f + 0.15f * (1f - p.charge())); // a deep, distant boom, kept quiet
				quiet(SaiyanSounds.POWER_DOWN, p.pos(), 0.3f, p.radius() * 6.0 + 60.0, 0.5f);
				int n = 6 + (int) (p.radius() * 0.6);
				for (int i = 0; i < n; i++) {
					Vec3 o = randomUnit().scale(p.radius() * 0.5 * RNG.nextDouble());
					mc.level.addParticle(ParticleTypes.EXPLOSION, p.pos().x + o.x, p.pos().y + Math.abs(o.y), p.pos().z + o.z, 0, 0, 0);
				}
			}
			case SpiritBombFxPayload.FIZZLE -> {
				FIZZLES.add(new Timed(p.pos(), p.radius(), p.charge(), now));
				for (int i = 0; i < 40 && MOTES.size() < MOTE_CAP + 40; i++) {
					Mote m = new Mote(-1, true);
					Vec3 d = randomUnit();
					m.x = p.pos().x + d.x * p.radius();
					m.y = p.pos().y + d.y * p.radius();
					m.z = p.pos().z + d.z * p.radius();
					double s = 0.25 + 0.25 * RNG.nextDouble();
					m.vx = d.x * s;
					m.vy = d.y * s;
					m.vz = d.z * s;
					MOTES.add(m);
				}
			}
			default -> {
			}
		}
	}

	/** A one-shot at {@code at} with no roll-off: {@code vol} up close, fading to nothing at {@code range}. */
	private static void quiet(SoundEvent s, Vec3 at, float vol, double range, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
		float v = (float) (vol * Mth.clamp(1.0 - d / range, 0.0, 1.0));
		if (v < 0.02f) {
			return;
		}
		mc.getSoundManager().play(new SimpleSoundInstance(s.getLocation(), SoundSource.PLAYERS, v, pitch, RNG, false, 0,
				SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
	}

	private static Vec3 randomUnit() {
		double y = RNG.nextDouble() * 2.0 - 1.0;
		double a = RNG.nextDouble() * Math.PI * 2.0;
		double r = Math.sqrt(1.0 - y * y);
		return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
	}

	// ---------------------------------------------------------------- held bombs

	/** The held bomb's radius for {@code p} at this frame (0 when none). */
	static float heldRadius(Player p, float partial) {
		SpiritBombState s = SaiyanSpiritBomb.state(p);
		return s.phase() == SaiyanSpiritBomb.CHARGE ? s.radiusAt(now(partial)) : 0f;
	}

	static Vec3 heldCentre(Player p, float partial, float radius) {
		return SaiyanSpiritBomb.holdPos(p.getPosition(partial), radius);
	}

	// ---------------------------------------------------------------- tick: motes, hums, smoke, flash

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		flash *= 0.84f;
		if (flash < 0.01f) {
			flash = 0f;
		}
		double now = now(0f);
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		Map<Integer, Vec3> centres = new HashMap<>();
		Map<Integer, Float> radii = new HashMap<>();
		for (Player p : mc.level.players()) {
			float r = heldRadius(p, 0f);
			if (r <= 0f) {
				NEXT_HUM.remove(p.getId());
				continue;
			}
			Vec3 c = heldCentre(p, 0f, r);
			centres.put(p.getId(), c);
			radii.put(p.getId(), r);
			// the low hum, a little higher as it grows
			long t = mc.level.getGameTime();
			Long next = NEXT_HUM.get(p.getId());
			if (next == null || t >= next) {
				NEXT_HUM.put(p.getId(), t + 60);
				quiet(net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, c, 0.35f, 160.0, 0.5f + 0.45f * (r / SaiyanSpiritBomb.RADIUS_MAX));
			}
			// energy gathering in from all round, more from below
			for (int i = 0; i < 3 && MOTES.size() < MOTE_CAP; i++) {
				Vec3 d = randomUnit();
				d = new Vec3(d.x, d.y > 0.5 ? -d.y : d.y, d.z);
				double far = r + 6.0 + RNG.nextDouble() * 22.0;
				Mote m = new Mote(p.getId(), false);
				m.x = c.x + d.x * far;
				m.y = c.y + d.y * far;
				m.z = c.z + d.z * far;
				double s = 0.3 + 0.2 * RNG.nextDouble();
				m.vx = -d.x * s;
				m.vy = -d.y * s;
				m.vz = -d.z * s;
				MOTES.add(m);
			}
		}
		for (Iterator<Mote> it = MOTES.iterator(); it.hasNext();) {
			Mote m = it.next();
			m.age++;
			if (m.outward) {
				m.vx *= 0.9;
				m.vy *= 0.9;
				m.vz *= 0.9;
				if (m.age > 20) {
					it.remove();
					continue;
				}
			} else {
				Vec3 c = centres.get(m.owner);
				if (c == null || m.age > 90) {
					it.remove();
					continue;
				}
				double dx = c.x - m.x, dy = c.y - m.y, dz = c.z - m.z;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (dist < radii.get(m.owner) * 0.92) {
					it.remove();
					continue;
				}
				double speed = Math.min(1.6, Math.sqrt(m.vx * m.vx + m.vy * m.vy + m.vz * m.vz) * 1.06);
				m.vx = dx / dist * speed;
				m.vy = dy / dist * speed;
				m.vz = dz / dist * speed;
			}
			m.x += m.vx;
			m.y += m.vy;
			m.z += m.vz;
		}
		// flights fly on
		FLIES.values().removeIf(f -> now - f.at() > 200);
		// lingering smoke over the blast
		for (Iterator<Timed> it = BLASTS.iterator(); it.hasNext();) {
			Timed b = it.next();
			double age = now - b.at();
			if (age > 140) {
				it.remove();
				continue;
			}
			if (age >= 4 && age < 120 && cam.distanceTo(b.pos()) < 200) {
				int n = Math.min(4, 1 + (int) (b.radius() / 6f));
				for (int i = 0; i < n; i++) {
					double a = RNG.nextDouble() * Math.PI * 2.0, rr = Math.sqrt(RNG.nextDouble()) * b.radius() * 0.95;
					mc.level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, b.pos().x + Math.cos(a) * rr,
							b.pos().y + RNG.nextDouble() * b.radius() * 0.25, b.pos().z + Math.sin(a) * rr, 0.0, 0.03 + RNG.nextDouble() * 0.04, 0.0);
				}
			}
		}
		FIZZLES.removeIf(f -> now - f.at() > 20);
	}

	private static void hud(GuiGraphics gg) {
		if (flash <= 0.01f) {
			return;
		}
		int a = (int) (Mth.clamp(flash, 0f, 1f) * 235f);
		gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), (a << 24) | 0xF4FAFF);
	}

	// ---------------------------------------------------------------- the pose

	/** Both arms straight up while rising and charging; swung forward for the throw. From {@code SpiritBombPoseMixin}. */
	public static void pose(Player p, HumanoidModel<?> model) {
		SpiritBombState s = SaiyanSpiritBomb.state(p);
		if (s.phase() == SaiyanSpiritBomb.NONE || p.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = (float) (p.level().getGameTime() + partial - s.since());
		float up = -3.05f;
		switch (s.phase()) {
			case SaiyanSpiritBomb.RISE -> {
				float k = Mth.clamp(age / 8f, 0f, 1f);
				arms(model, Mth.lerp(k, model.rightArm.xRot, up), 0.14f * k);
				legs(model, k);
			}
			case SaiyanSpiritBomb.CHARGE -> {
				arms(model, up + 0.03f * Mth.sin(age * 0.25f), 0.14f);
				legs(model, 1f);
			}
			case SaiyanSpiritBomb.THROW -> {
				float k = Mth.clamp(age / 5f, 0f, 1f);
				arms(model, Mth.lerp(k * k, up, -1.25f), 0.14f * (1f - k) + 0.05f);
				legs(model, 1f - k);
			}
			default -> {
			}
		}
	}

	private static void arms(HumanoidModel<?> model, float x, float spread) {
		set(model.rightArm, x, 0f, spread);
		set(model.leftArm, x, 0f, -spread);
	}

	/** Legs hanging a little apart and bent back, as in a hover. */
	private static void legs(HumanoidModel<?> model, float k) {
		model.rightLeg.xRot = Mth.lerp(k, model.rightLeg.xRot, 0.12f);
		model.leftLeg.xRot = Mth.lerp(k, model.leftLeg.xRot, 0.22f);
		model.rightLeg.zRot = Mth.lerp(k, model.rightLeg.zRot, 0.06f);
		model.leftLeg.zRot = Mth.lerp(k, model.leftLeg.zRot, -0.06f);
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	// ---------------------------------------------------------------- render

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = now(partial);
		float t = (float) (now % 100000.0);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		buffers = ctx.consumers();

		boolean any = false;
		for (Player p : mc.level.players()) {
			float r = heldRadius(p, partial);
			if (r > 0f) {
				drawBomb(pose, cam, heldCentre(p, partial, r), r, t, 1f);
				// a glow on the raised hands (not round his own camera in first person: it would white out the screen)
				Vec3 hands = p.getPosition(partial).add(0, SaiyanSpiritBomb.HAND_TOP - 0.15, 0);
				if (hands.distanceTo(cam) > 1.2) {
					sphere(pose, cam, SpiritBombRenderType.GLOW, hands, 0.35f + 0.1f * Mth.sin(t * 0.4f), 0.7f, 0.9f, 1f, 0.5f, 10, 0f, 0f);
				}
				any = true;
			}
		}
		for (Fly f : FLIES.values()) {
			drawBomb(pose, cam, f.pos().add(f.vel().scale(now - f.at())), f.radius(), t, 1f);
			any = true;
		}
		for (Timed f : FIZZLES) {
			float k = (float) Mth.clamp((now - f.at()) / 16.0, 0.0, 1.0);
			drawBomb(pose, cam, f.pos(), f.radius() * (1f - 0.45f * k), t, 1f - k);
			any = true;
		}
		for (Timed b : BLASTS) {
			drawBlast(pose, cam, b, now);
			any = true;
		}
		if (!MOTES.isEmpty()) {
			drawMotes(pose, cam, partial);
			any = true;
		}
		buffers = null;
		if (!any) {
			return;
		}
	}

	private static void drawBomb(PoseStack pose, Vec3 cam, Vec3 at, float r, float t, float fade) {
		if (r <= 0.01f || fade <= 0.01f) {
			return;
		}
		int seg = r < 2f ? 16 : r < 8f ? 24 : 32;
		double camDist = cam.distanceTo(at);
		// a pale blue body, a soft white heart, a blue skin of drifting light brightest at the rim
		sphere(pose, cam, SpiritBombRenderType.TINT, at, r * 0.97f, 0.42f, 0.7f, 1f, 0.26f * fade, seg, 0f, 0f, 0.6f);
		sphere(pose, cam, SpiritBombRenderType.GLOW, at, r * 0.55f, 0.8f, 0.92f, 1f, 0.22f * fade, seg, 0f, 0f, 0.6f);
		sphere(pose, cam, SpiritBombRenderType.GLOW, at, r, 0.3f, 0.6f, 1f, 0.42f * fade, seg, 0.45f, t, 0.85f);
		// three bands of energy rolling round the surface
		for (int i = 0; i < 3; i++) {
			float a = t * (0.021f + 0.007f * i) + i * 2.1f;
			Vec3 n = new Vec3(Math.cos(a) * Math.sin(1.1 + i), Math.cos(1.1 + i * 0.7), Math.sin(a) * Math.sin(1.1 + i)).normalize();
			ring(pose, cam, at, n, r * 1.015f, Math.max(0.05f, r * 0.04f), 0.65f, 0.85f, 1f, 0.22f * fade);
		}
		// soft bloom, skipped when the camera is inside a shell (it would only wash the whole screen out)
		float[] shells = { 1.12f, 1.3f, 1.6f };
		float[] alpha = { 0.12f, 0.055f, 0.028f };
		for (int i = 0; i < shells.length; i++) {
			float sr = r * shells[i];
			if (camDist > sr * 1.02) {
				sphere(pose, cam, SpiritBombRenderType.GLOW, at, sr, 0.35f, 0.65f, 1f, alpha[i] * fade, Math.max(14, seg - 8), 0f, 0f);
			}
		}
	}

	private static void drawBlast(PoseStack pose, Vec3 cam, Timed b, double now) {
		float age = (float) (now - b.at());
		float r = b.radius();
		if (age < 70f) {
			float grow = 1f - (float) Math.pow(1f - Mth.clamp(age / 10f, 0f, 1f), 3);
			float rad = r * (0.6f + 0.8f * grow);
			float a = age < 10f ? 1f : 1f - (age - 10f) / 60f;
			if (age < 8f) {
				sphere(pose, cam, SpiritBombRenderType.TINT, b.pos(), rad * 0.9f, 0.95f, 0.98f, 1f, 0.6f * (1f - age / 8f), 28, 0f, 0f);
			}
			sphere(pose, cam, SpiritBombRenderType.GLOW, b.pos(), rad * 0.6f, 1f, 1f, 1f, 0.7f * a, 28, 0f, 0f);
			sphere(pose, cam, SpiritBombRenderType.GLOW, b.pos(), rad, 0.5f, 0.78f, 1f, 0.45f * a, 32, 0.25f, age);
			if (cam.distanceTo(b.pos()) > rad * 1.27f) {
				sphere(pose, cam, SpiritBombRenderType.GLOW, b.pos(), rad * 1.25f, 0.55f, 0.8f, 1f, 0.15f * a, 24, 0f, 0f);
			}
		}
		if (age < 30f) {
			float k = age / 30f;
			float ease = 1f - (1f - k) * (1f - k);
			Vec3 ground = b.pos().add(0, 0.25, 0);
			ring(pose, cam, ground, new Vec3(0, 1, 0), r * (1f + 2.5f * ease), 1.5f + 0.25f * r * (1f - k), 0.75f, 0.9f, 1f, 0.6f * (1f - k));
			ring(pose, cam, b.pos().add(0, r * 0.3, 0), new Vec3(0, 1, 0), r * (0.8f + 1.6f * ease), 0.8f + 0.1f * r, 0.85f, 0.95f, 1f,
					0.3f * (1f - k));
		}
	}

	private static void drawMotes(PoseStack pose, Vec3 cam, float partial) {
		VertexConsumer vc = buffers.getBuffer(SpiritBombRenderType.GLOW);
		pose.pushPose();
		Matrix4f m = pose.last().pose();
		for (Mote mo : MOTES) {
			float fade = mo.outward ? 1f - mo.age / 20f : Mth.clamp(mo.age / 6f, 0f, 1f);
			Vec3 head = new Vec3(mo.x + mo.vx * partial, mo.y + mo.vy * partial, mo.z + mo.vz * partial);
			Vec3 tail = head.subtract(mo.vx * 3.0, mo.vy * 3.0, mo.vz * 3.0);
			Vec3 axis = head.subtract(tail);
			Vec3 view = head.subtract(cam);
			double near = view.length();
			if (near < 4.0) {
				continue; // right by the camera a streak is a white bar across the screen
			}
			Vec3 side = axis.cross(view);
			if (side.lengthSqr() < 1.0e-8) {
				continue;
			}
			side = side.normalize().scale(0.07);
			Vec3 h = head.subtract(cam), tl = tail.subtract(cam);
			quad(vc, m, v(h.add(side)), v(h.subtract(side)), v(tl.subtract(side)), v(tl.add(side)), 0.8f, 0.92f, 1f, 0.85f * fade);
		}
		pose.popPose();
	}

	private static Vector3f v(Vec3 a) {
		return new Vector3f((float) a.x, (float) a.y, (float) a.z);
	}

	private static void sphere(PoseStack pose, Vec3 cam, RenderType type, Vec3 at, float rad, float r, float g, float b, float a, int seg,
			float swirl, float t) {
		sphere(pose, cam, type, at, rad, r, g, b, a, seg, swirl, t, 0f);
	}

	/**
	 * A UV sphere; {@code swirl} > 0 shades its surface with slowly drifting light; {@code rim} > 0 makes it fainter where it faces
	 * the camera and brighter at the edge (an energy ball, not a solid disc -- and seen from right under it the screen does not
	 * go white).
	 */
	private static void sphere(PoseStack pose, Vec3 cam, RenderType type, Vec3 at, float rad, float r, float g, float b, float a, int seg,
			float swirl, float t, float rim) {
		if (rad <= 0.001f || a <= 0.002f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(type);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f toCam = new Vector3f((float) (cam.x - at.x), (float) (cam.y - at.y), (float) (cam.z - at.z));
		int rings = Math.max(4, seg / 2);
		Vector3f[] q = new Vector3f[4];
		float[] qa = new float[4];
		for (int i = 0; i < rings; i++) {
			float t0 = Mth.PI * i / rings - Mth.HALF_PI, t1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
			for (int j = 0; j < seg; j++) {
				float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
				float aa = a;
				if (swirl > 0f) {
					float n = Mth.sin(t0 * 5f + t * 0.13f) * Mth.sin(p0 * 4f - t * 0.09f) + 0.5f * Mth.sin(p0 * 9f + t0 * 7f + t * 0.21f);
					aa = a * (1f - swirl + swirl * (0.6f + 0.4f * n));
				}
				q[0] = sp(t0, p0, rad);
				q[1] = sp(t0, p1, rad);
				q[2] = sp(t1, p1, rad);
				q[3] = sp(t1, p0, rad);
				for (int k = 0; k < 4; k++) {
					qa[k] = aa;
					if (rim > 0f) {
						Vector3f d = new Vector3f(toCam).sub(q[k]);
						float len = d.length();
						float facing = len < 1.0e-4f ? 1f : Math.abs(d.dot(q[k]) / (len * rad));
						float edge = 1f - facing;
						qa[k] = aa * (1f - rim + rim * (0.25f + 1.1f * edge * edge));
					}
				}
				for (int k = 0; k < 4; k++) {
					vc.addVertex(m, q[k].x, q[k].y, q[k].z).setColor(r, g, b, Math.min(1f, qa[k]));
				}
			}
		}
		pose.popPose();
	}

	private static Vector3f sp(float lat, float lon, float rad) {
		return new Vector3f(Mth.cos(lat) * Mth.cos(lon) * rad, Mth.sin(lat) * rad, Mth.cos(lat) * Mth.sin(lon) * rad);
	}

	/** A flat band round {@code at}, its plane facing {@code normal}. */
	private static void ring(PoseStack pose, Vec3 cam, Vec3 at, Vec3 normal, float rad, float width, float r, float g, float b, float a) {
		if (rad <= 0.01f || a <= 0.002f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(SpiritBombRenderType.GLOW);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z).normalize();
		Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
		Vector3f w = new Vector3f(n).cross(u).normalize();
		int seg = 48;
		float in = Math.max(0f, rad - width);
		for (int i = 0; i < seg; i++) {
			float t0 = Mth.TWO_PI * i / seg, t1 = Mth.TWO_PI * (i + 1) / seg;
			Vector3f o0 = new Vector3f(u).mul(Mth.cos(t0)).add(new Vector3f(w).mul(Mth.sin(t0)));
			Vector3f o1 = new Vector3f(u).mul(Mth.cos(t1)).add(new Vector3f(w).mul(Mth.sin(t1)));
			quad(vc, m, new Vector3f(o0).mul(in), new Vector3f(o1).mul(in), new Vector3f(o1).mul(rad), new Vector3f(o0).mul(rad), r, g, b, a);
		}
		pose.popPose();
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float r, float g, float bl, float al) {
		vc.addVertex(m, a.x, a.y, a.z).setColor(r, g, bl, al);
		vc.addVertex(m, b.x, b.y, b.z).setColor(r, g, bl, al);
		vc.addVertex(m, c.x, c.y, c.z).setColor(r, g, bl, al);
		vc.addVertex(m, d.x, d.y, d.z).setColor(r, g, bl, al);
	}
}
