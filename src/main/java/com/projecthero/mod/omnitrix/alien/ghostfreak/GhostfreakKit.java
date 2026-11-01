package com.projecthero.mod.omnitrix.alien.ghostfreak;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Ghostfreak, the Ectonurite -- a pale grey-white phantom striped with black, one purple eye, floating on a wispy tail
 * instead of legs. He drifts down slowly (low gravity), physical blows do half, he never drowns, and the sun burns him a little
 * (the comics' weakness).
 *
 * <p>v0.17.2 revamp (user: "ghost freaks abilities arent very good ... dont give him a flight ability since i want him to be able
 * fly by double tapping space bar"):
 * <pre>
 *   R        Spectral Claw    a three-swipe combo (10, 10, then the Reaping   Shift+R  Wail            a shriek cone: 8, blown back, and
 *                             Rake: 18 in a wide arc, withers, knocks back)                             what hears it flees in terror
 *   G        Possess          dive INTO a mob: ride inside it, untouchable,   Shift+G  Soul Siphon     a 2 s drain tether: 2.5 every 5
 *                             while it fights for you; G again (or sneak, or                            ticks (20), healing 1 each pulse
 *                             any other move, or 15 s) bursts you out of it (14)
 *   X        Phase            intangible up to 5 s; what you pass through     Shift+X  Ecto Rush       an intangible dash through walls and
 *                             is chilled (4, slowed)                                                   everything in the way (10, weakens)
 *   Z        Chest Tentacles  his chest opens: up to 3 creatures in front are Shift+Z  Haunt           ultimate: everything within 10 takes 8,
 *                             yanked in and constricted (6, then 3 x4)                                 then 3 every 0.5 s for 3 s (26)
 *   V        Spirit Sight     toggle: see in the dark, every living thing within 32 outlined through walls (yours only)
 *   C        Fright Aura      10 s: everything near panics, weakened, 2/s     Shift+C  Fade            invisible for 10 s; the first blow out
 *                                                                                                       of it is an Ambush (+10, blinds)
 *   Attack   Chilling Touch (+2, slows)   Use (empty hand)  a spectral bolt (5)   Double-tap jump  fly (no key: always ready)
 * </pre>
 */
public final class GhostfreakKit extends AlienKit {
	public static final String ID = "ghostfreak";
	public static final int COLOUR = 0xC8D2D2;
	public static final int PURPLE = 0x9A3CFF;

	public static final int ANIM_CLAW = 1;
	public static final int ANIM_SCREAM = 2;
	public static final int ANIM_POSSESS = 3;
	public static final int ANIM_SIPHON = 4;
	public static final int ANIM_PHASE = 5;
	public static final int ANIM_FLIGHT = 6;
	public static final int ANIM_FADE = 7;
	/** v0.17.2: was the ground's Shadow Tendrils; now his chest's tentacles. */
	public static final int ANIM_TENTACLES = 8;
	public static final int ANIM_AURA = 9;
	public static final int ANIM_HAUNT = 10;
	public static final int ANIM_TOUCH = 11;
	public static final int ANIM_BOLT = 12;
	public static final int ANIM_RAKE = 14;
	public static final int ANIM_RUSH = 15;
	public static final int ANIM_BURST = 16;
	public static final int ANIM_CLAW_BACK = 17;

	public static final int F_PHASE = 1;
	public static final int F_INVIS = 2;
	/** Flying right now (a double-tap of jump). */
	public static final int F_FLY = 4;
	public static final int F_AURA = 8;
	/** Inside a possessed mob. */
	public static final int F_POSSESS = 16;
	public static final int F_RUSH = 64;

	public static final String CLAW = "spectral_claw";
	public static final String SCREAM = "scream";
	public static final String POSSESS = "possess";
	public static final String SIPHON = "soul_siphon";
	public static final String PHASE = "phase";
	public static final String RUSH = "ecto_rush";
	public static final String FLIGHT = "ghost_flight";
	public static final String FADE = "fade";
	public static final String TENTACLES = "chest_tentacles";
	public static final String AURA = "fright_aura";
	public static final String HAUNT = "haunt";
	public static final String TOUCH = "chilling_touch";
	public static final String BOLT = "spectral_bolt";

	/** The claw combo: two swipes of {@link #CLAW_DAMAGE}, then the Reaping Rake. A press within {@link #COMBO_WINDOW} carries it on. */
	public static final float CLAW_DAMAGE = 10f;
	public static final float RAKE_DAMAGE = 18f;
	public static final int CLAW_GAP = 8;
	public static final int RAKE_COOLDOWN = 40;
	public static final int COMBO_WINDOW = 30;
	public static final float SCREAM_DAMAGE = 8f;
	public static final int FEAR_TICKS = 100;
	/** Possess: up to 15 s inside; the real cooldown starts when he bursts out. */
	public static final int POSSESS_TICKS = 300;
	public static final int POSSESS_COOLDOWN = 300;
	public static final float BURST_DAMAGE = 14f;
	public static final float BURST_SPLASH = 6f;
	/** Soul Siphon: {@link #SIPHON_PULSE} every 5 ticks for {@link #SIPHON_TICKS} (8 pulses), healing {@link #SIPHON_HEAL} each. */
	public static final int SIPHON_TICKS = 40;
	public static final float SIPHON_PULSE = 2.5f;
	public static final float SIPHON_HEAL = 1f;
	public static final double SIPHON_RANGE = 12;
	/**
	 * Haunt is his ultimate: {@link #HAUNT_HIT} to everything within {@link #HAUNT_RADIUS}, then {@link #HAUNT_TICK} every 10
	 * ticks for {@link #HAUNT_TICKS} on each one it caught (8 + 18 = 26).
	 */
	public static final float HAUNT_HIT = 8f;
	public static final float HAUNT_TICK = 3f;
	public static final int HAUNT_TICKS = 60;
	public static final double HAUNT_RADIUS = 10;
	public static final int HAUNT_COOLDOWN = 600;
	/** Phase: how long it can last, and the cooldown that starts when it ends. */
	public static final int PHASE_TICKS = 100;
	public static final int PHASE_COOLDOWN = 200;
	public static final float PHASE_CHILL = 4f;
	/** Ecto Rush: {@link #RUSH_TICKS} of {@link #RUSH_SPEED} blocks a tick (about 11 blocks), through walls and bodies. */
	public static final int RUSH_TICKS = 6;
	public static final double RUSH_SPEED = 1.8;
	public static final float RUSH_DAMAGE = 10f;
	public static final int RUSH_COOLDOWN = 120;
	public static final int FADE_TICKS = 200;
	public static final float AMBUSH_DAMAGE = 10f;
	/** Chest Tentacles: up to {@link #TENTACLE_MAX} grabbed within {@link #TENTACLE_RANGE}, held {@link #TENTACLE_TICKS}. */
	public static final int TENTACLE_MAX = 3;
	public static final double TENTACLE_RANGE = 8;
	public static final int TENTACLE_TICKS = 40;
	public static final float TENTACLE_HIT = 6f;
	public static final float TENTACLE_TICK = 3f;
	public static final int AURA_TICKS = 200;
	public static final float AURA_DREAD = 2f;
	public static final float TOUCH_DAMAGE = 2f;
	public static final float BOLT_DAMAGE = 5f;
	public static final float PHYSICAL_FACTOR = 0.5f;
	public static final float SUN_DAMAGE = 1f;
	public static final float FLY_SPEED = 0.035f;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A claw swipe with a ghostly breath behind it. */
	public static final OmnitrixSfx.Cue SND_CLAW = OmnitrixSfx.cue("ghostfreak_claw")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 1.1f)
			.layer(SoundEvents.PHANTOM_BITE, 0.3f, 1.2f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.25f, 1.0f);
	/** The scream: a low moan and a rushing breath (no shriek). */
	public static final OmnitrixSfx.Cue SND_SCREAM = OmnitrixSfx.cue("ghostfreak_scream")
			.layer(SoundEvents.PHANTOM_AMBIENT, 0.45f, 0.6f)
			.layer(SoundEvents.VEX_CHARGE, 0.3f, 0.7f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.5f, 0.6f)
			.gap(10);
	/** Phasing: a breathy ghostly whoosh. */
	public static final OmnitrixSfx.Cue SND_PHASE = OmnitrixSfx.cue("ghostfreak_phase")
			.layer(SoundEvents.SOUL_ESCAPE, 0.5f, 0.8f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.25f, 0.6f)
			.layer(SoundEvents.BREEZE_INHALE, 0.2f, 0.6f);
	/** The laugh: a low, slowed moan with a whisper on it. */
	public static final OmnitrixSfx.Cue SND_LAUGH = OmnitrixSfx.cue("ghostfreak_laugh")
			.layer(SoundEvents.PHANTOM_AMBIENT, 0.4f, 0.55f)
			.layer(SoundEvents.SOUL_ESCAPE, 0.4f, 0.6f)
			.layer(SoundEvents.VEX_AMBIENT, 0.22f, 0.5f, 3)
			.gap(10);
	/** A tendril lashing out: a wet whoosh. */
	public static final OmnitrixSfx.Cue SND_TENDRIL = OmnitrixSfx.cue("ghostfreak_tendril")
			.layer(SoundEvents.PHANTOM_FLAP, 0.3f, 0.7f)
			.layer(SoundEvents.VEX_CHARGE, 0.22f, 0.6f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.15f, 0.6f)
			.gap(3);

	static final DustParticleOptions WISP = new DustParticleOptions(new Vector3f(0.85f, 0.88f, 0.9f), 1.0f);
	static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.25f, 1.0f), 0.9f);

	/** player -> the mob he is inside (and in which dimension). */
	private static final Map<UUID, Possession> POSSESSED = new ConcurrentHashMap<>();
	/** player -> creatures fleeing from him -> until when. */
	private static final Map<UUID, Map<Integer, Long>> FEARED = new ConcurrentHashMap<>();
	/** player -> the creatures his chest tentacles hold. */
	private static final Map<UUID, Grip> GRIPS = new ConcurrentHashMap<>();
	/** player -> the creature his Soul Siphon drinks from. */
	private static final Map<UUID, Drain> DRAINS = new ConcurrentHashMap<>();
	/** player -> the creatures one Phase (or one Ecto Rush) already went through. */
	private static final Map<UUID, Set<Integer>> PASSED = new ConcurrentHashMap<>();
	/** player -> his Ecto Rush heading. */
	private static final Map<UUID, Vec3> RUSHES = new ConcurrentHashMap<>();
	/** player -> the claw combo's next step (0, 1, 2 = the Rake). */
	private static final Map<UUID, Integer> COMBO = new ConcurrentHashMap<>();
	/** player -> the creatures his Haunt caught (the dread keeps biting for {@link #HAUNT_TICKS}). */
	private static final Map<UUID, Haunted> HAUNTED = new ConcurrentHashMap<>();

	private record Possession(ResourceKey<Level> dim, int mobId) {
	}

	private record Grip(ResourceKey<Level> dim, List<Integer> held) {
	}

	private record Drain(ResourceKey<Level> dim, int targetId) {
	}

	private record Haunted(ResourceKey<Level> dim, List<Integer> victims) {
	}

	private GhostfreakKit() {
	}

	public static void register() {
		Aliens.register(new GhostfreakKit());
		AlienProjectileEntity.trail("ghostfreak_bolt", VIOLET);
		AlienProjectileEntity.onHit("ghostfreak_bolt", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				l.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
				if (hit instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
					le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
				}
			}
		});
	}

	public static void clearSessionState() {
		POSSESSED.clear();
		FEARED.clear();
		GRIPS.clear();
		DRAINS.clear();
		PASSED.clear();
		RUSHES.clear();
		COMBO.clear();
		HAUNTED.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 revamp: was 24 health, +2 fist
		return AlienStats.builder().health(26f).speed(1.0f).armor(2f, 0f).attack(3f).gravity(0.25f).safeFall(30f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(CLAW, AbilitySlot.SLOT_1, CLAW_GAP), AlienMove.shift(SCREAM, AbilitySlot.SLOT_1, 240),
				AlienMove.tap(POSSESS, AbilitySlot.SLOT_2, POSSESS_COOLDOWN), AlienMove.shift(SIPHON, AbilitySlot.SLOT_2, 200),
				AlienMove.tap(PHASE, AbilitySlot.SLOT_3, PHASE_COOLDOWN), AlienMove.shift(RUSH, AbilitySlot.SLOT_3, RUSH_COOLDOWN),
				AlienMove.tap(TENTACLES, AbilitySlot.SLOT_4, 220), AlienMove.shift(HAUNT, AbilitySlot.SLOT_4, HAUNT_COOLDOWN),
				AlienMove.tap(SIGHT, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(AURA, AbilitySlot.SLOT_6, 600), AlienMove.shift(FADE, AbilitySlot.SLOT_6, 400),
				AlienMove.vanilla(TOUCH, "Attack", 0), AlienMove.vanilla(BOLT, "Use (empty hand)", 12),
				AlienMove.vanilla(FLIGHT, "Double-tap jump", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		// v0.17.2 (user: "i want him to be able fly by double tapping space bar"): always ready to fly, no key
		p.getAbilities().mayfly = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.onUpdateAbilities();
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 1.0f);
		l.sendParticles(WISP, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		endPhase(p, false);
		endRush(p);
		endFade(p);
		endPossession(p, false);
		setFlag(p, F_AURA, false);
		setTimer(p, "aura_end", 0);
		FEARED.remove(p.getUUID());
		GRIPS.remove(p.getUUID());
		setTimer(p, "tentacles_end", 0);
		DRAINS.remove(p.getUUID());
		setTimer(p, "siphon_end", 0);
		COMBO.remove(p.getUUID());
		HAUNTED.remove(p.getUUID());
		setTimer(p, "haunt_end", 0);
		setFlag(p, F_FLY, false);
		net.minecraft.world.level.GameType mode = p.gameMode.getGameModeForPlayer();
		if (mode != net.minecraft.world.level.GameType.CREATIVE && mode != net.minecraft.world.level.GameType.SPECTATOR) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
	}

	/** Phase or Ecto Rush is on: horizontal movement ignores blocks (the framework's collision mixin). */
	@Override
	public boolean phasing(Player p) {
		return flag(p, F_PHASE) || flag(p, F_RUSH);
	}

	/** Any other move bursts him out of the mob he is inside (G itself is the clean way out). */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!POSSESS.equals(moveId) && POSSESSED.containsKey(p.getUUID())) {
			endPossession(p, true);
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		p.setAirSupply(p.getMaxAirSupply()); // nothing to breathe
		// flight: always ready (a double-tap of jump), slow and floaty
		if (!p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.getAbilities().setFlyingSpeed(FLY_SPEED);
			p.onUpdateAbilities();
		}
		boolean flying = p.getAbilities().flying;
		if (flying != flag(p, F_FLY)) {
			setFlag(p, F_FLY, flying);
			if (flying) {
				anim(p, ANIM_FLIGHT);
				l.sendParticles(WISP, p.getX(), p.getY() + 0.3, p.getZ(), 16, 0.4, 0.2, 0.4, 0.0);
				OmnitrixSfx.playOn(p, SND_PHASE, 0.5f, 1.4f, net.minecraft.sounds.SoundSource.PLAYERS);
			}
		}
		if (now % 4 == 0 && !flag(p, F_POSSESS)) {
			l.sendParticles(WISP, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
		}
		// the sun burns an Ectonurite a little (not while he hides inside a host)
		if (now % 40 == 0 && !flag(p, F_POSSESS) && inSunlight(p)) {
			p.hurt(p.damageSources().inFire(), SUN_DAMAGE);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
			if (now % 200 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.sun").withStyle(ChatFormatting.GOLD), true);
			}
		}
		// Phase
		if (flag(p, F_PHASE)) {
			if (now >= timer(p, "phase_end")) {
				endPhase(p, true);
			} else {
				passThrough(p, l, PHASE_CHILL, false);
				if (now % 3 == 0) {
					l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 1, 0.3, 0.6, 0.3, 0.01);
				}
			}
		}
		tickRush(p, l, now);
		// Fade
		if (flag(p, F_INVIS)) {
			if (now >= timer(p, "fade_end")) {
				endFade(p);
			} else if (now % 20 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
				loseTrackOfMe(p, 24);
			}
		}
		if (COMBO.containsKey(p.getUUID()) && now > timer(p, "combo_until")) {
			COMBO.remove(p.getUUID());
		}
		tickPossession(p, l, now);
		tickFear(p, l, now);
		tickTentacles(p, l, now);
		tickSiphon(p, l, now);
		tickHaunt(p, l, now);
		// Fright Aura
		if (flag(p, F_AURA)) {
			if (now >= timer(p, "aura_end")) {
				setFlag(p, F_AURA, false);
				setTimer(p, "aura_end", 0);
			} else {
				if (now % 20 == 0) {
					for (LivingEntity e : around(p, p.position(), 7)) {
						frighten(p, e, 40);
						e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
						e.invulnerableTime = 0;
						hurt(p, e, AURA_DREAD, false); // v0.17.2: the dread itself hurts
					}
				}
				if (now % 2 == 0) {
					double a = now * 0.3;
					l.sendParticles(VIOLET, p.getX() + Math.cos(a) * 1.6, p.getY() + 0.6, p.getZ() + Math.sin(a) * 1.6, 1, 0, 0.1, 0, 0);
					l.sendParticles(VIOLET, p.getX() - Math.cos(a) * 1.6, p.getY() + 1.4, p.getZ() - Math.sin(a) * 1.6, 1, 0, 0.1, 0, 0);
				}
			}
		}
	}

	public static boolean inSunlight(ServerPlayer p) {
		Level l = p.level();
		if (!l.dimensionType().hasSkyLight() || !l.isDay() || p.isInWaterRainOrBubble()) {
			return false;
		}
		BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
		return l.canSeeSky(head) && l.getBrightness(net.minecraft.world.level.LightLayer.SKY, head) >= 12;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					scream(p);
				} else {
					claw(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					siphon(p, lookTarget(p, SIPHON_RANGE));
				} else if (POSSESSED.containsKey(p.getUUID())) {
					endPossession(p, true); // G again: burst out
				} else {
					possess(p, lookTarget(p, 12));
				}
			}
			case SLOT_3 -> {
				if (shift) {
					ectoRush(p);
				} else if (flag(p, F_PHASE)) {
					endPhase(p, true);
				} else {
					phase(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					haunt(p);
				} else {
					tentacles(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					fade(p);
				} else {
					frightAura(p);
				}
			}
			default -> {
			}
		}
	}

	/** Living creatures in a cone of {@code deg} degrees, {@code range} blocks in front. */
	public static List<LivingEntity> cone(ServerPlayer p, double range, double deg) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		double cos = Math.cos(Math.toRadians(deg / 2));
		for (LivingEntity e : around(p, eye.add(look.scale(range / 2)), range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(look) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	private static boolean boss(ServerPlayer p, LivingEntity e, float threat) {
		return QuirkThreat.add(e, p, threat) || e.getMaxHealth() >= 150f || e instanceof EnderDragon || e instanceof WitherBoss;
	}

	// ---------------------------------------------------------------- R: the claw combo / Shift+R: Wail

	/** The step the next R press will be (0, 1, or 2 = the Reaping Rake). */
	public static int comboStep(ServerPlayer p) {
		return COMBO.getOrDefault(p.getUUID(), 0);
	}

	/** R: one swipe of the combo -- two quick rakes, then the Reaping Rake (wide, heavy, withering, knocks back). */
	public int claw(ServerPlayer p) {
		if (!use(p, CLAW)) {
			return 0;
		}
		int step = comboStep(p);
		boolean rake = step >= 2;
		long now = p.level().getGameTime();
		if (rake) {
			COMBO.remove(p.getUUID());
			setTimer(p, "combo_until", 0);
			cooldown(p, CLAW, RAKE_COOLDOWN);
		} else {
			COMBO.put(p.getUUID(), step + 1);
			setTimer(p, "combo_until", now + COMBO_WINDOW);
		}
		anim(p, rake ? ANIM_RAKE : step == 0 ? ANIM_CLAW : ANIM_CLAW_BACK);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : cone(p, rake ? 4.5 : 3.5, rake ? 130 : 80)) {
			e.invulnerableTime = 0;
			hurt(p, e, rake ? RAKE_DAMAGE : CLAW_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, rake ? 60 : 40, rake ? 1 : 0));
			if (rake) {
				shove(e, p.position(), 1.1, 0.35);
			}
			l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), rake ? 12 : 6, 0.2, 0.3, 0.2, 0.02);
			n++;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		// each swipe from the other side; the Rake is a wide, low sweep of five
		double dir = step == 1 ? -1 : 1;
		int lines = rake ? 2 : 1;
		for (int k = -lines; k <= lines; k++) {
			double w = rake ? 2.2 : 1.4;
			Vec3 a = eye.add(look.scale(1.0)).add(side.scale(w * dir)).add(0, (rake ? 0.1 : 0.5) + k * 0.22, 0);
			Vec3 b = eye.add(look.scale(rake ? 3.4 : 2.6)).add(side.scale(-w * dir)).add(0, (rake ? -0.2 : -0.6) + k * 0.22, 0);
			QuirkFx.ribbon(l, a, b).colours(0xFFFFFF, PURPLE).size(rake ? 0.26f : 0.18f).life(rake ? 10 : 7).style(QuirkFx.RIBBON_LIGHT).spawn();
		}
		if (rake) {
			QuirkFx.ring(l, eye.add(look.scale(2.0))).colours(0xFFFFFF, PURPLE).size(2.4f).thickness(0.25f).life(9).vec(look)
					.flags(QuirkFx.F_VERTICAL).spawn();
			QuirkCamera.shakeAround(l, p.position(), 6, 0.2f, 6);
		}
		OmnitrixSounds.play(l, p.position(), SND_CLAW, rake ? 1.3f : 1.0f, rake ? 0.7f : 0.9f + p.getRandom().nextFloat() * 0.2f);
		return n;
	}

	public int scream(ServerPlayer p) {
		if (!use(p, SCREAM)) {
			return 0;
		}
		anim(p, ANIM_SCREAM);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : cone(p, 10.0, 60)) {
			e.invulnerableTime = 0;
			hurt(p, e, SCREAM_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FEAR_TICKS, 1));
			shove(e, p.position(), 0.9, 0.25); // v0.17.2: the wail blows them back
			frighten(p, e, FEAR_TICKS);
			n++;
		}
		QuirkFx.cone(l, p.getEyePosition().add(0, -0.1, 0), p.getLookAngle()).colours(0xFFFFFF, PURPLE).size(3.2f).length(10f).life(14)
				.style(QuirkFx.CONE_DEBRIS).spawn();
		for (int i = 1; i <= 3; i++) {
			QuirkFx.ring(l, p.getEyePosition().add(p.getLookAngle().scale(i * 2.5))).colours(0xFFFFFF, PURPLE).size(0.8f + i * 0.7f).thickness(0.2f).life(10)
					.vec(p.getLookAngle()).flags(QuirkFx.F_VERTICAL).spawn();
		}
		QuirkCamera.shakeAround(l, p.position(), 10, 0.25f, 10);
		OmnitrixSounds.play(l, p.position(), SND_SCREAM, 1.3f, 1.0f);
		return n;
	}

	/** Makes {@code e} run from him for {@code ticks} (bosses only note the threat; players are just weakened). */
	public void frighten(ServerPlayer p, LivingEntity e, int ticks) {
		if (boss(p, e, 4f) || !(e instanceof PathfinderMob mob)) {
			return;
		}
		Map<Integer, Long> mine = FEARED.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
		mine.merge(mob.getId(), p.level().getGameTime() + ticks, Math::max);
		flee(mob, p.position());
	}

	private static void flee(PathfinderMob mob, Vec3 from) {
		mob.setTarget(null);
		Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from);
		if (away != null) {
			mob.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
		}
	}

	/** Is {@code mob} fleeing from {@code p} right now? */
	public static boolean fleeing(ServerPlayer p, LivingEntity mob) {
		Map<Integer, Long> mine = FEARED.get(p.getUUID());
		Long until = mine == null ? null : mine.get(mob.getId());
		return until != null && until > p.level().getGameTime();
	}

	private void tickFear(ServerPlayer p, ServerLevel l, long now) {
		Map<Integer, Long> mine = FEARED.get(p.getUUID());
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
			l.sendParticles(VIOLET, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 1, 0.1, 0.05, 0.1, 0);
			return false;
		});
	}

	// ---------------------------------------------------------------- G: Possess (inside the host) / Shift+G: Soul Siphon

	/**
	 * Client-safe: is {@code e} a Ghostfreak riding inside a possessed host right now? The seat ({@code OmnitrixRiding#seat})
	 * sinks him into its body with his eyes at its eyes.
	 */
	public static boolean insideHost(@Nullable Entity e) {
		return e instanceof Player pl && ID.equals(Omnitrix.active(pl)) && (Omnitrix.state(pl).kitFlags & F_POSSESS) != 0;
	}

	/**
	 * G: dives into {@code target} (a mob, not a boss, not a player) and rides inside it -- invisible and untouchable -- while it
	 * fights for him: whatever he looks at, or the nearest hostile. G again, sneaking, any other move, its death or 15 s ends it;
	 * every end but its death and turning back bursts him out through it ({@link #BURST_DAMAGE}, {@link #BURST_SPLASH} around).
	 */
	public boolean possess(ServerPlayer p, LivingEntity target) {
		if (!(target instanceof Mob mob) || target instanceof Player) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (boss(p, mob, 6f)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.too_strong").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (POSSESSED.containsKey(p.getUUID()) || !use(p, POSSESS)) {
			return false;
		}
		endPhase(p, false);
		endRush(p);
		anim(p, ANIM_POSSESS);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, p.position().add(0, 1.2, 0), mob.position().add(0, mob.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, PURPLE).size(0.45f).life(12)
				.style(QuirkFx.RIBBON_SHADOW).spawn();
		if (p.isPassenger()) {
			p.stopRiding();
		}
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		POSSESSED.put(p.getUUID(), new Possession(p.level().dimension(), mob.getId()));
		setTimer(p, "possess_end", p.level().getGameTime() + POSSESS_TICKS);
		setFlag(p, F_POSSESS, true);
		cooldown(p, POSSESS, POSSESS_TICKS); // the real cooldown starts when he leaves it
		p.startRiding(mob, true);
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(mob));
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		mob.setTarget(null);
		mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, POSSESS_TICKS, 1));
		mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, POSSESS_TICKS, 0));
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, POSSESS_TICKS, 0));
		l.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 24, 0.3, 0.5, 0.3, 0.03);
		l.sendParticles(VIOLET, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.0);
		OmnitrixSounds.play(l, mob.position(), SND_LAUGH, 1.0f, 0.8f);
		return true;
	}

	@Nullable
	public static Mob possessed(ServerPlayer p) {
		Possession ps = POSSESSED.get(p.getUUID());
		if (ps == null || ps.dim() != p.level().dimension()) {
			return null;
		}
		return p.serverLevel().getEntity(ps.mobId()) instanceof Mob m && m.isAlive() ? m : null;
	}

	private void tickPossession(ServerPlayer p, ServerLevel l, long now) {
		if (!POSSESSED.containsKey(p.getUUID())) {
			return;
		}
		Mob mob = possessed(p);
		if (mob == null) {
			endPossession(p, false); // the host died: he simply drifts out
			return;
		}
		if (now >= timer(p, "possess_end") || p.getVehicle() != mob) {
			endPossession(p, true); // time, or he sneaked out: he bursts out through it
			return;
		}
		if (now % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		}
		// it fights for him: his crosshair's target, else the nearest hostile near it (never him, never his squad)
		LivingEntity want = lookTarget(p, 24);
		if (want == mob) {
			want = null;
		}
		if (want == null || !want.isAlive()) {
			LivingEntity cur = mob.getTarget();
			if (cur == null || cur == p || !cur.isAlive()) {
				want = null;
				double best = 16 * 16;
				for (LivingEntity e : around(p, mob.position(), 16)) {
					if (e != mob && e instanceof Enemy && e.distanceToSqr(mob) < best) {
						best = e.distanceToSqr(mob);
						want = e;
					}
				}
			} else {
				want = cur;
			}
		}
		if (want != mob.getTarget()) {
			mob.setTarget(want);
		}
		if (now % 3 == 0) {
			l.sendParticles(VIOLET, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 1, mob.getBbWidth() * 0.4, 0.3, mob.getBbWidth() * 0.4, 0);
		}
	}

	/** Out of the host: a {@code burst} tears through it (and splashes around it); otherwise he slips out quietly. */
	public void endPossession(ServerPlayer p, boolean burst) {
		Possession ps = POSSESSED.remove(p.getUUID());
		if (ps == null) {
			return;
		}
		setFlag(p, F_POSSESS, false);
		setTimer(p, "possess_end", 0);
		if (cooldownLeft(p, POSSESS) < POSSESS_COOLDOWN) {
			cooldown(p, POSSESS, POSSESS_COOLDOWN);
		}
		Entity vehicle = p.getVehicle();
		if (vehicle != null && vehicle.getId() == ps.mobId()) {
			p.stopRiding();
		}
		if (!flag(p, F_INVIS)) {
			MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
			if (inv != null && inv.getDuration() <= 40) {
				p.removeEffect(MobEffects.INVISIBILITY);
			}
		}
		if (!(p.server.getLevel(ps.dim()) instanceof ServerLevel l) || !(l.getEntity(ps.mobId()) instanceof Mob mob)) {
			return;
		}
		mob.setTarget(null);
		mob.removeEffect(MobEffects.GLOWING);
		mob.removeEffect(MobEffects.DAMAGE_BOOST);
		mob.removeEffect(MobEffects.MOVEMENT_SPEED);
		l.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
		if (!burst || !mob.isAlive()) {
			return;
		}
		anim(p, ANIM_BURST);
		Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
		mob.invulnerableTime = 0;
		mob.hurt(OmnitrixDamage.alienStrike(p), BURST_DAMAGE); // AlienKit.hurt skips riding partners; he has left it now anyway
		for (LivingEntity e : around(p, at, 3)) {
			if (e != mob) {
				e.invulnerableTime = 0;
				hurt(p, e, BURST_SPLASH, false);
				shove(e, at, 0.8, 0.3);
			}
		}
		Vec3 out = p.getLookAngle().multiply(1, 0, 1);
		p.push(out.x * 0.6, 0.45, out.z * 0.6);
		p.hurtMarked = true;
		QuirkFx.blast(l, at).colours(0xF0E8FF, PURPLE).size(2.2f).life(12).flags(QuirkFx.F_NO_SMOKE).spawn();
		l.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 30, 0.4, 0.5, 0.4, 0.08);
		l.sendParticles(VIOLET, at.x, at.y, at.z, 24, 0.5, 0.6, 0.5, 0.0);
		OmnitrixSounds.play(l, at, SND_SCREAM, 1.0f, 1.3f);
	}

	/** Shift+G: a drain tether on {@code t} for 2 s -- {@link #SIPHON_PULSE} every 5 ticks, healing {@link #SIPHON_HEAL} each. */
	public boolean siphon(ServerPlayer p, LivingEntity t) {
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, SIPHON)) {
			return false;
		}
		anim(p, ANIM_SIPHON);
		DRAINS.put(p.getUUID(), new Drain(p.level().dimension(), t.getId()));
		setTimer(p, "siphon_end", p.level().getGameTime() + SIPHON_TICKS);
		siphonPulse(p, level(p), t); // the first sip lands at once
		OmnitrixSounds.play(level(p), p.position(), SND_TENDRIL, 0.8f, 1.5f);
		return true;
	}

	private void siphonPulse(ServerPlayer p, ServerLevel l, LivingEntity t) {
		t.invulnerableTime = 0;
		if (hurt(p, t, SIPHON_PULSE, false)) {
			p.heal(SIPHON_HEAL);
		}
		QuirkFx.ribbon(l, t.position().add(0, t.getBbHeight() * 0.6, 0), p.position().add(0, 1.2, 0)).colours(0xFFFFFF, PURPLE).size(0.3f).life(6)
				.style(QuirkFx.RIBBON_SHADOW).spawn();
		Vec3 d = p.position().subtract(t.position()).add(0, 0.6, 0);
		for (int k = 0; k < 6; k++) {
			Vec3 at = t.position().add(d.scale(k / 6.0)).add(0, t.getBbHeight() * 0.5, 0);
			l.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private void tickSiphon(ServerPlayer p, ServerLevel l, long now) {
		Drain d = DRAINS.get(p.getUUID());
		if (d == null) {
			return;
		}
		long end = timer(p, "siphon_end");
		if (d.dim() != l.dimension() || now > end || !(l.getEntity(d.targetId()) instanceof LivingEntity t) || !t.isAlive()
				|| t.distanceToSqr(p) > SIPHON_RANGE * SIPHON_RANGE * 1.5) {
			DRAINS.remove(p.getUUID());
			setTimer(p, "siphon_end", 0);
			return;
		}
		if ((end - now) % 5 == 0 && now != end - SIPHON_TICKS) {
			siphonPulse(p, l, t);
		}
	}

	// ---------------------------------------------------------------- X: Phase / Shift+X: Ecto Rush / Shift+C: Fade

	public boolean phase(ServerPlayer p) {
		if (flag(p, F_PHASE) || !use(p, PHASE)) {
			return false;
		}
		anim(p, ANIM_PHASE);
		setFlag(p, F_PHASE, true);
		setTimer(p, "phase_end", p.level().getGameTime() + PHASE_TICKS);
		cooldown(p, PHASE, PHASE_TICKS); // the real cooldown starts when it ends
		PASSED.put(p.getUUID(), new HashSet<>());
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		OmnitrixSounds.play(l, p.position(), SND_PHASE, 1.0f, 1.0f);
		return true;
	}

	/** Phase ends (X again, its 5 s, turning back): solid again -- and pushed out of any wall he ended inside. */
	public void endPhase(ServerPlayer p, boolean cooldown) {
		if (!flag(p, F_PHASE)) {
			return;
		}
		setFlag(p, F_PHASE, false);
		setTimer(p, "phase_end", 0);
		if (!flag(p, F_RUSH)) {
			PASSED.remove(p.getUUID());
		}
		if (cooldown) {
			cooldown(p, PHASE, PHASE_COOLDOWN);
			OmnitrixSounds.play(level(p), p.position(), SND_PHASE, 0.7f, 0.7f);
		}
		com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.settleOutsideBlocks(p);
	}

	/** While intangible: each creature his body passes through takes {@code damage} once (and is slowed, or weakened by the rush). */
	private int passThrough(ServerPlayer p, ServerLevel l, float damage, boolean rush) {
		Set<Integer> done = PASSED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		net.minecraft.world.phys.AABB body = p.getBoundingBox().inflate(rush ? 0.8 : 0.3);
		int n = 0;
		for (LivingEntity e : around(p, p.position(), 3)) {
			if (!e.getBoundingBox().intersects(body) || !done.add(e.getId())) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, damage, false);
			e.addEffect(rush ? new MobEffectInstance(MobEffects.WEAKNESS, 80, 0) : new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
			l.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			n++;
		}
		return n;
	}

	/** Shift+X: Ecto Rush -- a short intangible dash where he aims, through walls and through everything in the way. */
	public boolean ectoRush(ServerPlayer p) {
		if (!use(p, RUSH)) {
			return false;
		}
		anim(p, ANIM_RUSH);
		Vec3 dir = p.getLookAngle();
		dir = new Vec3(dir.x, Math.max(-0.6, Math.min(0.6, dir.y)), dir.z).normalize();
		RUSHES.put(p.getUUID(), dir);
		if (!flag(p, F_PHASE)) {
			PASSED.put(p.getUUID(), new HashSet<>());
		}
		setFlag(p, F_RUSH, true);
		setTimer(p, "rush_end", p.level().getGameTime() + RUSH_TICKS);
		p.setDeltaMovement(dir.scale(RUSH_SPEED));
		p.hurtMarked = true;
		ServerLevel l = level(p);
		l.sendParticles(WISP, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		OmnitrixSfx.playOn(p, SND_PHASE, 1.1f, 1.5f, net.minecraft.sounds.SoundSource.PLAYERS);
		return true;
	}

	private void tickRush(ServerPlayer p, ServerLevel l, long now) {
		Vec3 dir = RUSHES.get(p.getUUID());
		if (dir == null) {
			return;
		}
		if (now >= timer(p, "rush_end")) {
			endRush(p);
			return;
		}
		p.setDeltaMovement(dir.scale(RUSH_SPEED));
		p.hurtMarked = true;
		p.resetFallDistance();
		passThrough(p, l, RUSH_DAMAGE, true);
		l.sendParticles(VIOLET, p.getX(), p.getY() + 1, p.getZ(), 4, 0.25, 0.5, 0.25, 0.0);
		l.sendParticles(WISP, p.getX(), p.getY() + 0.8, p.getZ(), 4, 0.25, 0.5, 0.25, 0.0);
		QuirkFx.ribbon(l, p.position().add(0, 1, 0).subtract(dir.scale(RUSH_SPEED)), p.position().add(0, 1, 0)).colours(0xFFFFFF, PURPLE).size(0.5f)
				.life(6).style(QuirkFx.RIBBON_LIGHT).spawn();
	}

	public void endRush(ServerPlayer p) {
		if (RUSHES.remove(p.getUUID()) == null && !flag(p, F_RUSH)) {
			return;
		}
		setFlag(p, F_RUSH, false);
		setTimer(p, "rush_end", 0);
		if (!flag(p, F_PHASE)) {
			PASSED.remove(p.getUUID());
		}
		p.setDeltaMovement(p.getDeltaMovement().scale(0.3));
		p.hurtMarked = true;
		com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.settleOutsideBlocks(p);
	}

	public boolean fade(ServerPlayer p) {
		if (!use(p, FADE)) {
			return false;
		}
		anim(p, ANIM_FADE);
		setFlag(p, F_INVIS, true);
		setTimer(p, "fade_end", p.level().getGameTime() + FADE_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
		loseTrackOfMe(p, 24);
		ServerLevel l = level(p);
		l.sendParticles(WISP, p.getX(), p.getY() + 1, p.getZ(), 24, 0.4, 0.8, 0.4, 0.0);
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 0.7f, 1.3f);
		return true;
	}

	public void endFade(ServerPlayer p) {
		if (!flag(p, F_INVIS)) {
			return;
		}
		setFlag(p, F_INVIS, false);
		setTimer(p, "fade_end", 0);
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.getDuration() <= 40 && !flag(p, F_POSSESS)) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	/** Mobs hunting him lose him (not bosses). */
	private static void loseTrackOfMe(ServerPlayer p, double range) {
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), m -> m.getTarget() == p)) {
			if (!(QuirkThreat.add(m, p, 0f) || m.getMaxHealth() >= 150f)) {
				m.setTarget(null);
			}
		}
	}

	// ---------------------------------------------------------------- Z: Chest Tentacles / C: Fright Aura / Shift+Z: Haunt

	private static Vec3 chest(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	/**
	 * Z: his chest splits open and tentacles lash out -- up to {@link #TENTACLE_MAX} creatures in front (8 blocks) are grabbed
	 * ({@link #TENTACLE_HIT}), yanked in close and constricted for 2 s ({@link #TENTACLE_TICK} every 10 ticks). Bosses are
	 * held and hurt, but too heavy to yank.
	 */
	public int tentacles(ServerPlayer p) {
		if (!use(p, TENTACLES)) {
			return 0;
		}
		anim(p, ANIM_TENTACLES);
		ServerLevel l = level(p);
		List<LivingEntity> in = cone(p, TENTACLE_RANGE, 80);
		in.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		List<Integer> held = new ArrayList<>();
		Vec3 front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
		for (LivingEntity e : in) {
			if (held.size() >= TENTACLE_MAX) {
				break;
			}
			e.invulnerableTime = 0;
			hurt(p, e, TENTACLE_HIT, false);
			if (!boss(p, e, 3f)) {
				Vec3 pull = front.subtract(e.position());
				double d = pull.length();
				if (d > 0.5) {
					e.setDeltaMovement(pull.scale(Math.min(1.0, 0.35 + d * 0.12) / d * Math.min(d, 2.5)).add(0, 0.25, 0));
					e.hurtMarked = true;
				}
			}
			held.add(e.getId());
			QuirkFx.ribbon(l, chest(p), e.position().add(0, e.getBbHeight() * 0.6, 0)).colours(0x2A0A3A, 0x05020A).size(0.3f).life(8)
					.style(QuirkFx.RIBBON_SHADOW).spawn();
		}
		if (!held.isEmpty()) {
			GRIPS.put(p.getUUID(), new Grip(p.level().dimension(), held));
			setTimer(p, "tentacles_end", p.level().getGameTime() + TENTACLE_TICKS);
		}
		l.sendParticles(VIOLET, chest(p).x, chest(p).y, chest(p).z, 16, 0.25, 0.25, 0.25, 0.05);
		OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 1.2f, 0.7f);
		return held.size();
	}

	private void tickTentacles(ServerPlayer p, ServerLevel l, long now) {
		Grip g = GRIPS.get(p.getUUID());
		if (g == null) {
			return;
		}
		long end = timer(p, "tentacles_end");
		if (now >= end || g.dim() != l.dimension()) {
			GRIPS.remove(p.getUUID());
			setTimer(p, "tentacles_end", 0);
			return;
		}
		boolean bite = (end - now) % 10 == 0;
		Vec3 front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
		for (int id : g.held()) {
			if (!(l.getEntity(id) instanceof LivingEntity e) || !e.isAlive()) {
				continue;
			}
			boolean boss = boss(p, e, 0f);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, boss ? 1 : 9));
			if (!boss && e.distanceToSqr(front) > 1.0 && now % 2 == 0) {
				Vec3 pull = front.subtract(e.position());
				e.setDeltaMovement(pull.normalize().scale(Math.min(0.5, pull.length() * 0.3)));
				e.hurtMarked = true;
			}
			if (bite) {
				e.invulnerableTime = 0;
				hurt(p, e, TENTACLE_TICK, false);
			}
			if (now % 2 == 0) {
				QuirkFx.ribbon(l, chest(p), e.position().add(0, e.getBbHeight() * 0.55, 0)).colours(0x2A0A3A, 0x05020A).size(0.22f).life(3)
						.style(QuirkFx.RIBBON_SHADOW).spawn();
			}
		}
		if (now % 20 == 0) {
			OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 0.6f, 1.0f);
		}
	}

	/** Whether {@code e} is held in his tentacles right now. */
	public static boolean gripped(ServerPlayer p, LivingEntity e) {
		Grip g = GRIPS.get(p.getUUID());
		return g != null && g.held().contains(e.getId());
	}

	public boolean frightAura(ServerPlayer p) {
		if (!use(p, AURA)) {
			return false;
		}
		anim(p, ANIM_AURA);
		setFlag(p, F_AURA, true);
		setTimer(p, "aura_end", p.level().getGameTime() + AURA_TICKS);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, PURPLE).size(7f).thickness(0.4f).life(16).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.2f, 0.85f);
		return true;
	}

	public int haunt(ServerPlayer p) {
		if (!use(p, HAUNT)) {
			return 0;
		}
		anim(p, ANIM_HAUNT);
		ServerLevel l = level(p);
		int n = 0;
		List<Integer> caught = new ArrayList<>();
		for (LivingEntity e : around(p, p.position(), HAUNT_RADIUS)) {
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
			e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
			e.invulnerableTime = 0;
			hurt(p, e, HAUNT_HIT, false);
			caught.add(e.getId());
			l.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
			n++;
		}
		if (!caught.isEmpty()) {
			HAUNTED.put(p.getUUID(), new Haunted(l.dimension(), caught));
			setTimer(p, "haunt_end", l.getGameTime() + HAUNT_TICKS);
		}
		QuirkFx.blast(l, p.position().add(0, 1, 0)).colours(0xE0D0FF, PURPLE).size(3.5f).life(18).flags(QuirkFx.F_NO_SMOKE).spawn();
		for (int i = 0; i < 3; i++) {
			QuirkFx.ring(l, p.position().add(0, 0.15, 0)).colours(0xFFFFFF, PURPLE).size(3f + i * 3.5f).thickness(0.35f).life(12 + i * 4).ground().spawn();
		}
		QuirkCamera.shakeAround(l, p.position(), 12, 0.3f, 12);
		OmnitrixSounds.play(l, p.position(), SND_SCREAM, 1.0f, 0.6f);
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 0.7f);
		return n;
	}

	/** Haunt's lingering dread -- {@link #HAUNT_TICK} every 10 ticks to each creature it caught, until "haunt_end". */
	private void tickHaunt(ServerPlayer p, ServerLevel l, long now) {
		Haunted h = HAUNTED.get(p.getUUID());
		if (h == null) {
			return;
		}
		long end = timer(p, "haunt_end");
		if (h.dim() != l.dimension() || now > end) {
			HAUNTED.remove(p.getUUID());
			setTimer(p, "haunt_end", 0);
			return;
		}
		if ((end - now) % 10 != 0 || end == now + HAUNT_TICKS) {
			return; // the first bite is 10 ticks after the hit, the sixth on the last tick
		}
		for (int id : h.victims()) {
			if (l.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
				e.invulnerableTime = 0;
				hurt(p, e, HAUNT_TICK, false);
				l.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
			}
		}
		if (now >= end) {
			HAUNTED.remove(p.getUUID());
			setTimer(p, "haunt_end", 0);
		}
	}

	// ---------------------------------------------------------------- vanilla keys

	/** Chilling Touch: +{@link #TOUCH_DAMAGE} and a slow; straight out of Fade it is an Ambush (+{@link #AMBUSH_DAMAGE}, blinds). */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		boolean ambush = flag(p, F_INVIS);
		endFade(p);
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_TOUCH);
			le.invulnerableTime = 0;
			hurt(p, le, TOUCH_DAMAGE + (ambush ? AMBUSH_DAMAGE : 0f), false);
			le.invulnerableTime = 0;
			le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
			ServerLevel l = level(p);
			l.sendParticles(WISP, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.3, 0.2, 0.0);
			if (ambush) {
				le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
				l.sendParticles(ParticleTypes.SOUL, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
				QuirkFx.blast(l, le.position().add(0, le.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, PURPLE).size(1.2f).life(8).flags(QuirkFx.F_NO_SMOKE).spawn();
				OmnitrixSounds.play(l, le.position(), SND_CLAW, 1.2f, 0.6f);
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!use(p, BOLT)) {
			return InteractionResult.FAIL;
		}
		anim(p, ANIM_BOLT);
		AlienProjectileEntity.of(p, "ghostfreak_bolt").colour(PURPLE).size(0.3f).damage(BOLT_DAMAGE).speed(1.6).life(40).spawn();
		OmnitrixSounds.play(level(p), p.position(), SND_CLAW, 0.5f, 1.6f);
		p.swing(hand, true);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- damage

	/** A blow, a bite, an arrow, a fall: what an intangible body only half feels. */
	public static boolean physical(DamageSource s) {
		if (s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.WITCH_RESISTANT_TO) || s.is(DamageTypes.WITHER)
				|| s.is(DamageTypes.SONIC_BOOM) || s.is(DamageTypes.LIGHTNING_BOLT) || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.STARVE)
				|| s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.IS_FALL) || s.is(DamageTypes.MOB_ATTACK) || s.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.STING) || s.is(DamageTypes.THORNS) || s.is(DamageTypes.FALLING_BLOCK)
				|| s.is(DamageTypes.FALLING_ANVIL) || s.is(DamageTypes.FALLING_STALACTITE) || s.is(DamageTypes.CACTUS)
				|| s.is(DamageTypes.SWEET_BERRY_BUSH) || s.is(DamageTypes.FLY_INTO_WALL) || s.is(DamageTypes.CRAMMING)
				|| s.is(DamageTypes.STALAGMITE) || s.is(OmnitrixDamage.ALIEN_STRIKE);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL)) {
			return false;
		}
		if (flag(p, F_POSSESS) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // v0.17.2: hidden inside his host, nothing reaches him
		}
		return !(flag(p, F_PHASE) && physical(source)); // intangible: nothing physical touches him
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return physical(source) ? amount * PHYSICAL_FACTOR : amount;
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Spirit Sight

	public static final String SIGHT = "spirit_sight";
	public static final int ANIM_SIGHT = 13;
	public static final int F_SIGHT = 32;
	public static final double SIGHT_RANGE = 32;
	private static final int SIGHT_NV = 260;

	private void vKey(ServerPlayer p, boolean shift) {
		spiritSight(p);
	}

	/**
	 * V: Spirit Sight on / off -- the dead see what the living cannot: night vision, and every living creature within 32 blocks
	 * glows through walls (the outline is drawn on the wearer's own client only; GhostfreakClient#glows).
	 */
	public boolean spiritSight(ServerPlayer p) {
		if (!use(p, SIGHT)) {
			return false;
		}
		boolean on = !flag(p, F_SIGHT);
		setFlag(p, F_SIGHT, on);
		anim(p, ANIM_SIGHT);
		ServerLevel l = level(p);
		if (on) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT_NV, 0, false, false, false));
			l.sendParticles(WISP, p.getX(), p.getEyeY(), p.getZ(), 16, 0.2, 0.2, 0.2, 0.02);
		} else {
			clearSight(p);
		}
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 0.5f, on ? 1.5f : 1.8f);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.ghostfreak.sight_on" : "message.projecthero.omnitrix.ghostfreak.sight_off")
				.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
		return true;
	}

	/** Takes back only his own short night vision (never a real potion's). */
	private static void clearSight(ServerPlayer p) {
		MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.getDuration() <= SIGHT_NV && !nv.isVisible()) {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	private void revertV(ServerPlayer p) {
		if (flag(p, F_SIGHT)) {
			setFlag(p, F_SIGHT, false);
			clearSight(p);
		}
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		if (flag(p, F_SIGHT) && now % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT_NV, 0, false, false, false));
		}
	}
}
