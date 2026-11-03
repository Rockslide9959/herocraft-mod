package com.projecthero.mod.omnitrix.alien.swampfire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Swampfire, the Methanosian (Recalibrated Omnitrix). v0.17.2 revamp (docs/OMNITRIX_REVAMP_SWAMPFIRE.md; user: "Yes do
 * all this stuff but dont add ultimate aliens yet please"): gas + fire is his combo engine, and being "killed" is a regrowth
 * moment. No Ultimate Swampfire, no blue fire: the Shift+Z ultimate is an ordinary orange methane explosion and a Methane Surge.
 *
 * v0.17.2 damage pass (user: "make the damage of all of ben 10s alien stronger"; docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md): the
 * damage numbers in the table below are the pre-pass ones (~x1.6 now) -- the constants are the source of truth.
 *
 * <pre>
 *   R          Fire Blast       a methane fireball (7, alight 4 s, 0.75 s); flying through his gas cloud sets the cloud off
 *   Shift+R    Methane Torch    HOLD up to 2 s: a cone of burning gas that grows while held (3.5 every 0.5 s, up to 14, alight 5 s),
 *                               real fire where it plays on the ground; 6 s cooldown from the release
 *   G          Seed Bomb        a lobbed seed pod (15 in 3.5, roots 2 s / bosses 0.5 s) that leaves a 2 s vine tangle
 *   Shift+G    Vine Snare       vines round the creature you look at (6, rooted 3 s, 20 blocks); Shift+G again while it holds = yank it
 *                               4 blocks toward you
 *   X          Methane Jet      a burst of burning gas launches him; no fall damage
 *   Shift+X    Vine Swing       tap: a vine hauls him to the block you look at (22); HOLD: stay tethered and swing, let go with the
 *                               momentum
 *   Z          Methane Cloud    vent a 4-block cloud (6 s, 2 a second + nausea); Z again while it hangs = Ignite (19 in 5, alight 5 s, a
 *                               ring of real fire). His fire passing through it ignites it too. 10 s cooldown after the blast
 *   Shift+Z    Methane Inferno  ULTIMATE: 1 s build-up, then a methane explosion (60 centre / 40 at the 8-block edge, real fire
 *                               scattered and ringed round him), then 12 s of Methane Surge (his fire +25%, Fire Blast leaves fire)
 *   C          Root Eruption    a line of root spikes, 10 blocks (16, launched); a spike under his cloud lights it
 *   Shift+C    Seed Scatter     5 seeds in a fan that plant (20 s, max 5); an enemy within 2 blocks bursts one (8, rooted 1.5 s)
 *   V          Regrowth         3 s rooted: +12, clears poison / wither / slowness / weakness; Reassembly recharges 2x meanwhile
 *   Shift+V    Overgrowth       bone meal round him (4) + a climbable vine ladder up the wall you look at (8 tall, 30 s)
 *   Attack     Burning Fist     sets alight 3 s; +2 against a creature already burning
 *   Use        Vine Lash        (empty hand) a vine whip, 6 blocks: 6 damage, small creatures pulled in
 *   Passives   fire / lava immune; regrows 1 HP/s (2 in fire); Reassembly (lethal damage -> 3 s mulch heap -> 10 HP, 150 s);
 *              Swamp Stench (2 s within 2 blocks of him = 3 s nausea)
 *   Weak       cold (freezing x2; frozen = no regrowth, no Reassembly); Doused (underwater his fire fizzles; rain halves his fire)
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
	public static final int ANIM_LASH = 15;
	public static final int ANIM_SCATTER = 16;
	public static final int ANIM_YANK = 17;
	public static final int ANIM_REFORM = 18;

	public static final int F_REGROW = 1;
	public static final int F_CLOUD = 2;
	public static final int F_SNARE = 4;
	public static final int F_CHARGE = 8;
	public static final int F_JET = 16;
	public static final int F_TORCH = 32;
	public static final int F_SWING = 64;
	public static final int F_SURGE = 128;
	public static final int F_MULCH = 256;
	public static final int F_TANGLE = 512;

	public static final String FIRE_BLAST = "fire_blast";
	public static final String TORCH = "methane_torch";
	public static final String SEED = "seed_bomb";
	public static final String SNARE = "vine_snare";
	public static final String JET = "methane_jet";
	public static final String SWING = "vine_swing";
	public static final String CLOUD = "methane_cloud";
	public static final String INFERNO = "methane_inferno";
	public static final String REGROW = "regrowth";
	public static final String OVERGROWTH = "overgrowth";
	public static final String ROOTS = "root_eruption";
	public static final String SCATTER = "seed_scatter";
	public static final String FIST = "burning_fist";
	public static final String LASH = "vine_lash";
	public static final String REASSEMBLY = "reassembly";

	/** Synced kit timers (the client draws from them). */
	public static final String SNARE_TARGET = "snare_target", SNARE_UNTIL = "snare_until", CLOUD_UNTIL = "cloud_until", REGROW_UNTIL = "regrow_until",
			JET_UNTIL = "jet_until", CHARGE_AT = "charge_at", TORCH_START = "torch_start", SWING_START = "swing_start", SWING_LEN = "swing_len",
			SURGE_UNTIL = "surge_until", MULCH_UNTIL = "mulch_until", TANGLE_UNTIL = "tangle_until", MINE_UNTIL = "mine_until_";
	public static final String P_CLOUD = "cloud", P_BLAST = "blast", P_ROOTS = "roots", P_ANCHOR = "anchor", P_TANGLE = "tangle", P_LASH = "lash",
			P_MINE = "mine", P_SNAP = "snap";

// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want them to be strong aswell"): ~x1.6 damage pass (docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md)
	public static final float BLAST_DAMAGE = 11f;
	public static final float TORCH_PULSE_DAMAGE = 9f;
	public static final int TORCH_MAX_TICKS = 40, TORCH_PULSE = 10, TORCH_COOLDOWN = 120;
	public static final double TORCH_RANGE = 8.0, TORCH_DEG = 60.0;
	public static final float SEED_DAMAGE = 25f, SEED_RADIUS = 3.5f, SEED_IMPACT = 8f;
	public static final int SEED_ROOT = 40, TANGLE_TICKS = 40;
	public static final double TANGLE_RADIUS = 2.5;
	public static final float SNARE_DAMAGE = 10f;
	public static final double SNARE_RANGE = 20.0, YANK_DISTANCE = 4.0;
	public static final int SNARE_TICKS = 60;
	public static final int JET_TICKS = 30;
	public static final double SWING_RANGE = 22.0;
	public static final int SWING_HOLD = 6, SWING_MAX = 100;
	public static final double CLOUD_RADIUS = 4.0;
	public static final int CLOUD_TICKS = 120;
	public static final float CLOUD_DAMAGE = 7f;
	public static final float IGNITE_DAMAGE = 32f, IGNITE_RADIUS = 5f;
	public static final double IGNITE_RING = 4.0;
	public static final int IGNITE_FIRE_TICKS = 100;
	public static final int CHARGE_TICKS = 20;
	public static final float INFERNO_DAMAGE = 100f, INFERNO_EDGE = 70f, INFERNO_RADIUS = 8f;
	public static final int INFERNO_FIRE_TICKS = 120, SURGE_TICKS = 240;
	public static final float SURGE_FACTOR = 1.25f;
	public static final int REGROW_TICKS = 60;
	public static final float REGROW_HEAL = 2f;
	public static final double BLOOM_RADIUS = 4.0;
	public static final int LADDER_MAX = 8, LADDER_TICKS = 600;
	public static final double LADDER_REACH = 5.0;
	public static final float ROOTS_DAMAGE = 26f;
	public static final int ROOTS_SPIKES = 6;
	public static final double ROOTS_SPACING = 1.7;
	public static final int SCATTER_SEEDS = 5, MINE_TICKS = 400, MINE_ROOT = 30, MAX_MINES = 5;
	public static final float MINE_DAMAGE = 14f;
	public static final double MINE_TRIGGER = 2.0;
	public static final float FIST_BONUS = 4f;
	public static final float LASH_DAMAGE = 10f;
	public static final double LASH_RANGE = 6.0;
	public static final int MULCH_TICKS = 60, REASSEMBLY_COOLDOWN = 3000;
	public static final float REFORM_HEALTH = 10f;
	public static final int STENCH_TICKS = 40, STENCH_NAUSEA = 60;
	public static final double STENCH_RADIUS = 2.0;
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
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue SND_LASH = OmnitrixSfx.cue("swampfire_lash")
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.5f, 1.3f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.3f, 1.4f);
	public static final OmnitrixSfx.Cue SND_MULCH = OmnitrixSfx.cue("swampfire_mulch")
			.layer(SoundEvents.SLIME_BLOCK_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.6f, 0.5f)
			.layer(SoundEvents.AZALEA_LEAVES_BREAK, 0.5f, 0.6f, 2);
	public static final OmnitrixSfx.Cue SND_REFORM = OmnitrixSfx.cue("swampfire_reform")
			.layer(SoundEvents.ROOTS_PLACE, 0.6f, 0.7f)
			.layer(SoundEvents.SLIME_SQUISH, 0.5f, 0.6f, 2)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 0.7f, 6);
	public static final OmnitrixSfx.Cue SND_FIZZLE = OmnitrixSfx.cue("swampfire_fizzle")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.1f)
			.layer(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.5f, 1.0f);

	private record Cloud(ResourceKey<Level> dim, Vec3 at, long end) {
	}

	private record Roots(ResourceKey<Level> dim, Vec3 from, Vec3 dir, long start, Set<Integer> hits) {
	}

	private record Tangle(ResourceKey<Level> dim, Vec3 at, long end) {
	}

	private record Mine(ResourceKey<Level> dim, Vec3 at, long end) {
	}

	/** player -> the methane cloud hanging where he let it out. */
	private static final Map<UUID, Cloud> CLOUDS = new ConcurrentHashMap<>();
	/** player -> the root line running ahead of him. */
	private static final Map<UUID, Roots> ROOT_LINES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> the Seed Bomb's vine tangle. */
	private static final Map<UUID, Tangle> TANGLES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> his planted Seed Scatter seeds (oldest first). */
	private static final Map<UUID, List<Mine>> MINES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> (creature id -> ticks it has stood in his stench). */
	private static final Map<UUID, Map<Integer, Integer>> STENCH = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the torch pulses, the mines, the stench count must not double up). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private static final ResourceLocation EARLY = ProjectHeroMod.id("swampfire_reassembly");

	private SwampfireKit() {
	}

	public static void register() {
		Aliens.register(new SwampfireKit());
		AlienProjectileEntity.onHit("swampfire_seed", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l && proj.getOwner() instanceof ServerPlayer sp) {
				Vec3 at = hit.getLocation();
				for (LivingEntity e : around(sp, at, SEED_RADIUS)) {
					AlienStatus.root(sp, e, SEED_ROOT, 20);
				}
				if (Aliens.kit(ID) instanceof SwampfireKit k && k.isSwampfire(sp)) {
					k.tangle(sp, at);
				}
				OmnitrixSfx.play(l, at, SND_POP, 1f, 1f);
			}
		});
		AlienProjectileEntity.onHit("swampfire_blast", (proj, hit) -> {
			// v0.17.2 revamp (Methane Surge): his fireballs leave real fire where they land
			if (proj.level() instanceof ServerLevel l && proj.getOwner() instanceof ServerPlayer sp && Aliens.kit(ID) instanceof SwampfireKit k
					&& k.isSwampfire(sp) && k.flag(sp, F_SURGE)) {
				if (hit instanceof BlockHitResult bh) {
					PyrokinesisHandlers.placeFire(sp, l, bh.getBlockPos().relative(bh.getDirection()), fireTtl(l, bh.getBlockPos(), 80));
				} else {
					PyrokinesisHandlers.groundFire(sp, l, hit.getLocation(), fireTtl(l, BlockPos.containing(hit.getLocation()), 80));
				}
			}
		});
		AlienProjectileEntity.onHit("swampfire_scatter", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel && proj.getOwner() instanceof ServerPlayer sp && Aliens.kit(ID) instanceof SwampfireKit k
					&& k.isSwampfire(sp)) {
				k.plant(sp, hit.getLocation());
			}
		});
		// v0.17.2 revamp, Reassembly: runs before the Omnitrix failsafe (an early phase), so lethal damage makes him mulch first
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(EARLY, (entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && Aliens.kit(ID) instanceof SwampfireKit k && k.isSwampfire(p)) {
				return !k.reassemble(p, source);
			}
			return true;
		});
	}

	public static void clearSessionState() {
		CLOUDS.clear();
		ROOT_LINES.clear();
		TANGLES.clear();
		MINES.clear();
		STENCH.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Swampfire right now. */
	public boolean isSwampfire(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(32).speed(1.0f).scale(1.0f).armor(10, 1).knockbackResist(0.1f).attack(9).build(); // v0.17.2 (user set the unarmed melee bonuses: "Swampfire +9, Echo Echo +4, Humungousaur +15, Jetray +5, Big Chill +6, Chromastone +7, Brainstorm +4, Spidermonkey +5, Goop +5")
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(FIRE_BLAST, AbilitySlot.SLOT_1, 15), AlienMove.shift(TORCH, AbilitySlot.SLOT_1, TORCH_COOLDOWN),
				AlienMove.tap(SEED, AbilitySlot.SLOT_2, 100), AlienMove.shift(SNARE, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(JET, AbilitySlot.SLOT_3, 40), AlienMove.shift(SWING, AbilitySlot.SLOT_3, 80),
				AlienMove.tap(CLOUD, AbilitySlot.SLOT_4, 200), AlienMove.shift(INFERNO, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(REGROW, AbilitySlot.SLOT_5, 400), AlienMove.shift(OVERGROWTH, AbilitySlot.SLOT_5, 200),
				AlienMove.tap(ROOTS, AbilitySlot.SLOT_6, 140), AlienMove.shift(SCATTER, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(FIST, "Attack", 0), AlienMove.vanilla(LASH, "Use (empty hand)", 16),
				AlienMove.vanilla(REASSEMBLY, "Passive (lethal damage)", REASSEMBLY_COOLDOWN));
	}

	// ---------------------------------------------------------------- positions as synced timers

	/** Stores {@code v} as three kit timers ({@code <name>_x/_y/_z}), one save. */
	public void setPos(ServerPlayer p, String name, Vec3 v) {
		OmnitrixState n = Omnitrix.state(p).copy();
		putPos(n, name, v);
		Omnitrix.save(p, n);
	}

	private void putPos(OmnitrixState n, String name, Vec3 v) {
		n.timers.put(key(name + "_x"), Math.round(v.x * 64) + OFF);
		n.timers.put(key(name + "_y"), Math.round(v.y * 64) + OFF);
		n.timers.put(key(name + "_z"), Math.round(v.z * 64) + OFF);
	}

	private void removePos(OmnitrixState n, String name) {
		for (String axis : new String[] { "_x", "_y", "_z" }) {
			n.timers.remove(key(name + axis));
		}
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

	private static final String[] STATE_TIMERS = { SNARE_TARGET, SNARE_UNTIL, CLOUD_UNTIL, REGROW_UNTIL, JET_UNTIL, CHARGE_AT, TORCH_START, SWING_START,
			SWING_LEN, SURGE_UNTIL, MULCH_UNTIL, TANGLE_UNTIL };

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		CLOUDS.remove(id);
		ROOT_LINES.remove(id);
		TANGLES.remove(id);
		MINES.remove(id);
		STENCH.remove(id);
		LAST_TICK.remove(id);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		// the move state (never the cooldowns, which share the prefix)
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : STATE_TIMERS) {
			n.timers.remove(key(k));
		}
		for (int i = 0; i < MAX_MINES; i++) {
			n.timers.remove(key(MINE_UNTIL + i));
			removePos(n, P_MINE + i);
		}
		for (String k : new String[] { P_CLOUD, P_BLAST, P_ROOTS, P_ROOTS + "_dir", P_ANCHOR, P_TANGLE, P_LASH, P_SNAP }) {
			removePos(n, k);
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_MULCH) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // a heap of mulch pulling itself together: nothing to hit
		}
		// Methanosian: fire and lava never hurt him
		return !source.is(DamageTypeTags.IS_FIRE) && !(flag(p, F_JET) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return source.is(DamageTypeTags.IS_FREEZING) ? amount * COLD_FACTOR : amount;
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_TORCH);
			case SLOT_2 -> flag(p, F_SNARE);
			case SLOT_3 -> flag(p, F_SWING);
			case SLOT_4 -> flag(p, F_CLOUD) || flag(p, F_SURGE);
			case SLOT_5 -> flag(p, F_REGROW);
			default -> false;
		};
	}

	/** Burning, or standing in fire / lava (his regrowth doubles). */
	public static boolean burning(Player p) {
		return p.isOnFire() || p.isInLava() || p.level().getBlockStatesIfLoaded(p.getBoundingBox().deflate(0.001))
				.anyMatch(b -> b.is(net.minecraft.tags.BlockTags.FIRE));
	}

	/** v0.17.2 revamp (cold weakness): frozen -- powder snow, Big Chill's breath, any ice that sets the freeze ticks. */
	public static boolean frozen(Player p) {
		return p.getTicksFrozen() > 0 || p.isInPowderSnow;
	}

	/** v0.17.2 revamp (Doused): under water his methane fire fizzles. */
	public static boolean doused(Player p) {
		return p.isUnderWater();
	}

	/** Rain halves how long his fire burns. */
	static int fireTtl(ServerLevel l, BlockPos at, int ttl) {
		return l.isRainingAt(at.above()) ? ttl / 2 : ttl;
	}

	/** Methane Surge: his fire moves hit 25% harder. */
	public float fireFactor(Player p) {
		return flag(p, F_SURGE) ? SURGE_FACTOR : 1f;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick -- the timed pulses must not double up if he is ticked twice in a tick (RipjawsKit.tick)
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		// Reassembly: the mulch heap
		if (flag(p, F_MULCH)) {
			tickMulch(p, l, now);
			return;
		}
		// the passive regrowth (none while frozen)
		if (now % 20 == 0 && p.getHealth() < p.getMaxHealth() && p.isAlive() && !frozen(p)) {
			p.heal(burning(p) ? REGEN_BURNING : REGEN);
		}
		if (flag(p, F_JET) && (now >= timer(p, JET_UNTIL) || (p.onGround() && now >= timer(p, JET_UNTIL) - JET_TICKS + 6) && !flag(p, F_SWING))) {
			setFlag(p, F_JET, false);
		}
		tickRegrow(p, l, now);
		// Vine Snare ends
		if (flag(p, F_SNARE) && now >= timer(p, SNARE_UNTIL)) {
			endSnare(p);
		}
		tickTorch(p, l, now);
		tickSwing(p, now);
		tickCloud(p, l, now);
		tickRoots(p, l, now);
		tickTangle(p, now);
		tickMines(p, l, now);
		tickStench(p, now);
		// Methane Inferno's build-up, then the Surge
		if (flag(p, F_CHARGE)) {
			long at = timer(p, CHARGE_AT);
			if (now >= at) {
				setFlag(p, F_CHARGE, false);
				setTimer(p, CHARGE_AT, 0);
				inferno(p);
			}
		}
		if (flag(p, F_SURGE) && now >= timer(p, SURGE_UNTIL)) {
			setFlag(p, F_SURGE, false);
			setTimer(p, SURGE_UNTIL, 0);
		}
	}

	private void tickRegrow(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_REGROW)) {
			return;
		}
		long until = timer(p, REGROW_UNTIL);
		if (now >= until || p.getHealth() >= p.getMaxHealth()) {
			setFlag(p, F_REGROW, false);
			setTimer(p, REGROW_UNTIL, 0);
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		} else if ((until - now) % 10 == 0) {
			if (!frozen(p)) {
				p.heal(REGROW_HEAL);
			}
			// v0.17.2 revamp: while he channels, Reassembly recharges twice as fast (10 more ticks off every half second)
			long ready = timer(p, REASSEMBLY);
			if (ready > now) {
				setTimer(p, REASSEMBLY, Math.max(now, ready - 10));
			}
		}
	}

	private void endSnare(ServerPlayer p) {
		setFlag(p, F_SNARE, false);
		setTimer(p, SNARE_TARGET, 0);
		setTimer(p, SNARE_UNTIL, 0);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHARGE) || flag(p, F_MULCH)) {
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
					if (flag(p, F_SNARE)) {
						yank(p);
					} else {
						snare(p);
					}
				} else {
					seedBomb(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					swingStart(p);
				} else {
					jet(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					infernoStart(p);
				} else if (hasCloud(p)) {
					ignite(p);
				} else {
					cloud(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					overgrowth(p);
				} else {
					regrow(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					seedScatter(p);
				} else {
					roots(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_1 && flag(p, F_TORCH)) {
			stopTorch(p); // the Methane Torch runs while Shift+R is held
		}
		if (slot == AbilitySlot.SLOT_3 && shift && flag(p, F_SWING)) {
			swingRelease(p, heldTicks);
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	/** Doused: refuses a fire move under water (no cooldown spent). */
	private boolean fizzles(ServerPlayer p) {
		if (!doused(p)) {
			return false;
		}
		say(p, "message.projecthero.omnitrix.swampfire.doused");
		OmnitrixSfx.play(level(p), p.position(), SND_FIZZLE, 0.8f, 1f);
		level(p).sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
		return true;
	}

	// ---------------------------------------------------------------- R / Shift+R: fire

	public boolean fireBlast(ServerPlayer p) {
		if (fizzles(p) || !use(p, FIRE_BLAST)) {
			return false;
		}
		anim(p, ANIM_BLAST);
		AlienProjectileEntity.of(p, "swampfire_blast").colour(0xFF8A2A).size(0.4f).damage(BLAST_DAMAGE * fireFactor(p)).fire(4).speed(1.6).spawn();
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

	/** Shift+R (held): the Methane Torch lights; it burns in pulses until the keys come up (2 s at most). */
	public boolean torch(ServerPlayer p) {
		if (flag(p, F_TORCH) || fizzles(p) || !use(p, TORCH)) {
			return false;
		}
		anim(p, ANIM_TORCH);
		setTimer(p, TORCH_START, p.level().getGameTime());
		setFlag(p, F_TORCH, true);
		OmnitrixSfx.play(level(p), p.position(), SND_TORCH, 1.2f, 1f);
		torchPulse(p, level(p), 0); // the first lick lands at once
		return true;
	}

	/** The torch's reach after {@code held} ticks: 4 blocks, growing to 8 over 1.5 s. */
	public static double torchReach(float held) {
		return TORCH_RANGE * (0.5 + 0.5 * Math.min(1.0, held / 30.0));
	}

	public void stopTorch(ServerPlayer p) {
		if (!flag(p, F_TORCH)) {
			return;
		}
		setFlag(p, F_TORCH, false);
		setTimer(p, TORCH_START, 0);
		cooldown(p, TORCH, TORCH_COOLDOWN); // v0.17.2 revamp: the 6 s run from the release
		if (Omnitrix.state(p).animId == ANIM_TORCH) {
			anim(p, 0);
		}
	}

	private void tickTorch(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_TORCH)) {
			return;
		}
		long held = now - timer(p, TORCH_START);
		if (held >= TORCH_MAX_TICKS || doused(p)) {
			stopTorch(p);
			return;
		}
		if (held > 0 && held % TORCH_PULSE == 0) {
			torchPulse(p, l, held);
		}
		if (held % 12 == 6) {
			OmnitrixSfx.play(l, p.position(), SND_TORCH, 0.6f, 1.1f);
		}
	}

	private void torchPulse(ServerPlayer p, ServerLevel l, long held) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double reach = torchReach(held);
		for (LivingEntity e : cone(p, eye, look, reach, TORCH_DEG)) {
			if (hurt(p, e, TORCH_PULSE_DAMAGE * fireFactor(p), true)) {
				e.igniteForSeconds(5);
			}
		}
		// where it plays on the ground: real fire (4 s, halved in rain)
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.BLOCK) {
			PyrokinesisHandlers.placeFire(p, l, hit.getBlockPos().relative(hit.getDirection()), fireTtl(l, hit.getBlockPos(), 80));
		}
		// a torch played through his gas sets it off
		Cloud c = CLOUDS.get(p.getUUID());
		if (c != null) {
			Vec3 to = c.at().subtract(eye);
			double d = to.length();
			if (d <= reach + CLOUD_RADIUS && (d < CLOUD_RADIUS || to.normalize().dot(look) > Math.cos(Math.toRadians(TORCH_DEG)))) {
				ignite(p);
			}
		}
	}

	// ---------------------------------------------------------------- G / Shift+G: seeds and vines

	public boolean seedBomb(ServerPlayer p) {
		if (!use(p, SEED)) {
			return false;
		}
		anim(p, ANIM_SEED);
		AlienProjectileEntity.of(p, "swampfire_seed").colour(0x6A9A2A).size(0.45f).damage(SEED_IMPACT).blast(SEED_RADIUS, SEED_DAMAGE).gravity(0.05).speed(1.15)
				.life(80).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_SEED, 1f, 1f);
		return true;
	}

	/** The Seed Bomb's 2 s vine tangle where it burst (tests call it straight). */
	public void tangle(ServerPlayer p, Vec3 at) {
		long now = p.level().getGameTime();
		TANGLES.put(p.getUUID(), new Tangle(p.level().dimension(), at, now + TANGLE_TICKS));
		OmnitrixState n = Omnitrix.state(p).copy();
		putPos(n, P_TANGLE, at);
		n.timers.put(key(TANGLE_UNTIL), now + TANGLE_TICKS);
		n.kitFlags |= F_TANGLE;
		Omnitrix.save(p, n);
		OmnitrixSfx.play(level(p), at, SND_VINES, 0.7f, 1.2f);
	}

	private void tickTangle(ServerPlayer p, long now) {
		Tangle t = TANGLES.get(p.getUUID());
		if (t == null) {
			if (flag(p, F_TANGLE)) {
				setFlag(p, F_TANGLE, false);
			}
			return;
		}
		if (now >= t.end() || t.dim() != p.level().dimension()) {
			TANGLES.remove(p.getUUID());
			setFlag(p, F_TANGLE, false);
			setTimer(p, TANGLE_UNTIL, 0);
			return;
		}
		if ((t.end() - now) % 5 == 0) {
			for (LivingEntity e : around(p, t.at(), TANGLE_RADIUS)) {
				AlienStatus.root(p, e, 10, 0);
			}
		}
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

	/** v0.17.2 revamp: Shift+G while the snare holds -- the vines yank the creature 4 blocks toward him. True if it did. */
	public boolean yank(ServerPlayer p) {
		long id = timer(p, SNARE_TARGET);
		Entity e = id > 0 ? p.level().getEntity((int) (id - 1)) : null;
		endSnare(p);
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			return false;
		}
		anim(p, ANIM_YANK);
		setPos(p, P_LASH, t.position().add(0, t.getBbHeight() * 0.5, 0));
		Vec3 to = p.position().subtract(t.position());
		double d = to.length();
		if (d > 1.5) {
			double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
			double move = Math.min(YANK_DISTANCE, d - 1.5) * (AlienStatus.isBoss(t) ? 0.25 : Math.max(0.3, res));
			Vec3 dir = to.normalize();
			if (t instanceof ServerPlayer tp) {
				tp.setDeltaMovement(dir.scale(move * 0.45).add(0, 0.25, 0));
				tp.hurtMarked = true;
			} else {
				t.teleportTo(t.getX() + dir.x * move, t.getY() + Math.max(0, dir.y * move), t.getZ() + dir.z * move);
			}
		}
		t.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		OmnitrixSfx.play(level(p), t.position(), SND_LASH, 1f, 0.8f);
		return true;
	}

	// ---------------------------------------------------------------- X / Shift+X: movement

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
		OmnitrixSfx.play(level(p), p.position(), SND_JET, 1f, 1f);
		return true;
	}

	/** Shift+X down: the vine catches the block he looks at. A tap hauls him there; a hold swings him on it. */
	public boolean swingStart(ServerPlayer p) {
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
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		putPos(n, P_ANCHOR, hit.getLocation());
		n.timers.put(key(SWING_START), now);
		n.timers.put(key(SWING_LEN), Math.round(hit.getLocation().distanceTo(p.position()) * 64) + 1);
		n.timers.put(key(JET_UNTIL), now + SWING_MAX + JET_TICKS);
		n.kitFlags |= F_SWING | F_JET;
		Omnitrix.save(p, n);
		OmnitrixSfx.play(level(p), p.position(), SND_VINES, 0.8f, 1.3f);
		return true;
	}

	/** Shift+X up: a tap hauls him to the anchor (the old Vine Swing); after a hold he lets go with the swing's momentum. */
	public void swingRelease(ServerPlayer p, int heldTicks) {
		Vec3 a = pos(p, P_ANCHOR);
		setFlag(p, F_SWING, false);
		setTimer(p, SWING_START, 0);
		setTimer(p, SWING_LEN, 0);
		setTimer(p, JET_UNTIL, p.level().getGameTime() + JET_TICKS);
		if (heldTicks < SWING_HOLD && a != null) {
			Vec3 to = a.subtract(p.position());
			Vec3 v = to.normalize().scale(Math.min(1.9, 0.7 + to.length() * 0.08));
			p.setDeltaMovement(v.x, v.y + 0.35, v.z);
			p.hurtMarked = true;
		} else {
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x * 1.15, Math.max(v.y, 0) + 0.25, v.z * 1.15); // a little flick off the end of the vine
			p.hurtMarked = true;
		}
		p.fallDistance = 0f;
		anim(p, ANIM_SWING);
	}

	private void tickSwing(ServerPlayer p, long now) {
		if (!flag(p, F_SWING)) {
			return;
		}
		long held = now - timer(p, SWING_START);
		Vec3 a = pos(p, P_ANCHOR);
		if (a == null || held > SWING_MAX) {
			swingRelease(p, SWING_MAX);
			return;
		}
		if (held < SWING_HOLD) {
			return;
		}
		// the pendulum: the vine never stretches -- the outward part of his speed is cancelled, and a slight pull keeps it taut
		double len = Math.max(2.0, (timer(p, SWING_LEN) - 1) / 64.0);
		Vec3 d = p.position().subtract(a);
		double r = d.length();
		Vec3 v = p.getDeltaMovement();
		if (r > len && r > 1.0e-3) {
			Vec3 n = d.scale(1.0 / r);
			double out = v.dot(n);
			if (out > 0) {
				v = v.subtract(n.scale(out));
			}
			v = v.subtract(n.scale(Math.min(0.5, (r - len) * 0.4)));
		}
		// leaning into the look pumps the swing
		Vec3 look = p.getLookAngle();
		v = v.add(look.x * 0.03, 0, look.z * 0.03);
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		// the vine slowly reels in, so a long swing rises
		if (len > 2.5) {
			setTimer(p, SWING_LEN, Math.round((len - 0.03) * 64) + 1);
		}
	}

	// ---------------------------------------------------------------- Z / Shift+Z: methane

	/** Z: vent the methane cloud (Z again lights it). Refused while the cooldown after a blast runs; bubbles away under water. */
	public boolean cloud(ServerPlayer p) {
		int left = cooldownLeft(p, CLOUD);
		if (left > 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cooldown",
					Component.translatable("projecthero.omnitrix." + ID + "." + CLOUD), String.format(java.util.Locale.ROOT, "%.1f", left / 20f))
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (doused(p)) {
			say(p, "message.projecthero.omnitrix.swampfire.bubbles");
			level(p).sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.5, 0.5, 0.5, 0.1);
			OmnitrixSfx.play(level(p), p.position(), SND_FIZZLE, 0.6f, 0.8f);
			return false;
		}
		anim(p, ANIM_CLOUD);
		long now = p.level().getGameTime();
		Vec3 at = p.position().add(0, 0.6, 0);
		CLOUDS.put(p.getUUID(), new Cloud(p.level().dimension(), at, now + CLOUD_TICKS));
		OmnitrixState n = Omnitrix.state(p).copy();
		putPos(n, P_CLOUD, at);
		n.timers.put(key(CLOUD_UNTIL), now + CLOUD_TICKS);
		n.kitFlags |= F_CLOUD;
		Omnitrix.save(p, n);
		OmnitrixSfx.play(level(p), at, SND_GAS, 1f, 1f);
		return true;
	}

	private void tickCloud(ServerPlayer p, ServerLevel l, long now) {
		Cloud c = CLOUDS.get(p.getUUID());
		if (c == null) {
			return;
		}
		if (now >= c.end() || c.dim() != l.dimension()) {
			clearCloud(p);
			cooldown(p, CLOUD, -1); // it drifted away unlit: the cooldown starts now
			return;
		}
		if ((c.end() - now) % 20 == 0) {
			for (LivingEntity e : around(p, c.at(), CLOUD_RADIUS)) {
				hurt(p, e, CLOUD_DAMAGE, false);
				e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), p);
			}
			if (l.random.nextInt(2) == 0) {
				OmnitrixSfx.play(l, c.at(), SND_GAS, 0.6f, 0.8f);
			}
		}
		// a Fire Blast flying through the gas sets it off
		for (AlienProjectileEntity proj : l.getEntitiesOfClass(AlienProjectileEntity.class, new AABB(c.at(), c.at()).inflate(CLOUD_RADIUS),
				e -> e.getOwner() == p && "swampfire_blast".equals(e.kind()) && e.distanceToSqr(c.at()) <= CLOUD_RADIUS * CLOUD_RADIUS)) {
			proj.discard();
			ignite(p);
			return;
		}
	}

	private void clearCloud(ServerPlayer p) {
		CLOUDS.remove(p.getUUID());
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags &= ~F_CLOUD;
		n.timers.remove(key(CLOUD_UNTIL));
		Omnitrix.save(p, n);
	}

	/**
	 * Ignite: the cloud goes up -- 19 in 5 blocks, alight 5 s, and a ring of real fire. Z again, or any of his fire passing through
	 * it. Nothing to light (or under water) = refused, no cooldown; the Z cooldown starts after the blast.
	 */
	public boolean ignite(ServerPlayer p) {
		Cloud c = CLOUDS.get(p.getUUID());
		if (c == null || c.dim() != p.level().dimension()) {
			say(p, "message.projecthero.omnitrix.swampfire.no_cloud");
			return false;
		}
		if (fizzles(p)) {
			return false;
		}
		anim(p, ANIM_IGNITE);
		clearCloud(p);
		cooldown(p, CLOUD, -1);
		ServerLevel l = level(p);
		Vec3 at = c.at();
		setPos(p, P_BLAST, at);
		for (LivingEntity e : around(p, at, IGNITE_RADIUS)) {
			if (hurt(p, e, IGNITE_DAMAGE * fireFactor(p), true)) {
				e.igniteForSeconds(5);
				shove(e, at, 0.8, 0.4);
			}
		}
		PyrokinesisHandlers.fireRing(p, l, at, IGNITE_RING, fireTtl(l, BlockPos.containing(at), IGNITE_FIRE_TICKS));
		com.projecthero.mod.quirk.fx.QuirkCamera.shakeAround(l, at, 10, 0.3f, 6);
		OmnitrixSfx.play(l, at, SND_IGNITE, 1.4f, 1f);
		return true;
	}

	public boolean infernoStart(ServerPlayer p) {
		if (fizzles(p) || !use(p, INFERNO)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE_TICKS, 9, false, false, false));
		setTimer(p, CHARGE_AT, p.level().getGameTime() + CHARGE_TICKS);
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 1f, 1f);
		return true;
	}

	/** Methane Inferno's explosion and the Methane Surge after it (tests call it straight). */
	public void inferno(ServerPlayer p) {
		anim(p, ANIM_INFERNO);
		ServerLevel l = level(p);
		Vec3 at = p.position().add(0, 1.0, 0);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		putPos(n, P_BLAST, at);
		n.timers.put(key(SURGE_UNTIL), now + SURGE_TICKS);
		n.kitFlags |= F_SURGE;
		Omnitrix.save(p, n);
		for (LivingEntity e : around(p, at, INFERNO_RADIUS)) {
			double d = Math.min(1.0, e.position().distanceTo(p.position()) / INFERNO_RADIUS);
			float dmg = (float) (INFERNO_DAMAGE + (INFERNO_EDGE - INFERNO_DAMAGE) * d);
			if (hurt(p, e, dmg, true)) {
				e.igniteForSeconds(8);
				shove(e, p.position(), 1.4, 0.6);
			}
		}
		int ttl = fireTtl(l, p.blockPosition(), INFERNO_FIRE_TICKS);
		PyrokinesisHandlers.fireRing(p, l, p.position(), INFERNO_RADIUS * 0.8, ttl);
		PyrokinesisHandlers.fireScatter(p, l, p.position(), INFERNO_RADIUS - 1.0, 14, ttl);
		if (CLOUDS.containsKey(p.getUUID())) {
			ignite(p); // his own cloud goes up with it
		}
		com.projecthero.mod.quirk.fx.QuirkCamera.shakeAround(l, at, 16, 0.5f, 10);
		OmnitrixSfx.play(l, at, SND_INFERNO, 1.6f, 1f);
	}

	// ---------------------------------------------------------------- V / Shift+V: plants

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
		p.removeEffect(MobEffects.WEAKNESS); // v0.17.2 revamp: he reattaches -- slowness and weakness grow out too
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, REGROW_TICKS, 3, false, false, false));
		setTimer(p, REGROW_UNTIL, p.level().getGameTime() + REGROW_TICKS);
		setFlag(p, F_REGROW, true);
		OmnitrixSfx.play(level(p), p.position(), SND_REGROW, 1f, 1f);
		return true;
	}

	/** Shift+V: Overgrowth -- bone meal round him, and a vine ladder up the wall he looks at. */
	public boolean overgrowth(ServerPlayer p) {
		if (!use(p, OVERGROWTH)) {
			return false;
		}
		anim(p, ANIM_BLOOM);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		int r = (int) BLOOM_RADIUS;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
			if (bp.distSqr(c) > BLOOM_RADIUS * BLOOM_RADIUS) {
				continue;
			}
			BlockState st = l.getBlockState(bp);
			if (st.getBlock() instanceof BonemealableBlock b && !(st.getBlock() instanceof net.minecraft.world.level.block.GrassBlock)
					&& b.isValidBonemealTarget(l, bp, st) && b.isBonemealSuccess(l, l.random, bp, st)) {
				b.performBonemeal(l, l.random, bp.immutable(), st);
				l.levelEvent(1505, bp, 0); // the bone meal sparkle
			}
		}
		ladder(p);
		OmnitrixSfx.play(l, p.position(), SND_REGROW, 0.8f, 1.2f);
		return true;
	}

	/** The vine ladder: real vine blocks up the face of the wall he looks at (5 blocks), up to 8 tall, gone after 30 s. Placed count. */
	public int ladder(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(LADDER_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection().getAxis() == Direction.Axis.Y) {
			return 0;
		}
		Direction face = hit.getDirection();
		BlockPos wall = hit.getBlockPos();
		BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(face.getOpposite()), true);
		int placed = 0;
		for (int i = 0; i < LADDER_MAX; i++) {
			BlockPos w = wall.above(i);
			BlockPos at = w.relative(face);
			if (!l.getBlockState(w).isFaceSturdy(l, w, face) || !p.mayBuild() || !l.mayInteract(p, at)) {
				break;
			}
			BlockState here = l.getBlockState(at);
			if (here.is(Blocks.VINE)) {
				placed++;
				continue;
			}
			if (!here.isAir() || !TempBlocks.place(l, at, vine, LADDER_TICKS)) {
				break;
			}
			placed++;
		}
		if (placed > 0) {
			OmnitrixSfx.play(l, hit.getLocation(), SND_VINES, 0.8f, 1.1f);
		}
		return placed;
	}

	// ---------------------------------------------------------------- C / Shift+C: roots and seeds

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
		OmnitrixState n = Omnitrix.state(p).copy();
		putPos(n, P_ROOTS, from);
		putPos(n, P_ROOTS + "_dir", dir);
		Omnitrix.save(p, n);
		return true;
	}

	private void tickRoots(ServerPlayer p, ServerLevel l, long now) {
		Roots r = ROOT_LINES.get(p.getUUID());
		if (r == null) {
			return;
		}
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
			OmnitrixSfx.play(l, at, SND_ROOTS, 0.7f, 1f);
			// the roots' embers light his cloud if they burst under it
			Cloud c = CLOUDS.get(p.getUUID());
			if (c != null && c.at().distanceToSqr(at) <= CLOUD_RADIUS * CLOUD_RADIUS && !doused(p)) {
				ignite(p);
			}
		}
	}

	/** Shift+C: five seeds flung in a fan; each plants where it lands. */
	public boolean seedScatter(ServerPlayer p) {
		if (!use(p, SCATTER)) {
			return false;
		}
		anim(p, ANIM_SCATTER);
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < SCATTER_SEEDS; i++) {
			double yaw = Math.toRadians((i - (SCATTER_SEEDS - 1) / 2.0) * 14.0);
			Vec3 d = new Vec3(look.x * Math.cos(yaw) - look.z * Math.sin(yaw), Math.max(look.y, -0.2) + 0.25, look.x * Math.sin(yaw) + look.z * Math.cos(yaw));
			AlienProjectileEntity.of(p, "swampfire_scatter").colour(0x6A9A2A).size(0.3f).damage(0f).gravity(0.06).speed(0.85).life(60).direction(d)
					.spawn();
		}
		OmnitrixSfx.play(level(p), p.position(), SND_SEED, 1f, 0.8f);
		return true;
	}

	/** One seed planted at {@code at} for 20 s (the oldest goes past 5). Tests call it straight. */
	public void plant(ServerPlayer p, Vec3 at) {
		List<Mine> list = MINES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		list.add(new Mine(p.level().dimension(), at, p.level().getGameTime() + MINE_TICKS));
		while (list.size() > MAX_MINES) {
			list.remove(0);
		}
		syncMines(p, list);
	}

	/** How many seeds of his are planted (tests). */
	public static int mines(ServerPlayer p) {
		List<Mine> list = MINES.get(p.getUUID());
		return list == null ? 0 : list.size();
	}

	private void syncMines(ServerPlayer p, List<Mine> list) {
		OmnitrixState n = Omnitrix.state(p).copy();
		for (int i = 0; i < MAX_MINES; i++) {
			if (i < list.size()) {
				putPos(n, P_MINE + i, list.get(i).at());
				n.timers.put(key(MINE_UNTIL + i), list.get(i).end());
			} else {
				removePos(n, P_MINE + i);
				n.timers.remove(key(MINE_UNTIL + i));
			}
		}
		Omnitrix.save(p, n);
	}

	/** An enemy, for the seeds: a monster, a player who is not a squad ally, or anything hunting him. */
	private static boolean enemy(ServerPlayer p, LivingEntity e) {
		return e instanceof Enemy || e instanceof Player || (e instanceof Mob m && m.getTarget() == p);
	}

	private void tickMines(ServerPlayer p, ServerLevel l, long now) {
		List<Mine> list = MINES.get(p.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		boolean changed = false;
		for (Iterator<Mine> it = list.iterator(); it.hasNext();) {
			Mine m = it.next();
			if (now >= m.end() || m.dim() != l.dimension()) {
				it.remove();
				changed = true;
				continue;
			}
			if (now % 2 != 0) {
				continue;
			}
			List<LivingEntity> near = around(p, m.at(), MINE_TRIGGER);
			near.removeIf(e -> !enemy(p, e));
			if (near.isEmpty()) {
				continue;
			}
			// the seed bursts into snapping vines
			for (LivingEntity e : around(p, m.at(), MINE_TRIGGER + 0.5)) {
				if (hurt(p, e, MINE_DAMAGE, false)) {
					AlienStatus.root(p, e, MINE_ROOT, 10);
				}
			}
			setPos(p, P_SNAP, m.at());
			setTimer(p, "snap_at", now);
			OmnitrixSfx.play(l, m.at(), SND_VINES, 0.9f, 1.2f);
			it.remove();
			changed = true;
		}
		if (changed) {
			syncMines(p, list);
		}
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_MULCH)) {
			return true;
		}
		if (target instanceof LivingEntity le) {
			if (le.isOnFire()) {
				// v0.17.2 revamp: +4 (damage pass; was +2) against a creature already burning (its own hit; vanilla's punch still lands after it)
				hurt(p, le, FIST_BONUS, true);
				le.invulnerableTime = 0;
			}
			le.igniteForSeconds(3);
			anim(p, ANIM_PUNCH);
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (flag(p, F_MULCH) || flag(p, F_CHARGE)) {
			return InteractionResult.FAIL;
		}
		return vineLash(p) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Use (empty hand): Vine Lash -- a whip of vine, 6 blocks; small creatures are pulled a little toward him. */
	public boolean vineLash(ServerPlayer p) {
		if (cooldownLeft(p, LASH) > 0) {
			return false; // quiet: a right-click spam should not flood the action bar
		}
		cooldown(p, LASH, -1);
		anim(p, ANIM_LASH);
		LivingEntity t = lookTarget(p, LASH_RANGE);
		Vec3 end;
		if (t != null) {
			end = t.position().add(0, t.getBbHeight() * 0.5, 0);
			hurt(p, t, LASH_DAMAGE, false);
			if (!AlienStatus.isBoss(t) && t.getBbWidth() <= 1.0f && t.getBbHeight() <= 2.2f) {
				Vec3 to = p.position().subtract(t.position());
				Vec3 h = new Vec3(to.x, 0, to.z);
				if (h.lengthSqr() > 1.0e-4) {
					h = h.normalize();
					t.push(h.x * 0.6, 0.25, h.z * 0.6);
					t.hurtMarked = true;
				}
			}
		} else {
			end = p.getEyePosition().add(p.getLookAngle().scale(LASH_RANGE));
		}
		setPos(p, P_LASH, end);
		OmnitrixSfx.play(level(p), p.position(), SND_LASH, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- passives: Reassembly, Swamp Stench

	/** Reassembly is off cooldown (client-safe). */
	public boolean reassemblyReady(Player p) {
		return cooldownLeft(p, REASSEMBLY) <= 0;
	}

	/**
	 * v0.17.2 revamp, Reassembly: lethal damage doesn't kill him -- he bursts into a heap of mulch for 3 s and pulls himself back
	 * together. Never against /kill or the void, never while frozen, never on cooldown. True when it saved him.
	 */
	public boolean reassemble(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || frozen(p) || !reassemblyReady(p) || flag(p, F_MULCH)) {
			return false;
		}
		long now = p.level().getGameTime();
		p.setHealth(1f);
		p.clearFire();
		stopTorch(p);
		if (flag(p, F_SWING)) {
			swingRelease(p, SWING_MAX);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(REASSEMBLY), now + REASSEMBLY_COOLDOWN);
		n.timers.put(key(MULCH_UNTIL), now + MULCH_TICKS);
		n.kitFlags = (n.kitFlags | F_MULCH) & ~(F_REGROW | F_CHARGE | F_SNARE);
		n.animId = 0;
		Omnitrix.save(p, n);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MULCH_TICKS, 9, false, false, false));
		p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
		p.hurtMarked = true;
		loseAll(p);
		OmnitrixSfx.play(level(p), p.position(), SND_MULCH, 1.2f, 1f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.swampfire.mulched").withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	/** Every mob hunting him forgets him (a heap of mulch is nothing to chase). */
	private static void loseAll(ServerPlayer p) {
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
			AlienStatus.loseTrack(m, p);
		}
	}

	private void tickMulch(ServerPlayer p, ServerLevel l, long now) {
		p.clearFire();
		if (now % 5 == 0) {
			loseAll(p);
		}
		Vec3 v = p.getDeltaMovement();
		if (v.x != 0 || v.z != 0 || v.y > 0) {
			p.setDeltaMovement(0, Math.min(0, v.y), 0);
			p.hurtMarked = true;
		}
		if (now >= timer(p, MULCH_UNTIL)) {
			reform(p);
		}
	}

	/** The heap pulls itself back together: 10 HP and 2 s of Resistance II. */
	public void reform(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(MULCH_UNTIL));
		n.kitFlags &= ~F_MULCH;
		Omnitrix.save(p, n);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		p.setHealth(Math.min(p.getMaxHealth(), Math.max(p.getHealth(), REFORM_HEALTH)));
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false, true));
		anim(p, ANIM_REFORM);
		OmnitrixSfx.play(level(p), p.position(), SND_REFORM, 1.1f, 1f);
	}

	/** Swamp Stench: a creature standing within 2 blocks of him for 2 s gets 3 s of Nausea. */
	private void tickStench(ServerPlayer p, long now) {
		if (now % 10 != 0) {
			return;
		}
		Map<Integer, Integer> m = STENCH.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		Set<Integer> seen = new HashSet<>();
		for (LivingEntity e : around(p, p.position().add(0, 0.9, 0), STENCH_RADIUS + 0.5)) {
			if (e.distanceToSqr(p) > (STENCH_RADIUS + 0.4) * (STENCH_RADIUS + 0.4)) {
				continue;
			}
			seen.add(e.getId());
			int t = m.merge(e.getId(), 10, Integer::sum);
			if (t >= STENCH_TICKS) {
				e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, STENCH_NAUSEA, 0, false, false, true), p);
				m.put(e.getId(), 0);
			}
		}
		m.keySet().retainAll(seen);
	}

	/**
	 * Whether {@code at} is in a Swampfire's methane cloud or his stench (Wildmutt's Choked Nose counts both). Server only.
	 */
	public static boolean inGas(Level level, Vec3 at) {
		for (Cloud c : CLOUDS.values()) {
			if (c.dim() == level.dimension() && c.at().distanceToSqr(at) <= (CLOUD_RADIUS + 0.5) * (CLOUD_RADIUS + 0.5)) {
				return true;
			}
		}
		if (level.getServer() == null) {
			return false;
		}
		for (Player pl : level.players()) {
			if (Aliens.kit(ID) instanceof SwampfireKit k && k.isSwampfire(pl) && pl.position().distanceToSqr(at) <= (STENCH_RADIUS + 1) * (STENCH_RADIUS + 1)
					&& pl.position().distanceToSqr(at) > 0.01) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- tests

	/** Whether a methane cloud of his hangs right now (tests). */
	public static boolean hasCloud(ServerPlayer p) {
		return CLOUDS.containsKey(p.getUUID());
	}

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		UUID id = p.getUUID();
		return CLOUDS.containsKey(id) || ROOT_LINES.containsKey(id) || TANGLES.containsKey(id) || MINES.containsKey(id) || STENCH.containsKey(id)
				|| LAST_TICK.containsKey(id);
	}
}
