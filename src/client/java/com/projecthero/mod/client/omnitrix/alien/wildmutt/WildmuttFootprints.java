package com.projecthero.mod.client.omnitrix.alien.wildmutt;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Wildmutt revamp, Deep Sniff (Shift+V): the last {@link WildmuttKit#FOOTPRINT_TICKS} ticks (20 s) of footsteps of the
 * players and creatures around him, as faint paw / foot prints on the ground in their scent colour, fading with age. His client
 * alone records them (every creature within {@link #RECORD_RADIUS} it can see, while he is Wildmutt) and shows them while he
 * sniffs. They are drawn by {@link WildmuttVision} over its blind pass (which would otherwise swallow anything drawn in the
 * world), so -- like the scent outlines -- they show through walls.
 */
public final class WildmuttFootprints {
	/** Creatures this close are tracked; prints further than {@link #SHOW_RADIUS} from him are not drawn. */
	public static final double RECORD_RADIUS = 32.0, SHOW_RADIUS = 28.0;
	/** A print every this many blocks walked, alternating left / right. */
	private static final double STRIDE = 0.75;
	private static final int MAX_PRINTS = 900;

	private record Print(double x, double y, double z, float yaw, long born, float size, int colour) {
	}

	private static final ArrayDeque<Print> PRINTS = new ArrayDeque<>();
	/** Per entity id: where it last left a print {x, z, side}. */
	private static final Map<Integer, double[]> LAST = new HashMap<>();

	private WildmuttFootprints() {
	}

	/** Every client tick while this client is Wildmutt: who stepped where. */
	public static void record(LocalPlayer me) {
		long now = me.level().getGameTime();
		while (!PRINTS.isEmpty() && (now - PRINTS.peekFirst().born() > WildmuttKit.FOOTPRINT_TICKS || PRINTS.peekFirst().born() > now)) {
			PRINTS.pollFirst();
		}
		if (me.tickCount % 2 != 0) {
			return;
		}
		AABB box = me.getBoundingBox().inflate(RECORD_RADIUS, 8, RECORD_RADIUS);
		for (LivingEntity e : me.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != me && e.isAlive() && !(e instanceof ArmorStand))) {
			if (!e.onGround()) {
				continue;
			}
			double[] last = LAST.get(e.getId());
			if (last == null) {
				LAST.put(e.getId(), new double[] { e.getX(), e.getZ(), 0 });
				continue;
			}
			double dx = e.getX() - last[0], dz = e.getZ() - last[1];
			double stride = STRIDE * Math.max(0.7, e.getBbWidth() / 0.6);
			if (dx * dx + dz * dz < stride * stride) {
				continue;
			}
			if (dx * dx + dz * dz > 25) { // a teleport, not a step
				last[0] = e.getX();
				last[1] = e.getZ();
				continue;
			}
			double len = Math.sqrt(dx * dx + dz * dz);
			double side = last[2] > 0 ? -1 : 1;
			double off = e.getBbWidth() * 0.22;
			double px = e.getX() + (-dz / len) * off * side, pz = e.getZ() + (dx / len) * off * side;
			float yaw = (float) (Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
			PRINTS.addLast(new Print(px, e.getY() + 0.02, pz, yaw, now, Mth.clamp(e.getBbWidth() * 0.32f, 0.14f, 0.5f), colourOf(e)));
			last[0] = e.getX();
			last[1] = e.getZ();
			last[2] = side;
			if (PRINTS.size() > MAX_PRINTS) {
				PRINTS.pollFirst();
			}
		}
		if (LAST.size() > 512) {
			LAST.clear();
		}
	}

	private static int colourOf(LivingEntity e) {
		if (e instanceof net.minecraft.world.entity.monster.Enemy) {
			return WildmuttSense.MONSTER_COLOUR;
		}
		if (e instanceof net.minecraft.world.entity.player.Player) {
			return WildmuttSense.PLAYER_COLOUR;
		}
		if (e instanceof net.minecraft.world.entity.animal.Animal) {
			return WildmuttSense.ANIMAL_COLOUR;
		}
		return WildmuttSense.COLOUR;
	}

	/** Not Wildmutt any more / left the world: forget everything. */
	/**
	 * v0.17.2 (the roar's sound ring was swallowed by his blind pass): where and when his own Savage Roar's ping went out, so the
	 * ring is drawn again over the blind pass with the prints. {@code Double.NaN} = none.
	 */
	private static double pingX = Double.NaN, pingY, pingZ;
	private static long pingStart;
	/** The ring's reach (the ping's) and how long it takes to get there (ticks). */
	private static final float PING_RADIUS = 24f, PING_TICKS = 14f;

	/** His roar's ping just reached him (WildmuttSense's receiver): the ring starts at his feet. */
	public static void ping(LocalPlayer me) {
		pingX = me.getX();
		pingY = me.getY() + 0.1;
		pingZ = me.getZ();
		pingStart = me.level().getGameTime();
	}

	public static void clear() {
		pingX = Double.NaN;
		PRINTS.clear();
		LAST.clear();
	}

	/** Draws the prints (only while he sniffs), after the blind pass. */
	public static void draw(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (me == null || !(AlienKit.activeKit(me) instanceof WildmuttKit k)) {
			return;
		}
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = me.level().getGameTime() + pt;
		boolean prints = !PRINTS.isEmpty() && k.flag(me, WildmuttKit.F_SNIFF);
		float ringAge = Double.isNaN(pingX) ? 2f : (float) ((now - pingStart) / PING_TICKS);
		if (!prints && ringAge >= 1.4f) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		RenderSystem.setProjectionMatrix(ctx.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.identity();
		RenderSystem.applyModelViewMatrix();
		PoseStack pose = new PoseStack();
		pose.last().pose().mul(ctx.positionMatrix());
		pose.translate(-cam.x, -cam.y, -cam.z);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		if (ringAge < 1.4f) {
			drawRing(pose, buffers, ringAge);
		}
		if (!prints) {
			mv.popMatrix();
			RenderSystem.applyModelViewMatrix();
			return;
		}
		VertexConsumer vc = QuirkFxDraw.glow(buffers, QuirkFxDraw.DECALS[QuirkFx.DECAL_PRINT]);
		double show = SHOW_RADIUS * SHOW_RADIUS;
		Iterator<Print> it = PRINTS.iterator();
		while (it.hasNext()) {
			Print p = it.next();
			if (me.distanceToSqr(p.x(), p.y(), p.z()) > show) {
				continue;
			}
			float age = (float) ((now - p.born()) / WildmuttKit.FOOTPRINT_TICKS);
			float a = 0.75f * (1f - age) * (1f - age) * Math.min(1f, (float) (now - p.born()) / 6f);
			if (a <= 0.02f) {
				continue;
			}
			pose.pushPose();
			pose.translate(p.x(), p.y(), p.z());
			pose.mulPose(Axis.YP.rotationDegrees(-p.yaw()));
			float r = p.size(), l = r * 1.4f;
			int c = p.colour();
			QuirkFxDraw.gquad(vc, pose.last(), new float[][] { { -r, 0, -l, 0, 0 }, { -r, 0, l, 0, 1 }, { r, 0, l, 1, 1 }, { r, 0, -l, 1, 0 } },
					new int[] { c, c, c, c }, new float[] { a, a, a, a });
			pose.popPose();
		}
		buffers.endBatch(RenderType.eyes(QuirkFxDraw.DECALS[QuirkFx.DECAL_PRINT]));
		mv.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	/** The roar's ping ring: a pale amber band racing out flat along the ground to {@link #PING_RADIUS}, fading as it goes. */
	private static void drawRing(PoseStack pose, MultiBufferSource.BufferSource buffers, float age) {
		float k = Math.min(1f, age);
		float r = PING_RADIUS * (1f - (1f - k) * (1f - k));
		float w = 0.6f + 1.6f * k;
		float a = 0.85f * (1f - Math.max(0f, (age - 0.6f) / 0.8f));
		if (a <= 0.02f) {
			return;
		}
		com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType.STAGE);
		org.joml.Matrix4f m = pose.last().pose();
		int seg = 72;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float x = (float) pingX, y = (float) pingY, z = (float) pingZ;
			// inner edge clear, the middle bright, the outer edge clear
			ring(vc, m, x, y, z, c0, s0, c1, s1, r - w, r, 0f, a);
			ring(vc, m, x, y, z, c0, s0, c1, s1, r, r + w * 0.5f, a, 0f);
		}
		buffers.endBatch(com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType.STAGE);
	}

	private static void ring(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x, float y, float z, float c0, float s0, float c1,
			float s1, float rIn, float rOut, float aIn, float aOut) {
		rIn = Math.max(0f, rIn);
		vert(vc, m, x + c0 * rIn, y, z + s0 * rIn, aIn);
		vert(vc, m, x + c1 * rIn, y, z + s1 * rIn, aIn);
		vert(vc, m, x + c1 * rOut, y, z + s1 * rOut, aOut);
		vert(vc, m, x + c0 * rOut, y, z + s0 * rOut, aOut);
	}

	private static void vert(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x, float y, float z, float a) {
		vc.addVertex(m, x, y, z).setColor(0xFF, 0xC0, 0x5A, Math.max(0, Math.min(255, (int) (a * 255f))));
	}
}
