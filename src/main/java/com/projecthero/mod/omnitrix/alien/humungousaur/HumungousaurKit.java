package com.projecthero.mod.omnitrix.alien.humungousaur;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 revamp (user: "Yes do all this stuff but dont add ultimate aliens yet please" -- docs/OMNITRIX_REVAMP_HUMUNGOUSAUR.md,
 * without Ultimate Humungousaur): Humungousaur, the Vaxasaurian, is THE SIZE ALIEN -- every size step widens his areas, lifts
 * heavier things and adds +10% to every move (not +20%, which broke the damage scale). Slow, huge, a tail club and bone spikes,
 * a Brace that takes blasts head-on; nothing like Four Arms' combos, claps and carries.
 *
 * <pre>
 *   R        Haymaker       a straight punch (7, small mobs knocked flat)                             cd 0.7 s
 *   Shift+R  Uppercut       launched skyward (14)                                                     cd 4 s
 *   G        Ground Pound   shockwave ring (15 in 4.5, +1.5 a step), thrown up                         cd 6 s
 *   Shift+G  Tail Club      a full tail spin (13 in 3.5, +1 a step); at x3.0 the club: 16, knocked down 1 s  cd 5 s
 *   X        Stampede       head-down ram (12); HOLD X to keep running up to 2.5 s, steering; carries the first target;
 *                           a wall ends it in a crash (+6, stunned 1 s); bursts glass, leaves, fences, planks  cd 6 s
 *   Shift+X  Dino Leap      a great bound, landing quake 10 in 4 (+1 a step); at x3.0 the landing is the TITANIC SLAM
 *                           (16 in 7, cracks)                                                          cd 6 s
 *   Z        Heavy Hurl     grabs the creature in front (lift scales with size, never bosses) or tears up a slab of the
 *                           ground and throws it where you look: 18 to what it hits, 8 splash in 2.5  cd 8 s
 *   Shift+Z  EARTHSHAKER    ULTIMATE: rears up and stomps three times (0.6 s apart), rooted: 10, 10 in 6, then 55 in 8
 *                           (+1 block a step), each a shockwave ring with cracks, everything knocked up  cd 30 s
 *   C        Primal Roar    a 10-block cone (+2 a step): 12, weakened + slowed; mobs under 20 HP flee 3 s  cd 8 s
 *   Shift+C  Spike Burst    bone spikes jut out: 14 in 3 (+1 a step); 8 s of thorns 3 and +3 armour   cd 15 s
 *   V        Grow           one size step bigger (pushes creatures aside); HOLD V 1 s: straight to full size  cd 2 s
 *   Shift+V  Shrink         one step smaller; HOLD Shift+V 1 s: straight back to base size            cd 0.5 s
 *   Attack   Heavy Fists    his attack stat (5, +2 / +5 at the steps); from x2.2 it cleaves a second creature
 *   Use      Brace          hold (empty hand): frontal damage -60%, no knockback, frontal projectiles stopped, half
 *                           speed; up to 4 s, then 3 s cooldown
 * </pre>
 * Passives: Vaxasaurian Hide (44 HP, armour 10 +3 toughness, 60% KB resistance, safe falls of 9), Size-Shift (+10% damage,
 * +3 armour, +0.75 reach, +15% KB resistance, -8% speed a step), Giant's Footsteps (full size: sprinting shakes cameras a
 * little and tramples crops and grass). Weaknesses: big and slow, a huge target (Grow refused without room), a hungry giant
 * (hunger x1.5 at x2.2, x2 at x3.0), rooted while the Earthshaker stomps.
 */
public final class HumungousaurKit extends AlienKit {
	public static final String ID = Aliens.HUMUNGOUSAUR.id();

	public static final int ANIM_PUNCH = 1;
	public static final int ANIM_UPPER = 2;
	public static final int ANIM_POUND = 3;
	public static final int ANIM_TAIL = 4;
	public static final int ANIM_CHARGE = 5;
	public static final int ANIM_LEAP = 6;
	public static final int ANIM_LAND = 7;
	public static final int ANIM_HURL = 8;
	public static final int ANIM_CRASH = 9;
	public static final int ANIM_GROW = 10;
	public static final int ANIM_SHRINK = 11;
	public static final int ANIM_ROAR = 12;
	public static final int ANIM_QUAKE = 13;
	public static final int ANIM_TITAN_LAND = 14;
	public static final int ANIM_SPIKES = 15;
	public static final int ANIM_CLEAVE = 16;

	public static final int F_CHARGE = 1;
	public static final int F_LEAP = 2;
	public static final int F_QUAKE = 4;
	public static final int F_SPIKES = 8;
	public static final int F_BRACE = 16;
	public static final int F_XHELD = 32;
	public static final int F_VHELD = 64;
	public static final int F_SVHELD = 128;

	public static final String PUNCH = "haymaker";
	public static final String UPPER = "uppercut";
	public static final String POUND = "ground_pound";
	public static final String TAIL = "tail_club";
	public static final String CHARGE = "stampede";
	public static final String LEAP = "dino_leap";
	public static final String HURL = "heavy_hurl";
	public static final String QUAKE = "earthshaker";
	public static final String GROW = "grow";
	public static final String SHRINK = "shrink";
	public static final String ROAR = "primal_roar";
	public static final String SPIKES = "spike_burst";
	public static final String FISTS = "heavy_fists";
	public static final String BRACE = "brace";

	/** Synced timers: the size step (absent = 0, 1, 2), when each timed move began / ends. */
	public static final String STAGE = "stage", CHARGE_START = "charge_start", LEAP_START = "leap_start", QUAKE_START = "quake_start",
			SPIKES_START = "spikes_start", SPIKES_END = "spikes_end", BRACE_USE = "brace_use", BRACE_START = "brace_start", V_DOWN = "v_down";

	public static final int MAX_STAGE = 2;
	/**
	 * v0.17.2 (user: "make humungosaur 12 blocks tall please, his bigger size 24 blocks and is biggest size 36 blocks"): 12 blocks tall at base (x6.67), 24 at the second size (x13.3); the game caps a body's scale at x16, so the
	 * biggest size is 28.8 blocks, as close to 36 as Minecraft allows.
	 */
	public static final float BASE_SCALE = 12f / 1.8f;
	public static final float[] STAGE_SCALE = { 0f, BASE_SCALE, 16f - BASE_SCALE }; // added onto the base scale (ADD_MULTIPLIED_BASE): 12 / 24 / 28.8 blocks
	public static final float[] STAGE_ARMOR = { 0f, 3f, 6f };
	public static final float[] STAGE_ATTACK = { 0f, 2f, 5f };
	public static final float[] STAGE_REACH = { 0f, 12f, 17f }; // v0.17.2: a giant's arm -- on top of the base 12 (24 / 29)
	public static final float[] STAGE_KB = { 0f, 0.15f, 0.3f };
	public static final float[] STAGE_SPEED = { 0f, 0.1f, 0.2f }; // v0.17.2: bigger strides as he grows
	/** v0.17.2 (user: "+10% per size step (not 20%)"). */
	public static final float STEP_BONUS = 0.1f;

