package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.moves.SaiyanMoves;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API of the Saiyan, the v0.17.3 Hero-Tier power (Wave 1: the core). Nothing else changes
 * {@link SaiyanState}. The moves live in {@code saiyan.moves.SaiyanMoves}, the hair and aura on the client.
 *
 * <h2>Ki</h2>
 * 0..100. Hold Q to charge it (12 a second; you stand and can't attack). Transforming costs Ki, and from Super Saiyan 2 up
 * every form drains it. At 0 you drop back to Base, exhausted for 5 seconds (slowed, no transforming). Base and Super Saiyan
 * trickle it back on their own. Flight costs a little.
 *
 * <h2>Forms</h2>
 * {@link SaiyanForm}: H opens the form wheel. Each form unlocks by time spent in the one before it (mastery), or all at once
 * with the gamerule {@code projectheroSaiyanAllForms}. Blue Overdrive only from Blue; Instinct only at 35% health or less.
 * Shift+V powers down.
 */
public final class Saiyan {
	public static final String KEY = "saiyan";
	public static final String RULE_ALL_FORMS = "projectheroSaiyanAllForms";
	public static GameRules.Key<GameRules.BooleanValue> ALL_FORMS;

	private static final ResourceLocation ATTACK_ID = PowerToggles.id("saiyan_attack");
	private static final ResourceLocation HEALTH_ID = PowerToggles.id("saiyan_health");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("saiyan_speed");
	private static final ResourceLocation CHARGE_ID = PowerToggles.id("saiyan_charge");
	private static final ResourceLocation EXHAUST_ID = PowerToggles.id("saiyan_exhaust");

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private Saiyan() {
	}

	static void registerRules() {
		ALL_FORMS = GameRuleRegistry.register(RULE_ALL_FORMS, GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		SaiyanFlight.clearSessionState();
		SaiyanAbilityManager.clearSessionState();
		SaiyanDamage.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		SaiyanFlight.forget(id);
		SaiyanAbilityManager.forget(id);
		SaiyanDamage.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static SaiyanState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SAIYAN_STATE);
	}

	static void save(ServerPlayer player, SaiyanState state) {
		player.setAttached(ModAttachments.SAIYAN_STATE, state);
	}

	public static SaiyanState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.SAIYAN_STATE, null);
	}

	public static boolean hasPower(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower;
	}

	/** The form the player is in right now (Base without the power). Client-safe. */
	public static SaiyanForm form(Player player) {
		SaiyanState s = peek(player);
		return s == null || !s.hasPower ? SaiyanForm.BASE : s.form();
	}

	public static float ki(Player player) {
		SaiyanState s = peek(player);
		return s == null ? 0f : s.ki;
	}

	public static float kiMax(Player player) {
		return SaiyanConfig.KI_MAX;
	}

	/** Q is held (the charge pose and aura). Client-safe. */
	public static boolean charging(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.charging;
	}

	public static boolean isFlying(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	/** Exhausted after running out of Ki. Client-safe. */
	public static boolean exhausted(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.exhaustedUntil > player.level().getGameTime();
	}

	/**
	 * Ticks since the player last changed form (the transformation burst plays over the first
	 * {@link SaiyanConfig#TRANSFORM_BURST_TICKS}); {@link Integer#MAX_VALUE} without the power or before any change.
	 */
	public static int transformingTicks(Player player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower || s.formSince <= 0L) {
			return Integer.MAX_VALUE;
		}
		long d = player.level().getGameTime() - s.formSince;
		return d < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, d);
	}

	/** The form is unlocked (by mastery or the gamerule). Server: reads the gamerule; client: the synced mask only. */
	public static boolean unlocked(Player player, SaiyanForm f) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower) {
			return false;
		}
		if (f == SaiyanForm.BASE || f == SaiyanForm.SSJ) {
			return true;
		}
		if (player.level() instanceof ServerLevel level && ALL_FORMS != null && level.getGameRules().getBoolean(ALL_FORMS)) {
			return true;
		}
		return (s.unlocked & (1 << f.ordinal())) != 0;
	}

	/** How far the next locked form along the chain has got, 0..1, and which form that is (null = everything unlocked). */
	public static SaiyanForm nextLocked(Player player) {
		for (SaiyanForm f : SaiyanForm.values()) {
			if (f.masteredFrom() != null && !unlocked(player, f)) {
				return f;
			}
		}
		return null;
	}

	public static float masteryProgress(Player player, SaiyanForm target) {
		SaiyanState s = peek(player);
		if (s == null || target == null || target.masteredFrom() == null || target.masteryTicks() <= 0) {
			return 1f;
		}
		return Math.min(1f, s.masteryOf(target.masteredFrom()) / (float) target.masteryTicks());
	}

	public static int cooldownRemaining(Player player, String id) {
		SaiyanState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		SaiyanState n = state(player).copy();
		n.abilityReadyAt.put(id, player.level().getGameTime() + ticks);
		save(player, n);
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- Ki

	/** Spends Ki if there is enough. False (and nothing spent) if not. */
	public static boolean spendKi(ServerPlayer player, float amount) {
		SaiyanState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild) {
			return true;
		}
		if (s.ki + 1.0e-3f < amount) {
			say(player, "message.projecthero.saiyan.no_ki", ChatFormatting.GRAY);
			return false;
		}
		SaiyanState n = s.copy();
		n.ki = Math.max(0f, s.ki - amount);
		save(player, n);
		return true;
	}

	public static void setKi(ServerPlayer player, float value) {
		SaiyanState n = state(player).copy();
		n.ki = Math.max(0f, Math.min(SaiyanConfig.KI_MAX, value));
		save(player, n);
	}

	/** Q pressed / let go. */
	public static void setCharging(ServerPlayer player, boolean on) {
		SaiyanState s = state(player);
		if (!s.hasPower || s.charging == on) {
			return;
		}
		if (on && (!player.isAlive() || player.isSpectator())) {
			return;
		}
		SaiyanState n = s.copy();
		n.charging = on;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		if (on) {
			// the power-up swell; the loop that follows while Q is held is the client's (SaiyanLoopSounds)
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SaiyanSounds.KI_CHARGE_START, SoundSource.PLAYERS, 0.7f, 1.0f);
		}
	}

	// ---------------------------------------------------------------- the power

	/** The serum, a random roll or the command. False if the player already holds it (or someone else does). */
	public static boolean grant(ServerPlayer player) {
		SaiyanState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		SaiyanState s = new SaiyanState();
		s.hasPower = true;
		s.ki = SaiyanConfig.KI_MAX;
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.8f, 1.1f);
		level.sendParticles(dust(SaiyanForm.SSJ.auraColor, 2.0f), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.saiyan.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.saiyan.subtitle").withStyle(ChatFormatting.YELLOW)));
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		SaiyanFlight.stop(player);
		save(player, new SaiyanState());
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the charge and the flight end. The form, the bar and the mastery stay. */
	public static void clearTransient(ServerPlayer player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		if (s.charging) {
			SaiyanState n = s.copy();
			n.charging = false;
			save(player, n);
		}
		SaiyanFlight.stop(player);
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		if (!hasPower(player)) {
			return;
		}
		// a death ends any form: back in Base with a full bar
		SaiyanState n = state(player).copy();
		n.form = SaiyanForm.BASE.ordinal();
		n.charging = false;
		n.flying = false;
		n.ki = SaiyanConfig.KI_MAX;
		n.exhaustedUntil = 0L;
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- forms

	/** Why a transformation into {@code target} is refused right now (null = it may go ahead). */
	public static String refusal(ServerPlayer player, SaiyanForm target) {
		SaiyanState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return "message.projecthero.saiyan.cant";
		}
		if (target == s.form()) {
			return "message.projecthero.saiyan.already_form";
		}
		if (target == SaiyanForm.BASE) {
			return null;
		}
		if (!unlocked(player, target)) {
			return "message.projecthero.saiyan.locked";
		}
		long now = player.level().getGameTime();
		if (s.exhaustedUntil > now) {
			return "message.projecthero.saiyan.exhausted";
		}
		if (now - s.lastTransformAt < SaiyanConfig.TRANSFORM_COOLDOWN && now >= s.lastTransformAt) {
			return "message.projecthero.saiyan.too_soon";
		}
		if (target == SaiyanForm.BLUE_OVERDRIVE && s.form() != SaiyanForm.BLUE) {
			return "message.projecthero.saiyan.overdrive_from_blue";
		}
		if (target == SaiyanForm.INSTINCT) {
			if (player.getHealth() > player.getMaxHealth() * SaiyanConfig.INSTINCT_HEALTH) {
				return "message.projecthero.saiyan.instinct_health";
			}
			if (s.ki < SaiyanConfig.INSTINCT_KI && !player.getAbilities().instabuild) {
				return "message.projecthero.saiyan.instinct_ki";
			}
		}
		if (s.ki + 1.0e-3f < target.cost && !player.getAbilities().instabuild) {
			return "message.projecthero.saiyan.no_ki";
		}
		return null;
	}

	/** The form wheel's pick (or the command). True if the form changed. */
	public static boolean transform(ServerPlayer player, SaiyanForm target) {
		if (target == SaiyanForm.BASE) {
			return powerDown(player);
		}
		String why = refusal(player, target);
		if (why != null) {
			say(player, why, ChatFormatting.GRAY, Component.translatable(target.nameKey()));
			return false;
		}
		SaiyanState s = state(player);
		long now = player.level().getGameTime();
		SaiyanState n = s.copy();
		if (!player.getAbilities().instabuild) {
			n.ki = Math.max(0f, s.ki - target.cost);
		}
		n.form = target.ordinal();
		n.formSince = now;
		n.lastTransformAt = now;
		save(player, n);
		reconcile(player);
		burst(player, target);
		player.displayClientMessage(Component.translatable(target.nameKey()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		return true;
	}

	/** Shift+V, the wheel's Base, running out of Ki: back to Base. */
	public static boolean powerDown(ServerPlayer player) {
		SaiyanState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (s.form() == SaiyanForm.BASE) {
			say(player, "message.projecthero.saiyan.already_base", ChatFormatting.GRAY);
			return false;
		}
		long now = player.level().getGameTime();
		SaiyanState n = s.copy();
		n.form = SaiyanForm.BASE.ordinal();
		n.formSince = now;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.POWER_DOWN, SoundSource.PLAYERS, 0.8f, 1.0f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
		return true;
	}

	/**
	 * The transformation's sound. The flash itself (star rays, a light pillar, a shockwave ring, ground debris) is drawn by every
	 * client from {@link #transformingTicks} ({@code client.saiyan.aura.SaiyanAuraRenderer} / {@code SaiyanAuraParticles}).
	 */
	private static void burst(ServerPlayer player, SaiyanForm f) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		// one burst for every form, pitched up a little the higher the form; Super Saiyan 2's lightning crackles on top
		level.playSound(null, c.x, c.y + 1.0, c.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.8f, 0.95f + f.ordinal() * 0.02f);
		if (f == SaiyanForm.SSJ2) {
			level.playSound(null, c.x, c.y + 1.0, c.z, SaiyanSounds.SSJ2_SPARK, SoundSource.PLAYERS, 0.7f, 1.0f);
		}
	}

	static DustParticleOptions dust(int argb, float size) {
		return new DustParticleOptions(new Vector3f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f), size);
	}

	/** Ki ran out in a form: back to Base, exhausted. */
	private static void exhaust(ServerPlayer player) {
		long now = player.level().getGameTime();
		SaiyanState n = state(player).copy();
		n.form = SaiyanForm.BASE.ordinal();
		n.formSince = now;
		n.ki = 0f;
		n.exhaustedUntil = now + SaiyanConfig.EXHAUST_TICKS;
		save(player, n);
		SaiyanFlight.stop(player);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.EXHAUSTED, SoundSource.PLAYERS, 0.8f, 1.0f);
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.3, 0.4, 0.3, 0.01);
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.exhausted_now").withStyle(ChatFormatting.RED), true);
	}

	/** Unlocks every form whose mastery is met; tells the player. */
	private static void checkUnlocks(ServerPlayer player, SaiyanState s) {
		if (ALL_FORMS != null && ((ServerLevel) player.level()).getGameRules().getBoolean(ALL_FORMS)) {
			s.unlocked |= (1 << SaiyanForm.values().length) - 1; // the gamerule: every form (synced, so the wheel shows them open)
			return;
		}
		for (SaiyanForm f : SaiyanForm.values()) {
			int bit = 1 << f.ordinal();
			if ((s.unlocked & bit) != 0) {
				continue;
			}
			boolean met;
			if (f == SaiyanForm.INSTINCT) {
				met = (s.unlocked & (1 << SaiyanForm.BLUE.ordinal())) != 0;
			} else if (f.masteredFrom() == null) {
				continue;
			} else {
				met = s.masteryOf(f.masteredFrom()) >= f.masteryTicks();
			}
			if (met) {
				s.unlocked |= bit;
				player.displayClientMessage(Component.translatable("message.projecthero.saiyan.unlocked", Component.translatable(f.nameKey()))
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
			}
		}
	}

	/** {@code /projecthero power tune saiyan unlock_all}: every form, by mask (independent of the gamerule). */
	public static void unlockAll(ServerPlayer player) {
		SaiyanState n = state(player).copy();
		for (SaiyanForm f : SaiyanForm.values()) {
			n.unlocked |= 1 << f.ordinal();
		}
		save(player, n);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, CHARGE_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, EXHAUST_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		SaiyanForm f = s.form();
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, f.attackBonus, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, f.healthBonus, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, f.speedMult() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (s.charging) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, CHARGE_ID, SaiyanConfig.CHARGE_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, CHARGE_ID);
		}
		if (s.exhaustedUntil > player.level().getGameTime()) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, EXHAUST_ID, SaiyanConfig.EXHAUST_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, EXHAUST_ID);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		SaiyanState s = player.getAttachedOrElse(ModAttachments.SAIYAN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		SaiyanForm f = s.form();
		boolean changed = false;
		// mastery: time in the current form (the unlock check runs every second, and every tick in a form)
		if (f != SaiyanForm.BASE && player.isAlive()) {
			s.mastery.set(f.ordinal(), s.masteryOf(f) + 1);
		}
		if (f != SaiyanForm.BASE || now % 20 == 0) {
			int before = s.unlocked;
			checkUnlocks(player, s);
			changed = before != s.unlocked;
		}
		// Ki
		float ki = s.ki;
		boolean creative = player.getAbilities().instabuild;
		if (s.charging) {
			ki += SaiyanConfig.KI_CHARGE_PER_TICK;
		} else if (f.kiDrainPerTick() <= 0f && !s.flying) {
			ki += SaiyanConfig.KI_REGEN_PER_TICK;
		}
		if (!creative) {
			ki -= f.kiDrainPerTick();
			if (s.flying) {
				ki -= SaiyanConfig.KI_FLIGHT_PER_TICK;
			}
		}
		ki = Math.max(0f, Math.min(SaiyanConfig.KI_MAX, ki));
		s.ki = ki;
		if (ki <= 0f && f != SaiyanForm.BASE) {
			exhaust(player);
			s = state(player);
			f = s.form();
		} else if (ki <= 0f && s.flying) {
			SaiyanFlight.stop(player);
			s = state(player);
		}
		// Blue Overdrive burns; God and Blue heal
		if (f == SaiyanForm.BLUE_OVERDRIVE && !creative && (now - s.formSince) > 0 && (now - s.formSince) % SaiyanConfig.OVERDRIVE_SELF_DAMAGE_INTERVAL == 0) {
			overdriveBurn(player);
			s = state(player);
			f = s.form();
		}
		if (f.regenerates && player.isAlive() && now % SaiyanConfig.REGEN_INTERVAL == 0 && player.getHealth() < player.getMaxHealth()) {
			player.heal(SaiyanConfig.REGEN_AMOUNT);
		}
		if (s.charging && player.tickCount % 2 == 0) {
			chargeFx(player, f);
		}
		if (player.tickCount % 20 == 0 || (s.exhaustedUntil > 0 && s.exhaustedUntil == now)) {
			reconcile(player);
		}
		SaiyanFlight.tick(player);
		SaiyanMoves.tick(player);
		if (changed || now % SaiyanConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}

	/** Overdrive's cost: 1 HP; at the floor it drops back to Blue by itself. */
	public static void overdriveBurn(ServerPlayer player) {
		float h = player.getHealth();
		if (h <= SaiyanConfig.OVERDRIVE_FLOOR + SaiyanConfig.OVERDRIVE_SELF_DAMAGE) {
			SaiyanState n = state(player).copy();
			n.form = SaiyanForm.BLUE.ordinal();
			n.formSince = player.level().getGameTime();
			save(player, n);
			reconcile(player);
			player.displayClientMessage(Component.translatable("message.projecthero.saiyan.overdrive_drop").withStyle(ChatFormatting.RED), true);
			return;
		}
		player.setHealth(h - SaiyanConfig.OVERDRIVE_SELF_DAMAGE);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(dust(0xFFFF2A2A, 1.4f), player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.35, 0.6, 0.35, 0.0);
	}

	/** The charge's look is the client's aura (bigger flame, ground glow, floating debris: {@code client.saiyan.aura}); nothing to send. */
	private static void chargeFx(ServerPlayer player, SaiyanForm f) {
	}
}
