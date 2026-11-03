package com.projecthero.mod.omnitrix.alien.stinkfly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Stinkfly, the Lepidopterran -- a green insect with four eyes on stalks, translucent wings and a stinger tail. He
 * flies for real, spits goo and gasses whatever is below him. Light: 20 health, but no fall damage at all.
 *
 * <p>v0.17.2 (user: "yes build these things please i like them" -- docs/OMNITRIX_REVAMP_STINKFLY.md): the goo now STICKS -- a
 * missed shot leaves a goo patch on the floor, wall or ceiling, the dive leaves a pool, and anything standing in goo is Gooed
 * (honey-slow, no jumping; {@link StinkflyGooEffect}); fire lights goo. The goo comes from his EYES (the 2005 show). Goo Storm
 * became the smaller Goo Rain (Shift+C), Slime Barrage folded into the held goo spit, and the ultimate is the Goo Cocoon. The
 * show's weaknesses: wet wings, fragile wings, fire.
 *
 * <pre>
 *   R        Slime Shot     a goo glob (10): roots 1.5 s; a miss leaves a      Shift+R  Slime Net      a wide net (6): roots everything (3 s); a
 *                           goo patch (8 s) on the block it hits                                       target backed by a wall is pinned to it
 *   G        Stench Cloud   the gas cloud ahead (5 s, 20 in all, poison,       Shift+G  Stinger Stab   the tail jabs (12 + Poison II)
 *                           nausea); mobs in it lose their target
 *   X        Wing Dash      a burst of speed where you look                    Shift+X  Stinger Lunge  dart forward stinger-first (14 to all in the path)
 *   Z        Dive Bomb      plunge and splash down (18 falling to 11), a       Shift+Z  Goo Cocoon     ULTIMATE: a 1.5 s stream wraps the target in a
 *                           goo pool left behind (5 s)                                                 cocoon (5 s, boss 1.5 s, +25% taken), then a gas burst
 *   V        Lift           carry a creature or a squad ally in your legs;     Shift+V  Goo Wall       a 3 x 3 sheet of goo (12 s, 30 HP): projectiles stick,
 *                           a 6+ block drop onto an enemy hurts both                                   creatures are glued 2 s; fire burns it
 *   C        Wing Gust      a blast of wind (6): shoves, deflects projectiles, Shift+C  Goo Rain       a cloud rains goo on an area (4 s, 12 in all, slows,
 *                           carries his own gas / rain cloud 8 blocks                                  leaves patches)
 *   Attack   stinger-tipped jab (poisons)       Use   a goo spit (5); held: rapid spit, 4 a second (3 each) for up to 3 s
 *   Double-tap jump   take off / land (Wing Flight)
 * </pre>
 */
