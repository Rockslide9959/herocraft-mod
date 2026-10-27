package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.data.GreenLanternAutoProtectFx;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.1 Ring Auto-Protection (user-approved): the Power Ring shields its bearer on its own when it senses danger. No
 * key at all -- five automatic saves, each with its own switch in the Shift+N suit screen (all on by default, stored as
 * the OFF bits of {@link GreenLanternState#autoProtectOff}):
 * <ul>
 * <li><b>Death Save</b> -- a hit that would kill the Lantern, or leave him under 3 hearts, is cancelled and a burst shell
 * stops every further hit for a second (bar the void and /kill). 300 Ring Charge, 60 s cooldown (a synced ability
 * cooldown, so the HUD can count it down). Never fires on an emptied ring: the spendable charge must cover it.</li>
 * <li><b>Fall Cushion</b> -- the ring's fall immunity (v0.11.6, free while the ring has charge) now belongs to this switch,
 * and just before a damaging landing a hard-light pad flashes under the Lantern and brakes him (20 + 2 a block fallen,
 * 10 s cooldown on the pad; the immunity itself has no cooldown). Not while flying.</li>
 * <li><b>Air Bubble</b> -- drowning or suffocating in a wall, a small atmosphere bubble goes over the head: the air refills
 * and the hit is stopped, for a few charge a second.</li>
 * <li><b>Heat Shell</b> -- in lava or on fire, fire and lava damage drop by 75% while the shell is up (a small upkeep).</li>
 * <li><b>Blind-Side Guard</b> -- a blow or projectile from outside the ~100 degree view cone loses 40% (3 s cooldown).</li>
 * </ul>
 * Runs for any bonded Green Lantern -- an active mutation does not switch it off (it only takes the keys). Registered on
 * {@code ALLOW_DAMAGE} straight after {@link GreenLanternDamage}, so the Z bubble / Dome soak first and the Death Save only
 * sees what would really land. Reduced hits use the mod's cancel-and-reapply pattern. Static state: cleared on server stop
 * ({@link #clearSessionState}, via {@code GreenLanternAbilityManager}) and with the Lantern's transient state
 * ({@link #onCleanup}, death / respawn / logout / dimension change / ring removal).
 */
public final class GreenLanternAutoProtect {
	/** OFF bits of {@link GreenLanternState#autoProtectOff}, in the suit screen's order. */
	public static final int MASTER = 1;
	public static final int DEATH_SAVE = 2;
	public static final int FALL_CUSHION = 4;
	public static final int AIR_BUBBLE = 8;
	public static final int HEAT_SHELL = 16;
	public static final int BLIND_SIDE = 32;
	/** Every switch, in the suit screen's order (master first). */
	public static final int[] SWITCHES = { MASTER, DEATH_SAVE, FALL_CUSHION, AIR_BUBBLE, HEAT_SHELL, BLIND_SIDE };
	/** Lang ids of {@link #SWITCHES} ({@code screen.projecthero.green_lantern_suit.auto.<id>}). */
	public static final String[] SWITCH_IDS = { "master", "death_save", "fall_cushion", "air_bubble", "heat_shell", "blind_side" };

	/** The Death Save's cooldown id in {@link GreenLanternState#abilityReadyAt} (synced, read by the HUD). */
	public static final String DEATH_SAVE_COOLDOWN = "gl_auto_death_save";

	/** What fired last, for tests. */
	public enum Save {
		DEATH, SHELL_BLOCK, FALL, CUSHION, AIR, HEAT, BLIND
	}

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.2f);
	private static final DustParticleOptions HEAT_DUST = new DustParticleOptions(new Vector3f(0.75f, 0.85f, 0.30f), 1.0f);

	/** Game time the Death Save shell stops blocking. */
	private static final Map<UUID, Long> SHELL_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CUSHION_READY_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> BLIND_READY_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> BUBBLE_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> HEAT_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Save> LAST = new ConcurrentHashMap<>();

	private GreenLanternAutoProtect() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GreenLanternAutoProtect::onAllowDamage);
	}

	public static void clearSessionState() {
		SHELL_UNTIL.clear();
		CUSHION_READY_AT.clear();
		BLIND_READY_AT.clear();
		BUBBLE_UNTIL.clear();
		HEAT_UNTIL.clear();
		LAST.clear();
	}

	public static void onCleanup(ServerPlayer player) {
		UUID id = player.getUUID();
		SHELL_UNTIL.remove(id);
		CUSHION_READY_AT.remove(id);
		BLIND_READY_AT.remove(id);
		BUBBLE_UNTIL.remove(id);
		HEAT_UNTIL.remove(id);
		LAST.remove(id);
		if (!GreenLanternAutoProtectFx.EMPTY.equals(fx(player))) {
			player.setAttached(ModAttachments.GREEN_LANTERN_AUTO_PROTECT_FX, GreenLanternAutoProtectFx.EMPTY);
		}
	}

	// ---------------------------------------------------------------- switches

	/** Whether {@code bit}'s save is on for this Lantern (the master switch must be on too). */
	public static boolean enabled(ServerPlayer player, int bit) {
		int off = GreenLantern.state(player).autoProtectOff;
		return (off & MASTER) == 0 && (off & bit) == 0;
	}

	/** The suit screen's switch: flips one save (or the master). */
	public static boolean toggle(ServerPlayer player, int bit) {
		if (!GreenLantern.hasPower(player)) {
			return false;
		}
		GreenLanternState c = GreenLantern.state(player).copy();
		c.autoProtectOff ^= bit;
		GreenLantern.save(player, c);
		return true;
	}

	public static void setEnabled(ServerPlayer player, int bit, boolean on) {
		GreenLanternState c = GreenLantern.state(player).copy();
		c.autoProtectOff = on ? c.autoProtectOff & ~bit : c.autoProtectOff | bit;
		GreenLantern.save(player, c);
	}

	/** The Fall Cushion owns the ring's fall-damage immunity ({@link GreenLanternDamage}). */
	public static boolean fallCushionOn(ServerPlayer player) {
		return enabled(player, FALL_CUSHION);
	}

	// ---------------------------------------------------------------- damage

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !GreenLantern.hasPower(player) || amount <= 0f
				|| player.isCreative() || player.isSpectator()
				|| com.projecthero.mod.greenlantern.mech.GreenLanternMech.isPiloting(player)) { // the mech takes the pilot's hits
			return true;
		}
		// the void and /kill always go through
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		// the Death Save's burst shell: nothing else lands while it is up
		Long shell = SHELL_UNTIL.get(id);
		if (shell != null && now < shell) {
			LAST.put(id, Save.SHELL_BLOCK);
			return false;
		}
		if ((GreenLantern.state(player).autoProtectOff & MASTER) != 0) {
			return true;
		}

		// ---- Air Bubble: drowning / suffocating in a wall
		if ((source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL)) && enabled(player, AIR_BUBBLE)
				&& GreenLanternEnergy.canSpend(player, GreenLanternConfig.AUTO_AIR_BUBBLE_PER_SEC)) {
			boolean fresh = !fx(player).has(GreenLanternAutoProtectFx.BUBBLE);
			if (fresh) {
				GreenLanternEnergy.spendRaw(player, GreenLanternConfig.AUTO_AIR_BUBBLE_PER_SEC);
			}
			startBubble(player, now);
			LAST.put(id, Save.AIR);
			return false;
		}

		// ---- the reductions (Heat Shell, Blind-Side Guard), then the Death Save looks at what is left
		float reduced = amount;
		if (source.is(DamageTypeTags.IS_FIRE) && enabled(player, HEAT_SHELL) && heatAffordable(player)) {
			startHeat(player, now);
			reduced *= 1f - GreenLanternConfig.AUTO_HEAT_SHELL_REDUCTION;
			LAST.put(id, Save.HEAT);
		}
		Vec3 from = blindSideFrom(player, source);
		if (from != null && enabled(player, BLIND_SIDE) && now >= BLIND_READY_AT.getOrDefault(id, 0L)) {
			BLIND_READY_AT.put(id, now + GreenLanternConfig.AUTO_BLIND_COOLDOWN_TICKS);
			reduced *= 1f - GreenLanternConfig.AUTO_BLIND_REDUCTION;
			blindSpark(player, from);
			GreenLanternRingVoice.say(player, GreenLanternRingVoice.Line.BLIND_SIDE);
			LAST.put(id, Save.BLIND);
		}

		// ---- Death Save
		if (enabled(player, DEATH_SAVE) && wouldDropBelow(player, source, reduced, GreenLanternConfig.AUTO_DEATH_SAVE_BELOW_HP)
				&& GreenLantern.abilityReady(player, DEATH_SAVE_COOLDOWN)
				&& GreenLanternEnergy.canSpend(player, GreenLanternConfig.AUTO_DEATH_SAVE_COST)) {
			deathSave(player, now);
			return false;
		}

		if (reduced < amount) {
			REENTRANT.set(true);
			try {
				player.hurt(source, reduced);
			} finally {
				REENTRANT.set(false);
			}
			return false;
		}
		return true;
	}

	/**
	 * Whether {@code amount} of {@code source} would leave the Lantern under {@code below} health (or kill him) once armour,
	 * Resistance, protection enchantments and absorption have had their say -- the same order {@code LivingEntity}
	 * applies them in.
	 */
	public static boolean wouldDropBelow(ServerPlayer player, DamageSource source, float amount, float below) {
		float dmg = amount;
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			dmg = CombatRules.getDamageAfterAbsorb(player, dmg, source, player.getArmorValue(),
					(float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		}
		if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
			MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
			if (res != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
				dmg = Math.max(dmg * (25 - (res.getAmplifier() + 1) * 5) / 25f, 0f);
			}
			if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
				float prot = EnchantmentHelper.getDamageProtection(player.serverLevel(), player, source);
				if (prot > 0f) {
					dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, prot);
				}
			}
		}
		float absorbed = Math.min(dmg, player.getAbsorptionAmount());
		float left = player.getHealth() - (dmg - absorbed);
		return left < below;
	}

	private static void deathSave(ServerPlayer player, long now) {
		GreenLanternEnergy.spendRaw(player, GreenLanternConfig.AUTO_DEATH_SAVE_COST);
		GreenLantern.triggerCooldown(player, DEATH_SAVE_COOLDOWN, GreenLanternConfig.AUTO_DEATH_SAVE_COOLDOWN_TICKS);
		SHELL_UNTIL.put(player.getUUID(), now + GreenLanternConfig.AUTO_DEATH_SAVE_SHELL_TICKS);
		LAST.put(player.getUUID(), Save.DEATH);
		player.resetFallDistance();
		setFx(player, fx(player).withShell(now));
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
		level.sendParticles(GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.7, 0.9, 0.7, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.6, 0.8, 0.6, 0.08);
		GreenLanternRingVoice.say(player, GreenLanternRingVoice.Line.SHIELD_ENGAGED);
	}

	/** Where a blow or projectile from outside the view cone came from (a point on that side), or null. */
	private static Vec3 blindSideFrom(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) {
			return null;
		}
		Entity direct = source.getDirectEntity();
		if (direct == null || direct == player) {
			return null;
		}
		Vec3 dir;
		if (direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE)) {
			// a projectile is already at the body when it lands -- it came from against its flight
			Vec3 v = direct.getDeltaMovement();
			dir = v.lengthSqr() > 1.0e-4 ? v.scale(-1) : direct.position().subtract(player.position());
		} else if (direct instanceof LivingEntity && direct == source.getEntity()) {
			dir = direct.position().subtract(player.position()); // melee
		} else {
			return null;
		}
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return null; // straight above / below: not a blind side
		}
		flat = flat.normalize();
		Vec3 look = player.getLookAngle();
		Vec3 lookFlat = new Vec3(look.x, 0, look.z);
		if (lookFlat.lengthSqr() < 1.0e-4) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			lookFlat = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		}
		lookFlat = lookFlat.normalize();
		double cosHalf = Math.cos(Math.toRadians(GreenLanternConfig.AUTO_BLIND_CONE_DEGREES / 2.0));
		if (flat.dot(lookFlat) >= cosHalf) {
			return null;
		}
		return player.position().add(0, player.getBbHeight() * 0.55, 0).add(flat.scale(0.6));
	}

	private static void blindSpark(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.12, 0.18, 0.12, 0.15);
		level.sendParticles(GREEN, at.x, at.y, at.z, 8, 0.15, 0.2, 0.15, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.6f);
	}

	// ---------------------------------------------------------------- per-player tick

	/** Per-Lantern tick, from {@code GreenLanternAbilityManager#serverTick}: the Fall Cushion pad and the shells' upkeep. */
	public static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		boolean master = (GreenLantern.state(player).autoProtectOff & MASTER) == 0;
		if (master && enabled(player, FALL_CUSHION)) {
			tickCushion(player, now);
		}
		GreenLanternAutoProtectFx fx = fx(player);
		// ---- Air Bubble: lingers while it is still needed, a few charge a second
		if (fx.has(GreenLanternAutoProtectFx.BUBBLE)) {
			// underwater the automatic Air Tank does the everyday job -- the bubble is the backstop when the air runs out
			boolean needed = player.isInWall() || (player.isUnderWater() && player.getAirSupply() <= 0);
			if (needed && master && enabled(player, AIR_BUBBLE)) {
				BUBBLE_UNTIL.merge(id, now + GreenLanternConfig.AUTO_AIR_BUBBLE_LINGER_TICKS, Math::max);
			}
			boolean keep = master && enabled(player, AIR_BUBBLE) && now < BUBBLE_UNTIL.getOrDefault(id, 0L);
			if (keep && player.tickCount % 20 == 0 && !GreenLanternEnergy.spendRaw(player, GreenLanternConfig.AUTO_AIR_BUBBLE_PER_SEC)) {
				keep = false;
			}
			if (keep) {
				if (player.isUnderWater()) {
					player.setAirSupply(player.getMaxAirSupply());
				}
			} else {
				BUBBLE_UNTIL.remove(id);
				setFx(player, fx(player).withFlag(GreenLanternAutoProtectFx.BUBBLE, false));
			}
		}
		// ---- Heat Shell: up while in lava / on fire (it raises itself too, not just on the first burn)
		boolean burning = player.isInLava() || player.isOnFire();
		if (burning && master && enabled(player, HEAT_SHELL) && heatAffordable(player)) {
			startHeat(player, now);
		}
		if (fx(player).has(GreenLanternAutoProtectFx.HEAT)) {
			boolean keep = master && enabled(player, HEAT_SHELL) && now < HEAT_UNTIL.getOrDefault(id, 0L);
			if (keep && player.tickCount % 20 == 0 && !GreenLanternEnergy.spendRaw(player, GreenLanternConfig.AUTO_HEAT_SHELL_PER_SEC)) {
				keep = false;
			}
			if (keep && player.tickCount % 4 == 0) {
				player.serverLevel().sendParticles(HEAT_DUST, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.6, 0.8, 0.6, 0.0);
			}
			if (!keep) {
				HEAT_UNTIL.remove(id);
				setFx(player, fx(player).withFlag(GreenLanternAutoProtectFx.HEAT, false));
			}
		}
	}

	private static void tickCushion(ServerPlayer player, long now) {
		if (player.onGround() || player.isFallFlying() || player.getAbilities().flying || GreenLanternFlight.isFlying(player)
				|| player.isInWater() || player.isPassenger()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		if (v.y > -0.3 || player.fallDistance <= safe) {
			return;
		}
		if (now < CUSHION_READY_AT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		Vec3 feet = player.position();
		double reach = GreenLanternConfig.AUTO_CUSHION_TRIGGER_BLOCKS + Math.abs(v.y);
		BlockHitResult hit = player.level().clip(new ClipContext(feet, feet.add(0, -reach, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK || !player.level().getFluidState(hit.getBlockPos()).isEmpty()) {
			return; // nothing close below yet -- or water, which lands softly anyway
		}
		cushion(player, now, (float) hit.getLocation().y);
	}

	/** The pad: flashes at {@code groundY}, brakes the fall and costs 20 + 2 a block fallen (if the ring can pay). */
	public static boolean cushion(ServerPlayer player, long now, float groundY) {
		float cost = 20f + 2f * Math.max(0f, player.fallDistance);
		if (!GreenLanternEnergy.spendRaw(player, cost)) {
			return false; // the ring's base fall immunity still cancels the damage while it has any charge
		}
		CUSHION_READY_AT.put(player.getUUID(), now + GreenLanternConfig.AUTO_CUSHION_COOLDOWN_TICKS);
		LAST.put(player.getUUID(), Save.CUSHION);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.6, Math.max(v.y, -0.3), v.z * 0.6);
		player.hurtMarked = true;
		setFx(player, fx(player).withCushion(now, groundY));
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), groundY, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
		level.playSound(null, player.getX(), groundY, player.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.6f, 1.4f);
		level.sendParticles(GREEN, player.getX(), groundY + 0.1, player.getZ(), 24, 0.8, 0.05, 0.8, 0.02);
		GreenLanternRingVoice.say(player, GreenLanternRingVoice.Line.CUSHION);
		return true;
	}

	private static boolean heatAffordable(ServerPlayer player) {
		return fx(player).has(GreenLanternAutoProtectFx.HEAT) || GreenLanternEnergy.canSpend(player, GreenLanternConfig.AUTO_HEAT_SHELL_PER_SEC);
	}

	private static void startHeat(ServerPlayer player, long now) {
		HEAT_UNTIL.merge(player.getUUID(), now + GreenLanternConfig.AUTO_HEAT_SHELL_LINGER_TICKS, Math::max);
		if (!fx(player).has(GreenLanternAutoProtectFx.HEAT)) {
			GreenLanternEnergy.spendRaw(player, GreenLanternConfig.AUTO_HEAT_SHELL_PER_SEC);
			setFx(player, fx(player).withFlag(GreenLanternAutoProtectFx.HEAT, true));
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
					SoundSource.PLAYERS, 0.5f, 1.8f);
			GreenLanternRingVoice.say(player, GreenLanternRingVoice.Line.HEAT_SHELL);
		}
	}

	private static void startBubble(ServerPlayer player, long now) {
		BUBBLE_UNTIL.merge(player.getUUID(), now + GreenLanternConfig.AUTO_AIR_BUBBLE_LINGER_TICKS, Math::max);
		player.setAirSupply(player.getMaxAirSupply());
		if (!fx(player).has(GreenLanternAutoProtectFx.BUBBLE)) {
			setFx(player, fx(player).withFlag(GreenLanternAutoProtectFx.BUBBLE, true));
			player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE,
					SoundSource.PLAYERS, 0.8f, 1.3f);
			GreenLanternRingVoice.say(player, GreenLanternRingVoice.Line.AIR_BUBBLE);
		}
	}

	// ---------------------------------------------------------------- fx

	public static GreenLanternAutoProtectFx fx(LivingEntity player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_AUTO_PROTECT_FX, GreenLanternAutoProtectFx.EMPTY);
	}

	private static void setFx(ServerPlayer player, GreenLanternAutoProtectFx fx) {
		if (!fx.equals(fx(player))) {
			player.setAttached(ModAttachments.GREEN_LANTERN_AUTO_PROTECT_FX, fx);
		}
	}

	// ---------------------------------------------------------------- tests / harness

	public static Save lastSave(ServerPlayer player) {
		return LAST.get(player.getUUID());
	}

	public static boolean shellUp(ServerPlayer player) {
		Long until = SHELL_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	/** Tests / harness: forget the Death Save cooldown, the shell and the small cooldowns. */
	public static void resetCooldowns(ServerPlayer player) {
		UUID id = player.getUUID();
		SHELL_UNTIL.remove(id);
		CUSHION_READY_AT.remove(id);
		BLIND_READY_AT.remove(id);
		LAST.remove(id);
		GreenLanternState c = GreenLantern.state(player).copy();
		c.abilityReadyAt.remove(DEATH_SAVE_COOLDOWN);
		GreenLantern.save(player, c);
	}
}
