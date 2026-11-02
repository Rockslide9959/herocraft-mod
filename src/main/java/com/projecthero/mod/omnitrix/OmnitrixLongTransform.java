package com.projecthero.mod.omnitrix;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 (user: "this is the fourarms transformation from the ben 10 show, can you try and recreate this in minecraft ... make
 * this long transformation sequence play when the player presses right click on the dial instead of left click"): the long,
 * show-accurate transformation. A RIGHT-click on the raised dial ({@link Omnitrix#slap(ServerPlayer, boolean)}) starts it for an
 * alien that has one (Four Arms so far); the left-click is still the quick slap. It is a timed sequence on the synced
 * {@link #TIMER} (its start tick), cut 1:1 to the episode's soundtrack ({@code four_arms_sequence.ogg}, the user's clip from the
 * slap to the green flash back into the world; video second {@code v} is tick {@code (v - 2.55) * 20}):
 * <ul>
 *   <li>the wearer is rooted (no walking, no jumping, no keys) for the whole {@link #TOTAL} ticks;</li>
 *   <li>the body actually changes at {@link #TRANSFORM_AT}, under the golden flash, and the 3-minute clock starts when the
 *       sequence ends;</li>
 *   <li>sneaking skips it (the quick transformation then, or straight to the end if already changed);</li>
 *   <li>death, logout, a dimension change, suppression or a revert cut it off (the music stops).</li>
 * </ul>
 * The client half ({@code OmnitrixSequenceClient}) is the show: the camera, the swelling, the stage, the flashes.
 */
public final class OmnitrixLongTransform {
	/** The sequence's start tick (synced: every client plays it from here). */
	public static final String TIMER = "omnitrix.long";
	/** The way the wearer faced when it started (float bits): the body is held to it and the camera moves round it. */
	public static final String YAW = "omnitrix.long_yaw";
	/** The body changes here (video 7.3 s, under the golden flash). */
	public static final int TRANSFORM_AT = 95;
	/** The end: the green flash has faded back into the world (video 15.6 s). */
	public static final int TOTAL = 261;
	/** Sneaking skips from here (a sneak held from before the click does not cut it at once). */
	public static final int SKIP_FROM = 10;

	private static final ResourceLocation ROOT_ID = ProjectHeroMod.id("omnitrix_long_root");
	private static final ResourceLocation JUMP_ID = ProjectHeroMod.id("omnitrix_long_jump");

	private OmnitrixLongTransform() {
	}

	/** Whether {@code alienId} has a long transformation. */
	public static boolean has(String alienId) {
		return Aliens.FOUR_ARMS.id().equals(alienId);
	}

	/** Ticks into {@code p}'s long transformation (with {@code partial}), or -1 when none is playing. Client-safe. */
	public static float age(Player p, float partial) {
		if (!Omnitrix.hasPower(p)) {
			return -1f;
		}
		OmnitrixState s = Omnitrix.state(p);
		Long start = s.timers.get(TIMER);
		if (start == null) {
			return -1f;
		}
		float t = p.level().getGameTime() - start + partial;
		// a client clock a little behind the server's reads as the start
		if (t < 0f && t > -40f) {
			t = 0f;
		}
		return t < 0f || t >= TOTAL ? -1f : t;
	}

	public static boolean playing(Player p) {
		return age(p, 0f) >= 0f;
	}

	/** The way the wearer faced at the start. */
	public static float yaw(OmnitrixState s) {
		Long bits = s.timers.get(YAW);
		return bits == null ? 0f : Float.intBitsToFloat((int) (long) bits);
	}

	/** Starts it (the right-click slap has passed every check). */
	static void start(ServerPlayer p, OmnitrixState n, long now) {
		n.timers.put(TIMER, now);
		n.timers.put(YAW, Float.floatToIntBits(p.getYRot()) & 0xFFFFFFFFL);
		root(p, true);
		OmnitrixSounds.playOn(p, OmnitrixSounds.FOUR_ARMS_SEQUENCE);
	}

	/** Every server tick while it plays (before anything else of the watch's). */
	static void tick(ServerPlayer p, OmnitrixState s, long now) {
		long t = now - s.readyAt(TIMER);
		if (t < 0 || t > TOTAL + 40) {
			finish(p, true);
			return;
		}
		if (t >= SKIP_FROM && p.isShiftKeyDown()) {
			boolean changed = s.transformed();
			finish(p, true);
			if (!changed) {
				Omnitrix.transform(p);
			}
			return;
		}
		if (!s.transformed() && t >= TRANSFORM_AT) {
			if (!Omnitrix.transform(p)) {
				finish(p, true);
				OmnitrixState n = Omnitrix.state(p).copy();
				n.selecting = false;
				Omnitrix.save(p, n);
				return;
			}
		}
		if (t >= TOTAL) {
			finish(p, false);
			return;
		}
		// rooted: no drifting off the mark the camera is circling
		p.setSprinting(false);
	}

	/** Ends it now (and silences the music if {@code cut}). Safe to call when none is playing. */
	public static void finish(ServerPlayer p, boolean cut) {
		root(p, false);
		if (!Omnitrix.hasPower(p)) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		if (!s.timers.containsKey(TIMER)) {
			return;
		}
		OmnitrixState n = s.copy();
		n.timers.remove(TIMER);
		n.timers.remove(YAW);
		Omnitrix.save(p, n);
		if (cut) {
			var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(OmnitrixSounds.FOUR_ARMS_SEQUENCE_SOUND.getLocation(),
					SoundSource.PLAYERS);
			for (ServerPlayer o : p.serverLevel().players()) {
				if (o == p || o.distanceToSqr(p) < 64 * 64) {
					o.connection.send(stop);
				}
			}
		}
	}

	/** Whether the root is on {@code p} (gametests). */
	public static boolean rooted(ServerPlayer p) {
		var a = p.getAttribute(Attributes.MOVEMENT_SPEED);
		return a != null && a.getModifier(ROOT_ID) != null;
	}

	private static void root(ServerPlayer p, boolean on) {
		if (on) {
			var op = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			com.projecthero.mod.hero.power.PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ROOT_ID, -1.0, op);
			com.projecthero.mod.hero.power.PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, -1.0, op);
			p.setSprinting(false);
		} else {
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ROOT_ID);
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, JUMP_ID);
		}
	}
}
