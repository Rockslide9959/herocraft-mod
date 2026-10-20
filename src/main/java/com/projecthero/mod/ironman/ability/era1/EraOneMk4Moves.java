package com.projecthero.mod.ironman.ability.era1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.ability.EraOneMk34Access;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Era I (user): the Mk IV Hydro's twelve moves ({@code docs/IRONMAN_ERA1_PLAN.md}) -- the gold deep-sea suit:
 * torpedoes, sonar, jets and a bubble shield. Its hits deal x1.5 in water (applied once, centrally, in
 * {@code IronManDamage} -- never here).
 *
 * <ul>
 *   <li>R: Torpedo 30, straight (fast in water, drops on land) / Homing Torpedo 26 at the nearest target.</li>
 *   <li>G: Sonar Ping -- everything in 48 blocks outlined through walls for 10 s (wearer only) / Sonic Pulse -- 10 and a
 *       2 s stun to everything in 8 blocks (4 s on Guardians and water mobs).</li>
 *   <li>X: Hydro Surge -- a jet dash along the look, 3x as strong in water / Ballast Drop -- sink fast underwater; on land a
 *       stomp of 20 in 4 blocks (fall-safe, from the air it slams down first).</li>
 *   <li>Z: Depth Charge -- thrown, sinks, 28 in 4 blocks / Mine Field -- 3 proximity mines, 22 each, for 60 s.</li>
 *   <li>V: Whirlpool -- pulls everything in 10 blocks of the aim point in for 4 s, 3 a second / Bubble Shield -- an 8 s air
 *       bubble (radius 4) round you: you and your allies breathe, mobs are pushed out.</li>
 *   <li>C: Headlamps toggle -- strong light that goes where you go and where you look (temporary light blocks, always
 *       taken down again).</li>
 * </ul>
 */
public final class EraOneMk4Moves {
	public static final String SUIT = "mark_4";

	public static final double TORPEDO_RANGE = 64.0;
	public static final double TORPEDO_SPEED_WATER = 1.6;
	public static final double TORPEDO_SPEED_LAND = 0.9;
	public static final double HOMING_RANGE = 48.0;
	public static final double PING_RADIUS = 48.0;
	public static final int PING_TICKS = 10 * 20;
	public static final double PULSE_RADIUS = 8.0;
	public static final int PULSE_STUN_TICKS = 2 * 20;
	public static final int PULSE_STUN_WATER_TICKS = 4 * 20;
	public static final double SURGE_SPEED = 1.1;
	public static final double SURGE_WATER_FACTOR = 3.0;
	public static final double STOMP_RADIUS = 4.0;
	public static final int BALLAST_SINK_TICKS = 3 * 20;
	public static final double CHARGE_RADIUS = 4.0;
	public static final int CHARGE_FUSE_TICKS = 3 * 20;
	public static final int MINE_COUNT = 3;
	public static final int MINE_LIFE_TICKS = 60 * 20;
	public static final int MINE_ARM_TICKS = 10;
	public static final double MINE_TRIGGER_RADIUS = 1.6;
	public static final double MINE_BLAST_RADIUS = 3.0;
	/** At most this many of one wearer's mines are out at once (the oldest go first). */
	public static final int MINE_CAP = 6;
	public static final double WHIRLPOOL_RADIUS = 10.0;
	public static final int WHIRLPOOL_TICKS = 4 * 20;
	public static final double BUBBLE_RADIUS = 4.0;
	public static final int BUBBLE_TICKS = 8 * 20;
	public static final double LAMP_REACH = 24.0;

	private static final DustParticleOptions MINE_LIGHT = new DustParticleOptions(new org.joml.Vector3f(1f, 0.25f, 0.15f), 0.9f);

	private EraOneMk4Moves() {
	}

