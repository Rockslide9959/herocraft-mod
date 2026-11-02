package com.projecthero.mod.omnitrix.alien.swampfire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Swampfire, the Methanosian (Recalibrated Omnitrix) -- a BASIC kit (user: "Just make them basic versions of the aliens
 * for now please that we can revamp later"). A walking swamp plant that sweats methane and lights it.
 *
 * <pre>
 *   Passive  Methanosian body  immune to fire and lava; regrows 1 health a second (2 while burning or standing in fire); his punch
 *                              sets the target alight. Weak to cold: freezing damage is doubled.
 *   R        Fire Blast        a methane fireball from the palm (7, alight 4 s)
 *   Shift+R  Methane Torch     a cone of burning gas, 8 blocks (14, alight 5 s)
 *   G        Seed Bomb         a lobbed seed pod that bursts (15 in 3.5, roots 1 s)
 *   Shift+G  Vine Snare        vines burst from the ground round the creature you look at (6, rooted 3 s)
 *   X        Methane Jet       a jet of burning gas launches him; no fall damage for 1.5 s
 *   Shift+X  Vine Swing        a vine to the block you look at (22) hauls him to it
 *   Z        Methane Cloud     a 4-block cloud of gas at his feet for 6 s (2 a second, nausea)
 *   Shift+Z  Ignite            sets his cloud off: a 5-block fireball (18, alight 5 s); nothing to light = no cooldown
 *   V        Regrowth          3 s rooted: +2 every half second (12), poison and wither cleared; refused at full health
 *   Shift+V  Bloom             makes the plants within 4 blocks grow (bone meal)
 *   C        Root Eruption     a line of root spikes bursts up ahead, 10 blocks (16, launched)
 *   Shift+C  Inferno Bloom     ULTIMATE: a 1 s build-up, then a methane firestorm round him (55 at the centre, 40 at the 8-block edge)
 *   Attack   Burning Fist      his punch sets the target alight (3 s)
 * </pre>
 */
public final class SwampfireKit extends AlienKit {
	public static final String ID = Aliens.SWAMPFIRE.id();

	public static final int ANIM_BLAST = 1;
	public static final int ANIM_TORCH = 2;
	public static final int ANIM_SEED = 3;
	public static final int ANIM_SNARE = 4;
	public static final int ANIM_JET = 5;
	public static final int ANIM_SWING = 6;
	public static final int ANIM_CLOUD = 7;
	public static final int ANIM_IGNITE = 8;
	public static final int ANIM_REGROW = 9;
	public static final int ANIM_BLOOM = 10;
	public static final int ANIM_ROOTS = 11;
	public static final int ANIM_CHARGE = 12;
	public static final int ANIM_INFERNO = 13;
	public static final int ANIM_PUNCH = 14;

	public static final int F_REGROW = 1;
	public static final int F_CLOUD = 2;
	public static final int F_SNARE = 4;
	public static final int F_CHARGE = 8;
	public static final int F_JET = 16;

	public static final String FIRE_BLAST = "fire_blast";
	public static final String TORCH = "methane_torch";
	public static final String SEED = "seed_bomb";
	public static final String SNARE = "vine_snare";
	public static final String JET = "methane_jet";
	public static final String SWING = "vine_swing";
	public static final String CLOUD = "methane_cloud";
	public static final String IGNITE = "ignite";
	public static final String REGROW = "regrowth";
	public static final String BLOOM = "bloom";
	public static final String ROOTS = "root_eruption";
	public static final String INFERNO = "inferno_bloom";
	public static final String FIST = "burning_fist";

	/** Synced kit timers (the client draws from them): the snared creature's id + 1, the cloud / blast / roots origins. */
	public static final String SNARE_TARGET = "snare_target", SNARE_UNTIL = "snare_until", CLOUD_UNTIL = "cloud_until", REGROW_UNTIL = "regrow_until",
			JET_UNTIL = "jet_until", CHARGE_AT = "charge_at";
	public static final String P_CLOUD = "cloud", P_BLAST = "blast", P_ROOTS = "roots", P_ANCHOR = "anchor";

	public static final float BLAST_DAMAGE = 7f;
	public static final float TORCH_DAMAGE = 14f;
	public static final double TORCH_RANGE = 8.0, TORCH_DEG = 60.0;
	public static final float SEED_DAMAGE = 15f, SEED_RADIUS = 3.5f;
	public static final float SNARE_DAMAGE = 6f;
	public static final double SNARE_RANGE = 20.0;
	public static final int SNARE_TICKS = 60;
	public static final int JET_TICKS = 30;
	public static final double SWING_RANGE = 22.0;
	public static final double CLOUD_RADIUS = 4.0;
	public static final int CLOUD_TICKS = 120;
	public static final float CLOUD_DAMAGE = 2f;
	public static final float IGNITE_DAMAGE = 18f, IGNITE_RADIUS = 5f;
	public static final int REGROW_TICKS = 60;
	public static final float REGROW_HEAL = 2f;
	public static final double BLOOM_RADIUS = 4.0;
	public static final float ROOTS_DAMAGE = 16f;
	public static final int ROOTS_SPIKES = 6;
	public static final double ROOTS_SPACING = 1.7;
	public static final int CHARGE_TICKS = 20;
	public static final float INFERNO_DAMAGE = 55f, INFERNO_EDGE = 40f, INFERNO_RADIUS = 8f;
	public static final float REGEN = 1f, REGEN_BURNING = 2f;
	public static final float COLD_FACTOR = 2f;

	/** Positions are stored as kit timers: round(v * 64) + this, so they stay positive (a timer of 0 or less is removed). */
	private static final long OFF = 1L << 40;

	public static final OmnitrixSfx.Cue SND_BLAST = OmnitrixSfx.cue("swampfire_blast")
			.layer(SoundEvents.FIRECHARGE_USE, 0.35f, 1.2f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.2f, 1.4f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.25f, 0.8f);
	public static final OmnitrixSfx.Cue SND_TORCH = OmnitrixSfx.cue("swampfire_torch")
			.layer(SoundEvents.BLAZE_SHOOT, 0.4f, 0.7f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.5f, 0.8f, 2)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.15f, 0.6f);
	public static final OmnitrixSfx.Cue SND_SEED = OmnitrixSfx.cue("swampfire_seed")
			.layer(SoundEvents.SLIME_SQUISH, 0.3f, 1.3f)
			.layer(SoundEvents.AZALEA_LEAVES_BREAK, 0.4f, 0.9f);
	public static final OmnitrixSfx.Cue SND_POP = OmnitrixSfx.cue("swampfire_pop")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 1.5f)
			.layer(SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 0.5f, 0.8f)
			.layer(SoundEvents.AZALEA_LEAVES_BREAK, 0.5f, 0.7f, 1);
	public static final OmnitrixSfx.Cue SND_VINES = OmnitrixSfx.cue("swampfire_vines")
			.layer(SoundEvents.ROOTS_BREAK, 0.55f, 0.6f)
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.5f, 0.8f, 2)
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.4f, 0.7f, 4);
	public static final OmnitrixSfx.Cue SND_JET = OmnitrixSfx.cue("swampfire_jet")
			.layer(SoundEvents.FIRECHARGE_USE, 0.4f, 0.6f)
			.layer(SoundEvents.BREEZE_JUMP, 0.3f, 0.7f);
	public static final OmnitrixSfx.Cue SND_GAS = OmnitrixSfx.cue("swampfire_gas")
			.layer(SoundEvents.BREWING_STAND_BREW, 0.25f, 0.5f)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.2f, 0.6f)
			.layer(SoundEvents.SLIME_SQUISH, 0.25f, 0.5f, 2);
	public static final OmnitrixSfx.Cue SND_IGNITE = OmnitrixSfx.cue("swampfire_ignite")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.45f, 0.9f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.45f, 0.6f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.6f, 0.7f, 3);
	public static final OmnitrixSfx.Cue SND_REGROW = OmnitrixSfx.cue("swampfire_regrow")
			.layer(SoundEvents.BONE_MEAL_USE, 0.45f, 0.8f)
			.layer(SoundEvents.AZALEA_LEAVES_PLACE, 0.4f, 0.7f, 3)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.2f, 0.6f, 6);
	public static final OmnitrixSfx.Cue SND_ROOTS = OmnitrixSfx.cue("swampfire_roots")
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.ROOTS_BREAK, 0.5f, 0.7f)
			.layer(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.15f, 1.3f)
			.gap(1);
	public static final OmnitrixSfx.Cue SND_INFERNO = OmnitrixSfx.cue("swampfire_inferno")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.6f, 0.6f)
			.layer(SoundEvents.BLAZE_SHOOT, 0.5f, 0.5f)
			.layer(SoundEvents.FIRE_AMBIENT, 0.7f, 0.6f, 4)
			.layer(SoundEvents.LAVA_POP, 0.6f, 0.7f, 6);
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("swampfire_charge")
			.layer(SoundEvents.FIRE_AMBIENT, 0.6f, 0.5f)
			.layer(SoundEvents.BREWING_STAND_BREW, 0.3f, 0.6f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.15f, 1.6f, 6);

	private record Cloud(ResourceKey<Level> dim, Vec3 at, long end) {
	}

	private record Roots(ResourceKey<Level> dim, Vec3 from, Vec3 dir, long start, Set<Integer> hits) {
	}

	/** player -> the methane cloud hanging where he let it out. */
	private static final Map<UUID, Cloud> CLOUDS = new ConcurrentHashMap<>();
	/** player -> the root line running ahead of him. */
	private static final Map<UUID, Roots> ROOT_LINES = new ConcurrentHashMap<>();

	private SwampfireKit() {
	}

	public static void register() {
		Aliens.register(new SwampfireKit());
		AlienProjectileEntity.onHit("swampfire_seed", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l && proj.getOwner() instanceof ServerPlayer sp) {
				Vec3 at = hit.getLocation();
				for (LivingEntity e : around(sp, at, SEED_RADIUS)) {
					AlienStatus.root(sp, e, 20, 20);
				}
				l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.3, at.z, 10, 1.2, 0.4, 1.2, 0.02);
				OmnitrixSfx.play(l, at, SND_POP, 1f, 1f);
			}
		});
	}

	public static void clearSessionState() {
		CLOUDS.clear();
		ROOT_LINES.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(32).speed(1.0f).scale(1.0f).armor(6, 1).knockbackResist(0.1f).attack(3).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(FIRE_BLAST, AbilitySlot.SLOT_1, 15), AlienMove.shift(TORCH, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(SEED, AbilitySlot.SLOT_2, 100), AlienMove.shift(SNARE, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(JET, AbilitySlot.SLOT_3, 40), AlienMove.shift(SWING, AbilitySlot.SLOT_3, 80),
				AlienMove.tap(CLOUD, AbilitySlot.SLOT_4, 200), AlienMove.shift(IGNITE, AbilitySlot.SLOT_4, 60),
				AlienMove.tap(REGROW, AbilitySlot.SLOT_5, 400), AlienMove.shift(BLOOM, AbilitySlot.SLOT_5, 200),
				AlienMove.tap(ROOTS, AbilitySlot.SLOT_6, 140), AlienMove.shift(INFERNO, AbilitySlot.SLOT_6, 1200),
				AlienMove.vanilla(FIST, "Attack", 0));
	}

	// ---------------------------------------------------------------- positions as synced timers

	/** Stores {@code v} as three kit timers ({@code <name>_x/_y/_z}), one save. */
	public void setPos(ServerPlayer p, String name, Vec3 v) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(name + "_x"), Math.round(v.x * 64) + OFF);
		n.timers.put(key(name + "_y"), Math.round(v.y * 64) + OFF);
		n.timers.put(key(name + "_z"), Math.round(v.z * 64) + OFF);
		Omnitrix.save(p, n);
	}

	/** A position stored with {@link #setPos} (client-safe), or null. */
	public static Vec3 pos(Player p, String name) {
		OmnitrixState s = Omnitrix.state(p);
		long x = s.readyAt(ID + "." + name + "_x");
		if (x <= 0) {
			return null;
		}
		return new Vec3((x - OFF) / 64.0, (s.readyAt(ID + "." + name + "_y") - OFF) / 64.0, (s.readyAt(ID + "." + name + "_z") - OFF) / 64.0);
	}

	/** The way the root line runs (a unit vector, stored like a position). */
	public static Vec3 rootsDir(Player p) {
		return pos(p, P_ROOTS + "_dir");
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		CLOUDS.remove(p.getUUID());
		ROOT_LINES.remove(p.getUUID());
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		// the move state (never the cooldowns, which share the prefix)
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : STATE_TIMERS) {
			n.timers.remove(key(k));
		}
		for (String k : new String[] { P_CLOUD, P_BLAST, P_ROOTS, P_ROOTS + "_dir", P_ANCHOR }) {
			for (String axis : new String[] { "_x", "_y", "_z" }) {
				n.timers.remove(key(k + axis));
			}
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	private static final String[] STATE_TIMERS = { SNARE_TARGET, SNARE_UNTIL, CLOUD_UNTIL, REGROW_UNTIL, JET_UNTIL, CHARGE_AT };

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		// Methanosian: fire and lava never hurt him
		return !source.is(DamageTypeTags.IS_FIRE) && !(flag(p, F_JET) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return source.is(DamageTypeTags.IS_FREEZING) ? amount * COLD_FACTOR : amount;
	}

	/** Burning, or standing in fire / lava (his regrowth doubles). */
	public static boolean burning(Player p) {
		return p.isOnFire() || p.isInLava() || p.level().getBlockStatesIfLoaded(p.getBoundingBox().deflate(0.001))
				.anyMatch(b -> b.is(net.minecraft.tags.BlockTags.FIRE));
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// the passive regrowth
		if (now % 20 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive()) {
			p.heal(burning(p) ? REGEN_BURNING : REGEN);
		}
		if (p.isOnFire() && now % 10 == 0) {
			l.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.25, 0.4, 0.25, 0.0);
		}
		if (flag(p, F_JET) && (now >= timer(p, JET_UNTIL) || (p.onGround() && now >= timer(p, JET_UNTIL) - JET_TICKS + 6))) {
			setFlag(p, F_JET, false);
		}
		// Regrowth
		if (flag(p, F_REGROW)) {
			long until = timer(p, REGROW_UNTIL);
			if (now >= until || p.getHealth() >= p.getMaxHealth()) {
				setFlag(p, F_REGROW, false);
				setTimer(p, REGROW_UNTIL, 0);
				p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
			} else if ((until - now) % 10 == 0) {
				p.heal(REGROW_HEAL);
				l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.4, 0.6, 0.4, 0.0);
			}
		}
		// Vine Snare ends
		if (flag(p, F_SNARE) && now >= timer(p, SNARE_UNTIL)) {
			setFlag(p, F_SNARE, false);
			setTimer(p, SNARE_TARGET, 0);
			setTimer(p, SNARE_UNTIL, 0);
		}
		// the cloud
		Cloud c = CLOUDS.get(p.getUUID());
		if (c != null) {
			if (now >= c.end() || c.dim() != l.dimension()) {
				clearCloud(p);
			} else if ((c.end() - now) % 20 == 0) {
				for (LivingEntity e : around(p, c.at(), CLOUD_RADIUS)) {
					hurt(p, e, CLOUD_DAMAGE, false);
					e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), p);
				}
				if (l.random.nextInt(2) == 0) {
					OmnitrixSfx.play(l, c.at(), SND_GAS, 0.6f, 0.8f);
				}
			}
		}
		// the root line, a spike every 2 ticks
		Roots r = ROOT_LINES.get(p.getUUID());
		if (r != null) {
			long t = now - r.start();
			if (r.dim() != l.dimension() || t > ROOTS_SPIKES * 2L) {
				ROOT_LINES.remove(p.getUUID());
			} else if (t % 2 == 0) {
				int i = (int) (t / 2);
				Vec3 at = r.from().add(r.dir().scale(ROOTS_SPACING * (i + 1)));
				for (LivingEntity e : around(p, at, 1.6)) {
					if (r.hits().add(e.getId()) && hurt(p, e, ROOTS_DAMAGE, false)) {
						e.setDeltaMovement(e.getDeltaMovement().add(0, 0.75, 0));
						e.hurtMarked = true;
					}
				}
				l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
						net.minecraft.world.level.block.Blocks.ROOTED_DIRT.defaultBlockState()), at.x, at.y + 0.2, at.z, 6, 0.3, 0.1, 0.3, 0.1);
				OmnitrixSfx.play(l, at, SND_ROOTS, 0.7f, 1f);
			}
		}
		// Inferno Bloom's build-up
		if (flag(p, F_CHARGE)) {
			long at = timer(p, CHARGE_AT);
			if (now >= at) {
				setFlag(p, F_CHARGE, false);
				setTimer(p, CHARGE_AT, 0);
				inferno(p);
			} else if (now % 4 == 0) {
				l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.6, 0.8, 0.6, 0.01);
			}
		}
	}

	private void clearCloud(ServerPlayer p) {
		CLOUDS.remove(p.getUUID());
		setFlag(p, F_CLOUD, false);
		setTimer(p, CLOUD_UNTIL, 0);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHARGE)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					torch(p);
				} else {
					fireBlast(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					snare(p);
				} else {
					seedBomb(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					swing(p);
				} else {
					jet(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					ignite(p);
				} else {
					cloud(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					bloom(p);
				} else {
					regrow(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					infernoStart(p);
				} else {
					roots(p);
				}
			}
			default -> {
			}
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	public boolean fireBlast(ServerPlayer p) {
		if (!use(p, FIRE_BLAST)) {
			return false;
		}
		anim(p, ANIM_BLAST);
		AlienProjectileEntity.of(p, "swampfire_blast").colour(0xFF8A2A).size(0.4f).damage(BLAST_DAMAGE).fire(4).speed(1.6).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_BLAST, 1f, 1f);
		return true;
	}

	/** Creatures in a cone of {@code deg} degrees, {@code range} blocks along {@code dir} from {@code from}. */
	public static List<LivingEntity> cone(ServerPlayer p, Vec3 from, Vec3 dir, double range, double deg) {
		double cos = Math.cos(Math.toRadians(deg / 2.0));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(p, from, range + 1.0)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double d = to.length();
			if (d > range + e.getBbWidth() * 0.5) {
				continue;
			}
			if (d < 1.0 || to.normalize().dot(dir) >= cos) {
				out.add(e);
			}
		}
		return out;
	}

	public boolean torch(ServerPlayer p) {
		if (!use(p, TORCH)) {
			return false;
		}
		anim(p, ANIM_TORCH);
		Vec3 eye = p.getEyePosition();
		for (LivingEntity e : cone(p, eye, p.getLookAngle(), TORCH_RANGE, TORCH_DEG)) {
			if (hurt(p, e, TORCH_DAMAGE, true)) {
				e.igniteForSeconds(5);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_TORCH, 1.2f, 1f);
		return true;
	}

	public boolean seedBomb(ServerPlayer p) {
		if (!use(p, SEED)) {
			return false;
		}
		anim(p, ANIM_SEED);
		AlienProjectileEntity.of(p, "swampfire_seed").colour(0x6A9A2A).size(0.45f).damage(4f).blast(SEED_RADIUS, SEED_DAMAGE).gravity(0.05).speed(1.15)
				.life(80).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_SEED, 1f, 1f);
		return true;
	}

	public boolean snare(ServerPlayer p) {
		LivingEntity t = lookTarget(p, SNARE_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.swampfire.no_target");
			return false;
		}
		if (!use(p, SNARE)) {
			return false;
		}
		anim(p, ANIM_SNARE);
		long now = p.level().getGameTime();
		hurt(p, t, SNARE_DAMAGE, false);
		AlienStatus.root(p, t, SNARE_TICKS, 20);
		setTimer(p, SNARE_TARGET, t.getId() + 1L);
		setTimer(p, SNARE_UNTIL, now + (AlienStatus.isBoss(t) ? SNARE_TICKS / 4 : SNARE_TICKS));
		setFlag(p, F_SNARE, true);
		OmnitrixSfx.play(level(p), t.position(), SND_VINES, 1f, 1f);
		return true;
	}

	public boolean jet(ServerPlayer p) {
		if (!use(p, JET)) {
			return false;
		}
		anim(p, ANIM_JET);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.x * 0.9, 0.95, h.z * 0.9);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, JET_UNTIL, p.level().getGameTime() + JET_TICKS);
		setFlag(p, F_JET, true);
		level(p).sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 8, 0.3, 0.05, 0.3, 0.05);
		OmnitrixSfx.play(level(p), p.position(), SND_JET, 1f, 1f);
		return true;
	}

	public boolean swing(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(SWING_RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			say(p, "message.projecthero.omnitrix.swampfire.no_anchor");
			return false;
		}
		if (!use(p, SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 to = hit.getLocation().subtract(p.position());
		Vec3 v = to.normalize().scale(Math.min(1.9, 0.7 + to.length() * 0.08));
		p.setDeltaMovement(v.x, v.y + 0.35, v.z);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setPos(p, P_ANCHOR, hit.getLocation());
		setTimer(p, JET_UNTIL, p.level().getGameTime() + JET_TICKS);
		setFlag(p, F_JET, true);
		OmnitrixSfx.play(level(p), p.position(), SND_VINES, 0.8f, 1.3f);
		return true;
	}

	public boolean cloud(ServerPlayer p) {
		if (!use(p, CLOUD)) {
			return false;
		}
		anim(p, ANIM_CLOUD);
		long now = p.level().getGameTime();
		Vec3 at = p.position().add(0, 0.6, 0);
		CLOUDS.put(p.getUUID(), new Cloud(p.level().dimension(), at, now + CLOUD_TICKS));
		setPos(p, P_CLOUD, at);
		setTimer(p, CLOUD_UNTIL, now + CLOUD_TICKS);
		setFlag(p, F_CLOUD, true);
		OmnitrixSfx.play(level(p), at, SND_GAS, 1f, 1f);
		return true;
	}

	public boolean ignite(ServerPlayer p) {
		Cloud c = CLOUDS.get(p.getUUID());
		if (c == null || c.dim() != p.level().dimension()) {
			say(p, "message.projecthero.omnitrix.swampfire.no_cloud");
			return false;
		}
		if (!use(p, IGNITE)) {
			return false;
		}
		anim(p, ANIM_IGNITE);
		clearCloud(p);
		ServerLevel l = level(p);
		Vec3 at = c.at();
		setPos(p, P_BLAST, at);
		for (LivingEntity e : around(p, at, IGNITE_RADIUS)) {
			if (hurt(p, e, IGNITE_DAMAGE, true)) {
				e.igniteForSeconds(5);
				shove(e, at, 0.8, 0.4);
			}
		}
		l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 30, 2.0, 1.0, 2.0, 0.1);
		OmnitrixSfx.play(l, at, SND_IGNITE, 1.4f, 1f);
		return true;
	}

	public boolean regrow(ServerPlayer p) {
		if (p.getHealth() >= p.getMaxHealth()) {
			say(p, "message.projecthero.omnitrix.swampfire.full_health");
			return false;
		}
		if (!use(p, REGROW)) {
			return false;
		}
		anim(p, ANIM_REGROW);
		p.removeEffect(MobEffects.POISON);
		p.removeEffect(MobEffects.WITHER);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, REGROW_TICKS, 3, false, false, false));
		setTimer(p, REGROW_UNTIL, p.level().getGameTime() + REGROW_TICKS);
		setFlag(p, F_REGROW, true);
		OmnitrixSfx.play(level(p), p.position(), SND_REGROW, 1f, 1f);
		return true;
	}

	public boolean bloom(ServerPlayer p) {
		if (!use(p, BLOOM)) {
			return false;
		}
		anim(p, ANIM_BLOOM);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		int r = (int) BLOOM_RADIUS;
		int grown = 0;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
			if (bp.distSqr(c) > BLOOM_RADIUS * BLOOM_RADIUS) {
				continue;
			}
			BlockState st = l.getBlockState(bp);
			if (st.getBlock() instanceof BonemealableBlock b && !(st.getBlock() instanceof net.minecraft.world.level.block.GrassBlock)
					&& b.isValidBonemealTarget(l, bp, st) && b.isBonemealSuccess(l, l.random, bp, st)) {
				b.performBonemeal(l, l.random, bp.immutable(), st);
				l.levelEvent(1505, bp, 0); // the bone meal sparkle
				grown++;
			}
		}
		l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 0.5, p.getZ(), 16, BLOOM_RADIUS * 0.5, 0.4, BLOOM_RADIUS * 0.5, 0.0);
		OmnitrixSfx.play(l, p.position(), SND_REGROW, 0.8f, 1.2f);
		return grown >= 0;
	}

	public boolean roots(ServerPlayer p) {
		if (!use(p, ROOTS)) {
			return false;
		}
		anim(p, ANIM_ROOTS);
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 from = p.position();
		ROOT_LINES.put(p.getUUID(), new Roots(p.level().dimension(), from, dir, p.level().getGameTime(), new HashSet<>()));
		setPos(p, P_ROOTS, from);
		setPos(p, P_ROOTS + "_dir", dir);
		return true;
	}

	public boolean infernoStart(ServerPlayer p) {
		if (!use(p, INFERNO)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE_TICKS, 9, false, false, false));
		setTimer(p, CHARGE_AT, p.level().getGameTime() + CHARGE_TICKS);
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 1f, 1f);
		return true;
	}

	/** The firestorm itself (tests call it straight). */
	public void inferno(ServerPlayer p) {
		anim(p, ANIM_INFERNO);
		ServerLevel l = level(p);
		Vec3 at = p.position().add(0, 1.0, 0);
		setPos(p, P_BLAST, at);
		for (LivingEntity e : around(p, at, INFERNO_RADIUS)) {
			double d = Math.min(1.0, e.position().distanceTo(p.position()) / INFERNO_RADIUS);
			float dmg = (float) (INFERNO_DAMAGE + (INFERNO_EDGE - INFERNO_DAMAGE) * d);
			if (hurt(p, e, dmg, true)) {
				e.igniteForSeconds(8);
				shove(e, p.position(), 1.4, 0.6);
			}
		}
		l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, 4.0, 1.2, 4.0, 0.15);
		l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 20, 4.0, 1.0, 4.0, 0.0);
		com.projecthero.mod.quirk.fx.QuirkCamera.shakeAround(l, at, 16, 0.5f, 10);
		OmnitrixSfx.play(l, at, SND_INFERNO, 1.6f, 1f);
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le) {
			le.igniteForSeconds(3);
			anim(p, ANIM_PUNCH);
		}
		return false;
	}

	/** Whether a methane cloud of his hangs right now (tests). */
	public static boolean hasCloud(ServerPlayer p) {
		return CLOUDS.containsKey(p.getUUID());
	}

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		return CLOUDS.containsKey(p.getUUID()) || ROOT_LINES.containsKey(p.getUUID());
	}
}
