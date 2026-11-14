package com.projecthero.mod.client.stand;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.TimeStop;
import com.projecthero.mod.stand.network.TimeStopPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Star Platinum: The World, on this client ({@link TimeStopPayload}).
 * <ul>
 *   <li><b>A Stand user</b> sees the stop: every entity but the caster (and its mount, and its projectiles' first ticks)
 *       holds still, particles hang in the air and the colour drains to a cold near-grey ({@code shaders/post/stand_time_stop.json},
 *       vanilla's {@code color_convolve}).</li>
 *   <li><b>Anyone else</b> perceives nothing: EVERYTHING freezes, the caster too, their own view and keys included, with no
 *       cue at all -- when time resumes the world simply jumps (the user's rule: non-Stand users can't perceive a Stand).</li>
 * </ul>
 * v0.17.4: players the server lists as mobile ({@link TimeStopPayload#mobile}: other Star Platinum users free, XLR8 and Speed
 * Force speedsters at sprint pace, the Super Speed mutation at walking pace) are never frozen on a perceiving client. On their own
 * client they keep their camera and movement (a speedster's capped by {@link #speedCap}, their action keys held back), and they
 * perceive the stop like a Stand user does (the world frozen); only Stand users get the colour drain.
 * A frozen entity skips its client tick ({@code SuperSpeedClientLevelMixin}) and renders at partial tick 0
 * ({@code StandTimeStopRenderMixin}), so nothing jitters between frames.
 */
public final class TimeStopClient {
	private static int casterId = -1;
	private static String dimension = "";
	private static long end;
	private static double radius;
	private static Vec3 centre = Vec3.ZERO;
	/** v0.17.4: entity id -&gt; {@link TimeStop.Mobility} of the players the stop doesn't freeze. */
	private static final java.util.Map<Integer, TimeStop.Mobility> MOBILE = new java.util.concurrent.ConcurrentHashMap<>();

	private static PostChain chain;
	private static boolean failed;
	private static int width;
	private static int height;

	private TimeStopClient() {
	}

	static void receive(TimeStopPayload p) {
		casterId = p.casterId();
		dimension = p.dimension();
		end = p.end();
		radius = p.radius();
		centre = new Vec3(p.x(), p.y(), p.z());
		MOBILE.clear();
		TimeStop.Mobility[] levels = TimeStop.Mobility.values();
		for (int m : p.mobile()) {
			MOBILE.put(m >> 2, levels[Math.min(levels.length - 1, m & 3)]);
		}
	}

	static void reset() {
		end = 0L;
		casterId = -1;
		MOBILE.clear();
		if (chain != null) {
			chain.close();
			chain = null;
		}
		failed = false;
	}

	/** A stop is running in this client's dimension. */
	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		if (end <= 0L || mc.level == null || !mc.level.dimension().location().toString().equals(dimension)) {
			return false;
		}
		if (mc.level.getGameTime() > end + 60L) {
			end = 0L; // the resume packet never came: let go
			return false;
		}
		return true;
	}

	/** This viewer is a Stand user (sees the caster move and the colour drain). */
	public static boolean seer() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && Stands.isStandUser(mc.player);
	}

	/** v0.17.4: how this entity moves in the running stop on this client (FROZEN unless the server listed it). */
	public static TimeStop.Mobility mobility(Entity e) {
		return e == null ? TimeStop.Mobility.FROZEN : MOBILE.getOrDefault(e.getId(), TimeStop.Mobility.FROZEN);
	}

	/** v0.17.4: the local player moves through the stop (a free Star Platinum user or a speedster). */
	public static boolean localMobile() {
		Minecraft mc = Minecraft.getInstance();
		return active() && mc.player != null && mobility(mc.player) != TimeStop.Mobility.FROZEN;
	}

	/** v0.17.4: the local player is a speedster moving through the stop: movement only, no attack / use / inventory keys. */
	public static boolean localLimited() {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.player == null || radius > 0.0 && mc.player.position().distanceToSqr(centre) > radius * radius) {
			return false;
		}
		TimeStop.Mobility m = mobility(mc.player);
		return m == TimeStop.Mobility.WALK || m == TimeStop.Mobility.SPRINT;
	}

	/** v0.17.4 ({@link TimeStop#clientCap}): the local speedster's pace in blocks a tick, 0 = none. */
	public static double speedCap(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (e != mc.player || !localLimited()) {
			return 0.0;
		}
		return TimeStop.capFor(mobility(e));
	}

	/** v0.17.4: this viewer perceives the stop (sees the caster and the mobile players move): Stand users and mobile players. */
	public static boolean perceives() {
		return seer() || localMobile();
	}

	public static int casterId() {
		return casterId;
	}

	/** v0.17.3: the local player is the one holding time still (the HUD lights Shift+Z: pressing it again resumes time). */
	public static boolean casterIsLocal() {
		Minecraft mc = Minecraft.getInstance();
		return active() && mc.player != null && mc.player.getId() == casterId;
	}

	/** Is this entity held still on this client? */
	public static boolean frozen(Entity e) {
		if (!active()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (e.level() != mc.level) {
			return false;
		}
		if (radius > 0.0 && e.position().distanceToSqr(centre) > radius * radius) {
			return false;
		}
		if (!perceives()) {
			return true; // a non-Stand user: everything, the caster too
		}
		if (e.getId() == casterId || mobility(e) != TimeStop.Mobility.FROZEN) {
			return false; // (v0.17.4: and the players who move through it)
		}
		Entity caster = mc.level.getEntity(casterId);
		if (caster != null && (e == caster.getRootVehicle() || e.getRootVehicle() == caster.getRootVehicle())) {
			return false;
		}
		return !(e instanceof Projectile p && p.tickCount < TimeStop.PROJECTILE_FREE_TICKS && p.getOwner() != null
				&& (p.getOwner().getId() == casterId || mobility(p.getOwner()) == TimeStop.Mobility.FREE));
	}

	/** The local player is frozen (no turning, no keys). */
	public static boolean localFrozen() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && frozen(mc.player);
	}

	/** The colour drain, for Stand users only (run at the end of world rendering, like Ladybug Vision). */
	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || failed || !active() || !seer()) {
			return;
		}
		if (chain == null) {
			try {
				chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
						ProjectHeroMod.id("shaders/post/stand_time_stop.json"));
			} catch (Exception e) {
				ProjectHeroMod.LOGGER.warn("[stand] time stop post effect failed to load", e);
				failed = true;
				return;
			}
			width = -1;
		}
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		if (w != width || h != height) {
			chain.resize(w, h);
			width = w;
			height = h;
		}
		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		chain.process(context.tickCounter().getGameTimeDeltaTicks());
		mc.getMainRenderTarget().bindWrite(true);
		RenderSystem.enableDepthTest();
	}
}