public final class StinkflyKit extends AlienKit {
	public static final String ID = "stinkfly";
	public static final int COLOUR = 0x9BCB3F;
	public static final int GOO = 0xB8E04A;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** The wings: an insect buzz (the flying loop plays it quietly every 16 ticks). */
	public static final OmnitrixSfx.Cue BUZZ = OmnitrixSfx.cue("stinkfly_buzz")
			.layer(SoundEvents.BEE_POLLINATE, 0.4f, 0.8f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.18f, 1.8f)
			.jitter(0.08f).gap(6);
	/** Slime spat: a wet spit. */
	public static final OmnitrixSfx.Cue SPIT = OmnitrixSfx.cue("stinkfly_spit")
			.layer(SoundEvents.LLAMA_SPIT, 0.45f, 1.0f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.3f, 1.2f);
	/** Slime landing: a wet splat. */
	public static final OmnitrixSfx.Cue SPLAT = OmnitrixSfx.cue("stinkfly_splat")
			.layer(SoundEvents.SLIME_SQUISH, 0.45f, 0.9f)
			.layer(SoundEvents.HONEY_BLOCK_FALL, 0.3f, 0.8f)
			.gap(3);
	/** A stink cloud: a hiss and a bubbling. */
	public static final OmnitrixSfx.Cue GAS = OmnitrixSfx.cue("stinkfly_gas")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 0.5f)
			.layer(SoundEvents.BREWING_STAND_BREW, 0.3f, 0.6f)
			.layer(SoundEvents.SLIME_SQUISH, 0.2f, 0.6f);
	public static final OmnitrixSfx.Cue STING = OmnitrixSfx.cue("stinkfly_sting")
			.layer(SoundEvents.BEE_STING, 0.45f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.3f, 1.2f);
	/** A dive: the wings revving into a rush. */
	public static final OmnitrixSfx.Cue DIVE = OmnitrixSfx.cue("stinkfly_dive")
			.layer(SoundEvents.BEE_POLLINATE, 0.45f, 1.1f)
			.layer(SoundEvents.WIND_CHARGE_THROW, 0.3f, 1.0f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.25f, 1.4f);
	/** v0.17.2: the Goo Cocoon's stream -- a thick, wet, pumping gush. */
	public static final OmnitrixSfx.Cue GUSH = OmnitrixSfx.cue("stinkfly_gush")
			.layer(SoundEvents.HONEY_BLOCK_SLIDE, 0.5f, 0.7f)
			.layer(SoundEvents.SLIME_SQUISH, 0.35f, 0.6f)
			.gap(4);

	public static final int ANIM_SHOT = 1;
	public static final int ANIM_NET = 2;
	public static final int ANIM_GAS = 3;
	public static final int ANIM_STAB = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_DIVE = 6;
	public static final int ANIM_GUST = 7;
	/** v0.17.2: Goo Rain (was Goo Storm's). */
	public static final int ANIM_STORM = 8;
	/** v0.17.2: one glob of the held rapid spit (a short head jerk; was Slime Barrage's). */
	public static final int ANIM_BARRAGE = 9;
	public static final int ANIM_JAB = 10;
	public static final int ANIM_TAKEOFF = 11;
	public static final int ANIM_SPLASH = 12;
	/** v0.17.2: the Goo Cocoon cast -- hovering upright, head pushed forward, wings at full spread. */
	public static final int ANIM_COCOON = 14;
	/** v0.17.2: the Goo Wall spat across the space in front. */
	public static final int ANIM_WALL = 15;

	public static final int F_FLYING = 1;
	public static final int F_DIVING = 2;
	/** v0.17.2 Wet Wings: soaked (in water, or for 5 s after) -- no take-off, the wings drawn dark and drooping. */
	public static final int F_WET = 8;
	/** v0.17.2 Fragile Wings / Fire: the wings clipped (or burning) -- forced gliding, an uneven flutter. */
	public static final int F_CLIPPED = 16;
	/** v0.17.2 Wet Wings: flying in rain (-30% flight speed, half a Wing Dash, goo roots half as long). */
	public static final int F_RAIN = 32;
	/** v0.17.2: pumping the Goo Cocoon stream (the client draws it from his eyes to {@link #COCOON_TARGET}). */
	public static final int F_COCOON = 64;

	public static final String SLIME_SHOT = "slime_shot";
	public static final String SLIME_NET = "slime_net";
	public static final String TOXIC_GAS = "toxic_gas";
	public static final String STINGER = "stinger_stab";
	public static final String FLIGHT = "wing_flight";
	public static final String WING_DASH = "wing_dash";
	public static final String LUNGE = "stinger_lunge";
	// v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger ... make each alien stronger accordingly please"): every damage number in the kit about x1.6, its gas and goo rain 7-8 a pulse (old -> new in docs/OMNITRIX_DAMAGE_PASS_PROTOTYPE.md)
	public static final float LUNGE_DAMAGE = 22f;
	public static final double LUNGE_RANGE = 8.0;
	public static final String DIVE_BOMB = "dive_bomb";
	public static final String WING_GUST = "wing_gust";
	/** v0.17.2: Shift+C (the old Goo Storm, smaller; it was the Shift+Z ultimate). */
	public static final String GOO_RAIN = "goo_rain";
	/** v0.17.2: the Shift+Z ultimate (replaces Goo Storm). */
	public static final String GOO_COCOON = "goo_cocoon";
	/** v0.17.2: Shift+V (new). */
	public static final String GOO_WALL = "goo_wall";
	public static final String JAB = "stinger_jab";
	public static final String GOO_SPIT = "goo_spit";

	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3
	// v0.17.2 (user: "adjust the other combat aliens damage aswell please to try and match this"): on Four Arms / Diamondhead's scale -- quick hits 5-8, moves 12-17, big ones 17-20, ultimates 21-36 (the shot is quick: 10 -> 6)
	public static final float SHOT_DAMAGE = 10f;
	public static final int SHOT_ROOT = 30;
	public static final float NET_RADIUS = 2.75f;
	public static final int NET_ROOT = 60;
	/** v0.17.2: a boss under the net is rooted 1 s. */
	public static final int NET_BOSS_ROOT = 20;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 2 (inline)
	public static final float NET_DAMAGE = 14f;
	public static final float GAS_RADIUS = 3.25f;
	public static final int GAS_TICKS = 100;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 1.5 (10 pulses = 20)
	public static final float GAS_DAMAGE = 7f;
	/** v0.17.2 Stench Cloud: how long a mob keeps wandering (no target) after leaving the cloud. */
	public static final int STENCH_LINGER = 40;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 6
	public static final float STAB_DAMAGE = 20f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 8 falling to 4.8
	// at the edge -- now 18 at the centre falling to 11
	public static final float DIVE_DAMAGE = 28f;
	public static final float DIVE_EDGE_DAMAGE = 18f;
	public static final float DIVE_RADIUS = 3.5f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 2
	public static final float GUST_DAMAGE = 10f;
	/** v0.17.2: how far a Wing Gust carries his own Stench Cloud / Goo Rain. */
	public static final double GUST_CARRY = 8.0;
	// v0.17.2 Goo Rain (was Goo Storm: r 6, 6 s, 4 a second = 24, the ultimate)
	public static final float RAIN_RADIUS = 5f;
	public static final int RAIN_TICKS = 80;
	public static final float RAIN_DAMAGE = 8f;
	// v0.17.2 Goo Cocoon
	public static final double COCOON_RANGE = 20.0;
	public static final int COCOON_STREAM = 30;
	public static final int COCOON_TICKS = 100;
	public static final int COCOON_BOSS_TICKS = 30;
	public static final float COCOON_TAKEN = 1.25f;
	// v0.17.2 (user: "increase the damage of every alien ultimate that deals damage to be between 40-70 damage"): the cocoon bursts for 45
	public static final float COCOON_BURST = 75f; // v0.17.2 damage pass: 45 -> 75
	public static final double COCOON_BURST_RADIUS = 3.0;
	public static final double COCOON_SPRAY = 4.0;
	/** The timer (synced, so the client can draw the stream) holding the cocoon target's entity id. */
	public static final String COCOON_TARGET = "cocoon_target";
	// v0.17.2 Goo Wall
	public static final int WALL_TICKS = 240;
	public static final int WALL_HP = 30;
	public static final int WALL_GLUE = 40;
	// v0.17.2 goo patches (R misses, the dive's pool, Goo Rain's globs)
	public static final float PATCH_HALF = 0.75f;
	public static final int PATCH_TICKS = 160;
	public static final int MAX_SHOT_PATCHES = 6;
	public static final float POOL_HALF = 1.5f;
	public static final int POOL_TICKS = 100;
	public static final int MAX_RAIN_PATCHES = 4;
	/** A creature landing in a patch is stuck this long (once per patch). */
	public static final int PATCH_STICK = 20;
	// v0.17.2 Goo Spit: tap 5 (was 2); held, 4 globs a second (3 each) for up to 3 s, then a 6 s cooldown
	public static final float SPIT_DAMAGE = 8f;
	public static final float RAPID_DAMAGE = 5f;
	public static final int RAPID_EVERY = 5;
	public static final int RAPID_TICKS = 60;
	public static final int RAPID_COOLDOWN = 120;
	/** A Use arriving within this many ticks of the last one is the same held click. */
	private static final int HOLD_GAP = 3;
	/** How long the click must be held before the rapid spit starts. */
	private static final int HOLD_DELAY = 6;
	// v0.17.2 weaknesses
	public static final int WET_TICKS = 100;
	public static final float CLIP_THRESHOLD = 8f;
	public static final int CLIP_TICKS = 40;
	public static final int CLIP_COOLDOWN = 200;
	public static final float FIRE_TAKEN = 1.25f;
	public static final float RAIN_FLIGHT = 0.7f;
	// v0.17.2 passives
	public static final double STENCH_RANGE = 6.0;
	public static final double EYES_RANGE = 10.0;
	public static final float FLY_SPEED = 0.06f;

	private static final DustParticleOptions GOO_DUST = new DustParticleOptions(new Vector3f(0.72f, 0.88f, 0.29f), 1.2f);
	// v0.17.2 (user: "Give his powers actual models and vfx instead of just glowing orbs please"): more goo / gas shades
	private static final DustParticleOptions GOO_DARK = new DustParticleOptions(new Vector3f(0.36f, 0.52f, 0.1f), 1.0f);
	private static final DustParticleOptions GOO_PALE = new DustParticleOptions(new Vector3f(0.88f, 0.96f, 0.55f), 0.9f);
	private static final net.minecraft.core.particles.ParticleOptions POISON_SWIRL = net.minecraft.core.particles.ColorParticleOption
			.create(ParticleTypes.ENTITY_EFFECT, 0.55f, 0.8f, 0.18f);

	/** player -> the stench cloud / goo rain they left (centre, dimension, end time, the rendered cloud's entity id). */
	private static final Map<UUID, Area> GAS_CLOUDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Area> RAINS = new ConcurrentHashMap<>();
	/** player -> their goo patches / pools. */
	private static final Map<UUID, List<Patch>> PATCHES = new ConcurrentHashMap<>();
	/** a stenched mob -> the player whose cloud it is and until when it wanders. */
	private static final Map<UUID, Mark> STENCHED = new ConcurrentHashMap<>();
	/** a creature pinned to a wall by the Slime Net -> who, until when, and the way it faces. */
	private static final Map<UUID, Pin> PINNED = new ConcurrentHashMap<>();
	/** player -> the Goo Cocoon being pumped (the target, when the stream ends). */
	private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();
	/** a cocooned creature -> its cocoon. */
	private static final Map<UUID, Cocoon> COCOONS = new ConcurrentHashMap<>();
	/** player -> their Goo Wall. */
	private static final Map<UUID, Wall> WALLS = new ConcurrentHashMap<>();
	/** a creature Lift let go of -> who dropped it, from what height (the drop-on hit). */
	private static final Map<UUID, Drop> DROPS = new ConcurrentHashMap<>();
	/** player -> {first Use, last Use, next rapid glob, state 0 tap / 1 rapid / -1 spent} of the held goo spit. */
	private static final Map<UUID, long[]> SPITS = new ConcurrentHashMap<>();
	/** player -> when his wings are dry again. */
	private static final Map<UUID, Long> WET = new ConcurrentHashMap<>();
	/** player -> {clipped until, can be clipped again at}. */
	private static final Map<UUID, long[]> CLIPS = new ConcurrentHashMap<>();
	/** The cocoon's +25%: re-issued behind a guard (the GreyMatterKit / OmnitrixDamage pattern). */
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private record Area(ResourceKey<Level> dim, Vec3 at, long until, int fx) {
	}

	private record Mark(UUID owner, long until) {
	}

	private record Pin(UUID owner, long until, float yaw, float pitch) {
	}

	private record Cast(int target, long end) {
	}

	private record Cocoon(UUID owner, long until, int fx) {
	}

	private record Drop(UUID owner, double fromY, boolean ally, long until) {
	}

	/** A goo patch or pool on a block face. */
	private static final class Patch {
		static final int SHOT = 0, POOL = 1, RAIN = 2;
		final ResourceKey<Level> dim;
		final Vec3 at;
		final Direction face;
		final float half;
		final long until;
		final int kind;
		final int fx;
		/** Who has already been stuck by this patch (once each). */
		final Set<Integer> stuck = new HashSet<>();

		Patch(ResourceKey<Level> dim, Vec3 at, Direction face, float half, long until, int kind, int fx) {
			this.dim = dim;
			this.at = at;
			this.face = face;
			this.half = half;
			this.until = until;
			this.kind = kind;
			this.fx = fx;
		}

		AABB box() {
			double ex = face.getAxis() == Direction.Axis.X ? 0.3 : half, ey = face.getAxis() == Direction.Axis.Y ? 0.3 : half,
					ez = face.getAxis() == Direction.Axis.Z ? 0.3 : half;
			return new AABB(at.x - ex, at.y - ey, at.z - ez, at.x + ex, at.y + ey, at.z + ez);
		}
	}

	/** The Goo Wall: a vertical 3 x 3 sheet, its normal along {@code axis}. */
	private static final class Wall {
		final ResourceKey<Level> dim;
		final Vec3 at;
		final Direction.Axis axis;
		final long until;
		final int fx;
		int hp = WALL_HP;
		final List<Integer> stuck = new ArrayList<>();
		final Map<Integer, Long> glued = new HashMap<>();

		Wall(ResourceKey<Level> dim, Vec3 at, Direction.Axis axis, long until, int fx) {
			this.dim = dim;
			this.at = at;
			this.axis = axis;
			this.until = until;
			this.fx = fx;
		}

		AABB box() {
			double ex = axis == Direction.Axis.X ? 0.25 : 1.5, ez = axis == Direction.Axis.Z ? 0.25 : 1.5;
			return new AABB(at.x - ex, at.y - 1.5, at.z - ez, at.x + ex, at.y + 1.5, at.z + ez);
		}

		Vec3 normal() {
			return axis == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
		}
	}

	/** v0.17.2 (Wildmutt's Choked Nose): is {@code at} inside any Stinkfly's gas cloud right now? */
	public static boolean inGas(Level level, Vec3 at) {
		long now = level.getGameTime();
		for (Area a : GAS_CLOUDS.values()) {
			if (a.dim() == level.dimension() && now < a.until() && a.at().add(0, 0.8, 0).distanceToSqr(at) <= (GAS_RADIUS + 0.5) * (GAS_RADIUS + 0.5)) {
				return true;
			}
		}
		return false;
	}

	private StinkflyKit() {
	}

	public static void register() {
		StinkflyGooEffect.initialize();
		Aliens.register(new StinkflyKit());
		AlienProjectileEntity.onHit("stinkfly_slime", (proj, hit) -> splat(proj, hit, false));
		AlienProjectileEntity.onHit("stinkfly_spit", (proj, hit) -> splat(proj, hit, false));
		AlienProjectileEntity.onHit("stinkfly_net", (proj, hit) -> splat(proj, hit, true));
		// v0.17.2 Goo Cocoon: a cocooned creature takes 25% more from every source
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (REENTRANT.get() || amount <= 0f || COCOONS.isEmpty() || entity.level().isClientSide()) {
				return true;
			}
			Cocoon c = COCOONS.get(entity.getUUID());
			if (c == null || c.until() <= entity.level().getGameTime() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			float more = amount * COCOON_TAKEN;
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
	}

	public static void clearSessionState() {
		GAS_CLOUDS.clear();
		RAINS.clear();
		LIFTED.clear();
		PATCHES.clear();
		STENCHED.clear();
		PINNED.clear();
		CASTS.clear();
		COCOONS.clear();
		WALLS.clear();
		DROPS.clear();
		SPITS.clear();
		WET.clear();
		CLIPS.clear();
	}

	// ---------------------------------------------------------------- goo hits

	private static void splat(AlienProjectileEntity proj, HitResult hit, boolean net) {
		if (!(proj.level() instanceof ServerLevel l) || !(proj.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		// v0.17.2 Wet Wings: goo shot in the rain roots half as long
		boolean rain = activeKit(owner) instanceof StinkflyKit kit && kit.flag(owner, F_RAIN);
		Vec3 at = hit.getLocation();
		// v0.17.2 (user: the particles): fewer flecks -- the rendered splash and the decal patches carry it
		l.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, net ? 14 : 6, net ? 1.0 : 0.25, 0.2, net ? 1.0 : 0.25, 0.1);
		splatBurst(l, at, proj.getDeltaMovement(), net ? 2.2f : "stinkfly_spit".equals(proj.kind()) ? 0.6f : 1f);
		OmnitrixSounds.play(l, at, SPLAT, net ? 1.0f : 0.7f, net ? 0.8f : 1.1f + l.random.nextFloat() * 0.2f);
		if (net) {
			QuirkFx.decal(l, groundBelow(l, at)).style(QuirkFx.DECAL_POOL).colour(0x6A9A1A).size(NET_RADIUS * 1.4f).life(160).spawn();
			QuirkFx.ring(l, at).colours(0xE8FFB0, GOO).size(NET_RADIUS).thickness(0.35f).life(10).ground().spawn();
			Vec3 v = proj.getDeltaMovement();
			Vec3 flight = new Vec3(v.x, 0, v.z).lengthSqr() > 1e-6 ? new Vec3(v.x, 0, v.z).normalize() : owner.getLookAngle().multiply(1, 0, 1).normalize();
			for (LivingEntity e : around(owner, at, NET_RADIUS)) {
				glue(owner, e, rain ? NET_ROOT / 2 : NET_ROOT, rain ? NET_BOSS_ROOT / 2 : NET_BOSS_ROOT, 40);
				// v0.17.2: a target with a wall behind it (along the net's flight) is pinned to it -- it can't move or turn
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
				BlockHitResult wall = l.clip(new ClipContext(c, c.add(flight.scale(1.2 + e.getBbWidth() * 0.5)), ClipContext.Block.COLLIDER,
						ClipContext.Fluid.NONE, e));
				if (wall.getType() == HitResult.Type.BLOCK) {
					int t = AlienStatus.isBoss(e) ? (rain ? NET_BOSS_ROOT / 2 : NET_BOSS_ROOT) : (rain ? NET_ROOT / 2 : NET_ROOT);
					PINNED.put(e.getUUID(), new Pin(owner.getUUID(), l.getGameTime() + t, e.getYRot(), e.getXRot()));
					e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 4, false, false, true), owner);
					if (e instanceof Mob m && !AlienStatus.isBoss(e)) {
						m.setTarget(null);
						m.getNavigation().stop();
					}
					addPatch(owner, l, wall.getLocation().add(Vec3.atLowerCornerOf(wall.getDirection().getNormal()).scale(0.02)), wall.getDirection(),
							1.0f, t + 40, Patch.SHOT);
				}
			}
		} else if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
			if ("stinkfly_slime".equals(proj.kind())) {
				AlienStatus.root(owner, e, rain ? SHOT_ROOT / 2 : SHOT_ROOT, 60);
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner);
			}
		} else if (hit instanceof BlockHitResult bh && "stinkfly_slime".equals(proj.kind())) {
			// v0.17.2: a missed Slime Shot leaves a goo patch on the block it hit -- floor, wall or ceiling
			addPatch(owner, l, at.add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.02)), bh.getDirection(), PATCH_HALF, PATCH_TICKS,
					Patch.SHOT);
		}
	}

	/** Roots {@code e} for {@code ticks} ({@code bossTicks} for a boss), a Slowness II {@code slowAfter} after. */
	private static void glue(ServerPlayer by, LivingEntity e, int ticks, int bossTicks, int slowAfter) {
		// AlienStatus.root gives a boss a quarter: hand it four times the boss time
		AlienStatus.root(by, e, AlienStatus.isBoss(e) ? bossTicks * 4 : ticks, slowAfter);
	}

	/**
	 * v0.17.2 (user: "Give his powers actual models and vfx instead of just glowing orbs please"): a goo splat -- a quick wet green
	 * splash bursting out, globs flung out in a crown (back along the shot and up), a few pale and dark flecks. (Fewer particles
	 * since the revamp: the user dislikes lots of them.)
	 */
	public static void splatBurst(ServerLevel l, Vec3 at, Vec3 vel, float k) {
		QuirkFx.blast(l, at).colours(0xF0FFB0, GOO).size(0.45f * k + 0.15f).life(8).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		Vec3 back = vel.lengthSqr() > 1e-6 ? vel.normalize().scale(-1) : new Vec3(0, 1, 0);
		int n = Math.round(5 * k) + 2;
		for (int i = 0; i < n; i++) {
			double a = l.random.nextDouble() * Math.PI * 2;
			Vec3 d = new Vec3(Math.cos(a), 0.6 + l.random.nextDouble() * 0.8, Math.sin(a)).add(back.scale(0.6)).normalize();
			// count 0: (dx, dy, dz) is the particle's motion, scaled by the speed
			l.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.05, at.z, 0, d.x, d.y, d.z, 0.18 + l.random.nextDouble() * 0.12 * k);
		}
		l.sendParticles(GOO_PALE, at.x, at.y, at.z, Math.round(3 * k), 0.2 * k, 0.15 * k, 0.2 * k, 0);
		l.sendParticles(GOO_DARK, at.x, at.y, at.z, Math.round(2 * k), 0.25 * k, 0.1 * k, 0.25 * k, 0);
	}

	private static Vec3 groundBelow(ServerLevel l, Vec3 at) {
		var hit = l.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(0, 0.05, 0) : at;
	}

	/**
	 * v0.17.2 (S2: the 2005 show -- he fires goo from his eye stalks): puts a fresh goo projectile at his eyes (his skin has them
	 * on his face) instead of at his mouth, with a little wet glint there.
	 */
	private static AlienProjectileEntity fromEyes(ServerPlayer p, AlienProjectileEntity e) {
		Vec3 look = p.getLookAngle();
		Vec3 at = p.getEyePosition().add(look.scale(0.5)).add(0, -0.16, 0);
		e.moveTo(at.x, at.y, at.z, p.getYRot(), p.getXRot());
		return e;
	}

	private static void eyeGlint(ServerLevel l, ServerPlayer p) {
		Vec3 eyes = p.getEyePosition().add(p.getLookAngle().scale(0.35));
		QuirkFx.blast(l, eyes).colours(0xF4FFD0, GOO).size(0.3f).life(4).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
	}

	// ---------------------------------------------------------------- goo patches

	/** A goo patch (or pool) on {@code face} at {@code at}: the rendered decal and its stickiness. Oldest ones go past the caps. */
	private static void addPatch(ServerPlayer p, ServerLevel l, Vec3 at, Direction face, float half, int ticks, int kind) {
		List<Patch> list = PATCHES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		int cap = kind == Patch.SHOT ? MAX_SHOT_PATCHES : kind == Patch.RAIN ? MAX_RAIN_PATCHES : Integer.MAX_VALUE;
		int have = 0;
		for (Patch pt : list) {
			have += pt.kind == kind ? 1 : 0;
		}
		for (Iterator<Patch> it = list.iterator(); it.hasNext() && have >= cap;) {
			Patch pt = it.next();
			if (pt.kind == kind) {
				discardFx(l, pt.fx);
				it.remove();
				have--;
			}
		}
		// the decal: a held, unmoving projectile the client draws (its "colour" carries the patch's life for the fade)
		AlienProjectileEntity fx = AlienProjectileEntity.of(p, "stinkfly_goo_" + face.getName()).colour(ticks).size(half * 2f).life(ticks).held(true);
		fx.setDeltaMovement(Vec3.ZERO);
		fx.moveTo(at.x, at.y, at.z, 0f, 0f);
		fx.spawn();
		list.add(new Patch(l.dimension(), at, face, half, l.getGameTime() + ticks, kind, fx.getId()));
	}

	private static void discardFx(ServerLevel l, int id) {
		Entity e = id < 0 ? null : l.getEntity(id);
		if (e != null) {
			e.discard();
		}
	}

	/** v0.17.2 (for tests and other kits): how many goo patches / pools {@code p} has down. */
	public static int patches(ServerPlayer p) {
		List<Patch> list = PATCHES.get(p.getUUID());
		return list == null ? 0 : list.size();
	}

	/** v0.17.2: whether {@code e} stands in one of {@code p}'s goo patches / pools. */
	public static boolean inGoo(ServerPlayer p, LivingEntity e) {
		List<Patch> list = PATCHES.get(p.getUUID());
		if (list != null) {
			for (Patch pt : list) {
				if (pt.dim == e.level().dimension() && pt.box().intersects(e.getBoundingBox())) {
					return true;
				}
			}
		}
		return false;
	}

	private void tickPatches(ServerPlayer p, ServerLevel l, long now) {
		List<Patch> list = PATCHES.get(p.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		for (Iterator<Patch> it = list.iterator(); it.hasNext();) {
			Patch pt = it.next();
			if (now >= pt.until || pt.dim != l.dimension()) {
				discardFx(l, pt.fx);
				it.remove();
				continue;
			}
			if (now % 2 != 0) {
				continue;
			}
			AABB box = pt.box();
			boolean burn = false;
			for (LivingEntity e : around(p, pt.at, pt.half + 1.5)) {
				if (!box.intersects(e.getBoundingBox())) {
					continue;
				}
				e.addEffect(new MobEffectInstance(StinkflyGooEffect.HOLDER, 12, 0, false, false, false), p);
				if (pt.stuck.add(e.getId())) {
					// landing in it: stuck for a second (once per patch)
					AlienStatus.root(p, e, PATCH_STICK, 0);
				}
				burn |= e.isOnFire();
			}
			if (!burn && now % 10 == 0) {
				burn = onFire(l, pt.at, pt.face, box);
			}
			if (burn) {
				// v0.17.2 Sticky Goo: goo is flammable -- one burst and the patch is gone
				StinkflyGooEffect.ignite(l, pt.at, p, null);
				discardFx(l, pt.fx);
				it.remove();
			}
		}
	}

	/** Fire touching goo at {@code at}: a fire / lava / lit-campfire block, a fireball or one of Heatblast's shots in it. */
	private static boolean onFire(ServerLevel l, Vec3 at, Direction face, AABB box) {
		BlockPos in = BlockPos.containing(at.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.3)));
		for (BlockPos q : new BlockPos[] { in, in.relative(face.getOpposite()) }) {
			BlockState s = l.getBlockState(q);
			if (s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA) || (s.getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(s))) {
				return true;
			}
		}
		return !l.getEntitiesOfClass(Projectile.class, box.inflate(0.6), StinkflyKit::fiery).isEmpty();
	}

	/** A projectile that lights goo: a fireball, anything burning, Heatblast's fire. */
	private static boolean fiery(Projectile pr) {
		return pr instanceof AbstractHurtingProjectile && !(pr instanceof net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge)
				|| pr.isOnFire() || pr instanceof AlienProjectileEntity ap && ap.kind().startsWith("heatblast");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "i dont want any of the aliens to have less than 20 hp"): was 14 health, +1 fist
		// v0.17.2 (user: "give armour ratings to the aliens you think deserve armour ratings please but dont make it terrible"): a chitin shell, 6
		// v0.17.2 (user set the unarmed melee bonuses: Four Arms +10, Diamondhead +8 (+4 more with the blades), Upgrade +5, Ghostfreak +5, Stinkfly +4, Wildmutt +6, Ripjaws +5, Grey Matter unchanged)
		return AlienStats.builder().health(20f).speed(1.1f).scale(0.9f).armor(6f, 0f).attack(4f).jump(0.08f).build();
	}

	// v0.17.2 (user: "make stinky able to use items and tools"): no pawsOnly -- his claws hold and use items again. Hitbox and eyes
	// stay the player's, scaled by the 0.9 size stat (0.54 x 1.62, eyes at 1.46: the middle of his head on the model).

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(SLIME_SHOT, AbilitySlot.SLOT_1, 16), AlienMove.shift(SLIME_NET, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(TOXIC_GAS, AbilitySlot.SLOT_2, 220), AlienMove.shift(STINGER, AbilitySlot.SLOT_2, 50),
				// v0.17.2 (user: "double tapping X already makes the player fly, make X wing dash and put another attack move on Shift+X")
				AlienMove.tap(WING_DASH, AbilitySlot.SLOT_3, 60), AlienMove.shift(LUNGE, AbilitySlot.SLOT_3, 100),
				// v0.17.2 (user: "yes build these things please i like them"): the ultimate is the Goo Cocoon now (Goo Storm became Shift+C's
				// smaller Goo Rain; Slime Barrage folded into the held goo spit), and Shift+V is the new Goo Wall
				AlienMove.tap(DIVE_BOMB, AbilitySlot.SLOT_4, 140), AlienMove.shift(GOO_COCOON, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(LIFT, AbilitySlot.SLOT_5, 60), AlienMove.shift(GOO_WALL, AbilitySlot.SLOT_5, 300),
				AlienMove.tap(WING_GUST, AbilitySlot.SLOT_6, 120), AlienMove.shift(GOO_RAIN, AbilitySlot.SLOT_6, 320),
				AlienMove.vanilla(JAB, "Attack", 20), AlienMove.vanilla(GOO_SPIT, "Use", 8),
				AlienMove.vanilla(FLIGHT, "Double-tap jump", 8));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		p.getAbilities().mayfly = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		// v0.17.2 (user: "if the player transforms into stinkfly midair i want the player to automatically start flying"): already
		// airborne (not standing, swimming or riding), he opens his wings and flies rather than falling
		if (!p.onGround() && !p.isInWater() && !p.isPassenger() && !p.onClimbable()) {
			p.getAbilities().flying = true;
			setFlag(p, F_FLYING, true);
			anim(p, ANIM_TAKEOFF);
			p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1));
			p.hurtMarked = true;
			p.resetFallDistance();
		}
		p.onUpdateAbilities();
		OmnitrixSfx.playOn(p, BUZZ, 1.0f, 1.0f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.4, 0.5, 0.4, 0.05);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		ServerLevel l = level(p);
		UUID id = p.getUUID();
		Area gas = GAS_CLOUDS.remove(id);
		if (gas != null) {
			discardFx(l, gas.fx());
		}
		Area rain = RAINS.remove(id);
		if (rain != null) {
			discardFx(l, rain.fx());
		}
		List<Patch> patches = PATCHES.remove(id);
		if (patches != null) {
			patches.forEach(pt -> discardFx(l, pt.fx));
		}
		removeWall(l, id, false);
		CASTS.remove(id);
		COCOONS.entrySet().removeIf(en -> {
			if (en.getValue().owner().equals(id)) {
				discardFx(l, en.getValue().fx());
				return true;
			}
			return false;
		});
		STENCHED.values().removeIf(m -> m.owner().equals(id));
		PINNED.values().removeIf(m -> m.owner().equals(id));
		DROPS.values().removeIf(m -> m.owner().equals(id));
		SPITS.remove(id);
		WET.remove(id);
		CLIPS.remove(id);
		setFlag(p, F_FLYING | F_DIVING | F_WET | F_CLIPPED | F_RAIN | F_COCOON, false);
		if (timer(p, COCOON_TARGET) > 0) {
			setTimer(p, COCOON_TARGET, 0);
		}
		if (timer(p, "dive_at") > 0) {
			setTimer(p, "dive_at", 0);
		}
		if (timer(p, "dive_end") > 0) {
			setTimer(p, "dive_end", 0);
		}
		net.minecraft.world.level.GameType mode = p.gameMode.getGameModeForPlayer();
		if (mode != net.minecraft.world.level.GameType.CREATIVE && mode != net.minecraft.world.level.GameType.SPECTATOR) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		boolean grounded = tickWings(p, l, now);
		// the wings: always ready (a double-tap of jump takes off) unless wet, clipped or burning; others see them beat
		if (!grounded && !p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.getAbilities().setFlyingSpeed(FLY_SPEED);
			p.onUpdateAbilities();
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLYING)) {
			setFlag(p, F_FLYING, flying);
			if (flying) {
				// take-off: a few hard strokes winding up into the buzz
				l.playSound(null, p, OmnitrixSounds.STINKFLY_WINGS_UP, net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 1.0f);
			}
		}
		if (flying && p.tickCount % 16 == 0) {
			// v0.17.1: the insect wingbeat loop (0.85 s, replayed every 16 ticks) -- higher and a touch louder the faster he flies
			float sp = (float) Math.min(1.0, p.getDeltaMovement().length() / 1.2);
			// v0.17.2 (user: "its almost as if we're leaving the sound effect behind"): bound to him, not to where he was
			l.playSound(null, p, OmnitrixSounds.STINKFLY_WINGS, net.minecraft.sounds.SoundSource.PLAYERS, 0.55f + 0.25f * sp,
					0.95f + 0.2f * sp + p.getRandom().nextFloat() * 0.06f);
		}
		if (p.tickCount % 40 == 0) {
			l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.4, p.getZ(), 1, 0.15, 0.1, 0.15, 0); // a goo drip
		}
		p.resetFallDistance();
		// Dive Bomb: the hop up, then the plunge, then the splash
		long diveAt = timer(p, "dive_at");
		if (diveAt > 0 && now >= diveAt) {
			setTimer(p, "dive_at", 0);
			startDive(p, now);
		}
		if (flag(p, F_DIVING)) {
			long end = timer(p, "dive_end");
			boolean landed = p.onGround() || p.isInWaterOrBubble() || p.horizontalCollision || p.verticalCollisionBelow;
			if (landed || now >= end) {
				diveImpact(p);
			} else {
				Vec3 v = p.getDeltaMovement();
				if (v.y > -1.6) {
					p.setDeltaMovement(v.x, Math.min(v.y, -0.9) - 0.12, v.z);
					p.hurtMarked = true;
				}
				// v0.17.2: goo streaming off him (the trail ribbon is the wake; fewer particles)
				if (now % 2 == 0) {
					l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.8, p.getZ(), 1, 0.25, 0.3, 0.25, 0.02);
				}
			}
		}
		tickGas(p, l, now);
		tickRain(p, l, now);
		tickPatches(p, l, now);
		tickStench(p, l, now);
		tickCocoon(p, l, now);
		tickWall(p, l, now);
		tickDrops(p, l, now);
		tickSpit(p, now);
		if (now % 10 == 0) {
			stenchPassive(p, l);
		}
	}

	// ---------------------------------------------------------------- v0.17.2 weaknesses: Wet Wings, Fragile Wings, Fire

	/**
	 * Wet Wings (in water he can't take off, and for 5 s after it; rain slows his flight), Fragile Wings (a hit of 8+ in flight
	 * clips them: 2 s gliding, 10 s internal cooldown) and Fire (burning, he only glides). Returns whether he is grounded.
	 */
	private boolean tickWings(ServerPlayer p, ServerLevel l, long now) {
		UUID id = p.getUUID();
		if (p.isInWater()) {
			WET.put(id, now + WET_TICKS);
		}
		boolean wet = WET.getOrDefault(id, 0L) > now;
		long[] clip = CLIPS.get(id);
		boolean clipped = clip != null && clip[0] > now;
		boolean burning = p.isOnFire() && !p.fireImmune() && !creative(p);
		if (wet != flag(p, F_WET)) {
			setFlag(p, F_WET, wet);
		}
		if ((clipped || burning) != flag(p, F_CLIPPED)) {
			setFlag(p, F_CLIPPED, clipped || burning);
		}
		if (now % 10 == 0) {
			boolean rain = l.isRainingAt(BlockPos.containing(p.getX(), p.getEyeY() + 1.0, p.getZ()));
			if (rain != flag(p, F_RAIN)) {
				setFlag(p, F_RAIN, rain);
			}
		}
		boolean grounded = (wet || clipped || burning) && !creative(p);
		if (grounded) {
			if (p.getAbilities().mayfly || p.getAbilities().flying) {
				p.getAbilities().flying = false;
				p.getAbilities().mayfly = false;
				p.onUpdateAbilities();
			}
			// he glides: no climbing, a slow sink
			if (!p.onGround() && !p.isInWater() && p.getDeltaMovement().y < 0) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 6, 0, false, false, false));
			}
			if (now % 6 == 0 && wet && !p.isInWater()) {
				l.sendParticles(ParticleTypes.DRIPPING_WATER, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.3, 0.1, 0.3, 0); // the wings drip
			}
		}
		return grounded;
	}

	/** Creative / spectator by the game mode (as onRevert asks; a test's mock player answers isCreative() true whatever its mode). */
	private static boolean creative(ServerPlayer p) {
		net.minecraft.world.level.GameType mode = p.gameMode.getGameModeForPlayer();
		return mode == net.minecraft.world.level.GameType.CREATIVE || mode == net.minecraft.world.level.GameType.SPECTATOR;
	}

	/** v0.17.2 Wet Wings: soaks his wings as if he just left water (5 s; for tests -- the tick does it while he is in water). */
	public static void soak(ServerPlayer p) {
		WET.put(p.getUUID(), p.level().getGameTime() + WET_TICKS);
	}

	/** v0.17.2 Wet Wings (client-safe, synced flags): how fast he flies -- 70% in the rain. */
	public static float flightFactor(Player p) {
		AlienKit k = activeKit(p);
		return k instanceof StinkflyKit kit && kit.flag(p, F_RAIN) ? RAIN_FLIGHT : 1f;
	}

	// ---------------------------------------------------------------- v0.17.2 the stench cloud, the rain, the stench

	private void tickGas(ServerPlayer p, ServerLevel l, long now) {
		Area gas = GAS_CLOUDS.get(p.getUUID());
		if (gas == null) {
			return;
		}
		if (now >= gas.until() || gas.dim() != l.dimension()) {
			GAS_CLOUDS.remove(p.getUUID());
			discardFx(l, gas.fx());
			return;
		}
		// v0.17.2 (the revamp: "fewer particles"): the cloud is a rendered volume now (StinkflyGooFx); only the odd sneeze
		if (now % 8 == 0) {
			l.sendParticles(ParticleTypes.SNEEZE, gas.at().x, gas.at().y + 0.8, gas.at().z, 2, GAS_RADIUS * 0.5, 0.5, GAS_RADIUS * 0.5, 0.01);
		}
		if (now % 10 == 0) {
			for (LivingEntity e : around(p, gas.at().add(0, 0.8, 0), GAS_RADIUS)) {
				hurt(p, e, GAS_DAMAGE, false);
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), p);
				e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0), p);
				// v0.17.2 Stench Cloud: mobs inside lose their target, and wander for 2 s after leaving
				if (e instanceof Mob m && !AlienStatus.isBoss(m)) {
					STENCHED.put(m.getUUID(), new Mark(p.getUUID(), now + STENCH_LINGER));
				}
			}
		}
	}

	private void tickStench(ServerPlayer p, ServerLevel l, long now) {
		if (STENCHED.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Mark>> it = STENCHED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Mark> en = it.next();
			if (!en.getValue().owner().equals(p.getUUID())) {
				continue;
			}
			Entity e = l.getEntity(en.getKey());
			if (!(e instanceof Mob m) || !m.isAlive() || now >= en.getValue().until()) {
				it.remove();
				continue;
			}
			if (m.getTarget() != null) {
				m.setTarget(null);
				m.setLastHurtByMob(null);
				m.getNavigation().stop();
			}
		}
	}

	/** v0.17.2 (Wildmutt's Choked Nose may ask): whether {@code e} is inside any Stinkfly's stench cloud. */
	public static boolean inStenchCloud(Entity e) {
		for (Area a : GAS_CLOUDS.values()) {
			if (a.dim() == e.level().dimension() && e.distanceToSqr(a.at().add(0, 0.8, 0)) <= GAS_RADIUS * GAS_RADIUS * 1.4) {
				return true;
			}
		}
		return false;
	}

	/** v0.17.2 Stench (passive): animals and villagers keep 6 blocks away from him. */
	private static void stenchPassive(ServerPlayer p, ServerLevel l) {
		for (PathfinderMob m : l.getEntitiesOfClass(PathfinderMob.class, p.getBoundingBox().inflate(STENCH_RANGE),
				m -> (m instanceof Animal && !(m instanceof TamableAnimal t && t.isTame()) || m instanceof AbstractVillager) && m.isAlive()
						&& !m.isLeashed() && !m.isPassenger() && m.distanceToSqr(p) < STENCH_RANGE * STENCH_RANGE)) {
			Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(m, 10, 4, p.position());
			if (away != null) {
				m.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
			}
		}
	}

	private void tickRain(ServerPlayer p, ServerLevel l, long now) {
		Area rain = RAINS.get(p.getUUID());
		if (rain == null) {
			return;
		}
		if (now >= rain.until() || rain.dim() != l.dimension()) {
			RAINS.remove(p.getUUID());
			discardFx(l, rain.fx());
			return;
		}
		// v0.17.2: the cloud and its falling globs are rendered (StinkflyGooFx); a glob landing now and then leaves a patch
		if (now % 15 == 0) {
			double a = l.random.nextDouble() * Math.PI * 2, r = Math.sqrt(l.random.nextDouble()) * RAIN_RADIUS * 0.85;
			Vec3 drop = groundBelow(l, new Vec3(rain.at().x + Math.cos(a) * r, rain.at().y + 1.0, rain.at().z + Math.sin(a) * r));
			addPatch(p, l, drop.add(0, -0.03, 0), Direction.UP, PATCH_HALF, RAIN_TICKS + 20, Patch.RAIN);
			splatBurst(l, drop, new Vec3(0, -1, 0), 0.5f);
		}
		if (now % 10 == 0) {
			for (LivingEntity e : around(p, rain.at().add(0, 1, 0), RAIN_RADIUS)) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), p);
				if (now % 20 == 0) {
					hurt(p, e, RAIN_DAMAGE, false);
				}
			}
		}
		if (now % 30 == 0) {
			OmnitrixSounds.play(l, rain.at(), SPLAT, 0.6f, 0.7f + l.random.nextFloat() * 0.3f);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					slimeNet(p);
				} else {
					slimeShot(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					stingerStab(p);
				} else {
					toxicGas(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					stingerLunge(p);
				} else {
					wingDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					gooCocoon(p); // v0.17.2: was Goo Storm
				} else {
					diveBomb(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					gooRain(p); // v0.17.2: was Slime Barrage
				} else {
					wingGust(p);
				}
			}
			default -> {
			}
		}
	}

	public boolean slimeShot(ServerPlayer p) {
		if (!use(p, SLIME_SHOT)) {
			return false;
		}
		anim(p, ANIM_SHOT);
		fromEyes(p, AlienProjectileEntity.of(p, "stinkfly_slime")).colour(GOO).size(0.4f).damage(SHOT_DAMAGE).gravity(0.012).speed(1.6).life(50).spawn();
		eyeGlint(level(p), p);
		OmnitrixSounds.play(level(p), p.position(), SPIT, 0.9f, 0.95f + p.getRandom().nextFloat() * 0.15f);
		return true;
	}

	public boolean slimeNet(ServerPlayer p) {
		if (!use(p, SLIME_NET)) {
			return false;
		}
		anim(p, ANIM_NET);
		fromEyes(p, AlienProjectileEntity.of(p, "stinkfly_net")).colour(0xD8F070).size(0.85f).damage(NET_DAMAGE).gravity(0.025).speed(1.15).life(60).spawn();
		eyeGlint(level(p), p);
		OmnitrixSounds.play(level(p), p.position(), SPIT, 1.0f, 0.7f);
		return true;
	}

	/** G: Stench Cloud (the id stays toxic_gas: cooldowns and saves). */
	public boolean toxicGas(ServerPlayer p) {
		if (!use(p, TOXIC_GAS)) {
			return false;
		}
		anim(p, ANIM_GAS);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		var hit = l.clip(new ClipContext(eye, eye.add(look.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = groundBelow(l, hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.5)) : eye.add(look.scale(4)));
		Area old = GAS_CLOUDS.get(p.getUUID());
		if (old != null) {
			discardFx(l, old.fx());
		}
		GAS_CLOUDS.put(p.getUUID(), new Area(l.dimension(), at, l.getGameTime() + GAS_TICKS, cloudFx(p, "stinkfly_cloud", at, GAS_RADIUS, GAS_TICKS)));
		// v0.17.2 (the revamp: "fewer particles"): the belch is one rolling puff; the cloud itself is rendered
		Vec3 mouth = eye.add(look.scale(0.4)).add(0, -0.15, 0);
		QuirkFx.flipbook(l, mouth.add(look.scale(0.8))).style(QuirkFx.BOOK_SMOKE).colours(0xC8E070, 0x5A7A20).size(0.9f).life(14).spawn();
		QuirkFx.ring(l, at).colours(0xC8E070, 0x6A8A20).style(QuirkFx.RING_STEAM).size(GAS_RADIUS + 0.5f).thickness(0.8f).life(GAS_TICKS / 2).ground().spawn();
		OmnitrixSounds.play(l, at, GAS, 1.0f, 1.0f);
		return true;
	}

	/** A rendered cloud (Stench Cloud / Goo Rain): a held projectile the client draws as puffs ("colour" = its life). */
	private static int cloudFx(ServerPlayer p, String kind, Vec3 at, float radius, int ticks) {
		AlienProjectileEntity fx = AlienProjectileEntity.of(p, kind).colour(ticks).size(radius).life(ticks).held(true);
		fx.setDeltaMovement(Vec3.ZERO);
		fx.moveTo(at.x, at.y, at.z, 0f, 0f);
		fx.spawn();
		return fx.getId();
	}

	public boolean stingerStab(ServerPlayer p) {
		if (!use(p, STINGER)) {
			return false;
		}
		anim(p, ANIM_STAB);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), STING, 1.0f, 1.0f);
		LivingEntity t = lookTarget(p, 3.75);
		if (t != null) {
			hurt(p, t, STAB_DAMAGE, false);
			t.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), p);
			l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
			// v0.17.2: the venom going in -- a poison swirl round the wound and a dark green spurt
			l.sendParticles(POISON_SWIRL, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.3, 0.3, 0.3, 0.6);
			l.sendParticles(GOO_DARK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 4, 0.15, 0.15, 0.15, 0);
		}
		return true;
	}

	/** Take off (a hop up into flight) or land. The wings are always ready, so a double-tap of jump flies too. */
	public boolean wingFlight(ServerPlayer p) {
		if (!use(p, FLIGHT)) {
			return false;
		}
		ServerLevel l = level(p);
		if (p.getAbilities().flying) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			setFlag(p, F_FLYING, false);
			OmnitrixSfx.playOn(p, BUZZ, 0.6f, 0.8f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
			return true;
		}
		// v0.17.2 Wet / Fragile Wings: soaked, clipped or burning wings can't lift him
		if (flag(p, F_WET) || flag(p, F_CLIPPED)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.stinkfly.wings_down").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		p.getAbilities().mayfly = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		setFlag(p, F_FLYING, true);
		anim(p, ANIM_TAKEOFF);
		p.push(0, 0.5, 0);
		p.hurtMarked = true;
		OmnitrixSfx.playOn(p, BUZZ, 1.0f, 1.1f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xF0FFE0, COLOUR).style(QuirkFx.RING_SOFT).size(2.2f).thickness(0.4f).life(10).ground().spawn();
		return true;
	}

	public boolean wingDash(ServerPlayer p) {
		if (!use(p, WING_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 look = p.getLookAngle();
		// v0.17.2 Wet Wings: in the rain the dash goes half as far
		p.setDeltaMovement(look.scale(flag(p, F_RAIN) ? 0.85 : 1.7).add(0, 0.1, 0));
		p.hurtMarked = true;
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xF0FFE0, COLOUR).size(0.35f).size2(8f).life(16).vec(new Vec3(0, 0.9, 0)).spawn();
		OmnitrixSfx.playOn(p, BUZZ, 1.0f, 1.4f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		return true;
	}

	/**
	 * Shift+X: Stinger Lunge -- he darts forward stinger-first up to 8 blocks (stopping at a wall); everything in the path takes
	 * 14 and is poisoned.
	 */
	public boolean stingerLunge(ServerPlayer p) {
		if (!use(p, LUNGE)) {
			return false;
		}
		anim(p, ANIM_STAB);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 from = p.position().add(0, p.getBbHeight() * 0.5, 0);
		var wall = l.clip(new ClipContext(from, from.add(look.scale(LUNGE_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double reach = wall.getType() == HitResult.Type.BLOCK ? Math.max(0, wall.getLocation().distanceTo(from) - 0.6) : LUNGE_RANGE;
		for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, from.add(look.scale(reach))).inflate(1.0), e -> e != p && e.isAlive())) {
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			double along = c.subtract(from).dot(look);
			if (along < -0.5 || along > reach + 0.8 || c.distanceTo(from.add(look.scale(Math.max(0, along)))) > 1.2 + t.getBbWidth() * 0.5) {
				continue;
			}
			hurt(p, t, LUNGE_DAMAGE, false);
			t.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), p);
			l.sendParticles(POISON_SWIRL, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.6);
			l.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.3);
		}
		// the dart itself: a hard burst along the path that bleeds off
		p.setDeltaMovement(look.scale(Math.min(2.2, 0.6 + reach * 0.22)).add(0, 0.08, 0));
		p.hurtMarked = true;
		QuirkFx.trail(l, p).colours(0xF0FFE0, COLOUR).size(0.3f).size2(6f).life(12).vec(new Vec3(0, 0.9, 0)).spawn();
		OmnitrixSfx.playOn(p, DIVE, 1.0f, 1.3f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		OmnitrixSounds.play(l, p.position(), STING, 1.0f, 0.9f);
		return true;
	}

	public boolean diveBomb(ServerPlayer p) {
		if (!use(p, DIVE_BOMB)) {
			return false;
		}
		ServerLevel l = level(p);
		long now = l.getGameTime();
		if (p.onGround()) {
			// a hop up first, then the plunge
			anim(p, ANIM_TAKEOFF);
			p.setDeltaMovement(p.getDeltaMovement().x, 1.25, p.getDeltaMovement().z);
			p.hurtMarked = true;
			setTimer(p, "dive_at", now + 9);
			OmnitrixSfx.playOn(p, BUZZ, 1.0f, 1.2f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		} else {
			startDive(p, now);
		}
		return true;
	}

	private void startDive(ServerPlayer p, long now) {
		anim(p, ANIM_DIVE);
		if (p.getAbilities().flying) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.min(-0.65, look.y), look.z).normalize();
		p.setDeltaMovement(dir.scale(2.1));
		p.hurtMarked = true;
		setFlag(p, F_DIVING, true);
		setTimer(p, "dive_end", now + 60);
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xF0FFE0, GOO).size(0.4f).size2(10f).life(24).vec(new Vec3(0, 0.6, 0)).spawn();
		OmnitrixSfx.playOn(p, DIVE, 1.0f, 1.0f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
	}

	/** The splash-down: a goo shockwave around where he lands, and (v0.17.2) a 3-block goo pool left for 5 s. */
	public void diveImpact(ServerPlayer p) {
		setFlag(p, F_DIVING, false);
		setTimer(p, "dive_end", 0);
		anim(p, ANIM_SPLASH);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		QuirkFx.blast(l, c.add(0, 0.3, 0)).colours(0xF0FFB0, GOO).size(DIVE_RADIUS * 0.7f).life(16).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xF0FFB0, GOO).size(DIVE_RADIUS + 0.5f).thickness(0.6f).life(14).ground().spawn();
		// v0.17.2: a crown of goo thrown out round him (fewer particles: 12 globs) and splats where it lands
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI * 2 / 12 + l.random.nextDouble() * 0.2;
			l.sendParticles(ParticleTypes.ITEM_SLIME, c.x + Math.cos(a) * 0.6, c.y + 0.2, c.z + Math.sin(a) * 0.6, 0, Math.cos(a), 0.7, Math.sin(a), 0.45);
		}
		for (int i = 0; i < 3; i++) {
			double a = i * Math.PI * 2 / 3 + l.random.nextDouble();
			splatBurst(l, c.add(Math.cos(a) * DIVE_RADIUS * 0.6, 0.1, Math.sin(a) * DIVE_RADIUS * 0.6), new Vec3(0, -1, 0), 0.8f);
		}
		OmnitrixSounds.play(l, c, SPLAT, 1.3f, 0.6f);
		QuirkCamera.shakeAround(l, c, 10, 0.35f, 8);
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), DIVE_RADIUS)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			// v0.17.2: 18 at the centre falling to 11 at the edge (was 8 x (1 - 0.4 x d/r))
			hurt(p, e, (float) (DIVE_DAMAGE - (DIVE_DAMAGE - DIVE_EDGE_DAMAGE) * Math.min(1.0, d / DIVE_RADIUS)), false);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), p);
			shove(e, c, 0.6, 0.3);
		}
		// v0.17.2: the pool (the honey-slow of a patch; replaces the old flat decal)
		addPatch(p, l, groundBelow(l, c.add(0, 0.3, 0)).add(0, -0.03, 0), Direction.UP, POOL_HALF, POOL_TICKS, Patch.POOL);
		p.resetFallDistance();
	}

	public boolean wingGust(ServerPlayer p) {
		if (!use(p, WING_GUST)) {
			return false;
		}
		anim(p, ANIM_GUST);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		QuirkFx.cone(l, eye.add(look.scale(0.6)).add(0, -0.3, 0), look).colours(0xF4FFF0, 0xA8D870).style(QuirkFx.CONE_DEBRIS).size(2.6f).length(7f)
				.life(12).spawn();
		gustRings(l, eye.add(0, -0.3, 0), look);
		OmnitrixSfx.playOn(p, BUZZ, 1.2f, 0.6f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		p.clearFire();
		for (LivingEntity e : around(p, eye.add(look.scale(3.5)), 4.5)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= 7.5 && to.normalize().dot(look) > Math.cos(Math.toRadians(40))) {
				hurt(p, e, GUST_DAMAGE, false);
				e.clearFire();
				e.push(look.x * 1.5, 0.35, look.z * 1.5);
				e.hurtMarked = true;
			}
		}
		// projectiles caught in the gust are blown away (never the goo's own rendered patches / clouds)
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().expandTowards(look.scale(6)).inflate(1.5),
				pr -> pr.getOwner() != p && !(pr instanceof AlienProjectileEntity ap && ap.held()))) {
			l.sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
			pr.discard();
		}
		// v0.17.2: a gust that hits his own Stench Cloud or Goo Rain carries it 8 blocks the way he looks
		carry(p, l, GAS_CLOUDS, eye, look, 0.0);
		carry(p, l, RAINS, eye, look, 0.0);
		return true;
	}

	private static void carry(ServerPlayer p, ServerLevel l, Map<UUID, Area> areas, Vec3 eye, Vec3 look, double lift) {
		Area a = areas.get(p.getUUID());
		if (a == null || a.dim() != l.dimension()) {
			return;
		}
		Vec3 to = a.at().add(0, 1.0, 0).subtract(eye);
		if (to.length() > 10.0 || to.normalize().dot(look) < Math.cos(Math.toRadians(50))) {
			return;
		}
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
		Vec3 at = groundBelow(l, a.at().add(h.scale(GUST_CARRY)).add(0, 1.0 + lift, 0));
		areas.put(p.getUUID(), new Area(a.dim(), at, a.until(), a.fx()));
		Entity fx = l.getEntity(a.fx());
		if (fx != null) {
			fx.moveTo(at.x, at.y, at.z);
		}
	}

	/** v0.17.2 (for tests): where {@code p}'s stench cloud sits, or null. */
	@org.jetbrains.annotations.Nullable
	public static Vec3 gasCloud(ServerPlayer p) {
		Area a = GAS_CLOUDS.get(p.getUUID());
		return a == null ? null : a.at();
	}

	/**
	 * v0.17.2 (user: "Give his powers actual models and vfx instead of just glowing orbs please"; fewer since the revamp): the gust
	 * as rings of wind sweeping out along the cone, wider the further they go, with wind-charge puffs.
	 */
	private static void gustRings(ServerLevel l, Vec3 from, Vec3 look) {
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(look).normalize();
		for (int ring = 0; ring < 3; ring++) {
			double dist = 1.2 + ring * 2.0, rad = 0.5 + ring * 1.0;
			int n = 8 + ring * 3;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + ring * 0.4;
				Vec3 off = side.scale(Math.cos(a) * rad).add(up.scale(Math.sin(a) * rad));
				Vec3 q = from.add(look.scale(dist)).add(off);
				Vec3 m = look.scale(1.0).add(off.normalize().scale(0.35));
				l.sendParticles(ParticleTypes.CLOUD, q.x, q.y, q.z, 0, m.x, m.y, m.z, 0.35);
			}
			Vec3 c = from.add(look.scale(dist));
			l.sendParticles(ParticleTypes.SMALL_GUST, c.x, c.y, c.z, 1, rad * 0.5, rad * 0.5, rad * 0.5, 0);
		}
	}

	/**
	 * v0.17.2 Shift+C: Goo Rain (the old Goo Storm, now a regular move) -- a green cloud above the spot he looks at rains globs for
	 * 4 s: everything under it is slowed and takes 3 a second (12), and globs leave goo patches where they land.
	 */
	public boolean gooRain(ServerPlayer p) {
		if (!use(p, GOO_RAIN)) {
			return false;
		}
		anim(p, ANIM_STORM);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		var hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(20)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : groundBelow(l, p.position());
		at = groundBelow(l, at.add(0, 0.5, 0));
		Area old = RAINS.get(p.getUUID());
		if (old != null) {
			discardFx(l, old.fx());
		}
		RAINS.put(p.getUUID(), new Area(l.dimension(), at, l.getGameTime() + RAIN_TICKS, cloudFx(p, "stinkfly_rain", at, RAIN_RADIUS, RAIN_TICKS)));
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xF0FFB0, GOO).size(RAIN_RADIUS).thickness(0.5f).life(20).ground().spawn();
		eyeGlint(l, p);
		OmnitrixSounds.play(l, at, GAS, 1.0f, 0.6f);
		OmnitrixSounds.play(l, p.position(), SPIT, 1.0f, 0.6f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.2 Shift+Z: the Goo Cocoon

	/**
	 * Shift+Z (ULTIMATE): Goo Cocoon -- he hovers and pumps a thick stream of goo from his eyes at the creature he looks at (20
	 * blocks) for 1.5 s; the spray glues everything within 4 of it for 2 s. Then the creature is wrapped in a cocoon: it can't
	 * move or attack for 5 s (a boss 1.5 s) and takes 25% more damage. When the cocoon bursts it lets out a gas puff (8 +
	 * nausea, r 3).
	 */
	public boolean gooCocoon(ServerPlayer p) {
		if (CASTS.containsKey(p.getUUID())) {
			return false;
		}
		LivingEntity t = lookTarget(p, COCOON_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.stinkfly.no_cocoon_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, GOO_COCOON)) {
			return false;
		}
		ServerLevel l = level(p);
		long now = l.getGameTime();
		anim(p, ANIM_COCOON);
		CASTS.put(p.getUUID(), new Cast(t.getId(), now + COCOON_STREAM));
		setTimer(p, COCOON_TARGET, t.getId() + 1L); // +1: a timer of 0 means unset
		setFlag(p, F_COCOON, true);
		OmnitrixSounds.play(l, p.position(), GUSH, 1.2f, 0.8f);
		OmnitrixSfx.playOn(p, BUZZ, 1.2f, 0.9f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	/** v0.17.2 (client-safe): the entity id the Goo Cocoon stream is pumped at, or -1. */
	public int cocoonTarget(Player p) {
		long t = timer(p, COCOON_TARGET);
		return flag(p, F_COCOON) && t > 0 ? (int) (t - 1L) : -1;
	}

	/** v0.17.2 (for tests): whether {@code e} is in a Goo Cocoon. */
	public static boolean cocooned(LivingEntity e) {
		Cocoon c = COCOONS.get(e.getUUID());
		return c != null && c.until() > e.level().getGameTime();
	}

	private void endCast(ServerPlayer p) {
		CASTS.remove(p.getUUID());
		setFlag(p, F_COCOON, false);
		setTimer(p, COCOON_TARGET, 0);
	}

	private void tickCocoon(ServerPlayer p, ServerLevel l, long now) {
		Cast cast = CASTS.get(p.getUUID());
		if (cast != null) {
			Entity e = l.getEntity(cast.target());
			if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(p) > (COCOON_RANGE + 4) * (COCOON_RANGE + 4)) {
				endCast(p);
			} else {
				// hovering in place while he pumps
				if (!p.onGround()) {
					p.setDeltaMovement(p.getDeltaMovement().scale(0.2).multiply(1, 0, 1));
					p.hurtMarked = true;
					p.resetFallDistance();
				}
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
				if (now % 6 == 0) {
					QuirkFx.blast(l, c).colours(0xF0FFB0, GOO).size(0.5f + t.getBbWidth() * 0.5f).life(6).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
					OmnitrixSounds.play(l, c, SPLAT, 0.5f, 0.8f + l.random.nextFloat() * 0.2f);
				}
				if (now % 10 == 0) {
					// the spray: everything within 4 of the stream's end is glued for 2 s
					for (LivingEntity o : around(p, c, COCOON_SPRAY)) {
						if (o != t) {
							o.addEffect(new MobEffectInstance(StinkflyGooEffect.HOLDER, 40, 0, false, false, false), p);
						}
					}
				}
				if (now >= cast.end()) {
					endCast(p);
					cocoon(p, l, t, now);
				}
			}
		}
		if (COCOONS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Cocoon>> it = COCOONS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Cocoon> en = it.next();
			Cocoon c = en.getValue();
			if (!c.owner().equals(p.getUUID())) {
				continue;
			}
			Entity e = l.getEntity(en.getKey());
			if (!(e instanceof LivingEntity t) || !t.isAlive()) {
				discardFx(l, c.fx());
				it.remove();
				continue;
			}
			Entity fx = l.getEntity(c.fx());
			if (fx != null && fx.position().distanceToSqr(t.position()) > 0.01) {
				fx.moveTo(t.getX(), t.getY(), t.getZ());
			}
			if (now >= c.until()) {
				it.remove();
				discardFx(l, c.fx());
				burstCocoon(p, l, t);
			}
		}
	}

	/** Wraps {@code t}: rooted, no attacks (Weakness V), forgets its target; the shell is drawn round its hitbox. */
	private void cocoon(ServerPlayer p, ServerLevel l, LivingEntity t, long now) {
		boolean boss = AlienStatus.isBoss(t);
		int ticks = boss ? COCOON_BOSS_TICKS : COCOON_TICKS;
		glue(p, t, COCOON_TICKS, COCOON_BOSS_TICKS, 0);
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false, true), p);
		t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 4, false, false, true), p);
		if (t instanceof Mob m && !boss) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		if (t instanceof Player pl) {
			pl.stopUsingItem();
		}
		// the shell: a held projectile the client draws round the hitbox ("colour" carries the hitbox width, in thousandths)
		AlienProjectileEntity fx = AlienProjectileEntity.of(p, boss ? "stinkfly_cocoon_boss" : "stinkfly_cocoon").colour(Math.round(t.getBbWidth() * 1000))
				.size(t.getBbHeight()).life(ticks + 5).held(true);
		fx.setDeltaMovement(Vec3.ZERO);
		fx.moveTo(t.getX(), t.getY(), t.getZ(), 0f, 0f);
		fx.spawn();
		COCOONS.put(t.getUUID(), new Cocoon(p.getUUID(), now + ticks, fx.getId()));
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		splatBurst(l, c, new Vec3(0, -1, 0), 1.2f);
		OmnitrixSounds.play(l, c, SPLAT, 1.2f, 0.6f);
	}

	/** The cocoon splits: its shell falls away in pieces and a gas puff (8 + nausea 3 s, r 3) goes off. */
	private void burstCocoon(ServerPlayer p, ServerLevel l, LivingEntity t) {
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		QuirkFx.shards(l, c).colours(0xD8F070, 0x6A9A1A).size(Math.max(0.8f, t.getBbHeight() * 0.6f)).count(4).life(26).spawn();
		QuirkFx.flipbook(l, c).style(QuirkFx.BOOK_SMOKE).colours(0xC8E070, 0x5A7A20).size(1.6f + t.getBbWidth()).life(24).spawn();
		OmnitrixSounds.play(l, c, GAS, 1.0f, 0.8f);
		OmnitrixSounds.play(l, c, SPLAT, 1.0f, 0.7f);
		for (LivingEntity e : around(p, c, COCOON_BURST_RADIUS)) {
			hurt(p, e, COCOON_BURST, false);
			e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0), p);
		}
	}

	// ---------------------------------------------------------------- v0.17.2 Shift+V: the Goo Wall

	/**
	 * Shift+V: Goo Wall -- a 3 x 3 sheet of goo spat across the space in front (standing on the ground, or spanning a gap), 12 s,
	 * 30 HP. Projectiles stick in it (5 HP each); creatures that walk in are glued 2 s (5 HP each). Wing Gust doesn't break it;
	 * fire burns it away at once.
	 */
	public boolean gooWall(ServerPlayer p) {
		if (!use(p, GOO_WALL)) {
			return false;
		}
		anim(p, ANIM_WALL);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Direction dir = Direction.getNearest(look.x, 0, look.z);
		Vec3 base = p.position().add(Vec3.atLowerCornerOf(dir.getNormal()).scale(2.5));
		// snapped to the block grid along its normal (a door or a corridor is sealed edge to edge)
		double nx = dir.getAxis() == Direction.Axis.X ? Math.floor(base.x) + 0.5 : base.x;
		double nz = dir.getAxis() == Direction.Axis.Z ? Math.floor(base.z) + 0.5 : base.z;
		Vec3 ground = groundBelow(l, new Vec3(nx, base.y + 1.0, nz));
		boolean gap = ground.y > base.y + 0.9 || ground.y < base.y - 5.5;
		Vec3 at = new Vec3(nx, (gap ? base.y : ground.y) + 1.5, nz);
		removeWall(l, p.getUUID(), false);
		AlienProjectileEntity fx = AlienProjectileEntity.of(p, "stinkfly_wall_" + dir.getAxis().getName()).colour(WALL_TICKS).size(3f).life(WALL_TICKS)
				.held(true);
		fx.setDeltaMovement(Vec3.ZERO);
		fx.moveTo(at.x, at.y, at.z, 0f, 0f);
		fx.spawn();
		WALLS.put(p.getUUID(), new Wall(l.dimension(), at, dir.getAxis(), l.getGameTime() + WALL_TICKS, fx.getId()));
		eyeGlint(l, p);
		splatBurst(l, at, look, 1.2f);
		OmnitrixSounds.play(l, at, SPLAT, 1.2f, 0.7f);
		OmnitrixSounds.play(l, p.position(), SPIT, 1.0f, 0.75f);
		return true;
	}

	/** v0.17.2 (for tests): the Goo Wall's HP (0 = no wall). */
	public static int wallHp(ServerPlayer p) {
		Wall w = WALLS.get(p.getUUID());
		return w == null ? 0 : w.hp;
	}

	/** v0.17.2 (for tests): the Goo Wall's centre, or null. */
	@org.jetbrains.annotations.Nullable
	public static Vec3 wallAt(ServerPlayer p) {
		Wall w = WALLS.get(p.getUUID());
		return w == null ? null : w.at;
	}

	private static void removeWall(ServerLevel l, UUID owner, boolean burnt) {
		Wall w = WALLS.remove(owner);
		if (w == null) {
			return;
		}
		discardFx(l, w.fx);
		for (int id : w.stuck) {
			Entity e = l.getEntity(id);
			if (e != null) {
				e.setNoGravity(false); // stuck arrows drop out of it
			}
		}
		if (burnt) {
			StinkflyGooEffect.ignite(l, w.at, null, null);
		} else {
			splatBurst(l, w.at.add(0, -1.0, 0), new Vec3(0, -1, 0), 0.9f);
		}
	}

	private void tickWall(ServerPlayer p, ServerLevel l, long now) {
		Wall w = WALLS.get(p.getUUID());
		if (w == null) {
			return;
		}
		if (now >= w.until || w.dim != l.dimension() || w.hp <= 0) {
			removeWall(l, p.getUUID(), false);
			return;
		}
		AABB box = w.box();
		boolean burn = false;
		// projectiles: stuck fast (shown where they hit); fire burns the sheet away
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, box.inflate(3.0),
				pr -> pr.isAlive() && !(pr instanceof AlienProjectileEntity ap && ap.held()) && pr.getOwner() != p)) {
			if (w.stuck.contains(pr.getId())) {
				pr.setDeltaMovement(Vec3.ZERO);
				continue;
			}
			Vec3 v = pr.getDeltaMovement();
			if (!pr.getBoundingBox().expandTowards(v).intersects(box)) {
				continue;
			}
			if (fiery(pr)) {
				burn = true;
				pr.discard();
				break;
			}
			if (pr instanceof AlienProjectileEntity) {
				splatBurst(l, pr.position(), v, 0.5f);
				pr.discard();
			} else {
				// into the sheet's plane, then held there
				Vec3 n = w.normal();
				double d = pr.position().subtract(w.at).dot(n);
				Vec3 at = pr.position().subtract(n.scale(d * 0.8));
				pr.setDeltaMovement(Vec3.ZERO);
				pr.setNoGravity(true);
				pr.moveTo(at.x, at.y, at.z);
				w.stuck.add(pr.getId());
			}
			w.hp -= 5;
			OmnitrixSounds.play(l, pr.position(), SPLAT, 0.6f, 1.2f);
		}
		if (!burn && now % 2 == 0) {
			for (LivingEntity e : around(p, w.at, 3.0)) {
				if (!box.intersects(e.getBoundingBox())) {
					continue;
				}
				if (e.isOnFire()) {
					burn = true;
					break;
				}
				if (w.glued.getOrDefault(e.getId(), 0L) <= now) {
					// walked into it: glued 2 s and pushed back out the side it came from
					w.glued.put(e.getId(), now + WALL_GLUE + 20);
					AlienStatus.root(p, e, WALL_GLUE, 0);
					e.addEffect(new MobEffectInstance(StinkflyGooEffect.HOLDER, WALL_GLUE, 0, false, false, false), p);
					Vec3 n = w.normal();
					double side = Math.signum(e.position().subtract(w.at).dot(n));
					e.push(n.x * side * 0.35, 0, n.z * side * 0.35);
					e.hurtMarked = true;
					w.hp -= 5;
					OmnitrixSounds.play(l, e.position(), SPLAT, 0.7f, 0.9f);
				}
			}
		}
		if (!burn && now % 10 == 0) {
			burn = onFire(l, w.at.add(0, -1.4, 0), Direction.UP, box);
		}
		if (burn) {
			removeWall(l, p.getUUID(), true);
		}
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_JAB);
			if (ready(p, JAB)) {
				cooldown(p, JAB, -1);
				le.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), p);
				level(p).sendParticles(GOO_DUST, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 3, 0.2, 0.2, 0.2, 0);
			}
		}
		return false;
	}

	/**
	 * Use (empty hand): the goo spit. v0.17.2 (the revamp; Slime Barrage folded in): a tap spits one glob (5); held, he spits
	 * rapidly -- 4 globs a second (3 each), slight spread, for up to 3 s, then a 6 s cooldown. (The client re-sends Use every tick
	 * the button is held -- {@code OmnitrixInputMixin} cancels vanilla's use before its 4-tick delay is set.)
	 */
	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		// v0.17.2 (user: "make stinky able to use items and tools"): the goo spit is the empty hand's Use, like Heatblast's bolt
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		long now = p.level().getGameTime();
		long[] s = SPITS.get(p.getUUID());
		if (s != null && now - s[1] <= HOLD_GAP) {
			// the same click, still held
			s[1] = now;
			if (s[3] < 0) {
				return InteractionResult.FAIL; // spent: nothing more until the button comes up
			}
			if (now - s[0] >= HOLD_DELAY) {
				if (now - s[0] > HOLD_DELAY + RAPID_TICKS) {
					s[3] = -1;
					cooldown(p, GOO_SPIT, RAPID_COOLDOWN);
					return InteractionResult.FAIL;
				}
				s[3] = 1;
				if (now >= s[2]) {
					s[2] = now + RAPID_EVERY;
					rapidGlob(p);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (!use(p, GOO_SPIT)) {
			return InteractionResult.FAIL;
		}
		SPITS.put(p.getUUID(), new long[] { now, now, now + HOLD_DELAY, 0 });
		anim(p, ANIM_SHOT);
		fromEyes(p, AlienProjectileEntity.of(p, "stinkfly_spit")).colour(0xC8E860).size(0.24f).damage(SPIT_DAMAGE).gravity(0.02).speed(1.4).life(30).spawn();
		OmnitrixSounds.play(level(p), p.position(), SPIT, 0.6f, 1.4f);
		p.swing(hand, true);
		return InteractionResult.SUCCESS;
	}

	private void rapidGlob(ServerPlayer p) {
		anim(p, ANIM_BARRAGE);
		Vec3 look = p.getLookAngle();
		Vec3 dir = look.add((p.getRandom().nextDouble() - 0.5) * 0.12, (p.getRandom().nextDouble() - 0.5) * 0.08, (p.getRandom().nextDouble() - 0.5) * 0.12);
		fromEyes(p, AlienProjectileEntity.of(p, "stinkfly_spit")).colour(0xC8E860).size(0.24f).damage(RAPID_DAMAGE).gravity(0.02).direction(dir).speed(1.45)
				.life(30).spawn();
		OmnitrixSounds.play(level(p), p.position(), SPIT, 0.5f, 1.5f + p.getRandom().nextFloat() * 0.2f);
	}

	/** The held spit ends when the Use stops arriving: a rapid run then costs its 6 s cooldown. */
	private void tickSpit(ServerPlayer p, long now) {
		long[] s = SPITS.get(p.getUUID());
		if (s != null && now - s[1] > HOLD_GAP) {
			SPITS.remove(p.getUUID());
			if (s[3] == 1) {
				cooldown(p, GOO_SPIT, RAPID_COOLDOWN);
			}
		}
	}

	// ---------------------------------------------------------------- damage

	/** No fall damage, ever, and no "flew into a wall" either: he lands like a bug. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypes.FLY_INTO_WALL);
	}

	/**
	 * v0.17.2 weaknesses: Fire (+25% fire damage) and Fragile Wings (a single hit of 8+ while flying clips his wings: 2 s of forced
	 * gliding; 10 s before it can happen again).
	 */
	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			amount *= FIRE_TAKEN;
		}
		long now = p.level().getGameTime();
		if (amount >= CLIP_THRESHOLD && p.getAbilities().flying) {
			long[] c = CLIPS.get(p.getUUID());
			if (c == null || now >= c[1]) {
				clipWings(p, now);
			}
		}
		return amount;
	}

	/** v0.17.2 Fragile Wings: clipped -- out of the air for 2 s (gliding down). */
	public void clipWings(ServerPlayer p, long now) {
		CLIPS.put(p.getUUID(), new long[] { now + CLIP_TICKS, now + CLIP_COOLDOWN });
		setFlag(p, F_CLIPPED, true);
		if (!creative(p)) {
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
		}
		OmnitrixSfx.playOn(p, BUZZ, 1.0f, 0.6f, net.minecraft.sounds.SoundSource.PLAYERS);
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Lift

	public static final String LIFT = "lift";
	public static final int ANIM_LIFT = 13;
	public static final int F_LIFT = 4;
	public static final double LIFT_REACH = 4.5;
	/** Too big to lift: bosses and anything this tall / wide. */
	public static final float LIFT_MAX_HEALTH = 150f;
	/** v0.17.2: a drop onto an enemy from this high (blocks) hits both of them... */
	public static final double DROP_MIN = 6.0;
	/** ...for 2 a block fallen, at most 16. */
	public static final float DROP_PER_BLOCK = 3f, DROP_MAX = 26f; // v0.17.2 damage pass: was 2 / 16
	/** player -> the entity id he carries. */
	private static final Map<UUID, Integer> LIFTED = new ConcurrentHashMap<>();

	private void vKey(ServerPlayer p, boolean shift) {
		if (shift) {
			gooWall(p); // v0.17.2: Shift+V (was empty)
		} else {
			lift(p);
		}
	}

	/** What he is carrying, or null. */
	@org.jetbrains.annotations.Nullable
	public static LivingEntity lifted(ServerPlayer p) {
		Integer id = LIFTED.get(p.getUUID());
		return id != null && p.level().getEntity(id) instanceof LivingEntity le ? le : null;
	}

	/**
	 * V: Lift -- the creature in front (4.5 blocks) is snatched up in his legs and carried under him wherever he flies (an ally to
	 * safety, a foe up high to let go of). V again drops it. Bosses and anything big are too heavy. v0.17.2 (S4): a player only if
	 * an ally (his squad) -- never an enemy player.
	 */
	public boolean lift(ServerPlayer p) {
		if (LIFTED.containsKey(p.getUUID())) {
			drop(p);
			return true;
		}
		LivingEntity t = lookTarget(p, LIFT_REACH);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.stinkfly.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (t instanceof Player && !com.projecthero.mod.squad.Squads.areAllies(p, t)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.stinkfly.not_ally").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (t.getMaxHealth() >= LIFT_MAX_HEALTH || t.getBbHeight() > 2.6f || t.getBbWidth() > 1.6f) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.stinkfly.too_heavy").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, LIFT)) {
			return false;
		}
		LIFTED.put(p.getUUID(), t.getId());
		DROPS.remove(t.getUUID());
		setFlag(p, F_LIFT, true);
		anim(p, ANIM_LIFT);
		ServerLevel l = level(p);
		OmnitrixSfx.playOn(p, BUZZ, 1.0f, 1.3f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		l.sendParticles(ParticleTypes.ITEM_SLIME, t.getX(), t.getY() + t.getBbHeight(), t.getZ(), 6, 0.3, 0.2, 0.3, 0.05);
		return true;
	}

	/** Lets go of what he carries (it falls from wherever he is; v0.17.2: a long drop onto an enemy hurts both). */
	public void drop(ServerPlayer p) {
		Integer id = LIFTED.remove(p.getUUID());
		setFlag(p, F_LIFT, false);
		if (id != null && p.level().getEntity(id) instanceof LivingEntity le) {
			le.resetFallDistance();
			le.setDeltaMovement(p.getDeltaMovement().scale(0.6));
			le.hurtMarked = true;
			boolean ally = le instanceof Player && com.projecthero.mod.squad.Squads.areAllies(p, le);
			DROPS.put(le.getUUID(), new Drop(p.getUUID(), le.getY(), ally, p.level().getGameTime() + 100));
			OmnitrixSfx.playOn(p, BUZZ, 0.7f, 0.9f, net.minecraft.sounds.SoundSource.PLAYERS); // v0.17.2: on him, so a fast flight does not leave it behind
		}
	}

	private void revertV(ServerPlayer p) {
		drop(p);
	}

	/** v0.17.2: what he dropped hits the ground -- from 6+ blocks onto an enemy, the fall is damage to both (an ally takes none). */
	private void tickDrops(ServerPlayer p, ServerLevel l, long now) {
		if (DROPS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Drop>> it = DROPS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Drop> en = it.next();
			Drop d = en.getValue();
			if (!d.owner().equals(p.getUUID())) {
				continue;
			}
			Entity e = l.getEntity(en.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || now >= d.until()) {
				it.remove();
				continue;
			}
			double fell = d.fromY() - le.getY();
			List<LivingEntity> under = new ArrayList<>();
			if (fell >= DROP_MIN - 0.5) {
				for (LivingEntity o : around(p, le.position(), 1.5 + le.getBbWidth())) {
					if (o != le && o.getBoundingBox().intersects(le.getBoundingBox().inflate(0.4, 0.6, 0.4))) {
						under.add(o);
					}
				}
			}
			if (!under.isEmpty() || le.onGround() || le.isInWater()) {
				it.remove();
				if (fell >= DROP_MIN && !under.isEmpty()) {
					float dmg = (float) Math.min(DROP_MAX, DROP_PER_BLOCK * fell);
					for (LivingEntity o : under) {
						hurt(p, o, dmg, false);
						shove(o, le.position(), 0.5, 0.2);
					}
					if (d.ally()) {
						le.resetFallDistance();
					} else {
						le.resetFallDistance(); // the drop-on is its fall
						hurt(p, le, dmg, false);
					}
					QuirkFx.ring(l, le.position().add(0, 0.05, 0)).colours(0xF0FFE0, COLOUR).size(2.5f).thickness(0.5f).life(10).ground().spawn();
					QuirkCamera.shakeAround(l, le.position(), 8, 0.3f, 6);
					OmnitrixSounds.play(l, le.position(), SPLAT, 1.0f, 0.5f);
				}
			}
		}
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		// v0.17.2: the Slime Net's wall pins hold their faces still
		if (!PINNED.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Pin>> it = PINNED.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Pin> en = it.next();
				if (!en.getValue().owner().equals(p.getUUID())) {
					continue;
				}
				Entity e = l.getEntity(en.getKey());
				if (!(e instanceof LivingEntity le) || !le.isAlive() || now >= en.getValue().until()) {
					it.remove();
					continue;
				}
				le.setYRot(en.getValue().yaw());
				le.setXRot(en.getValue().pitch());
				le.setYHeadRot(en.getValue().yaw());
				le.yBodyRot = en.getValue().yaw();
				le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
			}
		}
		Integer id = LIFTED.get(p.getUUID());
		if (id == null) {
			return;
		}
		Entity e = l.getEntity(id);
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.distanceToSqr(p) > 64 || !p.isAlive()) {
			drop(p);
			return;
		}
		// under him in the air; in front of him on the ground (so it is never pushed into the floor)
		Vec3 at = p.onGround() ? p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.6 + le.getBbWidth() * 0.5))
				: p.position().add(0, -le.getBbHeight() - 0.05, 0);
		le.teleportTo(at.x, at.y, at.z);
		le.setDeltaMovement(Vec3.ZERO);
		le.resetFallDistance();
		le.hurtMarked = true;
		if (now % 20 == 0) {
			l.sendParticles(ParticleTypes.ITEM_SLIME, le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 1, 0.2, 0.1, 0.2, 0.02);
		}
	}
}
