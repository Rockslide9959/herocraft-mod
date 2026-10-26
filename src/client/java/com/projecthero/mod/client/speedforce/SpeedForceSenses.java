package com.projecthero.mod.client.speedforce;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.speedforce.SpeedForceColours;
import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user): the Speed Force Speedster's senses -- client-side and the runner's own (the v0.15.4 privacy rule: nothing
 * is sent, nothing set on the mob; the outline is answered from {@code Minecraft#shouldEntityAppearGlowing}, which only
 * this client's renderer asks, and the colour only for client-side entities).
 * <ul>
 *   <li><b>Danger sense</b>: from {@link SpeedForceConfig#DANGER_MOMENTUM} momentum, hostiles ahead along the run within
 *       {@link SpeedForceConfig#DANGER_RANGE} blocks are outlined for {@link SpeedForceConfig#DANGER_MARK_TICKS} after they
 *       are seen there.</li>
 *   <li><b>Flash Time vision</b>: while the runner's own Flash Time holds, hostiles near them show a faint line, in the
 *       lightning colour, of where they are heading -- read from how they have been moving (their AI target isn't synced).</li>
 * </ul>
 */
public final class SpeedForceSenses {
	/** entity id -> the game time its danger mark lasts until. */
	private static final Map<Integer, Long> MARKED = new HashMap<>();
	/** entity id -> its smoothed per-tick motion (Flash Time vision), and where it was last tick. */
	private static final Map<Integer, Vec3> VELOCITY = new HashMap<>();
	private static final Map<Integer, Vec3> LAST_POS = new HashMap<>();

	private SpeedForceSenses() {
	}

	static void reset() {
		MARKED.clear();
		VELOCITY.clear();
		LAST_POS.clear();
	}

	static void tick(Minecraft mc) {
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null || !SpeedForceSpeedster.speedActive(me)) {
			reset();
			return;
		}
		long now = mc.level.getGameTime();
		MARKED.values().removeIf(until -> until < now);
		if (SpeedForceSpeedster.momentum(me) >= SpeedForceConfig.DANGER_MOMENTUM) {
			Vec3 run = new Vec3(me.getX() - me.xo, 0, me.getZ() - me.zo);
			if (run.lengthSqr() < 0.04) {
				Vec3 look = me.getLookAngle();
				run = new Vec3(look.x, 0, look.z);
			}
			if (run.lengthSqr() > 1.0e-6) {
				Vec3 dir = run.normalize();
				double r = SpeedForceConfig.DANGER_RANGE;
				for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(r, 16, r), e -> e instanceof Enemy && e.isAlive())) {
					Vec3 to = new Vec3(e.getX() - me.getX(), 0, e.getZ() - me.getZ());
					double d = to.length();
					if (d <= r && d > 0.5 && to.scale(1.0 / d).dot(dir) >= SpeedForceConfig.DANGER_CONE) {
						MARKED.put(e.getId(), now + SpeedForceConfig.DANGER_MARK_TICKS);
					}
				}
			}
		}
		// Flash Time vision: follow how the hostiles near the runner move
		if (flashTime(me)) {
			double r = SpeedForceConfig.FLASH_VISION_RANGE;
			java.util.Set<Integer> seen = new java.util.HashSet<>();
			for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(r), e -> e instanceof Enemy && e instanceof LivingEntity && e.isAlive())) {
				seen.add(e.getId());
				Vec3 pos = e.position();
				Vec3 last = LAST_POS.put(e.getId(), pos);
				if (last != null && last.distanceToSqr(pos) < 4.0) {
					Vec3 step = pos.subtract(last);
					Vec3 v = VELOCITY.get(e.getId());
					VELOCITY.put(e.getId(), v == null ? step : v.scale(0.6).add(step.scale(0.4)));
				}
			}
			LAST_POS.keySet().retainAll(seen);
			VELOCITY.keySet().retainAll(seen);
		} else {
			VELOCITY.clear();
			LAST_POS.clear();
		}
	}

	private static boolean flashTime(LocalPlayer me) {
		SpeedForceState s = SpeedForceSpeedster.peek(me);
		return s != null && s.flashLeft == SpeedForceState.Z_FLASH_TIME;
	}

	/** How many hostiles the danger sense is marking right now (the visor's chip). */
	public static int markedCount() {
		return MARKED.size();
	}

	/** Danger sense: should {@code e} be outlined for the local runner? */
	public static boolean marked(Entity e) {
		Long until = MARKED.get(e.getId());
		Minecraft mc = Minecraft.getInstance();
		return until != null && mc.level != null && e.level() == mc.level && until >= mc.level.getGameTime() && e.isAlive();
	}

	/** The predicted paths, faint ribbons in the runner's lightning colour (from {@code SpeedForceClient}'s world render). */
	static void renderPaths(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || VELOCITY.isEmpty() || !flashTime(mc.player)) {
			return;
		}
		int colour = SpeedForceColours.of(mc.player);
		int body = SpeedForceColours.tone(colour, 2);
		int core = SpeedForceColours.tone(colour, 3);
		float alpha = SpeedForceConfig.FLASH_VISION_ALPHA;
		for (Iterator<Map.Entry<Integer, Vec3>> it = VELOCITY.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Vec3> en = it.next();
			Entity e = mc.level.getEntity(en.getKey());
			if (e == null) {
				it.remove();
				continue;
			}
			Vec3 v = new Vec3(en.getValue().x, 0, en.getValue().z);
			if (v.lengthSqr() < 0.0004) {
				continue; // standing still
			}
			Vec3 from = e.getPosition(partial).add(0, 0.15, 0);
			int n = 8;
			Vec3[] path = new Vec3[n + 1];
			for (int i = 0; i <= n; i++) {
				path[i] = from.add(v.scale(SpeedForceConfig.FLASH_VISION_TICKS * i / (double) n)).subtract(cam);
			}
			ThorDraw.ribbon(vc, pose, path, 0.05f, body, alpha);
			ThorDraw.ribbon(vc, pose, path, 0.015f, core, alpha * 1.5f);
		}
	}
}
