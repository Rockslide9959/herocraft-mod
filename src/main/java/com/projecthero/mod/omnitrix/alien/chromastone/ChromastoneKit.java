package com.projecthero.mod.omnitrix.alien.chromastone;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, user: "Just make them basic versions of the aliens for now please that we can revamp later"):
 * Chromastone, the Crystalsapien -- a living violet crystal that drinks in energy and fires it back out as prism light.
 *
 * <p>The signature: energy, fire, magic and projectile damage is mostly ABSORBED (he takes a fifth of it) into a synced Charge
 * meter (0-100, 4 per point absorbed; the HUD shows it), and sunlight trickles in too. The charge feeds his prism moves.
 *
 * <pre>
 *   R        Crystal Bolt     a light bolt (24): 6                           Shift+R  Prism Beam       20 charge: a piercing beam (28): 15
 *   G        Shard Spray      five crystal rays in a fan (14): 5 each        Shift+G  Refraction       30 charge: rays to up to 5 creatures
 *                                                                                                      within 16: 12 each
 *   X        Crystal Leap     a bounding leap                                Shift+X  Light Dash       10 charge: a streak along the look
 *   Z        Crystal Slam     a ground pound (r 4): 13 + launch              Shift+Z  Spike Burst      crystal spikes erupt (r 5): 17
 *   V        Absorb Stance    5 s: EVERY hit absorbed (x2 charge), slowed    Shift+V  Light Burst      15 charge: a flash blinds (r 8) 3 s
 *   C        Charge Release   a shockwave (r 6): 8 + a tenth of the charge   Shift+C  Full Spectrum    ULTIMATE (50+ charge, spends it all):
 *                             (spends it all; up to 18)                                                0.75 s charge, a rainbow beam (32):
 *                                                                                                      40 + 0.6 per charge over 50 (up to 70)
 * </pre>
 * Weakness: blunt force -- blows, explosions and falling things crack him (x1.25, never absorbed).
 */