	static void init() {
		EraOneMk34State.initialize();
		EraOneMk3Moves.tap("e1mk4_torpedo", (p, suit, m) -> torpedo(p, m.damage()));
		EraOneMk3Moves.tap("e1mk4_homing_torpedo", (p, suit, m) -> homingTorpedo(p, m.damage()));
		EraOneMk3Moves.tap("e1mk4_sonar_ping", (p, suit, m) -> sonarPing(p));
		EraOneMk3Moves.tap("e1mk4_sonic_pulse", (p, suit, m) -> sonicPulse(p, m.damage()));
		EraOneMk3Moves.tap("e1mk4_hydro_surge", (p, suit, m) -> hydroSurge(p));
		EraOneMk3Moves.tap("e1mk4_ballast_drop", (p, suit, m) -> ballast(p, m.damage()));
		EraOneMk3Moves.tap("e1mk4_depth_charge", (p, suit, m) -> depthCharge(p, m.damage()));
		EraOneMk3Moves.tap("e1mk4_mine_field", (p, suit, m) -> mineField(p, m.damage()));
		EraOneMk3Moves.tap("e1mk4_whirlpool", (p, suit, m) -> whirlpool(p));
		EraOneMk3Moves.tap("e1mk4_bubble_shield", (p, suit, m) -> bubbleShield(p));
		EraOneMk3Moves.tap("e1mk4_headlamps", (p, suit, m) -> toggleHeadlamps(p));
	}

	/** In water (the fluid at the feet / body, not only the synced flag -- works the tick you jump in). */
	public static boolean wet(ServerPlayer p) {
		return p.isInWater() || p.level().getFluidState(BlockPos.containing(p.getX(), p.getY() + 0.3, p.getZ())).is(FluidTags.WATER);
	}

