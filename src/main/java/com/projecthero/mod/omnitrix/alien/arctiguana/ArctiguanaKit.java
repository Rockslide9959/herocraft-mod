package com.projecthero.mod.omnitrix.alien.arctiguana;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Arctiguana (Ben 10 classic, a hidden
 * prototype alien found by a mistransform): a blue reptile who breathes ice. His freezing breath encases enemies in ice; the cold never
 * touches him; he is quick and low to the ground, with a heavy tail. Every freeze is the Big Chill shell ({@link BigChillKit#freezeSolid}:
 * rooted inside a see-through block of ice, no blocks harmed) -- only the Frost Wall puts real (temporary) ice into the world, into air.
 *
 * <pre>
 *   R          Ice Breath      a 1.5 s breath, a 7-block cone: 3 every quarter second, slowed and frosted; a creature caught by 3 gusts
 *                              (or already frosted) is frozen solid in a block of ice for 3 s
 *   Shift+R    Ice Shard Spit  a spat shard (7), frosts what it hits; a frosted creature it hits freezes solid for 2 s
 *   G          Frost Wall      a 5x3 wall of packed ice 2.5 blocks ahead, into air only; it melts away after 8 s
 *   Z          Deep Freeze     a burst of cold round him (6 blocks): 17, and everything in it frozen solid for 3 s
 *   X          Ice Slide       a frictionless belly slide where he looks for 0.8 s, leaving frost; whatever he slides through is
 *                              frosted (4)
 *   C          Tail Whip       the tail swung right round him (3.5 blocks): 8, knocked back
 *   V          Frost Armour    10 s of ice plates: 40% less damage; whoever hits him in melee is frosted (2)
 *   Attack     Frost Claws     his punches frost what they hit
 *   Passives   Cold Blooded (freezing and powder snow never hurt or slow him); Reptile Agility (quick, springy, a soft landing)
 *   Weakness   Heat (fire and lava hurt him half again)
 * </pre>
 */
public final class ArctiguanaKit extends AlienKit {
	public static final String ID = "arctiguana";
	public static final int ICE = 0x9AE0FF, ICE_PALE = 0xE8F8FF, ICE_DEEP = 0x48CFFA;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_BREATH = 1, F_SLIDE = 2, F_ARMOUR = 4;
	public static final int ANIM_BREATH = 1, ANIM_SPIT = 2, ANIM_WALL = 3, ANIM_FREEZE = 4, ANIM_SLIDE = 5, ANIM_WHIP = 6, ANIM_ARMOUR = 7, ANIM_CLAW = 8,
			ANIM_SLIDE_END = 9;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.arctiguana.<id>)
	public static final String BREATH = "ice_breath", SPIT = "ice_shard_spit", WALL = "frost_wall", FREEZE = "deep_freeze", SLIDE = "ice_slide",
			WHIP = "tail_whip", ARMOUR = "frost_armour", CLAWS = "frost_claws", COLD = "cold_blooded", AGILE = "reptile_agility", HEAT = "heat_weakness";
	public static final String BREATH_UNTIL = "breath_until", SLIDE_UNTIL = "slide_until", ARMOUR_UNTIL = "armour_until";

	// ---------------------------------------------------------------- numbers (quick 5-8, normal 12-17, big 17-20)
	public static final float BREATH_DAMAGE = 3f, SPIT_DAMAGE = 7f, FREEZE_DAMAGE = 17f, SLIDE_DAMAGE = 4f, WHIP_DAMAGE = 8f, ARMOUR_CHILL_DAMAGE = 2f;
	public static final float ARMOUR_TAKEN = 0.6f, HEAT_TAKEN = 1.5f;
	public static final int BREATH_TICKS = 30, BREATH_EVERY = 5, BREATH_GUSTS_TO_FREEZE = 3, FREEZE_TICKS = 60, SPIT_FREEZE_TICKS = 40, WALL_TICKS = 160,
			SLIDE_TICKS = 16, ARMOUR_TICKS = 200;
	public static final double BREATH_RANGE = 7, BREATH_HALF_ANGLE = 35, FREEZE_RADIUS = 6, WHIP_RADIUS = 3.5, SLIDE_SPEED = 0.95;
	public static final int WALL_WIDE = 5, WALL_HIGH = 3;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_BREATH = OmnitrixSfx.cue("arctiguana_breath")
			.layer(SoundEvents.POWDER_SNOW_STEP, 0.6f, 0.7f)
			.layer(SoundEvents.PLAYER_BREATH, 0.4f, 0.6f)
			.layer(SoundEvents.GLASS_HIT, 0.2f, 1.6f, 2)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_FREEZE = OmnitrixSfx.cue("arctiguana_freeze")
			.layer(SoundEvents.GLASS_PLACE, 0.6f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.4f)
			.layer(SoundEvents.PLAYER_HURT_FREEZE, 0.3f, 1.2f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_SPIT = OmnitrixSfx.cue("arctiguana_spit")
			.layer(SoundEvents.LLAMA_SPIT, 0.5f, 1.3f)
			.layer(SoundEvents.AMETHYST_CLUSTER_HIT, 0.4f, 1.6f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_WALL = OmnitrixSfx.cue("arctiguana_wall")
			.layer(SoundEvents.GLASS_PLACE, 0.6f, 0.6f)
			.layer(SoundEvents.POWDER_SNOW_PLACE, 0.6f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 0.9f, 3)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_BIG = OmnitrixSfx.cue("arctiguana_deep_freeze")
			.layer(SoundEvents.GLASS_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.PLAYER_HURT_FREEZE, 0.5f, 0.7f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.9f, 2)
			.layer(SoundEvents.POWDER_SNOW_BREAK, 0.6f, 0.6f, 4)
			.gap(6);
	public static final OmnitrixSfx.Cue SND_SLIDE = OmnitrixSfx.cue("arctiguana_slide")
			.layer(SoundEvents.POWDER_SNOW_STEP, 0.5f, 1.2f)
			.layer(SoundEvents.PLAYER_SMALL_FALL, 0.3f, 0.7f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_WHIP = OmnitrixSfx.cue("arctiguana_whip")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.8f)
			.layer(SoundEvents.TURTLE_SHAMBLE, 0.5f, 0.7f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_ARMOUR = OmnitrixSfx.cue("arctiguana_armour")
			.layer(SoundEvents.GLASS_PLACE, 0.5f, 1.1f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.3f)
			.gap(4);

	// ---------------------------------------------------------------- server-only state
	/** How many gusts of the current breath have caught each creature. */
	private static final Map<UUID, Map<Integer, Integer>> GUSTS = new ConcurrentHashMap<>();
	/** Who the current slide has already frosted. */
	private static final Map<UUID, java.util.Set<Integer>> SLID = new ConcurrentHashMap<>();
	/** The Frost Walls still standing: where, and when they melt (for the drips as they go). */
	private record Wall(ServerLevel level, List<BlockPos> blocks, long meltsAt) {
	}

	private static final Map<UUID, List<Wall>> WALLS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private ArctiguanaKit() {
	}

	public static void register() {
		Aliens.register(new ArctiguanaKit());
		AlienProjectileEntity.onHit("arctiguana_shard", (proj, hit) -> {
			if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && proj.getOwner() instanceof ServerPlayer sp) {
				BigChillKit.chill(sp, le, SPIT_FREEZE_TICKS);
				if (BigChillKit.shelled(le)) {
					OmnitrixSfx.play((ServerLevel) sp.level(), le.position(), SND_FREEZE, 0.8f, 1.1f);
				}
			}
			Vec3 at = hit.getLocation();
			((ServerLevel) proj.level()).sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.05);
		});
	}

	public static void clearSessionState() {
		GUSTS.clear();
		SLID.clear();
		WALLS.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Arctiguana right now (client-safe). */
	public static boolean isArctiguana(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// a lean, low reptile: quick and springy, not a bruiser
		return AlienStats.builder().health(34f).speed(1.2f).scale(1.0f).armor(8f, 0f).attack(5f).jump(0.3f).safeFall(5f).step(0.5f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BREATH, AbilitySlot.SLOT_1, 120), AlienMove.shift(SPIT, AbilitySlot.SLOT_1, 15),
				AlienMove.tap(WALL, AbilitySlot.SLOT_2, 240),
				AlienMove.tap(SLIDE, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(FREEZE, AbilitySlot.SLOT_4, 400),
				AlienMove.tap(ARMOUR, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(WHIP, AbilitySlot.SLOT_6, 30),
				AlienMove.vanilla(CLAWS, "Attack", 0), AlienMove.vanilla(COLD, "Passive", 0), AlienMove.vanilla(AGILE, "Passive", 0),
				AlienMove.vanilla(HEAT, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_BREATH);
			case SLOT_3 -> flag(p, F_SLIDE);
			case SLOT_5 -> flag(p, F_ARMOUR);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_ARMOUR, 0.6f, 0.9f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		GUSTS.remove(id);
		SLID.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { BREATH_UNTIL, SLIDE_UNTIL, ARMOUR_UNTIL }) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.FREEZE)) {
			return false; // Cold Blooded
		}
		if (flag(p, F_SLIDE) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		float a = amount;
		if (source.is(DamageTypeTags.IS_FIRE)) {
			a *= HEAT_TAKEN; // Heat
		}
		if (flag(p, F_ARMOUR)) {
			a *= ARMOUR_TAKEN;
			Entity d = source.getDirectEntity();
			if (d instanceof LivingEntity le && le != p && le.distanceToSqr(p) < 5 * 5) {
				// the plates bite back: a melee attacker is frosted
				BigChillKit.chill(p, le, 0);
				le.invulnerableTime = 0;
				hurt(p, le, ARMOUR_CHILL_DAMAGE, false);
				level(p).sendParticles(ParticleTypes.SNOWFLAKE, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 10, 0.25, 0.3, 0.25, 0.04);
			}
		}
		return a;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		// Cold Blooded: powder snow never frosts him
		if (p.getTicksFrozen() > 0) {
			p.setTicksFrozen(0);
		}
		if (flag(p, F_BREATH)) {
			breathTick(p, l, now);
		}
		if (flag(p, F_SLIDE)) {
			slideTick(p, l, now);
		}
		if (flag(p, F_ARMOUR)) {
			if (now >= timer(p, ARMOUR_UNTIL)) {
				setFlag(p, F_ARMOUR, false);
				setTimer(p, ARMOUR_UNTIL, 0L);
				l.sendParticles(ParticleTypes.FALLING_WATER, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.35, 0.5, 0.35, 0.0);
			} else if (now % 6 == 0) {
				l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.35, 0.5, 0.35, 0.01);
			}
		}
		wallTick(p, now);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					spit(p);
				} else {
					breath(p);
				}
			}
			case SLOT_2 -> frostWall(p);
			case SLOT_3 -> slide(p);
			case SLOT_4 -> deepFreeze(p);
			case SLOT_5 -> frostArmour(p);
			case SLOT_6 -> tailWhip(p);
			default -> {
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity e && p.getAttackStrengthScale(0.5f) > 0.5f) {
			// Frost Claws: vanilla's punch lands (+5 attack); the claws frost what they hit
			anim(p, ANIM_CLAW);
			BigChillKit.chill(p, e, 0);
			level(p).sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.25, 0.2, 0.03);
		}
		return false;
	}

	// ---------------------------------------------------------------- R: Ice Breath / Shift+R: Ice Shard Spit

	/** Where his breath leaves him. */
	public static Vec3 mouth(Player p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.35 * p.getScale())).add(0, -0.12 * p.getScale(), 0);
	}

	/** R: a 1.5 s breath of freezing air along his aim. */
	public boolean breath(ServerPlayer p) {
		if (flag(p, F_BREATH) || !use(p, BREATH)) {
			return false;
		}
		anim(p, ANIM_BREATH);
		GUSTS.put(p.getUUID(), new ConcurrentHashMap<>());
		setTimer(p, BREATH_UNTIL, p.level().getGameTime() + BREATH_TICKS);
		setFlag(p, F_BREATH, true);
		OmnitrixSfx.play(level(p), p.getEyePosition(), SND_BREATH, 1f, 1f);
		return true;
	}

	private void breathTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, BREATH_UNTIL);
		if (now >= until) {
			setFlag(p, F_BREATH, false);
			setTimer(p, BREATH_UNTIL, 0L);
			GUSTS.remove(p.getUUID());
			return;
		}
		Vec3 m = mouth(p);
		Vec3 look = p.getLookAngle();
		// the breath: a white stream of frost blown out of his mouth and fanning along the cone (count 0 = the offsets are a velocity)
		double fan = Math.tan(Math.toRadians(BREATH_HALF_ANGLE)) * 0.8;
		for (int i = 0; i < 8; i++) {
			Vec3 v = look.add((l.random.nextDouble() - 0.5) * fan, (l.random.nextDouble() - 0.5) * fan, (l.random.nextDouble() - 0.5) * fan).normalize();
			boolean puff = i % 3 == 0;
			l.sendParticles(puff ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, m.x, m.y, m.z, 0, v.x, v.y, v.z, puff ? 0.35 : 0.55);
		}
		for (int i = 0; i < 3; i++) {
			double d = 1.0 + l.random.nextDouble() * BREATH_RANGE * 0.85;
			double spread = d * 0.5;
			Vec3 at = m.add(look.scale(d)).add((l.random.nextDouble() - 0.5) * spread, (l.random.nextDouble() - 0.5) * spread, (l.random.nextDouble() - 0.5) * spread);
			l.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
		}
		long start = until - BREATH_TICKS;
		if ((now - start) % BREATH_EVERY != 0) {
			return;
		}
		OmnitrixSfx.play(l, m, SND_BREATH, 0.5f, 1.1f);
		Map<Integer, Integer> gusts = GUSTS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		for (LivingEntity e : RecalKitMath.cone(p, BREATH_RANGE, BREATH_HALF_ANGLE)) {
			boolean frosted = e.getTicksFrozen() >= e.getTicksRequiredToFreeze();
			int n = gusts.merge(e.getId(), 1, Integer::sum);
			e.invulnerableTime = 0;
			hurt(p, e, BREATH_DAMAGE, false);
			if (!BigChillKit.shelled(e) && (n >= BREATH_GUSTS_TO_FREEZE || frosted && n >= 2)) {
				freeze(p, e, FREEZE_TICKS);
			} else {
				BigChillKit.chill(p, e, 0);
			}
		}
	}

	/** Freezes {@code e} solid in a block of ice for {@code ticks} (bosses for a quarter), with the crack of it. */
	public static void freeze(ServerPlayer p, LivingEntity e, int ticks) {
		BigChillKit.freezeSolid(p, e, ticks);
		ServerLevel l = (ServerLevel) p.level();
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		QuirkFx.shards(l, c).block(Blocks.PACKED_ICE.defaultBlockState()).size(Math.max(0.8f, e.getBbHeight() * 0.5f)).count(8).life(16).spawn();
		OmnitrixSfx.play(l, c, SND_FREEZE, 1f, 1f);
	}

	/** Shift+R: an ice shard spat at the crosshair. */
	public boolean spit(ServerPlayer p) {
		if (!use(p, SPIT)) {
			return false;
		}
		anim(p, ANIM_SPIT);
		AlienProjectileEntity a = AlienProjectileEntity.of(p, "arctiguana_shard").colour(ICE).size(0.3f).damage(SPIT_DAMAGE).speed(2.2).life(30);
		Vec3 m = mouth(p);
		a.moveTo(m.x, m.y, m.z, p.getYRot(), p.getXRot());
		a.spawn();
		OmnitrixSfx.playOn(p, SND_SPIT, 0.8f, 1.1f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- G: Frost Wall

	/** G: a 5x3 wall of packed ice 2.5 blocks ahead, only into air; it melts after 8 s. Returns how many blocks went up. */
	public int frostWall(ServerPlayer p) {
		if (!p.mayBuild() || !use(p, WALL)) {
			return 0;
		}
		anim(p, ANIM_WALL);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
		Vec3 side = new Vec3(-h.z, 0, h.x);
		Vec3 c = p.position().add(h.scale(2.5));
		List<BlockPos> placed = new ArrayList<>();
		for (int w = -(WALL_WIDE / 2); w <= WALL_WIDE / 2; w++) {
			for (int y = 0; y < WALL_HIGH; y++) {
				BlockPos q = BlockPos.containing(c.x + side.x * w, p.getY() + y + 0.01, c.z + side.z * w);
				if (!l.getBlockState(q).isAir() || !l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(q)).isEmpty()) {
					continue; // only ever into empty air
				}
				if (TempBlocks.place(l, q, Blocks.PACKED_ICE.defaultBlockState(), WALL_TICKS)) {
					placed.add(q.immutable());
					l.sendParticles(ParticleTypes.SNOWFLAKE, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
				}
			}
		}
		if (!placed.isEmpty()) {
			WALLS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Wall(l, placed, p.level().getGameTime() + WALL_TICKS));
		}
		QuirkFx.ring(l, c.add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size(3.2f).thickness(0.25f).life(12).ground().spawn();
		OmnitrixSfx.play(l, c, SND_WALL, 1f, 1f);
		return placed.size();
	}

	/** The walls drip as they melt (the last 1.5 s); a melted one is forgotten. */
	private static void wallTick(ServerPlayer p, long now) {
		List<Wall> ws = WALLS.get(p.getUUID());
		if (ws == null) {
			return;
		}
		ws.removeIf(w -> now > w.meltsAt + 2);
		for (Wall w : ws) {
			if (w.meltsAt - now < 30 && now % 4 == 0) {
				for (BlockPos q : w.blocks) {
					w.level.sendParticles(ParticleTypes.FALLING_WATER, q.getX() + 0.5, q.getY() + 0.1, q.getZ() + 0.5, 1, 0.35, 0.05, 0.35, 0.0);
				}
			}
			if (w.meltsAt == now) {
				for (BlockPos q : w.blocks) {
					w.level.sendParticles(ParticleTypes.SPLASH, q.getX() + 0.5, q.getY() + 0.3, q.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
				}
			}
		}
		if (ws.isEmpty()) {
			WALLS.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- X: Ice Slide

	/** X: a frictionless belly slide along his aim, leaving frost. */
	public boolean slide(ServerPlayer p) {
		if (flag(p, F_SLIDE) || !use(p, SLIDE)) {
			return false;
		}
		anim(p, ANIM_SLIDE);
		SLID.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		setTimer(p, SLIDE_UNTIL, p.level().getGameTime() + SLIDE_TICKS);
		setFlag(p, F_SLIDE, true);
		Vec3 h = slideDir(p);
		p.setDeltaMovement(h.x * SLIDE_SPEED, Math.max(0.1, p.getDeltaMovement().y), h.z * SLIDE_SPEED);
		p.hurtMarked = true;
		OmnitrixSfx.play(level(p), p.position(), SND_SLIDE, 1f, 1f);
		return true;
	}

	private static Vec3 slideDir(Player p) {
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		return h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	private void slideTick(ServerPlayer p, ServerLevel l, long now) {
		if (now >= timer(p, SLIDE_UNTIL)) {
			setFlag(p, F_SLIDE, false);
			setTimer(p, SLIDE_UNTIL, 0L);
			anim(p, ANIM_SLIDE_END);
			SLID.remove(p.getUUID());
			return;
		}
		Vec3 h = slideDir(p);
		// frictionless: the speed is held up for the whole slide, and he can steer it
		p.setDeltaMovement(h.x * SLIDE_SPEED, p.getDeltaMovement().y, h.z * SLIDE_SPEED);
		p.hurtMarked = true;
		p.resetFallDistance();
		l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.02);
		if (now % 3 == 0) {
			QuirkFx.decal(l, p.position().add(0, 0.02, 0)).colours(ICE_PALE, ICE).size(1.2f).life(60).spawn();
		}
		java.util.Set<Integer> hit = SLID.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		for (LivingEntity e : around(p, p.position().add(0, 0.5, 0), 1.4)) {
			if (hit.add(e.getId())) {
				e.invulnerableTime = 0;
				hurt(p, e, SLIDE_DAMAGE, false);
				BigChillKit.chill(p, e, 0);
				shove(e, p.position(), 0.5, 0.3);
			}
		}
		if (now % 6 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_SLIDE, 0.6f, 1.2f);
		}
	}

	// ---------------------------------------------------------------- Z: Deep Freeze

	/** Z: a burst of cold round him -- everything near is hurt and frozen solid. Returns how many it caught. */
	public int deepFreeze(ServerPlayer p) {
		if (!use(p, FREEZE)) {
			return -1;
		}
		anim(p, ANIM_FREEZE);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 0.6, 0);
		int n = 0;
		for (LivingEntity e : around(p, c, FREEZE_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, FREEZE_DAMAGE, false);
			freeze(p, e, FREEZE_TICKS);
			n++;
		}
		// a cold shockwave (no fire shell): a ground ring, a standing ring, a frost pool, ice shards and ice spikes round him
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SHOCK).colours(ICE_PALE, ICE).size((float) FREEZE_RADIUS + 1f).thickness(0.9f).life(16).ground()
				.ease(QuirkFx.Ease.OUT_EXPO).spawn();
		QuirkFx.ring(l, c.add(0, 0.4, 0)).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 1, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE_DEEP)
				.size((float) FREEZE_RADIUS).thickness(0.6f).life(14).spawn();
		QuirkFx.decal(l, p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0xD8F2FF).size((float) FREEZE_RADIUS * 0.85f).life(160).spawn();
		QuirkFx.shards(l, c).block(Blocks.BLUE_ICE.defaultBlockState()).size(3f).count(20).life(26).spawn();
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + 0.3;
			double r = 2.2 + (i % 2) * 1.6;
			QuirkFx.spike(l, p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4))
					.block(Blocks.PACKED_ICE.defaultBlockState()).colours(ICE_PALE, ICE).size(1.4f + (i % 3) * 0.4f).size2(0.35f).life(50).spawn();
		}
		l.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 60, FREEZE_RADIUS * 0.4, 0.6, FREEZE_RADIUS * 0.4, 0.06);
		OmnitrixSfx.play(l, c, SND_BIG, 1.2f, 1f);
		return n;
	}

	// ---------------------------------------------------------------- C: Tail Whip

	/** C: the tail swung right round him. Returns how many it hit. */
	public int tailWhip(ServerPlayer p) {
		if (!use(p, WHIP)) {
			return -1;
		}
		anim(p, ANIM_WHIP);
		int n = 0;
		for (LivingEntity e : around(p, p.position().add(0, 0.6, 0), WHIP_RADIUS)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, WHIP_DAMAGE, false)) {
				shove(e, p.position(), 1.0, 0.3);
				n++;
			}
		}
		Vec3 back = p.position().add(0, 0.5, 0).subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2));
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, back.x, back.y, back.z, 1, 0, 0, 0, 0);
		QuirkFx.ring(level(p), p.position().add(0, 0.5, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size((float) WHIP_RADIUS).thickness(0.15f).life(6).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_WHIP, 1f, 1f);
		return n;
	}

	// ---------------------------------------------------------------- V: Frost Armour

	/** V: 10 s of ice plates. */
	public boolean frostArmour(ServerPlayer p) {
		if (flag(p, F_ARMOUR) || !use(p, ARMOUR)) {
			return false;
		}
		anim(p, ANIM_ARMOUR);
		setTimer(p, ARMOUR_UNTIL, p.level().getGameTime() + ARMOUR_TICKS);
		setFlag(p, F_ARMOUR, true);
		QuirkFx.ring(level(p), p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size(1.8f).thickness(0.2f).life(10).spawn();
		level(p).sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		OmnitrixSfx.play(level(p), p.position(), SND_ARMOUR, 1f, 1f);
		return true;
	}
}