	private static final ResourceLocation M_SCALE = PowerToggles.id("humungousaur_grow_scale");
	private static final ResourceLocation M_ARMOR = PowerToggles.id("humungousaur_grow_armor");
	private static final ResourceLocation M_ATTACK = PowerToggles.id("humungousaur_grow_attack");
	private static final ResourceLocation M_REACH_E = PowerToggles.id("humungousaur_grow_reach_entity");
	private static final ResourceLocation M_REACH_B = PowerToggles.id("humungousaur_grow_reach_block");
	private static final ResourceLocation M_KB = PowerToggles.id("humungousaur_grow_knockback");
	private static final ResourceLocation M_SPEED = PowerToggles.id("humungousaur_grow_speed");
	private static final ResourceLocation M_SPIKE_ARMOR = PowerToggles.id("humungousaur_spike_armor");
	private static final ResourceLocation M_BRACE_SPEED = PowerToggles.id("humungousaur_brace_speed");
	private static final ResourceLocation M_BRACE_KB = PowerToggles.id("humungousaur_brace_knockback");
	private static final ResourceLocation M_ROOT_SPEED = PowerToggles.id("humungousaur_root_speed");
	private static final ResourceLocation M_ROOT_JUMP = PowerToggles.id("humungousaur_root_jump");

	public static final float PUNCH_DAMAGE = 7f;
	public static final float UPPER_DAMAGE = 14f;
	public static final float POUND_DAMAGE = 15f;
	public static final double POUND_RADIUS = 4.5, POUND_STEP = 1.5;
	public static final float TAIL_DAMAGE = 13f, CLUB_DAMAGE = 16f;
	public static final double TAIL_RADIUS = 3.5;
	public static final int CLUB_DOWN_TICKS = 20;
	public static final float CHARGE_DAMAGE = 12f, CRASH_DAMAGE = 6f;
	public static final int CHARGE_TICKS = 20, CHARGE_MAX_TICKS = 50, CRASH_STUN_TICKS = 20;
	public static final float LAND_DAMAGE = 10f;
	public static final double LAND_RADIUS = 4.0;
	public static final float TITAN_LAND_DAMAGE = 16f;
	public static final double TITAN_LAND_RADIUS = 7.0;
	public static final float HURL_DAMAGE = 18f, HURL_SPLASH = 8f;
	public static final double HURL_SPLASH_RADIUS = 2.5;
	public static final int HURL_FLIGHT_TICKS = 60;
	/** The widest / tallest creature Heavy Hurl lifts at each size: a player, an iron golem, a ravager. */
	public static final float[] LIFT_WIDTH = { 1.0f, 1.5f, 2.0f };
	public static final float[] LIFT_HEIGHT = { 2.0f, 3.0f, 3.2f };
	public static final float QUAKE_SMALL = 10f, QUAKE_BIG = 55f;
	public static final double QUAKE_SMALL_RADIUS = 6.0, QUAKE_BIG_RADIUS = 8.0;
	/** Earthshaker's three stomps, ticks after the press (0.6 s apart), and when he can move again. */
	public static final int[] QUAKE_STOMPS = { 12, 24, 36 };
	public static final int QUAKE_TICKS = 44;
	public static final float ROAR_DAMAGE = 12f;
	public static final double ROAR_RANGE = 10.0, ROAR_DEG = 70.0;
	public static final float ROAR_FLEE_HEALTH = 20f;
	public static final int ROAR_FLEE_TICKS = 60;
	public static final float SPIKE_DAMAGE = 14f, THORNS = 3f;
	public static final double SPIKE_RADIUS = 3.0;
	public static final int SPIKE_TICKS = 160;
	public static final int BRACE_MAX_TICKS = 80, BRACE_COOLDOWN = 60, BRACE_GRACE = 6;
	public static final float BRACE_TAKEN = 0.4f;
	public static final int HOLD_TICKS = 20;
	public static final int AIR_MAX = 60;
	public static final int SAFE_FALL = 9;

