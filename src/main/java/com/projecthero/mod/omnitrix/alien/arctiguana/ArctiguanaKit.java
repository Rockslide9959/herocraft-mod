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
 *   R          Ice Shard Spit  (v0.17.4: was Shift+R) a spat shard (7), frosts what it hits; a frosted creature it hits freezes solid for 2 s
 *   Shift+R    Shard Fan       (v0.17.4) five shards in a spread: 5 each, frosting
 *   Hold R     Ice Breath      (v0.17.4: was tap R, 1.5 s) breathed while R is held (up to 3 s), a 7-block cone: 3 every quarter second, slowed and
 *                              frosted; a creature caught by 3 gusts (or already frosted) is frozen solid in a block of ice for 3 s
 *   Shift+G    Ice Pillar      (v0.17.4) a pillar of ice bursts up under the creature he looks at (16): 13, thrown up
 *   Hold G     Ice Path        (v0.17.4) while held, a strip of ice forms under and ahead of him, even over water and gaps (melts after 10 s)
 *   Shift+X    Wall Cling      (v0.17.4) grip the wall he faces for up to 5 s and climb it (Jump leaps off)
 *   Hold X     Long Skate      (v0.17.4) the slide held on (up to 3 s), a frost trail, 4 to things passed
 *   Shift+Z    Shatter         (v0.17.4) every creature frozen solid within 12 shatters: 14 each, the ice ends
 *   Hold Z     Blizzard        (v0.17.4) a snowstorm round him while held (8 blocks, up to 3 s): 2 every half second, slowed, frosted
 *   Shift+V    Ice Decoy       (v0.17.4) an ice statue of him: mobs within 16 go for it for 5 s; it bursts into frost (6 within 3)
 *   Shift+C    Frozen Tail Spike (v0.17.4) an ice-coated tail thrust (4 blocks): 12, frosted
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
	public static final int F_BREATH = 1, F_SLIDE = 2, F_ARMOUR = 4, F_PATH = 8, F_CLING = 16, F_SKATE = 32, F_BLIZZARD = 64;
	public static final int ANIM_BREATH = 1, ANIM_SPIT = 2, ANIM_WALL = 3, ANIM_FREEZE = 4, ANIM_SLIDE = 5, ANIM_WHIP = 6, ANIM_ARMOUR = 7, ANIM_CLAW = 8,
			ANIM_SLIDE_END = 9, ANIM_FAN = 10, ANIM_PILLAR = 11, ANIM_PATH = 12, ANIM_CLING = 13, ANIM_SHATTER = 14, ANIM_BLIZZARD = 15, ANIM_DECOY = 16,
			ANIM_SPIKE = 17;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.arctiguana.<id>)
	public static final String BREATH = "ice_breath", SPIT = "ice_shard_spit", FAN = "shard_fan", WALL = "frost_wall", PILLAR = "ice_pillar",
			PATH = "ice_path", FREEZE = "deep_freeze", SHATTER = "shatter", BLIZZARD = "blizzard", SLIDE = "ice_slide", CLING = "wall_cling",
			SKATE = "long_skate", WHIP = "tail_whip", SPIKE = "frozen_tail_spike", ARMOUR = "frost_armour", DECOY_ID = "ice_decoy", CLAWS = "frost_claws", COLD = "cold_blooded", AGILE = "reptile_agility", HEAT = "heat_weakness";
	public static final String BREATH_UNTIL = "breath_until", SLIDE_UNTIL = "slide_until", ARMOUR_UNTIL = "armour_until", PATH_AT = "path_at",
			CLING_AT = "cling_at", BLIZZARD_AT = "blizzard_at";
	private static final String[] TIMERS = { BREATH_UNTIL, SLIDE_UNTIL, ARMOUR_UNTIL, PATH_AT, CLING_AT, BLIZZARD_AT };

	// ---------------------------------------------------------------- numbers (quick 5-8, normal 12-17, big 17-20)
	public static final float BREATH_DAMAGE = 3f, SPIT_DAMAGE = 7f, FREEZE_DAMAGE = 17f, SLIDE_DAMAGE = 4f, WHIP_DAMAGE = 8f, ARMOUR_CHILL_DAMAGE = 2f;
	public static final float ARMOUR_TAKEN = 0.6f, HEAT_TAKEN = 1.5f, FAN_DAMAGE = 5f, PILLAR_DAMAGE = 13f, SHATTER_DAMAGE = 14f, BLIZZARD_DAMAGE = 2f,
			DECOY_BURST = 6f, SPIKE_DAMAGE = 12f;
	/** v0.17.4: the holds' longest runs (the breath is a hold now: up to 3 s), the pulses, the decoy's life, the path's melt (ticks). */
	public static final int BREATH_MAX = 60, PATH_MAX = 120, PATH_MELT = 200, CLING_MAX = 100, SKATE_MAX = 60, BLIZZARD_MAX = 60, BLIZZARD_EVERY = 10,
			DECOY_TICKS = 100, FAN_SHARDS = 5;
	public static final double FAN_HALF_ANGLE = 20, PILLAR_RANGE = 16, SHATTER_RADIUS = 12, BLIZZARD_RADIUS = 8, DECOY_LURE = 16, DECOY_BURST_RADIUS = 3,
			SPIKE_RANGE = 4, SPIKE_HALF_ANGLE = 25;
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
	/** v0.17.4: his Ice Decoy (an entity id) and the mobs it has lured (ids), so they are held on it for its 5 s. */
	private static final Map<UUID, Integer> DECOYS = new ConcurrentHashMap<>();
	private static final Map<UUID, java.util.Set<Integer>> LURED = new ConcurrentHashMap<>();
	/** v0.17.4 Wall Cling: ticks in a row with no wall beside him. */
	private static final Map<UUID, Integer> OFF_WALL = new ConcurrentHashMap<>();
	/** v0.17.4 Wall Cling, client side: when the local player last leapt off (the grip lets go at once). */
	public static volatile long clientLeapAt = -100;

	public static net.minecraft.world.entity.EntityType<ArctiguanaDecoyEntity> DECOY;

	private ArctiguanaKit() {
	}

	public static void register() {
		DECOY = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE,
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, com.projecthero.mod.ProjectHeroMod.id("arctiguana_decoy")),
				net.minecraft.world.entity.EntityType.Builder.<ArctiguanaDecoyEntity>of(ArctiguanaDecoyEntity::new, net.minecraft.world.entity.MobCategory.MISC)
						.sized(0.6f, 1.8f).noSave().clientTrackingRange(8).build("arctiguana_decoy"));
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(DECOY, ArctiguanaDecoyEntity.createAttributes());
		AlienProjectileEntity.onHit("arctiguana_fan", (proj, hit) -> {
			if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && proj.getOwner() instanceof ServerPlayer sp) {
				BigChillKit.chill(sp, le, 0); // a fan shard only frosts
			}
			Vec3 at = hit.getLocation();
			((ServerLevel) proj.level()).sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 5, 0.12, 0.12, 0.12, 0.04);
		});
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
		DECOYS.clear();
		LURED.clear();
		OFF_WALL.clear();
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
		return List.of(AlienMove.tap(SPIT, AbilitySlot.SLOT_1, 15), AlienMove.shift(FAN, AbilitySlot.SLOT_1, 80), AlienMove.hold(BREATH, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(WALL, AbilitySlot.SLOT_2, 240), AlienMove.shift(PILLAR, AbilitySlot.SLOT_2, 160), AlienMove.hold(PATH, AbilitySlot.SLOT_2, 0),
				AlienMove.tap(SLIDE, AbilitySlot.SLOT_3, 60), AlienMove.shift(CLING, AbilitySlot.SLOT_3, 80), AlienMove.hold(SKATE, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(FREEZE, AbilitySlot.SLOT_4, 400), AlienMove.shift(SHATTER, AbilitySlot.SLOT_4, 240), AlienMove.hold(BLIZZARD, AbilitySlot.SLOT_4, 480),
				AlienMove.tap(ARMOUR, AbilitySlot.SLOT_5, 400), AlienMove.shift(DECOY_ID, AbilitySlot.SLOT_5, 360),
				AlienMove.tap(WHIP, AbilitySlot.SLOT_6, 30), AlienMove.shift(SPIKE, AbilitySlot.SLOT_6, 120),
				AlienMove.vanilla(CLAWS, "Attack", 0), AlienMove.vanilla(COLD, "Passive", 0), AlienMove.vanilla(AGILE, "Passive", 0),
				AlienMove.vanilla(HEAT, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_BREATH);
			case SLOT_2 -> flag(p, F_PATH);
			case SLOT_3 -> flag(p, F_SLIDE) || flag(p, F_CLING);
			case SLOT_4 -> flag(p, F_BLIZZARD);
			case SLOT_5 -> flag(p, F_ARMOUR);
			default -> false;
		};
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_3 || slot == AbilitySlot.SLOT_4;
	}

	/**
	 * v0.17.4 Wall Cling: while it holds and a wall is beside him, the wall is a ladder (pressing in climbs, Sneak holds still). Both sides
	 * answer from the synced flag; on his own client a leap off lets go at once.
	 */
	@Override
	public boolean climbs(Player p) {
		if ((Omnitrix.state(p).kitFlags & F_CLING) == 0) {
			return false;
		}
		if (p.level().isClientSide && p.level().getGameTime() - clientLeapAt < 12) {
			return false;
		}
		return wallNormal(p) != null;
	}

	/** The way out from the wall he faces (or stands beside), or null with none there (both sides). */
	@org.jetbrains.annotations.Nullable
	public static Vec3 wallNormal(Player p) {
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
		Vec3[] dirs = { f, new Vec3(-f.z, 0, f.x), new Vec3(f.z, 0, -f.x), f.scale(-1) };
		double reach = p.getBbWidth() * 0.5 + 0.3;
		for (Vec3 d : dirs) {
			for (double y : new double[] { 0.2, 1.0 }) {
				BlockPos q = BlockPos.containing(p.getX() + d.x * reach, p.getY() + y, p.getZ() + d.z * reach);
				if (!p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty()) {
					return d.scale(-1);
				}
			}
		}
		return null;
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.arctiguana." + key)
				.withStyle(net.minecraft.ChatFormatting.GRAY), true);
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
		OFF_WALL.remove(id);
		LURED.remove(id);
		Integer d = DECOYS.remove(id);
		if (d != null && p.level().getEntity(d) instanceof ArctiguanaDecoyEntity e) {
			e.discard();
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : TIMERS) {
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
		if ((flag(p, F_SLIDE) || flag(p, F_CLING)) && source.is(DamageTypeTags.IS_FALL)) {
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
		if (flag(p, F_PATH)) {
			pathTick(p, l, now);
		}
		if (flag(p, F_CLING)) {
			clingTick(p, l, now);
		}
		if (flag(p, F_BLIZZARD)) {
			blizzardTick(p, l, now);
		}
		if (DECOYS.containsKey(p.getUUID())) {
			decoyTick(p, l, now);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					shardFan(p);
				} else {
					spit(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					icePillar(p);
				} else {
					frostWall(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					wallCling(p);
				} else {
					slide(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					shatter(p);
				} else {
					deepFreeze(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					iceDecoy(p);
				} else {
					frostArmour(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					tailSpike(p);
				} else {
					tailWhip(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		switch (slot) {
			case SLOT_1 -> breath(p);
			case SLOT_2 -> startPath(p);
			case SLOT_3 -> startSkate(p);
			case SLOT_4 -> startBlizzard(p);
			default -> {
			}
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		switch (slot) {
			case SLOT_1 -> stopBreath(p);
			case SLOT_2 -> endPath(p);
			case SLOT_3 -> {
				if (flag(p, F_SKATE)) {
					endSlide(p);
				}
			}
			case SLOT_4 -> endBlizzard(p);
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

	/** Hold R (v0.17.4: was a 1.5 s tap): freezing breath along his aim while R is held, up to 3 s; the 6 s cooldown runs from the release. */
	public boolean breath(ServerPlayer p) {
		if (flag(p, F_BREATH)) {
			return false;
		}
		if (!ready(p, BREATH)) {
			return use(p, BREATH);
		}
		anim(p, ANIM_BREATH);
		GUSTS.put(p.getUUID(), new ConcurrentHashMap<>());
		setTimer(p, BREATH_UNTIL, p.level().getGameTime() + BREATH_MAX);
		setFlag(p, F_BREATH, true);
		OmnitrixSfx.play(level(p), p.getEyePosition(), SND_BREATH, 1f, 1f);
		return true;
	}

	/** The breath stops (let go, or 3 s up); the cooldown starts now. */
	public void stopBreath(ServerPlayer p) {
		if (!flag(p, F_BREATH)) {
			return;
		}
		setFlag(p, F_BREATH, false);
		setTimer(p, BREATH_UNTIL, 0L);
		GUSTS.remove(p.getUUID());
		cooldown(p, BREATH, -1);
		anim(p, ANIM_SLIDE_END);
	}

	private void breathTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, BREATH_UNTIL);
		if (now >= until) {
			stopBreath(p);
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
		long start = until - BREATH_MAX;
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

	/** R (v0.17.4: was Shift+R): an ice shard spat at the crosshair. */
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
			endSlide(p);
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

	/** Ends a slide or a Long Skate (the skate's cooldown runs from here). */
	private void endSlide(ServerPlayer p) {
		if (!flag(p, F_SLIDE)) {
			return;
		}
		boolean skate = flag(p, F_SKATE);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags &= ~(F_SLIDE | F_SKATE);
		n.timers.remove(key(SLIDE_UNTIL));
		Omnitrix.save(p, n);
		anim(p, ANIM_SLIDE_END);
		SLID.remove(p.getUUID());
		if (skate) {
			cooldown(p, SKATE, -1);
		}
	}

	/** Hold X (v0.17.4): Long Skate -- the frictionless slide kept going while X is held (up to 3 s), frosting and hitting what it passes. */
	public boolean startSkate(ServerPlayer p) {
		if (flag(p, F_SLIDE) || flag(p, F_CLING)) {
			return false;
		}
		if (!ready(p, SKATE)) {
			return use(p, SKATE);
		}
		anim(p, ANIM_SLIDE);
		SLID.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(SLIDE_UNTIL), p.level().getGameTime() + SKATE_MAX);
		n.kitFlags |= F_SLIDE | F_SKATE;
		Omnitrix.save(p, n);
		Vec3 h = slideDir(p);
		p.setDeltaMovement(h.x * SLIDE_SPEED, Math.max(0.1, p.getDeltaMovement().y), h.z * SLIDE_SPEED);
		p.hurtMarked = true;
		OmnitrixSfx.play(level(p), p.position(), SND_SLIDE, 1f, 0.9f);
		return true;
	}

	/** Shift+X (v0.17.4): Wall Cling -- he grips the wall he faces (or stands beside) for up to 5 s and can climb it; Jump leaps off. */
	public boolean wallCling(ServerPlayer p) {
		if (flag(p, F_CLING)) {
			endCling(p); // a second press lets go
			return true;
		}
		if (wallNormal(p) == null) {
			say(p, "no_wall");
			return false;
		}
		if (!use(p, CLING)) {
			return false;
		}
		anim(p, ANIM_CLING);
		OFF_WALL.remove(p.getUUID());
		setTimer(p, CLING_AT, p.level().getGameTime());
		setFlag(p, F_CLING, true);
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3));
		p.hurtMarked = true;
		p.resetFallDistance();
		level(p).sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_ARMOUR, 0.6f, 1.3f);
		return true;
	}

	private void clingTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, CLING_AT);
		int off = wallNormal(p) == null ? OFF_WALL.merge(p.getUUID(), 1, Integer::sum) : 0;
		if (off == 0) {
			OFF_WALL.remove(p.getUUID());
		}
		if (age >= CLING_MAX || off > 4 || (age > 10 && p.onGround() && off > 0)) {
			endCling(p);
			return;
		}
		p.resetFallDistance();
		if (now % 8 == 0) {
			Vec3 out = wallNormal(p);
			if (out != null) {
				Vec3 at = p.position().subtract(out.scale(0.4)).add(0, 1.0, 0);
				l.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.1, 0.4, 0.1, 0.0);
			}
		}
	}

	public void endCling(ServerPlayer p) {
		if (!flag(p, F_CLING)) {
			return;
		}
		setFlag(p, F_CLING, false);
		setTimer(p, CLING_AT, 0L);
		OFF_WALL.remove(p.getUUID());
		anim(p, ANIM_SLIDE_END);
	}

	/** {@code p} grips a wall right now (client-safe). */
	public static boolean clinging(Player p) {
		return isArctiguana(p) && (Omnitrix.state(p).kitFlags & F_CLING) != 0;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+R Shard Fan, Shift+G Ice Pillar, Hold G Ice Path

	/** Shift+R: five shards spat in a spread: 5 each, frosting. */
	public boolean shardFan(ServerPlayer p) {
		if (!use(p, FAN)) {
			return false;
		}
		anim(p, ANIM_FAN);
		Vec3 m = mouth(p);
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < FAN_SHARDS; i++) {
			double yaw = (i - (FAN_SHARDS - 1) / 2.0) * (FAN_HALF_ANGLE * 2 / (FAN_SHARDS - 1));
			AlienProjectileEntity a = AlienProjectileEntity.of(p, "arctiguana_fan").colour(ICE).size(0.25f).damage(FAN_DAMAGE).speed(2.0).life(16);
			a.moveTo(m.x, m.y, m.z, p.getYRot(), p.getXRot());
			a.direction(RecalKitMath.turned(look, yaw)).spawn();
		}
		level(p).sendParticles(ParticleTypes.SNOWFLAKE, m.x, m.y, m.z, 10, 0.2, 0.1, 0.2, 0.08);
		OmnitrixSfx.playOn(p, SND_SPIT, 0.8f, 0.9f, net.minecraft.sounds.SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, SND_SPIT, 0.6f, 1.2f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: a pillar of ice bursts up under the creature he looks at (16): 13, thrown up (a boss stays put). */
	public boolean icePillar(ServerPlayer p) {
		LivingEntity t = lookTarget(p, PILLAR_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, PILLAR)) {
			return false;
		}
		anim(p, ANIM_PILLAR);
		t.invulnerableTime = 0;
		hurt(p, t, PILLAR_DAMAGE, false);
		if (!com.projecthero.mod.omnitrix.alien.AlienStatus.isBoss(t)) {
			double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
			t.setDeltaMovement(t.getDeltaMovement().x * 0.3, 0.75 + 0.45 * Math.max(0.3, res), t.getDeltaMovement().z * 0.3);
			t.hurtMarked = true;
		}
		ServerLevel l = level(p);
		Vec3 base = t.position();
		QuirkFx.spike(l, base.subtract(0, 0.3, 0), new Vec3(0, 1, 0)).block(Blocks.PACKED_ICE.defaultBlockState()).colours(ICE_PALE, ICE).size(2.6f).size2(0.7f).life(40)
				.spawn();
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + 0.4;
			QuirkFx.spike(l, base.add(Math.cos(a) * 0.8, -0.2, Math.sin(a) * 0.8), new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5)).block(Blocks.PACKED_ICE.defaultBlockState())
					.colours(ICE_PALE, ICE).size(1.2f).size2(0.3f).life(36).spawn();
		}
		QuirkFx.shards(l, base.add(0, 0.6, 0)).block(Blocks.PACKED_ICE.defaultBlockState()).size(1.4f).count(12).life(18).spawn();
		l.sendParticles(ParticleTypes.SNOWFLAKE, base.x, base.y + 0.5, base.z, 30, 0.5, 1.0, 0.5, 0.08);
		OmnitrixSfx.play(l, base, SND_WALL, 1f, 1.2f);
		OmnitrixSfx.play(l, base, SND_FREEZE, 0.8f, 0.8f);
		return true;
	}

	/** Hold G: Ice Path -- while G is held (up to 6 s), ice forms under him and ahead of him, over water and gaps; it melts after 10 s. */
	public boolean startPath(ServerPlayer p) {
		if (flag(p, F_PATH)) {
			return false;
		}
		if (!p.mayBuild()) {
			return false;
		}
		anim(p, ANIM_PATH);
		setTimer(p, PATH_AT, p.level().getGameTime());
		setFlag(p, F_PATH, true);
		OmnitrixSfx.play(level(p), p.position(), SND_WALL, 0.7f, 1.3f);
		return true;
	}

	private void pathTick(ServerPlayer p, ServerLevel l, long now) {
		if (now - timer(p, PATH_AT) >= PATH_MAX) {
			endPath(p);
			return;
		}
		Vec3 f = slideDir(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		int y = (int) Math.floor(p.getY() - 0.01) - 1;
		if (p.isInWater()) {
			y = (int) Math.floor(p.getY() + 0.3); // swimming: lay it at the surface, under him
		}
		int laid = 0;
		for (double ahead = 0; ahead <= 2.0; ahead += 1.0) {
			for (int w = -1; w <= 1; w++) {
				double x = p.getX() + f.x * ahead + side.x * w * 0.9, z = p.getZ() + f.z * ahead + side.z * w * 0.9;
				BlockPos q = BlockPos.containing(x, y + 0.5, z);
				net.minecraft.world.level.block.state.BlockState st = l.getBlockState(q);
				if ((st.isAir() || st.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) && TempBlocks.place(l, q, Blocks.PACKED_ICE.defaultBlockState(), PATH_MELT)) {
					laid++;
					if (now % 2 == 0) {
						l.sendParticles(ParticleTypes.SNOWFLAKE, q.getX() + 0.5, q.getY() + 1.05, q.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.01);
					}
				}
			}
		}
		if (laid > 0 && now % 4 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_WALL, 0.35f, 1.5f);
		}
	}

	public void endPath(ServerPlayer p) {
		if (!flag(p, F_PATH)) {
			return;
		}
		setFlag(p, F_PATH, false);
		setTimer(p, PATH_AT, 0L);
		anim(p, ANIM_SLIDE_END);
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+Z Shatter, Hold Z Blizzard

	/**
	 * Shift+Z: every creature frozen solid within 12 (that he can see) shatters out of its ice: 14 each, and the ice is gone. Nothing frozen:
	 * nothing happens and no cooldown is spent. Returns how many shattered (-1 if not ready).
	 */
	public int shatter(ServerPlayer p) {
		List<LivingEntity> frozen = new ArrayList<>();
		for (LivingEntity e : around(p, p.position(), SHATTER_RADIUS)) {
			if (BigChillKit.shelled(e) && p.hasLineOfSight(e)) {
				frozen.add(e);
			}
		}
		if (frozen.isEmpty()) {
			if (cooldownLeft(p, SHATTER) > 0) {
				use(p, SHATTER);
				return -1;
			}
			say(p, "nothing_frozen");
			return 0;
		}
		if (!use(p, SHATTER)) {
			return -1;
		}
		anim(p, ANIM_SHATTER);
		ServerLevel l = level(p);
		for (LivingEntity e : frozen) {
			// out of the shell (still frosted), the root gone
			e.setTicksFrozen(BigChillKit.CHILL);
			net.minecraft.world.effect.MobEffectInstance slow = e.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
			if (slow != null && slow.getAmplifier() >= 9) {
				e.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
			}
			e.invulnerableTime = 0;
			hurt(p, e, SHATTER_DAMAGE, false);
			Vec3 c = e.getBoundingBox().getCenter();
			QuirkFx.shards(l, c).block(Blocks.PACKED_ICE.defaultBlockState()).size(Math.max(1.0f, e.getBbHeight() * 0.6f)).count(16).life(24).spawn();
			QuirkFx.ring(l, c).flags(QuirkFx.F_VERTICAL).vec(c.subtract(p.getEyePosition())).colours(ICE_PALE, ICE).size(1.6f).thickness(0.3f).life(8).spawn();
			OmnitrixSfx.play(l, c, SND_BIG, 0.9f, 1.3f);
		}
		return frozen.size();
	}

	/** Hold Z: Blizzard -- a snowstorm round him while Z is held (8 blocks, up to 3 s): 2 every half second, slowed and frosted. */
	public boolean startBlizzard(ServerPlayer p) {
		if (flag(p, F_BLIZZARD)) {
			return false;
		}
		if (!ready(p, BLIZZARD)) {
			return use(p, BLIZZARD);
		}
		anim(p, ANIM_BLIZZARD);
		setTimer(p, BLIZZARD_AT, p.level().getGameTime());
		setFlag(p, F_BLIZZARD, true);
		OmnitrixSfx.play(level(p), p.position(), SND_BREATH, 1f, 0.7f);
		return true;
	}

	private void blizzardTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, BLIZZARD_AT);
		if (age >= BLIZZARD_MAX) {
			endBlizzard(p);
			return;
		}
		// the storm: snow whirling round him, thicker as it builds
		double k = Math.min(1.0, age / 15.0);
		for (int i = 0; i < 6 + (int) (10 * k); i++) {
			double a = l.random.nextDouble() * Math.PI * 2, d = 1 + l.random.nextDouble() * (BLIZZARD_RADIUS - 1);
			Vec3 at = p.position().add(Math.cos(a) * d, 0.2 + l.random.nextDouble() * 2.5, Math.sin(a) * d);
			Vec3 v = new Vec3(-Math.sin(a), -0.1, Math.cos(a)).scale(0.5);
			l.sendParticles(i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 0, v.x, v.y, v.z, i % 3 == 0 ? 0.2 : 0.6);
		}
		if (age % BLIZZARD_EVERY == 0) {
			for (LivingEntity e : around(p, p.position(), BLIZZARD_RADIUS)) {
				e.invulnerableTime = 0;
				hurt(p, e, BLIZZARD_DAMAGE, false);
				BigChillKit.chill(p, e, 0);
			}
			OmnitrixSfx.play(l, p.position(), SND_BREATH, 0.5f, 0.6f + 0.1f * (float) k);
		}
	}

	public void endBlizzard(ServerPlayer p) {
		if (!flag(p, F_BLIZZARD)) {
			return;
		}
		setFlag(p, F_BLIZZARD, false);
		setTimer(p, BLIZZARD_AT, 0L);
		cooldown(p, BLIZZARD, -1);
		anim(p, ANIM_SLIDE_END);
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+V Ice Decoy

	/** Shift+V: he leaves an ice statue of himself where he stands; mobs within 16 go for it for 5 s, and it bursts into frost. */
	public boolean iceDecoy(ServerPlayer p) {
		if (!use(p, DECOY_ID)) {
			return false;
		}
		anim(p, ANIM_DECOY);
		Integer old = DECOYS.remove(p.getUUID());
		if (old != null && p.level().getEntity(old) instanceof ArctiguanaDecoyEntity e) {
			e.discard();
		}
		ArctiguanaDecoyEntity d = ArctiguanaDecoyEntity.spawn(p);
		DECOYS.put(p.getUUID(), d.getId());
		LURED.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		ServerLevel l = level(p);
		QuirkFx.shards(l, p.position().add(0, 1, 0)).block(Blocks.PACKED_ICE.defaultBlockState()).size(1.6f).count(10).life(12).flags(QuirkFx.F_REASSEMBLE).spawn();
		l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		OmnitrixSfx.play(l, p.position(), SND_WALL, 1f, 1.1f);
		lure(p, l, d);
		return true;
	}

	/** Hostile mobs round the statue (and anything hunting him) are set on it. */
	private void lure(ServerPlayer p, ServerLevel l, ArctiguanaDecoyEntity d) {
		java.util.Set<Integer> lured = LURED.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		for (net.minecraft.world.entity.Mob m : l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, d.getBoundingBox().inflate(DECOY_LURE))) {
			if (!m.isAlive() || com.projecthero.mod.omnitrix.alien.AlienStatus.isBoss(m) || com.projecthero.mod.omnitrix.OmnitrixRiding.partners(p, m)) {
				continue;
			}
			boolean hostile = m instanceof net.minecraft.world.entity.monster.Enemy || m.getTarget() == p || lured.contains(m.getId());
			if (hostile && (m.getTarget() == null || m.getTarget() == p || lured.contains(m.getId())) && m.getTarget() != d) {
				m.setTarget(d);
				lured.add(m.getId());
			}
		}
	}

	private void decoyTick(ServerPlayer p, ServerLevel l, long now) {
		Integer id = DECOYS.get(p.getUUID());
		if (!(l.getEntity(id) instanceof ArctiguanaDecoyEntity d) || !d.isAlive()) {
			DECOYS.remove(p.getUUID());
			LURED.remove(p.getUUID());
			return;
		}
		lure(p, l, d);
		if (now % 10 == 0) {
			l.sendParticles(ParticleTypes.SNOWFLAKE, d.getX(), d.getY() + 1.0, d.getZ(), 2, 0.3, 0.6, 0.3, 0.01);
		}
	}

	/** The statue bursts into frost (broken, or its 5 s are up): 6 within 3, frosted; the lured mobs come to their senses. */
	public static void decoyBurst(ServerPlayer p, ArctiguanaDecoyEntity d) {
		ServerLevel l = (ServerLevel) d.level();
		Vec3 c = d.position().add(0, 1.0, 0);
		for (LivingEntity e : around(p, c, DECOY_BURST_RADIUS)) {
			if (e == d) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, DECOY_BURST, false);
			BigChillKit.chill(p, e, 0);
		}
		java.util.Set<Integer> lured = LURED.remove(p.getUUID());
		if (lured != null) {
			for (Integer m : lured) {
				if (l.getEntity(m) instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == d) {
					mob.setTarget(null);
				}
			}
		}
		DECOYS.remove(p.getUUID(), d.getId());
		QuirkFx.shards(l, c).block(Blocks.PACKED_ICE.defaultBlockState()).size(1.8f).count(18).life(22).spawn();
		QuirkFx.ring(l, d.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(ICE_PALE, ICE).size((float) DECOY_BURST_RADIUS).thickness(0.4f).life(10).ground()
				.spawn();
		l.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 40, 0.8, 0.8, 0.8, 0.08);
		OmnitrixSfx.play(l, c, SND_BIG, 0.9f, 1.2f);
	}

	/** Test hook: his statue standing right now, or null. */
	@org.jetbrains.annotations.Nullable
	public static ArctiguanaDecoyEntity decoy(ServerPlayer p) {
		Integer id = DECOYS.get(p.getUUID());
		return id != null && p.level().getEntity(id) instanceof ArctiguanaDecoyEntity d ? d : null;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+C Frozen Tail Spike

	/** Shift+C: his tail, coated in ice, thrust out ahead (4 blocks): 12, frosted. Returns how many it hit. */
	public int tailSpike(ServerPlayer p) {
		if (!use(p, SPIKE)) {
			return -1;
		}
		anim(p, ANIM_SPIKE);
		int n = 0;
		for (LivingEntity e : RecalKitMath.cone(p, SPIKE_RANGE, SPIKE_HALF_ANGLE)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, SPIKE_DAMAGE, false)) {
				BigChillKit.chill(p, e, 0);
				shove(e, p.position(), 0.6, 0.15);
				n++;
			}
		}
		ServerLevel l = level(p);
		Vec3 f = slideDir(p);
		Vec3 from = p.position().add(0, 0.9, 0).add(f.scale(0.3));
		QuirkFx.spike(l, from, f.add(0, 0.05, 0)).block(Blocks.PACKED_ICE.defaultBlockState()).colours(ICE_PALE, ICE).size((float) SPIKE_RANGE).size2(0.3f).life(12)
				.spawn();
		Vec3 tip = from.add(f.scale(SPIKE_RANGE));
		l.sendParticles(ParticleTypes.SNOWFLAKE, tip.x, tip.y, tip.z, 14, 0.3, 0.3, 0.3, 0.06);
		OmnitrixSfx.play(l, p.position(), SND_WHIP, 1f, 1.3f);
		OmnitrixSfx.play(l, tip, SND_FREEZE, 0.7f, 1.3f);
		return n;
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
