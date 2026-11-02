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
	public static void clear() {
		PRINTS.clear();
		LAST.clear();
	}

	/** Draws the prints (only while he sniffs), after the blind pass. */
	public static void draw(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (me == null || PRINTS.isEmpty() || !(AlienKit.activeKit(me) instanceof WildmuttKit k) || !k.flag(me, WildmuttKit.F_SNIFF)) {
			return;
		}
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = me.level().getGameTime() + pt;
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
}
