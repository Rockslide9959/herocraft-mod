package com.projecthero.mod.omnitrix.alien.cannonbolt;

import java.util.HashMap;
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
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "build canonbolt alien for the prototype omnitrix please ... do research on his powers and look at what he does in
 * the show aswell when building his kit please, change his abilities if hes in ball mode or not please"). Cannonbolt, an Arburian
 * Pelarota: a big, slow, top-heavy brute with his face on his chest and a shell of gold plates on his back, shoulders and arms. He
 * curls into a near-indestructible ball and rolls like a cannonball -- ramming through enemies and walls, ricocheting round a room
 * like a pinball, bouncing, rolling up walls, shrugging lasers off his shell. Spec: {@code docs/OMNITRIX_REVAMP_CANNONBOLT.md}.
 *
 * <p>V curls him up / uncurls him: in <b>Ball Mode</b> every key swaps for a rolling move ({@link #liveMoveOn}), his hitbox is a
 * one-block ball ({@link #body}), he rolls faster the longer he keeps rolling (Momentum), rams whatever he rolls into, rolls up
 * walls, bounces off any fall, deflects projectiles and takes half damage -- but he cannot punch or use items.
 */
public final class CannonboltKit extends AlienKit {
	public static final String ID = "cannonbolt";
	public static final int COLOUR = 0xDABE0B;

	// ---------------------------------------------------------------- synced flags
	/** Ball Mode (V): the ball hitbox, the swapped keys. */
	public static final int F_BALL = 1;
	/** Drawn as the ball right now (Ball Mode, or a curl mid-move: the Tumble, the Curl Smash's drop, the Cannonball Launch). */
	public static final int F_ROUND = 2;
	public static final int F_BOOST = 4;
	public static final int F_SPIN = 8;
	public static final int F_UNSTOPPABLE = 16;
	public static final int F_GUARD = 32;
	public static final int F_RICOCHET = 64;
	/** In the air from one of his own moves (no fall damage, the landing hits). */
	public static final int F_AIR = 128;

	// ---------------------------------------------------------------- poses
	public static final int ANIM_BASH = 1, ANIM_SLAM = 2, ANIM_CURL = 3, ANIM_GUARD = 4, ANIM_POUND = 5, ANIM_UNCURL = 6, ANIM_PUNCH = 7;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.cannonbolt.<id>)
	// standing
	public static final String BASH = "shoulder_bash", SLAM = "shell_slam", CURL_SMASH = "curl_smash", GUARD = "shell_guard", TUMBLE = "tumble_roll",
			POUND = "ground_pound", LAUNCH = "cannonball_launch", CURL = "curl_up", FISTS = "pelarota_fists", BACK = "shell_back";
	// Ball Mode
	public static final String CHARGE = "cannonball_charge", RICOCHET = "pinball_ricochet", BOUNCE = "big_bounce", DROP = "meteor_drop",
			BOOST = "roll_boost", SPIN = "shell_spin", UNSTOPPABLE = "unstoppable", UNCURL = "uncurl", RAM = "ram", SHELL = "armoured_shell",
			WALL_ROLL = "wall_roll";

	// ---------------------------------------------------------------- numbers (the alien damage scale: quick ~9-14, normal 14-22, big 22-28)
	public static final float BASH_DAMAGE = 14f, SLAM_DAMAGE = 20f, CURL_SMASH_DAMAGE = 22f, TUMBLE_DAMAGE = 9f, POUND_DAMAGE = 16f, LAUNCH_DAMAGE = 26f;
	public static final float CHARGE_DAMAGE = 20f, RICOCHET_DAMAGE = 16f, BOUNCE_DAMAGE = 18f, DROP_DAMAGE = 28f, SPIN_DAMAGE = 8f,
			UNSTOPPABLE_DAMAGE = 24f, RAM_MIN = 8f, RAM_MAX = 20f;
	public static final int GUARD_TICKS = 60, TUMBLE_TICKS = 10, LAUNCH_TICKS = 24, CHARGE_TICKS = 20, RICOCHET_TICKS = 100, RICOCHET_BOUNCES = 6,
			BOOST_TICKS = 100, SPIN_TICKS = 40, UNSTOPPABLE_TICKS = 160, MOMENTUM_TICKS = 60;
	/** Horizontal blocks / tick before rolling into something hurts it. */
	public static final double RAM_SPEED = 0.3;
	/**
	 * v0.17.2 (user: "make cannonbolts ball form move at 25 blocks per second while sprinting"): the roll's speed bonus while
	 * sprinting at full Momentum (tuned in game: 1.25 blocks a tick on flat ground). Plain rolling keeps 1.0 + 1.2 x Momentum.
	 */
	public static final double SPRINT_TOP_BONUS = 4.57; // measured 1.235 b/t at 4.5
	/** v0.17.2 (user: "when he sprint jumps its more like a forward launch kind of thing"): the launch's speed out (blocks / tick) and lift. */
	public static final double LAUNCH_SPEED = 1.7, LAUNCH_LIFT = 0.5;
	public static final float LAUNCH_LAND_DAMAGE = 12f;
	public static final int LAUNCH_GAP_TICKS = 16;
	/**
	 * v0.17.2 (user: "if he falls from a great distance the ball form should bounce a bit first"): after a fall that would hurt
	 * (8+ blocks) the ball bounces up to this many times, each {@link #BOUNCE_KEEP} of the last, then settles.
	 */
	public static final int FALL_BOUNCES = 3;
	public static final double BOUNCE_KEEP = 0.55, BOUNCE_MIN = 0.22;
	/** v0.17.2 (user: "make canonbolts ball form 1,75 blocks tall please"): the ball's hitbox before the body's scale (1.3). */
	public static final float BALL_SIZE = 1.75f / 1.3f;
	/**
	 * v0.17.2 (user: "make the players view come out of their chest please since they dont have a head" / "make the nametag appear
	 * above the body instead of above the chest please"): standing, the box ends at his headless body's top (24 px), the eyes sit in
	 * his chest and the nametag just over his shoulders -- before the body's scale (1.3).
	 */
	public static final float BODY_HEIGHT = 1.55f, CHEST_EYE = 1.25f, CROUCH_HEIGHT = 1.3f, CROUCH_EYE = 1.0f, TAG_DROP = -0.15f; // v0.17.2 (user: "make the nametag a bit higher since right now its inside his body"): was 0.4

	private static final ResourceLocation ROLL_SPEED = PowerToggles.id("omnitrix_cannonbolt_roll");
	private static final ResourceLocation ROLL_STEP = PowerToggles.id("omnitrix_cannonbolt_step");
	private static final ResourceLocation STEADY = PowerToggles.id("omnitrix_cannonbolt_steady");

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_CURL = OmnitrixSfx.cue("cannonbolt_curl")
			.layer(SoundEvents.ARMOR_EQUIP_NETHERITE, 0.5f, 0.7f)
			.layer(SoundEvents.ANVIL_PLACE, 0.12f, 1.6f, 2);
	public static final OmnitrixSfx.Cue SND_UNCURL = OmnitrixSfx.cue("cannonbolt_uncurl")
			.layer(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.5f, 0.6f)
			.layer(SoundEvents.SHULKER_OPEN, 0.3f, 0.6f, 1);
	public static final OmnitrixSfx.Cue SND_IMPACT = OmnitrixSfx.cue("cannonbolt_impact")
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.5f, 0.6f)
			.layer(SoundEvents.ANVIL_LAND, 0.15f, 0.55f);
	public static final OmnitrixSfx.Cue SND_RICOCHET = OmnitrixSfx.cue("cannonbolt_ricochet")
			.layer(SoundEvents.ANVIL_LAND, 0.2f, 1.3f)
			.layer(SoundEvents.SHIELD_BLOCK, 0.35f, 0.8f);
	public static final OmnitrixSfx.Cue SND_QUAKE = OmnitrixSfx.cue("cannonbolt_quake")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.35f, 0.6f)
			.layer(SoundEvents.ANVIL_LAND, 0.2f, 0.5f, 1);
	public static final OmnitrixSfx.Cue SND_ROLL = OmnitrixSfx.cue("cannonbolt_roll")
			.layer(SoundEvents.MINECART_RIDING, 0.18f, 0.55f)
			.gap(16);
	public static final OmnitrixSfx.Cue SND_DEFLECT = OmnitrixSfx.cue("cannonbolt_deflect")
			.layer(SoundEvents.SHIELD_BLOCK, 0.45f, 1.2f)
			.layer(SoundEvents.ANVIL_LAND, 0.08f, 1.8f);

	// ---------------------------------------------------------------- server-only move state
	private enum DashKind {
		TUMBLE, LAUNCH, CHARGE
	}

	private record Dash(DashKind kind, Vec3 dir, double speed, long end, Set<Integer> hits) {
	}

	private static final class Rico {
		Vec3 dir;
		double speed;
		int bounces;
		final long end;
		final Map<Integer, Long> hits = new HashMap<>();

		Rico(Vec3 dir, double speed, long end) {
			this.dir = dir;
			this.speed = speed;
			this.end = end;
		}
	}

	private enum AirKind {
		CURL_SMASH, BOUNCE, DROP, LAUNCH
	}

	private static final class Air {
		final AirKind kind;
		final long start;
		boolean falling;
		/** The Sprint Launch: the way he was fired (he lands still rolling that way). */
		Vec3 dir = Vec3.ZERO;

		Air(AirKind kind, long start) {
			this.kind = kind;
			this.start = start;
		}
	}

	/** v0.17.2: a high fall's bounces still to come -- the next bounce's lift, how many are left, when it began. */
	private static final class Bounces {
		double lift;
		int left;
		final long start;
		boolean airborne;

		Bounces(double lift, int left, long start) {
			this.lift = lift;
			this.left = left;
			this.start = start;
		}
	}

	private static final Map<UUID, Bounces> BOUNCES = new ConcurrentHashMap<>();
	/** Last tick's real motion (the client moves the player: the server's own velocity is not his). */
	private static final Map<UUID, Vec3> LAST_MOVE = new ConcurrentHashMap<>();
	/** Whether he stood on the ground last tick (the sprint jump), and the tick the last launch fired. */
	private static final Map<UUID, Boolean> WAS_GROUND = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_LAUNCH = new ConcurrentHashMap<>();

	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Rico> RICOS = new ConcurrentHashMap<>();
	private static final Map<UUID, Air> AIRS = new ConcurrentHashMap<>();
	/** Last tick's position (the real rolling speed: the client moves the player). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** Ticks of unbroken rolling (Momentum). */
	private static final Map<UUID, Integer> ROLLED = new ConcurrentHashMap<>();
	/** Creature id -> the tick the ball last rammed it. */
	private static final Map<UUID, Map<Integer, Long>> RAMMED = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private CannonboltKit() {
	}

	public static void register() {
		Aliens.register(new CannonboltKit());
	}

	public static void clearSessionState() {
		DASHES.clear();
		RICOS.clear();
		AIRS.clear();
		LAST_POS.clear();
		ROLLED.clear();
		RAMMED.clear();
		LAST_TICK.clear();
		BOUNCES.clear();
		LAST_MOVE.clear();
		WAS_GROUND.clear();
		LAST_LAUNCH.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// a heavy, slow brute (the show: he lumbers when uncurled) -- his shell plates are diamond-grade armour
		return AlienStats.builder().health(40f).speed(0.8f).scale(1.3f).armor(16f, 3f).knockbackResist(0.5f).attack(9f).step(0.4f).jump(0.02f)
				.safeFall(4f).reach(0.3f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				// standing
				AlienMove.tap(BASH, AbilitySlot.SLOT_1, 24), AlienMove.shift(SLAM, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(CURL_SMASH, AbilitySlot.SLOT_2, 200), AlienMove.shift(GUARD, AbilitySlot.SLOT_2, 240),
				AlienMove.tap(TUMBLE, AbilitySlot.SLOT_3, 80),
				AlienMove.tap(POUND, AbilitySlot.SLOT_4, 140),
				AlienMove.tap(CURL, AbilitySlot.SLOT_5, 10),
				AlienMove.tap(LAUNCH, AbilitySlot.SLOT_6, 500),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(BACK, "Passive", 0),
				// Ball Mode (the HUD shows them on the same keys while he is rolled up, see liveMoveOn)
				AlienMove.vanilla(CHARGE, "Ball Mode: R", 100), AlienMove.vanilla(RICOCHET, "Ball Mode: Shift+R", 240),
				AlienMove.vanilla(BOUNCE, "Ball Mode: G", 80), AlienMove.vanilla(DROP, "Ball Mode: Shift+G", 400),
				AlienMove.vanilla(BOOST, "Ball Mode: X", 240), AlienMove.vanilla(SPIN, "Ball Mode: Z", 160),
				AlienMove.vanilla(UNCURL, "Ball Mode: V", 10), AlienMove.vanilla(UNSTOPPABLE, "Ball Mode: C", 900),
				AlienMove.vanilla(RAM, "Ball Mode: roll into it", 0), AlienMove.vanilla(SHELL, "Ball Mode: passive", 0),
				AlienMove.vanilla(WALL_ROLL, "Ball Mode: roll into a wall", 0));
	}

	/** Ball Mode swaps every key for its rolling move. */
	@Override
	public AlienMove liveMoveOn(Player p, AbilitySlot slot, boolean shift, boolean hold) {
		if (!flag(p, F_BALL) || hold) {
			return moveOn(slot, shift, hold);
		}
		return switch (slot) {
			case SLOT_1 -> move(shift ? RICOCHET : CHARGE);
			case SLOT_2 -> move(shift ? DROP : BOUNCE);
			case SLOT_3 -> shift ? null : move(BOOST);
			case SLOT_4 -> shift ? null : move(SPIN);
			case SLOT_5 -> shift ? null : move(UNCURL);
			case SLOT_6 -> shift ? null : move(UNSTOPPABLE);
			default -> moveOn(slot, shift, hold);
		};
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_5 -> flag(p, F_BALL);
			case SLOT_3 -> flag(p, F_BOOST);
			case SLOT_4 -> flag(p, F_SPIN);
			case SLOT_6 -> flag(p, F_UNSTOPPABLE);
			case SLOT_2 -> flag(p, F_GUARD);
			case SLOT_1 -> flag(p, F_RICOCHET);
			default -> false;
		};
	}

	/** v0.17.2: {@code p} is Cannonbolt rolled up right now (the server's "moved too quickly" check waves his top speed through). */
	public static boolean rolling(Player p) {
		return p != null && ID.equals(Omnitrix.active(p)) && ball(p);
	}

	/** Client-safe: whether {@code p} is rolled up (Ball Mode). */
	public static boolean ball(Player p) {
		return (Omnitrix.state(p).kitFlags & F_BALL) != 0;
	}

	// ---------------------------------------------------------------- the body

	@Override
	@Nullable
	public EntityDimensions body(Player p, Pose pose) {
		if (flag(p, F_BALL)) {
			return BALL_BODY;
		}
		return switch (pose) {
			case STANDING -> STANDING_BODY;
			case CROUCHING -> CROUCHING_BODY;
			default -> null;
		};
	}

	// built once: the body mixin refreshes the hitbox whenever the answer stops being equal to the last one
	private static final EntityDimensions BALL_BODY = EntityDimensions.scalable(BALL_SIZE, BALL_SIZE).withEyeHeight(BALL_SIZE * 0.6f);
	private static final EntityDimensions STANDING_BODY = headless(BODY_HEIGHT, CHEST_EYE);
	private static final EntityDimensions CROUCHING_BODY = headless(CROUCH_HEIGHT, CROUCH_EYE);

	/** A headless body {@code height} tall, the eyes in his chest, the nametag a little under the box's top (the renderer adds 0.5). */
	private static EntityDimensions headless(float height, float eye) {
		return EntityDimensions.scalable(0.6f, height).withEyeHeight(eye).withAttachments(net.minecraft.world.entity.EntityAttachments.builder()
				.attach(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0f, height - TAG_DROP, 0f)
				.attach(net.minecraft.world.entity.EntityAttachment.VEHICLE, Player.DEFAULT_VEHICLE_ATTACHMENT));
	}

	@Override
	public boolean climbs(Player p) {
		// the show: the ball rolls straight up walls -- only while pressed into one ("on a ladder" caps the roll's speed)
		return flag(p, F_BALL) && p.horizontalCollision;
	}

	@Override
	public boolean pawsOnly(Player p) {
		return flag(p, F_BALL); // no hands out of the shell
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_UNCURL, 1.0f, 0.9f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(14).ground().spawn();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		for (int f : new int[] { F_BALL, F_ROUND, F_BOOST, F_SPIN, F_UNSTOPPABLE, F_GUARD, F_RICOCHET, F_AIR }) {
			setFlag(p, f, false);
		}
		for (String t : new String[] { "guard_end", "boost_end", "spin_end", "unstoppable_end" }) {
			setTimer(p, t, 0);
		}
		clearRoll(p);
		UUID id = p.getUUID();
		DASHES.remove(id);
		RICOS.remove(id);
		AIRS.remove(id);
		LAST_POS.remove(id);
		ROLLED.remove(id);
		RAMMED.remove(id);
		LAST_TICK.remove(id);
		BOUNCES.remove(id);
		LAST_MOVE.remove(id);
		WAS_GROUND.remove(id);
		LAST_LAUNCH.remove(id);
	}

	private static void clearRoll(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, ROLL_SPEED);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, ROLL_STEP);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADY);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_BALL)) {
			switch (slot) {
				case SLOT_1 -> {
					if (shift) {
						ricochet(p);
					} else {
						charge(p);
					}
				}
				case SLOT_2 -> {
					if (shift) {
						meteorDrop(p);
					} else {
						bounce(p);
					}
				}
				case SLOT_3 -> {
					if (!shift) {
						boost(p);
					}
				}
				case SLOT_4 -> {
					if (!shift) {
						spin(p);
					}
				}
				case SLOT_5 -> {
					if (!shift && use(p, UNCURL)) {
						setBall(p, false);
					}
				}
				case SLOT_6 -> {
					if (!shift) {
						unstoppable(p);
					}
				}
				default -> {
				}
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					shellSlam(p);
				} else {
					shoulderBash(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					shellGuard(p);
				} else {
					curlSmash(p);
				}
			}
			case SLOT_3 -> {
				if (!shift) {
					tumble(p);
				}
			}
			case SLOT_4 -> {
				if (!shift) {
					groundPound(p);
				}
			}
			case SLOT_5 -> {
				if (!shift && use(p, CURL)) {
					setBall(p, true);
				}
			}
			case SLOT_6 -> {
				if (!shift) {
					launch(p);
				}
			}
			default -> {
			}
		}
	}

	/** Left-click: his heavy fists standing (the body's attack stat); none from inside the ball. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_BALL)) {
			return true;
		}
		anim(p, ANIM_PUNCH);
		return false;
	}

	// ---------------------------------------------------------------- V: curl up / uncurl

	/**
	 * Into or out of Ball Mode. Uncurling needs room to stand (false, and a line, under a low ceiling). Curling drops the Shell Guard;
	 * uncurling stops every rolling move.
	 */
	public boolean setBall(ServerPlayer p, boolean on) {
		if (flag(p, F_BALL) == on) {
			return true;
		}
		ServerLevel l = level(p);
		if (!on) {
			float scale = p.getScale();
			AABB stand = EntityDimensions.scalable(0.6f, BODY_HEIGHT).scale(scale).makeBoundingBox(p.position());
			if (!l.noCollision(p, stand.deflate(1.0e-3))) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.no_room").withStyle(ChatFormatting.GRAY), true);
				cooldown(p, UNCURL, 0);
				return false;
			}
			for (int f : new int[] { F_BOOST, F_SPIN, F_UNSTOPPABLE, F_RICOCHET }) {
				setFlag(p, f, false);
			}
			for (String t : new String[] { "boost_end", "spin_end", "unstoppable_end" }) {
				setTimer(p, t, 0);
			}
			RICOS.remove(p.getUUID());
			Dash d = DASHES.get(p.getUUID());
			if (d != null && d.kind() == DashKind.CHARGE) {
				DASHES.remove(p.getUUID());
			}
			ROLLED.remove(p.getUUID());
			BOUNCES.remove(p.getUUID());
			clearRoll(p);
			anim(p, ANIM_UNCURL);
			OmnitrixSounds.play(l, p.position(), SND_UNCURL, 1.0f, 1.0f);
		} else {
			setFlag(p, F_GUARD, false);
			setTimer(p, "guard_end", 0);
			anim(p, ANIM_CURL);
			OmnitrixSounds.play(l, p.position(), SND_CURL, 1.0f, 1.0f);
		}
		setFlag(p, F_BALL, on);
		p.refreshDimensions();
		l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.6, p.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
		return true;
	}

	// ---------------------------------------------------------------- standing moves

	/** R: a lunge behind his shoulder plate -- 14 to everything just in front, knocked back. */
	public boolean shoulderBash(ServerPlayer p) {
		if (!use(p, BASH)) {
			return false;
		}
		anim(p, ANIM_BASH);
		ServerLevel l = level(p);
		Vec3 dir = flat(p);
		p.setDeltaMovement(dir.x * 0.75, 0.1, dir.z * 0.75);
		p.hurtMarked = true;
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0).add(dir.scale(1.6));
		for (LivingEntity e : around(p, at, 1.9)) {
			hurt(p, e, BASH_DAMAGE, false);
			shove(e, p.position(), 1.3, 0.3);
		}
		OmnitrixSounds.play(l, at, SND_IMPACT, 0.8f, 1.2f);
		return true;
	}

	/** Shift+R: he topples forward onto his shell -- 20 in a 2.5-block patch ahead, the ones under it slowed for 2 s. */
	public boolean shellSlam(ServerPlayer p) {
		if (!use(p, SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(flat(p).scale(1.8));
		for (LivingEntity e : around(p, c.add(0, 0.5, 0), 2.5)) {
			hurt(p, e, SLAM_DAMAGE, false);
			shove(e, c, 0.5, 0.15);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(12).ground().spawn();
		dust(l, c, 20);
		QuirkCamera.shakeAround(l, c, 10, 0.3f, 8);
		OmnitrixSounds.play(l, c, SND_QUAKE, 0.9f, 1.1f);
		return true;
	}

	/** G: jump, curl in the air and drop like a wrecking ball -- 22 in a 3.5-block ring where he lands. */
	public boolean curlSmash(ServerPlayer p) {
		if (!use(p, CURL_SMASH)) {
			return false;
		}
		anim(p, ANIM_CURL);
		Vec3 dir = flat(p);
		p.setDeltaMovement(dir.x * 0.45, 0.95, dir.z * 0.45);
		p.hurtMarked = true;
		AIRS.put(p.getUUID(), new Air(AirKind.CURL_SMASH, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_CURL, 0.8f, 1.2f);
		return true;
	}

	/** Shift+G: his shoulder plates turned to the front for 3 s -- blows from in front lose 75%, projectiles bounce back. */
	public boolean shellGuard(ServerPlayer p) {
		if (!use(p, GUARD)) {
			return false;
		}
		anim(p, ANIM_GUARD);
		setFlag(p, F_GUARD, true);
		setTimer(p, "guard_end", p.level().getGameTime() + GUARD_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_CURL, 0.6f, 1.4f);
		return true;
	}

	/** X: a quick curl-and-roll dodge, 6 blocks the way he faces; 9 to whatever he bowls through, half damage while curled. */
	public boolean tumble(ServerPlayer p) {
		if (!use(p, TUMBLE)) {
			return false;
		}
		startDash(p, DashKind.TUMBLE, flat(p), 0.65, TUMBLE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_CURL, 0.7f, 1.3f);
		return true;
	}

	/** Z: both fists into the ground -- 16 in a 4-block ring, everything thrown up. */
	public boolean groundPound(ServerPlayer p) {
		if (!use(p, POUND)) {
			return false;
		}
		anim(p, ANIM_POUND);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, 4.0)) {
			hurt(p, e, POUND_DAMAGE, false);
			shove(e, c, 0.5, 0.7);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(5f).thickness(0.8f).life(14).ground().spawn();
		dust(l, c, 30);
		QuirkCamera.shakeAround(l, c, 14, 0.45f, 10);
		OmnitrixSounds.play(l, c, SND_QUAKE, 1.1f, 0.9f);
		return true;
	}

	/** C: curl and fire himself where he looks like a cannonball -- 26 to everything in the way, weak blocks burst; he lands rolled up. */
	public boolean launch(ServerPlayer p) {
		if (!use(p, LAUNCH)) {
			return false;
		}
		anim(p, ANIM_CURL);
		startDash(p, DashKind.LAUNCH, p.getLookAngle().normalize(), 1.6, LAUNCH_TICKS);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 1.0f, 1.3f);
		QuirkFx.ring(l, p.position().add(0, 0.8, 0)).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, COLOUR).size(2.2f).thickness(0.5f)
				.life(9).spawn();
		return true;
	}

	// ---------------------------------------------------------------- Ball Mode moves

	/** Ball R: a straight-line burst where he faces -- 20 to everything in the way, weak blocks burst, stops dead against a wall. */
	public boolean charge(ServerPlayer p) {
		if (!use(p, CHARGE)) {
			return false;
		}
		startDash(p, DashKind.CHARGE, flat(p), 1.25, CHARGE_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_ROLL, 1.2f, 1.2f);
		return true;
	}

	/**
	 * Ball Shift+R: the show's pinball -- he shoots off where he faces and bounces off every wall and creature he hits, faster each
	 * time (6 bounces or 5 s): 16 per hit.
	 */
	public boolean ricochet(ServerPlayer p) {
		if (!use(p, RICOCHET)) {
			return false;
		}
		RICOS.put(p.getUUID(), new Rico(flat(p), 1.1, p.level().getGameTime() + RICOCHET_TICKS));
		setFlag(p, F_RICOCHET, true);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 1.0f, 0.9f);
		return true;
	}

	/** Ball G: a huge bounce (about 8 blocks up); the landing hits 18 in a 4-block ring. */
	public boolean bounce(ServerPlayer p) {
		if (!use(p, BOUNCE)) {
			return false;
		}
		Vec3 v = motion(p);
		p.setDeltaMovement(v.x, 1.25, v.z);
		p.hurtMarked = true;
		BOUNCES.remove(p.getUUID());
		AIRS.put(p.getUUID(), new Air(AirKind.BOUNCE, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 0.8f, 0.7f);
		return true;
	}

	/** Ball Shift+G: up high, then straight down like a meteor -- 28 in a 5-block ring and a quake. */
	public boolean meteorDrop(ServerPlayer p) {
		if (!use(p, DROP)) {
			return false;
		}
		p.setDeltaMovement(0, 1.7, 0);
		p.hurtMarked = true;
		BOUNCES.remove(p.getUUID());
		AIRS.put(p.getUUID(), new Air(AirKind.DROP, p.level().getGameTime()));
		setFlag(p, F_AIR, true);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 1.0f, 0.6f);
		return true;
	}

	/** Ball X: 5 s of top speed (his Momentum starts full and he rolls even faster). */
	public boolean boost(ServerPlayer p) {
		if (!use(p, BOOST)) {
			return false;
		}
		setFlag(p, F_BOOST, true);
		setTimer(p, "boost_end", p.level().getGameTime() + BOOST_TICKS);
		ROLLED.put(p.getUUID(), MOMENTUM_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_ROLL, 1.3f, 1.4f);
		return true;
	}

	/** Ball Z: he spins on the spot for 2 s -- 8 to everything within 3 blocks every 0.4 s, projectiles flung away. */
	public boolean spin(ServerPlayer p) {
		if (!use(p, SPIN)) {
			return false;
		}
		setFlag(p, F_SPIN, true);
		setTimer(p, "spin_end", p.level().getGameTime() + SPIN_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_RICOCHET, 0.8f, 1.5f);
		return true;
	}

	/** Ball C (ultimate): 8 s as an unstoppable cannonball -- faster still, 80% less damage, no knockback, 24 rams through weak walls. */
	public boolean unstoppable(ServerPlayer p) {
		if (!use(p, UNSTOPPABLE)) {
			return false;
		}
		setFlag(p, F_UNSTOPPABLE, true);
		setTimer(p, "unstoppable_end", p.level().getGameTime() + UNSTOPPABLE_TICKS);
		ROLLED.put(p.getUUID(), MOMENTUM_TICKS);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(4f).thickness(0.7f).life(14).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 1.2f, 1.2f);
		return true;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		Vec3 pos = p.position();
		Vec3 prev = LAST_POS.put(p.getUUID(), pos);
		Vec3 moved = prev == null ? Vec3.ZERO : pos.subtract(prev);
		double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
		if (speed > 4.0) {
			speed = 0; // a teleport, not a roll
			moved = Vec3.ZERO;
		}
		LAST_MOVE.put(p.getUUID(), moved);
		boolean onGround = p.onGround();
		Boolean wasGround = WAS_GROUND.put(p.getUUID(), onGround);
		endTimed(p, "guard_end", F_GUARD, now);
		endTimed(p, "boost_end", F_BOOST, now);
		endTimed(p, "spin_end", F_SPIN, now);
		endTimed(p, "unstoppable_end", F_UNSTOPPABLE, now);
		boolean ball = flag(p, F_BALL);
		tickDash(p, l, now);
		tickRicochet(p, l, now);
		tickAir(p, l, now);
		if (ball && Boolean.TRUE.equals(wasGround) && !onGround && moved.y > 0.2 && p.isSprinting()) {
			sprintLaunch(p, l, now, moved);
		}
		tickBounces(p, l, now);
		if (ball) {
			tickRoll(p, l, now, moved, speed);
			if (flag(p, F_SPIN)) {
				tickSpin(p, l, now);
			}
			deflectAround(p, l, 1.6, false);
		} else if (flag(p, F_GUARD)) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, false, false, false));
			deflectAround(p, l, 3.0, true);
		}
		boolean round = ball || DASHES.containsKey(p.getUUID()) || airRound(p);
		setFlag(p, F_ROUND, round);
		boolean steady = flag(p, F_UNSTOPPABLE) || RICOS.containsKey(p.getUUID()) || DASHES.containsKey(p.getUUID());
		if (steady) {
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADY, 1.0, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADY);
		}
	}

	private void endTimed(ServerPlayer p, String timer, int flag, long now) {
		long end = timer(p, timer);
		if (end > 0 && now >= end) {
			setTimer(p, timer, 0);
			setFlag(p, flag, false);
		}
	}

	private boolean airRound(ServerPlayer p) {
		Air a = AIRS.get(p.getUUID());
		return a != null && (a.kind != AirKind.CURL_SMASH || a.falling);
	}

	/** Ball Mode's rolling: Momentum (faster the longer he rolls), the step over blocks, the ram, the rumble. */
	private void tickRoll(ServerPlayer p, ServerLevel l, long now, Vec3 moved, double speed) {
		int rolled = ROLLED.getOrDefault(p.getUUID(), 0);
		rolled = speed > 0.12 ? Math.min(MOMENTUM_TICKS, rolled + 1) : speed < 0.04 ? 0 : rolled;
		ROLLED.put(p.getUUID(), rolled);
		double mom = rolled / (double) MOMENTUM_TICKS;
		// v0.17.2 (user): sprinting, he builds to 25 blocks a second; Roll Boost / Unstoppable fill his Momentum (above) but never go
		// past that top speed -- walking, they add on as before
		double bonus = p.isSprinting() ? 1.0 + (SPRINT_TOP_BONUS - 1.0) * mom
				: 1.0 + 1.2 * mom + (flag(p, F_BOOST) ? 0.8 : 0) + (flag(p, F_UNSTOPPABLE) ? 1.0 : 0);
		if (flag(p, F_SPIN)) {
			bonus = -0.5;
		}
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, ROLL_SPEED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, ROLL_STEP, 0.5, AttributeModifier.Operation.ADD_VALUE);
		if (speed > 0.15 && p.onGround()) {
			OmnitrixSounds.play(l, p.position(), SND_ROLL, 0.6f + (float) speed, 0.8f + (float) speed * 0.6f);
			if (now % 3 == 0) {
				BlockState under = l.getBlockState(p.blockPosition().below());
				if (!under.isAir()) {
					l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.3, 0.05, 0.3, 0.1);
				}
			}
		}
		if (speed < RAM_SPEED || DASHES.containsKey(p.getUUID()) || RICOS.containsKey(p.getUUID())) {
			return;
		}
		Vec3 dir = new Vec3(moved.x, 0, moved.z).normalize();
		boolean unstoppable = flag(p, F_UNSTOPPABLE);
		float damage = unstoppable ? UNSTOPPABLE_DAMAGE : Mth.clamp((float) (6.0 + speed * 20.0), RAM_MIN, RAM_MAX);
		Map<Integer, Long> rammed = RAMMED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		Vec3 c = center(p).add(dir.scale(p.getBbWidth() * 0.6));
		for (LivingEntity e : around(p, c, p.getBbWidth() * 0.5 + 0.7)) {
			Long at = rammed.get(e.getId());
			if (at != null && now - at < 10) {
				continue;
			}
			rammed.put(e.getId(), now);
			if (hurt(p, e, damage, false)) {
				shove(e, p.position().subtract(dir), 1.0 + speed, 0.35);
				OmnitrixSounds.play(l, e.position(), SND_IMPACT, 0.8f, 1.0f);
			}
		}
		if (unstoppable) {
			smashAhead(p, l, dir, 1);
		}
		if (rammed.size() > 64) {
			rammed.entrySet().removeIf(en -> now - en.getValue() > 40);
		}
	}

	private void tickSpin(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, "spin_end") - SPIN_TICKS;
		if ((now - start) % 8 != 0) {
			return;
		}
		Vec3 c = center(p);
		for (LivingEntity e : around(p, c, 3.0)) {
			hurt(p, e, SPIN_DAMAGE, false);
			shove(e, c, 0.8, 0.25);
		}
		deflectAround(p, l, 3.5, false);
		l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.8, 0.1, 0.8, 0);
		OmnitrixSounds.play(l, c, SND_ROLL, 1.0f, 1.6f);
	}

	// ---------------------------------------------------------------- dashes (the Tumble, the Launch, the Charge)

	private void startDash(ServerPlayer p, DashKind kind, Vec3 dir, double speed, int ticks) {
		DASHES.put(p.getUUID(), new Dash(kind, dir, speed, p.level().getGameTime() + ticks, new HashSet<>()));
		setFlag(p, F_ROUND, true);
	}

	private void tickDash(ServerPlayer p, ServerLevel l, long now) {
		Dash d = DASHES.get(p.getUUID());
		if (d == null) {
			return;
		}
		boolean wall = d.kind() != DashKind.TUMBLE && smashAhead(p, l, d.dir(), d.kind() == DashKind.TUMBLE ? 0 : 2) && blocked(p, l, d.dir(), d.speed());
		if (now >= d.end() || wall || (d.kind() == DashKind.TUMBLE && blocked(p, l, d.dir(), d.speed()))) {
			endDash(p, l, d, wall);
			return;
		}
		double vy = d.kind() == DashKind.LAUNCH ? d.dir().y * d.speed() : Math.min(0, p.getDeltaMovement().y);
		p.setDeltaMovement(d.dir().x * d.speed(), vy, d.dir().z * d.speed());
		p.hurtMarked = true;
		p.resetFallDistance();
		float damage = switch (d.kind()) {
			case TUMBLE -> TUMBLE_DAMAGE;
			case LAUNCH -> LAUNCH_DAMAGE;
			case CHARGE -> CHARGE_DAMAGE;
		};
		Vec3 c = center(p).add(d.dir().scale(0.8));
		for (LivingEntity e : around(p, c, p.getBbWidth() * 0.5 + 0.9)) {
			if (d.hits().add(e.getId()) && hurt(p, e, damage, false)) {
				shove(e, p.position(), d.kind() == DashKind.TUMBLE ? 0.6 : 1.6, 0.4);
				OmnitrixSounds.play(l, e.position(), SND_IMPACT, 0.9f, 1.0f);
			}
		}
		if (now % 2 == 0) {
			l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		}
	}

	private void endDash(ServerPlayer p, ServerLevel l, Dash d, boolean wall) {
		DASHES.remove(p.getUUID());
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x * 0.2, Math.min(0, v.y), v.z * 0.2);
		p.hurtMarked = true;
		if (wall) {
			Vec3 at = center(p).add(d.dir().scale(0.8));
			QuirkCamera.shake(p, 0.4f, 8);
			OmnitrixSounds.play(l, at, SND_IMPACT, 1.0f, 0.8f);
			l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.2);
		}
		if (d.kind() == DashKind.LAUNCH) {
			setBall(p, true); // he lands rolled up
		}
	}

	// ---------------------------------------------------------------- the pinball

	private void tickRicochet(ServerPlayer p, ServerLevel l, long now) {
		Rico r = RICOS.get(p.getUUID());
		if (r == null) {
			return;
		}
		if (now >= r.end || r.bounces > RICOCHET_BOUNCES || !flag(p, F_BALL)) {
			RICOS.remove(p.getUUID());
			setFlag(p, F_RICOCHET, false);
			return;
		}
		// a wall ahead: reflect off its face
		Vec3 c = center(p);
		double reach = r.speed + p.getBbWidth() * 0.5 + 0.1;
		BlockHitResult hit = l.clip(new ClipContext(c, c.add(r.dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis() != Direction.Axis.Y) {
			Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
			r.dir = r.dir.subtract(n.scale(2 * r.dir.dot(n))).normalize();
			bounceFx(p, l, hit.getLocation(), r);
		}
		// a creature: hit it and glance off it
		for (LivingEntity e : around(p, c.add(r.dir.scale(0.6)), p.getBbWidth() * 0.5 + 0.8)) {
			Long at = r.hits.get(e.getId());
			if (at != null && now - at < 10) {
				continue;
			}
			r.hits.put(e.getId(), now);
			hurt(p, e, RICOCHET_DAMAGE, false);
			shove(e, c, 1.2, 0.4);
			Vec3 away = c.subtract(e.position());
			away = new Vec3(away.x, 0, away.z);
			if (away.lengthSqr() > 1.0e-4) {
				Vec3 n = away.normalize();
				if (r.dir.dot(n) < 0) {
					r.dir = r.dir.subtract(n.scale(2 * r.dir.dot(n))).normalize();
				}
			}
			bounceFx(p, l, e.position().add(0, e.getBbHeight() * 0.5, 0), r);
		}
		p.setDeltaMovement(r.dir.x * r.speed, Math.min(0, p.getDeltaMovement().y), r.dir.z * r.speed);
		p.hurtMarked = true;
		p.resetFallDistance();
	}

	private void bounceFx(ServerPlayer p, ServerLevel l, Vec3 at, Rico r) {
		r.bounces++;
		r.speed = Math.min(1.9, r.speed * 1.1);
		l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
		OmnitrixSounds.play(l, at, SND_RICOCHET, 1.0f, 0.8f + r.bounces * 0.08f);
		QuirkCamera.shake(p, 0.2f, 4);
	}

	// ---------------------------------------------------------------- airborne moves

	private void tickAir(ServerPlayer p, ServerLevel l, long now) {
		Air a = AIRS.get(p.getUUID());
		if (a == null) {
			return;
		}
		long t = now - a.start;
		p.resetFallDistance();
		if (!a.falling) {
			int apex = a.kind == AirKind.DROP ? 12 : a.kind == AirKind.CURL_SMASH ? 7 : 0;
			if (apex > 0 && t >= apex) {
				a.falling = true;
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x * 0.3, a.kind == AirKind.DROP ? -2.5 : -1.4, v.z * 0.3);
				p.hurtMarked = true;
				OmnitrixSounds.play(l, p.position(), SND_ROLL, 1.0f, 0.6f);
			} else if (apex == 0 && t > 4) {
				a.falling = true;
			}
		}
		if (t > 3 && (p.onGround() || p.isInWater()) || t > 200) {
			AIRS.remove(p.getUUID());
			setFlag(p, F_AIR, false);
			if (t > 200 || p.isInWater()) {
				return;
			}
			if (a.kind == AirKind.LAUNCH) {
				landLaunch(p, l, a);
				return;
			}
			float damage = a.kind == AirKind.DROP ? DROP_DAMAGE : a.kind == AirKind.BOUNCE ? BOUNCE_DAMAGE : CURL_SMASH_DAMAGE;
			float radius = a.kind == AirKind.DROP ? 5f : a.kind == AirKind.BOUNCE ? 4f : 3.5f;
			Vec3 c = p.position();
			for (LivingEntity e : around(p, c, radius)) {
				hurt(p, e, damage, false);
				shove(e, c, 0.9, 0.5);
			}
			QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(radius + 1f).thickness(0.8f).life(14).ground().spawn();
			dust(l, c, (int) (radius * 8));
			QuirkCamera.shakeAround(l, c, radius * 3, a.kind == AirKind.DROP ? 0.6f : 0.35f, 10);
			OmnitrixSounds.play(l, c, SND_QUAKE, a.kind == AirKind.DROP ? 1.4f : 1.0f, 1.0f);
			if (a.kind == AirKind.CURL_SMASH) {
				anim(p, ANIM_UNCURL);
			}
		}
	}

	// ---------------------------------------------------------------- v0.17.2: the Sprint Launch and the fall bounces

	/** His real motion last tick (horizontal), for anything that sets his velocity (the server's own is not his). */
	private static Vec3 motion(ServerPlayer p) {
		Vec3 m = LAST_MOVE.getOrDefault(p.getUUID(), Vec3.ZERO);
		return new Vec3(m.x, 0, m.z);
	}

	/**
	 * v0.17.2 (user: "when he sprint jumps its more like a forward launch kind of thing"): a sprint jump in Ball Mode fires him
	 * forward where he looks -- a long, low arc at least as fast as he was rolling -- in a puff of dust and a crack.
	 */
	private void sprintLaunch(ServerPlayer p, ServerLevel l, long now, Vec3 moved) {
		if (AIRS.containsKey(p.getUUID()) || DASHES.containsKey(p.getUUID()) || RICOS.containsKey(p.getUUID()) || flag(p, F_SPIN)) {
			return;
		}
		Long last = LAST_LAUNCH.get(p.getUUID());
		if (last != null && now - last < LAUNCH_GAP_TICKS) {
			return;
		}
		LAST_LAUNCH.put(p.getUUID(), now);
		BOUNCES.remove(p.getUUID());
		Vec3 dir = flat(p);
		double speed = Math.max(LAUNCH_SPEED, Math.sqrt(moved.x * moved.x + moved.z * moved.z) * 1.2);
		p.setDeltaMovement(dir.x * speed, LAUNCH_LIFT, dir.z * speed);
		p.hurtMarked = true;
		Air a = new Air(AirKind.LAUNCH, now);
		a.dir = dir;
		AIRS.put(p.getUUID(), a);
		setFlag(p, F_AIR, true);
		ROLLED.put(p.getUUID(), MOMENTUM_TICKS);
		Vec3 c = center(p);
		QuirkFx.ring(l, c.subtract(dir.scale(0.6))).flags(QuirkFx.F_VERTICAL).vec(dir).colours(0xFFFFFF, COLOUR).size(2.4f).thickness(0.5f).life(9).spawn();
		dust(l, p.position(), 18);
		l.sendParticles(ParticleTypes.CLOUD, c.x, p.getY() + 0.2, c.z, 10, 0.5, 0.1, 0.5, 0.05);
		OmnitrixSounds.play(l, p.position(), SND_QUAKE, 0.8f, 1.4f);
	}

	/** The launch comes down: a thud and a small ring (12 within 2.5), and he rolls straight on at speed. */
	private void landLaunch(ServerPlayer p, ServerLevel l, Air a) {
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, 2.5)) {
			hurt(p, e, LAUNCH_LAND_DAMAGE, false);
			shove(e, c, 1.0, 0.4);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3.2f).thickness(0.6f).life(12).ground().spawn();
		dust(l, c, 24);
		QuirkCamera.shake(p, 0.3f, 6);
		OmnitrixSounds.play(l, c, SND_IMPACT, 1.0f, 0.9f);
		p.setDeltaMovement(a.dir.x * 0.9, 0, a.dir.z * 0.9);
		p.hurtMarked = true;
		ROLLED.put(p.getUUID(), MOMENTUM_TICKS);
	}

	/** One bounce of a high fall's run: up by the next lift, still rolling the way he was. */
	private void bounceOnce(ServerPlayer p, ServerLevel l, Bounces b) {
		Vec3 v = motion(p);
		p.setDeltaMovement(v.x, b.lift, v.z);
		p.hurtMarked = true;
		b.airborne = false;
		b.left--;
		b.lift *= BOUNCE_KEEP;
		l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.4, 0.05, 0.4, 0.2);
		dust(l, p.position(), 10);
		OmnitrixSounds.play(l, p.position(), SND_RICOCHET, 0.5f + (float) b.lift, 0.7f + (FALL_BOUNCES - b.left) * 0.12f);
	}

	/** The rest of a fall's bounces: each time he lands again, a smaller one, until they run out (never more than 6 s). */
	private void tickBounces(ServerPlayer p, ServerLevel l, long now) {
		Bounces b = BOUNCES.get(p.getUUID());
		if (b == null) {
			return;
		}
		if (!flag(p, F_BALL) || p.isInWater() || now - b.start > 120 || AIRS.containsKey(p.getUUID())) {
			BOUNCES.remove(p.getUUID());
			return;
		}
		p.resetFallDistance();
		if (!p.onGround()) {
			b.airborne = true;
			return;
		}
		if (!b.airborne) {
			return;
		}
		if (b.left <= 0 || b.lift < BOUNCE_MIN) {
			BOUNCES.remove(p.getUUID()); // settled: he rolls on
			return;
		}
		bounceOnce(p, l, b);
	}

	/** Gametests: the fall bounces still to come (0 = none running). */
	public static int bouncesLeft(ServerPlayer p) {
		Bounces b = BOUNCES.get(p.getUUID());
		return b == null ? 0 : b.left;
	}

	// ---------------------------------------------------------------- helpers

	/** Projectiles flying at him within {@code radius} glance off the shell ({@code frontOnly}: the Shell Guard's front). */
	private void deflectAround(ServerPlayer p, ServerLevel l, double radius, boolean frontOnly) {
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(radius), pr -> pr.getOwner() != p)) {
			Vec3 to = center(p).subtract(pr.position());
			if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0 || frontOnly && !inFront(p, pr.position())) {
				continue;
			}
			pr.deflect(frontOnly ? ProjectileDeflection.AIM_DEFLECT : ProjectileDeflection.REVERSE, p, p, true);
			pr.setOwner(p);
			l.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
			OmnitrixSounds.play(l, pr.position(), SND_DEFLECT, 0.9f, 1.1f);
		}
	}

	/** Bursts weak blocks (glass, leaves, light wood...) in the slab ahead. True if anything is still in the way after. */
	private boolean smashAhead(ServerPlayer p, ServerLevel l, Vec3 dir, int depth) {
		if (depth <= 0) {
			return true;
		}
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return true;
		}
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		int high = Math.max(1, Mth.ceil(p.getBbHeight()));
		for (int ahead = 1; ahead <= depth; ahead++) {
			for (int s = -1; s <= 1; s++) {
				for (int up = 0; up < high; up++) {
					BlockPos pos = BlockPos.containing(p.position().add(flat.scale(ahead * 0.8)).add(side.scale(s * 0.6)).add(0, up + 0.2, 0));
					BlockState st = l.getBlockState(pos);
					if (FourArmsKit.weakBlock(l, pos, st) && HeroConfig.get().abilityTerrainDamage && p.mayBuild() && l.mayInteract(p, pos)) {
						l.destroyBlock(pos, true, p);
					}
				}
			}
		}
		return true;
	}

	/** Whether a solid block sits right in front of the ball / body along {@code dir}. */
	private static boolean blocked(ServerPlayer p, ServerLevel l, Vec3 dir, double speed) {
		Vec3 c = center(p);
		Vec3 flat = new Vec3(dir.x, dir.y, dir.z);
		BlockHitResult hit = l.clip(new ClipContext(c, c.add(flat.scale(speed + p.getBbWidth() * 0.5 + 0.1)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis() != Direction.Axis.Y;
	}

	private static Vec3 center(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.5, 0);
	}

	private static Vec3 flat(Player p) {
		return Vec3.directionFromRotation(0, p.getYRot());
	}

	private static boolean inFront(Player p, Vec3 at) {
		Vec3 to = at.subtract(p.position());
		return new Vec3(to.x, 0, to.z).normalize().dot(flat(p)) > 0.2;
	}

	private static void dust(ServerLevel l, Vec3 c, int n) {
		BlockState under = l.getBlockState(BlockPos.containing(c).below());
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, n, 1.2, 0.1, 1.2, 0.15);
		}
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		boolean ball = flag(p, F_BALL);
		if (source.is(DamageTypeTags.IS_FALL)) {
			if (flag(p, F_AIR) || DASHES.containsKey(p.getUUID())) {
				return false; // his own landings
			}
			if (ball) {
				// v0.17.2 (user: "if he falls from a great distance the ball form should bounce a bit first"): the shell takes no fall
				// damage -- a fall that would have hurt (8+ blocks) starts a run of bounces, each smaller, then he rolls on
				double lift = Mth.clamp(0.3 + amount * 0.04, 0.45, 1.1);
				Bounces b = new Bounces(lift, FALL_BOUNCES, p.level().getGameTime());
				BOUNCES.put(p.getUUID(), b);
				bounceOnce(p, level(p), b);
				return false;
			}
		}
		// the rolled-up shell (and the Shell Guard's front) turns projectiles away
		if (source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.BYPASSES_ARMOR)
				&& (ball || flag(p, F_GUARD) && source.getSourcePosition() != null && inFront(p, source.getSourcePosition()))) {
			OmnitrixSounds.play(level(p), p.position(), SND_DEFLECT, 0.9f, 1.0f);
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return amount;
		}
		if (flag(p, F_UNSTOPPABLE)) {
			return amount * 0.2f;
		}
		if (flag(p, F_ROUND)) {
			return amount * 0.5f; // the Armoured Shell, all the way round
		}
		Vec3 from = source.getSourcePosition();
		if (from != null) {
			if (flag(p, F_GUARD) && inFront(p, from)) {
				return amount * 0.25f;
			}
			if (!inFront(p, from) && behind(p, from)) {
				return amount * 0.6f; // the plates on his back
			}
		}
		return amount;
	}

	private static boolean behind(Player p, Vec3 at) {
		Vec3 to = at.subtract(p.position());
		return new Vec3(to.x, 0, to.z).normalize().dot(flat(p)) < -0.3;
	}

	/** Gametests: whether a dash / ricochet / airborne move is running for {@code p}. */
	public static boolean moving(ServerPlayer p) {
		return DASHES.containsKey(p.getUUID()) || RICOS.containsKey(p.getUUID()) || AIRS.containsKey(p.getUUID());
	}

	/** Gametests: the Momentum ticks banked. */
	public static int momentum(ServerPlayer p) {
		return ROLLED.getOrDefault(p.getUUID(), 0);
	}
}
