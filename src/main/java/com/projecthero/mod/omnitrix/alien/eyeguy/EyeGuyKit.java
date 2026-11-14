package com.projecthero.mod.omnitrix.alien.eyeguy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Eye Guy, an Opticoid (prototype Omnitrix, a
 * hidden alien found by a mistransform): a slim yellow alien covered in eyes, with a great eye in his chest. His eyes fire beams -- a
 * plain laser, freezing beams, fire beams -- he sees in every direction at once, and the chest eye fires one huge beam.
 *
 * <pre>
 *   R          Eye Laser          a green laser from his eyes at the crosshair (32 blocks): 7
 *   Shift+R    Laser Volley       a laser from six eyes at once at up to six creatures in front of him (20 blocks, 70 deg): 6 each
 *                                 (all six into one creature if it is alone)
 *   G          Freeze Beam        an ice-blue beam (24): 12, Slowness IV 4 s and frozen solid (powder snow's chill)
 *   Shift+G    Fire Beam          an orange beam (24): 12 and set alight for 5 s
 *   X          Eye Dodge          a quick hop the way he is moving (backwards when still); 0.4 s untouchable
 *   Z          Chest Eye Beam     ULTIMATE: 1.5 s charging (slowed, the chest eye blazing), then a huge piercing beam (36 blocks): 32 to
 *                                 everything on it, knocked back
 *   C          Eye Flash          every eye flashes at once: everything within 9 blinded and dazed 3 s (mobs lose him), 5 to each
 *   V          All-Seeing         12 s: every creature within 40 blocks shows through walls (to him only) and he sees in the dark
 *   Passives   Opticoid (no blind spot: hits from behind do 25% less); Many Eyes (Blindness and Darkness never take him)
 *   Weakness   Soft Body (thin and frail: 28 health, light armour); Dazzled (his beams do half while he burns: his eyes
 *              water)
 *   v0.17.4 (docs/OMNITRIX_PROTOTYPE_REVAMP_0174.md), nothing removed; new:
 *   Hold R     Sweeping Laser     a beam from his eyes that follows his aim while held (up to 3 s): 2 every 0.2 s to what it touches
 *   Hold G     Thermal Shock      freeze and fire beams at once while held (up to 2.5 s): 4 every 0.25 s, slowed and burning; a creature
 *                                 caught the whole way (1.5 s or more) shatters for 10 more
 *   Shift+X    Retreat Volley     a hop back 6 blocks and three lasers at the nearest creature: 5 each
 *   Shift+Z    Optic Lockdown     his eyes lock on up to 6 creatures within 16 for 3 s: 2 every 0.5 s, Slowness II
 *   Shift+V    Weak Spot          the creature he looks at is marked for 8 s: it takes 30% more from him
 *   Hold V     Far Sight          his view zooms in while held, like a spyglass
 *   Shift+C    Hypno Gaze         the creature he stares down forgets him for 5 s and attacks the nearest other mob (not bosses)
 * </pre>
 */
public final class EyeGuyKit extends AlienKit {
	public static final String ID = "eye_guy";
	/** The beams' colours (the client tints his glowing irises with the last one fired). */
	public static final int LASER = 0x6CFF3A, ICE = 0x8FE0FF, FIRE = 0xFF8A1E, MEGA = 0xE8FF6A, FLASH = 0xFFFFE0;
	public static final DustParticleOptions LASER_DUST = new DustParticleOptions(new org.joml.Vector3f(0.45f, 1f, 0.25f), 0.6f);
	public static final DustParticleOptions ICE_DUST = new DustParticleOptions(new org.joml.Vector3f(0.6f, 0.9f, 1f), 0.7f);

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_SIGHT = 1, F_MEGA = 2, F_DODGE = 4, F_SWEEP = 8, F_THERMAL = 16, F_RETREAT = 32, F_LOCK = 64, F_ZOOM = 128;
	public static final int ANIM_LASER = 1, ANIM_VOLLEY = 2, ANIM_FREEZE = 3, ANIM_FIRE = 4, ANIM_DODGE = 5, ANIM_MEGA_CHARGE = 6, ANIM_MEGA = 7, ANIM_FLASH = 8,
			ANIM_SIGHT = 9, ANIM_PUNCH = 10, ANIM_SWEEP = 11, ANIM_THERMAL = 12, ANIM_RETREAT = 13, ANIM_LOCK = 14, ANIM_WEAK = 15, ANIM_ZOOM = 16, ANIM_HYPNO = 17,
			ANIM_SHATTER = 18;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.eye_guy.<id>)
	public static final String LASER_ID = "eye_laser", VOLLEY = "laser_volley", FREEZE = "freeze_beam", FIRE_ID = "fire_beam", DODGE = "eye_dodge",
			MEGA_ID = "chest_eye_beam", FLASH_ID = "eye_flash", SIGHT = "all_seeing", SWEEP = "sweeping_laser", THERMAL = "thermal_shock",
			RETREAT = "retreat_volley", LOCK = "optic_lockdown", WEAK = "weak_spot", ZOOM = "far_sight", HYPNO = "hypno_gaze", OPTICOID = "opticoid", MANY_EYES = "many_eyes", SOFT = "soft_body",
			DAZZLED = "dazzled";

	public static final String SIGHT_UNTIL = "sight_until", MEGA_AT = "mega_at", DODGE_UNTIL = "dodge_until", SWEEP_AT = "sweep_at", THERMAL_AT = "thermal_at",
			RETREAT_AT = "retreat_at", LOCK_AT = "lock_at", WEAK_ID = "weak_id", WEAK_UNTIL = "weak_until";
	private static final String[] TIMERS = { SIGHT_UNTIL, MEGA_AT, DODGE_UNTIL, SWEEP_AT, THERMAL_AT, RETREAT_AT, LOCK_AT, WEAK_ID, WEAK_UNTIL };

	// ---------------------------------------------------------------- numbers (basic 5-8, specials 12-17, big 17-20, ultimates 21-36)
	public static final float LASER_DAMAGE = 7f, VOLLEY_DAMAGE = 6f, FREEZE_DAMAGE = 12f, FIRE_DAMAGE = 12f, MEGA_DAMAGE = 32f, FLASH_DAMAGE = 5f;
	public static final float BEHIND_TAKEN = 0.75f, DAZZLED_DEALT = 0.5f, SWEEP_DAMAGE = 2f, THERMAL_DAMAGE = 4f, THERMAL_SHATTER = 10f, RETREAT_DAMAGE = 5f,
			LOCK_DAMAGE = 2f, WEAK_TAKEN = 1.3f;
	/** v0.17.4: the holds' longest runs and pulse gaps, and the timed moves (ticks). */
	public static final int SWEEP_MAX = 60, SWEEP_EVERY = 4, THERMAL_MAX = 50, THERMAL_EVERY = 5, THERMAL_MIN_PULSES = 6, RETREAT_SHOTS = 3, RETREAT_EVERY = 3,
			LOCK_TICKS = 60, LOCK_EVERY = 10, LOCK_MAX = 6, WEAK_TICKS = 160, HYPNO_TICKS = 100;
	public static final double SWEEP_RANGE = 32, SWEEP_RADIUS = 0.6, RETREAT_RANGE = 20, LOCK_RANGE = 16, WEAK_RANGE = 32, HYPNO_RANGE = 20;
	public static final int VOLLEY_EYES = 6, FREEZE_SLOW_TICKS = 80, FIRE_SECONDS = 5, MEGA_CHARGE_TICKS = 30, FLASH_TICKS = 60, SIGHT_TICKS = 240, DODGE_TICKS = 8;
	public static final double LASER_RANGE = 32, VOLLEY_RANGE = 20, VOLLEY_HALF_ANGLE = 35, BEAM_RANGE = 24, MEGA_RANGE = 36, FLASH_RADIUS = 9, SIGHT_RANGE = 40;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_LASER = OmnitrixSfx.cue("eye_guy_laser")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.35f, 2.0f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.4f, 1.8f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_FREEZE = OmnitrixSfx.cue("eye_guy_freeze")
			.layer(SoundEvents.GLASS_BREAK, 0.3f, 1.6f)
			.layer(SoundEvents.POWDER_SNOW_STEP, 0.6f, 0.8f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.8f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_FIRE = OmnitrixSfx.cue("eye_guy_fire")
			.layer(SoundEvents.BLAZE_SHOOT, 0.4f, 1.3f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.3f, 1.1f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_MEGA_CHARGE = OmnitrixSfx.cue("eye_guy_mega_charge")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.5f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.8f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_MEGA = OmnitrixSfx.cue("eye_guy_mega")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.8f)
			.layer(SoundEvents.GENERIC_EXPLODE.value(), 0.3f, 1.3f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.25f, 1.6f, 1)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_FLASH = OmnitrixSfx.cue("eye_guy_flash")
			.layer(SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f, 1.4f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_SIGHT = OmnitrixSfx.cue("eye_guy_sight")
			.layer(SoundEvents.ENDER_EYE_DEATH, 0.5f, 1.4f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 1.6f)
			.gap(6);
	public static final OmnitrixSfx.Cue SND_DODGE = OmnitrixSfx.cue("eye_guy_dodge")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.3f, 1.8f)
			.gap(3);

	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** v0.17.4: the held beams' drawn carriers (Sweeping Laser: one; Thermal Shock: ice, fire). */
	private static final Map<UUID, AlienProjectileEntity[]> HELD_BEAMS = new ConcurrentHashMap<>();
	/** v0.17.4 Thermal Shock: how many pulses have caught each creature, and how many pulses there have been. */
	private static final Map<UUID, Map<Integer, Integer>> THERMAL_HITS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> THERMAL_PULSES = new ConcurrentHashMap<>();
	/** v0.17.4: the Retreat Volley's mark and the Optic Lockdown's (entity ids). */
	private static final Map<UUID, Integer> RETREAT_TARGET = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Integer>> LOCKED = new ConcurrentHashMap<>();

	private EyeGuyKit() {
	}

	public static void register() {
		Aliens.register(new EyeGuyKit());
	}

	public static void clearSessionState() {
		LAST_TICK.clear();
		HELD_BEAMS.clear();
		THERMAL_HITS.clear();
		THERMAL_PULSES.clear();
		RETREAT_TARGET.clear();
		LOCKED.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	public static boolean isEyeGuy(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	/** All-Seeing is up for {@code p} (client-safe: a synced flag). */
	public static boolean seeing(Player p) {
		return isEyeGuy(p) && (Omnitrix.state(p).kitFlags & F_SIGHT) != 0;
	}

	@Override
	public AlienStats stats() {
		// slim and Ben's height, light on his feet; frail
		return AlienStats.builder().health(28f).speed(1.1f).scale(1.0f).armor(4f, 0f).attack(5f).jump(0.2f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(LASER_ID, AbilitySlot.SLOT_1, 10), AlienMove.shift(VOLLEY, AbilitySlot.SLOT_1, 80), AlienMove.hold(SWEEP, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(FREEZE, AbilitySlot.SLOT_2, 120), AlienMove.shift(FIRE_ID, AbilitySlot.SLOT_2, 120), AlienMove.hold(THERMAL, AbilitySlot.SLOT_2, 280),
				AlienMove.tap(DODGE, AbilitySlot.SLOT_3, 30), AlienMove.shift(RETREAT, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(MEGA_ID, AbilitySlot.SLOT_4, 700), AlienMove.shift(LOCK, AbilitySlot.SLOT_4, 360),
				AlienMove.tap(SIGHT, AbilitySlot.SLOT_5, 500), AlienMove.shift(WEAK, AbilitySlot.SLOT_5, 240), AlienMove.hold(ZOOM, AbilitySlot.SLOT_5, 0),
				AlienMove.tap(FLASH_ID, AbilitySlot.SLOT_6, 260), AlienMove.shift(HYPNO, AbilitySlot.SLOT_6, 400),
				AlienMove.vanilla(OPTICOID, "Passive", 0), AlienMove.vanilla(MANY_EYES, "Passive", 0), AlienMove.vanilla(SOFT, "Weakness", 0),
				AlienMove.vanilla(DAZZLED, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_SWEEP);
			case SLOT_2 -> flag(p, F_THERMAL);
			case SLOT_4 -> flag(p, F_MEGA) || flag(p, F_LOCK);
			case SLOT_5 -> flag(p, F_SIGHT) || flag(p, F_ZOOM);
			default -> false;
		};
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_5;
	}

	// ---------------------------------------------------------------- beams

	/**
	 * A beam drawn from one of his eyes to {@code end} for {@code life} ticks: {@code kind} picks the look ("eye_guy_beam" straight from his
	 * eyes, "eye_guy_volley_<eye>" from eye {@code eye} (0..5) round his body), {@code rgb} its colour,
	 * {@code width} in blocks.
	 */
	public static void beam(ServerPlayer p, String kind, Vec3 end, int rgb, int life, float width, int eye) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, kind.equals("eye_guy_volley") ? kind + "_" + eye : kind);
		a.moveTo(end.x, end.y, end.z, 0f, 0f);
		a.colour(rgb).size(width).held(true).speed(0).life(life).damage(0f).spawn();
	}

	private static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	private float dealt(ServerPlayer p, float amount) {
		// Dazzled: burning, his eyes water and the beams scatter
		return p.isOnFire() ? amount * DAZZLED_DEALT : amount;
	}

	/** v0.17.4: one of his hits on {@code t} -- Dazzled, and 30% more on the creature his Weak Spot marks. */
	private boolean zap(ServerPlayer p, LivingEntity t, float amount, boolean fire) {
		t.invulnerableTime = 0;
		return hurt(p, t, dealt(p, amount) * (marked(p, t) ? WEAK_TAKEN : 1f), fire);
	}

	/** Whether {@code e} wears {@code p}'s Weak Spot mark right now (client-safe). */
	public static boolean marked(Player p, Entity e) {
		OmnitrixState s = Omnitrix.state(p);
		return e != null && s.readyAt(ID + "." + WEAK_ID) == e.getId() + 1L && s.readyAt(ID + "." + WEAK_UNTIL) > p.level().getGameTime();
	}

	/** {@code p}'s Far Sight zoom is held (client-safe). */
	public static boolean zooming(Player p) {
		return isEyeGuy(p) && (Omnitrix.state(p).kitFlags & F_ZOOM) != 0;
	}

	/** The beam end at the crosshair: the creature there (and it), else the block / range end. */
	private record Aim(Vec3 end, LivingEntity target) {
	}

	private static Aim aim(ServerPlayer p, double range) {
		LivingEntity t = lookTarget(p, range);
		return new Aim(t != null ? centre(t) : RecalKitMath.beamEnd(p, p.getLookAngle(), range), t);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_SIGHT, 0.6f, 1.2f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		LAST_TICK.remove(p.getUUID());
		endHeldBeams(p);
		THERMAL_HITS.remove(p.getUUID());
		THERMAL_PULSES.remove(p.getUUID());
		RETREAT_TARGET.remove(p.getUUID());
		LOCKED.remove(p.getUUID());
		com.projecthero.mod.omnitrix.alien.AlienConfusion.clear(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : TIMERS) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		p.removeEffect(MobEffects.NIGHT_VISION);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_DODGE) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // mid-hop: it never touches him
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		// Opticoid: eyes in the back of his head -- nothing sneaks up on him
		Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (from != null && from != p) {
			Vec3 to = from.position().subtract(p.position());
			Vec3 facing = Vec3.directionFromRotation(0f, p.getYRot());
			if (to.lengthSqr() > 1e-4 && to.normalize().dot(facing) < -0.3) {
				return amount * BEHIND_TAKEN;
			}
		}
		return amount;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		// Many Eyes: one of them is always open
		if (p.hasEffect(MobEffects.BLINDNESS)) {
			p.removeEffect(MobEffects.BLINDNESS);
		}
		if (p.hasEffect(MobEffects.DARKNESS)) {
			p.removeEffect(MobEffects.DARKNESS);
		}
		if (flag(p, F_SIGHT)) {
			if (now >= timer(p, SIGHT_UNTIL)) {
				setFlag(p, F_SIGHT, false);
				setTimer(p, SIGHT_UNTIL, 0L);
				p.removeEffect(MobEffects.NIGHT_VISION);
			} else if (now % 40 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));
			}
		}
		if (flag(p, F_DODGE) && now >= timer(p, DODGE_UNTIL)) {
			setFlag(p, F_DODGE, false);
			setTimer(p, DODGE_UNTIL, 0L);
		}
		if (flag(p, F_SWEEP)) {
			sweepTick(p, level(p), now);
		}
		if (flag(p, F_THERMAL)) {
			thermalTick(p, level(p), now);
		}
		if (flag(p, F_RETREAT)) {
			retreatTick(p, level(p), now);
		}
		if (flag(p, F_LOCK)) {
			lockTick(p, level(p), now);
		}
		com.projecthero.mod.omnitrix.alien.AlienConfusion.tick(p);
		if (flag(p, F_MEGA)) {
			long at = timer(p, MEGA_AT);
			if (now >= at) {
				fireMega(p);
			} else if (now % 4 == 0) {
				Vec3 chest = p.position().add(0, p.getBbHeight() * 0.6, 0).add(p.getLookAngle().scale(0.4));
				level(p).sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 2, 0.4, 0.4, 0.4, 0.02);
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_MEGA)) {
			return; // the chest eye is charging: nothing else
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					volley(p);
				} else {
					laser(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					fireBeam(p);
				} else {
					freezeBeam(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					retreatVolley(p);
				} else {
					dodge(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					opticLockdown(p);
				} else {
					chestBeam(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					weakSpot(p);
				} else {
					allSeeing(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					hypnoGaze(p);
				} else {
					flash(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (flag(p, F_MEGA)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> startSweep(p);
			case SLOT_2 -> startThermal(p);
			case SLOT_5 -> {
				setFlag(p, F_ZOOM, true);
				anim(p, ANIM_ZOOM);
			}
			default -> {
			}
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		switch (slot) {
			case SLOT_1 -> endSweep(p);
			case SLOT_2 -> endThermal(p);
			case SLOT_5 -> {
				if (flag(p, F_ZOOM)) {
					setFlag(p, F_ZOOM, false);
					anim(p, ANIM_PUNCH);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		anim(p, ANIM_PUNCH);
		if (target instanceof LivingEntity e && marked(p, e)) {
			// v0.17.4 Weak Spot: his punch finds the mark (the hit is the kit's, 30% harder)
			float k = Math.max(0.2f, p.getAttackStrengthScale(0.5f));
			e.invulnerableTime = 0;
			hurt(p, e, (float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * k * WEAK_TAKEN, false);
			p.resetAttackStrengthTicker();
			level(p).sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.2, 0.3, 0.2, 0.2);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a quick green laser from his eyes at the crosshair. */
	public boolean laser(ServerPlayer p) {
		if (!use(p, LASER_ID)) {
			return false;
		}
		anim(p, ANIM_LASER);
		Aim a = aim(p, LASER_RANGE);
		if (a.target != null) {
			zap(p, a.target, LASER_DAMAGE, false);
		}
		beam(p, "eye_guy_beam", a.end, LASER, 6, 0.09f, 0);
		level(p).sendParticles(LASER_DUST, a.end.x, a.end.y, a.end.z, 6, 0.15, 0.15, 0.15, 0.05);
		OmnitrixSfx.playOn(p, SND_LASER, 0.6f, 1.2f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: six eyes at once, each at a creature in front of him (all six at one if it is alone). */
	public boolean volley(ServerPlayer p) {
		List<LivingEntity> in = new ArrayList<>(RecalKitMath.cone(p, VOLLEY_RANGE, VOLLEY_HALF_ANGLE));
		in.removeIf(e -> !p.hasLineOfSight(e));
		in.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
		if (!use(p, VOLLEY)) {
			return false;
		}
		anim(p, ANIM_VOLLEY);
		for (int i = 0; i < VOLLEY_EYES; i++) {
			if (in.isEmpty()) {
				// nothing to shoot: the eyes fan out at the crosshair
				Vec3 end = RecalKitMath.beamEnd(p, RecalKitMath.turned(p.getLookAngle(), (i - 2.5) * 6), VOLLEY_RANGE);
				beam(p, "eye_guy_volley", end, LASER, 7, 0.06f, i);
				continue;
			}
			LivingEntity t = in.get(i % in.size());
			zap(p, t, VOLLEY_DAMAGE, false);
			Vec3 c = centre(t).add((p.getRandom().nextDouble() - 0.5) * 0.4, (p.getRandom().nextDouble() - 0.5) * 0.5, (p.getRandom().nextDouble() - 0.5) * 0.4);
			beam(p, "eye_guy_volley", c, LASER, 7, 0.06f, i);
			level(p).sendParticles(LASER_DUST, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0.05);
		}
		OmnitrixSfx.playOn(p, SND_LASER, 0.75f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: an ice-blue beam -- the creature it hits is slowed hard and frozen over. */
	public boolean freezeBeam(ServerPlayer p) {
		if (!use(p, FREEZE)) {
			return false;
		}
		anim(p, ANIM_FREEZE);
		Aim a = aim(p, BEAM_RANGE);
		if (a.target != null) {
			zap(p, a.target, FREEZE_DAMAGE, false);
			a.target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_SLOW_TICKS, 3), p);
			a.target.setTicksFrozen(Math.max(a.target.getTicksFrozen(), a.target.getTicksRequiredToFreeze() + FREEZE_SLOW_TICKS));
			a.target.clearFire();
			Vec3 c = a.end;
			level(p).sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.05);
		}
		beam(p, "eye_guy_beam", a.end, ICE, 9, 0.13f, 0);
		level(p).sendParticles(ICE_DUST, a.end.x, a.end.y, a.end.z, 10, 0.2, 0.2, 0.2, 0.05);
		OmnitrixSfx.playOn(p, SND_FREEZE, 0.7f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: an orange beam that sets what it hits alight. */
	public boolean fireBeam(ServerPlayer p) {
		if (!use(p, FIRE_ID)) {
			return false;
		}
		anim(p, ANIM_FIRE);
		Aim a = aim(p, BEAM_RANGE);
		if (a.target != null) {
			zap(p, a.target, FIRE_DAMAGE, true);
			a.target.igniteForSeconds(FIRE_SECONDS);
			a.target.setTicksFrozen(0);
		}
		beam(p, "eye_guy_beam", a.end, FIRE, 9, 0.13f, 0);
		level(p).sendParticles(ParticleTypes.FLAME, a.end.x, a.end.y, a.end.z, 14, 0.2, 0.3, 0.2, 0.04);
		OmnitrixSfx.playOn(p, SND_FIRE, 0.7f, 1f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- X

	/** X: a quick hop the way he is moving (straight back when standing), untouchable for 0.4 s. */
	public boolean dodge(ServerPlayer p) {
		if (!use(p, DODGE)) {
			return false;
		}
		anim(p, ANIM_DODGE);
		Vec3 mv = p.getDeltaMovement();
		Vec3 h = new Vec3(mv.x, 0, mv.z);
		if (h.lengthSqr() < 0.004) {
			Vec3 look = p.getLookAngle();
			h = new Vec3(-look.x, 0, -look.z);
		}
		h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : h.normalize();
		p.setDeltaMovement(h.scale(1.1).add(0, 0.42, 0));
		p.hurtMarked = true;
		setTimer(p, DODGE_UNTIL, p.level().getGameTime() + DODGE_TICKS);
		setFlag(p, F_DODGE, true);
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_DODGE, 0.6f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- Z

	/** Z: the chest eye opens and charges (1.5 s, slowed), then fires {@link #fireMega}. */
	public boolean chestBeam(ServerPlayer p) {
		if (!use(p, MEGA_ID)) {
			return false;
		}
		anim(p, ANIM_MEGA_CHARGE);
		setTimer(p, MEGA_AT, p.level().getGameTime() + MEGA_CHARGE_TICKS);
		setFlag(p, F_MEGA, true);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MEGA_CHARGE_TICKS + 4, 3, false, false, true));
		OmnitrixSfx.playOn(p, SND_MEGA_CHARGE, 0.75f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** The chest eye's beam: everything on a 36-block line (through creatures, stopped by blocks). */
	public void fireMega(ServerPlayer p) {
		setFlag(p, F_MEGA, false);
		setTimer(p, MEGA_AT, 0L);
		anim(p, ANIM_MEGA);
		Vec3 look = p.getLookAngle();
		Vec3 end = RecalKitMath.beamEnd(p, look, MEGA_RANGE);
		for (LivingEntity e : RecalKitMath.line(p, look, end.distanceTo(p.getEyePosition()), 1.0)) {
			if (zap(p, e, MEGA_DAMAGE, false)) {
				shove(e, p.position(), 1.2, 0.3);
			}
		}
		// the beam itself is drawn by the client from the ANIM_MEGA pose (EyeGuyFx.megaBeam): a carrier out at 36 blocks would be culled
		com.projecthero.mod.beamclash.BeamClash.report(p, p.getEyePosition(), look, MEGA_RANGE, 1.6f, MEGA); // v0.17.3: it can clash
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		l.sendParticles(LASER_DUST, end.x, end.y, end.z, 30, 0.6, 0.6, 0.6, 0.2);
		QuirkFx.blast(l, end).colours(0xFFFFFF, MEGA).size(2.2f).life(10).spawn();
		OmnitrixSfx.playOn(p, SND_MEGA, 0.75f, 1f, SoundSource.PLAYERS);
	}

	/** 0..1: how far the chest eye has charged (client-safe; 0 when not charging). */
	public static float megaCharge(Player p, float partial) {
		if ((Omnitrix.state(p).kitFlags & F_MEGA) == 0) {
			return 0f;
		}
		long at = Omnitrix.state(p).readyAt(ID + "." + MEGA_AT);
		float left = at - (p.level().getGameTime() + partial);
		return Mth.clamp(1f - left / MEGA_CHARGE_TICKS, 0f, 1f);
	}

	// ---------------------------------------------------------------- C

	/** C: every eye flashes at once -- everything round him blinded and dazed, mobs lose him. */
	public boolean flash(ServerPlayer p) {
		if (!use(p, FLASH_ID)) {
			return false;
		}
		anim(p, ANIM_FLASH);
		ServerLevel l = level(p);
		Vec3 head = p.getEyePosition();
		for (LivingEntity e : around(p, p.position(), FLASH_RADIUS)) {
			zap(p, e, FLASH_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLASH_TICKS, 0), p);
			AlienStatus.stun(p, e, FLASH_TICKS / 2);
			if (e instanceof Mob m) {
				AlienStatus.loseTrack(m, p);
			}
		}
		l.sendParticles(ParticleTypes.FLASH, head.x, head.y, head.z, 1, 0, 0, 0, 0);
		l.sendParticles(ParticleTypes.END_ROD, head.x, head.y - 0.4, head.z, 40, 0.5, 0.7, 0.5, 0.25);
		QuirkFx.ring(l, p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, FLASH).size((float) FLASH_RADIUS).thickness(0.3f).life(8).spawn();
		OmnitrixSfx.play(l, p.position(), SND_FLASH, 0.75f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- V

	/** V: All-Seeing -- every creature round him shows through walls (his eyes only) and the dark is no bar. */
	public boolean allSeeing(ServerPlayer p) {
		if (!use(p, SIGHT)) {
			return false;
		}
		anim(p, ANIM_SIGHT);
		setTimer(p, SIGHT_UNTIL, p.level().getGameTime() + SIGHT_TICKS);
		setFlag(p, F_SIGHT, true);
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));
		QuirkFx.ring(level(p), p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, LASER).size(4f).thickness(0.15f).life(10).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_SIGHT, 0.7f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.4: held beams (Sweeping Laser, Thermal Shock)

	/** A held beam carrier of {@code kind}, placed each tick by the kit (the client draws it from his eyes, steady while it lives). */
	private static AlienProjectileEntity heldBeam(ServerPlayer p, String kind, int rgb, float width, int life) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, kind);
		Vec3 at = RecalKitMath.beamEnd(p, p.getLookAngle(), SWEEP_RANGE);
		a.moveTo(at.x, at.y, at.z, 0f, 0f);
		a.colour(rgb).size(width).held(true).speed(0).life(life).damage(0f).spawn();
		return a;
	}

	private void endHeldBeams(ServerPlayer p) {
		AlienProjectileEntity[] b = HELD_BEAMS.remove(p.getUUID());
		if (b != null) {
			for (AlienProjectileEntity a : b) {
				a.discard();
			}
		}
	}

	/** Moves the held beams' far ends to where he aims (a creature in the way, else the block / range end); returns that end. */
	private Aim steer(ServerPlayer p, double range) {
		Aim a = aim(p, range);
		AlienProjectileEntity[] b = HELD_BEAMS.get(p.getUUID());
		if (b != null) {
			for (AlienProjectileEntity c : b) {
				c.setPos(a.end.x, a.end.y, a.end.z);
			}
		}
		return a;
	}

	/** Hold R: Sweeping Laser -- a beam from his eyes that follows his aim while R is held (up to 3 s): 2 every 0.2 s to what it touches. */
	public boolean startSweep(ServerPlayer p) {
		if (flag(p, F_SWEEP) || flag(p, F_THERMAL)) {
			return false;
		}
		if (!ready(p, SWEEP)) {
			return use(p, SWEEP);
		}
		anim(p, ANIM_SWEEP);
		endHeldBeams(p);
		HELD_BEAMS.put(p.getUUID(), new AlienProjectileEntity[] { heldBeam(p, "eye_guy_sweep", LASER, 0.1f, SWEEP_MAX + 20) });
		setTimer(p, SWEEP_AT, p.level().getGameTime());
		setFlag(p, F_SWEEP, true);
		OmnitrixSfx.playOn(p, SND_LASER, 0.7f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	private void sweepTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, SWEEP_AT);
		if (age >= SWEEP_MAX) {
			endSweep(p);
			return;
		}
		Aim a = steer(p, SWEEP_RANGE);
		Vec3 eye = p.getEyePosition();
		com.projecthero.mod.beamclash.BeamClash.report(p, eye, p.getLookAngle(), SWEEP_RANGE, 0.8f, LASER); // every live tick: it can clash
		if (com.projecthero.mod.beamclash.BeamClash.inClash(p)) {
			return; // the clash does the damage while it is locked
		}
		if (age % SWEEP_EVERY == 0) {
			for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), a.end.distanceTo(eye) + 0.3, SWEEP_RADIUS)) {
				zap(p, e, SWEEP_DAMAGE, false);
			}
			l.sendParticles(LASER_DUST, a.end.x, a.end.y, a.end.z, 4, 0.12, 0.12, 0.12, 0.05);
		}
		if (age % 10 == 0) {
			OmnitrixSfx.playOn(p, SND_LASER, 0.4f, 1.4f, SoundSource.PLAYERS);
		}
	}

	public void endSweep(ServerPlayer p) {
		if (!flag(p, F_SWEEP)) {
			return;
		}
		endHeldBeams(p);
		setFlag(p, F_SWEEP, false);
		setTimer(p, SWEEP_AT, 0L);
		cooldown(p, SWEEP, -1);
		anim(p, ANIM_PUNCH);
	}

	/**
	 * Hold G: Thermal Shock -- the freeze and fire beams together at what he aims at while G is held (up to 2.5 s): 4 every 0.25 s, slowed and
	 * burning. A creature caught by every pulse of it (1.5 s or more) shatters for 10 more when it ends.
	 */
	public boolean startThermal(ServerPlayer p) {
		if (flag(p, F_THERMAL) || flag(p, F_SWEEP)) {
			return false;
		}
		if (!ready(p, THERMAL)) {
			return use(p, THERMAL);
		}
		anim(p, ANIM_THERMAL);
		endHeldBeams(p);
		HELD_BEAMS.put(p.getUUID(), new AlienProjectileEntity[] { heldBeam(p, "eye_guy_sweep_ice", ICE, 0.12f, THERMAL_MAX + 20),
				heldBeam(p, "eye_guy_sweep_fire", FIRE, 0.12f, THERMAL_MAX + 20) });
		THERMAL_HITS.put(p.getUUID(), new ConcurrentHashMap<>());
		THERMAL_PULSES.put(p.getUUID(), 0);
		setTimer(p, THERMAL_AT, p.level().getGameTime());
		setFlag(p, F_THERMAL, true);
		OmnitrixSfx.playOn(p, SND_FREEZE, 0.6f, 0.9f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, SND_FIRE, 0.6f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	private void thermalTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, THERMAL_AT);
		if (age >= THERMAL_MAX) {
			endThermal(p);
			return;
		}
		Aim a = steer(p, BEAM_RANGE);
		com.projecthero.mod.beamclash.BeamClash.report(p, p.getEyePosition(), p.getLookAngle(), BEAM_RANGE, 1.2f, MEGA);
		if (com.projecthero.mod.beamclash.BeamClash.inClash(p) || age % THERMAL_EVERY != 0) {
			return;
		}
		int pulse = THERMAL_PULSES.merge(p.getUUID(), 1, Integer::sum);
		if (a.target != null) {
			LivingEntity t = a.target;
			zap(p, t, THERMAL_DAMAGE, true);
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), p);
			t.igniteForSeconds(3);
			THERMAL_HITS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).merge(t.getId(), 1, Integer::sum);
			Vec3 c = a.end;
			l.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 5, 0.2, 0.3, 0.2, 0.03);
			l.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 5, 0.2, 0.3, 0.2, 0.03);
			if (pulse % 2 == 0) {
				l.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02); // the steam of it
			}
		}
		if (pulse % 3 == 1) {
			OmnitrixSfx.playOn(p, SND_FIRE, 0.4f, 1.2f, SoundSource.PLAYERS);
		}
	}

	/** Ends Thermal Shock; every creature caught by every pulse (6 or more) shatters for 10. Returns how many shattered. */
	public int endThermal(ServerPlayer p) {
		if (!flag(p, F_THERMAL)) {
			return 0;
		}
		endHeldBeams(p);
		setFlag(p, F_THERMAL, false);
		setTimer(p, THERMAL_AT, 0L);
		cooldown(p, THERMAL, -1);
		int pulses = THERMAL_PULSES.getOrDefault(p.getUUID(), 0);
		Map<Integer, Integer> hits = THERMAL_HITS.remove(p.getUUID());
		THERMAL_PULSES.remove(p.getUUID());
		int n = 0;
		if (hits != null && pulses >= THERMAL_MIN_PULSES) {
			ServerLevel l = level(p);
			for (Map.Entry<Integer, Integer> en : hits.entrySet()) {
				if (en.getValue() >= pulses && l.getEntity(en.getKey()) instanceof LivingEntity t && t.isAlive()) {
					zap(p, t, THERMAL_SHATTER, false);
					Vec3 c = centre(t);
					QuirkFx.shards(l, c).block(net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()).size(Math.max(0.8f, t.getBbHeight() * 0.5f))
							.count(12).life(18).spawn();
					l.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.05);
					OmnitrixSfx.play(l, c, SND_FREEZE, 0.9f, 0.7f);
					n++;
				}
			}
		}
		anim(p, n > 0 ? ANIM_SHATTER : ANIM_PUNCH);
		return n;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+X Retreat Volley

	/** Shift+X: a hop back about 6 blocks, firing three lasers at the nearest creature as he goes: 5 each. */
	public boolean retreatVolley(ServerPlayer p) {
		if (!use(p, RETREAT)) {
			return false;
		}
		anim(p, ANIM_RETREAT);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(-look.x, 0, -look.z);
		h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : h.normalize();
		p.setDeltaMovement(h.scale(1.15).add(0, 0.5, 0));
		p.hurtMarked = true;
		setTimer(p, DODGE_UNTIL, p.level().getGameTime() + DODGE_TICKS);
		setFlag(p, F_DODGE, true);
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (LivingEntity e : around(p, p.position(), RETREAT_RANGE)) {
			double d = e.distanceToSqr(p);
			if (d < bd && p.hasLineOfSight(e)) {
				bd = d;
				best = e;
			}
		}
		if (best != null) {
			RETREAT_TARGET.put(p.getUUID(), best.getId());
		} else {
			RETREAT_TARGET.remove(p.getUUID());
		}
		setTimer(p, RETREAT_AT, p.level().getGameTime());
		setFlag(p, F_RETREAT, true);
		retreatShot(p, level(p));
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_DODGE, 0.6f, 1.2f);
		return true;
	}

	private void retreatTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, RETREAT_AT);
		if (age >= (long) RETREAT_SHOTS * RETREAT_EVERY - 2) {
			setFlag(p, F_RETREAT, false);
			setTimer(p, RETREAT_AT, 0L);
			RETREAT_TARGET.remove(p.getUUID());
			return;
		}
		if (age > 0 && age % RETREAT_EVERY == 0) {
			retreatShot(p, l);
		}
	}

	/** One of the Retreat Volley's lasers: at its mark if it still stands, else at the crosshair. */
	private void retreatShot(ServerPlayer p, ServerLevel l) {
		Integer id = RETREAT_TARGET.get(p.getUUID());
		Entity e = id == null ? null : l.getEntity(id);
		Vec3 end;
		if (e instanceof LivingEntity t && t.isAlive()) {
			zap(p, t, RETREAT_DAMAGE, false);
			end = centre(t);
		} else {
			end = RecalKitMath.beamEnd(p, p.getLookAngle(), LASER_RANGE);
		}
		beam(p, "eye_guy_volley", end, LASER, 6, 0.07f, l.random.nextInt(VOLLEY_EYES));
		l.sendParticles(LASER_DUST, end.x, end.y, end.z, 4, 0.12, 0.12, 0.12, 0.05);
		OmnitrixSfx.playOn(p, SND_LASER, 0.55f, 1.3f, SoundSource.PLAYERS);
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+Z Optic Lockdown

	/** Shift+Z: his eyes lock onto up to 6 creatures within 16 for 3 s: 2 every half second, Slowness II. Returns how many he locked. */
	public int opticLockdown(ServerPlayer p) {
		if (flag(p, F_LOCK)) {
			return 0;
		}
		List<LivingEntity> in = new ArrayList<>(around(p, p.position(), LOCK_RANGE));
		in.removeIf(e -> !p.hasLineOfSight(e));
		if (in.isEmpty()) {
			if (cooldownLeft(p, LOCK) > 0) {
				use(p, LOCK);
				return -1;
			}
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.eye_guy.no_target")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return 0;
		}
		if (!use(p, LOCK)) {
			return -1;
		}
		in.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < Math.min(LOCK_MAX, in.size()); i++) {
			ids.add(in.get(i).getId());
		}
		LOCKED.put(p.getUUID(), ids);
		anim(p, ANIM_LOCK);
		setTimer(p, LOCK_AT, p.level().getGameTime());
		setFlag(p, F_LOCK, true);
		lockPulse(p, level(p), 0);
		OmnitrixSfx.play(level(p), p.position(), SND_SIGHT, 0.7f, 1.3f);
		return ids.size();
	}

	private void lockTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, LOCK_AT);
		if (age >= LOCK_TICKS) {
			setFlag(p, F_LOCK, false);
			setTimer(p, LOCK_AT, 0L);
			LOCKED.remove(p.getUUID());
			return;
		}
		if (age > 0 && age % 5 == 0) {
			lockPulse(p, l, age);
		}
	}

	/** The lock's beams every 5 ticks (they fade in 8), its damage every 10. */
	private void lockPulse(ServerPlayer p, ServerLevel l, long age) {
		List<Integer> ids = LOCKED.get(p.getUUID());
		if (ids == null) {
			return;
		}
		int eye = 0;
		for (Integer id : ids) {
			if (!(l.getEntity(id) instanceof LivingEntity t) || !t.isAlive()) {
				eye++;
				continue;
			}
			Vec3 c = centre(t);
			beam(p, "eye_guy_volley", c, LASER, 8, 0.05f, eye++ % VOLLEY_EYES);
			if (age % LOCK_EVERY == 0) {
				zap(p, t, LOCK_DAMAGE, false);
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LOCK_EVERY + 10, 1), p);
				l.sendParticles(LASER_DUST, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0.03);
			}
		}
		if (age % LOCK_EVERY == 0) {
			OmnitrixSfx.playOn(p, SND_LASER, 0.35f, 1.6f, SoundSource.PLAYERS);
		}
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+V Weak Spot

	/** Shift+V: the creature he looks at (32) is marked for 8 s -- it takes 30% more from him, and he sees it through anything. */
	public boolean weakSpot(ServerPlayer p) {
		LivingEntity t = lookTarget(p, WEAK_RANGE);
		if (t == null) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.eye_guy.no_target")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, WEAK)) {
			return false;
		}
		anim(p, ANIM_WEAK);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(WEAK_ID), (long) t.getId() + 1);
		n.timers.put(key(WEAK_UNTIL), p.level().getGameTime() + WEAK_TICKS);
		Omnitrix.save(p, n);
		Vec3 c = centre(t);
		beam(p, "eye_guy_beam", c, FLASH, 6, 0.05f, 0);
		level(p).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.2, 0.3, 0.2, 0.2);
		QuirkFx.ring(level(p), c).flags(QuirkFx.F_VERTICAL).vec(c.subtract(p.getEyePosition())).colours(0xFFFFFF, FIRE).size(0.9f).thickness(0.12f).life(10).spawn();
		OmnitrixSfx.playOn(p, SND_SIGHT, 0.6f, 1.6f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+C Hypno Gaze

	/** Shift+C: he stares down the creature he looks at -- for 5 s it forgets him and attacks the nearest other mob (never a boss). */
	public boolean hypnoGaze(ServerPlayer p) {
		LivingEntity t = lookTarget(p, HYPNO_RANGE);
		if (t == null || !com.projecthero.mod.omnitrix.alien.AlienConfusion.turnable(t)) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable(t == null ? "message.projecthero.omnitrix.eye_guy.no_target"
					: "message.projecthero.omnitrix.eye_guy.too_strong").withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, HYPNO)) {
			return false;
		}
		anim(p, ANIM_HYPNO);
		com.projecthero.mod.omnitrix.alien.AlienConfusion.turn(p, t, HYPNO_TICKS);
		Vec3 c = centre(t);
		for (int i = 0; i < VOLLEY_EYES; i++) {
			beam(p, "eye_guy_volley", c, 0xC070FF, 8, 0.04f, i);
		}
		level(p).sendParticles(ParticleTypes.WITCH, c.x, c.y + 0.4, c.z, 20, 0.3, 0.4, 0.3, 0.05);
		QuirkFx.ring(level(p), c).flags(QuirkFx.F_VERTICAL).vec(c.subtract(p.getEyePosition())).colours(0xFFFFFF, 0xC070FF).size(1.2f).thickness(0.15f).life(12)
				.spawn();
		OmnitrixSfx.playOn(p, SND_SIGHT, 0.7f, 0.8f, SoundSource.PLAYERS);
		return true;
	}
}
