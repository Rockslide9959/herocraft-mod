package com.projecthero.mod.omnitrix.alien.greymatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.OmnitrixRiding;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Grey Matter, the Galvan -- a tiny grey frog-like genius (a third of a player: a 0.6-block-tall hitbox that squeezes
 * through one-block gaps, eyes at 0.53) with a big head and big black eyes. Fragile and quick; mobs keep losing sight of him. He
 * wins with his brain: pressure points, weak spots, gadgets, repairs -- and (v0.17.2 revamp, docs/OMNITRIX_REVAMP_UPGRADE_GREYMATTER_XLR8.md)
 * getting inside the machine, or the monster, and pulling the right wire.
 *
 * <pre>
 *   R        Nerve Pinch      4, stuns the creature in front 3 s                Shift+R  Weak-Spot Strike  a precise crit (18; 24 on a marked target)
 *   G        Analyze          reads a creature and marks it: +35% damage from   Shift+G  Gizmo Turret      a fist-sized turret (6 blocks off): taunts mobs
 *                             everyone 10 s; a marked boss's strikes are                                  within 10, darts the nearest foe (the analysed
 *                             foreseen (a grey warning ring)                                              one first) 2 x 2/s for 10 s; 6 HP
 *   X        Big Leap         a huge leap for something so small (no fall)      Shift+X  Scurry            5 s of top speed, hunters lose you; starts with
 *                                                                                                          a smoke puff (r 3, blind 2 s)
 *   Z        Snare Trap       a tripwire snare (10, roots 4 s; 3 max)           Shift+Z  Inside Job        ULTIMATE: climb INTO a mob within 6 -- 3 s of
 *                                                                                                          6 every 0.5 s through its armour, then out the
 *                                                                                                          far side, stunned 2 s; a machine is rewired
 *                                                                                                          instead (x1.5, shut down 5 s, bosses 2 s)
 *   V        Pocket Workshop  a crafting grid anywhere                          Shift+V  Rewire            tinker with the block you look at (16 blocks):
 *                                                                                                          doors, levers, buttons, lamps, spawners (off
 *                                                                                                          60 s), primed TNT, tripwires
 *   C        Genius Repair    fix a block / boost a furnace / mend held gear    Shift+C  Mind Map          ores, chests and spawners through walls (8 s)
 *   Attack   precision jab (+2 on an analyzed target)    Use on a creature: a quick read    Use on a player: shoulder ride (Backseat
 *            Genius: what the carrier hits is analysed for 5 s)
 * </pre>
 *
 * Passives: Too Small to Track, Sticky Climber (walls like a spider; Sneak holds on), Amphibian (breathes underwater, swims 50%
 * faster), Galvan Intellect (an enchanting table's three offers read out in full; anvil repairs 30% cheaper -- the mixins
 * {@code GreyMatterEnchantMixin} / {@code GreyMatterAnvilMixin}). Weaknesses: Swatted (x1.5 knockback, a big creature's hit stuns
 * him 0.5 s -- {@code GreyMatterKnockbackMixin}), Too Small for Big Weapons (a sword, axe or trident swings at half speed),
 * Fragile (falling blocks hit him twice as hard).
 */
