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
 * </pre>
 */
public final class EyeGuyKit extends AlienKit {
	public static final String ID = "eye_guy";
	/** The beams' colours (the client tints his glowing irises with the last one fired). */
	public static final int LASER = 0x6CFF3A, ICE = 0x8FE0FF, FIRE = 0xFF8A1E, MEGA = 0xE8FF6A, FLASH = 0xFFFFE0;
	public static final DustParticleOptions LASER_DUST = new DustParticleOptions(new org.joml.Vector3f(0.45f, 1f, 0.25f), 0.6f);
	public static final DustParticleOptions ICE_DUST = new DustParticleOptions(new org.joml.Vector3f(0.6f, 0.9f, 1f), 0.7f);

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_SIGHT = 1, F_MEGA = 2, F_DODGE = 4;
	public static final int ANIM_LASER = 1, ANIM_VOLLEY = 2, ANIM_FREEZE = 3, ANIM_FIRE = 4, ANIM_DODGE = 5, ANIM_MEGA_CHARGE = 6, ANIM_MEGA = 7, ANIM_FLASH = 8,
			ANIM_SIGHT = 9, ANIM_PUNCH = 10;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.eye_guy.<id>)
	public static final String LASER_ID = "eye_laser", VOLLEY = "laser_volley", FREEZE = "freeze_beam", FIRE_ID = "fire_beam", DODGE = "eye_dodge",
			MEGA_ID = "chest_eye_beam", FLASH_ID = "eye_flash", SIGHT = "all_seeing", OPTICOID = "opticoid", MANY_EYES = "many_eyes", SOFT = "soft_body",
			DAZZLED = "dazzled";

	public static final String SIGHT_UNTIL = "sight_until", MEGA_AT = "mega_at", DODGE_UNTIL = "dodge_until";

	// ---------------------------------------------------------------- numbers (basic 5-8, specials 12-17, big 17-20, ultimates 21-36)
	public static final float LASER_DAMAGE = 7f, VOLLEY_DAMAGE = 6f, FREEZE_DAMAGE = 12f, FIRE_DAMAGE = 12f, MEGA_DAMAGE = 32f, FLASH_DAMAGE = 5f;
	public static final float BEHIND_TAKEN = 0.75f, DAZZLED_DEALT = 0.5f;
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

	private EyeGuyKit() {
	}

	public static void register() {
		Aliens.register(new EyeGuyKit());
	}

	public static void clearSessionState() {
		LAST_TICK.clear();
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
		return List.of(AlienMove.tap(LASER_ID, AbilitySlot.SLOT_1, 10), AlienMove.shift(VOLLEY, AbilitySlot.SLOT_1, 80),
				AlienMove.tap(FREEZE, AbilitySlot.SLOT_2, 120), AlienMove.shift(FIRE_ID, AbilitySlot.SLOT_2, 120),
				AlienMove.tap(DODGE, AbilitySlot.SLOT_3, 30),
				AlienMove.tap(MEGA_ID, AbilitySlot.SLOT_4, 700),
				AlienMove.tap(SIGHT, AbilitySlot.SLOT_5, 500),
				AlienMove.tap(FLASH_ID, AbilitySlot.SLOT_6, 260),
				AlienMove.vanilla(OPTICOID, "Passive", 0), AlienMove.vanilla(MANY_EYES, "Passive", 0), AlienMove.vanilla(SOFT, "Weakness", 0),
				AlienMove.vanilla(DAZZLED, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_4 -> flag(p, F_MEGA);
			case SLOT_5 -> flag(p, F_SIGHT);
			default -> false;
		};
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
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { SIGHT_UNTIL, MEGA_AT, DODGE_UNTIL }) {
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
			case SLOT_3 -> dodge(p);
			case SLOT_4 -> chestBeam(p);
			case SLOT_5 -> allSeeing(p);
			case SLOT_6 -> flash(p);
			default -> {
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		anim(p, ANIM_PUNCH);
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
			a.target.invulnerableTime = 0;
			hurt(p, a.target, dealt(p, LASER_DAMAGE), false);
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
			t.invulnerableTime = 0;
			hurt(p, t, dealt(p, VOLLEY_DAMAGE), false);
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
			a.target.invulnerableTime = 0;
			hurt(p, a.target, dealt(p, FREEZE_DAMAGE), false);
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
			a.target.invulnerableTime = 0;
			hurt(p, a.target, dealt(p, FIRE_DAMAGE), true);
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
			e.invulnerableTime = 0;
			if (hurt(p, e, dealt(p, MEGA_DAMAGE), false)) {
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
			e.invulnerableTime = 0;
			hurt(p, e, dealt(p, FLASH_DAMAGE), false);
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
}
