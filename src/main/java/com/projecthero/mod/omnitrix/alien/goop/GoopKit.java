package com.projecthero.mod.omnitrix.alien.goop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.BatchBScheduler;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Goop, the Polymorph (Recalibrated Omnitrix) -- a shapeshifting body of acidic green slime held together by the
 * anti-gravity projector. v0.17.2 revamp (docs/OMNITRIX_REVAMP_GOOP.md; user: "can you work on revamps for the rest of the alien
 * force aliens please, look at their powers and abilities in the show and do research and make them good please"): what he does
 * in Alien Force -- stretches, engulfs, sprays and dissolves with acid, squeezes through gaps, flows after his own projector, and
 * goes to a helpless puddle when it is knocked away. Upgrade is the other liquid alien (he merges with machines); Goop is acid and
 * shape. No Ultimate form (user: "dont add ultimate aliens yet"); the body is only the user's skin (user: "delete all the alien
 * models and only use the skins i provided please no extra things untill i say").
 *
 * v0.17.2 damage pass (user: "make the damage of all of ben 10s alien stronger"; docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md): the
 * damage numbers in the table below are the pre-pass ones (~x1.6 now) -- the constants are the source of truth.
 *
 * <pre>
 *   R          Acid Spit        a glob of acid: 7 (1 s)
 *   Shift+R    Acid Glob        a lobbed glob: 12 within 2.5 where it lands + an acid puddle 8 s (2 every 0.5 s, slowed)
 *   G          Slime Whip       his arm stretches 8 blocks: 13 to what is in front, small creatures dragged 3 blocks in
 *   Shift+G    Engulf           swallows a creature (4.5): held inside 3 s, 4 every 0.75 s (16), then spat out; Shift+G again
 *                               spits it early: thrown 6 blocks, +6
 *   X          Slime Bounce     a springy leap; he rebounds off the ground twice more on his own (each lower); sneak stops it
 *   Shift+X    Squeeze (HOLD)   while held (6 s at most): 0.35x, through one-block gaps, +20% speed, climbs walls; 3 s cooldown
 *                               from the release
 *   Z          Acid Spray       1.5 s of acid out of both hands, steered by the look: 3.5 every 0.5 s (14) in a 6-block cone,
 *                               weakened 5 s
 *   Shift+Z    Acid Tide        ULTIMATE: he becomes a wave and surges 10 blocks forward (untouchable): 45 to everything it
 *                               rolls over, carried along; then an acid lake round him (6 blocks, 6 s: 5 every 0.5 s, slowed)
 *   C          Slime Slam       he splashes down round himself: 17 within 4, pushed back, slowed 3 s
 *   Shift+C    Goo Cannon       a huge glob: up to 18 within 3 where it bursts
 *   V          Reform           pulls himself back together: +8 health, every harmful effect gone
 *   Shift+V    Projector Fling  throws his projector up to 16 blocks; his body pours after it as a puddle (untouchable) and
 *                               reforms where it lands
 *   Attack     Corrosive Touch  +2 acid on every punch
 *   Use        Dissolve         (empty hand, on a block) acid eats the block for 10 s (nothing harder than iron bars, no block
 *                               entities; the abilityTerrainDamage rule and build rights apply)
 *   Sneak      Slime Chute      (in the air) flattens out: a slow fall while held
 *   Passives   Polymorph body: physical blows half, no fall damage, never suffocates; Acid Skin: whatever strikes him in melee
 *              takes 2
 *   Weak       the projector: a hit of 10+ (or any lightning) knocks it off -- a puddle (0.45x, -70% speed, no moves) until he
 *              flows back over it (it floats back by itself after 10 s); heat (fire x1.5); squeezed thin, blows land in full
 * </pre>
 *
 * <p>Carriers ({@link AlienProjectileEntity}, held): {@code goop_puddle} (radius = size) and {@code goop_projector} (the knocked-off
 * / thrown projector on the ground).
 */
public final class GoopKit extends AlienKit {
	public static final String ID = Aliens.GOOP.id();

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SPIT = OmnitrixSfx.cue("goop_spit")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.4f, 1.3f)
			.layer(SoundEvents.LLAMA_SPIT, 0.3f, 1.2f)
			.gap(2);
	public static final OmnitrixSfx.Cue SPLAT = OmnitrixSfx.cue("goop_splat")
			.layer(SoundEvents.SLIME_BLOCK_FALL, 0.45f, 0.9f)
			.layer(SoundEvents.BUCKET_EMPTY, 0.25f, 1.4f)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.12f, 1.6f, 2)
			.gap(3);
	public static final OmnitrixSfx.Cue SIZZLE = OmnitrixSfx.cue("goop_sizzle")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.12f, 1.7f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.15f, 0.8f)
			.gap(10);
	public static final OmnitrixSfx.Cue WHIP = OmnitrixSfx.cue("goop_whip")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.3f, 0.8f)
			.layer(SoundEvents.SLIME_SQUISH, 0.4f, 1.0f);
	public static final OmnitrixSfx.Cue GULP = OmnitrixSfx.cue("goop_gulp")
			.layer(SoundEvents.SLIME_BLOCK_PLACE, 0.5f, 0.7f)
			.layer(SoundEvents.HONEY_DRINK, 0.4f, 0.6f, 2);
	public static final OmnitrixSfx.Cue BOING = OmnitrixSfx.cue("goop_boing")
			.layer(SoundEvents.SLIME_JUMP, 0.45f, 1.0f)
			.layer(SoundEvents.SLIME_BLOCK_STEP, 0.3f, 1.2f);
	public static final OmnitrixSfx.Cue SQUISH = OmnitrixSfx.cue("goop_squish")
			.layer(SoundEvents.HONEY_BLOCK_SLIDE, 0.45f, 1.1f)
			.layer(SoundEvents.SLIME_SQUISH, 0.3f, 1.4f);
	public static final OmnitrixSfx.Cue REFORM = OmnitrixSfx.cue("goop_reform")
			.layer(SoundEvents.SLIME_BLOCK_PLACE, 0.4f, 1.2f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.15f, 1.8f, 3);
	public static final OmnitrixSfx.Cue KNOCKED = OmnitrixSfx.cue("goop_knocked")
			.layer(SoundEvents.ANVIL_LAND, 0.2f, 1.8f)
			.layer(SoundEvents.SLIME_DEATH, 0.4f, 0.8f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.6f, 2);
	public static final OmnitrixSfx.Cue PICKUP = OmnitrixSfx.cue("goop_pickup")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 1.8f)
			.layer(SoundEvents.SLIME_BLOCK_PLACE, 0.4f, 1.1f);
	public static final OmnitrixSfx.Cue CANNON = OmnitrixSfx.cue("goop_cannon")
			.layer(SoundEvents.SLIME_BLOCK_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.LLAMA_SPIT, 0.4f, 0.6f)
			.layer(SoundEvents.BUCKET_EMPTY, 0.3f, 0.8f, 1);
	// v0.17.2 revamp
	public static final OmnitrixSfx.Cue SPRAY = OmnitrixSfx.cue("goop_spray")
			.layer(SoundEvents.BUCKET_EMPTY, 0.3f, 1.6f)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.15f, 1.3f, 2)
			.gap(4);
	public static final OmnitrixSfx.Cue TIDE = OmnitrixSfx.cue("goop_tide")
			.layer(SoundEvents.GENERIC_SPLASH, 0.6f, 0.6f)
			.layer(SoundEvents.SLIME_BLOCK_BREAK, 0.6f, 0.5f)
			.layer(SoundEvents.BUCKET_EMPTY, 0.5f, 0.5f, 3)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 0.9f, 6);
	public static final OmnitrixSfx.Cue FLING_SOUND = OmnitrixSfx.cue("goop_fling")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.25f, 1.9f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.5f)
			.layer(SoundEvents.HONEY_BLOCK_SLIDE, 0.4f, 0.9f, 3);
	public static final OmnitrixSfx.Cue MELT = OmnitrixSfx.cue("goop_melt")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 0.8f)
			.layer(SoundEvents.LAVA_EXTINGUISH, 0.15f, 1.6f)
			.layer(SoundEvents.SLIME_SQUISH, 0.3f, 0.6f, 2)
			.gap(3);

	// ---------------------------------------------------------------- poses, flags, moves
	public static final int ANIM_SPIT = 1, ANIM_GLOB = 2, ANIM_WHIP = 3, ANIM_ENGULF = 4, ANIM_BOUNCE = 5, ANIM_SQUEEZE = 6, ANIM_SLAM = 7,
			ANIM_SPRAY = 8, ANIM_REFORM = 9, ANIM_GLIDE = 10, ANIM_FLOOD = 11, ANIM_CANNON = 12, ANIM_KNOCKED = 13, ANIM_FLING = 14,
			ANIM_SPIT_OUT = 15, ANIM_MELT = 16, ANIM_PUNCH = 17;

	public static final int F_SQUEEZE = 1;
	/** The projector is knocked off: he is a puddle. */
	public static final int F_OFF = 2;
	/** Slime Chute (sneaking in the air). */
	public static final int F_GLIDE = 4;
	public static final int F_ENGULF = 8;
	/** v0.17.2 revamp: Acid Spray is running. */
	public static final int F_SPRAY = 16;
	/** v0.17.2 revamp: Acid Tide's surge (he is a wave). */
	public static final int F_TIDE = 32;
	/** v0.17.2 revamp: pouring after the flung projector (a puddle on the move). */
	public static final int F_POUR = 64;

	public static final String ACID_SPIT = "acid_spit", ACID_GLOB = "acid_glob", SLIME_WHIP = "slime_whip", ENGULF = "engulf", BOUNCE = "slime_bounce",
			SQUEEZE = "squeeze", SLIME_SLAM = "slime_slam", ACID_SPRAY = "acid_spray", REFORM_MOVE = "reform", FLING = "projector_fling",
			ACID_TIDE = "acid_tide", GOO_CANNON = "goo_cannon", TOUCH = "corrosive_touch", DISSOLVE = "dissolve", CHUTE = "slime_chute";

	/** Synced kit timers. */
	public static final String SQUEEZE_START = "squeeze_start", SPRAY_START = "spray_start", TIDE_START = "tide_start", POUR_END = "pour_end",
			OFF_SINCE = "off_since";

	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger, i want each alien to feel like its own entire ability and i want them to be strong aswell"): ~x1.6 damage pass (docs/OMNITRIX_DAMAGE_PASS_ALIENFORCE.md);
	// the puddle pulses 16 times, so its tick stays a little under the 7-10 of a short DoT
	public static final float SPIT_DAMAGE = 11f, GLOB_DAMAGE = 20f, PUDDLE_DAMAGE = 5f, WHIP_DAMAGE = 22f, ENGULF_DAMAGE = 8f, SPIT_OUT_DAMAGE = 10f,
			SLAM_DAMAGE = 28f, SPRAY_DAMAGE = 8f, TIDE_DAMAGE = 85f, LAKE_DAMAGE = 8f, CANNON_DAMAGE = 30f, REFORM_HEAL = 8f, TOUCH_DAMAGE = 4f,
			ACID_SKIN = 4f;
	public static final double WHIP_RANGE = 8, WHIP_PULL = 3, ENGULF_RANGE = 4.5, SLAM_RADIUS = 4, SPRAY_RANGE = 6, SPRAY_DEG = 50, LAKE_RADIUS = 6,
			GLOB_RADIUS = 2.5, TIDE_LENGTH = 10, TIDE_WIDTH = 2.2, FLING_RANGE = 16, DISSOLVE_REACH = 4.5;
	public static final int PUDDLE_TICKS = 160, LAKE_TICKS = 120, PULSE = 10, ENGULF_TICKS = 60, ENGULF_PULSE = 15, SQUEEZE_MAX = 120,
			SQUEEZE_COOLDOWN = 60, SPRAY_TICKS = 30, SPRAY_PULSE = 10, TIDE_TICKS = 10, PROJECTOR_RETURN = 200, DISSOLVE_TICKS = 200,
			BOUNCES = 2;
	/** Dissolve eats nothing harder than this (iron bars / an iron door are 5; obsidian 50). */
	public static final float DISSOLVE_HARDNESS = 5f;
	/** Physical blows are halved (a polymorph's body) -- unless he is squeezed thin. */
	public static final float PHYSICAL_TAKEN = 0.5f;
	public static final float FIRE_TAKEN = 1.5f;
	/** A hit this big (before his resistances) knocks the projector off. */
	public static final float KNOCK_OFF = 10f;
	public static final double SQUEEZE_SCALE = 0.35, PUDDLE_SCALE = 0.45, SQUEEZE_SPEED = 0.2, PUDDLE_SPEED = -0.7;
	public static final double CHUTE_FALL = -0.14;

	private static final ResourceLocation SQUEEZE_SCALE_ID = PowerToggles.id("omnitrix_goop_squeeze_scale");
	private static final ResourceLocation SQUEEZE_SPEED_ID = PowerToggles.id("omnitrix_goop_squeeze_speed");
	private static final ResourceLocation PUDDLE_SCALE_ID = PowerToggles.id("omnitrix_goop_puddle_scale");
	private static final ResourceLocation PUDDLE_SPEED_ID = PowerToggles.id("omnitrix_goop_puddle_speed");
	public static final int ACID = 0x7CE03A, ACID_DARK = 0x2E7A14, ACID_PALE = 0xC8FF9A;

	// ---------------------------------------------------------------- per-player state
	private static final Map<UUID, List<Puddle>> PUDDLES = new ConcurrentHashMap<>();
	private static final Map<UUID, Engulfed> ENGULFED = new ConcurrentHashMap<>();
	private static final Map<UUID, Projector> PROJECTORS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> Acid Tide's surge. */
	private static final Map<UUID, Tide> TIDES = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> the pour after his flung projector. */
	private static final Map<UUID, Pour> POURS = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: player -> Slime Bounce's rebounds left. */
	private static final Map<UUID, Bounce> BOUNCING = new ConcurrentHashMap<>();
	/** v0.17.2 revamp: once per game tick (the spray / tide / puddle pulses must not double up: RipjawsKit.tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private static final class Puddle {
		final ResourceKey<Level> dim;
		final Vec3 at;
		final double radius;
		final float damage;
		final long until;
		long next;
		final AlienProjectileEntity look;

		Puddle(ResourceKey<Level> dim, Vec3 at, double radius, float damage, long until, long next, AlienProjectileEntity look) {
			this.dim = dim;
			this.at = at;
			this.radius = radius;
			this.damage = damage;
			this.until = until;
			this.next = next;
			this.look = look;
		}
	}

	private static final class Engulfed {
		final LivingEntity target;
		final long until;
		long next;

		Engulfed(LivingEntity target, long until, long next) {
			this.target = target;
			this.until = until;
			this.next = next;
		}
	}

	private record Projector(ResourceKey<Level> dim, Vec3 at, long since, AlienProjectileEntity look) {
	}

	private record Tide(ResourceKey<Level> dim, Vec3 dir, long start, Set<Integer> hits) {
	}

	private record Pour(ResourceKey<Level> dim, Vec3 from, Vec3 to, long start, long end, AlienProjectileEntity look) {
	}

	private static final class Bounce {
		int left;
		double power;
		long since;

		Bounce(int left, double power, long since) {
			this.left = left;
			this.power = power;
			this.since = since;
		}
	}

	private GoopKit() {
	}

	public static void register() {
		Aliens.register(new GoopKit());
		AlienProjectileEntity.onHit("goop_glob", (proj, hit) -> {
			if (proj.getOwner() instanceof ServerPlayer owner && Aliens.kit(ID) instanceof GoopKit kit) {
				Vec3 at = ground(owner, hit.getLocation());
				kit.puddle(owner, at, GLOB_RADIUS, PUDDLE_DAMAGE, PUDDLE_TICKS);
				OmnitrixSfx.play(level(owner), at, SPLAT, 1f, 1f);
				QuirkFx.ring(level(owner), at.add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size((float) GLOB_RADIUS + 0.5f)
						.thickness(0.4f).life(10).ground().spawn();
			}
		});
		AlienProjectileEntity.onHit("goop_spit", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel level) {
				Vec3 at = hit.getLocation();
				QuirkFx.ring(level, at).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size(0.9f).thickness(0.25f).life(6).spawn();
				OmnitrixSfx.play(level, at, SIZZLE, 0.8f, 1f);
			}
		});
		AlienProjectileEntity.onHit("goop_cannon", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel level) {
				Vec3 at = hit.getLocation();
				QuirkFx.blast(level, at).colours(ACID_PALE, ACID_DARK).size(3f).life(14).flags(QuirkFx.F_NO_SMOKE).spawn();
				QuirkFx.decal(level, at.add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(ACID_DARK).size(2.6f).life(160).spawn();
				QuirkCamera.shakeAround(level, at, 8, 0.2f, 5);
				OmnitrixSfx.play(level, at, SPLAT, 1.3f, 0.8f);
			}
		});
	}

	public static void clearSessionState() {
		for (List<Puddle> l : PUDDLES.values()) {
			l.forEach(pd -> pd.look.discard());
		}
		PUDDLES.clear();
		ENGULFED.clear();
		for (Projector pr : PROJECTORS.values()) {
			pr.look().discard();
		}
		PROJECTORS.clear();
		for (Pour po : POURS.values()) {
			po.look().discard();
		}
		POURS.clear();
		TIDES.clear();
		BOUNCING.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Goop right now. */
	public boolean isGoop(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(26).armor(2, 0).knockbackResist(0.3f).attack(5).safeFall(6).build(); // v0.17.2 (user set the unarmed melee bonuses: "Swampfire +9, Echo Echo +4, Humungousaur +15, Jetray +5, Big Chill +6, Chromastone +7, Brainstorm +4, Spidermonkey +5, Goop +5")
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(ACID_SPIT, AbilitySlot.SLOT_1, 20), AlienMove.shift(ACID_GLOB, AbilitySlot.SLOT_1, 200),
				AlienMove.tap(SLIME_WHIP, AbilitySlot.SLOT_2, 80), AlienMove.shift(ENGULF, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(BOUNCE, AbilitySlot.SLOT_3, 30), AlienMove.shift(SQUEEZE, AbilitySlot.SLOT_3, SQUEEZE_COOLDOWN),
				AlienMove.tap(ACID_SPRAY, AbilitySlot.SLOT_4, 140), AlienMove.shift(ACID_TIDE, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(REFORM_MOVE, AbilitySlot.SLOT_5, 400), AlienMove.shift(FLING, AbilitySlot.SLOT_5, 160),
				AlienMove.tap(SLIME_SLAM, AbilitySlot.SLOT_6, 200), AlienMove.shift(GOO_CANNON, AbilitySlot.SLOT_6, 260),
				AlienMove.vanilla(TOUCH, "Attack", 0), AlienMove.vanilla(DISSOLVE, "Use (on a block)", 20),
				AlienMove.vanilla(CHUTE, "Sneak (in the air)", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		List<Puddle> l = PUDDLES.remove(id);
		if (l != null) {
			l.forEach(pd -> pd.look.discard());
		}
		Engulfed en = ENGULFED.remove(id);
		if (en != null) {
			release(p, en, false);
		}
		Projector pr = PROJECTORS.remove(id);
		if (pr != null) {
			pr.look().discard();
		}
		Pour po = POURS.remove(id);
		if (po != null) {
			po.look().discard();
		}
		TIDES.remove(id);
		BOUNCING.remove(id);
		LAST_TICK.remove(id);
		PowerToggles.clearModifier(p, Attributes.SCALE, SQUEEZE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SQUEEZE_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.SCALE, PUDDLE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PUDDLE_SPEED_ID);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : new String[] { SQUEEZE_START, SPRAY_START, TIDE_START, POUR_END, OFF_SINCE }) {
			n.timers.remove(key(k));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	/** Anything of {@code p}'s left behind (gametests). */
	public static int leftovers(ServerPlayer p) {
		UUID id = p.getUUID();
		return (PUDDLES.containsKey(id) ? 1 : 0) + (ENGULFED.containsKey(id) ? 1 : 0) + (PROJECTORS.containsKey(id) ? 1 : 0)
				+ (TIDES.containsKey(id) ? 1 : 0) + (POURS.containsKey(id) ? 1 : 0) + (BOUNCING.containsKey(id) ? 1 : 0);
	}

	/** Whether any per-player state of {@code p} is held, the tick guard included (tests: nothing after turning back). */
	public static boolean holds(ServerPlayer p) {
		return leftovers(p) > 0 || LAST_TICK.containsKey(p.getUUID());
	}

	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!SQUEEZE.equals(moveId) && flag(p, F_SQUEEZE)) {
			endSqueeze(p); // any other move pops him back to full size
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// once per game tick: the pulses below must not double up (a mock player can be ticked twice)
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel level = level(p);
		UUID id = p.getUUID();
		// the projector on the ground: flow back over it (or it floats home by itself)
		Projector pr = PROJECTORS.get(id);
		if (pr != null) {
			if (pr.dim() != level.dimension() || now - pr.since() >= PROJECTOR_RETURN || p.position().distanceTo(pr.at()) < 1.5) {
				pickUp(p);
			} else if (now % 20 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
			}
		}
		if (flag(p, F_SQUEEZE) && now - timer(p, SQUEEZE_START) >= SQUEEZE_MAX) {
			endSqueeze(p);
		}
		tickChute(p);
		tickBounce(p, now);
		tickSpray(p, level, now);
		tickTide(p, level, now);
		tickPour(p, level, now);
		// the engulfed creature, held inside him
		Engulfed en = ENGULFED.get(id);
		if (en != null) {
			if (now >= en.until || !en.target.isAlive()) {
				ENGULFED.remove(id);
				release(p, en, false);
			} else {
				Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).scale(0.2));
				en.target.teleportTo(c.x, p.getY() + 0.1, c.z);
				en.target.setDeltaMovement(Vec3.ZERO);
				if (now >= en.next) {
					en.next = now + ENGULF_PULSE;
					en.target.invulnerableTime = 0;
					hurt(p, en.target, ENGULF_DAMAGE, false);
					OmnitrixSfx.play(level, c, SIZZLE, 0.8f, 0.9f);
				}
			}
		}
		// acid puddles
		List<Puddle> puddles = PUDDLES.get(id);
		if (puddles != null) {
			puddles.removeIf(pd -> {
				if (now >= pd.until || pd.dim != level.dimension()) {
					pd.look.discard();
					return true;
				}
				if (now >= pd.next) {
					pd.next = now + PULSE;
					puddlePulse(p, pd);
				}
				return false;
			});
			if (puddles.isEmpty()) {
				PUDDLES.remove(id);
			}
		}
	}

	/** One acid pulse on everything standing in {@code pd}. Returns how many it burnt. */
	private int puddlePulse(ServerPlayer p, Puddle pd) {
		int n = 0;
		for (LivingEntity e : around(p, pd.at, pd.radius)) {
			if (Math.abs(e.getY() - pd.at.y) < 1.2) {
				e.invulnerableTime = 0;
				if (hurt(p, e, pd.damage, false)) {
					n++;
				}
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, true), p);
			}
		}
		if (n > 0) {
			OmnitrixSfx.play(level(p), pd.at, SIZZLE, 0.7f, 1f);
		}
		return n;
	}

	/** Every acid puddle of {@code p} pulses now (gametests: no waiting on the tick). Returns how many creatures it burnt. */
	public int pulsePuddlesNow(ServerPlayer p) {
		int n = 0;
		for (Puddle pd : PUDDLES.getOrDefault(p.getUUID(), List.of())) {
			n += puddlePulse(p, pd);
		}
		return n;
	}

	// ---------------------------------------------------------------- the projector

	/** Whether the projector is knocked off. Client-safe. */
	public boolean projectorOff(Player p) {
		return flag(p, F_OFF);
	}

	/** Where the knocked-off projector lies, or null. */
	@Nullable
	public static Vec3 projectorAt(ServerPlayer p) {
		Projector pr = PROJECTORS.get(p.getUUID());
		return pr == null ? null : pr.at();
	}

	/** A big hit knocks the projector off: it lands a few blocks away and he slumps into a puddle until he gets back to it. */
	public void knockOff(ServerPlayer p, @Nullable Entity from) {
		if (flag(p, F_OFF) || flag(p, F_POUR) || flag(p, F_TIDE)) {
			return;
		}
		endSqueeze(p);
		stopSpray(p);
		BOUNCING.remove(p.getUUID());
		Vec3 away;
		if (from != null && from != p) {
			away = p.position().subtract(from.position()).multiply(1, 0, 1);
		} else {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			away = new Vec3(Math.cos(a), 0, Math.sin(a));
		}
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		Vec3 spot = p.position();
		for (double d = 0.5; d <= 3.0; d += 0.5) {
			Vec3 at = p.position().add(away.scale(d)).add(0, 0.5, 0);
			if (!p.level().getBlockState(BlockPos.containing(at)).isAir()) {
				break;
			}
			spot = at;
		}
		spot = ground(p, spot);
		AlienProjectileEntity look = AlienProjectileEntity.of(p, "goop_projector");
		look.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0f);
		look.colour(0).size(0.6f).held(true).speed(0).life(PROJECTOR_RETURN + 40).damage(0f).spawn();
		long now = p.level().getGameTime();
		PROJECTORS.put(p.getUUID(), new Projector(p.level().dimension(), spot, now, look));
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags |= F_OFF;
		n.timers.put(key(OFF_SINCE), now);
		Omnitrix.save(p, n);
		anim(p, ANIM_KNOCKED);
		PowerToggles.modifier(p, Attributes.SCALE, PUDDLE_SCALE_ID, PUDDLE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, PUDDLE_SPEED_ID, PUDDLE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Engulfed en = ENGULFED.remove(p.getUUID());
		if (en != null) {
			release(p, en, false);
		}
		QuirkFx.decal(level(p), p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(ACID_DARK).size(1.6f).life(PROJECTOR_RETURN).spawn();
		OmnitrixSfx.playOn(p, KNOCKED, 1f, 1f, SoundSource.PLAYERS);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
	}

	/** He is back under his projector. */
	public void pickUp(ServerPlayer p) {
		Projector pr = PROJECTORS.remove(p.getUUID());
		if (pr != null) {
			pr.look().discard();
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags &= ~F_OFF;
		n.timers.remove(key(OFF_SINCE));
		Omnitrix.save(p, n);
		PowerToggles.clearModifier(p, Attributes.SCALE, PUDDLE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PUDDLE_SPEED_ID);
		anim(p, ANIM_REFORM);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size(1.6f).thickness(0.3f).life(10).spawn();
		OmnitrixSfx.playOn(p, PICKUP, 1f, 1f, SoundSource.PLAYERS);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_back").withStyle(ChatFormatting.GREEN), true);
	}

	private static Vec3 ground(ServerPlayer p, Vec3 at) {
		HitResult down = p.level().clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.BLOCK ? down.getLocation().add(0, 0.02, 0) : at;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)) {
			return false; // a blob lands soft and fits anywhere
		}
		if ((flag(p, F_POUR) || flag(p, F_TIDE)) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // a puddle on the move / a wave: nothing to hit
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		boolean physical = GhostfreakKit.physical(source);
		if ((amount >= KNOCK_OFF || source.is(DamageTypes.LIGHTNING_BOLT)) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& !source.is(DamageTypes.STARVE) && !source.is(DamageTypes.DROWN)) {
			knockOff(p, source.getEntity());
		}
		// v0.17.2 revamp, Acid Skin: whatever strikes him in melee gets a hand full of acid
		if (physical && source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity() && attacker != p
				&& attacker.distanceToSqr(p) < 16) {
			hurt(p, attacker, ACID_SKIN, false);
		}
		float a = amount;
		if (physical && !flag(p, F_SQUEEZE)) {
			a *= PHYSICAL_TAKEN; // v0.17.2 revamp: squeezed thin, the blow lands in full
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			a *= FIRE_TAKEN;
		}
		return a;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_OFF)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
			return;
		}
		if (flag(p, F_POUR) || flag(p, F_TIDE)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					acidGlob(p);
				} else {
					acidSpit(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					if (ENGULFED.containsKey(p.getUUID())) {
						spitOut(p);
					} else {
						engulf(p);
					}
				} else {
					slimeWhip(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					squeeze(p);
				} else {
					slimeBounce(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					acidTide(p);
				} else {
					acidSpray(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					projectorFling(p);
				} else {
					reform(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					gooCannon(p);
				} else {
					slimeSlam(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		// v0.17.2 revamp: Squeeze lasts while Shift+X is held
		if (slot == AbilitySlot.SLOT_3 && shift && flag(p, F_SQUEEZE)) {
			endSqueeze(p);
		}
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> flag(p, F_ENGULF);
			case SLOT_3 -> flag(p, F_SQUEEZE);
			case SLOT_4 -> flag(p, F_SPRAY) || flag(p, F_TIDE);
			case SLOT_5 -> flag(p, F_POUR);
			default -> false;
		};
	}

	/** v0.17.2 revamp: squeezed, he slides up walls (synced flag: asked on both sides). */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_SQUEEZE);
	}

	/** Refuses a move while the projector is off (the moves are public for the gametests). */
	private boolean ok(ServerPlayer p) {
		if (flag(p, F_OFF)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
			return false;
		}
		return !flag(p, F_POUR) && !flag(p, F_TIDE);
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	/** Where his hands are, for the effects (in front of the chest, a little to the right). */
	private static Vec3 hand(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return p.getEyePosition().add(look.scale(0.5)).add(right.scale(0.3)).add(0, -0.35 * p.getScale(), 0);
	}

	// ---------------------------------------------------------------- R / Shift+R: acid

	public boolean acidSpit(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_SPIT)) {
			return false;
		}
		anim(p, ANIM_SPIT);
		AlienProjectileEntity.of(p, "goop_spit").colour(ACID).size(0.3f).damage(SPIT_DAMAGE).speed(1.9).gravity(0.01).life(40).spawn();
		OmnitrixSfx.playOn(p, SPIT, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	public boolean acidGlob(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_GLOB)) {
			return false;
		}
		anim(p, ANIM_GLOB);
		AlienProjectileEntity.of(p, "goop_glob").colour(ACID).size(0.55f).damage(0f).blast((float) GLOB_RADIUS, GLOB_DAMAGE).speed(1.1).gravity(0.05)
				.life(80).spawn();
		OmnitrixSfx.playOn(p, SPIT, 1f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	/** A puddle of acid at {@code at}. */
	public void puddle(ServerPlayer p, Vec3 at, double radius, float damage, int ticks) {
		long now = p.level().getGameTime();
		AlienProjectileEntity look = AlienProjectileEntity.of(p, "goop_puddle");
		look.moveTo(at.x, at.y, at.z, 0f, 0f);
		look.colour(ticks).size((float) radius).held(true).speed(0).life(ticks + 5).damage(0f).spawn();
		List<Puddle> l = PUDDLES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		while (l.size() >= 4) {
			l.remove(0).look.discard();
		}
		l.add(new Puddle(p.level().dimension(), at, radius, damage, now + ticks, now + PULSE, look));
	}

	// ---------------------------------------------------------------- G / Shift+G: whip, engulf

	/** G: his arm stretches 8 blocks and lashes -- 13 to what is in front; small creatures are dragged 3 blocks in. */
	public boolean slimeWhip(ServerPlayer p) {
		if (!ok(p) || !use(p, SLIME_WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity first = lookTarget(p, WHIP_RANGE);
		Vec3 end = first != null ? first.position().add(0, first.getBbHeight() * 0.5, 0) : eye.add(look.scale(WHIP_RANGE));
		for (LivingEntity e : around(p, eye.add(look.scale(WHIP_RANGE * 0.5)), WHIP_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (e == first || (to.length() <= WHIP_RANGE + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.85)) {
				hurt(p, e, WHIP_DAMAGE, false);
				if (!AlienStatus.isBoss(e) && e.getBbWidth() <= 1.4f) {
					Vec3 h = p.position().subtract(e.position()).multiply(1, 0, 1);
					double d = h.length();
					if (d > 1.5) {
						Vec3 dir = h.normalize();
						double move = Math.min(WHIP_PULL, d - 1.5);
						e.push(dir.x * move * 0.33, 0.25, dir.z * move * 0.33);
						e.hurtMarked = true;
					}
				}
			}
		}
		QuirkFx.ribbon(level(p), hand(p), end).style(QuirkFx.RIBBON_CLOTH).colours(ACID_PALE, ACID).size(0.35f).life(8).spawn();
		OmnitrixSfx.playOn(p, WHIP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	public boolean engulf(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		LivingEntity t = lookTarget(p, ENGULF_RANGE);
		if (t == null || AlienStatus.isBoss(t) || t.getBbHeight() > 2.3f || t.getBbWidth() > 1.5f) {
			say(p, "message.projecthero.omnitrix.goop.no_engulf");
			return false;
		}
		if (!use(p, ENGULF)) {
			return false;
		}
		anim(p, ANIM_ENGULF);
		long now = p.level().getGameTime();
		ENGULFED.put(p.getUUID(), new Engulfed(t, now + ENGULF_TICKS, now + 5));
		setFlag(p, F_ENGULF, true);
		OmnitrixSfx.playOn(p, GULP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether {@code p} holds a creature inside (gametests). */
	public static boolean engulfing(ServerPlayer p) {
		return ENGULFED.containsKey(p.getUUID());
	}

	/** v0.17.2 revamp: Shift+G while a creature is inside -- spits it out early: thrown 6 blocks, +6. True if one was spat. */
	public boolean spitOut(ServerPlayer p) {
		Engulfed en = ENGULFED.remove(p.getUUID());
		if (en == null) {
			return false;
		}
		anim(p, ANIM_SPIT_OUT);
		release(p, en, true);
		return true;
	}

	private void release(ServerPlayer p, Engulfed en, boolean hard) {
		setFlag(p, F_ENGULF, false);
		if (en.target.isAlive()) {
			Vec3 f = p.getLookAngle().multiply(1, 0, 1);
			f = f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
			en.target.teleportTo(p.getX() + f.x * 1.2, p.getY() + 0.2, p.getZ() + f.z * 1.2);
			if (hard) {
				en.target.invulnerableTime = 0;
				hurt(p, en.target, SPIT_OUT_DAMAGE, false);
				en.target.setDeltaMovement(f.x * 1.6, 0.45, f.z * 1.6); // about 6 blocks
				en.target.hurtMarked = true;
			} else {
				shove(en.target, p.position(), 0.8, 0.3);
			}
		}
		if (p.level() instanceof ServerLevel level) {
			QuirkFx.ring(level, p.position().add(0, 1.0, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT)
					.colours(ACID_PALE, ACID).size(1.4f).thickness(0.3f).life(8).spawn();
			OmnitrixSfx.play(level, p.position(), SPLAT, 0.8f, hard ? 0.9f : 1.2f);
		}
	}

	// ---------------------------------------------------------------- X / Shift+X: movement

	public boolean slimeBounce(ServerPlayer p) {
		if (!ok(p) || !use(p, BOUNCE)) {
			return false;
		}
		anim(p, ANIM_BOUNCE);
		launch(p, 1.0);
		BOUNCING.put(p.getUUID(), new Bounce(BOUNCES, 1.0, p.level().getGameTime()));
		return true;
	}

	private void launch(ServerPlayer p, double power) {
		Vec3 f = p.getLookAngle().multiply(1, 0, 1);
		f = f.lengthSqr() < 1.0e-4 ? Vec3.ZERO : f.normalize();
		p.setDeltaMovement(f.scale(1.0 * power).add(0, 0.85 * power, 0));
		p.hurtMarked = true;
		p.fallDistance = 0f;
		QuirkFx.ring(level(p), p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size(1.3f * (float) power).thickness(0.3f)
				.life(8).ground().spawn();
		OmnitrixSfx.playOn(p, BOING, 1f, (float) (0.8 + 0.2 / power), SoundSource.PLAYERS);
	}

	/** Slime Bounce's rebounds: each landing springs him up again, lower each time; sneaking soaks it up. */
	private void tickBounce(ServerPlayer p, long now) {
		Bounce b = BOUNCING.get(p.getUUID());
		if (b == null) {
			return;
		}
		if (p.isShiftKeyDown() || p.isInWater() || now - b.since > 80) {
			BOUNCING.remove(p.getUUID());
			return;
		}
		if (p.onGround() && now - b.since >= 4) {
			if (b.left <= 0) {
				BOUNCING.remove(p.getUUID());
				return;
			}
			b.left--;
			b.power *= 0.72;
			b.since = now;
			anim(p, ANIM_BOUNCE);
			launch(p, b.power);
		}
	}

	/** How many rebounds of the current Slime Bounce are left, or -1 (tests). */
	public static int bouncesLeft(ServerPlayer p) {
		Bounce b = BOUNCING.get(p.getUUID());
		return b == null ? -1 : b.left;
	}

	/** Shift+X (held): squeezed small (0.35x) while held, up to 6 s; letting go (or Shift+X again) pops him back. */
	public boolean squeeze(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		if (flag(p, F_SQUEEZE)) {
			endSqueeze(p);
			return true;
		}
		int left = cooldownLeft(p, SQUEEZE);
		if (left > 0) {
			use(p, SQUEEZE); // the "not ready" line
			return false;
		}
		anim(p, ANIM_SQUEEZE);
		PowerToggles.modifier(p, Attributes.SCALE, SQUEEZE_SCALE_ID, SQUEEZE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SQUEEZE_SPEED_ID, SQUEEZE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags |= F_SQUEEZE;
		n.timers.put(key(SQUEEZE_START), p.level().getGameTime());
		Omnitrix.save(p, n);
		OmnitrixSfx.playOn(p, SQUISH, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	private void endSqueeze(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.SCALE, SQUEEZE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SQUEEZE_SPEED_ID);
		if (flag(p, F_SQUEEZE)) {
			OmnitrixState n = Omnitrix.state(p).copy();
			n.kitFlags &= ~F_SQUEEZE;
			n.timers.remove(key(SQUEEZE_START));
			Omnitrix.save(p, n);
			cooldown(p, SQUEEZE, SQUEEZE_COOLDOWN); // the cooldown runs from the release
			OmnitrixSfx.playOn(p, SQUISH, 0.8f, 0.8f, SoundSource.PLAYERS);
		}
	}

	/** Slime Chute: sneaking in the air, he flattens into a sheet and floats down. */
	private void tickChute(ServerPlayer p) {
		boolean on = !p.onGround() && p.isShiftKeyDown() && !p.isInWater() && !p.getAbilities().flying && !flag(p, F_OFF) && !flag(p, F_POUR)
				&& !flag(p, F_TIDE);
		if (on) {
			Vec3 v = p.getDeltaMovement();
			if (v.y < CHUTE_FALL) {
				p.setDeltaMovement(v.x * 0.98, CHUTE_FALL, v.z * 0.98);
				p.hurtMarked = true;
			}
			p.fallDistance = 0f;
		}
		if (on != flag(p, F_GLIDE)) {
			setFlag(p, F_GLIDE, on);
		}
	}

	// ---------------------------------------------------------------- Z / Shift+Z: spray, the tide

	/** Z: 1.5 s of acid from both hands, steered by the look -- 3.5 every half second in a 6-block cone, weakened 5 s. */
	public boolean acidSpray(ServerPlayer p) {
		if (!ok(p) || flag(p, F_SPRAY) || !use(p, ACID_SPRAY)) {
			return false;
		}
		anim(p, ANIM_SPRAY);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags |= F_SPRAY;
		n.timers.put(key(SPRAY_START), p.level().getGameTime());
		Omnitrix.save(p, n);
		sprayPulse(p, level(p));
		return true;
	}

	private void stopSpray(ServerPlayer p) {
		if (flag(p, F_SPRAY)) {
			OmnitrixState n = Omnitrix.state(p).copy();
			n.kitFlags &= ~F_SPRAY;
			n.timers.remove(key(SPRAY_START));
			Omnitrix.save(p, n);
		}
	}

	private void tickSpray(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_SPRAY)) {
			return;
		}
		long t = now - timer(p, SPRAY_START);
		if (t > SPRAY_TICKS) {
			stopSpray(p);
			return;
		}
		if (t > 0 && t % SPRAY_PULSE == 0) {
			sprayPulse(p, l);
		}
	}

	/** One lick of the spray (tests call it through {@link #acidSpray} and the tick). Returns how many it hit. */
	private int sprayPulse(ServerPlayer p, ServerLevel l) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double cos = Math.cos(Math.toRadians(SPRAY_DEG / 2));
		int n = 0;
		for (LivingEntity e : around(p, eye, SPRAY_RANGE + 1)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d > SPRAY_RANGE + e.getBbWidth() * 0.5 || (d >= 1.0 && to.normalize().dot(look) < cos)) {
				continue;
			}
			e.invulnerableTime = 0;
			if (hurt(p, e, SPRAY_DAMAGE, false)) {
				n++;
			}
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
		}
		QuirkFx.cone(l, hand(p), look).style(QuirkFx.CONE_DEBRIS).colours(ACID, ACID_DARK).size((float) (SPRAY_RANGE * Math.tan(Math.toRadians(SPRAY_DEG / 2))))
				.length((float) SPRAY_RANGE).life(12).flags(QuirkFx.F_NO_SMOKE).spawn();
		// the stream itself: a fat ribbon of acid from his hands out along the look
		QuirkFx.ribbon(l, hand(p), hand(p).add(look.scale(SPRAY_RANGE))).style(QuirkFx.RIBBON_CLOTH).colours(ACID_PALE, ACID).size(0.55f).life(11)
				.spawn();
		OmnitrixSfx.play(l, p.position(), SPRAY, 0.9f, 1f);
		return n;
	}

	/**
	 * Shift+Z (ULTIMATE): Acid Tide -- he collapses into a wave and surges 10 blocks the way he looks over half a second,
	 * untouchable; everything it rolls over takes 45 and is carried along. Where it breaks, an acid lake (6 blocks, 6 s).
	 */
	public boolean acidTide(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_TIDE)) {
			return false;
		}
		endSqueeze(p);
		stopSpray(p);
		BOUNCING.remove(p.getUUID());
		anim(p, ANIM_FLOOD);
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		long now = p.level().getGameTime();
		TIDES.put(p.getUUID(), new Tide(p.level().dimension(), dir, now, new HashSet<>()));
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags |= F_TIDE;
		n.timers.put(key(TIDE_START), now);
		Omnitrix.save(p, n);
		PowerToggles.modifier(p, Attributes.SCALE, PUDDLE_SCALE_ID, PUDDLE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ServerLevel l = level(p);
		QuirkCamera.shake(p, 0.3f, 8);
		OmnitrixSfx.play(l, p.position(), TIDE, 1.3f, 1f);
		tideStep(p, l, TIDES.get(p.getUUID()));
		return true;
	}

	private void tickTide(ServerPlayer p, ServerLevel l, long now) {
		Tide t = TIDES.get(p.getUUID());
		if (t == null) {
			return;
		}
		if (t.dim() != l.dimension() || now - t.start() >= TIDE_TICKS) {
			breakTide(p);
			return;
		}
		tideStep(p, l, t);
	}

	private void tideStep(ServerPlayer p, ServerLevel l, Tide t) {
		double speed = TIDE_LENGTH / TIDE_TICKS;
		p.setDeltaMovement(t.dir().x * speed, Math.min(p.getDeltaMovement().y, 0.1), t.dir().z * speed);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		// the wave's wake: a splash ring at his feet every other tick, and the crest standing up ahead of him
		if ((l.getGameTime() - t.start()) % 2 == 0) {
			QuirkFx.ring(l, p.position().add(0, 0.06, 0)).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size(2.2f).thickness(0.5f).life(12).ground()
					.spawn();
			QuirkFx.ring(l, p.position().add(t.dir().scale(0.8)).add(0, 0.7, 0)).flags(QuirkFx.F_VERTICAL).vec(t.dir()).style(QuirkFx.RING_SOFT)
					.colours(ACID_PALE, ACID).size(1.5f).thickness(0.6f).life(6).spawn();
		}
		for (LivingEntity e : around(p, p.position().add(0, 0.5, 0), TIDE_WIDTH)) {
			if (t.hits().add(e.getId())) {
				hurt(p, e, TIDE_DAMAGE, false);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), p);
			}
			if (!AlienStatus.isBoss(e)) {
				e.setDeltaMovement(t.dir().x * speed * 1.05, 0.15, t.dir().z * speed * 1.05); // carried on the wave
				e.hurtMarked = true;
			}
		}
	}

	/** The wave breaks: the acid lake where he stands, and he pulls himself back up. */
	public void breakTide(ServerPlayer p) {
		TIDES.remove(p.getUUID());
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags &= ~F_TIDE;
		n.timers.remove(key(TIDE_START));
		Omnitrix.save(p, n);
		if (!flag(p, F_OFF)) {
			PowerToggles.clearModifier(p, Attributes.SCALE, PUDDLE_SCALE_ID);
		}
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
		p.hurtMarked = true;
		ServerLevel l = level(p);
		Vec3 at = ground(p, p.position());
		puddle(p, at, LAKE_RADIUS, LAKE_DAMAGE, LAKE_TICKS);
		QuirkFx.ring(l, at.add(0, 0.05, 0)).style(QuirkFx.RING_SHOCK).colours(ACID_PALE, ACID).size((float) LAKE_RADIUS + 1f).thickness(0.9f).life(14)
				.ground().spawn();
		QuirkCamera.shakeAround(l, at, 14, 0.35f, 8);
		anim(p, ANIM_REFORM);
		OmnitrixSfx.play(l, at, SPLAT, 1.5f, 0.7f);
	}

	// ---------------------------------------------------------------- C / Shift+C: slam, cannon

	public boolean slimeSlam(ServerPlayer p) {
		if (!ok(p) || !use(p, SLIME_SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		for (LivingEntity e : around(p, p.position().add(0, 0.5, 0), SLAM_RADIUS)) {
			hurt(p, e, SLAM_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			shove(e, p.position(), 0.7, 0.3);
		}
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.08, 0)).style(QuirkFx.RING_SHOCK).colours(ACID_PALE, ACID).size((float) SLAM_RADIUS + 0.5f).thickness(0.6f)
				.life(12).ground().spawn();
		QuirkFx.decal(l, p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(ACID_DARK).size((float) SLAM_RADIUS * 0.7f).life(120).spawn();
		QuirkCamera.shakeAround(l, p.position(), 8, 0.2f, 6);
		OmnitrixSfx.playOn(p, SPLAT, 1.2f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	public boolean gooCannon(ServerPlayer p) {
		if (!ok(p) || !use(p, GOO_CANNON)) {
			return false;
		}
		anim(p, ANIM_CANNON);
		AlienProjectileEntity.of(p, "goop_cannon").colour(ACID).size(0.9f).damage(0f).blast(3f, CANNON_DAMAGE).speed(1.4).gravity(0.02).life(60).spawn();
		QuirkCamera.shake(p, 0.15f, 4);
		OmnitrixSfx.playOn(p, CANNON, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- V / Shift+V: reform, the projector fling

	/** V: pulls himself back together -- +8 health and the poisons out. Nothing to mend: refused, no cooldown. */
	public boolean reform(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		boolean hurtNow = p.getHealth() < p.getMaxHealth();
		boolean bad = p.getActiveEffects().stream().anyMatch(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL);
		if (!hurtNow && !bad) {
			say(p, "message.projecthero.omnitrix.goop.whole");
			return false;
		}
		if (!use(p, REFORM_MOVE)) {
			return false;
		}
		anim(p, ANIM_REFORM);
		p.heal(REFORM_HEAL);
		p.getActiveEffects().stream().filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).map(MobEffectInstance::getEffect)
				.toList().forEach(p::removeEffect);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size(1.4f).thickness(0.3f).life(12).spawn();
		OmnitrixSfx.playOn(p, REFORM, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/**
	 * Shift+V: Projector Fling -- the projector is thrown up to 16 blocks where he looks; his body drops to a puddle and pours
	 * after it (untouchable), reforming where it lands. Refused with nowhere to land (no cooldown).
	 */
	public boolean projectorFling(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(FLING_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(FLING_RANGE)) : hit.getLocation().subtract(look.scale(0.6));
		Vec3 spot = landing(p, end);
		if (spot == null || spot.distanceToSqr(p.position()) < 4.0) {
			say(p, "message.projecthero.omnitrix.goop.no_fling");
			return false;
		}
		if (!use(p, FLING)) {
			return false;
		}
		endSqueeze(p);
		stopSpray(p);
		BOUNCING.remove(p.getUUID());
		anim(p, ANIM_FLING);
		long now = p.level().getGameTime();
		int ticks = (int) Math.max(6, Math.ceil(spot.distanceTo(p.position()) / 1.4));
		AlienProjectileEntity look2 = AlienProjectileEntity.of(p, "goop_projector");
		look2.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0f);
		look2.colour(1).size(0.6f).held(true).speed(0).life(ticks + 4).damage(0f).spawn();
		POURS.put(p.getUUID(), new Pour(p.level().dimension(), p.position(), spot, now, now + ticks, look2));
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags |= F_POUR;
		n.timers.put(key(POUR_END), now + ticks);
		Omnitrix.save(p, n);
		PowerToggles.modifier(p, Attributes.SCALE, PUDDLE_SCALE_ID, PUDDLE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, hand(p), spot.add(0, 0.3, 0)).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, ACID).size(0.2f).life(ticks + 4).spawn();
		OmnitrixSfx.play(l, p.position(), FLING_SOUND, 1f, 1f);
		return true;
	}

	/** Where his body can reform near {@code at}: on the ground under it, with room for his full-size box; null if none. */
	@Nullable
	private static Vec3 landing(ServerPlayer p, Vec3 at) {
		Vec3 g = ground(p, at);
		for (double up = 0; up <= 2.0; up += 0.5) {
			Vec3 feet = g.add(0, up, 0);
			AABB box = p.getDimensions(p.getPose()).makeBoundingBox(feet).deflate(0.05);
			// his full size (he may be squeezed or a puddle right now)
			double s = Math.max(1.0, 1.0 / Math.max(0.1, p.getScale()));
			AABB full = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + (box.maxY - box.minY) * s, box.maxZ);
			if (p.level().noCollision(p, full)) {
				return feet;
			}
		}
		return null;
	}

	private void tickPour(ServerPlayer p, ServerLevel l, long now) {
		Pour po = POURS.get(p.getUUID());
		if (po == null) {
			return;
		}
		if (po.dim() != l.dimension()) {
			endPour(p, null);
			return;
		}
		double u = Math.min(1.0, (now - po.start()) / (double) Math.max(1, po.end() - po.start()));
		Vec3 at = po.from().lerp(po.to(), u);
		p.teleportTo(at.x, at.y, at.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.fallDistance = 0f;
		if (now >= po.end()) {
			endPour(p, po.to());
		}
	}

	/** The pour is over: he reforms at {@code at} (or where he is). */
	public void endPour(ServerPlayer p, @Nullable Vec3 at) {
		Pour po = POURS.remove(p.getUUID());
		if (po != null) {
			po.look().discard();
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.kitFlags &= ~F_POUR;
		n.timers.remove(key(POUR_END));
		Omnitrix.save(p, n);
		if (!flag(p, F_OFF)) {
			PowerToggles.clearModifier(p, Attributes.SCALE, PUDDLE_SCALE_ID);
		}
		if (at != null) {
			p.teleportTo(at.x, at.y, at.z);
		}
		p.fallDistance = 0f;
		anim(p, ANIM_REFORM);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(ACID_PALE, ACID).size(1.6f).thickness(0.3f).life(10).spawn();
		OmnitrixSfx.playOn(p, PICKUP, 0.8f, 1.1f, SoundSource.PLAYERS);
	}

	/** Whether {@code p} is pouring after his projector (tests). */
	public static boolean pouring(ServerPlayer p) {
		return POURS.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- vanilla keys

	/** Attack: Corrosive Touch -- +2 acid on every punch (its own hit; vanilla's punch still lands). */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_OFF) || flag(p, F_POUR) || flag(p, F_TIDE)) {
			return true;
		}
		if (target instanceof LivingEntity le) {
			hurt(p, le, TOUCH_DAMAGE, false);
			le.invulnerableTime = 0;
			anim(p, ANIM_PUNCH);
		}
		return false;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (flag(p, F_OFF) || flag(p, F_POUR) || flag(p, F_TIDE)) {
			return InteractionResult.FAIL;
		}
		return dissolve(p, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Whether Dissolve can eat {@code st} at {@code pos} (soft enough, one block, no block entity, no fluid). */
	public static boolean dissolvable(ServerLevel l, BlockPos pos, BlockState st) {
		float hard = st.getDestroySpeed(l, pos);
		return !st.isAir() && hard >= 0 && hard <= DISSOLVE_HARDNESS && !st.hasBlockEntity() && st.getFluidState().isEmpty()
				&& !st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !st.hasProperty(BlockStateProperties.BED_PART);
	}

	/**
	 * Use on a block (empty hand): Dissolve -- his acid eats the block for 10 s (it comes back). Nothing harder than iron bars,
	 * no block entities; the abilityTerrainDamage rule and build rights apply. True if it ate one.
	 */
	public boolean dissolve(ServerPlayer p, BlockPos pos) {
		if (cooldownLeft(p, DISSOLVE) > 0 || p.distanceToSqr(Vec3.atCenterOf(pos)) > (DISSOLVE_REACH + 1) * (DISSOLVE_REACH + 1)) {
			return false; // quiet: right-click spam
		}
		ServerLevel l = level(p);
		BlockState st = l.getBlockState(pos);
		if (!HeroConfig.get().abilityTerrainDamage || !p.mayBuild() || !l.mayInteract(p, pos)) {
			say(p, "message.projecthero.omnitrix.goop.no_dissolve");
			return false;
		}
		if (!dissolvable(l, pos, st)) {
			say(p, "message.projecthero.omnitrix.goop.too_hard");
			return false;
		}
		if (!BatchBScheduler.removeTemporarily(l, pos, DISSOLVE_TICKS)) {
			return false;
		}
		cooldown(p, DISSOLVE, -1);
		anim(p, ANIM_MELT);
		Vec3 c = Vec3.atCenterOf(pos);
		QuirkFx.shards(l, c).block(st).size(0.8f).count(8).life(20).spawn();
		QuirkFx.decal(l, Vec3.atBottomCenterOf(pos).add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(ACID_DARK).size(0.9f).life(DISSOLVE_TICKS).spawn();
		OmnitrixSfx.play(l, c, MELT, 1f, 1f);
		return true;
	}
}
