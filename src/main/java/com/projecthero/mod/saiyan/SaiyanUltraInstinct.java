package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.kit.SaiyanKit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "Give Saiyans a secret Ultra Instinct transformation"): the hidden third form. Design and numbers:
 * {@code docs/SAIYAN_DESIGN.md}, "Ultra Instinct".
 *
 * <ul>
 *   <li><b>The secret unlock</b>, once per player, for good: in a fight with a <i>stronger foe</i> (a boss, or 1.5x his max
 *   health -- {@link Saiyan#strongFoe}), stay at or below {@link #LOW_HEALTH_SHARE} of max health for {@link #UNLOCK_TICKS} and
 *   take at least {@link #UNLOCK_HITS} hits there without dying. Healing above {@link #RESET_HEALTH_SHARE}, the fight ending
 *   or dying starts it over. The Guidebook only hints at it.</li>
 *   <li><b>Entering</b>: H while already a Super Saiyan (the same H that transforms; no new key), unlocked, not exhausted, with
 *   at least {@link #ENTER_KI} Ki. Base -> Super Saiyan -> Ultra Instinct.</li>
 *   <li><b>The form</b>: a silver aura, +{@link #SPEED} speed, +{@link #DAMAGE} damage on top of Super Saiyan's, and an
 *   <b>auto-dodge</b>: {@link #DODGE_MELEE} of melee hits and {@link #DODGE_PROJECTILE} of projectiles are sidestepped
 *   (at most one every {@link #DODGE_GAP} ticks), leaving a silver afterimage. A hit he lands within {@link #COUNTER_TICKS} of
 *   a dodge is a counter: +{@link #COUNTER_DAMAGE} more.</li>
 *   <li><b>The cost</b>: {@link #KI_DRAIN_PER_TICK} Ki a tick (3 a second, no Ki regen), at most {@link #UI_TICKS}; it ends at 0 Ki
 *   or on time, and takes Super Saiyan with it (its usual 60 s lockout). Then {@link #EXHAUSTED_TICKS} of Slowness II and
 *   Weakness I, and no Ultra Instinct for {@link #LOCKOUT_TICKS}.</li>
 * </ul>
 * Saved in the synced Saiyan state's {@code abilityReadyAt} map (keys below; the record codec is at its field limit). The unlock
 * progress and the counter windows are per-player statics, dropped by {@link #clearSessionState} (from
 * {@link Saiyan#clearSessionState}, which {@code ServerStateReset} calls) and per player by {@link #forget}.
 */
public final class SaiyanUltraInstinct {
	private static final int S = 20;

	// ---------------------------------------------------------------- the unlock
	public static final float LOW_HEALTH_SHARE = 0.25f;
	public static final float RESET_HEALTH_SHARE = 0.40f;
	public static final int UNLOCK_TICKS = 10 * S;
	public static final int UNLOCK_HITS = 5;

	// ---------------------------------------------------------------- the form
	public static final float ENTER_KI = 50f;
	public static final int UI_TICKS = 30 * S;
	public static final float KI_DRAIN_PER_TICK = 3f / S;
	public static final double SPEED = 0.40;
	public static final float DAMAGE = 0.25f;
	public static final float DODGE_MELEE = 0.60f;
	public static final float DODGE_PROJECTILE = 0.75f;
	public static final int DODGE_GAP = 6;
	public static final double DODGE_STEP = 1.6;
	public static final int COUNTER_TICKS = 2 * S;
	public static final float COUNTER_DAMAGE = 0.50f;
	public static final int EXHAUSTED_TICKS = 10 * S;
	public static final int LOCKOUT_TICKS = 3 * 60 * S;
	/** The afterimage and aura colour. */
	public static final int SILVER = 0xE8F0FF;

	// keys in SaiyanState.abilityReadyAt
	static final String K_UNLOCKED = "ui_unlocked";
	static final String K_SINCE = "ui_since";
	static final String K_UNTIL = "ui_until";
	static final String K_LOCK = "ui_lock";

	private static final ResourceLocation SPEED_ID = PowerToggles.id("saiyan_ui_speed");

	/** Unlock progress: {ticks low in a hard fight, hits taken there}. */
	private static final Map<UUID, int[]> PROGRESS = new ConcurrentHashMap<>();
	/** The last dodge's game time (the gap and the counter window). */
	private static final Map<UUID, Long> LAST_DODGE = new ConcurrentHashMap<>();
	/** Tests: a fixed roll (0..1) instead of the random one; null = random. */
	private static Float forcedRoll;

	private SaiyanUltraInstinct() {
	}

	static void clearSessionState() {
		PROGRESS.clear();
		LAST_DODGE.clear();
		forcedRoll = null;
	}

	static void forget(UUID id) {
		PROGRESS.remove(id);
		LAST_DODGE.remove(id);
	}

	/** Test hook: every dodge roll returns {@code roll} (null for random again). */
	public static void forceRoll(Float roll) {
		forcedRoll = roll;
	}

	private static long get(SaiyanState s, String key) {
		Long v = s == null ? null : s.abilityReadyAt.get(key);
		return v == null ? 0L : v;
	}

	// ---------------------------------------------------------------- client-safe reads

	public static boolean unlocked(Player p) {
		SaiyanState s = Saiyan.peek(p);
		return s != null && s.hasPower && get(s, K_UNLOCKED) > 0L;
	}

	public static boolean active(Player p) {
		SaiyanState s = Saiyan.peek(p);
		return s != null && s.hasPower && s.ssj && get(s, K_UNTIL) > p.level().getGameTime();
	}

	public static int ticksLeft(Player p) {
		SaiyanState s = Saiyan.peek(p);
		return !active(p) ? 0 : (int) Math.max(0L, get(s, K_UNTIL) - p.level().getGameTime());
	}

	public static int lockoutTicksLeft(Player p) {
		SaiyanState s = Saiyan.peek(p);
		return s == null ? 0 : (int) Math.max(0L, get(s, K_LOCK) - p.level().getGameTime());
	}

	/** Unlock progress for the tests: {ticks, hits}. */
	public static int[] progress(Player p) {
		int[] v = PROGRESS.get(p.getUUID());
		return v == null ? new int[2] : v.clone();
	}

	/** The extra damage share right now: the form's, and a counter's on top. */
	public static float damageBonus(Player p) {
		if (!active(p)) {
			return 0f;
		}
		Long d = LAST_DODGE.get(p.getUUID());
		long now = p.level().getGameTime();
		return DAMAGE + (d != null && now - d <= COUNTER_TICKS && now >= d ? COUNTER_DAMAGE : 0f);
	}

	// ---------------------------------------------------------------- the unlock

	/** Per tick (from {@link Saiyan#tick}). */
	static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		SaiyanState s = Saiyan.state(p);
		if (active(p)) {
			if (!p.getAbilities().instabuild) {
				s.ki = Math.max(0f, s.ki - KI_DRAIN_PER_TICK); // in place, like the core's own Ki tick
			}
			if (s.ki <= 0f || get(s, K_UNTIL) - now <= 1L) {
				end(p);
			}
			return;
		}
		if (get(s, K_UNTIL) > 0L) {
			end(p); // Super Saiyan ended under it (a death, the command): tidy up
			return;
		}
		if (unlocked(p)) {
			return;
		}
		float max = p.getMaxHealth();
		int[] pr = PROGRESS.get(p.getUUID());
		if (!p.isAlive() || !Saiyan.strongFoe(p) || p.getHealth() > max * RESET_HEALTH_SHARE) {
			if (pr != null) {
				PROGRESS.remove(p.getUUID());
			}
			return;
		}
		if (p.getHealth() <= max * LOW_HEALTH_SHARE) {
			pr = PROGRESS.computeIfAbsent(p.getUUID(), k -> new int[2]);
			pr[0]++;
			if (pr[0] >= UNLOCK_TICKS && pr[1] >= UNLOCK_HITS) {
				unlock(p);
			}
		}
	}

	/** He took a hit (from {@link Saiyan#onHurt}): counts toward the unlock while low in a hard fight. */
	static void onHurt(ServerPlayer p) {
		if (unlocked(p) || !Saiyan.strongFoe(p) || p.getHealth() > p.getMaxHealth() * LOW_HEALTH_SHARE) {
			return;
		}
		PROGRESS.computeIfAbsent(p.getUUID(), k -> new int[2])[1]++;
	}

	/** The secret is found: once, for good. */
	public static void unlock(ServerPlayer p) {
		SaiyanState n = Saiyan.state(p).copy();
		n.abilityReadyAt.put(K_UNLOCKED, 1L);
		Saiyan.save(p, n);
		PROGRESS.remove(p.getUUID());
		ServerLevel level = p.serverLevel();
		level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SaiyanSounds.KI_CHARGE_START, SoundSource.PLAYERS, 0.8f, 1.35f);
		level.sendParticles(Saiyan.dust(0xFF000000 | SILVER, 1.4f), p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.9, 0.5, 0.0);
		p.displayClientMessage(Component.translatable("message.projecthero.saiyan.ui_unlocked").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), false);
		p.displayClientMessage(Component.translatable("message.projecthero.saiyan.ui_unlocked_hint").withStyle(ChatFormatting.GRAY), false);
	}

	// ---------------------------------------------------------------- the form

	/** Why H can't go Ultra Instinct now (null = it may). Only asked while he is a Super Saiyan. */
	public static String refusal(ServerPlayer p) {
		if (!unlocked(p)) {
			return "message.projecthero.saiyan.already_ssj";
		}
		if (active(p)) {
			return "message.projecthero.saiyan.ui_already";
		}
		if (lockoutTicksLeft(p) > 0) {
			return "message.projecthero.saiyan.ui_exhausted";
		}
		if (Saiyan.ki(p) + 1.0e-3f < ENTER_KI && !p.getAbilities().instabuild) {
			return "message.projecthero.saiyan.ui_no_ki";
		}
		return null;
	}

	/** H as a Super Saiyan: Ultra Instinct. True if he entered it. */
	public static boolean enter(ServerPlayer p) {
		String why = refusal(p);
		if (why != null) {
			Saiyan.say(p, why, ChatFormatting.GRAY, (lockoutTicksLeft(p) + 19) / 20, (int) ENTER_KI);
			return false;
		}
		long now = p.level().getGameTime();
		SaiyanState n = Saiyan.state(p).copy();
		n.abilityReadyAt.put(K_SINCE, now);
		n.abilityReadyAt.put(K_UNTIL, now + UI_TICKS);
		n.formSince = now; // the transformation burst plays again, silver
		n.charging = false;
		Saiyan.save(p, n);
		Saiyan.reconcile(p);
		ServerLevel level = p.serverLevel();
		level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.9f, 1.2f);
		level.sendParticles(Saiyan.dust(0xFF000000 | SILVER, 2.0f), p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.6, 1.0, 0.6, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.5, 0.9, 0.5, 0.05);
		p.displayClientMessage(Component.translatable(SaiyanForm.ULTRA.nameKey()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
		return true;
	}

	/** Ultra Instinct ends (time, Ki, death): Super Saiyan goes with it, then the exhaustion and the lockout. */
	public static void end(ServerPlayer p) {
		SaiyanState s = Saiyan.state(p);
		boolean was = get(s, K_UNTIL) > 0L;
		if (!was) {
			return;
		}
		long now = p.level().getGameTime();
		SaiyanState n = s.copy();
		n.abilityReadyAt.remove(K_SINCE);
		n.abilityReadyAt.remove(K_UNTIL);
		n.abilityReadyAt.put(K_LOCK, now + LOCKOUT_TICKS);
		Saiyan.save(p, n);
		LAST_DODGE.remove(p.getUUID());
		if (s.ssj) {
			Saiyan.endSsj(p);
		}
		Saiyan.reconcile(p);
		if (p.isAlive()) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTED_TICKS, 1, false, true));
			p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUSTED_TICKS, 0, false, true));
			p.displayClientMessage(Component.translatable("message.projecthero.saiyan.ui_over").withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** Command / test hook: lifts the post-Ultra-Instinct lockout. */
	public static void clearLockout(ServerPlayer p) {
		SaiyanState n = Saiyan.state(p).copy();
		n.abilityReadyAt.remove(K_LOCK);
		Saiyan.save(p, n);
	}

	/** Death: the form ends quietly (no exhaustion on the new body), the lockout still starts. */
	static void onRespawn(ServerPlayer p) {
		SaiyanState s = Saiyan.state(p);
		if (get(s, K_UNTIL) > 0L) {
			SaiyanState n = s.copy();
			n.abilityReadyAt.remove(K_SINCE);
			n.abilityReadyAt.remove(K_UNTIL);
			n.abilityReadyAt.put(K_LOCK, p.level().getGameTime() + LOCKOUT_TICKS);
			Saiyan.save(p, n);
		}
		forget(p.getUUID());
	}

	/** From {@link Saiyan#reconcile}: the form's speed. */
	static void reconcile(ServerPlayer p, boolean hasPower) {
		if (hasPower && active(p)) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
		}
	}

	// ---------------------------------------------------------------- the auto-dodge

	/** A melee swing or a projectile -- what the body can sidestep. Explosions, fire, falls, magic and the like can't be. */
	public static boolean dodgeable(DamageSource source, Entity victim) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof Projectile) {
			return true;
		}
		return direct instanceof LivingEntity && direct != victim && direct == source.getEntity();
	}

	/**
	 * From {@code SaiyanDamage.allowDamage}: true if the hit is sidestepped (cancelled). He steps {@link #DODGE_STEP} blocks to
	 * one side of the attack's line (whichever side is free), leaving a silver afterimage where he stood.
	 */
	public static boolean tryDodge(ServerPlayer p, DamageSource source) {
		if (!active(p) || !p.isAlive() || !dodgeable(source, p)) {
			return false;
		}
		long now = p.level().getGameTime();
		Long last = LAST_DODGE.get(p.getUUID());
		if (last != null && now - last < DODGE_GAP && now >= last) {
			return false;
		}
		boolean projectile = source.getDirectEntity() instanceof Projectile;
		float roll = forcedRoll != null ? forcedRoll : p.getRandom().nextFloat();
		if (roll >= (projectile ? DODGE_PROJECTILE : DODGE_MELEE)) {
			return false;
		}
		LAST_DODGE.put(p.getUUID(), now);
		Vec3 from = p.position();
		Vec3 to = sidestep(p, source);
		if (to != null) {
			p.teleportTo(to.x, to.y, to.z);
			p.resetFallDistance();
		}
		SaiyanKit.ghost(p, from, to != null ? to : from, SILVER);
		ServerLevel level = p.serverLevel();
		level.playSound(null, from.x, from.y + 1.0, from.z, SaiyanSounds.UI_DODGE, SoundSource.PLAYERS, 0.5f, 1.0f + p.getRandom().nextFloat() * 0.1f);
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 1.0, from.z, 6, 0.25, 0.5, 0.25, 0.02);
		if (source.getDirectEntity() instanceof Projectile proj) {
			proj.discard(); // the arrow / fireball flies on past where he was -- simplest: it is gone
		}
		return true;
	}

	/** Where the sidestep lands: perpendicular to the attack's line, the free side (left or right at random first), or null. */
	static Vec3 sidestep(ServerPlayer p, DamageSource source) {
		Vec3 line;
		Entity direct = source.getDirectEntity();
		if (direct instanceof Projectile proj && proj.getDeltaMovement().lengthSqr() > 1.0e-4) {
			line = proj.getDeltaMovement();
		} else if (direct != null) {
			line = p.position().subtract(direct.position());
		} else {
			line = p.getLookAngle();
		}
		line = new Vec3(line.x, 0, line.z);
		if (line.lengthSqr() < 1.0e-6) {
			line = new Vec3(0, 0, 1);
		}
		line = line.normalize();
		Vec3 side = new Vec3(-line.z, 0, line.x);
		int first = p.getRandom().nextBoolean() ? 1 : -1;
		for (double step : new double[] { DODGE_STEP, DODGE_STEP * 0.6 }) {
			for (int k : new int[] { first, -first }) {
				Vec3 at = p.position().add(side.scale(step * k));
				AABB box = p.getBoundingBox().move(at.subtract(p.position()));
				if (p.level().noCollision(p, box) && !p.level().getBlockState(net.minecraft.core.BlockPos.containing(at).below()).isAir()
						|| p.level().noCollision(p, box) && !p.onGround()) {
					return at;
				}
			}
		}
		return null;
	}
}
