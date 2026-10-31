package com.projecthero.mod.omnitrix.alien.ghostfreak;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
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
 * instead of legs, half see-through. He drifts down slowly (low gravity), physical blows do half, he never drowns, and the sun
 * burns him a little (the comics' weakness).
 *
 * <pre>
 *   R        Spectral Claw    a raking slash in front (12, withers)          Shift+R  Scream          a shriek cone: what hears it flees in terror (6)
 *   G        Possess          slip into a mob: for 10 s it fights for you     Shift+G  Soul Siphon     drain a creature (10) and heal 5
 *   X        Phase            intangible for up to 5 s: through walls, no physical harm (X again ends it)
 *   Shift+X  Ghost Flight     toggle: float freely
 *   Z        Shadow Tendrils  tendrils from the ground pin and choke what     Shift+Z  Haunt           ultimate: everything within 10 takes 8, then 3
 *                             is near (3 every 0.5 s for 3 s)                                         every 0.5 s for 3 s (26), in darkness and dread
 *   V        Spirit Sight     toggle: see in the dark, every living thing within 32 outlined through walls (yours only)
 *   C        Fright Aura      10 s: everything near you panics and weakens    Shift+C  Fade            invisible for 10 s (your attack ends it)
 *   Attack   Chilling Touch (+1, slows)      Use (empty hand)  a spectral bolt (3)
 * </pre>
 *
 * <p>v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the rebalance
 * above -- Shadow Tendrils to Z, Haunt to Shift+Z as his ultimate, Fade to Shift+C.
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
	public static final int ANIM_TENDRILS = 8;
	public static final int ANIM_AURA = 9;
	public static final int ANIM_HAUNT = 10;
	public static final int ANIM_TOUCH = 11;
	public static final int ANIM_BOLT = 12;

	public static final int F_PHASE = 1;
	public static final int F_INVIS = 2;
	public static final int F_FLY = 4;
	public static final int F_AURA = 8;
	public static final int F_POSSESS = 16;

	public static final String CLAW = "spectral_claw";
	public static final String SCREAM = "scream";
	public static final String POSSESS = "possess";
	public static final String SIPHON = "soul_siphon";
	public static final String PHASE = "phase";
	public static final String FLIGHT = "ghost_flight";
	public static final String FADE = "fade";
	public static final String TENDRILS = "shadow_tendrils";
	public static final String AURA = "fright_aura";
	public static final String HAUNT = "haunt";
	public static final String TOUCH = "chilling_touch";
	public static final String BOLT = "spectral_bolt";

	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 7
	public static final float CLAW_DAMAGE = 12f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3
	public static final float SCREAM_DAMAGE = 6f;
	public static final int FEAR_TICKS = 100;
	public static final int POSSESS_TICKS = 200;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 6
	public static final float SIPHON_DAMAGE = 10f;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3 (half the drain)
	public static final float SIPHON_HEAL = 5f;
	/**
	 * v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): Haunt is his
	 * ultimate now -- was a flat 2. {@link #HAUNT_HIT} to everything within {@link #HAUNT_RADIUS}, then {@link #HAUNT_TICK} every
	 * 10 ticks for {@link #HAUNT_TICKS} on each one it caught (8 + 18 = 26).
	 */
	public static final float HAUNT_HIT = 8f;
	public static final float HAUNT_TICK = 3f;
	public static final int HAUNT_TICKS = 60;
	public static final double HAUNT_RADIUS = 10;
	public static final int HAUNT_COOLDOWN = 600;
	/** Phase: how long it can last, and the cooldown that starts when it ends. */
	public static final int PHASE_TICKS = 100;
	public static final int PHASE_COOLDOWN = 200;
	public static final int FADE_TICKS = 200;
	public static final int TENDRIL_TICKS = 60;
	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 2 (12 over 3 s)
	public static final float TENDRIL_DAMAGE = 3f;
	public static final int AURA_TICKS = 200;
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

	/** player -> the mob possessed (and in which dimension). */
	private static final Map<UUID, Possession> POSSESSED = new ConcurrentHashMap<>();
	/** player -> creatures fleeing from him -> until when. */
	private static final Map<UUID, Map<Integer, Long>> FEARED = new ConcurrentHashMap<>();
	/** player -> where the Shadow Tendrils grip. */
	private static final Map<UUID, Tendrils> TENDRIL_PITS = new ConcurrentHashMap<>();

	private record Possession(ResourceKey<Level> dim, int mobId) {
	}

	private record Tendrils(ResourceKey<Level> dim, Vec3 at) {
	}

	/** v0.17.2: player -> the creatures his Haunt caught (the dread keeps biting for {@link #HAUNT_TICKS}). */
	private static final Map<UUID, Haunted> HAUNTED = new ConcurrentHashMap<>();

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
		TENDRIL_PITS.clear();
		HAUNTED.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(24f).speed(1.0f).armor(2f, 0f).attack(2f).gravity(0.25f).safeFall(30f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(CLAW, AbilitySlot.SLOT_1, 16), AlienMove.shift(SCREAM, AbilitySlot.SLOT_1, 240),
				AlienMove.tap(POSSESS, AbilitySlot.SLOT_2, 400), AlienMove.shift(SIPHON, AbilitySlot.SLOT_2, 200),
				AlienMove.tap(PHASE, AbilitySlot.SLOT_3, PHASE_COOLDOWN), AlienMove.shift(FLIGHT, AbilitySlot.SLOT_3, 10),
				// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was
				// Z Fade, Shift+Z Shadow Tendrils, Shift+C Haunt (500)
				AlienMove.tap(TENDRILS, AbilitySlot.SLOT_4, 280), AlienMove.shift(HAUNT, AbilitySlot.SLOT_4, HAUNT_COOLDOWN),
				AlienMove.tap(SIGHT, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(AURA, AbilitySlot.SLOT_6, 600), AlienMove.shift(FADE, AbilitySlot.SLOT_6, 400),
				AlienMove.vanilla(TOUCH, "Attack", 0), AlienMove.vanilla(BOLT, "Use (empty hand)", 12));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 1.0f);
		l.sendParticles(WISP, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		endPhase(p, false);
		stopFlying(p);
		endFade(p);
		endPossession(p);
		setFlag(p, F_AURA, false);
		setTimer(p, "aura_end", 0);
		FEARED.remove(p.getUUID());
		TENDRIL_PITS.remove(p.getUUID());
		HAUNTED.remove(p.getUUID());
		setTimer(p, "haunt_end", 0);
	}

	/** Phase is on: horizontal movement ignores blocks (the framework's collision mixin). */
	@Override
	public boolean phasing(Player p) {
		return flag(p, F_PHASE);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		p.setAirSupply(p.getMaxAirSupply()); // nothing to breathe
		if (now % 4 == 0) {
			l.sendParticles(WISP, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
		}
		// the sun burns an Ectonurite a little
		if (now % 40 == 0 && inSunlight(p)) {
			p.hurt(p.damageSources().inFire(), SUN_DAMAGE);
			l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
			if (now % 200 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.sun").withStyle(ChatFormatting.GOLD), true);
			}
		}
		// Phase
		long phaseEnd = timer(p, "phase_end");
		if (flag(p, F_PHASE)) {
			if (now >= phaseEnd) {
				endPhase(p, true);
			} else if (now % 3 == 0) {
				l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 1, 0.3, 0.6, 0.3, 0.01);
			}
		}
		// Ghost Flight
		if (flag(p, F_FLY) && !p.getAbilities().mayfly) {
			p.getAbilities().mayfly = true;
			p.onUpdateAbilities();
		}
		// Fade
		long fadeEnd = timer(p, "fade_end");
		if (flag(p, F_INVIS)) {
			if (now >= fadeEnd) {
				endFade(p);
			} else if (now % 20 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
				loseTrackOfMe(p, 24);
			}
		}
		tickPossession(p, l, now);
		tickFear(p, l, now);
		tickTendrils(p, l, now);
		tickHaunt(p, l, now);
		// Fright Aura
		long auraEnd = timer(p, "aura_end");
		if (flag(p, F_AURA)) {
			if (now >= auraEnd) {
				setFlag(p, F_AURA, false);
				setTimer(p, "aura_end", 0);
			} else {
				if (now % 20 == 0) {
					for (LivingEntity e : around(p, p.position(), 7)) {
						frighten(p, e, 40);
						e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
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
					siphon(p, lookTarget(p, 10));
				} else {
					possess(p, lookTarget(p, 12));
				}
			}
			case SLOT_3 -> {
				if (shift) {
					toggleFlight(p);
				} else if (flag(p, F_PHASE)) {
					endPhase(p, true);
				} else {
					phase(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					haunt(p); // v0.17.2: was Shift+C
				} else {
					tendrils(p); // v0.17.2: was Shift+Z
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					fade(p); // v0.17.2: was Z
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

	public int claw(ServerPlayer p) {
		if (!use(p, CLAW)) {
			return 0;
		}
		anim(p, ANIM_CLAW);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : cone(p, 4.0, 80)) {
			e.invulnerableTime = 0;
			hurt(p, e, CLAW_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
			l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			n++;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		for (int k = -1; k <= 1; k++) {
			Vec3 a = eye.add(look.scale(1.0)).add(side.scale(1.4)).add(0, 0.5 + k * 0.25, 0);
			Vec3 b = eye.add(look.scale(2.6)).add(side.scale(-1.4)).add(0, -0.6 + k * 0.25, 0);
			QuirkFx.ribbon(l, a, b).colours(0xFFFFFF, PURPLE).size(0.18f).life(7).style(QuirkFx.RIBBON_LIGHT).spawn();
		}
		OmnitrixSounds.play(l, p.position(), SND_CLAW, 1.0f, 0.9f + p.getRandom().nextFloat() * 0.2f);
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

	// ---------------------------------------------------------------- Possess

	/** G: slips into {@code target} (a mob, not a boss): for 10 s it fights for him -- whatever he looks at, or the nearest hostile. */
	public boolean possess(ServerPlayer p, LivingEntity target) {
		if (!(target instanceof Mob mob) || target instanceof Player) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (boss(p, mob, 6f)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.too_strong").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, POSSESS)) {
			return false;
		}
		endPossession(p);
		anim(p, ANIM_POSSESS);
		POSSESSED.put(p.getUUID(), new Possession(p.level().dimension(), mob.getId()));
		setTimer(p, "possess_end", p.level().getGameTime() + POSSESS_TICKS);
		setFlag(p, F_POSSESS, true);
		mob.setTarget(null);
		mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, POSSESS_TICKS, 1));
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, POSSESS_TICKS, 0));
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, p.position().add(0, 1.2, 0), mob.position().add(0, mob.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, PURPLE).size(0.45f).life(12)
				.style(QuirkFx.RIBBON_SHADOW).spawn();
		l.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.03);
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
		if (mob == null || now >= timer(p, "possess_end")) {
			endPossession(p);
			return;
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
		if (want == null && mob instanceof PathfinderMob pm && now % 20 == 0 && mob.distanceToSqr(p) > 16) {
			pm.getNavigation().moveTo(p, 1.2); // no one to fight: it keeps close
		}
		if (now % 3 == 0) {
			l.sendParticles(VIOLET, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 1, mob.getBbWidth() * 0.4, 0.3, mob.getBbWidth() * 0.4, 0);
		}
	}

	public void endPossession(ServerPlayer p) {
		Possession ps = POSSESSED.remove(p.getUUID());
		setFlag(p, F_POSSESS, false);
		setTimer(p, "possess_end", 0);
		if (ps != null && p.server.getLevel(ps.dim()) instanceof ServerLevel l && l.getEntity(ps.mobId()) instanceof Mob mob) {
			mob.setTarget(null);
			mob.removeEffect(MobEffects.GLOWING);
			l.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
		}
	}

	public boolean siphon(ServerPlayer p, LivingEntity t) {
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ghostfreak.no_host").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, SIPHON)) {
			return false;
		}
		anim(p, ANIM_SIPHON);
		t.invulnerableTime = 0;
		if (hurt(p, t, SIPHON_DAMAGE, false)) {
			p.heal(SIPHON_HEAL); // v0.17.2: was half the drain (3)
		}
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, t.position().add(0, t.getBbHeight() * 0.6, 0), p.position().add(0, 1.2, 0)).colours(0xFFFFFF, PURPLE).size(0.3f).life(14)
				.style(QuirkFx.RIBBON_SHADOW).spawn();
		Vec3 d = p.position().subtract(t.position()).add(0, 0.6, 0);
		for (int k = 0; k < 8; k++) {
			Vec3 at = t.position().add(d.scale(k / 8.0)).add(0, t.getBbHeight() * 0.5, 0);
			l.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 0.8f, 1.5f);
		return true;
	}

	// ---------------------------------------------------------------- Phase / flight / Fade

	public boolean phase(ServerPlayer p) {
		if (flag(p, F_PHASE) || !use(p, PHASE)) {
			return false;
		}
		anim(p, ANIM_PHASE);
		setFlag(p, F_PHASE, true);
		setTimer(p, "phase_end", p.level().getGameTime() + PHASE_TICKS);
		cooldown(p, PHASE, PHASE_TICKS); // the real cooldown starts when it ends
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
		if (cooldown) {
			cooldown(p, PHASE, PHASE_COOLDOWN);
			OmnitrixSounds.play(level(p), p.position(), SND_PHASE, 0.7f, 0.7f);
		}
		com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.settleOutsideBlocks(p);
	}

	public boolean toggleFlight(ServerPlayer p) {
		if (!use(p, FLIGHT)) {
			return false;
		}
		if (flag(p, F_FLY)) {
			stopFlying(p);
			return true;
		}
		anim(p, ANIM_FLIGHT);
		setFlag(p, F_FLY, true);
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.getAbilities().setFlyingSpeed(FLY_SPEED);
		p.onUpdateAbilities();
		level(p).sendParticles(WISP, p.getX(), p.getY() + 0.3, p.getZ(), 16, 0.4, 0.2, 0.4, 0.0);
		OmnitrixSounds.play(level(p), p.position(), SND_PHASE, 0.6f, 1.4f);
		return true;
	}

	private void stopFlying(ServerPlayer p) {
		if (!flag(p, F_FLY)) {
			return;
		}
		setFlag(p, F_FLY, false);
		if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.getAbilities().setFlyingSpeed(0.05f);
		p.onUpdateAbilities();
		p.resetFallDistance();
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
		if (inv != null && inv.getDuration() <= 40) {
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

	// ---------------------------------------------------------------- Shadow Tendrils / Fright Aura / Haunt

	public boolean tendrils(ServerPlayer p) {
		if (!use(p, TENDRILS)) {
			return false;
		}
		anim(p, ANIM_TENDRILS);
		TENDRIL_PITS.put(p.getUUID(), new Tendrils(p.level().dimension(), p.position()));
		setTimer(p, "tendrils_end", p.level().getGameTime() + TENDRIL_TICKS);
		ServerLevel l = level(p);
		QuirkFx.decal(l, p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0x0A0410).size(6f).life(TENDRIL_TICKS + 20).spawn();
		OmnitrixSounds.play(l, p.position(), SND_TENDRIL, 1.1f, 0.8f);
		return true;
	}

	private void tickTendrils(ServerPlayer p, ServerLevel l, long now) {
		Tendrils t = TENDRIL_PITS.get(p.getUUID());
		if (t == null) {
			return;
		}
		if (now >= timer(p, "tendrils_end") || t.dim() != l.dimension()) {
			TENDRIL_PITS.remove(p.getUUID());
			setTimer(p, "tendrils_end", 0);
			return;
		}
		if (now % 10 != 0) {
			return;
		}
		for (LivingEntity e : around(p, t.at(), 6)) {
			boolean boss = boss(p, e, 2f);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, boss ? 1 : 9));
			e.invulnerableTime = 0;
			hurt(p, e, TENDRIL_DAMAGE, false);
			Vec3 from = new Vec3(e.getX() + (l.random.nextDouble() - 0.5), t.at().y + 0.05, e.getZ() + (l.random.nextDouble() - 0.5));
			QuirkFx.ribbon(l, from, e.position().add(0, e.getBbHeight() * 0.6, 0)).colours(0x2A0A3A, 0x05020A).size(0.25f).life(12)
					.style(QuirkFx.RIBBON_SHADOW).spawn();
		}
		if (now % 20 == 0) {
			OmnitrixSounds.play(l, t.at(), SND_TENDRIL, 0.6f, 1.0f);
		}
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
			// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 2
			hurt(p, e, HAUNT_HIT, false);
			caught.add(e.getId());
			l.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
			n++;
		}
		if (!caught.isEmpty()) {
			// v0.17.2: and the dread keeps biting -- HAUNT_TICK every 10 ticks for HAUNT_TICKS on each one it caught
			HAUNTED.put(p.getUUID(), new Haunted(l.dimension(), caught));
			setTimer(p, "haunt_end", l.getGameTime() + HAUNT_TICKS);
		}
		QuirkFx.blast(l, p.position().add(0, 1, 0)).colours(0xE0D0FF, PURPLE).size(3.5f).life(18).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.decal(l, p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_POOL).colour(0x0A0410).size(10f).life(80).spawn();
		OmnitrixSounds.play(l, p.position(), SND_SCREAM, 1.0f, 0.6f);
		OmnitrixSounds.play(l, p.position(), SND_LAUGH, 1.0f, 0.7f);
		return n;
	}

	/** v0.17.2: Haunt's lingering dread -- {@link #HAUNT_TICK} every 10 ticks to each creature it caught, until "haunt_end". */
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

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		endFade(p);
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_TOUCH);
			le.invulnerableTime = 0;
			hurt(p, le, 1f, false);
			le.invulnerableTime = 0;
			le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
			level(p).sendParticles(WISP, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.3, 0.2, 0.0);
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
		AlienProjectileEntity.of(p, "ghostfreak_bolt").colour(PURPLE).size(0.3f).damage(3f).speed(1.5).life(40).spawn();
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