	public static final OmnitrixSfx.Cue SND_PUNCH = OmnitrixSfx.cue("humungousaur_punch")
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.5f, 0.6f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.6f, 1);
	public static final OmnitrixSfx.Cue SND_QUAKE = OmnitrixSfx.cue("humungousaur_quake")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.4f, 0.6f)
			.layer(SoundEvents.RAVAGER_STEP, 0.6f, 0.5f)
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.55f, 0.6f, 1)
			.layer(SoundEvents.GRAVEL_BREAK, 0.5f, 0.6f, 3);
	public static final OmnitrixSfx.Cue SND_SWOOSH = OmnitrixSfx.cue("humungousaur_swoosh")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 0.55f)
			.layer(SoundEvents.RAVAGER_ATTACK, 0.35f, 0.8f, 2);
	public static final OmnitrixSfx.Cue SND_ROAR = OmnitrixSfx.cue("humungousaur_roar")
			.layer(SoundEvents.RAVAGER_ROAR, 0.6f, 0.75f)
			.layer(SoundEvents.ENDER_DRAGON_GROWL, 0.15f, 1.3f)
			.layer(SoundEvents.POLAR_BEAR_WARNING, 0.4f, 0.6f, 2);
	public static final OmnitrixSfx.Cue SND_GROW = OmnitrixSfx.cue("humungousaur_grow")
			.layer(SoundEvents.RAVAGER_STUNNED, 0.45f, 0.6f)
			.layer(SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.5f, 0.6f, 2)
			.layer(SoundEvents.DRIPSTONE_BLOCK_BREAK, 0.4f, 0.6f, 4);
	public static final OmnitrixSfx.Cue SND_SHRINK = OmnitrixSfx.cue("humungousaur_shrink")
			.layer(SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.45f, 0.9f)
			.layer(SoundEvents.RAVAGER_STEP, 0.4f, 1.0f, 2);
	public static final OmnitrixSfx.Cue SND_TEAR = OmnitrixSfx.cue("humungousaur_tear")
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.STONE_BREAK, 0.55f, 0.6f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 0.6f, 4);
	public static final OmnitrixSfx.Cue SND_STOMP = OmnitrixSfx.cue("humungousaur_stomp")
			.layer(SoundEvents.RAVAGER_STEP, 0.5f, 0.6f)
			.layer(SoundEvents.HORSE_GALLOP, 0.4f, 0.5f)
			.jitter(0.08f).gap(5);
	public static final OmnitrixSfx.Cue SND_SPIKES = OmnitrixSfx.cue("humungousaur_spikes")
			.layer(SoundEvents.DRIPSTONE_BLOCK_BREAK, 0.55f, 0.6f)
			.layer(SoundEvents.SKELETON_HURT, 0.35f, 0.5f)
			.layer(SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.4f, 0.7f, 1);
	public static final OmnitrixSfx.Cue SND_THUD = OmnitrixSfx.cue("humungousaur_thud")
			.layer(SoundEvents.SHIELD_BLOCK, 0.4f, 0.55f)
			.layer(SoundEvents.RAVAGER_STEP, 0.35f, 0.7f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_CRASH = OmnitrixSfx.cue("humungousaur_crash")
			.layer(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.45f, 0.6f)
			.layer(SoundEvents.RAVAGER_STUNNED, 0.4f, 0.8f)
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.5f, 0.6f, 1);

	private static final int DUST = 0xB0A090;

	/** player -> creatures the running Stampede already rammed, and the one it carries (or -1). */
	private static final class Run {
		final Set<Integer> hits = new HashSet<>();
		int carried = -1;
	}

	/** A creature Heavy Hurl threw: who threw it, when, how hard. */
	private record Thrown(UUID thrower, long at, float mult) {
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	/** thrown creature's id -> the throw. */
	private static final Map<Integer, Thrown> THROWN = new ConcurrentHashMap<>();
	/** player -> creature id -> fleeing until (the Primal Roar). */
	private static final Map<UUID, Map<Integer, Long>> FLEEING = new ConcurrentHashMap<>();
	/** player -> his food exhaustion last tick (the Hungry Giant). */
	private static final Map<UUID, Float> LAST_EXHAUSTION = new ConcurrentHashMap<>();
	/** player -> the game tick he was last ticked (the timed stomps never double up). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private HumungousaurKit() {
	}

	public static void register() {
		Aliens.register(new HumungousaurKit());
		AlienProjectileEntity.onHit("humungousaur_slab", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				BlockState st = Block.stateById(proj.colour());
				QuirkFx.shards(l, at.add(0, 0.3, 0)).block(st.isAir() ? Blocks.DIRT.defaultBlockState() : st).size(1.6f).count(14).spawn();
				QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, DUST).size((float) HURL_SPLASH_RADIUS + 0.5f).thickness(0.6f).life(12).ground().spawn();
				QuirkCamera.shakeAround(l, at, 8, 0.25f, 6);
				OmnitrixSfx.play(l, at, SND_QUAKE, 0.9f, 1.2f);
			}
		});
	}

	public static void clearSessionState() {
		RUNS.clear();
		THROWN.clear();
		FLEEING.clear();
		LAST_EXHAUSTION.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(44).speed(0.85f).scale(BASE_SCALE).armor(10, 3).knockbackResist(0.6f).attack(5).step(3f).reach(9f).safeFall(SAFE_FALL)
				.build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(PUNCH, AbilitySlot.SLOT_1, 14), AlienMove.shift(UPPER, AbilitySlot.SLOT_1, 80),
				AlienMove.tap(POUND, AbilitySlot.SLOT_2, 120), AlienMove.shift(TAIL, AbilitySlot.SLOT_2, 100),
				AlienMove.tap(CHARGE, AbilitySlot.SLOT_3, 120), AlienMove.shift(LEAP, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(HURL, AbilitySlot.SLOT_4, 160), AlienMove.shift(QUAKE, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(GROW, AbilitySlot.SLOT_5, 40), AlienMove.shift(SHRINK, AbilitySlot.SLOT_5, 10),
				AlienMove.tap(ROAR, AbilitySlot.SLOT_6, 160), AlienMove.shift(SPIKES, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(BRACE, "Hold Use (empty hand)", BRACE_COOLDOWN));
	}

	// ---------------------------------------------------------------- size-shift

	/** The size step (0 = the base x1.5; client-safe). */
	public static int stage(Player p) {
		return (int) Math.max(0, Math.min(MAX_STAGE, Omnitrix.state(p).readyAt(ID + "." + STAGE)));
	}

	/** Damage multiplier for the current size: +10% a step. */
	public static float mult(Player p) {
		return 1f + STEP_BONUS * stage(p);
	}

	/** Sets the size step and its attribute modifiers (0 removes them all). */
	public void setStage(ServerPlayer p, int stage) {
		stage = Math.max(0, Math.min(MAX_STAGE, stage));
		setTimer(p, STAGE, stage);
		if (stage == 0) {
			clearGrowth(p);
			return;
		}
		PowerToggles.modifier(p, Attributes.SCALE, M_SCALE, STAGE_SCALE[stage], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ARMOR, M_ARMOR, STAGE_ARMOR[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, M_ATTACK, STAGE_ATTACK[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, M_REACH_E, STAGE_REACH[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.BLOCK_INTERACTION_RANGE, M_REACH_B, STAGE_REACH[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, M_KB, STAGE_KB[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, M_SPEED, STAGE_SPEED[stage], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void clearGrowth(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.SCALE, M_SCALE);
		PowerToggles.clearModifier(p, Attributes.ARMOR, M_ARMOR);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, M_ATTACK);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, M_REACH_E);
		PowerToggles.clearModifier(p, Attributes.BLOCK_INTERACTION_RANGE, M_REACH_B);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, M_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, M_SPEED);
	}

	/** His body's scale at {@code stage} (12 / 24 / 28.8 blocks tall). */
	public static float scaleAt(int stage) {
		return BASE_SCALE + STAGE_SCALE[Math.max(0, Math.min(MAX_STAGE, stage))];
	}

	/** v0.17.2 Huge Target: room above and around him for {@code stage}'s body (so Grow never buries him in a roof). */
	public static boolean roomFor(ServerPlayer p, int stage) {
		// a player's standing box at that scale (his live box can lag a tick behind a size change)
		double w = 0.6 * scaleAt(stage), h = 1.8 * scaleAt(stage);
		AABB box = new AABB(p.getX() - w / 2, p.getY() + 0.01, p.getZ() - w / 2, p.getX() + w / 2, p.getY() + h, p.getZ() + w / 2);
		return p.level().noCollision(p, box);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		RUNS.remove(id);
		FLEEING.remove(id);
		LAST_EXHAUSTION.remove(id);
		LAST_TICK.remove(id);
		THROWN.values().removeIf(t -> t.thrower().equals(id));
		clearGrowth(p);
		PowerToggles.clearModifier(p, Attributes.ARMOR, M_SPIKE_ARMOR);
		unroot(p);
		unbrace(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { STAGE, CHARGE_START, LEAP_START, QUAKE_START, SPIKES_START, SPIKES_END, BRACE_USE, BRACE_START, V_DOWN }) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_CHARGE);
			case SLOT_4 -> flag(p, F_QUAKE);
			case SLOT_6 -> flag(p, F_SPIKES);
			default -> false;
		};
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !((flag(p, F_LEAP) || flag(p, F_QUAKE)) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		// Spike Burst: whoever strikes him in melee is stabbed
		if (flag(p, F_SPIKES) && source.getEntity() instanceof LivingEntity att && source.getDirectEntity() == att && att != p
				&& att.distanceToSqr(p) < 36) {
			hurt(p, att, THORNS, false);
		}
		// Brace: frontal hits take 40%
		if (flag(p, F_BRACE) && source.getSourcePosition() != null && !source.is(DamageTypeTags.BYPASSES_ARMOR) && inFront(p, source.getSourcePosition())) {
			ServerLevel l = level(p);
			OmnitrixSfx.play(l, p.position(), SND_THUD, 1f, 1f);
			QuirkCamera.shake(p, 0.12f, 4);
			return amount * BRACE_TAKEN;
		}
		return amount;
	}

	/** Within about 75 degrees either side of where he faces. */
	public static boolean inFront(ServerPlayer p, Vec3 from) {
		Vec3 to = from.subtract(p.position());
		Vec3 h = new Vec3(to.x, 0, to.z);
		if (h.lengthSqr() < 1.0e-4) {
			return true;
		}
		return h.normalize().dot(Vec3.directionFromRotation(0, p.getYRot())) > 0.25;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return; // once a game tick: the stomps, the stampede and the holds count ticks
		}
		ServerLevel l = level(p);
		tickCharge(p, l, now);
		tickLeap(p, now);
		tickQuake(p, l, now);
		tickSpikes(p, now);
		tickBrace(p, l, now);
		tickSizeHolds(p, now);
		tickThrown(p, l, now);
		tickFleeing(p, l, now);
		tickHunger(p);
		tickFootsteps(p, l, now);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					uppercut(p);
				} else {
					punch(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					tailClub(p);
				} else {
					pound(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					leap(p);
				} else if (charge(p)) {
					setFlag(p, F_XHELD, true);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					earthshaker(p);
				} else {
					hurl(p);
				}
			}
			case SLOT_5 -> {
				// v0.17.2: hold V / Shift+V a second to go straight to full / base size
				setTimer(p, V_DOWN, p.level().getGameTime());
				setFlag(p, shift ? F_SVHELD : F_VHELD, true);
				if (shift) {
					shrink(p);
				} else {
					grow(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					spikeBurst(p);
				} else {
					roar(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3) {
			setFlag(p, F_XHELD, false);
		} else if (slot == AbilitySlot.SLOT_5) {
			setFlag(p, F_VHELD, false);
			setFlag(p, F_SVHELD, false);
			setTimer(p, V_DOWN, 0);
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static double reach(ServerPlayer p) {
		return 2.5 + p.getScale() * 1.2;
	}

	/** Flat forward (yaw only). */
	private static Vec3 forward(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		return h.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : h.normalize();
	}

	/** Terrain damage on, and he may change the world here. */
	private static boolean canSmash(ServerPlayer p, ServerLevel l, BlockPos pos) {
		return HeroConfig.get().abilityTerrainDamage && p.mayBuild() && l.mayInteract(p, pos);
	}

	// ---------------------------------------------------------------- R / Shift+R

	public boolean punch(ServerPlayer p) {
		if (!use(p, PUNCH)) {
			return false;
		}
		anim(p, ANIM_PUNCH);
		LivingEntity t = lookTarget(p, reach(p));
		ServerLevel l = level(p);
		if (t != null && hurt(p, t, PUNCH_DAMAGE * mult(p), false)) {
			shove(t, p.position(), 1.3, 0.35);
			if (FourArmsKit.smallMob(t)) {
				AlienStatus.stun(p, t, 10); // a small mob is knocked flat
			}
			QuirkFx.ring(l, t.position().add(0, t.getBbHeight() * 0.6, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, DUST)
					.size(1.4f).thickness(0.35f).life(7).spawn();
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		}
		OmnitrixSfx.play(l, p.position(), SND_PUNCH, 1f, 1f);
		return true;
	}

	public boolean uppercut(ServerPlayer p) {
		if (!use(p, UPPER)) {
			return false;
		}
		anim(p, ANIM_UPPER);
		LivingEntity t = lookTarget(p, reach(p));
		if (t != null && hurt(p, t, UPPER_DAMAGE * mult(p), false)) {
			double res = 1.0 - Math.min(1.0, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			t.setDeltaMovement(t.getDeltaMovement().add(0, 1.15 * Math.max(0.3, res), 0));
			t.hurtMarked = true;
			QuirkFx.ring(level(p), t.position().add(0, t.getBbHeight() * 0.3, 0)).colours(0xFFFFFF, DUST).size(1.6f).thickness(0.35f).life(8).spawn();
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		}
		OmnitrixSfx.play(level(p), p.position(), SND_PUNCH, 1.2f, 0.9f);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** A shockwave of {@code radius} at {@code c}: {@code dmg}, thrown up; a rendered ring and a crack in the ground. */
	private void quake(ServerPlayer p, Vec3 c, double radius, float dmg, double out, double up, float shake, boolean cracks) {
		ServerLevel l = level(p);
		for (LivingEntity e : around(p, c, radius)) {
			if (hurt(p, e, dmg, false)) {
				shove(e, c, out, up);
			}
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, DUST).size((float) radius).thickness(0.6f + (float) radius * 0.08f)
				.life(14).ground().spawn();
		BlockState under = l.getBlockState(BlockPos.containing(c).below());
		if (cracks) {
			QuirkFx.decal(l, c.add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size((float) radius * 0.7f).yaw(p.getYRot()).life(200).spawn();
		}
		if (!under.isAir()) {
			QuirkFx.shards(l, c.add(0, 0.2, 0)).block(under).size((float) radius * 0.4f).count((int) (radius * 3)).spawn();
		}
		QuirkCamera.shakeAround(l, c, radius * 3, shake, 8);
		OmnitrixSfx.play(l, c, SND_QUAKE, 1.2f, 1f);
	}

	public boolean pound(ServerPlayer p) {
		if (!use(p, POUND)) {
			return false;
		}
		anim(p, ANIM_POUND);
		quake(p, p.position(), POUND_RADIUS + POUND_STEP * stage(p), POUND_DAMAGE * mult(p), 0.7, 0.6, 0.35f, true);
		return true;
	}

	/** Shift+G: a full spin of the tail; at full size the club on its end hits harder and knocks them down. */
	public boolean tailClub(ServerPlayer p) {
		if (!use(p, TAIL)) {
			return false;
		}
		anim(p, ANIM_TAIL);
		boolean club = stage(p) >= MAX_STAGE;
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 0.5, 0);
		double r = TAIL_RADIUS + stage(p);
		for (LivingEntity e : around(p, c, r)) {
			if (hurt(p, e, (club ? CLUB_DAMAGE : TAIL_DAMAGE) * mult(p), false)) {
				shove(e, c, 1.5, 0.3);
				if (club) {
					AlienStatus.stun(p, e, CLUB_DOWN_TICKS);
				}
			}
		}
		QuirkFx.ring(l, p.position().add(0, p.getBbHeight() * 0.25, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xE8DCC0).size((float) r)
				.thickness(0.5f).life(10).spawn();
		if (club) {
			QuirkCamera.shakeAround(l, p.position(), r * 2, 0.25f, 6);
		}
		OmnitrixSfx.play(l, p.position(), SND_SWOOSH, 1.2f, club ? 0.8f : 1f);
		return true;
	}

	// ---------------------------------------------------------------- X: Stampede / Shift+X: Dino Leap

	public boolean charge(ServerPlayer p) {
		if (!use(p, CHARGE)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		RUNS.put(p.getUUID(), new Run());
		setTimer(p, CHARGE_START, p.level().getGameTime());
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_ROAR, 0.6f, 1.2f);
		return true;
	}

	/** Is the Stampede still running {@code t} ticks in: at least 1 s, then while X is held, up to 2.5 s. */
	public boolean charging(ServerPlayer p, long t) {
		return t < CHARGE_TICKS || flag(p, F_XHELD) && t < CHARGE_MAX_TICKS;
	}

	private void tickCharge(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_CHARGE)) {
			return;
		}
		long t = now - timer(p, CHARGE_START);
		if (!charging(p, t)) {
			endCharge(p);
			return;
		}
		Vec3 h = forward(p);
		p.setDeltaMovement(h.x * 0.75, Math.min(p.getDeltaMovement().y, 0.0), h.z * 0.75);
		p.hurtMarked = true;
		Run run = RUNS.computeIfAbsent(p.getUUID(), k -> new Run());
		smashAhead(p, l, h);
		if (t > 2 && wallAhead(p, l, h)) {
			crash(p, h);
			return;
		}
		Vec3 front = p.position().add(h.scale(0.6 * p.getScale())).add(0, p.getBbHeight() * 0.4, 0);
		for (LivingEntity e : around(p, front, 1.0 + p.getBbWidth() * 0.6)) {
			if (e.getId() == run.carried || !run.hits.add(e.getId())) {
				continue;
			}
			if (hurt(p, e, CHARGE_DAMAGE * mult(p), false)) {
				if (run.carried < 0 && !AlienStatus.isBoss(e)) {
					run.carried = e.getId(); // the first one rides his head down the field
				} else {
					shove(e, p.position(), 1.6, 0.5);
				}
			}
		}
		if (run.carried >= 0 && l.getEntity(run.carried) instanceof LivingEntity c && c.isAlive()) {
			Vec3 at = p.position().add(h.scale(p.getBbWidth() * 0.5 + c.getBbWidth() * 0.5 + 0.2));
			c.setPos(at.x, Math.max(c.getY(), p.getY()), at.z);
			c.setDeltaMovement(p.getDeltaMovement());
			c.hurtMarked = true;
		}
		if (t % 4 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_STOMP, 0.8f, 1f);
		}
	}

	/** A solid block (one he can't burst) at chest height just ahead of him. */
	private static boolean wallAhead(ServerPlayer p, ServerLevel l, Vec3 dir) {
		double ahead = p.getBbWidth() * 0.5 + 0.45;
		for (double up : new double[] { 0.6, Math.min(p.getBbHeight() - 0.3, 1.6) }) {
			BlockPos pos = BlockPos.containing(p.position().add(dir.scale(ahead)).add(0, up, 0));
			BlockState st = l.getBlockState(pos);
			if (!st.getCollisionShape(l, pos).isEmpty() && !(smashable(l, pos, st) && canSmash(p, l, pos))) {
				return true;
			}
		}
		return false;
	}

	/** Glass, leaves, fences, light wood (Four Arms' list) and wooden planks. */
	public static boolean smashable(ServerLevel l, BlockPos pos, BlockState st) {
		return FourArmsKit.weakBlock(l, pos, st) || st.is(BlockTags.PLANKS) && !st.hasBlockEntity();
	}

	private void smashAhead(ServerPlayer p, ServerLevel l, Vec3 dir) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int half = (int) Math.ceil(p.getBbWidth() * 0.5);
		int tall = (int) Math.ceil(p.getBbHeight());
		for (int s = -half; s <= half; s++) {
			for (int up = 0; up < tall; up++) {
				BlockPos pos = BlockPos.containing(p.position().add(dir.scale(p.getBbWidth() * 0.5 + 0.6)).add(side.scale(s * 0.9)).add(0, up + 0.2, 0));
				BlockState st = l.getBlockState(pos);
				if (!st.isAir() && smashable(l, pos, st) && canSmash(p, l, pos)) {
					l.destroyBlock(pos, true, p);
				}
			}
		}
	}

	/** The Stampede hit a wall: what he carries is pinned (+6, stunned 1 s), a camera kick, a ring of chips. */
	public void crash(ServerPlayer p, Vec3 dir) {
		ServerLevel l = level(p);
		Run run = RUNS.get(p.getUUID());
		if (run != null && run.carried >= 0 && l.getEntity(run.carried) instanceof LivingEntity c && c.isAlive()) {
			c.invulnerableTime = 0;
			hurt(p, c, CRASH_DAMAGE * mult(p), false);
			AlienStatus.stun(p, c, CRASH_STUN_TICKS);
		}
		anim(p, ANIM_CRASH);
		Vec3 at = p.position().add(dir.scale(p.getBbWidth() * 0.5 + 0.4)).add(0, p.getBbHeight() * 0.45, 0);
		BlockState wall = l.getBlockState(BlockPos.containing(at.add(dir.scale(0.4))));
		QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, DUST).size(2.2f).thickness(0.5f).life(10).spawn();
		if (!wall.isAir()) {
			QuirkFx.shards(l, at).block(wall).size(1.2f).count(10).spawn();
		}
		QuirkCamera.shake(p, 0.45f, 8);
		OmnitrixSfx.play(l, at, SND_CRASH, 1f, 1f);
		p.setDeltaMovement(dir.scale(-0.3).add(0, 0.1, 0));
		p.hurtMarked = true;
		endCharge(p);
	}

	private void endCharge(ServerPlayer p) {
		ServerLevel l = level(p);
		Run run = RUNS.remove(p.getUUID());
		if (run != null && run.carried >= 0 && l.getEntity(run.carried) instanceof LivingEntity c && c.isAlive()) {
			shove(c, p.position(), 1.2, 0.4); // dropped off the front
		}
		setFlag(p, F_CHARGE, false);
		setFlag(p, F_XHELD, false);
		setTimer(p, CHARGE_START, 0);
	}

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 h = forward(p);
		p.setDeltaMovement(h.x * 1.1, 1.05, h.z * 1.1);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, LEAP_START, p.level().getGameTime());
		setFlag(p, F_LEAP, true);
		OmnitrixSfx.play(level(p), p.position(), SND_STOMP, 1.2f, 0.8f);
		return true;
	}

	private void tickLeap(ServerPlayer p, long now) {
		long t = now - timer(p, LEAP_START);
		if (flag(p, F_LEAP) && t > 4 && (p.onGround() || t > AIR_MAX)) {
			setFlag(p, F_LEAP, false);
			setTimer(p, LEAP_START, 0);
			land(p);
		}
	}

	/** The Dino Leap's landing: a quake; at full size the TITANIC SLAM (user: "kept as the landing of a Dino Leap at full size"). */
	public void land(ServerPlayer p) {
		p.fallDistance = 0f;
		if (stage(p) >= MAX_STAGE) {
			anim(p, ANIM_TITAN_LAND);
			ServerLevel l = level(p);
			quake(p, p.position(), TITAN_LAND_RADIUS, TITAN_LAND_DAMAGE * mult(p), 1.1, 0.8, 0.6f, true);
			QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, DUST).size((float) TITAN_LAND_RADIUS * 0.6f).life(14)
					.ground().spawn();
			OmnitrixSfx.play(l, p.position(), SND_QUAKE, 1.6f, 0.8f);
		} else {
			anim(p, ANIM_LAND);
			quake(p, p.position(), LAND_RADIUS + stage(p), LAND_DAMAGE * mult(p), 0.7, 0.4, 0.3f, false);
		}
	}

	// ---------------------------------------------------------------- Z: Heavy Hurl / Shift+Z: Earthshaker

	/** Whether Heavy Hurl lifts {@code e} at {@code stage}: a player, an iron golem, a ravager at the three sizes; never bosses. */
	public static boolean canLift(int stage, LivingEntity e) {
		int s = Math.max(0, Math.min(MAX_STAGE, stage));
		return !AlienStatus.isBoss(e) && e.getBbWidth() <= LIFT_WIDTH[s] + 1.0e-3 && e.getBbHeight() <= LIFT_HEIGHT[s] + 1.0e-3;
	}

	/**
	 * Z: one press -- the creature in front if he can lift it (thrown where he looks; it bowls over whatever it meets), otherwise a
	 * torn-up slab of the ground.
	 */
	public boolean hurl(ServerPlayer p) {
		LivingEntity t = lookTarget(p, reach(p) + 0.5);
		if (t != null && !canLift(stage(p), t)) {
			say(p, "message.projecthero.omnitrix.humungousaur.too_big");
			t = null;
		}
		if (!use(p, HURL)) {
			return false;
		}
		anim(p, ANIM_HURL);
		ServerLevel l = level(p);
		float m = mult(p);
		Vec3 look = p.getLookAngle();
		if (t != null) {
			t.setDeltaMovement(look.x * 1.9, Math.max(0.45, look.y * 1.4 + 0.45), look.z * 1.9);
			t.hurtMarked = true;
			t.hasImpulse = true;
			THROWN.put(t.getId(), new Thrown(p.getUUID(), p.level().getGameTime(), m));
			OmnitrixSfx.play(l, p.position(), SND_SWOOSH, 1.2f, 0.8f);
		} else {
			BlockState under = l.getBlockState(p.blockPosition().below());
			if (under.isAir() || !under.getFluidState().isEmpty() || under.hasBlockEntity()) {
				under = Blocks.DIRT.defaultBlockState();
			}
			AlienProjectileEntity.of(p, "humungousaur_slab").colour(Block.getId(under)).size(0.7f + 0.2f * stage(p)).damage(HURL_DAMAGE * m)
					.blast((float) HURL_SPLASH_RADIUS, HURL_SPLASH * m).gravity(0.035).speed(1.35).life(80).spawn();
			Vec3 torn = p.position().add(forward(p).scale(p.getBbWidth() * 0.5 + 0.8));
			QuirkFx.decal(l, torn.add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(1.8f).yaw(p.getYRot()).life(200).spawn();
			QuirkFx.shards(l, torn.add(0, 0.2, 0)).block(under).size(1.2f).count(10).spawn();
			OmnitrixSfx.play(l, p.position(), SND_TEAR, 1f, 1f);
		}
		return true;
	}

	/** Is {@code e} in flight from a Heavy Hurl right now? */
	public static boolean thrown(LivingEntity e) {
		return THROWN.containsKey(e.getId());
	}

	private void tickThrown(ServerPlayer p, ServerLevel l, long now) {
		if (THROWN.isEmpty()) {
			return;
		}
		THROWN.entrySet().removeIf(en -> {
			Thrown th = en.getValue();
			if (!th.thrower().equals(p.getUUID())) {
				return false;
			}
			if (!(l.getEntity(en.getKey()) instanceof LivingEntity e) || !e.isAlive() || now - th.at() > HURL_FLIGHT_TICKS) {
				return true;
			}
			long age = now - th.at();
			LivingEntity met = null;
			for (LivingEntity o : around(p, e.position().add(0, e.getBbHeight() * 0.5, 0), e.getBbWidth() * 0.5 + 0.9)) {
				if (o != e) {
					met = o;
					break;
				}
			}
			if (age > 2 && (met != null || e.onGround() || e.horizontalCollision)) {
				thrownImpact(p, e, met, th.mult());
				return true;
			}
			return false;
		});
	}

	/** A hurled creature lands / hits something: 18 to it and to what it met, 8 to everything else within 2.5. */
	public void thrownImpact(ServerPlayer p, LivingEntity thrown, LivingEntity met, float m) {
		ServerLevel l = level(p);
		THROWN.remove(thrown.getId());
		Vec3 at = thrown.position();
		thrown.invulnerableTime = 0;
		hurt(p, thrown, HURL_DAMAGE * m, false);
		if (met != null) {
			met.invulnerableTime = 0;
			if (hurt(p, met, HURL_DAMAGE * m, false)) {
				shove(met, at, 1.2, 0.4);
			}
		}
		for (LivingEntity e : around(p, at, HURL_SPLASH_RADIUS)) {
			if (e != thrown && e != met && hurt(p, e, HURL_SPLASH * m, false)) {
				shove(e, at, 0.6, 0.35);
			}
		}
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, DUST).size((float) HURL_SPLASH_RADIUS + 0.5f).thickness(0.6f).life(12).ground().spawn();
		QuirkCamera.shakeAround(l, at, 8, 0.25f, 6);
		OmnitrixSfx.play(l, at, SND_QUAKE, 0.9f, 1.2f);
	}

	/** Shift+Z, the ULTIMATE: he rears up and stomps three times, rooted; the stomps run in {@link #tickQuake}. */
	public boolean earthshaker(ServerPlayer p) {
		if (!use(p, QUAKE)) {
			return false;
		}
		anim(p, ANIM_QUAKE);
		setTimer(p, QUAKE_START, p.level().getGameTime());
		setFlag(p, F_QUAKE, true);
		root(p);
		OmnitrixSfx.play(level(p), p.position(), SND_ROAR, 1.3f, 0.8f);
		return true;
	}

	/** The Earthshaker's stomp {@code i} (0, 1, 2) at his feet (tests call it straight). */
	public void stomp(ServerPlayer p, int i) {
		boolean last = i >= QUAKE_STOMPS.length - 1;
		double r = (last ? QUAKE_BIG_RADIUS : QUAKE_SMALL_RADIUS) + stage(p);
		float dmg = (last ? QUAKE_BIG : QUAKE_SMALL) * mult(p);
		quake(p, p.position(), r, dmg, last ? 1.0 : 0.4, last ? 1.2 : 0.8, last ? 0.8f : 0.45f, true);
		if (last) {
			ServerLevel l = level(p);
			QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, 0xC8B898).size((float) r * 0.55f).thickness(0.8f)
					.life(18).ground().spawn();
			QuirkFx.ring(l, p.position().add(0, 1.0, 0)).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, DUST)
					.size(3.5f).life(12).spawn();
			OmnitrixSfx.play(l, p.position(), SND_QUAKE, 1.8f, 0.7f);
		}
	}

	private void tickQuake(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_QUAKE)) {
			return;
		}
		long t = now - timer(p, QUAKE_START);
		for (int i = 0; i < QUAKE_STOMPS.length; i++) {
			if (t == QUAKE_STOMPS[i]) {
				stomp(p, i);
			}
		}
		p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
		p.hurtMarked = true;
		if (t >= QUAKE_TICKS || t < 0) {
			setFlag(p, F_QUAKE, false);
			setTimer(p, QUAKE_START, 0);
			unroot(p);
		}
	}

	private static void root(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, M_ROOT_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, M_ROOT_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void unroot(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, M_ROOT_SPEED);
		PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, M_ROOT_JUMP);
	}

	// ---------------------------------------------------------------- V / Shift+V: size

	public boolean grow(ServerPlayer p) {
		int s = stage(p);
		if (s >= MAX_STAGE) {
			say(p, "message.projecthero.omnitrix.humungousaur.full_size");
			return false;
		}
		if (!roomFor(p, s + 1)) {
			say(p, "message.projecthero.omnitrix.humungousaur.no_room");
			return false;
		}
		if (!use(p, GROW)) {
			return false;
		}
		growTo(p, s + 1);
		return true;
	}

	/** Hold V: straight to full size (as far as there is room). */
	public boolean growFull(ServerPlayer p) {
		int s = stage(p);
		int to = s;
		while (to < MAX_STAGE && roomFor(p, to + 1)) {
			to++;
		}
		if (to == s) {
			say(p, s >= MAX_STAGE ? "message.projecthero.omnitrix.humungousaur.full_size" : "message.projecthero.omnitrix.humungousaur.no_room");
			return false;
		}
		cooldown(p, GROW, -1);
		growTo(p, to);
		return true;
	}

	private void growTo(ServerPlayer p, int to) {
		int from = stage(p);
		anim(p, ANIM_GROW);
		setStage(p, to);
		ServerLevel l = level(p);
		// each grow pushes nearby creatures aside (no damage)
		double r = scaleAt(to) * 0.6 + 1.5;
		for (LivingEntity e : around(p, p.position(), r)) {
			shove(e, p.position(), 0.9, 0.2);
		}
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, DUST).size((float) r).life(12).ground().spawn();
		OmnitrixSfx.play(l, p.position(), SND_GROW, 1f + 0.2f * from, 1f);
	}

	public boolean shrink(ServerPlayer p) {
		int s = stage(p);
		if (s <= 0) {
			say(p, "message.projecthero.omnitrix.humungousaur.base_size");
			return false;
		}
		if (!use(p, SHRINK)) {
			return false;
		}
		shrinkTo(p, s - 1);
		return true;
	}

	/** Hold Shift+V: straight back to base size. */
	public boolean shrinkBase(ServerPlayer p) {
		if (stage(p) <= 0) {
			return false;
		}
		cooldown(p, SHRINK, -1);
		shrinkTo(p, 0);
		return true;
	}

	private void shrinkTo(ServerPlayer p, int to) {
		anim(p, ANIM_SHRINK);
		setStage(p, to);
		OmnitrixSfx.play(level(p), p.position(), SND_SHRINK, 1f, 1f);
	}

	private void tickSizeHolds(ServerPlayer p, long now) {
		long down = timer(p, V_DOWN);
		if (down <= 0 || now - down != HOLD_TICKS) {
			return;
		}
		if (flag(p, F_VHELD)) {
			growFull(p);
		} else if (flag(p, F_SVHELD)) {
			shrinkBase(p);
		}
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** Creatures in a cone of {@code deg} degrees, {@code range} long, from {@code from} along {@code dir}. */
	public static List<LivingEntity> cone(ServerPlayer p, Vec3 from, Vec3 dir, double range, double deg) {
		double cos = Math.cos(Math.toRadians(deg / 2.0));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(p, from, range + 1.0)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(dir) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	public boolean roar(ServerPlayer p) {
		if (!use(p, ROAR)) {
			return false;
		}
		anim(p, ANIM_ROAR);
		long now = p.level().getGameTime();
		for (LivingEntity e : cone(p, p.getEyePosition(), p.getLookAngle(), ROAR_RANGE + stage(p) * 2, ROAR_DEG)) {
			hurt(p, e, ROAR_DAMAGE * mult(p), false);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			if (e.isAlive() && e instanceof PathfinderMob mob && !AlienStatus.isBoss(e) && e.getHealth() < ROAR_FLEE_HEALTH) {
				FLEEING.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).merge(mob.getId(), now + ROAR_FLEE_TICKS, Math::max);
				flee(mob, p.position());
			}
		}
		QuirkCamera.shakeAround(level(p), p.position(), 10, 0.25f, 6);
		OmnitrixSfx.play(level(p), p.position(), SND_ROAR, 1.4f, 1f);
		return true;
	}

	private static void flee(PathfinderMob mob, Vec3 from) {
		mob.setTarget(null);
		Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from);
		if (away != null) {
			mob.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
		}
	}

	/** Is {@code mob} running from {@code p}'s roar right now? */
	public static boolean fleeing(ServerPlayer p, LivingEntity mob) {
		Map<Integer, Long> mine = FLEEING.get(p.getUUID());
		Long until = mine == null ? null : mine.get(mob.getId());
		return until != null && until > p.level().getGameTime();
	}

	private void tickFleeing(ServerPlayer p, ServerLevel l, long now) {
		Map<Integer, Long> mine = FLEEING.get(p.getUUID());
		if (mine == null || mine.isEmpty() || now % 10 != 0) {
			return;
		}
		mine.entrySet().removeIf(en -> {
			if (en.getValue() <= now || !(l.getEntity(en.getKey()) instanceof PathfinderMob mob) || !mob.isAlive()) {
				return true;
			}
			if (mob.getNavigation().isDone() || mob.getTarget() != null) {
				flee(mob, p.position());
			}
			return false;
		});
	}

	/** Shift+C: bone spikes jut out of his back, shoulders and tail -- a hit around him, then 8 s of thorns and +3 armour. */
	public boolean spikeBurst(ServerPlayer p) {
		if (!use(p, SPIKES)) {
			return false;
		}
		anim(p, ANIM_SPIKES);
		long now = p.level().getGameTime();
		ServerLevel l = level(p);
		double r = SPIKE_RADIUS + stage(p);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.4, 0);
		for (LivingEntity e : around(p, c, r)) {
			if (hurt(p, e, SPIKE_DAMAGE * mult(p), false)) {
				shove(e, c, 0.9, 0.3);
			}
		}
		setTimer(p, SPIKES_START, now);
		setTimer(p, SPIKES_END, now + SPIKE_TICKS);
		setFlag(p, F_SPIKES, true);
		PowerToggles.modifier(p, Attributes.ARMOR, M_SPIKE_ARMOR, 3.0, AttributeModifier.Operation.ADD_VALUE);
		QuirkFx.ring(l, p.position().add(0, p.getBbHeight() * 0.45, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, 0xE8DCC0).size((float) r).thickness(0.5f)
				.life(10).spawn();
		OmnitrixSfx.play(l, p.position(), SND_SPIKES, 1f, 1f);
		return true;
	}

	private void tickSpikes(ServerPlayer p, long now) {
		if (flag(p, F_SPIKES) && now >= timer(p, SPIKES_END)) {
			setFlag(p, F_SPIKES, false);
			PowerToggles.clearModifier(p, Attributes.ARMOR, M_SPIKE_ARMOR);
		}
	}

	// ---------------------------------------------------------------- the vanilla keys: Heavy Fists, Brace

	/** Heavy Fists: from x2.2 on his punch cleaves -- a second creature in front of him takes the same blow. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (stage(p) < 1 || !(target instanceof LivingEntity primary)) {
			return false;
		}
		anim(p, ANIM_CLEAVE);
		float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		Vec3 eye = p.getEyePosition();
		for (LivingEntity e : cone(p, eye, p.getLookAngle(), p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 90)) {
			if (e != primary) {
				if (hurt(p, e, dmg, false)) {
					shove(e, p.position(), 0.6, 0.2);
				}
				break; // up to two creatures in all
			}
		}
		return false;
	}

	/** A right-click (empty hand): the Brace comes up, or stays up while the uses keep coming (vanilla repeats them while held). */
	public InteractionResult braceUse(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (!flag(p, F_BRACE)) {
			if (!ready(p, BRACE) || flag(p, F_QUAKE)) {
				return InteractionResult.PASS;
			}
			setTimer(p, BRACE_START, now);
			setFlag(p, F_BRACE, true);
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, M_BRACE_SPEED, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, M_BRACE_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			OmnitrixSfx.play(level(p), p.position(), SND_THUD, 0.8f, 0.8f);
		}
		setTimer(p, BRACE_USE, now);
		return InteractionResult.SUCCESS;
	}

	/** Is he braced right now? (client-safe) */
	public boolean braced(Player p) {
		return flag(p, F_BRACE);
	}

	private void unbrace(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, M_BRACE_SPEED);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, M_BRACE_KB);
	}

	/** Lowers the Brace and starts its 3 s cooldown. */
	public void lowerBrace(ServerPlayer p) {
		setFlag(p, F_BRACE, false);
		setTimer(p, BRACE_START, 0);
		setTimer(p, BRACE_USE, 0);
		unbrace(p);
		cooldown(p, BRACE, -1);
	}

	private void tickBrace(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_BRACE)) {
			return;
		}
		long use = timer(p, BRACE_USE);
		if (now - use > BRACE_GRACE || now < use || now - timer(p, BRACE_START) >= BRACE_MAX_TICKS) {
			lowerBrace(p);
			return;
		}
		// frontal projectiles are stopped dead against his forearms
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.5), pr -> pr.getOwner() != p)) {
			Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(pr.position());
			if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0 || !inFront(p, pr.position())) {
				continue;
			}
			QuirkFx.ring(l, pr.position()).flags(QuirkFx.F_VERTICAL).vec(pr.getDeltaMovement().normalize()).colours(0xFFFFFF, DUST).size(0.9f).thickness(0.25f)
					.life(6).spawn();
			OmnitrixSfx.play(l, pr.position(), SND_THUD, 0.8f, 1.2f);
			pr.discard();
		}
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND && p.getMainHandItem().isEmpty() ? braceUse(p) : InteractionResult.PASS;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		return hand == InteractionHand.MAIN_HAND && p.getMainHandItem().isEmpty() ? braceUse(p) : InteractionResult.PASS;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockState st = p.level().getBlockState(hit.getBlockPos());
		if (st.hasBlockEntity() || st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.FENCE_GATES) || st.is(BlockTags.BUTTONS)
				|| st.is(Blocks.LEVER) || st.is(BlockTags.BEDS)) {
			return InteractionResult.PASS; // doors, chests and switches still work as with an empty hand
		}
		return braceUse(p);
	}

	// ---------------------------------------------------------------- passives / weaknesses

	/** Hungry Giant: whatever hunger he burns is x1.5 at x2.2 and x2 at x3.0. */
	private void tickHunger(ServerPlayer p) {
		float now = p.getFoodData().getExhaustionLevel();
		Float last = LAST_EXHAUSTION.put(p.getUUID(), now);
		int s = stage(p);
		if (last == null || s == 0 || p.getAbilities().invulnerable) {
			return;
		}
		float used = now >= last ? now - last : now + 4f - last; // past 4 the bar wraps (a point of saturation / food spent)
		if (used > 0f && used < 4f) {
			float extra = used * (s == 1 ? 0.5f : 1.0f);
			p.causeFoodExhaustion(extra);
			LAST_EXHAUSTION.put(p.getUUID(), p.getFoodData().getExhaustionLevel());
		}
	}

	/** Giant's Footsteps: at full size, sprinting gives nearby cameras a tiny shake and tramples crops and grass. */
	private void tickFootsteps(ServerPlayer p, ServerLevel l, long now) {
		if (stage(p) < MAX_STAGE || !p.isSprinting() || !p.onGround() || flag(p, F_CHARGE)) {
			return;
		}
		if (now % 8 == 0) {
			QuirkCamera.shakeAround(l, p.position(), 12, 0.08f, 4);
			OmnitrixSfx.play(l, p.position(), SND_STOMP, 0.6f, 0.9f);
		}
		int half = (int) Math.ceil(p.getBbWidth() * 0.5);
		BlockPos feet = p.blockPosition();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				BlockPos pos = feet.offset(dx, 0, dz);
				BlockState st = l.getBlockState(pos);
				if ((st.is(BlockTags.CROPS) || st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN) || st.is(Blocks.LARGE_FERN))
						&& canSmash(p, l, pos)) {
					l.destroyBlock(pos, true, p);
				}
			}
		}
	}

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		UUID id = p.getUUID();
		return RUNS.containsKey(id) || FLEEING.containsKey(id) || LAST_EXHAUSTION.containsKey(id) || LAST_TICK.containsKey(id)
				|| THROWN.values().stream().anyMatch(t -> t.thrower().equals(id));
	}
}
