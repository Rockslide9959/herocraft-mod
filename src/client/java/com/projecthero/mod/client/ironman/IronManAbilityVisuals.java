package com.projecthero.mod.client.ironman;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ironman.IronManAbilityFx;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.26: real geometry for the Iron Man abilities, drawn in the world for everyone who can see the wearer:
 * <ul>
 *   <li><b>Repulsor / charged repulsor / Unibeam / wrist laser</b> -- glowing beams (soft outer glow + additive hot core)
 *       with a flare ring at the muzzle, fading out over a few ticks, instead of particle lines.</li>
 *   <li><b>Repulsor Shield</b> -- a hexagonal energy shield in front of the palms (a full-body sphere of hex cells on the
 *       Mark VII), rippling.</li>
 *   <li><b>Flamethrower</b> -- a flickering cone of flame from the wrist.</li>
 *   <li><b>Strong punch</b> -- an expanding shockwave ring off the fist.</li>
 * </ul>
 * Glow quads go into {@code debugQuads}, cores into {@code lightning}; every glow is drawn before any core (asking a
 * batched buffer for a second type closes the first -- see {@link BeamDraw}).
 */
public final class IronManAbilityVisuals {
	private record Beam(Vec3 a, Vec3 b, int kind, long born) {
	}

	private static final List<Beam> BEAMS = new ArrayList<>();

