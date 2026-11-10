package com.projecthero.mod.undead;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user): Hold X (2 s) -- Head Throw. "player rips their head off and holds in behind their shoulder with one arm while
 * the other arm points where the player is aiming (kinda like how you would throw a baseball), upon releasing X the player
 * throws their head around 25 blocks, show like a trajectory line for where the head will probably land ... players can throw
 * their heads into 1 block gaps and regenerate on the other side, players body only starts to regenerate once they touch a block
 * but regeneration can be started early by right clicking while still midair, player cant move while aiming their head".
 *
 * <p>The contract with the X key routing: {@link #windUp} every tick of the hold before it completes (the struggle to tear it
 * off), {@link #start} when the 2 s hold completes (the head comes off and is held back: aiming), {@link #tickHeld} every tick
 * while X stays held after that, {@link #release} when X is let go (the throw), {@link #cancel} if the hold is interrupted.
 *
 * <ul>
 *   <li><b>Aiming</b> ({@link #aiming}): the head is held behind the shoulder, the other arm points along the look; he is rooted
 *       (no walking: UndeadHeadThrowInputMixin holds the keys; no jumping: a jump modifier). Every client draws the pose; the local one also draws the dotted
 *       arc {@link #predict} works out -- the same physics as the flight -- to where the head will land.</li>
 *   <li><b>The throw</b>: every part but the head breaks ({@code KIND_LAUNCHED}) and is left behind where he stood, falling as
 *       one; the player is moved to the held head and flies with it -- he IS the head, the camera with it (the 0.5 rolling-head
 *       box, {@link UndeadStages#ROLL}, so it fits through 1-block gaps). The flight is a scripted ballistic arc: the server sets
 *       and sends the velocity every tick ({@link UndeadBulletConfig#HEAD_GRAVITY}, no drag), the client moves the player.</li>
 *   <li><b>Regrowth</b> waits ({@link #holdsRegen}, read by {@link Undead#tick}) until the head touches a block, or until he
 *       right-clicks mid-air ({@link #regrowNow}, from {@code UndeadHeadThrowPayload}); then the body grows back around the head
 *       wherever it is, by the usual staged regrowth.</li>
 * </ul>
 * Every map here is dropped by {@link #clearSessionState} and {@link #forget}.
 */
public final class UndeadHeadThrow {
	/** Synced as {@link UndeadState#animId}: the struggle to tear the head off, holding it back to throw, and in the air. */
	public static final int ANIM_RIP = 90;
	public static final int ANIM_AIM = 91;
	public static final int ANIM_THROWN = 92;
	/** The thrown head's box: the rolling head's ({@link UndeadStages}), half a block. */
	public static final double HEAD_SIZE = 0.5;

	private static final ResourceLocation ROOT_JUMP_ID = PowerToggles.id("undead_head_throw_root_jump");

	/** A head in the air: its velocity this tick, when it left, whether the body may already grow, where it was last tick. */
	private static final class Flight {
		Vec3 vel;
		final long start;
		boolean regen;
		Vec3 last;
		int still;

		Flight(Vec3 vel, long start, Vec3 at) {
			this.vel = vel;
			this.start = start;
			this.last = at;
		}
	}

	/** Aiming: since when. */
	private static final Map<UUID, Long> AIMING = new ConcurrentHashMap<>();
	private static final Map<UUID, Flight> FLYING = new ConcurrentHashMap<>();

	private UndeadHeadThrow() {
	}

	static void clearSessionState() {
		AIMING.clear();
		FLYING.clear();
	}

	static void forget(UUID id) {
		AIMING.remove(id);
		FLYING.remove(id);
	}

	/** Ends it all (power revoked, death, a new world): the root comes off; a head in the air simply falls on. */
	static void clear(ServerPlayer p) {
		AIMING.remove(p.getUUID());
		FLYING.remove(p.getUUID());
		unroot(p);
	}

	// ---------------------------------------------------------------- shared with the client

	/** The arm that holds the head back (the main one, or the other when the main is not usable): FLAG_OFF_ARM, client-safe. */
	public static int throwingArm(Player p, UndeadState s) {
		return s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(p)) : Undead.mainArm(p);
	}

	/**
	 * Where the held head's middle is, behind the throwing shoulder at about head height, at {@code partial} -- the throw's
	 * starting point and the trajectory line's (client-safe).
	 */
	public static Vec3 heldHeadPos(Player p, float partial) {
		UndeadState s = Undead.peek(p);
		float side = s != null && throwingArm(p, s) == UndeadState.LEFT_ARM ? -1f : 1f;
		float yaw = partial >= 1f ? p.getYRot() : net.minecraft.util.Mth.rotLerp(partial, p.yRotO, p.getYRot());
		Vec3 flat = Vec3.directionFromRotation(0f, yaw);
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 base = partial >= 1f ? p.position() : p.getPosition(partial);
		// (matches the pose: the arm cocked back past the vertical, the head cupped behind and outside the hand)
		return base.add(0, 1.9, 0).add(right.scale(0.45 * side)).add(flat.scale(-0.38));
	}

	/** The velocity the head leaves with: straight along the look at {@link UndeadBulletConfig#HEAD_SPEED}. */
	public static Vec3 throwVelocity(Player p, float partial) {
		return (partial >= 1f ? p.getLookAngle() : p.getViewVector(partial)).normalize().scale(UndeadBulletConfig.HEAD_SPEED);
	}

	/** The head's box with its middle at {@code c}. */
	public static AABB headBox(Vec3 c) {
		double h = HEAD_SIZE * 0.5;
		return new AABB(c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h);
	}

	/** One tick of the arc: the next velocity (gravity, no drag). Shared by the flight and {@link #predict}. */
	public static Vec3 step(Vec3 vel) {
		return vel.add(0, -UndeadBulletConfig.HEAD_GRAVITY, 0);
	}

	/**
	 * The head's middle tick by tick from {@code start} at {@code vel} (the flight's own physics), up to {@code maxTicks}, ending
	 * with the point where its box first touches a block or water (then {@code landed[0]} is true). Client-safe: the trajectory
	 * line, and the gametests.
	 */
	public static List<Vec3> predict(Level level, Vec3 start, Vec3 vel, int maxTicks, boolean[] landed) {
		List<Vec3> out = new ArrayList<>();
		out.add(start);
		Vec3 pos = start;
		Vec3 v = vel;
		if (landed != null) {
			landed[0] = false;
		}
		for (int t = 0; t < maxTicks; t++) {
			int sub = Math.max(2, (int) Math.ceil(v.length() / 0.2));
			Vec3 d = v.scale(1.0 / sub);
			for (int k = 0; k < sub; k++) {
				Vec3 next = pos.add(d);
				AABB box = headBox(next);
				if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
					out.add(pos);
					if (landed != null) {
						landed[0] = true;
					}
					return out;
				}
				pos = next;
			}
			out.add(pos);
			v = step(v);
			if (pos.y < level.getMinBuildHeight() - 8) {
				break;
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- client-safe state

	/** Aiming: the head held back, rooted. Client-safe (the synced anim on a client, the server's own record on the server). */
	public static boolean aiming(Player p) {
		if (p instanceof ServerPlayer sp) {
			return AIMING.containsKey(sp.getUUID());
		}
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.animId == ANIM_AIM;
	}

	/** Tearing the head off (the hold before {@link #start}), client-safe. */
	public static boolean ripping(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.animId == ANIM_RIP;
	}

	/**
	 * Client-safe: a thrown head still in the air whose body has not started growing yet -- right click now grows it early
	 * (the client sends {@code UndeadHeadThrowPayload}).
	 */
	public static boolean awaitingRegrowth(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.animId == ANIM_THROWN && s.broken(UndeadState.BODY) && s.regrowStart(UndeadState.BODY) <= 0L;
	}

	/** The head is in the air from a throw (server). */
	public static boolean flying(ServerPlayer p) {
		return FLYING.containsKey(p.getUUID());
	}

	/** Nothing may start growing back: the thrown head has not touched a block yet (and no right click). From {@link Undead#tick}. */
	public static boolean holdsRegen(ServerPlayer p) {
		Flight f = FLYING.get(p.getUUID());
		return f != null && !f.regen;
	}

	// ---------------------------------------------------------------- the moves

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void anim(ServerPlayer p, int id, long start) {
		UndeadState n = Undead.state(p).copy();
		n.animId = id;
		n.animStart = start;
		Undead.save(p, n);
	}

	private static void endAnim(ServerPlayer p, int id) {
		if (Undead.state(p).animId == id) {
			anim(p, UndeadState.ANIM_NONE, p.level().getGameTime());
		}
	}

	/** The head is there and whole, and a body and an arm to tear it off and throw it with. Says why not unless {@code quiet}. */
	private static boolean canThrow(ServerPlayer p, boolean quiet) {
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		if (quiet) {
			if (!s.hasPower || !p.isAlive() || p.isSpectator() || s.reformUntil > now) {
				return false;
			}
		} else if (!Undead.canAct(p)) {
			return false;
		}
		if (FLYING.containsKey(p.getUUID()) || s.boneSword() || s.flag(UndeadState.FLAG_PROPELLING) || UndeadLaunch.flying(p)) {
			return false;
		}
		if (!s.has(UndeadState.HEAD, now) || s.broken(UndeadState.HEAD)) {
			if (!quiet) {
				Undead.say(p, "message.projecthero.undead.no_head", ChatFormatting.GRAY);
			}
			return false;
		}
		if (s.broken(UndeadState.BODY) || Undead.shootingArm(p) < 0) {
			if (!quiet) {
				Undead.say(p, "message.projecthero.undead.no_body", ChatFormatting.GRAY);
			}
			return false;
		}
		return Undead.cooldownRemaining(p, UndeadAbilities.HEAD_THROW) <= 0;
	}

	/**
	 * The hold before {@link #start} ({@code ticksHeld} into it): from {@link UndeadBulletConfig#HEAD_RIP_SHOW} he grabs his head
	 * with both hands and wrenches at it -- jerks side to side that grow harder, the neck tearing, blood beading and spurting, wet
	 * tears getting louder. Only if the throw could happen.
	 */
	public static void windUp(ServerPlayer p, int ticksHeld) {
		if (ticksHeld < UndeadBulletConfig.HEAD_RIP_SHOW || AIMING.containsKey(p.getUUID()) || !canThrow(p, true)) {
			return;
		}
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		if (s.animId != ANIM_RIP) {
			anim(p, ANIM_RIP, now - (ticksHeld - UndeadBulletConfig.HEAD_RIP_SHOW));
			p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
		}
		float k = Math.min(1f, (ticksHeld - UndeadBulletConfig.HEAD_RIP_SHOW) / (float) (UndeadBulletConfig.HEAD_RIP_HOLD - UndeadBulletConfig.HEAD_RIP_SHOW));
		Vec3 neck = p.getEyePosition().add(0, -0.3, 0);
		int every = k < 0.5f ? 8 : k < 0.8f ? 5 : 3;
		if (ticksHeld % every == 0) {
			// a wrench: the wet tear and a spurt from the neck
			UndeadSfx.drip(p.level(), neck);
			p.level().playSound(null, neck.x, neck.y, neck.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.35f + 0.5f * k, 0.7f + 0.3f * k);
			level(p).sendParticles(Undead.BLOOD, neck.x, neck.y, neck.z, 2 + (int) (6 * k), 0.16, 0.04, 0.16, 0.02);
		}
		if (k > 0.6f && ticksHeld % 7 == 0) {
			p.level().playSound(null, neck.x, neck.y, neck.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.4f + 0.4f * k, 0.6f);
		}
	}

	/**
	 * The 2 s hold completed: the head tears off with a burst of blood and he holds it back behind his throwing shoulder, the other
	 * arm pointing along the aim; rooted until {@link #release}. False (and the struggle ends) if it can't.
	 */
	public static boolean start(ServerPlayer p) {
		if (AIMING.containsKey(p.getUUID())) {
			return true;
		}
		if (!canThrow(p, false)) {
			endAnim(p, ANIM_RIP);
			return false;
		}
		int arm = Undead.shootingArm(p);
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_OFF_ARM, arm != Undead.mainArm(p));
		n.setFlag(UndeadState.FLAG_PUNCH, false);
		n.animId = ANIM_AIM;
		n.animStart = now;
		Undead.save(p, n);
		AIMING.put(p.getUUID(), now);
		root(p);
		ServerLevel level = level(p);
		Vec3 neck = p.getEyePosition().add(0, -0.25, 0);
		level.sendParticles(Undead.BLOOD_BIG, neck.x, neck.y, neck.z, 10, 0.12, 0.08, 0.12, 0.0);
		level.sendParticles(Undead.BLOOD, neck.x, neck.y + 0.15, neck.z, 18, 0.25, 0.3, 0.25, 0.0);
		UndeadSfx.partBreak(level, neck);
		UndeadSfx.bloodBurst(level, neck, 1.0f);
		return true;
	}

	/** Each tick X stays held after {@link #start}: blood runs from the neck stump and the held head. */
	public static void tickHeld(ServerPlayer p) {
		Long since = AIMING.get(p.getUUID());
		if (since == null) {
			return;
		}
		long age = p.level().getGameTime() - since;
		if (age % 4 == 0) {
			Vec3 neck = p.getEyePosition().add(0, -0.25, 0);
			Vec3 head = heldHeadPos(p, 1f).add(0, -0.25, 0);
			level(p).sendParticles(Undead.BLOOD, neck.x, neck.y, neck.z, 1, 0.08, 0.02, 0.08, 0.0);
			level(p).sendParticles(Undead.BLOOD, head.x, head.y, head.z, 1, 0.06, 0.02, 0.06, 0.0);
		}
		if (age % 30 == 15) {
			UndeadSfx.drip(p.level(), p.getEyePosition());
		}
	}

	/**
	 * X let go while aiming: the throw. Every part but the head is left behind (falling as one), and he -- just the head now --
	 * flies on the arc from behind his shoulder.
	 */
	public static void release(ServerPlayer p) {
		if (AIMING.remove(p.getUUID()) == null) {
			endAnim(p, ANIM_RIP); // let go before the head came off
			return;
		}
		unroot(p);
		UndeadState s = Undead.state(p);
		if (!s.hasPower || !p.isAlive() || s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			endAnim(p, ANIM_AIM);
			return;
		}
		long now = p.level().getGameTime();
		Vec3 feet = p.position();
		float yaw = p.yBodyRot;
		Vec3 head = heldHeadPos(p, 1f);
		Vec3 vel = throwVelocity(p, 1f);
		boolean legless = Undead.bothLegsMissing(p);
		for (int part = UndeadState.BODY; part < UndeadState.PARTS; part++) {
			if (Undead.missing(p, part)) {
				continue;
			}
			Undead.breakPart(p, part, UndeadState.KIND_LAUNCHED);
			UndeadPartEntity.drop(p, part, legless ? UndeadPartEntity.STYLE_LEFT_BODY_LEGLESS : UndeadPartEntity.STYLE_LEFT_BODY, feet, yaw, Vec3.ZERO);
		}
		for (int seg = UndeadState.PARTS; seg < UndeadState.SEGS; seg++) {
			if (!Undead.missing(p, seg)) {
				Undead.breakPart(p, seg, UndeadState.KIND_LAUNCHED); // a hand or foot whose limb was already gone
			}
		}
		// he is the head now: moved to where it was held (if it fits there), then thrown
		Vec3 at = new Vec3(head.x, head.y - HEAD_SIZE * 0.5, head.z);
		if (!p.level().noCollision(headBox(head))) {
			at = new Vec3(feet.x, p.getEyeY() - HEAD_SIZE * 0.5, feet.z);
		}
		p.teleportTo(level(p), at.x, at.y, at.z, p.getYRot(), p.getXRot());
		UndeadState n = Undead.state(p).copy();
		n.animId = ANIM_THROWN;
		n.animStart = now;
		if (UndeadBulletConfig.HEAD_THROW_COOLDOWN > 0) {
			n.abilityReadyAt.put(UndeadAbilities.HEAD_THROW, now + UndeadBulletConfig.HEAD_THROW_COOLDOWN);
		}
		Undead.save(p, n);
		p.refreshDimensions();
		FLYING.put(p.getUUID(), new Flight(vel, now, at));
		AbilityHelpers.launchSelf(p, vel);
		ServerLevel level = level(p);
		Vec3 neck = feet.add(0, legless ? 0.8 : 1.5, 0);
		level.sendParticles(Undead.BLOOD_BIG, neck.x, neck.y, neck.z, 8, 0.12, 0.08, 0.12, 0.0);
		level.sendParticles(Undead.BLOOD, head.x, head.y, head.z, 10, 0.15, 0.15, 0.15, 0.0);
		UndeadSfx.launch(level, head);
		p.level().playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	/** The hold broke off: a held head goes back on (nothing was broken yet), the struggle ends. A head in the air flies on. */
	public static void cancel(ServerPlayer p) {
		if (AIMING.remove(p.getUUID()) != null) {
			unroot(p);
			endAnim(p, ANIM_AIM);
			UndeadSfx.drip(p.level(), p.getEyePosition());
		}
		endAnim(p, ANIM_RIP);
	}

	/** Right click mid-air (from {@code UndeadHeadThrowPayload}): the body may start growing back around the head now. */
	public static void regrowNow(ServerPlayer p) {
		Flight f = FLYING.get(p.getUUID());
		if (f == null || f.regen) {
			return;
		}
		f.regen = true;
		Vec3 c = p.position().add(0, 0.25, 0);
		level(p).sendParticles(Undead.BLOOD, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.0);
		UndeadSfx.drip(p.level(), c);
	}

	/** Every tick (from {@link UndeadAbilityManager#tickHold}): the aim's checks and the flight. */
	static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long since = AIMING.get(p.getUUID());
		if (since != null) {
			UndeadState s = Undead.state(p);
			// lost the head or body meanwhile, shattered, or the release never came (a screen took the key): it goes back on
			if (!s.hasPower || !p.isAlive() || s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY) || s.reformUntil > now || now - since > 1200
					|| s.animId != ANIM_AIM) {
				cancel(p);
			} else {
				root(p);
				Vec3 v = p.getDeltaMovement();
				if (p.onGround() && (v.x * v.x + v.z * v.z) > 1.0e-4) {
					p.setDeltaMovement(0, v.y, 0);
				}
			}
		}
		Flight f = FLYING.get(p.getUUID());
		if (f == null) {
			return;
		}
		long age = now - f.start;
		Vec3 pos = p.position();
		f.still = age >= 3 && pos.distanceToSqr(f.last) < 1.0e-4 ? f.still + 1 : 0;
		boolean touched = age >= 3 && (p.onGround() || p.horizontalCollision || p.verticalCollision || p.isInWater() || f.still >= 2);
		if (!p.isAlive() || touched || age > UndeadBulletConfig.HEAD_MAX_FLIGHT || age < 0 || !Undead.state(p).hasPower) {
			land(p, touched);
			return;
		}
		p.resetFallDistance();
		f.last = pos;
		f.vel = step(f.vel);
		AbilityHelpers.launchSelf(p, f.vel);
		if (age % 2 == 0) {
			level(p).sendParticles(Undead.BLOOD, pos.x, pos.y + 0.1, pos.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** It touched a block (or gave out): the scripted arc ends and the body may grow back around the head where it is. */
	private static void land(ServerPlayer p, boolean touched) {
		FLYING.remove(p.getUUID());
		p.resetFallDistance();
		endAnim(p, ANIM_THROWN);
		if (touched) {
			Vec3 c = p.position().add(0, 0.25, 0);
			UndeadSfx.headRoll(p.level(), c, 1.0f);
			level(p).sendParticles(Undead.BLOOD, c.x, c.y, c.z, 6, 0.15, 0.08, 0.15, 0.0);
		}
	}

	private static void root(ServerPlayer p) {
		// (no speed modifier: it would zoom the camera in -- the client's movement keys are held still instead, UndeadHeadThrowInputMixin)
		PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, ROOT_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void unroot(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, ROOT_JUMP_ID);
	}
}
