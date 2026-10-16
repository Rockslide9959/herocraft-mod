package com.projecthero.mod.bluebeetle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Blue Beetle (Jaime Reyes), the v0.16.5 Hero-Tier power. Nothing else touches
 * {@link BlueBeetleState}; every change re-saves through {@link ServerPlayer#setAttached}. Moves in
 * {@link BlueBeetleAbilities}, damage rules (Adaptation, the shield, the bubble) in {@link BlueBeetleDamage}, Khaji-Da's
 * lines in {@link KhajiDa}, numbers in {@link BlueBeetleConfig}.
 *
 * <h2>How it works</h2>
 * The Scarab (desert pyramid suspicious sand) latches onto the player's spine: a 5 s bonding as the armour builds itself
 * texel by texel outward from the Scarab. <b>H</b> suits up or down (the same build, reversed to take it off). Suited:
 * diamond-level armour, +9 melee, no fall damage, night vision, Adaptation, flight (double-tap jump), the six keys.
 * Unsuited, Khaji-Da still warns him about hostiles nearby.
 *
 * <h2>Scarab Energy</h2>
 * 0..100, refilling 3 a second (twice that standing still); flying drains 2 a second. Khaji-Da Unleashed makes every
 * move free for 15 s, then the bar refills at half speed for 30 s.
 */
public final class BlueBeetle {
	public static final String KEY = "blue_beetle";

	private static final ResourceLocation ARMOR_ID = PowerToggles.id("blue_beetle_armor");
	private static final ResourceLocation TOUGHNESS_ID = PowerToggles.id("blue_beetle_toughness");
	private static final ResourceLocation MELEE_ID = PowerToggles.id("blue_beetle_melee");
	private static final ResourceLocation BLADE_ID = PowerToggles.id("blue_beetle_blade");

	public static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.15f, 0.55f, 1.0f), 1.2f);
	public static final DustParticleOptions BLUE_BIG = new DustParticleOptions(new Vector3f(0.3f, 0.7f, 1.0f), 2.2f);
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.15f), 1.3f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** Players the suit lent flight to (so exactly that is taken back). */
	private static final java.util.Set<UUID> FLIGHT_GRANTED = ConcurrentHashMap.newKeySet();

	private BlueBeetle() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		LAST_POS.clear();
		FLIGHT_GRANTED.clear();
		BlueBeetleAbilities.clearSessionState();
		BlueBeetleAbilityManager.clearSessionState();
		BlueBeetleDamage.clearSessionState();
		KhajiDa.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static BlueBeetleState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.BLUE_BEETLE_STATE);
	}

	static void save(ServerPlayer player, BlueBeetleState state) {
		player.setAttached(ModAttachments.BLUE_BEETLE_STATE, state);
	}

	private static BlueBeetleState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
	}

	public static boolean hasPower(Player player) {
		BlueBeetleState s = peek(player);
		return s != null && s.hasPower;
	}

	public static boolean suited(Player player) {
		BlueBeetleState s = peek(player);
		return s != null && s.hasPower && s.suited;
	}

	public static float energy(Player player) {
		BlueBeetleState s = peek(player);
		return s == null ? 0f : s.energy;
	}

	public static boolean unleashed(Player player) {
		BlueBeetleState s = peek(player);
		return s != null && s.suited && s.unleashedUntil > player.level().getGameTime();
	}

	public static boolean shieldUp(Player player) {
		BlueBeetleState s = peek(player);
		return s != null && s.suited && s.fx.shield;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		BlueBeetleState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	public static float damageMultiplier(Player player) {
		return unleashed(player) ? BlueBeetleConfig.UNLEASH_DAMAGE : 1f;
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

	// ---------------------------------------------------------------- energy

	/** Spends energy (free while Khaji-Da is unleashed). */
	public static boolean spendEnergy(ServerPlayer player, float cost) {
		if (cost <= 0f || unleashed(player)) {
			return true;
		}
		BlueBeetleState s = state(player);
		if (s.energy + 1.0e-3f < cost) {
			return false;
		}
		BlueBeetleState n = s.copy();
		n.energy = Math.max(0f, Math.min(BlueBeetleConfig.ENERGY_MAX, s.energy) - cost);
		save(player, n);
		return true;
	}

	public static void setEnergy(ServerPlayer player, float value) {
		BlueBeetleState n = state(player).copy();
		n.energy = Math.max(0f, Math.min(BlueBeetleConfig.ENERGY_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The Scarab / the command. False if the player already carries Khaji-Da. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		long now = player.level().getGameTime();
		BlueBeetleState s = new BlueBeetleState();
		s.hasPower = true;
		s.energy = BlueBeetleConfig.ENERGY_MAX;
		s.suited = true;
		s.suitChangeAt = now;
		s.animId = BlueBeetleState.ANIM_BOND;
		s.animStart = now;
		save(player, s);
		reconcile(player);
		// v0.16.5 (user): no blue glow -- the armour just builds itself out from the Scarab, texel by texel
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.2, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5f, 1.6f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.blue_beetle.title").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.blue_beetle.subtitle").withStyle(ChatFormatting.AQUA)));
		player.displayClientMessage(Component.translatable("message.projecthero.blue_beetle.acquired")
				.withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.blue_beetle.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		KhajiDa.speak(player, KhajiDa.Topic.BOND);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		BlueBeetleAbilities.clear(player);
		landWings(player);
		save(player, new BlueBeetleState());
		reconcile(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		LAST_POS.remove(id);
		BlueBeetleAbilityManager.forget(id);
		KhajiDa.forget(id);
	}

	// ---------------------------------------------------------------- the suit (H)

	/** The bonding (5 s) or a suit-up still building. */
	public static boolean changing(Player player) {
		BlueBeetleState s = peek(player);
		if (s == null || !s.hasPower || !s.suited) {
			return false;
		}
		long age = player.level().getGameTime() - s.suitChangeAt;
		int len = s.animId == BlueBeetleState.ANIM_BOND ? BlueBeetleConfig.BOND_TICKS : BlueBeetleConfig.SUIT_UP_TICKS;
		return age >= 0 && age < len;
	}

	public static void toggleSuit(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator() || changing(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < BlueBeetleConfig.TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.suited) {
			suitDown(player);
		} else {
			suitUp(player);
		}
	}

	public static void suitUp(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (!s.hasPower || s.suited) {
			return;
		}
		long now = player.level().getGameTime();
		BlueBeetleState n = s.copy();
		n.suited = true;
		n.suitChangeAt = now;
		n.animId = BlueBeetleState.ANIM_SUIT_UP;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.2, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.4f);
		KhajiDa.speak(player, KhajiDa.Topic.SUIT_UP);
	}

	public static void suitDown(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (!s.suited) {
			return;
		}
		BlueBeetleAbilities.clear(player);
		landWings(player);
		long now = player.level().getGameTime();
		BlueBeetleState n = state(player).copy();
		n.suited = false;
		n.suitChangeAt = now;
		n.flying = false;
		n.unleashedUntil = 0L;
		n.camo = false;
		n.fx = new BlueBeetleState.Fx();
		n.animId = BlueBeetleState.ANIM_SUIT_DOWN;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.2, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	// ---------------------------------------------------------------- flight (double-tap jump; the wings come out)

	/**
	 * v0.16.5 (user: "make the player able to fly by double pressing space bar"): double-tap jump in the air takes off,
	 * again drops out of flight; touching down lands him too ({@link #tick}). Suited only. Flying breaks Camouflage.
	 */
	public static void toggleFlight(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (s.flying) {
			landWings(player);
			return;
		}
		if (player.onGround() || !canAct(player)) {
			return;
		}
		if (s.energy < BlueBeetleConfig.FLIGHT_DRAIN && !unleashed(player)) {
			say(player, "message.projecthero.blue_beetle.low_energy", ChatFormatting.BLUE, (int) BlueBeetleConfig.FLIGHT_DRAIN,
					(int) Math.floor(s.energy));
			return;
		}
		BlueBeetleAbilities.breakCamo(player);
		BlueBeetleState n = state(player).copy();
		n.flying = true;
		save(player, n);
		openWings(player);
		ServerLevel level = (ServerLevel) player.level();
		// v0.16.5 (user): no wind sounds -- just the hum of the wings
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.7f);
	}

	static void openWings(ServerPlayer player) {
		if (!player.getAbilities().mayfly) {
			FLIGHT_GRANTED.add(player.getUUID());
			player.getAbilities().mayfly = true;
		}
		player.getAbilities().flying = true;
		player.getAbilities().setFlyingSpeed(BlueBeetleConfig.FLIGHT_SPEED);
		player.onUpdateAbilities();
	}

	/** Lands: folds the wings and gives back exactly the flight the suit lent (creative / other powers keep theirs). */
	public static void landWings(ServerPlayer player) {
		BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s != null && s.flying) {
			BlueBeetleState n = s.copy();
			n.flying = false;
			save(player, n);
		}
		if (player.getAbilities().getFlyingSpeed() == BlueBeetleConfig.FLIGHT_SPEED) {
			player.getAbilities().setFlyingSpeed(0.05f);
		}
		if (FLIGHT_GRANTED.remove(player.getUUID())) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.resetFallDistance();
		}
		player.onUpdateAbilities();
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		if (suited(player)) {
			PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, BlueBeetleConfig.SUIT_ARMOR, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, BlueBeetleConfig.SUIT_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, BlueBeetleConfig.SUIT_MELEE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		}
		if (suited(player) && state(player).fx.blade) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BLADE_ID, BlueBeetleConfig.BLADE_MELEE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, BLADE_ID);
		}
	}

	static boolean canAct(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!s.suited) {
			say(player, "message.projecthero.blue_beetle.not_suited", ChatFormatting.GRAY);
			return false;
		}
		if (changing(player)) {
			say(player, "message.projecthero.blue_beetle.bonding", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (now % 5 == 0) {
			tickEnergy(player, s, now);
		}
		if (s.suited) {
			if (player.tickCount % 40 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
			}
			if (s.flying) {
				if (!player.getAbilities().flying && player.onGround()) {
					landWings(player); // touched down
				} else {
					player.resetFallDistance();
				}
			}
			if (s.camo && (s.flying || player.getAbilities().flying || !player.hasEffect(MobEffects.INVISIBILITY))) {
				BlueBeetleAbilities.breakCamo(player); // flying (or milk) shows him
			}
			if (s.unleashedUntil != 0L && s.unleashedUntil <= now) {
				BlueBeetleAbilities.endUnleashed(player);
			}
			if (now % 200 == 0 && player.getHealth() < player.getMaxHealth() * 0.3f) {
				KhajiDa.speak(player, KhajiDa.Topic.LOW_HEALTH);
			}
		} else if (now % 40 == 0) {
			warnHostiles(player);
		}
		BlueBeetleAbilities.tick(player);
	}

	private static void tickEnergy(ServerPlayer player, BlueBeetleState s, long now) {
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		boolean still = last != null && last.distanceToSqr(pos) < 0.0025 && player.onGround();
		float rate = BlueBeetleConfig.ENERGY_REGEN * (still ? BlueBeetleConfig.STILL_MULTIPLIER : 1f);
		if (s.slowRegenUntil > now) {
			rate *= 0.5f;
		}
		float delta = rate / 4f;
		if (s.flying && player.getAbilities().flying) {
			delta -= BlueBeetleConfig.FLIGHT_DRAIN / 4f;
		}
		if (unleashed(player)) {
			return;
		}
		float cur = Float.isNaN(s.energy) ? 0f : s.energy;
		float next = Math.max(0f, Math.min(BlueBeetleConfig.ENERGY_MAX, cur + delta));
		if (next != s.energy) {
			BlueBeetleState n = s.copy();
			n.energy = next;
			save(player, n);
		}
		if (next <= 0f && s.flying) {
			landWings(player);
			say(player, "message.projecthero.blue_beetle.energy_empty", ChatFormatting.BLUE);
		}
	}

	/** Unsuited, Khaji-Da still watches his back. */
	private static void warnHostiles(ServerPlayer player) {
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(BlueBeetleConfig.WARN_RANGE),
				m -> m instanceof Enemy && m.isAlive())) {
			KhajiDa.warn(player, m);
			return;
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		if (s.camo) {
			player.removeEffect(MobEffects.INVISIBILITY); // the camouflage's endless invisibility
		}
		BlueBeetleState n = s.copy();
		n.flying = false;
		n.fx = new BlueBeetleState.Fx();
		n.unleashedUntil = 0L;
		n.camo = false;
		n.suitChangeAt = 0L;
		n.animId = BlueBeetleState.ANIM_NONE;
		if (n.slowRegenUntil > now + BlueBeetleConfig.UNLEASH_AFTER_TICKS || n.adaptUntil > now + BlueBeetleConfig.ADAPT_TICKS) {
			n.slowRegenUntil = 0L;
			n.adaptUntil = 0L;
			n.adaptType = "";
		}
		n.energy = Float.isNaN(n.energy) ? 0f : Math.max(0f, Math.min(BlueBeetleConfig.ENERGY_MAX, n.energy));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + BlueBeetleConfig.UNLEASH_COOLDOWN + 20L);
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		BlueBeetleState s = state(player);
		if (!s.hasPower) {
			return;
		}
		BlueBeetleState n = s.copy();
		n.suited = false;
		n.suitChangeAt = 0L;
		n.flying = false;
		n.fx = new BlueBeetleState.Fx();
		n.unleashedUntil = 0L;
		n.camo = false;
		n.adaptType = "";
		n.adaptUntil = 0L;
		n.animId = BlueBeetleState.ANIM_NONE;
		n.energy = Math.max(n.energy, BlueBeetleConfig.ENERGY_MAX * 0.5f);
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
	}

	public static void clearTransient(ServerPlayer player) {
		BlueBeetleAbilities.clear(player);
		landWings(player);
		forget(player.getUUID());
		BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s != null && s.hasPower) {
			BlueBeetleState n = s.copy();
			n.fx = new BlueBeetleState.Fx();
			n.camo = false;
			save(player, n);
			reconcile(player);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		BlueBeetleState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