public final class ChromastoneKit extends AlienKit {
	public static final String ID = Aliens.CHROMASTONE.id();
	public static final int MAX_CHARGE = 100;
	/** Charge per point of damage absorbed. */
	public static final float CHARGE_PER_DAMAGE = 4f;
	/** The part of an absorbable hit that still gets through. */
	public static final float ABSORB_LEAK = 0.2f;

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue BOLT = OmnitrixSfx.cue("chromastone_bolt")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 1.4f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.15f, 2.0f);
	public static final OmnitrixSfx.Cue PRISM = OmnitrixSfx.cue("chromastone_prism")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.35f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45f, 1.2f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.25f, 1.5f, 2);
	public static final OmnitrixSfx.Cue ABSORB = OmnitrixSfx.cue("chromastone_absorb")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 0.8f)
			.layer(SoundEvents.BEACON_AMBIENT, 0.3f, 1.4f)
			.gap(6);
	public static final OmnitrixSfx.Cue CRACK = OmnitrixSfx.cue("chromastone_crack")
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.5f, 0.7f)
			.layer(SoundEvents.GLASS_HIT, 0.3f, 0.6f)
			.gap(4);
	public static final OmnitrixSfx.Cue SLAM = OmnitrixSfx.cue("chromastone_slam")
			.layer(SoundEvents.AMETHYST_BLOCK_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.15f, 1.4f)
			.layer(SoundEvents.AMETHYST_CLUSTER_PLACE, 0.4f, 0.8f, 2);
	public static final OmnitrixSfx.Cue LEAP = OmnitrixSfx.cue("chromastone_leap")
			.layer(SoundEvents.AMETHYST_BLOCK_STEP, 0.5f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.2f, 0.7f);
	public static final OmnitrixSfx.Cue STANCE = OmnitrixSfx.cue("chromastone_stance")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.35f, 0.7f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45f, 0.6f);
	public static final OmnitrixSfx.Cue FLASH = OmnitrixSfx.cue("chromastone_flash")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 2.0f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.45f, 2.0f);
	public static final OmnitrixSfx.Cue SPECTRUM = OmnitrixSfx.cue("chromastone_spectrum")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.45f, 1.2f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.2f, 1.5f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.8f, 3);

	// ---------------------------------------------------------------- poses (client: ChromastoneClient)
	public static final int ANIM_BOLT = 1, ANIM_PRISM = 2, ANIM_SHARDS = 3, ANIM_REFRACT = 4, ANIM_LEAP = 5, ANIM_DASH = 6, ANIM_SLAM = 7,
			ANIM_SPIKES = 8, ANIM_STANCE = 9, ANIM_FLASH = 10, ANIM_RELEASE = 11, ANIM_SPECTRUM = 12;

	public static final int F_STANCE = 1;
	public static final int F_SPECTRUM = 2;
	/** The Refraction's rays: the targets' entity ids ride in the {@code ray.N} timers for the client (id + 1). */
	public static final int F_REFRACT = 4;

	public static final String BOLT_MOVE = "crystal_bolt", PRISM_BEAM = "prism_beam", SHARD_SPRAY = "shard_spray", REFRACTION = "refraction",
			LEAP_MOVE = "crystal_leap", LIGHT_DASH = "light_dash", CRYSTAL_SLAM = "crystal_slam", SPIKE_BURST = "spike_burst",
			ABSORB_STANCE = "absorb_stance", LIGHT_BURST = "light_burst", CHARGE_RELEASE = "charge_release", FULL_SPECTRUM = "full_spectrum";
	/** The kit timer holding the charge (0-100; unset = 0). */
	public static final String CHARGE = "charge";

	public static final float BOLT_DAMAGE = 6f;
	public static final float PRISM_DAMAGE = 15f;
	public static final int PRISM_COST = 20;
	public static final float SHARD_DAMAGE = 5f;
	public static final float REFRACT_DAMAGE = 12f;
	public static final int REFRACT_COST = 30;
	public static final int DASH_COST = 10;
	public static final float SLAM_DAMAGE = 13f;
	public static final float SPIKE_DAMAGE = 17f;
	public static final int FLASH_COST = 15;
	public static final float RELEASE_BASE = 8f;
	public static final int SPECTRUM_MIN = 50;
	public static final float SPECTRUM_BASE = 40f;
	public static final int SPECTRUM_WINDUP = 15;
	public static final double SPECTRUM_RANGE = 32.0;
	public static final int STANCE_TICKS = 100;

	public static void register() {
		Aliens.register(new ChromastoneKit());
	}

	/** Nothing static per player: the charge and every running move live in the synced state. */
	public static void clearSessionState() {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(34).speed(0.95f).armor(10, 2).attack(4).knockbackResist(0.4f).safeFall(3).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BOLT_MOVE, AbilitySlot.SLOT_1, 14), AlienMove.shift(PRISM_BEAM, AbilitySlot.SLOT_1, 60),
				AlienMove.tap(SHARD_SPRAY, AbilitySlot.SLOT_2, 80), AlienMove.shift(REFRACTION, AbilitySlot.SLOT_2, 120),
				AlienMove.tap(LEAP_MOVE, AbilitySlot.SLOT_3, 50), AlienMove.shift(LIGHT_DASH, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(CRYSTAL_SLAM, AbilitySlot.SLOT_4, 140), AlienMove.shift(SPIKE_BURST, AbilitySlot.SLOT_4, 220),
				AlienMove.tap(ABSORB_STANCE, AbilitySlot.SLOT_5, 300), AlienMove.shift(LIGHT_BURST, AbilitySlot.SLOT_5, 200),
				AlienMove.tap(CHARGE_RELEASE, AbilitySlot.SLOT_6, 200), AlienMove.shift(FULL_SPECTRUM, AbilitySlot.SLOT_6, 900));
	}

	// ---------------------------------------------------------------- the charge

	/** The charge, 0-100 (client-safe: the synced kit timer). */
	public static int charge(Player p) {
		return (int) Math.max(0L, Math.min(MAX_CHARGE, com.projecthero.mod.omnitrix.Omnitrix.state(p).readyAt(ID + "." + CHARGE)));
	}

	public void setCharge(ServerPlayer p, int value) {
		int v = Math.max(0, Math.min(MAX_CHARGE, value));
		if (v != charge(p)) {
			setTimer(p, CHARGE, v);
		}
	}

	public void addCharge(ServerPlayer p, float amount) {
		setCharge(p, charge(p) + Math.max(1, Math.round(amount)));
	}

	/** Spends {@code cost} charge; false (and a line on the action bar) if there is not enough. */
	private boolean spend(ServerPlayer p, int cost, String moveId) {
		if (charge(p) < cost) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.no_charge",
					Component.translatable("projecthero.omnitrix." + ID + "." + moveId), cost).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return false;
		}
		setCharge(p, charge(p) - cost);
		return true;
	}

	/** Energy, fire, magic or a projectile: what his crystal drinks in. */
	public static boolean absorbable(DamageSource s) {
		if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypeTags.IS_LIGHTNING) || s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.WITCH_RESISTANT_TO)
				|| s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC) || s.is(DamageTypes.SONIC_BOOM) || s.is(DamageTypes.WITHER)
				|| s.is(DamageTypes.DRAGON_BREATH) || s.is(com.projecthero.mod.omnitrix.OmnitrixDamage.ALIEN_FIRE);
	}

	/** Blunt force: blows, explosions, falling things -- they crack crystal. */
	public static boolean blunt(DamageSource s) {
		return s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypes.MOB_ATTACK) || s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.FALLING_BLOCK)
				|| s.is(DamageTypes.FALLING_ANVIL) || s.is(DamageTypes.FALLING_STALACTITE) || s.is(com.projecthero.mod.omnitrix.OmnitrixDamage.ALIEN_STRIKE);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		setCharge(p, 0);
		OmnitrixSfx.playOn(p, ABSORB, 1.0f, 1.2f, SoundSource.PLAYERS);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		setFlag(p, F_STANCE | F_SPECTRUM | F_REFRACT, false);
		for (String t : new String[] { CHARGE, "stance_end", "spectrum_at", "ray.0", "ray.1", "ray.2", "ray.3", "ray.4" }) {
			if (timer(p, t) > 0) {
				setTimer(p, t, 0);
			}
		}
		MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.getDuration() <= 12 && slow.getAmplifier() == 2) {
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5 && flag(p, F_STANCE);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// sunlight trickles in (Crystalsapiens feed on light): +1 every 2 s under the open daytime sky
		if (now % 40 == 0 && l.isDay() && !l.isRaining() && l.canSeeSky(BlockPos.containing(p.getEyePosition())) && charge(p) < MAX_CHARGE) {
			setCharge(p, charge(p) + 1);
		}
		if (p.isOnFire()) {
			p.clearFire(); // crystal does not burn
		}
		if (flag(p, F_STANCE)) {
			if (now >= timer(p, "stance_end")) {
				setFlag(p, F_STANCE, false);
				setTimer(p, "stance_end", 0);
			} else if (now % 5 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2, false, false, false));
			}
		}
		if (flag(p, F_REFRACT) && now - s.animStart > 10) {
			setFlag(p, F_REFRACT, false);
		}
		if (flag(p, F_SPECTRUM)) {
			p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
			p.hurtMarked = true;
			if (now % 3 == 0) {
				l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getEyeY() - 0.3, p.getZ(), 2, 0.4, 0.4, 0.4, 0.02);
			}
			if (now >= timer(p, "spectrum_at")) {
				fireSpectrum(p, l);
			}
		}
		if (charge(p) >= 50 && now % 20 == 0) {
			l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.3, 0.5, 0.3, 0.01); // brimming with light
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_SPECTRUM)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					prismBeam(p);
				} else {
					bolt(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					refraction(p);
				} else {
					shardSpray(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					lightDash(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					spikeBurst(p);
				} else {
					slam(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					lightBurst(p);
				} else {
					stance(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					fullSpectrum(p);
				} else {
					release(p);
				}
			}
			default -> {
			}
		}
	}

	private boolean bolt(ServerPlayer p) {
		if (!use(p, BOLT_MOVE)) {
			return false;
		}
		anim(p, ANIM_BOLT);
		LivingEntity t = RecalKitMath.first(p, p.getLookAngle(), 24.0, 0.4);
		if (t != null) {
			hurt(p, t, BOLT_DAMAGE, false);
			glint(level(p), t.getBoundingBox().getCenter(), 4);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), BOLT, 1.0f, 1.0f);
		return true;
	}

	/** Shift+R: 20 charge for a beam that pierces everything in line (15 each). True if it fired. */
	public boolean prismBeam(ServerPlayer p) {
		if (cooldownLeft(p, PRISM_BEAM) > 0) {
			return use(p, PRISM_BEAM); // the "not ready" line
		}
		if (!spend(p, PRISM_COST, PRISM_BEAM) || !use(p, PRISM_BEAM)) {
			return false;
		}
		anim(p, ANIM_PRISM);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), 28.0, 0.6)) {
			hurt(p, e, PRISM_DAMAGE, false);
			glint(level(p), e.getBoundingBox().getCenter(), 8);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), PRISM, 1.2f, 1.0f);
		return true;
	}

	private boolean shardSpray(ServerPlayer p) {
		if (!use(p, SHARD_SPRAY)) {
			return false;
		}
		anim(p, ANIM_SHARDS);
		Vec3 look = p.getLookAngle();
		Set<Integer> hit = new HashSet<>();
		for (int i = -2; i <= 2; i++) {
			LivingEntity t = RecalKitMath.first(p, RecalKitMath.turned(look, i * 8.0), 14.0, 0.45);
			if (t != null && hit.add(t.getId())) {
				hurt(p, t, SHARD_DAMAGE, false);
			}
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), BOLT, 1.2f, 0.8f);
		return true;
	}

	private boolean refraction(ServerPlayer p) {
		if (cooldownLeft(p, REFRACTION) > 0) {
			return use(p, REFRACTION);
		}
		List<LivingEntity> targets = around(p, p.position(), 16).stream().filter(p::hasLineOfSight)
				.sorted(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(5).toList();
		if (targets.isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!spend(p, REFRACT_COST, REFRACTION) || !use(p, REFRACTION)) {
			return false;
		}
		anim(p, ANIM_REFRACT);
		setFlag(p, F_REFRACT, true);
		for (int i = 0; i < 5; i++) {
			setTimer(p, "ray." + i, i < targets.size() ? targets.get(i).getId() + 1L : 0L);
		}
		for (LivingEntity e : targets) {
			hurt(p, e, REFRACT_DAMAGE, false);
			glint(level(p), e.getBoundingBox().getCenter(), 6);
		}
		OmnitrixSfx.play(level(p), p.getEyePosition(), PRISM, 1.2f, 1.3f);
		return true;
	}

	private boolean leap(ServerPlayer p) {
		if (!use(p, LEAP_MOVE)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 h = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(1.1).add(0, 0.85, 0));
		p.hurtMarked = true;
		OmnitrixSfx.playOn(p, LEAP, 1.0f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	private boolean lightDash(ServerPlayer p) {
		if (cooldownLeft(p, LIGHT_DASH) > 0) {
			return use(p, LIGHT_DASH);
		}
		if (!spend(p, DASH_COST, LIGHT_DASH) || !use(p, LIGHT_DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		p.setDeltaMovement(p.getLookAngle().scale(2.6));
		p.hurtMarked = true;
		p.resetFallDistance();
		level(p).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
		OmnitrixSfx.playOn(p, FLASH, 0.8f, 1.2f, SoundSource.PLAYERS);
		return true;
	}

	private boolean slam(ServerPlayer p) {
		if (!use(p, CRYSTAL_SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		for (LivingEntity e : around(p, p.position(), 4)) {
			hurt(p, e, SLAM_DAMAGE, false);
			shove(e, p.position(), 0.5, 0.7);
		}
		level(p).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.1, p.getZ(), 14, 2.0, 0.1, 2.0, 0.05);
		OmnitrixSfx.play(level(p), p.position(), SLAM, 1.2f, 1.0f);
		return true;
	}

	private boolean spikeBurst(ServerPlayer p) {
		if (!use(p, SPIKE_BURST)) {
			return false;
		}
		anim(p, ANIM_SPIKES);
		for (LivingEntity e : around(p, p.position(), 5)) {
			hurt(p, e, SPIKE_DAMAGE, false);
			shove(e, p.position(), 0.4, 0.9);
		}
		OmnitrixSfx.play(level(p), p.position(), SLAM, 1.4f, 0.8f);
		return true;
	}

	private boolean stance(ServerPlayer p) {
		if (flag(p, F_STANCE)) {
			setFlag(p, F_STANCE, false);
			setTimer(p, "stance_end", 0);
			return true;
		}
		if (!use(p, ABSORB_STANCE)) {
			return false;
		}
		anim(p, ANIM_STANCE);
		setFlag(p, F_STANCE, true);
		setTimer(p, "stance_end", p.level().getGameTime() + STANCE_TICKS);
		OmnitrixSfx.playOn(p, STANCE, 1.0f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	private boolean lightBurst(ServerPlayer p) {
		if (cooldownLeft(p, LIGHT_BURST) > 0) {
			return use(p, LIGHT_BURST);
		}
		if (!spend(p, FLASH_COST, LIGHT_BURST) || !use(p, LIGHT_BURST)) {
			return false;
		}
		anim(p, ANIM_FLASH);
		for (LivingEntity e : around(p, p.position(), 8)) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false), p);
			com.projecthero.mod.quirk.QuirkThreat.add(e, p, 3f);
		}
		level(p).sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0, 0, 0, 0);
		OmnitrixSfx.play(level(p), p.position(), FLASH, 1.2f, 1.0f);
		return true;
	}

	/** C: everything stored goes out in a shockwave -- 8 + a tenth of the charge (up to 18). True if it fired. */
	public boolean release(ServerPlayer p) {
		if (!use(p, CHARGE_RELEASE)) {
			return false;
		}
		anim(p, ANIM_RELEASE);
		int c = charge(p);
		float dmg = RELEASE_BASE + c * 0.1f;
		setCharge(p, 0);
		for (LivingEntity e : around(p, p.position(), 6)) {
			hurt(p, e, dmg, false);
			shove(e, p.position(), 0.8, 0.3);
		}
		OmnitrixSfx.play(level(p), p.position(), PRISM, 1.3f, 0.7f);
		return true;
	}

	private boolean fullSpectrum(ServerPlayer p) {
		if (cooldownLeft(p, FULL_SPECTRUM) > 0) {
			return use(p, FULL_SPECTRUM);
		}
		if (charge(p) < SPECTRUM_MIN) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.chromastone.no_charge",
					Component.translatable("projecthero.omnitrix." + ID + "." + FULL_SPECTRUM), SPECTRUM_MIN).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return false;
		}
		if (!use(p, FULL_SPECTRUM)) {
			return false;
		}
		anim(p, ANIM_SPECTRUM);
		setFlag(p, F_SPECTRUM, true);
		setTimer(p, "spectrum_at", p.level().getGameTime() + SPECTRUM_WINDUP);
		OmnitrixSfx.playOn(p, STANCE, 1.3f, 1.4f, SoundSource.PLAYERS);
		return true;
	}

	/** The Full Spectrum beam: 40 + 0.6 per charge over 50 (70 at full) to everything in a 32-block line; the charge is spent. */
	public void fireSpectrum(ServerPlayer p, ServerLevel l) {
		setFlag(p, F_SPECTRUM, false);
		setTimer(p, "spectrum_at", 0);
		int c = charge(p);
		float dmg = SPECTRUM_BASE + Math.max(0, c - SPECTRUM_MIN) * 0.6f;
		setCharge(p, 0);
		for (LivingEntity e : RecalKitMath.line(p, p.getLookAngle(), SPECTRUM_RANGE, 1.3)) {
			hurt(p, e, dmg, false);
			shove(e, p.position(), 1.0, 0.3);
			glint(l, e.getBoundingBox().getCenter(), 12);
		}
		OmnitrixSfx.play(l, p.getEyePosition(), SPECTRUM, 1.6f, 1.0f);
	}

	private static void glint(ServerLevel l, Vec3 at, int n) {
		l.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, n, 0.2, 0.2, 0.2, 0.06);
	}

	// ---------------------------------------------------------------- damage: absorption

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.CAMPFIRE)); // crystal does not burn
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		boolean stance = flag(p, F_STANCE) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (stance || absorbable(source)) {
			// only when the hit would land (not inside the hurt cooldown), so nothing is farmed from i-frames
			if (p.invulnerableTime <= 10) {
				addCharge(p, amount * CHARGE_PER_DAMAGE * (stance ? 2f : 1f));
				OmnitrixSfx.play(level(p), p.position(), ABSORB, 0.8f, 1.0f);
				level(p).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.5, 0.3, 0.03);
			}
			return stance ? 0f : amount * ABSORB_LEAK;
		}
		if (blunt(source)) {
			OmnitrixSfx.play(level(p), p.position(), CRACK, 0.8f, 1.0f);
			return amount * 1.25f;
		}
		return amount;
	}
}