public final class GreyMatterKit extends AlienKit {
	public static final String ID = "grey_matter";
	public static final int COLOUR = 0xA6ACB4;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A tiny, quick pinch / bite. */
	public static final OmnitrixSfx.Cue PINCH = OmnitrixSfx.cue("grey_matter_pinch")
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.35f, 1.6f)
			.layer(SoundEvents.SILVERFISH_HURT, 0.22f, 1.4f);
	/** Sizing something up: a click, a page flick and a light zap. */
	public static final OmnitrixSfx.Cue SCAN = OmnitrixSfx.cue("grey_matter_scan")
			.layer(SoundEvents.LEVER_CLICK, 0.2f, 1.9f)
			.layer(SoundEvents.BOOK_PAGE_TURN, 0.25f, 1.5f)
			.layer(OmnitrixSounds.SWEEP, 0.12f, 2.0f, 1);
	/** A gadget snapping together, a light zap. */
	public static final OmnitrixSfx.Cue GADGET = OmnitrixSfx.cue("grey_matter_gadget")
			.layer(SoundEvents.CRAFTER_CRAFT, 0.35f, 1.5f)
			.layer(SoundEvents.LEVER_CLICK, 0.2f, 1.6f)
			.layer(OmnitrixSounds.SWEEP, 0.15f, 1.9f, 2)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.25f, 1.4f, 2);
	/** A little leap: tiny quick scurries. */
	public static final OmnitrixSfx.Cue LEAP = OmnitrixSfx.cue("grey_matter_leap")
			.layer(SoundEvents.SILVERFISH_STEP, 0.35f, 1.6f)
			.layer(SoundEvents.RABBIT_JUMP, 0.35f, 1.4f);
	/** Tinkering a repair: small clinks, a click, a spark. */
	public static final OmnitrixSfx.Cue REPAIR = OmnitrixSfx.cue("grey_matter_repair")
			.layer(SoundEvents.SMITHING_TABLE_USE, 0.22f, 1.7f)
			.layer(SoundEvents.CRAFTER_CRAFT, 0.25f, 1.8f, 3)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.2f, 1.5f, 5);
	/** Thinking: a squeak, a click, a page. */
	public static final OmnitrixSfx.Cue THINK = OmnitrixSfx.cue("grey_matter_think")
			.layer(SoundEvents.SILVERFISH_AMBIENT, 0.28f, 1.5f)
			.layer(SoundEvents.LEVER_CLICK, 0.15f, 2.0f)
			.layer(SoundEvents.BOOK_PAGE_TURN, 0.2f, 1.6f, 3);
	/** The Gizmo Turret's lure (every half second; v0.17.1 the Decoy's): a soft tick and a squeak. */
	public static final OmnitrixSfx.Cue DECOY_SOUND = OmnitrixSfx.cue("grey_matter_decoy")
			.layer(SoundEvents.TRIPWIRE_CLICK_ON, 0.16f, 1.7f)
			.layer(SoundEvents.SILVERFISH_AMBIENT, 0.12f, 1.6f)
			.gap(8);
	/** v0.17.2: the Gizmo Turret's dart -- a tiny dispenser pop and a tick on the hit. */
	public static final OmnitrixSfx.Cue DART = OmnitrixSfx.cue("grey_matter_dart")
			.layer(SoundEvents.DISPENSER_LAUNCH, 0.18f, 1.9f)
			.layer(SoundEvents.ARROW_HIT, 0.14f, 1.8f, 2);
	/** v0.17.2: Inside Job -- squeezing in, wet and squirmy. */
	public static final OmnitrixSfx.Cue SQUEEZE = OmnitrixSfx.cue("grey_matter_squeeze")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.35f, 1.5f)
			.layer(SoundEvents.SILVERFISH_STEP, 0.3f, 1.3f, 2);
	/** v0.17.2: Rewire -- a click, a zap and a relay snapping over. */
	public static final OmnitrixSfx.Cue REWIRE_SOUND = OmnitrixSfx.cue("grey_matter_rewire")
			.layer(SoundEvents.LEVER_CLICK, 0.25f, 1.8f)
			.layer(OmnitrixSounds.SWEEP, 0.12f, 1.8f, 1)
			.layer(SoundEvents.COPPER_BULB_TURN_OFF, 0.25f, 1.3f, 3);

	public static final int ANIM_PINCH = 1;
	public static final int ANIM_STRIKE = 2;
	public static final int ANIM_ANALYZE = 3;
	/** v0.17.2: Gizmo Turret (was the Decoy's throw). */
	public static final int ANIM_TURRET = 4;
	public static final int ANIM_LEAP = 5;
	public static final int ANIM_SCURRY = 6;
	public static final int ANIM_TRAP = 7;
	/** v0.17.2: Inside Job's dive (was Smoke Charge). */
	public static final int ANIM_DIVE = 8;
	public static final int ANIM_REPAIR = 9;
	public static final int ANIM_MIND = 10;
	public static final int ANIM_JAB = 11;
	public static final int ANIM_READ = 12;
	public static final int ANIM_WORKSHOP = 13;
	/** v0.17.2: popping out of an Inside Job with a little spin. */
	public static final int ANIM_POP = 14;
	/** v0.17.2: Rewire -- pointing a finger at the block. */
	public static final int ANIM_REWIRE = 15;

	public static final int F_LEAP = 1;
	public static final int F_SCURRY = 2;
	/** v0.17.2: on a shoulder ride, he sits on the carrier's LEFT shoulder (synced, so every client seats him alike). */
	public static final int F_LEFT_SHOULDER = 4;
	/** v0.17.2 Inside Job: he is inside a creature (the client hides him and shakes it). */
	public static final int F_INSIDE = 8;
	/** v0.17.2 Sticky Climber: clinging to a wall (the flat-to-wall pose). */
	public static final int F_CLIMB = 16;

	public static final String NERVE_PINCH = "nerve_pinch";
	public static final String WEAK_SPOT = "weak_spot_strike";
	public static final String ANALYZE = "analyze";
	/** v0.17.2 (Shift+G, was Decoy): Gizmo Turret. */
	public static final String TURRET = "gizmo_turret";
	public static final String BIG_LEAP = "big_leap";
	public static final String SCURRY = "scurry";
	public static final String SNARE = "snare_trap";
	/** v0.17.2 (Shift+Z ultimate, was Smoke Charge): Inside Job. */
	public static final String INSIDE_JOB = "inside_job";
	public static final String REPAIR_MOVE = "genius_repair";
	public static final String MIND_MAP = "mind_map";
	public static final String JAB = "precision_jab";
	public static final String QUICK_READ = "quick_read";
	/** v0.17.2 (user: "grey matter players can ride regular players by right clicking them"): {@code OmnitrixRiding}. */
	public static final String SHOULDER_RIDE = "shoulder_ride";
	public static final String WORKSHOP = "pocket_workshop";
	/** v0.17.2 (Shift+V): Rewire. */
	public static final String REWIRE = "rewire";

	/** Synced kit timers: when the Inside Job ends, and the creature he is in (entity id + 1, for the client's shake). */
	public static final String INSIDE_END = "inside_end", INSIDE_TARGET = "inside_target";
	/** v0.17.2 Swatted: his keys are dead until this game time (a big creature's hit). */
	public static final String SWAT_END = "swat_end";

	public static final float SCALE = 0.33f;
	public static final int STUN_TICKS = 60;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 1 / 12 / 2 / none
	public static final float PINCH_DAMAGE = 4f;
	public static final float STRIKE_DAMAGE = 18f;
	/** What Weak-Spot Strike lands on an Analyze-marked creature, after the mark's x1.35 (the strike is scaled down to land on 24). */
	public static final float STRIKE_MARKED_DAMAGE = 24f;
	public static final float SNARE_DAMAGE = 10f;
	public static final float MARK_FACTOR = 1.35f;
	public static final int MARK_TICKS = 200;
	public static final int SCURRY_TICKS = 100;
	public static final int TRAP_LIFE = 1200;
	public static final int TRAP_ROOT = 80;
	public static final int MAX_TRAPS = 3;
	public static final float REPAIR_FRACTION = 0.3f;
	public static final int BOOST_TICKS = 200;
	public static final int MIND_TICKS = 160;
	public static final int MIND_RANGE = 20;

	// v0.17.2 revamp numbers (docs/OMNITRIX_REVAMP_UPGRADE_GREYMATTER_XLR8.md, Grey Matter)
	/** Analyze, tuned: a marked boss closing to strike someone on his side is foreseen this far out (a grey warning ring). */
	public static final double FORESIGHT_RANGE = 3.0;
	public static final int FORESIGHT_GAP = 15;
	/** Backseat Genius: what his carrier hits is marked this long. */
	public static final int BACKSEAT_MARK_TICKS = 100;
	/** Gizmo Turret. */
	public static final double TURRET_PLACE = 6.0, TURRET_RANGE = 10.0, TURRET_TAUNT = 10.0;
	public static final int TURRET_TICKS = 200, TURRET_SHOT_GAP = 10;
	public static final float TURRET_DAMAGE = 2f, TURRET_HP = 6f;
	/** Scurry's smoke puff (what is left of Smoke Charge). */
	public static final float PUFF_RADIUS = 3f;
	public static final int PUFF_BLIND = 40;
	/** Inside Job. */
	public static final double INSIDE_RANGE = 6.0;
	public static final int INSIDE_TICKS = 60, INSIDE_PULSE = 10;
	public static final float INSIDE_DAMAGE = 6f, MACHINE_FACTOR = 1.5f;
	public static final int INSIDE_STUN = 40, SHUTDOWN_TICKS = 100;
	/** A boss machine is shut down this long (the stun helper gives a boss a quarter: 160 / 4 = 2 s). */
	public static final int SHUTDOWN_BOSS_TICKS = 160;
	/** Rewire. */
	public static final double REWIRE_RANGE = 16.0;
	public static final int REWIRE_SPAWNER_COOLDOWN = 600, SPAWNER_OFF_TICKS = 1200, POWER_TICKS = 200;
	/** Weaknesses. */
	public static final float SWAT_KNOCKBACK = 1.5f, FALLING_BLOCK_FACTOR = 2f;
	public static final int SWAT_STUN = 10;
	public static final double BIG_WEAPON_SPEED = -0.5;
	/** Passives. */
	public static final double SWIM_BONUS = 0.5;
	public static final float ANVIL_DISCOUNT = 0.3f;

	private static final ResourceLocation SCURRY_SPEED = PowerToggles.id("omnitrix_grey_matter_scurry");
	private static final ResourceLocation BIG_WEAPON = PowerToggles.id("omnitrix_grey_matter_big_weapon");
	private static final ResourceLocation SWIM = PowerToggles.id("omnitrix_grey_matter_swim");
	private static final DustParticleOptions GADGET_DUST = new DustParticleOptions(new Vector3f(0.6f, 0.65f, 0.7f), 0.8f);
	private static final DustParticleOptions LIGHT_DUST = new DustParticleOptions(new Vector3f(1f, 0.25f, 0.2f), 0.6f);
	private static final DustParticleOptions THINK_DUST = new DustParticleOptions(new Vector3f(0.5f, 0.9f, 1f), 0.7f);

	/** a marked creature -> who marked it and until when (it takes +35% damage from everyone). */
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	/** player -> their snares. */
	private static final Map<UUID, List<Trap>> TRAPS = new ConcurrentHashMap<>();
	/** v0.17.2: player -> their Gizmo Turret. */
	private static final Map<UUID, Turret> TURRETS = new ConcurrentHashMap<>();
	/** v0.17.2: player -> the Inside Job he is on. */
	private static final Map<UUID, Inside> INSIDE = new ConcurrentHashMap<>();
	/** v0.17.2: a marked boss -> when its last strike was foreseen. */
	private static final Map<UUID, Long> FORESEEN = new ConcurrentHashMap<>();
	/** v0.17.2: player -> the lamps his Rewire lit (they go dark again after 10 s). */
	private static final Map<UUID, List<Lamp>> LAMPS = new ConcurrentHashMap<>();
	/** v0.17.2: player -> the game tick his kit last ran (the once-a-tick guard in {@link #tick}). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private record Mark(UUID owner, long until) {
	}

	private record Trap(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private record Lamp(ResourceKey<Level> dim, BlockPos pos, long until) {
	}

	private static final class Turret {
		final ResourceKey<Level> dim;
		final Vec3 at;
		final long until;
		float hp = TURRET_HP;
		@Nullable
		AlienProjectileEntity model;

		Turret(ResourceKey<Level> dim, Vec3 at, long until) {
			this.dim = dim;
			this.at = at;
			this.until = until;
		}
	}

	private static final class Inside {
		final UUID target;
		final ResourceKey<Level> dim;
		final Vec3 from;
		final long start;
		final boolean machine;
		final boolean boss;
		Vec3 last;
		float width;

		Inside(LivingEntity t, Vec3 from, long start) {
			this.target = t.getUUID();
			this.dim = t.level().dimension();
			this.from = from;
			this.start = start;
			this.machine = machine(t);
			this.boss = AlienStatus.isBoss(t);
			this.last = t.position();
			this.width = t.getBbWidth();
		}
	}

	private GreyMatterKit() {
	}

	public static void register() {
		Aliens.register(new GreyMatterKit());
		// Analyze's mark: a marked creature takes more from every source (re-issued behind a guard, the OmnitrixDamage pattern)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (REENTRANT.get() || amount <= 0f || MARKS.isEmpty() || entity.level().isClientSide()) {
				return true;
			}
			Mark m = MARKS.get(entity.getUUID());
			if (m == null) {
				return true;
			}
			if (m.until() <= entity.level().getGameTime()) {
				MARKS.remove(entity.getUUID());
				return true;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			float more = amount * MARK_FACTOR;
			if (entity instanceof Player && source.scalesWithDifficulty()) {
				// a player's hurt scales by difficulty again on the re-issue: undo it (the OmnitrixDamage correction)
				more = switch (entity.level().getDifficulty()) {
					case HARD -> more / 1.5f;
					case EASY -> more >= 2f ? 2f * (more - 1f) : more;
					default -> more;
				};
			}
			REENTRANT.set(true);
			try {
				entity.hurt(source, more);
			} finally {
				REENTRANT.set(false);
			}
			return false;
		});
		// v0.17.2 Backseat Genius: riding a carrier's shoulder, he reads whatever they hit -- marked for 5 s, no cooldown
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0f && source.getEntity() instanceof ServerPlayer carrier && carrier != entity && !carrier.getPassengers().isEmpty()) {
				backseat(carrier, entity);
			}
		});
		// v0.17.2 Inside Job: out of the creature BEFORE the player list drops him -- vanilla saves (and removes) a mount with a
		// lone player passenger on logout, and that mount is somebody's zombie here
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (p != null && INSIDE.containsKey(p.getUUID())) {
				endInside(p, false);
			}
		});
	}

	public static void clearSessionState() {
		MARKS.clear();
		TRAPS.clear();
		TURRETS.clear();
		INSIDE.clear();
		FORESEEN.clear();
		LAMPS.clear();
		LAST_TICK.clear();
	}

	/** Whether Analyze has {@code e} marked right now. */
	public static boolean marked(LivingEntity e) {
		Mark m = MARKS.get(e.getUUID());
		return m != null && m.until() > e.level().getGameTime();
	}

	/** Marks {@code t} for {@code ticks} (glowing, +35% from everyone) in {@code owner}'s name; a longer mark already on it stays. */
	public static void mark(ServerPlayer owner, LivingEntity t, int ticks) {
		long until = t.level().getGameTime() + ticks;
		Mark old = MARKS.get(t.getUUID());
		if (old != null && old.until() >= until) {
			return;
		}
		MARKS.put(t.getUUID(), new Mark(owner.getUUID(), until));
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false), owner);
	}

	public static int traps(ServerPlayer p) {
		List<Trap> t = TRAPS.get(p.getUUID());
		return t == null ? 0 : t.size();
	}

	/** v0.17.2: the HP his Gizmo Turret has left (0 = none standing). */
	public static float turretHp(ServerPlayer p) {
		Turret t = TURRETS.get(p.getUUID());
		return t == null ? 0f : t.hp;
	}

	/** v0.17.2: his Gizmo Turret's model entity, or null. */
	@Nullable
	public static AlienProjectileEntity turretModel(ServerPlayer p) {
		Turret t = TURRETS.get(p.getUUID());
		return t == null ? null : t.model;
	}

	/** v0.17.2: is he inside a creature (Inside Job) right now? Server side. */
	public static boolean inside(ServerPlayer p) {
		return INSIDE.containsKey(p.getUUID());
	}

	/** v0.17.2: is {@code p} Grey Matter inside a creature? Client-safe (the synced flag). */
	public static boolean insideNow(Player p) {
		return AlienKit.activeKit(p) instanceof GreyMatterKit k && k.flag(p, F_INSIDE);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "i dont want any of the aliens to have less than 20 hp" / "basically all of them should have" melee bonuses): was 8 health, +0 fist
		return AlienStats.builder().health(20f).speed(1.35f).scale(SCALE).attack(2f).jump(0.05f).safeFall(1f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(NERVE_PINCH, AbilitySlot.SLOT_1, 120), AlienMove.shift(WEAK_SPOT, AbilitySlot.SLOT_1, 100),
				AlienMove.tap(ANALYZE, AbilitySlot.SLOT_2, 200), AlienMove.shift(TURRET, AbilitySlot.SLOT_2, 300), // v0.17.2: Gizmo Turret 15 s (was Decoy)
				AlienMove.tap(BIG_LEAP, AbilitySlot.SLOT_3, 30), AlienMove.shift(SCURRY, AbilitySlot.SLOT_3, 240),
				AlienMove.tap(SNARE, AbilitySlot.SLOT_4, 160), AlienMove.shift(INSIDE_JOB, AbilitySlot.SLOT_4, 600), // v0.17.2: Inside Job 30 s (was Smoke Charge)
				AlienMove.tap(WORKSHOP, AbilitySlot.SLOT_5, 20), AlienMove.shift(REWIRE, AbilitySlot.SLOT_5, 100), // v0.17.2: Rewire 5 s (spawners 30 s)
				AlienMove.tap(REPAIR_MOVE, AbilitySlot.SLOT_6, 600), AlienMove.shift(MIND_MAP, AbilitySlot.SLOT_6, 900),
				AlienMove.vanilla(JAB, "Attack", 0), AlienMove.vanilla(QUICK_READ, "Use on a creature", 10),
				AlienMove.vanilla(SHOULDER_RIDE, "Use on a player", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), THINK, 1.0f, 1.4f);
		l.sendParticles(THINK_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.2, 0.2, 0.2, 0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		// v0.17.2 Inside Job: always back outside, safely, before anything else is undone
		if (INSIDE.containsKey(p.getUUID())) {
			endInside(p, false);
		}
		OmnitrixRiding.endRides(p); // v0.17.2: off the shoulder
		setFlag(p, F_LEFT_SHOULDER, false);
		UUID me = p.getUUID();
		MARKS.values().removeIf(m -> m.owner().equals(me));
		TRAPS.remove(me);
		Turret t = TURRETS.remove(me);
		if (t != null && t.model != null) {
			t.model.discard();
		}
		List<Lamp> lamps = LAMPS.remove(me);
		if (lamps != null) {
			for (Lamp lamp : lamps) {
				unlight(p.server.getLevel(lamp.dim()), lamp.pos());
			}
		}
		LAST_TICK.remove(me);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SCURRY_SPEED);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, BIG_WEAPON);
		PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM);
		MobEffectInstance wb = p.getEffect(MobEffects.WATER_BREATHING);
		if (wb != null && !wb.isVisible() && wb.getDuration() <= 60) {
			p.removeEffect(MobEffects.WATER_BREATHING); // only the Amphibian's own short, hidden one
		}
		setFlag(p, F_LEAP | F_SCURRY | F_INSIDE | F_CLIMB, false);
		if (timer(p, "scurry_end") > 0) {
			setTimer(p, "scurry_end", 0);
		}
		if (timer(p, SWAT_END) > 0) {
			setTimer(p, SWAT_END, 0);
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		// v0.17.2: once per game tick -- the timed pulses (Inside Job, the turret's darts) must not double up if he is ticked twice
		// in a tick (a gametest's mock player is ticked by the server and by the test's own pump)
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		tickV(p, l, now);
		tickInside(p, l, now);
		tickPassives(p, l, now);
		// so small mobs keep losing sight of him
		if (now % 20 == 0) {
			for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) {
				if (m.distanceToSqr(p) > 2.5 * 2.5 && l.random.nextFloat() < 0.3f && AlienStatus.loseTrack(m, p)) {
					l.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getEyeY() + 0.4, m.getZ(), 3, 0.1, 0.1, 0.1, 0);
				}
			}
		}
		if (flag(p, F_LEAP) && (p.onGround() || p.isInWaterOrBubble())) {
			setFlag(p, F_LEAP, false);
			p.resetFallDistance();
		}
		long sc = timer(p, "scurry_end");
		if (sc > 0) {
			if (now >= sc) {
				setTimer(p, "scurry_end", 0);
				setFlag(p, F_SCURRY, false);
				PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SCURRY_SPEED);
			} else if (now % 6 == 0) {
				l.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.05, p.getZ(), 1, 0.05, 0, 0.05, 0);
			}
		}
		// the snares
		List<Trap> traps = TRAPS.get(p.getUUID());
		if (traps != null && !traps.isEmpty()) {
			for (Trap t : traps) {
				if (now >= t.until() || t.dim() != l.dimension()) {
					traps.remove(t);
					continue;
				}
				if (now % 10 == 0) {
					l.sendParticles((now / 10) % 2 == 0 ? LIGHT_DUST : GADGET_DUST, t.at().x, t.at().y + 0.15, t.at().z, 1, 0, 0, 0, 0);
				}
				if (now % 2 == 0) {
					AABB box = new AABB(t.at(), t.at()).inflate(0.6, 0.1, 0.6).expandTowards(0, 0.8, 0);
					for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator()
							&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !com.projecthero.mod.squad.Squads.areAllies(p, e))) {
						spring(p, l, t, e);
						traps.remove(t);
						break;
					}
				}
			}
		}
		tickTurret(p, l, now);
		tickForesight(p, l, now);
		tickLamps(p, now);
	}

	private void spring(ServerPlayer p, ServerLevel l, Trap t, LivingEntity e) {
		AlienStatus.root(p, e, TRAP_ROOT, 40);
		hurt(p, e, SNARE_DAMAGE, false); // v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 2
		QuirkFx.ring(l, t.at().add(0, 0.05, 0)).colours(0xFFFFFF, 0x8A9AA8).size(1.4f).thickness(0.25f).life(8).ground().spawn();
		QuirkFx.spike(l, t.at(), new Vec3(0, 1, 0)).block(Blocks.TRIPWIRE_HOOK.defaultBlockState()).colours(0xA0A8B0, 0x606870).size(1.0f).size2(0.15f)
				.life(TRAP_ROOT).spawn();
		l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.3, e.getZ(), 12, 0.3, 0.2, 0.3, 0.2);
		OmnitrixSounds.play(l, t.at(), GADGET, 1.2f, 0.7f);
		l.playSound(null, t.at().x, t.at().y, t.at().z, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1f, 1f);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (INSIDE.containsKey(p.getUUID())) {
			return; // v0.17.2: busy inside a creature
		}
		if (timer(p, SWAT_END) > p.level().getGameTime()) {
			return; // v0.17.2 Swatted: still seeing stars
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					weakSpot(p);
				} else {
					nervePinch(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					gizmoTurret(p);
				} else {
					analyze(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					scurry(p);
				} else {
					bigLeap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					insideJob(p);
				} else {
					snare(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					mindMap(p);
				} else {
					geniusRepair(p);
				}
			}
			default -> {
			}
		}
	}

	@Nullable
	private LivingEntity closeTarget(ServerPlayer p, double range) {
		LivingEntity t = lookTarget(p, range);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.no_target").withStyle(ChatFormatting.GRAY), true);
		}
		return t;
	}

	public boolean nervePinch(ServerPlayer p) {
		LivingEntity t = closeTarget(p, 3.0);
		if (t == null || !use(p, NERVE_PINCH)) {
			return false;
		}
		anim(p, ANIM_PINCH);
		ServerLevel l = level(p);
		AlienStatus.stun(p, t, STUN_TICKS);
		hurt(p, t, PINCH_DAMAGE, false); // v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 1
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.85, t.getZ(), 14, 0.2, 0.15, 0.2, 0.05);
		QuirkFx.ring(l, t.position().add(0, t.getBbHeight() + 0.3, 0)).colours(0xFFFFFF, 0xF0E060).size(0.7f).thickness(0.12f).life(STUN_TICKS / 2).spawn();
		OmnitrixSounds.play(l, t.position(), PINCH, 1.0f, 1.0f);
		return true;
	}

	public boolean weakSpot(ServerPlayer p) {
		LivingEntity t = closeTarget(p, 3.25);
		if (t == null || !use(p, WEAK_SPOT)) {
			return false;
		}
		anim(p, ANIM_STRIKE);
		ServerLevel l = level(p);
		// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 12 (16.2 marked)
		// -- now 18, and 24 on a marked creature (the mark multiplies every hit by MARK_FACTOR, so the strike is sent as 24 / 1.35)
		hurt(p, t, strikeDamage(t), false);
		l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 20, 0.3, 0.3, 0.3, 0.4);
		l.sendParticles(ParticleTypes.ENCHANTED_HIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
		QuirkFx.blast(l, t.position().add(0, t.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, 0xC0D8FF).size(0.8f).life(6)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		OmnitrixSounds.play(l, t.position(), PINCH, 1.2f, 0.6f);
		return true;
	}

	/** The damage Weak-Spot Strike sends: 18, or on a marked creature what comes to 24 once the mark's x1.35 is applied. */
	public static float strikeDamage(LivingEntity t) {
		return marked(t) ? STRIKE_MARKED_DAMAGE / MARK_FACTOR : STRIKE_DAMAGE;
	}

	/** G: reads the creature you look at (24 blocks) and marks it: glowing, +35% damage from everyone for 10 s. */
	public boolean analyze(ServerPlayer p) {
		LivingEntity t = closeTarget(p, 24.0);
		if (t == null || !use(p, ANALYZE)) {
			return false;
		}
		anim(p, ANIM_ANALYZE);
		ServerLevel l = level(p);
		MARKS.remove(t.getUUID()); // a fresh 10 s, whoever marked it before
		mark(p, t, MARK_TICKS);
		QuirkThreat.add(t, p, 2f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.analysis", t.getDisplayName(),
				String.format(java.util.Locale.ROOT, "%.0f", t.getHealth()), String.format(java.util.Locale.ROOT, "%.0f", t.getMaxHealth()), t.getArmorValue(),
				Component.translatable("message.projecthero.omnitrix.grey_matter.weak." + weakness(t))).withStyle(ChatFormatting.AQUA), false);
		p.displayClientMessage(Component.translatable(AlienStatus.isBoss(t) ? "message.projecthero.omnitrix.grey_matter.marked_boss"
				: "message.projecthero.omnitrix.grey_matter.marked").withStyle(ChatFormatting.AQUA), true);
		Vec3 eye = p.getEyePosition();
		QuirkFx.ribbon(l, eye, t.position().add(0, t.getBbHeight() * 0.5, 0)).colours(0xE0F8FF, 0x5AB8F0).size(0.08f).life(10).spawn();
		QuirkFx.ring(l, t.position().add(0, 0.05, 0)).colours(0xE0F8FF, 0x5AB8F0).size(Math.max(1f, t.getBbWidth() * 1.4f)).thickness(0.12f).life(30).ground()
				.spawn();
		l.sendParticles(THINK_DUST, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 8, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4,
				t.getBbWidth() * 0.5, 0);
		OmnitrixSounds.play(l, p.position(), SCAN, 1.0f, 1.0f);
		return true;
	}

	/** The analysis' weakness line (lang {@code message.projecthero.omnitrix.grey_matter.weak.<id>}). */
	public static String weakness(LivingEntity t) {
		if (t instanceof EnderMan) {
			return "water";
		}
		if (machine(t)) {
			return "machine"; // v0.17.2: Inside Job rewires it
		}
		if (t.getType().is(EntityTypeTags.UNDEAD)) {
			return "undead";
		}
		if (t.getType().is(EntityTypeTags.ARTHROPOD)) {
			return "arthropod";
		}
		if (t.getType().is(EntityTypeTags.AQUATIC)) {
			return "aquatic";
		}
		if (t.fireImmune()) {
			return "fireproof";
		}
		if (t.getType().is(EntityTypeTags.ILLAGER)) {
			return "illager";
		}
		if (AlienStatus.isBoss(t)) {
			return "boss";
		}
		return "general";
	}

	/**
	 * v0.17.2 Analyze, tuned (the doc: "a marked boss's attacks telegraph 0.5 s earlier"): a boss he marked, closing to strike him
	 * or a squadmate, is foreseen -- a grey ring under it and a grey line to whoever it is about to hit, with a soft click, before
	 * the blow lands. There is no shared boss wind-up to hook, so the warning comes from the boss's own approach (its target
	 * within {@value #FORESIGHT_RANGE} blocks of its reach), at most every {@value #FORESIGHT_GAP} ticks.
	 */
	private void tickForesight(ServerPlayer p, ServerLevel l, long now) {
		if (MARKS.isEmpty() || now % 2 != 0) {
			return;
		}
		UUID me = p.getUUID();
		for (Map.Entry<UUID, Mark> en : MARKS.entrySet()) {
			Mark m = en.getValue();
			if (!m.owner().equals(me) || m.until() <= now || !(l.getEntity(en.getKey()) instanceof Mob boss) || !boss.isAlive() || !AlienStatus.isBoss(boss)) {
				continue;
			}
			LivingEntity target = boss.getTarget();
			if (target == null || !target.isAlive() || !(target == p || target instanceof Player tp && com.projecthero.mod.squad.Squads.areAllies(p, tp))) {
				continue;
			}
			double gap = boss.distanceTo(target) - (boss.getBbWidth() + target.getBbWidth()) * 0.5;
			Long last = FORESEEN.get(boss.getUUID());
			if (gap > FORESIGHT_RANGE + 1.0 || last != null && now - last < FORESIGHT_GAP) {
				continue;
			}
			FORESEEN.put(boss.getUUID(), now);
			QuirkFx.ring(l, boss.position().add(0, 0.06, 0)).colours(0xE8ECF0, COLOUR).size(Math.max(1.4f, boss.getBbWidth() * 1.3f)).thickness(0.14f).life(10)
					.ground().spawn();
			QuirkFx.ribbon(l, boss.position().add(0, boss.getBbHeight() * 0.6, 0), target.position().add(0, target.getBbHeight() * 0.5, 0))
					.colours(0xE8ECF0, COLOUR).size(0.04f).life(8).spawn();
			OmnitrixSounds.play(l, target.position(), SCAN, 0.6f, 0.8f);
		}
	}

	/** v0.17.2 Backseat Genius: {@code carrier} hit {@code victim} -- a Grey Matter on their shoulder marks it for 5 s. */
	private static void backseat(ServerPlayer carrier, LivingEntity victim) {
		for (Entity e : carrier.getPassengers()) {
			if (e instanceof ServerPlayer gm && e != victim && OmnitrixRiding.onShoulder(gm) && victim.isAlive()) {
				boolean fresh = !marked(victim);
				mark(gm, victim, BACKSEAT_MARK_TICKS);
				if (fresh) {
					ServerLevel l = level(gm);
					QuirkFx.ring(l, victim.position().add(0, 0.05, 0)).colours(0xE0F8FF, 0x5AB8F0).size(Math.max(1f, victim.getBbWidth() * 1.3f)).thickness(0.1f)
							.life(16).ground().spawn();
					OmnitrixSounds.play(l, gm.position(), SCAN, 0.5f, 1.4f);
				}
				return;
			}
		}
	}

	// ---------------------------------------------------------------- Shift+G Gizmo Turret

	/**
	 * Shift+G (v0.17.2, the Decoy folded in): builds a fist-sized turret from pocket scraps where you look (6 blocks). It beeps and
	 * blinks, so mobs within 10 go after it, and it darts the nearest foe (the creature you analysed first) for 2, twice a second,
	 * for 10 s. 6 HP: what reaches it can break it.
	 */
	public boolean gizmoTurret(ServerPlayer p) {
		if (!use(p, TURRET)) {
			return false;
		}
		anim(p, ANIM_TURRET);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(TURRET_PLACE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.3)) : eye.add(look.scale(TURRET_PLACE));
		at = ground(l, at);
		Turret old = TURRETS.remove(p.getUUID());
		if (old != null && old.model != null) {
			old.model.discard();
		}
		long now = l.getGameTime();
		Turret t = new Turret(l.dimension(), at, now + TURRET_TICKS);
		AlienProjectileEntity model = AlienProjectileEntity.of(p, "grey_matter_turret").colour(COLOUR).size(0.5f).life(TURRET_TICKS + 40).held(true);
		model.moveTo(at.x, at.y, at.z, p.getYRot(), 0f);
		model.direction(new Vec3(0, 1, 0));
		model.speed(0.0);
		model.spawn();
		model.setDeltaMovement(Vec3.ZERO);
		t.model = model;
		TURRETS.put(p.getUUID(), t);
		QuirkFx.ribbon(l, p.position().add(0, p.getBbHeight() * 0.6, 0), at.add(0, 0.2, 0)).colours(0xFFFFFF, 0x8A9AA8).size(0.04f).life(6).spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0xF04030).size(1.2f).thickness(0.12f).life(12).ground().spawn();
		OmnitrixSounds.play(l, at, GADGET, 1.0f, 1.3f);
		return true;
	}

	private static Vec3 ground(ServerLevel l, Vec3 at) {
		var down = l.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return down.getType() == HitResult.Type.BLOCK ? down.getLocation() : at;
	}

	private void tickTurret(ServerPlayer p, ServerLevel l, long now) {
		Turret t = TURRETS.get(p.getUUID());
		if (t == null) {
			return;
		}
		if (now >= t.until || t.dim != l.dimension() || t.hp <= 0f || t.model == null || t.model.isRemoved()) {
			breakTurret(p, l, t, t.hp <= 0f);
			return;
		}
		Vec3 top = t.at.add(0, 0.3, 0);
		if (now % 10 == 0) {
			OmnitrixSounds.play(l, t.at, DECOY_SOUND, 1.0f, (now / 10) % 2 == 0 ? 1.15f : 0.9f);
		}
		// the lure (the old Decoy): what hunts him, and anything hostile with nothing better to do, goes for the beeping box
		if (now % 5 == 0) {
			for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(t.at, t.at).inflate(TURRET_TAUNT),
					m -> m.isAlive() && !AlienStatus.isBoss(m) && (m instanceof Enemy || m.getTarget() == p) && !OmnitrixRiding.partners(p, m)
							&& !com.projecthero.mod.squad.Squads.areAllies(p, m))) {
				if (m.distanceToSqr(t.at) > TURRET_TAUNT * TURRET_TAUNT) {
					continue;
				}
				if (m.getTarget() == p) {
					AlienStatus.loseTrack(m, p);
				}
				if (m.getTarget() == null) {
					if (m.distanceToSqr(t.at) > 1.6 * 1.6) {
						m.getNavigation().moveTo(t.at.x, t.at.y, t.at.z, 1.1);
					} else if (now % 20 == 0) {
						// it reached the turret: it bashes it
						m.swing(InteractionHand.MAIN_HAND);
						double dmg = m.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? m.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2.0;
						t.hp -= (float) Math.max(1.0, dmg);
						l.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 4, 0.1, 0.1, 0.1, 0.05);
						l.playSound(null, top.x, top.y, top.z, SoundEvents.COPPER_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
					}
					m.getLookControl().setLookAt(t.at);
				}
			}
		}
		// the darts
		if ((now - (t.until - TURRET_TICKS)) % TURRET_SHOT_GAP == 0) {
			LivingEntity foe = turretTarget(p, l, top);
			if (foe != null) {
				Vec3 aim = foe.position().add(0, foe.getBbHeight() * 0.55, 0);
				Vec3 d = aim.subtract(top);
				t.model.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
				hurt(p, foe, TURRET_DAMAGE, false);
				QuirkFx.ribbon(l, top, aim).colours(0xFFFFFF, 0xB0B8C0).size(0.025f).life(3).spawn();
				OmnitrixSounds.play(l, top, DART, 1.0f, 1.0f);
			}
		}
	}

	/** The turret's target: the creature he analysed if it is in range and in sight, else the nearest foe in range and in sight. */
	@Nullable
	private static LivingEntity turretTarget(ServerPlayer p, ServerLevel l, Vec3 top) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		boolean bestMarked = false;
		for (LivingEntity e : around(p, top, TURRET_RANGE)) {
			if (e.distanceToSqr(top) > TURRET_RANGE * TURRET_RANGE || e instanceof Player) {
				continue;
			}
			Mark mk = MARKS.get(e.getUUID());
			boolean mine = mk != null && mk.owner().equals(p.getUUID()) && mk.until() > l.getGameTime();
			boolean foe = mine || e instanceof Enemy || e instanceof Mob m && m.getTarget() == p;
			if (!foe) {
				continue;
			}
			Vec3 aim = e.position().add(0, e.getBbHeight() * 0.55, 0);
			if (l.clip(new ClipContext(top, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))
					.getType() == HitResult.Type.BLOCK) {
				continue;
			}
			double dd = e.distanceToSqr(top);
			if (mine && !bestMarked || mine == bestMarked && dd < bestD) {
				best = e;
				bestD = dd;
				bestMarked = mine;
			}
		}
		return best;
	}

	private void breakTurret(ServerPlayer p, ServerLevel l, Turret t, boolean smashed) {
		TURRETS.remove(p.getUUID());
		if (t.model != null) {
			t.model.discard();
		}
		if (t.dim == l.dimension()) {
			QuirkFx.blast(l, t.at.add(0, 0.25, 0)).colours(0xFFFFFF, 0x8A9AA8).size(smashed ? 0.7f : 0.45f).life(6).flags(QuirkFx.F_NO_RING).spawn();
			l.sendParticles(ParticleTypes.POOF, t.at.x, t.at.y + 0.2, t.at.z, 4, 0.1, 0.1, 0.1, 0.02);
			OmnitrixSounds.play(l, t.at, GADGET, 0.7f, smashed ? 0.6f : 0.9f);
		}
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** v0.17.2: Big Leap / Scurry from a shoulder ride -- he gets off where he sits (not set down beside them) and goes. */
	private static void hopOff(ServerPlayer p) {
		if (p.getVehicle() instanceof Player) {
			Vec3 at = p.position();
			p.stopRiding();
			p.teleportTo(at.x, at.y, at.z);
		}
	}

	public boolean bigLeap(ServerPlayer p) {
		if (!use(p, BIG_LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		hopOff(p); // v0.17.2: a leap off the shoulder he rides
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z).normalize();
		p.setDeltaMovement(f.x * 1.05, 0.85 + Math.max(0, look.y) * 0.4, f.z * 1.05);
		p.hurtMarked = true;
		p.resetFallDistance();
		setFlag(p, F_LEAP, true);
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.05, p.getZ(), 4, 0.1, 0, 0.1, 0.02);
		OmnitrixSounds.play(l, p.position(), LEAP, 1.0f, 1.0f);
		return true;
	}

	/** Shift+X: 5 s of top speed, hunters lose you -- v0.17.2: it starts with a smoke puff (r 3, blind 2 s, no damage). */
	public boolean scurry(ServerPlayer p) {
		if (!use(p, SCURRY)) {
			return false;
		}
		anim(p, ANIM_SCURRY);
		hopOff(p); // v0.17.2: off the shoulder to run
		ServerLevel l = level(p);
		setTimer(p, "scurry_end", l.getGameTime() + SCURRY_TICKS);
		setFlag(p, F_SCURRY, true);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SCURRY_SPEED, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// v0.17.2 (user approved "Smoke Charge drops to a puff on Scurry"): what is left of Smoke Charge -- a rendered puff, no damage
		Vec3 c = p.position().add(0, 0.3, 0);
		QuirkFx.flipbook(l, c.add(0, 0.4, 0)).style(QuirkFx.BOOK_SMOKE).colours(0xD0D4D8, 0x707478).size(PUFF_RADIUS * 0.8f).life(24).spawn();
		l.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 6, 0.6, 0.2, 0.6, 0.02);
		for (LivingEntity e : around(p, c, PUFF_RADIUS)) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, PUFF_BLIND, 0), p);
		}
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
			AlienStatus.loseTrack(m, p);
		}
		OmnitrixSounds.play(l, p.position(), LEAP, 0.8f, 1.6f);
		l.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 1.8f);
		return true;
	}

	/** Z: quick-crafts a tripwire snare on the ground just ahead (at most three; a fourth replaces the oldest). */
	public boolean snare(ServerPlayer p) {
		if (!use(p, SNARE)) {
			return false;
		}
		anim(p, ANIM_TRAP);
		ServerLevel l = level(p);
		Vec3 f = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
		Vec3 at = ground(l, p.position().add(f.scale(1.2)).add(0, 0.6, 0));
		List<Trap> list = TRAPS.computeIfAbsent(p.getUUID(), k -> new CopyOnWriteArrayList<>());
		while (list.size() >= MAX_TRAPS) {
			list.remove(0);
		}
		list.add(new Trap(l.dimension(), at, l.getGameTime() + TRAP_LIFE));
		QuirkFx.decal(l, at.add(0, 0.03, 0)).style(QuirkFx.DECAL_PRINT).colour(0x50585E).size(0.9f).life(TRAP_LIFE).spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0x8A9AA8).size(0.9f).thickness(0.12f).life(10).ground().spawn();
		l.sendParticles(GADGET_DUST, at.x, at.y + 0.2, at.z, 6, 0.2, 0.1, 0.2, 0);
		OmnitrixSounds.play(l, at, GADGET, 1.0f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+Z Inside Job

	/**
	 * Whether {@code e} is a machine to Inside Job (it is rewired, not thrashed): iron golems, the Sentinels, Ultron's robots, and
	 * any mod creature whose id names it a robot, drone, sentinel, sentry or mech.
	 */
	public static boolean machine(LivingEntity e) {
		if (e instanceof IronGolem || e instanceof com.projecthero.mod.sentinel.entity.SentinelRobot || e instanceof com.projecthero.mod.ultron.entity.UltronRobot) {
			return true;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return path.contains("robot") || path.contains("drone") || path.contains("sentinel") || path.contains("ultron") || path.contains("sentry")
				|| path.contains("mech");
	}

	/** One Inside Job pulse's damage on {@code t} (before armour, which it ignores): 6, a machine 9. */
	public static float insidePulse(LivingEntity t) {
		return machine(t) ? INSIDE_DAMAGE * MACHINE_FACTOR : INSIDE_DAMAGE;
	}

	/**
	 * Shift+Z ULTIMATE (v0.17.2, replaces Smoke Charge; the show's "crawling inside machines to rewire them"): leap at a creature
	 * within 6 and climb inside (ear, vent, armour seam). For 3 s he is gone and it thrashes -- 6 every 0.5 s that ignores armour --
	 * then he pops out of the far side and it is stunned 2 s. A machine is rewired instead: x1.5 damage and shut down 5 s (a boss
	 * 2 s). Creatures only, never players (v0.17.2 default for the doc's open question GM1). He rides inside it ({@code
	 * startRiding}), invisible and untouchable, and always comes back out safely: when it dies or vanishes, when he turns back, when
	 * he logs out ({@link #register}'s disconnect hook) or gets off.
	 */
	public boolean insideJob(ServerPlayer p) {
		if (INSIDE.containsKey(p.getUUID())) {
			return false;
		}
		LivingEntity t = closeTarget(p, INSIDE_RANGE);
		if (t == null) {
			return false;
		}
		if (!(t instanceof Mob) || !t.isAlive()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.not_a_creature").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, INSIDE_JOB)) {
			return false;
		}
		ServerLevel l = level(p);
		Vec3 from = p.position();
		hopOff(p); // off a shoulder first
		if (p.isPassenger()) {
			p.stopRiding();
		}
		if (!p.startRiding(t, true)) {
			cooldown(p, INSIDE_JOB, 0); // could not get in: no cooldown spent
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.not_a_creature").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		long now = l.getGameTime();
		Inside in = new Inside(t, from, now);
		INSIDE.put(p.getUUID(), in);
		anim(p, ANIM_DIVE);
		setFlag(p, F_INSIDE, true);
		setTimer(p, INSIDE_END, now + INSIDE_TICKS);
		setTimer(p, INSIDE_TARGET, t.getId() + 1L);
		p.setInvisible(true);
		p.resetFallDistance();
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
			AlienStatus.loseTrack(m, p);
		}
		QuirkThreat.add(t, p, 4f);
		QuirkFx.ribbon(l, from.add(0, 0.3, 0), t.position().add(0, t.getBbHeight() * 0.6, 0)).colours(0xE8ECF0, COLOUR).size(0.05f).life(5).spawn();
		if (in.machine) {
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
		}
		OmnitrixSounds.play(l, t.position(), SQUEEZE, 1.0f, 1.0f);
		p.displayClientMessage(Component.translatable(in.machine ? "message.projecthero.omnitrix.grey_matter.inside_machine"
				: "message.projecthero.omnitrix.grey_matter.inside", t.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	private void tickInside(ServerPlayer p, ServerLevel l, long now) {
		Inside in = INSIDE.get(p.getUUID());
		if (in == null) {
			if (flag(p, F_INSIDE)) {
				setFlag(p, F_INSIDE, false); // a stale flag (a session reset mid-job)
				p.setInvisible(p.hasEffect(MobEffects.INVISIBILITY));
			}
			return;
		}
		Entity e = l.dimension() == in.dim ? l.getEntity(in.target) : null;
		if (!(e instanceof LivingEntity t) || !t.isAlive() || t.isRemoved() || p.getVehicle() != t) {
			endInside(p, false); // it died, vanished, or he was pulled out: out where it last stood
			return;
		}
		in.last = t.position();
		in.width = t.getBbWidth();
		p.resetFallDistance();
		long since = now - in.start;
		if (since > 0 && since % INSIDE_PULSE == 0 && since <= INSIDE_TICKS) {
			pulse(p, l, t, in);
			if (!t.isAlive()) {
				endInside(p, false);
				return;
			}
		}
		if (since >= INSIDE_TICKS) {
			endInside(p, true);
		}
	}

	/** One thrash: 6 (a machine 9) through all of its armour, a shudder and a grey glint at a seam. */
	private void pulse(ServerPlayer p, ServerLevel l, LivingEntity t, Inside in) {
		float dmg = in.machine ? INSIDE_DAMAGE * MACHINE_FACTOR : INSIDE_DAMAGE;
		t.invulnerableTime = 0;
		// not AlienKit.hurt: he rides it, and hurt() never touches your own mount
		t.hurt(OmnitrixDamage.alienStrike(p), throughArmour(p, t, dmg));
		if (!in.boss) {
			double a = l.random.nextDouble() * Math.PI * 2;
			t.push(Math.cos(a) * 0.12, 0.08, Math.sin(a) * 0.12);
			t.hurtMarked = true;
		}
		double a = l.random.nextDouble() * Math.PI * 2;
		Vec3 seam = t.position().add(Math.cos(a) * t.getBbWidth() * 0.52, t.getBbHeight() * (0.45 + l.random.nextDouble() * 0.4), Math.sin(a) * t.getBbWidth() * 0.52);
		QuirkFx.blast(l, seam).colours(0xFFFFFF, COLOUR).size(0.22f).life(4).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		OmnitrixSounds.play(l, t.position(), in.machine ? REWIRE_SOUND : PINCH, 0.8f, 0.7f + l.random.nextFloat() * 0.3f);
	}

	/**
	 * What to send so that {@code amount} lands after {@code t}'s armour and toughness -- the armour ignored. Vanilla's armour curve
	 * is not linear in the damage, so it is solved (bisection) rather than scaled.
	 */
	public static float throughArmour(ServerPlayer p, LivingEntity t, float amount) {
		float armour = t.getArmorValue();
		if (armour <= 0f || amount <= 0f) {
			return amount;
		}
		float tough = (float) t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		DamageSource src = OmnitrixDamage.alienStrike(p);
		float lo = amount, hi = amount * 6f;
		for (int i = 0; i < 32; i++) {
			float mid = (lo + hi) * 0.5f;
			if (net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(t, mid, src, armour, tough) < amount) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return hi;
	}

	/**
	 * Out of the Inside Job, always somewhere safe: the far side of the creature (from where he went in), else the near side,
	 * else where it stands, else where he jumped from. {@code finished} = the full 3 s: the creature is stunned (a machine shut
	 * down).
	 */
	private static void endInside(ServerPlayer p, boolean finished) {
		Inside in = INSIDE.remove(p.getUUID());
		GreyMatterKit k = (GreyMatterKit) Aliens.kit(ID);
		ServerLevel l = level(p);
		Entity e = in != null && l.dimension() == in.dim ? l.getEntity(in.target) : null;
		LivingEntity t = e instanceof LivingEntity le && le.isAlive() ? le : null;
		if (p.getVehicle() != null && (in == null || p.getVehicle().getUUID().equals(in.target))) {
			p.stopRiding();
		}
		p.setInvisible(p.hasEffect(MobEffects.INVISIBILITY));
		if (k != null) {
			k.setFlag(p, F_INSIDE, false);
			k.setTimer(p, INSIDE_END, 0);
			k.setTimer(p, INSIDE_TARGET, 0);
		}
		p.resetFallDistance();
		if (in == null || l.dimension() != in.dim) {
			return;
		}
		Vec3 base = t != null ? t.position() : in.last;
		float half = (t != null ? t.getBbWidth() : in.width) * 0.5f + 0.4f;
		Vec3 dir = new Vec3(base.x - in.from.x, 0, base.z - in.from.z);
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z) : dir;
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : dir.normalize();
		Vec3 out = null;
		Vec3[] spots = { base.add(dir.scale(half)), base.add(dir.scale(half)).add(0, 0.5, 0), base.subtract(dir.scale(half)), base, base.add(0, 0.5, 0),
				in.from };
		// a spot a full-size player fits first (he may be turning back right now), then one he fits
		for (net.minecraft.world.entity.EntityDimensions dims : new net.minecraft.world.entity.EntityDimensions[] { Player.STANDING_DIMENSIONS,
				p.getDimensions(Pose.STANDING) }) {
			for (Vec3 c : spots) {
				if (out == null && l.noCollision(p, dims.makeBoundingBox(c))) {
					out = c;
				}
			}
		}
		if (out == null) {
			out = in.from;
		}
		p.teleportTo(out.x, out.y, out.z);
		p.setDeltaMovement(dir.x * 0.25, 0.3, dir.z * 0.25);
		p.hurtMarked = true;
		if (k != null) {
			k.anim(p, ANIM_POP);
		}
		if (finished && t != null) {
			if (in.machine) {
				AlienStatus.stun(p, t, in.boss ? SHUTDOWN_BOSS_TICKS : SHUTDOWN_TICKS);
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
				QuirkFx.ring(l, t.position().add(0, t.getBbHeight() + 0.3, 0)).colours(0xE8ECF0, 0x5AB8F0).size(0.8f).thickness(0.1f)
						.life(in.boss ? 40 : SHUTDOWN_TICKS).spawn();
			} else {
				AlienStatus.stun(p, t, INSIDE_STUN);
				QuirkFx.ring(l, t.position().add(0, t.getBbHeight() + 0.3, 0)).colours(0xFFFFFF, 0xF0E060).size(0.7f).thickness(0.12f).life(INSIDE_STUN).spawn();
			}
		}
		OmnitrixSounds.play(l, out, LEAP, 0.9f, 1.3f);
		OmnitrixSounds.play(l, out, SQUEEZE, 0.7f, 1.4f);
	}

	// ---------------------------------------------------------------- C / Shift+C

	/**
	 * C: the block you look at if he can fix or boost it (an anvil, cracked bricks; a furnace, smoker, blast furnace or brewing
	 * stand gets 10 s of work done at once), otherwise the gear in your hands (30% of its durability each). Nothing to fix: no
	 * cooldown.
	 */
	public boolean geniusRepair(ServerPlayer p) {
		if (cooldownLeft(p, REPAIR_MOVE) > 0) {
			return use(p, REPAIR_MOVE);
		}
		ServerLevel l = level(p);
		BlockHitResult hit = l.clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(4.5)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, p));
		boolean fixed = false;
		if (hit.getType() == HitResult.Type.BLOCK && p.mayInteract(l, hit.getBlockPos())) {
			fixed = fixBlock(p, l, hit.getBlockPos());
		}
		if (!fixed) {
			fixed = repairHeld(p);
		}
		if (!fixed) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.nothing_to_fix").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		use(p, REPAIR_MOVE);
		anim(p, ANIM_REPAIR);
		OmnitrixSounds.play(l, p.position(), REPAIR, 1.0f, 1.0f);
		return true;
	}

	/** A broken or slow block he can fix or boost (true if he did). */
	public boolean fixBlock(ServerPlayer p, ServerLevel l, BlockPos pos) {
		BlockState s = l.getBlockState(pos);
		BlockState into = null;
		if (s.is(Blocks.CHIPPED_ANVIL) || s.is(Blocks.DAMAGED_ANVIL)) {
			into = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, s.getValue(AnvilBlock.FACING));
		} else if (s.is(Blocks.CRACKED_STONE_BRICKS)) {
			into = Blocks.STONE_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
			into = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_DEEPSLATE_TILES)) {
			into = Blocks.DEEPSLATE_TILES.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_NETHER_BRICKS)) {
			into = Blocks.NETHER_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)) {
			into = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.INFESTED_CRACKED_STONE_BRICKS)) {
			into = Blocks.STONE_BRICKS.defaultBlockState();
		}
		Vec3 c = Vec3.atCenterOf(pos);
		if (into != null) {
			l.setBlock(pos, into, Block.UPDATE_ALL);
			sparks(l, c);
			return true;
		}
		BlockEntity be = l.getBlockEntity(pos);
		if (be instanceof AbstractFurnaceBlockEntity f) {
			for (int i = 0; i < BOOST_TICKS; i++) {
				AbstractFurnaceBlockEntity.serverTick(l, pos, l.getBlockState(pos), f);
			}
			sparks(l, c);
			l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 8, 0.3, 0.2, 0.3, 0.02);
			return true;
		}
		if (be instanceof BrewingStandBlockEntity b) {
			for (int i = 0; i < BOOST_TICKS; i++) {
				BrewingStandBlockEntity.serverTick(l, pos, l.getBlockState(pos), b);
			}
			sparks(l, c);
			return true;
		}
		return false;
	}

	private static void sparks(ServerLevel l, Vec3 c) {
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 16, 0.35, 0.3, 0.35, 0.1);
		l.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y + 0.5, c.z, 10, 0.35, 0.3, 0.35, 0.1);
		QuirkFx.ring(l, c.add(0, -0.45, 0)).colours(0xE0F8FF, 0x5AB8F0).size(1.2f).thickness(0.15f).life(12).ground().spawn();
	}

	/** Repairs the gear in both hands by 30% of its durability. True if anything was damaged. */
	public boolean repairHeld(ServerPlayer p) {
		boolean any = false;
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack st = p.getItemInHand(h);
			if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > 0) {
				st.setDamageValue(Math.max(0, st.getDamageValue() - Math.max(1, Math.round(st.getMaxDamage() * REPAIR_FRACTION))));
				any = true;
			}
		}
		if (any) {
			ServerLevel l = level(p);
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 12, 0.2, 0.2, 0.2, 0.08);
			l.sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
		}
		return any;
	}

	/** Shift+C: everything valuable within 20 blocks outlined through the walls for 8 s (Moon Knight's Scholar's Sight renderer). */
	public boolean mindMap(ServerPlayer p) {
		if (!use(p, MIND_MAP)) {
			return false;
		}
		anim(p, ANIM_MIND);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		List<long[]> found = new ArrayList<>();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-MIND_RANGE, -12, -MIND_RANGE), c.offset(MIND_RANGE, 12, MIND_RANGE))) {
			BlockState st = l.getBlockState(q);
			int col = mapColour(st);
			if (col != 0) {
				found.add(new long[] { q.asLong(), (long) c.distSqr(q), col });
			}
		}
		found.sort((a, b) -> Long.compare(a[1], b[1]));
		int n = Math.min(256, found.size());
		long[] pos = new long[n];
		int[] cols = new int[n];
		for (int i = 0; i < n; i++) {
			pos[i] = found.get(i)[0];
			cols[i] = (int) found.get(i)[2];
		}
		ServerPlayNetworking.send(p, new MoonKnightScholarSightPayload(pos, cols, MIND_TICKS));
		// a ripple of thought outwards (particles: a ring effect would tower over something this small)
		for (int k = 0; k < 24; k++) {
			double a = k * Math.PI * 2 / 24;
			l.sendParticles(THINK_DUST, p.getX() + Math.cos(a) * 1.2, p.getY() + 0.1, p.getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
		}
		l.sendParticles(THINK_DUST, p.getX(), p.getEyeY() + 0.2, p.getZ(), 20, 0.3, 0.2, 0.3, 0.02);
		OmnitrixSounds.play(l, p.position(), THINK, 1.0f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.mind_map", n).withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** The Mind Map's outline colour for a block (0 = not shown). */
	public static int mapColour(BlockState st) {
		if (st.is(ConventionalBlockTags.ORES)) {
			if (st.is(ConventionalBlockTags.DIAMOND_ORES)) {
				return 0x5AF0F0;
			}
			if (st.is(ConventionalBlockTags.GOLD_ORES)) {
				return 0xF0D040;
			}
			if (st.is(ConventionalBlockTags.REDSTONE_ORES)) {
				return 0xF03030;
			}
			if (st.is(ConventionalBlockTags.EMERALD_ORES)) {
				return 0x40F070;
			}
			if (st.is(ConventionalBlockTags.LAPIS_ORES)) {
				return 0x3A60F0;
			}
			if (st.is(ConventionalBlockTags.IRON_ORES)) {
				return 0xE0B090;
			}
			return 0xC0C0C0;
		}
		if (st.is(Blocks.SPAWNER) || st.is(Blocks.TRIAL_SPAWNER) || st.is(Blocks.VAULT)) {
			return 0xC040F0;
		}
		if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST) || st.is(Blocks.BARREL) || st.is(Blocks.ENDER_CHEST) || st.is(Blocks.SUSPICIOUS_SAND)
				|| st.is(Blocks.SUSPICIOUS_GRAVEL)) {
			return 0xF0A040;
		}
		return 0;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (INSIDE.containsKey(p.getUUID())) {
			return true; // no punching from inside
		}
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_JAB);
			if (marked(le)) {
				le.invulnerableTime = 0;
				hurt(p, le, 2f, false);
				level(p).sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
				le.invulnerableTime = 0; // the fist itself still lands this tick
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !(target instanceof LivingEntity le) || cooldownLeft(p, QUICK_READ) > 0) {
			return InteractionResult.PASS;
		}
		cooldown(p, QUICK_READ, -1);
		anim(p, ANIM_READ);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.read", le.getDisplayName(),
				String.format(java.util.Locale.ROOT, "%.1f", le.getHealth()), String.format(java.util.Locale.ROOT, "%.0f", le.getMaxHealth()))
				.withStyle(ChatFormatting.AQUA), true);
		OmnitrixSounds.play(level(p), p.position(), SCAN, 0.5f, 1.6f);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (INSIDE.containsKey(p.getUUID())) {
			return false; // v0.17.2 Inside Job: nothing reaches him in there (the void and /kill still do: OmnitrixDamage)
		}
		return !(flag(p, F_LEAP) && source.is(DamageTypeTags.IS_FALL));
	}

	/**
	 * v0.17.2 weaknesses: Fragile (an anvil, gravel, sand or a dripstone falling on him hits twice as hard) and Swatted (a hit
	 * from a creature bigger than a player stuns him 0.5 s -- his keys are dead and he is pinned; the x1.5 knockback is
	 * {@code GreyMatterKnockbackMixin}).
	 */
	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_STALACTITE)) {
			amount *= FALLING_BLOCK_FACTOR;
		}
		if (source.getEntity() instanceof LivingEntity a && source.getDirectEntity() == a && !(a instanceof Player) && bigger(a)
				&& p.invulnerableTime <= 10) {
			swat(p);
		}
		return amount;
	}

	/** v0.17.2 Swatted: a creature bigger than a player (taller than 1.95 or wider than 0.95 blocks). */
	public static boolean bigger(LivingEntity a) {
		return a.getBbHeight() > 1.95f || a.getBbWidth() > 0.95f;
	}

	private void swat(ServerPlayer p) {
		long now = p.level().getGameTime();
		setTimer(p, SWAT_END, now + SWAT_STUN);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SWAT_STUN, 9, false, false, false));
		QuirkFx.ring(level(p), p.position().add(0, p.getBbHeight() + 0.15, 0)).colours(0xFFFFFF, 0xF0E060).size(0.35f).thickness(0.06f).life(SWAT_STUN).spawn();
	}

	/** v0.17.2 Swatted: the knockback factor for {@code e} -- x1.5 on Grey Matter (not while inside a creature), else 1. Both sides. */
	public static double knockbackScale(LivingEntity e) {
		return e instanceof Player p && AlienKit.activeKit(p) instanceof GreyMatterKit k && !k.flag(p, F_INSIDE) ? SWAT_KNOCKBACK : 1.0;
	}

	/** v0.17.2 Galvan Intellect: is {@code p} Grey Matter (the enchanting / anvil perks)? Both sides. */
	public static boolean galvan(@Nullable Player p) {
		return p != null && AlienKit.activeKit(p) instanceof GreyMatterKit;
	}

	/** v0.17.2 Galvan Intellect: an anvil repair's XP cost for Grey Matter (30% off, never under 1). */
	public static int anvilCost(int cost) {
		return cost <= 1 ? cost : Math.max(1, Math.round(cost * (1f - ANVIL_DISCOUNT)));
	}

	// ---------------------------------------------------------------- v0.17.2 passives

	/**
	 * Sticky Climber: pressed into a wall he climbs it like a spider (the vanilla climbable rule), and Sneak holds him on it even
	 * when not pressing in. Asked on both sides: his own physics, no server-only state.
	 */
	@Override
	public boolean climbs(Player p) {
		if (p.isPassenger() || flag(p, F_INSIDE) || p.getAbilities().flying) {
			return false;
		}
		if (p.horizontalCollision) {
			return true;
		}
		if (!p.level().isClientSide() && !p.onGround() && touchingWall(p)) {
			return true; // the server never sees his collisions (the client moves him): against a wall in the air he is climbing
		}
		return p.isShiftKeyDown() && !p.onGround() && touchingWall(p);
	}

	private static boolean touchingWall(Player p) {
		AABB box = p.getBoundingBox();
		AABB probe = new AABB(box.minX - 0.08, box.minY + 0.05, box.minZ - 0.08, box.maxX + 0.08, box.maxY - 0.05, box.maxZ + 0.08);
		return !p.level().noCollision(p, probe);
	}

	/** Amphibian (breath + swim), Too Small for Big Weapons, and the wall-crawl pose flag. */
	private void tickPassives(ServerPlayer p, ServerLevel l, long now) {
		if (now % 20 == 0) {
			PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, SWIM_BONUS, AttributeModifier.Operation.ADD_VALUE);
			if (p.isEyeInFluid(FluidTags.WATER)) {
				p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false, false));
			}
		}
		ItemStack held = p.getMainHandItem();
		boolean big = held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES) || held.is(Items.TRIDENT);
		if (big) {
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, BIG_WEAPON, BIG_WEAPON_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, BIG_WEAPON);
		}
		setFlag(p, F_CLIMB, !p.onGround() && !p.isInWater() && climbs(p));
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Pocket Workshop

	private void vKey(ServerPlayer p, boolean shift) {
		if (shift) {
			rewire(p); // v0.17.2
		} else {
			pocketWorkshop(p);
		}
	}

	/** V: Pocket Workshop -- a Galvan needs no table: a crafting grid opens wherever he stands (what is left in it comes back). */
	public boolean pocketWorkshop(ServerPlayer p) {
		if (!use(p, WORKSHOP)) {
			return false;
		}
		anim(p, ANIM_WORKSHOP);
		ServerLevel l = level(p);
		net.minecraft.world.inventory.ContainerLevelAccess access = net.minecraft.world.inventory.ContainerLevelAccess.create(l, p.blockPosition());
		p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, who) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, access) {
			@Override
			public boolean stillValid(Player player) {
				return player.isAlive(); // no table to stand next to
			}
		}, Component.translatable("container.projecthero.omnitrix.pocket_workshop")));
		l.sendParticles(THINK_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 6, 0.2, 0.2, 0.2, 0);
		OmnitrixSounds.play(l, p.position(), THINK, 0.8f, 1.6f);
		return true;
	}

	private void revertV(ServerPlayer p) {
		if (p.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu) {
			p.closeContainer(); // the grid goes with the brain
		}
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
	}

	// ---------------------------------------------------------------- v0.17.2 Shift+V Rewire

	/**
	 * Shift+V (v0.17.2, "rewiring machines and traps"): remote-tinker with what you look at (16 blocks) -- open / close a door,
	 * trapdoor or gate (iron ones too); flip a lever, press a button, or light a redstone lamp for 10 s; switch a spawner off for
	 * 60 s (its cooldown 30 s); defuse primed TNT (it drops back as an item); disarm a tripwire (the string drops). Nothing to
	 * rewire: no cooldown.
	 */
	public boolean rewire(ServerPlayer p) {
		if (cooldownLeft(p, REWIRE) > 0) {
			return use(p, REWIRE);
		}
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(REWIRE_RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		double reach = hit.getType() == HitResult.Type.MISS ? REWIRE_RANGE : hit.getLocation().distanceTo(eye);
		// primed TNT first (it sits in front of whatever block it is on)
		EntityHitResult tntHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(reach)),
				p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0), e -> e instanceof PrimedTnt && e.isAlive(), reach * reach);
		int cd = -1;
		Vec3 spot = null;
		if (tntHit != null && tntHit.getEntity() instanceof PrimedTnt tnt) {
			spot = tnt.position().add(0, 0.5, 0);
			defuse(l, tnt);
			cd = 0;
		} else if (hit.getType() == HitResult.Type.BLOCK && p.mayInteract(l, hit.getBlockPos())) {
			BlockPos pos = hit.getBlockPos();
			int r = rewireBlock(p, l, pos);
			if (r >= 0) {
				cd = r;
				spot = Vec3.atCenterOf(pos);
			}
		}
		if (cd < 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.nothing_to_rewire").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		cooldown(p, REWIRE, cd > 0 ? cd : -1);
		anim(p, ANIM_REWIRE);
		// a thin grey line from his hand to it, and a brief grey ring on it
		Vec3 hand = p.position().add(0, p.getBbHeight() * 0.6, 0).add(look.scale(0.2));
		QuirkFx.ribbon(l, hand, spot).colours(0xE8ECF0, COLOUR).size(0.025f).life(8).spawn();
		QuirkFx.ring(l, spot).colours(0xE8ECF0, COLOUR).size(0.9f).thickness(0.08f).life(12).spawn();
		OmnitrixSounds.play(l, spot, REWIRE_SOUND, 1.0f, 1.0f);
		return true;
	}

	/** Rewires the block at {@code pos}: the cooldown to use (0 = Rewire's own), or -1 if there is nothing he can rewire there. */
	public int rewireBlock(ServerPlayer p, ServerLevel l, BlockPos pos) {
		BlockState s = l.getBlockState(pos);
		Block b = s.getBlock();
		if (b instanceof DoorBlock door) {
			door.setOpen(p, l, s, pos, !s.getValue(DoorBlock.OPEN));
			return 0;
		}
		if (b instanceof TrapDoorBlock) {
			boolean open = !s.getValue(TrapDoorBlock.OPEN);
			l.setBlock(pos, s.setValue(TrapDoorBlock.OPEN, open), Block.UPDATE_ALL);
			l.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.1f);
			return 0;
		}
		if (b instanceof FenceGateBlock) {
			boolean open = !s.getValue(FenceGateBlock.OPEN);
			l.setBlock(pos, s.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL);
			l.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 0.8f, 1.1f);
			return 0;
		}
		if (b instanceof LeverBlock lever) {
			lever.pull(s, l, pos, p);
			return 0;
		}
		if (b instanceof ButtonBlock button) {
			if (s.getValue(ButtonBlock.POWERED)) {
				return -1;
			}
			button.press(s, l, pos, p);
			return 0;
		}
		if (b instanceof RedstoneLampBlock) {
			if (s.getValue(RedstoneLampBlock.LIT)) {
				return -1;
			}
			// lit without a neighbour update (which would put it straight back out); dark again in 10 s
			l.setBlock(pos, s.setValue(RedstoneLampBlock.LIT, true), Block.UPDATE_CLIENTS);
			LAMPS.computeIfAbsent(p.getUUID(), k -> new CopyOnWriteArrayList<>()).add(new Lamp(l.dimension(), pos.immutable(), l.getGameTime() + POWER_TICKS));
			return 0;
		}
		if (b instanceof TripWireBlock) {
			// the shears' way: disarmed first, so taking the string out trips nothing
			l.setBlock(pos, s.setValue(TripWireBlock.DISARMED, true), Block.UPDATE_INVISIBLE);
			l.removeBlock(pos, false);
			Block.popResource(l, pos, new ItemStack(Items.STRING));
			return 0;
		}
		if (l.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
			disableSpawner(l, pos, spawner);
			return REWIRE_SPAWNER_COOLDOWN;
		}
		return -1;
	}

	/** Switches a spawner off for {@value #SPAWNER_OFF_TICKS} ticks (its delay set that long; its flame barely turns meanwhile). */
	public static void disableSpawner(ServerLevel l, BlockPos pos, SpawnerBlockEntity spawner) {
		CompoundTag tag = spawner.getSpawner().save(new CompoundTag());
		tag.putShort("Delay", (short) SPAWNER_OFF_TICKS);
		spawner.getSpawner().load(l, pos, tag);
		spawner.setChanged();
		BlockState st = l.getBlockState(pos);
		l.sendBlockUpdated(pos, st, st, Block.UPDATE_ALL);
	}

	/** A spawner's delay right now (ticks until it may spawn). */
	public static int spawnerDelay(SpawnerBlockEntity spawner) {
		return spawner.getSpawner().save(new CompoundTag()).getShort("Delay");
	}

	private static void defuse(ServerLevel l, PrimedTnt tnt) {
		Vec3 at = tnt.position();
		tnt.discard();
		ItemEntity item = new ItemEntity(l, at.x, at.y + 0.2, at.z, new ItemStack(Items.TNT));
		item.setDefaultPickUpDelay();
		l.addFreshEntity(item);
		l.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.6f);
	}

	private void tickLamps(ServerPlayer p, long now) {
		List<Lamp> lamps = LAMPS.get(p.getUUID());
		if (lamps == null || lamps.isEmpty() || now % 5 != 0) {
			return;
		}
		for (Lamp lamp : lamps) {
			if (now >= lamp.until()) {
				lamps.remove(lamp);
				unlight(p.server.getLevel(lamp.dim()), lamp.pos());
			}
		}
	}

	private static void unlight(@Nullable ServerLevel l, BlockPos pos) {
		if (l == null || !l.isLoaded(pos)) {
			return;
		}
		BlockState s = l.getBlockState(pos);
		if (s.getBlock() instanceof RedstoneLampBlock && s.getValue(RedstoneLampBlock.LIT) && !l.hasNeighborSignal(pos)) {
			l.setBlock(pos, s.setValue(RedstoneLampBlock.LIT, false), Block.UPDATE_CLIENTS);
		}
	}
}