	private IronManAbilityVisuals() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(IronManAbilityVisuals::render);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> BEAMS.clear());
	}

	/** Called by {@code IronManBeamClient} for every beam packet. */
	public static void addBeam(Vec3 a, Vec3 b, int kind) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (BEAMS.size() > 64) {
			BEAMS.remove(0);
		}
		BEAMS.add(new Beam(a, b, kind, mc.level.getGameTime()));
	}

	private static int life(int kind) {
		return switch (kind) {
			case 1 -> 9;
			case 2 -> 3;
			case 3 -> 2;
			default -> 6;
		};
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		MultiBufferSource buffers = ctx.consumers();
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = mc.level.getGameTime() + pt;
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();

		Iterator<Beam> it = BEAMS.iterator();
		while (it.hasNext()) {
			Beam b = it.next();
			if (now - b.born() > life(b.kind()) + 1) {
				it.remove();
			}
		}
		List<Player> users = new ArrayList<>();
		for (Player p : mc.level.players()) {
			if (IronManAbilityPose.current(p) != null) {
				users.add(p);
			}
		}

		for (int pass = 0; pass < 2; pass++) {
			boolean glow = pass == 0;
			VertexConsumer vc = buffers.getBuffer(glow ? RenderType.debugQuads() : RenderType.lightning());
			for (Beam b : BEAMS) {
				drawBeam(vc, glow, last, b, now, cam);
			}
			for (Player p : users) {
				IronManAbilityPose.Play play = IronManAbilityPose.current(p);
				if (play == null) {
					continue;
				}
				float age = IronManAbilityPose.age(play);
				switch (play.anim()) {
					case IronManAbilityFx.BARRIER -> shield(vc, glow, last, p, pt, now, cam);
					case IronManAbilityFx.FLAME -> flame(vc, glow, last, p, pt, now, cam);
					case IronManAbilityFx.PUNCH -> punch(vc, glow, last, p, pt, age, cam);
					default -> {
					}
				}
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- beams

	private static void drawBeam(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Beam b, float now, Vec3 cam) {
		float age = now - b.born();
		float fade = Mth.clamp(1f - age / life(b.kind()), 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		float width;
		int colour;
		switch (b.kind()) {
			case 1 -> {
				width = 0.34f;
				colour = 0xBFF4FF;
			}
			case 2 -> {
				width = 0.55f;
				colour = 0xD8F8FF;
				fade = 1f;
			}
			case 3 -> {
				width = 0.07f;
				colour = 0xFF2A2A;
				fade = 1f;
			}
			default -> {
				width = 0.2f;
				colour = 0x9FE8FF;
			}
		}
		// the beam thins as it fades
		float w = width * (0.5f + 0.5f * fade);
		BeamDraw.beam(vc, glow, pose, b.a(), b.b(), cam, w, glow ? colour : 0xFFFFFF, fade);
		// a flare ring at the palm / chest
		if (b.kind() != 3) {
			ring(vc, glow, pose, b.a(), b.b().subtract(b.a()).normalize(), width * (1.2f + (1f - fade) * 1.5f), glow ? colour : 0xFFFFFF,
					fade * 0.9f, cam, 16);
		}
	}

	// ---------------------------------------------------------------- shield

	private static void shield(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Player p, float pt, float now, Vec3 cam) {
		Vec3 look = p.getViewVector(pt);
		Vec3 base = p.getPosition(pt);
		boolean full = isFullBodyShield(p);
		float pulse = 0.85f + 0.15f * Mth.sin(now * 0.5f);
		if (full) {
			// Mark VII: a full-body sphere of hex cells -- drawn as latitude / longitude rings
			Vec3 c = base.add(0, p.getBbHeight() * 0.5, 0);
			double r = 1.7;
			for (int i = 1; i < 6; i++) {
				double lat = -Math.PI / 2 + Math.PI * i / 6;
				ring(vc, glow, pose, c.add(0, Math.sin(lat) * r, 0), new Vec3(0, 1, 0), (float) (Math.cos(lat) * r), 0x7FDFFF,
						0.45f * pulse, cam, 28);
			}
			for (int i = 0; i < 6; i++) {
				double lon = Math.PI * i / 6 + now * 0.02;
				ring(vc, glow, pose, c, new Vec3(Math.cos(lon), 0, Math.sin(lon)), (float) r, 0x7FDFFF, 0.35f * pulse, cam, 28);
			}
			return;
		}
		Vec3 c = base.add(0, p.getBbHeight() * 0.55, 0).add(look.scale(1.1));
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		double r = 1.35;
		// the hexagon's rim and spokes, and a smaller inner hexagon
		Vec3[] outer = hex(c, right, up, r, now * 0.01);
		Vec3[] inner = hex(c, right, up, r * 0.5, -now * 0.015);
		for (int i = 0; i < 6; i++) {
			BeamDraw.segment(vc, pose, outer[i], outer[(i + 1) % 6], cam, 0.06f, 0.06f, glow ? 0x7FDFFF : 0xE0FFFF, 0.8f * pulse, 0.8f * pulse);
			BeamDraw.segment(vc, pose, inner[i], inner[(i + 1) % 6], cam, 0.04f, 0.04f, glow ? 0x7FDFFF : 0xE0FFFF, 0.6f * pulse, 0.6f * pulse);
			BeamDraw.segment(vc, pose, inner[i], outer[i], cam, 0.025f, 0.025f, glow ? 0x7FDFFF : 0xE0FFFF, 0.5f * pulse, 0.5f * pulse);
		}
		if (glow) {
			// the faint fill: wide soft spokes from the centre
			for (int i = 0; i < 6; i++) {
				BeamDraw.segment(vc, pose, c, outer[i].add(outer[(i + 1) % 6]).scale(0.5), cam, 0.55f, 0.55f, 0x40C8FF, 0.16f * pulse, 0.05f);
			}
		}
	}

	private static Vec3[] hex(Vec3 c, Vec3 right, Vec3 up, double r, double turn) {
		Vec3[] out = new Vec3[6];
		for (int i = 0; i < 6; i++) {
			double a = turn + i * Math.PI / 3;
			out[i] = c.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
		}
		return out;
	}

	private static boolean isFullBodyShield(Player p) {
		var chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem();
		if (chest instanceof com.projecthero.mod.ironman.item.IronManArmorItem piece) {
			var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(piece.suitId());
			return suit != null && suit.fullBodyShield();
		}
		return false;
	}

	// ---------------------------------------------------------------- flame

	private static void flame(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Player p, float pt, float now, Vec3 cam) {
		Vec3 look = p.getViewVector(pt);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 nozzle = p.getEyePosition(pt).add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.35, 0);
		int tongues = 5;
		for (int i = 0; i < tongues; i++) {
			double jitter = Math.sin(now * 1.7 + i * 2.3) * 0.12;
			double len = 5.0 + Math.sin(now * 2.1 + i) * 0.8;
			Vec3 dir = look.add(right.scale(jitter)).add(0, Math.cos(now * 1.3 + i) * 0.06, 0).normalize();
			Vec3 end = nozzle.add(dir.scale(len));
			if (glow) {
				BeamDraw.segment(vc, pose, nozzle, end, cam, 0.05f, 0.42f, i % 2 == 0 ? 0xFF7A1A : 0xFFB030, 0.4f, 0.0f);
			} else {
				BeamDraw.segment(vc, pose, nozzle, nozzle.add(dir.scale(len * 0.55)), cam, 0.03f, 0.16f, 0xFFE070, 0.8f, 0.0f);
			}
		}
	}

	// ---------------------------------------------------------------- punch

	private static void punch(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Player p, float pt, float age, Vec3 cam) {
		if (age < 3f) {
			return; // the wind-up
		}
		float k = Mth.clamp((age - 3f) / 6f, 0f, 1f);
		Vec3 look = p.getViewVector(pt);
		Vec3 fist = p.getEyePosition(pt).add(look.scale(1.3)).add(0, -0.4, 0);
		float fade = 1f - k;
		ring(vc, glow, pose, fist.add(look.scale(k * 0.8)), look, 0.2f + k * 1.5f, glow ? 0x9FE8FF : 0xFFFFFF, fade, cam, 24);
		ring(vc, glow, pose, fist.add(look.scale(k * 0.4)), look, 0.1f + k * 0.9f, glow ? 0xFFC24A : 0xFFFFFF, fade * 0.7f, cam, 20);
	}

	// ---------------------------------------------------------------- shared

	/** A camera-facing-segment ring of {@code n} pieces round {@code centre}, in the plane normal to {@code normal}. */
	private static void ring(VertexConsumer vc, boolean glow, PoseStack.Pose pose, Vec3 centre, Vec3 normal, float radius, int colour,
			float alpha, Vec3 cam, int n) {
		if (alpha <= 0.01f || radius <= 0.01f) {
			return;
		}
		Vec3 nrm = normal.normalize();
		Vec3 a = nrm.cross(new Vec3(0, 1, 0));
		if (a.lengthSqr() < 1.0e-6) {
			a = nrm.cross(new Vec3(1, 0, 0));
		}
		a = a.normalize();
		Vec3 b = nrm.cross(a).normalize();
		float w = glow ? Math.max(0.04f, radius * 0.08f) : Math.max(0.015f, radius * 0.025f);
		Vec3 prev = null;
		for (int i = 0; i <= n; i++) {
			double t = i * Math.PI * 2 / n;
			Vec3 pt = centre.add(a.scale(Math.cos(t) * radius)).add(b.scale(Math.sin(t) * radius));
			if (prev != null) {
				BeamDraw.segment(vc, pose, prev, pt, cam, w, w, colour, alpha, alpha);
			}
			prev = pt;
		}
	}

	/** Entities by id, for callers holding only an id. */
	static Entity byId(int id) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? null : mc.level.getEntity(id);
	}
}