	private static boolean wetAt(ServerLevel level, Vec3 at) {
		return level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER);
	}

	/** Standing on something (the synced flag, or a solid block right under the feet). */
	private static boolean grounded(ServerPlayer p) {
		if (p.onGround()) {
			return true;
		}
		BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		return !p.level().getBlockState(below).getCollisionShape(p.level(), below).isEmpty();
	}

	/** Guardians and the water mobs (fish, squid, dolphins, drowned...): the Sonic Pulse stuns them twice as long. */
	public static boolean waterMob(LivingEntity e) {
		return e instanceof Guardian || e instanceof WaterAnimal || e instanceof Drowned || e.getType().is(EntityTypeTags.AQUATIC);
	}

	// ================================================================== R

	private static boolean torpedo(ServerPlayer p, float damage) {
		boolean wet = wet(p);
		Vec3 from = EraOneMk34Access.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), TORPEDO_RANGE);
		IronManMissileEntity m = EraOneMk34Access.missile(p, from.add(dir), dir, wet ? TORPEDO_SPEED_WATER : TORPEDO_SPEED_LAND, damage,
				damage * 0.4f, 1.6f).withTorpedo().withSinking();
		m.setDeltaMovement(dir.normalize().scale(wet ? TORPEDO_SPEED_WATER : TORPEDO_SPEED_LAND)); // launched at speed (not the slow ramp)
		EraOneMk34Access.spawn(p, m);
		launchFx(p, from, wet);
		return true;
	}

	private static boolean homingTorpedo(ServerPlayer p, float damage) {
		boolean wet = wet(p);
		LivingEntity t = nearestTarget(p, HOMING_RANGE);
		Vec3 from = EraOneMk34Access.palm(p);
		Vec3 dir = t != null ? EraOneMk34Access.centre(t).subtract(from).normalize() : p.getLookAngle();
		IronManMissileEntity m = EraOneMk34Access.missile(p, from.add(dir), dir, wet ? 1.3 : 0.8, damage, damage * 0.4f, 1.6f).withTorpedo();
		m.setDeltaMovement(dir.normalize().scale(wet ? 1.3 : 0.8));
		if (t != null) {
			m.withTarget(t);
			EraOneMk34Access.privateMark(p, List.of(t), 60);
			IronManSounds.play(p, IronManSounds.TARGET_LOCK, 0.8f, 0.9f);
		} else {
			m.withHoming();
		}
		EraOneMk34Access.spawn(p, m);
		launchFx(p, from, wet);
		return true;
	}

	/** The lock first, else the nearest threat in range, else the nearest thing it may hit. */
	static LivingEntity nearestTarget(ServerPlayer p, double range) {
		LivingEntity lock = IronManTargeting.lockedWithin(p, range);
		if (lock != null) {
			return lock;
		}
		List<LivingEntity> all = new ArrayList<>(EraOneMk34Access.around(p, p.position(), range));
		all.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		for (LivingEntity e : all) {
			if (HeroTargets.isHostile(p, e)) {
				return e;
			}
		}
		return all.isEmpty() ? null : all.get(0);
	}

	private static void launchFx(ServerPlayer p, Vec3 from, boolean wet) {
		EraOneMk34Access.pose(p, IronManAbilityFx.ROCKET, 12);
		EraOneMk34Access.sound(p, IronManSounds.MISSILE_LAUNCH, 1.0f, 0.7f);
		ServerLevel level = EraOneMk34Access.level(p);
		EraOneMk34Access.cloud(level, from, wet ? ParticleTypes.BUBBLE : ParticleTypes.SMOKE, 20, 0.3, 0.1);
		if (wet) {
			EraOneMk34Access.soundAt(level, from, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8f, 1.4f);
		}
	}

	// ================================================================== G

	private static boolean sonarPing(ServerPlayer p) {
		List<LivingEntity> found = EraOneMk34Access.around(p, p.position(), PING_RADIUS);
		EraOneMk34Access.privateMark(p, found, PING_TICKS); // through walls, on the wearer's own screen only
		EraOneMk34State.of(p).lastPing = found.size();
		boolean wet = wet(p);
		ServerLevel level = EraOneMk34Access.level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (int i = 1; i <= 6; i++) {
			final double r = PING_RADIUS * i / 6.0;
			EraOneMk34Access.later(p, i * 2, w -> EraOneMk34Access.ring(EraOneMk34Access.level(w), c, r,
					wet ? ParticleTypes.BUBBLE : ParticleTypes.SCULK_CHARGE_POP, 24 + (int) r));
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		EraOneMk34Access.sound(p, IronManSounds.SCAN, 0.9f, 0.8f);
		EraOneMk34Access.soundAt(level, c, SoundEvents.BELL_RESONATE, 0.6f, 1.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.marked", found.size()).withStyle(ChatFormatting.AQUA),
				true);
		return true;
	}

	/** Gametests: how many things the last Sonar Ping outlined. */
	public static int lastPing(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s == null ? 0 : s.lastPing;
	}

	private static boolean sonicPulse(ServerPlayer p, float damage) {
		boolean wet = wet(p);
		ServerLevel level = EraOneMk34Access.level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : EraOneMk34Access.around(p, c, PULSE_RADIUS)) {
			EraOneMk34Access.hit(p, e, damage);
			if (e instanceof Mob m) {
				EraOneMk34Access.stun(m, waterMob(e) ? PULSE_STUN_WATER_TICKS : PULSE_STUN_TICKS);
			}
		}
		for (int i = 1; i <= 4; i++) {
			EraOneMk34Access.ring(level, c, PULSE_RADIUS * i / 4.0, wet ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD, 12 + i * 8);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_FLARE, 12);
		EraOneMk34Access.soundAt(level, c, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.6f);
		return true;
	}

	// ================================================================== X

	private static boolean hydroSurge(ServerPlayer p) {
		boolean wet = wet(p);
		Vec3 look = p.getLookAngle();
		Vec3 v = look.scale(SURGE_SPEED * (wet ? SURGE_WATER_FACTOR : 1.0));
		if (!wet && grounded(p) && v.y < 0.15) {
			v = new Vec3(v.x, 0.15, v.z); // off the floor, not scraping along it
		}
		EraOneMk34Access.launch(p, v);
		ServerLevel level = EraOneMk34Access.level(p);
		Vec3 c = p.position().add(0, 0.8, 0);
		if (wet) {
			EraOneMk34Access.cloud(level, c, ParticleTypes.BUBBLE, 40, 0.5, 0.3);
			EraOneMk34Access.line(level, c, c.subtract(look.scale(4.0)), ParticleTypes.BUBBLE_COLUMN_UP, 3.0);
		} else {
			EraOneMk34Access.cloud(level, c, ParticleTypes.CLOUD, 16, 0.3, 0.1);
			EraOneMk34Access.line(level, c, c.subtract(look.scale(3.0)), ParticleTypes.SPLASH, 3.0);
		}
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_LAUNCH, 10);
		EraOneMk34Access.sound(p, IronManSounds.THRUSTER, 1.0f, wet ? 0.7f : 1.1f);
		EraOneMk34Access.soundAt(level, c, SoundEvents.TRIDENT_RIPTIDE_1, 0.8f, 1.0f);
		return true;
	}

	private static boolean ballast(ServerPlayer p, float damage) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		long now = p.level().getGameTime();
		ServerLevel level = EraOneMk34Access.level(p);
		if (wet(p)) {
			s.ballastSinkUntil = now + BALLAST_SINK_TICKS;
			EraOneMk34Access.launch(p, new Vec3(p.getDeltaMovement().x * 0.3, -1.5, p.getDeltaMovement().z * 0.3));
			EraOneMk34Access.cloud(level, p.position().add(0, 1.2, 0), ParticleTypes.BUBBLE, 40, 0.5, 0.2);
			EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_DIVE, 12);
			EraOneMk34Access.soundAt(level, p.position(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.0f, 0.6f);
			return true;
		}
		if (grounded(p)) {
			stomp(p, damage);
			return true;
		}
		// in the air on land: slam straight down, the stomp goes off on landing
		s.ballastStompPending = true;
		s.ballastStompUntil = now + 60;
		EraOneMk34Access.launch(p, new Vec3(p.getDeltaMovement().x * 0.2, -2.5, p.getDeltaMovement().z * 0.2));
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_DIVE, 20);
		EraOneMk34Access.sound(p, IronManSounds.THRUSTER, 1.0f, 0.6f);
		return true;
	}

	private static void stomp(ServerPlayer p, float damage) {
		ServerLevel level = EraOneMk34Access.level(p);
		Vec3 c = p.position();
		for (LivingEntity e : EraOneMk34Access.around(p, c.add(0, 0.5, 0), STOMP_RADIUS)) {
			EraOneMk34Access.hit(p, e, damage);
			EraOneMk34Access.shove(e, c, 1.2, 0.5);
		}
		p.resetFallDistance();
		EraOneMk34Access.groundRing(level, c, STOMP_RADIUS * 0.5, 16);
		EraOneMk34Access.groundRing(level, c, STOMP_RADIUS, 24);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 20, 1.5, 0.1, 1.5, 0.08);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_STOMP, 14);
		EraOneMk34Access.soundAt(level, c, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
		EraOneMk34Access.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.7f);
	}

	// ================================================================== Z

	private static boolean depthCharge(ServerPlayer p, float damage) {
		Vec3 from = EraOneMk34Access.palm(p);
		Vec3 vel = p.getLookAngle().scale(0.9).add(0, 0.15, 0);
		EraOneMk34State.CHARGES.add(new EraOneMk34State.Charge(p.getUUID(), p.level().dimension(), from, vel, p.level().getGameTime(), damage));
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_THROW, 10);
		EraOneMk34Access.soundAt(EraOneMk34Access.level(p), from, SoundEvents.TNT_PRIMED, 0.8f, 1.3f);
		return true;
	}

	private static boolean mineField(ServerPlayer p, float damage) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 base = p.position().add(0, 1.0, 0);
		Vec3[] spots = { base.add(flat.scale(3.0)), base.add(flat.scale(4.5)).add(right.scale(2.0)),
				base.add(flat.scale(4.5)).add(right.scale(-2.0)) };
		// the oldest of this wearer's mines go first past the cap
		List<EraOneMk34State.Mine> mine = new ArrayList<>();
		for (EraOneMk34State.Mine m : EraOneMk34State.MINES) {
			if (m.owner.equals(p.getUUID())) {
				mine.add(m);
			}
		}
		for (int i = 0; i < mine.size() + MINE_COUNT - MINE_CAP; i++) {
			EraOneMk34State.MINES.remove(mine.get(i));
		}
		long now = p.level().getGameTime();
		ServerLevel level = EraOneMk34Access.level(p);
		for (Vec3 spot : spots) {
			Vec3 at = EraOneMk34Access.groundAt(p, spot);
			EraOneMk34State.MINES.add(new EraOneMk34State.Mine(p.getUUID(), p.level().dimension(), at, now + MINE_LIFE_TICKS, damage));
			EraOneMk34Access.line(level, EraOneMk34Access.palm(p), at, ParticleTypes.SMOKE, 2.0);
			level.sendParticles(MINE_LIGHT, at.x, at.y + 0.15, at.z, 6, 0.1, 0.05, 0.1, 0.0);
		}
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_THROW, 12);
		EraOneMk34Access.soundAt(level, p.position(), SoundEvents.DISPENSER_LAUNCH, 1.0f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.e1mk4_mines", MINE_COUNT, MINE_LIFE_TICKS / 20),
				true);
		return true;
	}

	/** Gametests: how many of this wearer's mines are out. */
	public static int mines(ServerPlayer p) {
		int n = 0;
		for (EraOneMk34State.Mine m : EraOneMk34State.MINES) {
			if (m.owner.equals(p.getUUID())) {
				n++;
			}
		}
		return n;
	}

	/** Gametests: where this wearer's mines are. */
	public static List<Vec3> minePositions(ServerPlayer p) {
		List<Vec3> out = new ArrayList<>();
		for (EraOneMk34State.Mine m : EraOneMk34State.MINES) {
			if (m.owner.equals(p.getUUID())) {
				out.add(m.pos);
			}
		}
		return out;
	}

	/** Gametests: the game time this wearer's newest mine runs out. */
	public static long mineExpiry(ServerPlayer p) {
		long t = 0L;
		for (EraOneMk34State.Mine m : EraOneMk34State.MINES) {
			if (m.owner.equals(p.getUUID())) {
				t = Math.max(t, m.until);
			}
		}
		return t;
	}

	/** Depth charges and mines (world objects), once per server tick. */
	static void tickWorld(MinecraftServer server) {
		for (Iterator<EraOneMk34State.Charge> it = EraOneMk34State.CHARGES.iterator(); it.hasNext();) {
			EraOneMk34State.Charge c = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
			ServerLevel level = server.getLevel(c.dim);
			if (owner == null || level == null || owner.level() != level || !level.isLoaded(BlockPos.containing(c.pos))) {
				it.remove();
				continue;
			}
			if (tickCharge(owner, level, c)) {
				it.remove();
			}
		}
		for (Iterator<EraOneMk34State.Mine> it = EraOneMk34State.MINES.iterator(); it.hasNext();) {
			EraOneMk34State.Mine m = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(m.owner);
			ServerLevel level = server.getLevel(m.dim);
			if (owner == null || level == null || owner.level() != level) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now >= m.until) {
				level.sendParticles(ParticleTypes.SMOKE, m.pos.x, m.pos.y + 0.2, m.pos.z, 6, 0.15, 0.1, 0.15, 0.01);
				it.remove();
				continue;
			}
			boolean wet = wetAt(level, m.pos.add(0, 0.2, 0));
			if (now % 10 == 0) {
				level.sendParticles(MINE_LIGHT, m.pos.x, m.pos.y + 0.15, m.pos.z, 1, 0, 0, 0, 0);
				level.sendParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.GLOW, m.pos.x, m.pos.y + 0.2, m.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (now < m.until - MINE_LIFE_TICKS + MINE_ARM_TICKS) {
				continue; // arming
			}
			if (!EraOneMk34Access.around(owner, m.pos.add(0, 0.4, 0), MINE_TRIGGER_RADIUS).isEmpty()) {
				for (LivingEntity e : EraOneMk34Access.around(owner, m.pos.add(0, 0.4, 0), MINE_BLAST_RADIUS)) {
					EraOneMk34Access.hit(owner, e, m.damage);
					EraOneMk34Access.shove(e, m.pos, 1.0, 0.6);
				}
				blastFx(level, m.pos, wet, 1.0f);
				it.remove();
			}
		}
	}

	/** One tick of a thrown charge: arcs in air, sinks in water; true when it went off. */
	private static boolean tickCharge(ServerPlayer owner, ServerLevel level, EraOneMk34State.Charge c) {
		long age = level.getGameTime() - c.born;
		boolean wet = wetAt(level, c.pos);
		c.vel = wet ? new Vec3(c.vel.x * 0.8, Math.max(-0.35, c.vel.y * 0.7 - 0.06), c.vel.z * 0.8)
				: new Vec3(c.vel.x * 0.98, c.vel.y * 0.98 - 0.08, c.vel.z * 0.98);
		Vec3 next = c.pos.add(c.vel);
		BlockHitResult hit = level.clip(new ClipContext(c.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		boolean landed = hit.getType() != HitResult.Type.MISS;
		c.pos = landed ? hit.getLocation().add(0, 0.1, 0) : next;
		level.sendParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.SMOKE, c.pos.x, c.pos.y, c.pos.z, 2, 0.03, 0.03, 0.03, 0.0);
		boolean touched = !EraOneMk34Access.around(owner, c.pos, 1.0).isEmpty();
		if (!landed && !touched && age < CHARGE_FUSE_TICKS) {
			return false;
		}
		for (LivingEntity e : EraOneMk34Access.around(owner, c.pos, CHARGE_RADIUS)) {
			EraOneMk34Access.hit(owner, e, c.damage);
			EraOneMk34Access.shove(e, c.pos, 1.2, 0.6);
		}
		blastFx(level, c.pos, wet, 1.4f);
		return true;
	}

	private static void blastFx(ServerLevel level, Vec3 at, boolean wet, float volume) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		if (wet) {
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 60, 2.0, 1.0, 2.0, 0.2);
		}
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 60, 2.0, 0.5, 2.0, 0.2);
		EraOneMk34Access.soundAt(level, at, SoundEvents.GENERIC_EXPLODE, volume, 0.7f);
	}

	// ================================================================== V

	private static boolean whirlpool(ServerPlayer p) {
		Vec3 at = EraOneMk34Access.groundAt(p, EraOneMk34Access.aimPoint(p, 16.0));
		EraOneMk34Access.whirlpool(p, at, WHIRLPOOL_RADIUS, WHIRLPOOL_TICKS); // the comic whirlpool area: 3 a second
		EraOneMk34Access.line(EraOneMk34Access.level(p), EraOneMk34Access.palm(p), at, ParticleTypes.BUBBLE_POP, 2.0);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_MAGNET, 12);
		EraOneMk34Access.soundAt(EraOneMk34Access.level(p), at, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.4f, 0.8f);
		return true;
	}

	private static boolean bubbleShield(ServerPlayer p) {
		EraOneMk34State.of(p).bubbleUntil = p.level().getGameTime() + BUBBLE_TICKS;
		ServerLevel level = EraOneMk34Access.level(p);
		EraOneMk34Access.ring(level, p.position().add(0, 1.0, 0), BUBBLE_RADIUS, wet(p) ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_POP, 40);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_GUARD, 10);
		EraOneMk34Access.soundAt(level, p.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2f, 0.8f);
		IronManSounds.play(p, IronManSounds.SHIELD_UP, 0.8f, 1.3f);
		return true;
	}

	public static boolean bubbleUp(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s != null && s.bubbleUntil > p.level().getGameTime();
	}

	private static void tickBubble(ServerPlayer p, EraOneMk34State.P s, long now) {
		ServerLevel level = EraOneMk34Access.level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		AABB box = new AABB(c, c).inflate(BUBBLE_RADIUS + 0.5);
		// you and your allies breathe inside it
		for (Player other : level.getEntitiesOfClass(Player.class, box, o -> o.isAlive() && o.distanceToSqr(c) <= BUBBLE_RADIUS * BUBBLE_RADIUS)) {
			if (other == p || !HeroTargets.canHarm(p, other)) {
				other.setAirSupply(other.getMaxAirSupply());
			}
		}
		// mobs are pushed back out
		for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && HeroTargets.canHarm(p, m))) {
			Vec3 away = m.position().subtract(p.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			if (flat.length() > BUBBLE_RADIUS) {
				continue;
			}
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
			m.setDeltaMovement(flat.scale(0.6).add(0, 0.1, 0));
			m.hurtMarked = true;
			m.hasImpulse = true;
		}
		if (now % 3 == 0) {
			boolean wet = wet(p);
			for (int i = 0; i < 10; i++) {
				double yaw = p.getRandom().nextDouble() * Math.PI * 2;
				double pitch = Math.acos(2 * p.getRandom().nextDouble() - 1);
				double x = Math.sin(pitch) * Math.cos(yaw) * BUBBLE_RADIUS;
				double y = Math.cos(pitch) * BUBBLE_RADIUS;
				double z = Math.sin(pitch) * Math.sin(yaw) * BUBBLE_RADIUS;
				level.sendParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_POP, c.x + x, c.y + y, c.z + z, 1, 0, 0, 0, 0);
			}
		}
		if (now % 40 == 0) {
			EraOneMk34Access.soundAt(level, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.6f, 1.0f);
		}
	}

	// ================================================================== C: headlamps

	private static boolean toggleHeadlamps(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		s.headlamps = !s.headlamps;
		if (s.headlamps) {
			followLights(p, s);
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.e1mk4_lamps_on"), true);
		} else {
			removeLights(p.getServer(), s);
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.e1mk4_lamps_off"), true);
		}
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_GUARD, 6);
		EraOneMk34Access.soundAt(EraOneMk34Access.level(p), p.getEyePosition(), SoundEvents.LEVER_CLICK, 0.6f, s.headlamps ? 1.4f : 0.9f);
		return true;
	}

	public static boolean headlampsOn(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s != null && s.headlamps;
	}

	/** Gametests: where the headlamps' light blocks are right now. */
	public static List<BlockPos> lightPositions(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		List<BlockPos> out = new ArrayList<>();
		if (s != null) {
			for (EraOneMk34State.Light l : s.lights) {
				out.add(l.pos());
			}
		}
		return out;
	}

	/** The cells the lamps should light: at the head, and where the beams land along the look. */
	private static List<BlockPos> wantedCells(ServerPlayer p) {
		List<BlockPos> out = new ArrayList<>();
		Vec3 eye = p.getEyePosition();
		BlockPos head = BlockPos.containing(eye);
		out.add(head);
		Vec3 look = p.getLookAngle();
		double reach = EraOneMk34Access.wallDistance(p, eye, look, LAMP_REACH);
		BlockPos spot = BlockPos.containing(eye.add(look.scale(Math.max(0.0, reach - 0.6))));
		if (spot.distSqr(head) > 4) {
			out.add(spot);
		}
		return out;
	}

	private static boolean placeable(BlockState s) {
		return s.isAir() || s.is(Blocks.WATER) && s.getFluidState().isSource();
	}

	private static boolean ours(ServerLevel level, EraOneMk34State.Light l) {
		return level != null && level.isLoaded(l.pos()) && level.getBlockState(l.pos()).is(Blocks.LIGHT);
	}

	private static void removeLight(ServerLevel level, EraOneMk34State.Light l) {
		if (ours(level, l)) {
			BlockState back = l.waterlogged() ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
			level.setBlock(l.pos(), back, Block.UPDATE_ALL);
		}
	}

	/** Take every headlamp light of this player down (toggle-off, suit-off, logout, stop). */
	static void removeLights(MinecraftServer server, EraOneMk34State.P s) {
		if (server != null) {
			for (EraOneMk34State.Light l : s.lights) {
				removeLight(server.getLevel(l.dim()), l);
			}
		}
		s.lights.clear();
	}

	/** Move the light blocks to where the lamps shine now (new ones placed, old ones taken down). */
	private static void followLights(ServerPlayer p, EraOneMk34State.P s) {
		ServerLevel level = p.serverLevel();
		List<BlockPos> wanted = wantedCells(p);
		for (Iterator<EraOneMk34State.Light> it = s.lights.iterator(); it.hasNext();) {
			EraOneMk34State.Light l = it.next();
			if (l.dim() != level.dimension() || !wanted.contains(l.pos()) || !ours(level, l)) {
				removeLight(p.getServer().getLevel(l.dim()), l);
				it.remove();
			}
		}
		for (BlockPos cell : wanted) {
			boolean have = false;
			for (EraOneMk34State.Light l : s.lights) {
				have |= l.pos().equals(cell);
			}
			if (have || !level.isLoaded(cell)) {
				continue;
			}
			BlockState here = level.getBlockState(cell);
			if (!placeable(here)) {
				continue;
			}
			boolean water = here.is(Blocks.WATER);
			level.setBlock(cell, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15)
					.setValue(BlockStateProperties.WATERLOGGED, water), Block.UPDATE_ALL);
			s.lights.add(new EraOneMk34State.Light(level.dimension(), cell.immutable(), water));
		}
	}

	// ================================================================== tick

	/** Per server tick for a player with state ({@code active} = the Mk IV is worn and working). */
	static void tick(ServerPlayer p, EraOneMk34State.P s, boolean active) {
		if (!active) {
			s.ballastSinkUntil = 0L;
			s.ballastStompPending = false;
			s.bubbleUntil = 0L;
			if (s.headlamps || !s.lights.isEmpty()) {
				s.headlamps = false;
				removeLights(p.getServer(), s);
			}
			return;
		}
		long now = p.level().getGameTime();
		if (s.ballastSinkUntil > now) {
			if (!wet(p) || grounded(p)) {
				s.ballastSinkUntil = 0L;
			} else if (p.getDeltaMovement().y > -0.8) {
				EraOneMk34Access.launch(p, new Vec3(p.getDeltaMovement().x * 0.5, -0.8, p.getDeltaMovement().z * 0.5));
				if (now % 2 == 0) {
					EraOneMk34Access.level(p).sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1.6, p.getZ(), 4, 0.3, 0.2, 0.3, 0.05);
				}
			}
		}
		if (s.ballastStompPending) {
			p.resetFallDistance(); // fall-safe
			if (grounded(p) || wet(p)) {
				s.ballastStompPending = false;
				IronManSuit suit = IronManSuits.byId(SUIT);
				var m = com.projecthero.mod.ironman.ability.IronManComicKit.byId("e1mk4_ballast_drop");
				if (!wet(p) && suit != null) {
					stomp(p, m == null ? 20f : m.damage());
				}
			} else if (now > s.ballastStompUntil) {
				s.ballastStompPending = false;
			}
		}
		if (s.bubbleUntil > now) {
			tickBubble(p, s, now);
		} else if (s.bubbleUntil != 0L) {
			s.bubbleUntil = 0L;
			EraOneMk34Access.soundAt(EraOneMk34Access.level(p), p.position(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.0f, 0.8f);
		}
		if (s.headlamps) {
			followLights(p, s);
			if (now % 5 == 0) {
				Vec3 e = p.getEyePosition().add(p.getLookAngle().scale(0.4));
				EraOneMk34Access.level(p).sendParticles(ParticleTypes.END_ROD, e.x, e.y, e.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

}
