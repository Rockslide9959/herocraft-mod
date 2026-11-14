package com.projecthero.mod.doctorfate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.doctorfate.data.FateAttachments;
import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Doctor Fate (Kent Nelson, the Helmet of Fate and Nabu): the single server-side API of the power. Nothing else changes
 * {@link FateState}. Numbers are in {@link DoctorFateConfig}; the spells are {@link DoctorFateSpells}' (routed by
 * {@link DoctorFateAbilityManager}); Nabu's side is {@link DoctorFateDominion}, {@link DoctorFateBargains} and
 * {@link DoctorFatePossession}.
 *
 * <ul>
 *   <li><b>Helmet off</b>: v0.17.4 (user) no Fate powers at all, normal health, Dominion drains. The helmet is the Helmet of Fate item
 *   (or set down somewhere); H summons it from anywhere ({@link DoctorFateHelmet}).</li>
 *   <li><b>Helmet on</b> (H or Shift+right-click the item, the put-on sequence): 40 health, 30% damage resistance, +8 melee,
 *   Regeneration I, no armour, flight (double-tap Space), Wither and Poison immunity, every spell, Dominion rising.</li>
 * </ul>
 */
public final class DoctorFate {
	public static final String KEY = "doctor_fate";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("doctor_fate_health");
	/** v0.17.4 (user: "melee bonus +8"): +8 attack damage while the helmet is on. */
	private static final ResourceLocation MELEE_ID = PowerToggles.id("doctor_fate_melee");

	public static final int GOLD = 0xFFC93A;
	public static final int BLUE = 0x2A56C8;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> LAST_HEALTH = new ConcurrentHashMap<>();

	private DoctorFate() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_HEALTH.clear();
		DoctorFateAbilityManager.clearSessionState();
		DoctorFateSpells.clearSessionState();
		DoctorFateDominion.clearSessionState();
		DoctorFatePossession.clearSessionState();
		DoctorFateFlight.clearSessionState();
		DoctorFateMindscape.clearSessionState();
		DoctorFateHelmet.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_HEALTH.remove(id);
		DoctorFateAbilityManager.forget(id);
		DoctorFateSpells.forget(id);
		DoctorFateDominion.forget(id);
		DoctorFatePossession.forget(id);
		DoctorFateFlight.forget(id);
		DoctorFateMindscape.forget(id);
		DoctorFateHelmet.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static FateState state(ServerPlayer player) {
		return player.getAttachedOrCreate(FateAttachments.STATE);
	}

	public static void save(ServerPlayer player, FateState state) {
		player.setAttached(FateAttachments.STATE, state);
	}

	public static FateState peek(Player player) {
		return player.getAttachedOrElse(FateAttachments.STATE, null);
	}

	public static boolean hasPower(Player player) {
		FateState s = peek(player);
		return s != null && s.hasPower;
	}

	public static long now(Player player) {
		return player.level().getGameTime();
	}

	/** The helmet is on (the 1 s animation finished). Client-safe. */
	public static boolean wearsHelmet(Player player) {
		FateState s = peek(player);
		return s != null && s.hasPower && s.helmetOn;
	}

	/**
	 * 0..1 of the suit showing. v0.17.4: during the put-on sequence it fades in while the helmet comes down onto the head (from tick
	 * 20 of 30); during the lift-off it fades out as the helmet is lifted away (ticks 6-16 of 24); 0 while a summon is still flying
	 * in. Client-safe.
	 */
	public static float helmetShown(Player player, float partial) {
		FateState s = peek(player);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		if (s.helmetChangeAt > 0L) {
			float t = now(player) - s.helmetChangeAt + partial;
			if (s.helmetGoingOn) {
				return Math.max(0f, Math.min(1f, (t - 20f) / (DoctorFateConfig.HELMET_EQUIP_TICKS - 20f)));
			}
			return 1f - Math.max(0f, Math.min(1f, (t - 6f) / 10f));
		}
		return s.helmetOn ? 1f : 0f;
	}

	/** v0.17.4: ticks into the helmet's put-on / lift-off sequence (negative while a summon flies in; -1000 = none). Client-safe. */
	public static float helmetSequenceAge(Player player, float partial) {
		FateState s = peek(player);
		if (s == null || !s.hasPower || s.helmetChangeAt <= 0L) {
			return -1000f;
		}
		return now(player) - s.helmetChangeAt + partial;
	}

	public static float dominion(Player player) {
		FateState s = peek(player);
		return s == null ? 0f : s.dominion;
	}

	/** 0 Whispers, 1 Override, 2 Pull, 3 Taking hold, 4 Possession. */
	public static int tier(float dominion) {
		if (dominion >= DoctorFateConfig.DOMINION_MAX) {
			return 4;
		}
		if (dominion >= DoctorFateConfig.TIER_HOLD) {
			return 3;
		}
		if (dominion >= DoctorFateConfig.TIER_PULL) {
			return 2;
		}
		return dominion >= DoctorFateConfig.TIER_OVERRIDE ? 1 : 0;
	}

	public static int tier(Player player) {
		return possessed(player) ? 4 : tier(dominion(player));
	}

	public static boolean possessed(Player player) {
		FateState s = peek(player);
		return s != null && s.hasPower && s.possessedUntil > now(player);
	}

	public static int possessionTicksLeft(Player player) {
		FateState s = peek(player);
		return s == null ? 0 : (int) Math.max(0L, s.possessedUntil - now(player));
	}

	public static int lockoutTicksLeft(Player player) {
		FateState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.lockoutUntil - now(player));
	}

	public static boolean isFlying(Player player) {
		FateState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	public static boolean shieldUp(Player player) {
		FateState s = peek(player);
		return s != null && s.hasPower && s.shieldUp;
	}

	public static boolean avatarActive(Player player) {
		FateState s = peek(player);
		return s != null && s.hasPower && s.avatarUntil > now(player);
	}

	public static int avatarTicksLeft(Player player) {
		FateState s = peek(player);
		return s == null ? 0 : (int) Math.max(0L, s.avatarUntil - now(player));
	}

	public static int cooldownRemaining(Player player, String id) {
		FateState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		FateState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	public static void clearCooldowns(ServerPlayer player) {
		FateState n = state(player).copy();
		n.abilityReadyAt.clear();
		n.lockoutUntil = 0L;
		save(player, n);
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = now(player);
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Sends a payload to this player if his client can take it (mock players in the gametests can't). */
	public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (player.connection != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, payload.type())) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
		}
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	// ---------------------------------------------------------------- Dominion

	/** Adds Dominion (negative takes it away), clamped 0..100. Possession itself starts in the tick. */
	public static void addDominion(ServerPlayer player, float amount) {
		FateState s = state(player);
		if (!s.hasPower || amount == 0f) {
			return;
		}
		setDominion(player, s.dominion + amount);
	}

	public static void setDominion(ServerPlayer player, float value) {
		FateState n = state(player).copy();
		n.dominion = Math.max(0f, Math.min(DoctorFateConfig.DOMINION_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- the power

	/** Accepting Nabu's offer (or the command). False if he already has it, or the unique-powers rule refuses. */
	public static boolean grant(ServerPlayer player) {
		FateState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		FateState s = new FateState();
		s.hasPower = true;
		s.dominion = old.dominion; // Dominion is saved per player: Nabu remembers
		save(player, s);
		reconcile(player);
		// v0.17.4: Nabu's helmet goes straight onto the new host's head (the put-on sequence); a fresh generation retires any old copy
		com.projecthero.mod.doctorfate.helmet.FateHelmetRegistry.get(player.server).bump(player.getUUID());
		DoctorFateHelmet.beginEquip(player, now(player));
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.3f);
		level.sendParticles(dust(GOLD, 1.4f), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.doctor_fate.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.doctor_fate.subtitle").withStyle(ChatFormatting.BLUE)));
		player.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		if (hasPower(player)) {
			DoctorFateFlight.stop(player);
		}
		FateState old = state(player);
		FateState n = new FateState();
		n.dominion = old.dominion;
		save(player, n);
		reconcile(player);
		if (old.hasPower) {
			DoctorFateHelmet.retire(player); // v0.17.4: every Helmet of Fate copy he left anywhere goes
		}
		DoctorFatePossession.release(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the shield, the flight, the Avatar and any offer end. */
	public static void clearTransient(ServerPlayer player) {
		FateState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		DoctorFateFlight.stop(player);
		FateState n = state(player).copy();
		n.shieldUp = false;
		n.avatarUntil = 0L;
		n.flying = false;
		// v0.17.4: a sequence cut short finishes at once (a summon in flight lands on the head; a lift-off gives the item)
		boolean giveItem = false;
		if (n.helmetChangeAt > 0L) {
			n.helmetOn = n.helmetGoingOn;
			giveItem = !n.helmetGoingOn;
			n.helmetChangeAt = 0L;
		}
		boolean fallOff = false;
		if (n.possessedUntil > 0L) {
			n.possessedUntil = 0L;
			fallOff = n.helmetOn;
			n.helmetOn = false;
			n.lockoutUntil = now(player) + DoctorFateConfig.LOCKOUT_TICKS;
		}
		n.bargain = n.bargain.withPending(FateState.Bargain.NONE_KIND, 0L);
		save(player, n);
		if (giveItem) {
			DoctorFateHelmet.giveBack(player);
		}
		if (fallOff) {
			DoctorFateHelmet.fallOff(player);
		}
		DoctorFatePossession.release(player);
		DoctorFateAbilityManager.forget(player.getUUID());
		DoctorFateMindscape.close(player);
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
		clearTransient(player);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
	}

	// ---------------------------------------------------------------- the helmet

	/**
	 * H. v0.17.4 (user): wearing it, H takes it off (the lift-off sequence, then the Helmet of Fate item); not wearing it, H summons it
	 * from wherever it is and it goes on ({@link DoctorFateHelmet#pressH}). Refused during Possession (it can't come off); during the
	 * 60 s lockout after one the summon lands in the inventory instead.
	 */
	public static boolean pressH(ServerPlayer player) {
		return DoctorFateHelmet.pressH(player);
	}

	/** The helmet on / off at once (the animation's end, the end of a Possession, the tests). */
	public static void setHelmet(ServerPlayer player, boolean on) {
		FateState n = state(player).copy();
		boolean was = n.helmetOn;
		n.helmetOn = on;
		n.helmetChangeAt = 0L;
		if (!on) {
			n.shieldUp = false;
			n.avatarUntil = 0L;
		}
		save(player, n);
		if (was && !on) {
			DoctorFateFlight.stop(player);
		}
		reconcile(player);
		if (on && !was) {
			player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4f));
		}
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		FateState s = peek(player);
		if (s == null || !s.hasPower || !s.helmetOn) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		} else {
			PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, DoctorFateConfig.HELMET_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, DoctorFateConfig.HELMET_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		FateState s = player.getAttachedOrElse(FateAttachments.STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		// v0.17.4 the put-on / lift-off sequences finishing
		if (s.helmetChangeAt > 0L) {
			long age = now - s.helmetChangeAt;
			if (s.helmetGoingOn && age >= DoctorFateConfig.HELMET_EQUIP_TICKS) {
				DoctorFateHelmet.finishEquip(player);
				s = state(player);
			} else if (!s.helmetGoingOn && age >= DoctorFateConfig.HELMET_REMOVE_TICKS) {
				DoctorFateHelmet.finishRemove(player);
				s = state(player);
			}
		}
		DoctorFateHelmet.tick(player, s, now);
		s = state(player);
		// Dominion: rises slowly with the helmet on, falls with it off (in place; re-sent every few ticks)
		if (!possessed(player)) {
			if (s.helmetOn) {
				s.dominion = Math.min(DoctorFateConfig.DOMINION_MAX, s.dominion + DoctorFateConfig.WEAR_GAIN_PER_TICK);
			} else {
				s.dominion = Math.max(0f, s.dominion - DoctorFateConfig.OFF_DRAIN_PER_TICK);
			}
		}
		if (s.helmetOn) {
			// Wither and Poison immunity
			if (player.hasEffect(MobEffects.WITHER)) {
				player.removeEffect(MobEffects.WITHER);
			}
			if (player.hasEffect(MobEffects.POISON)) {
				player.removeEffect(MobEffects.POISON);
			}
		}
		// Taking hold: he heals 2x (every point of healing since the last tick is matched)
		float hp = player.getHealth();
		Float lastHp = LAST_HEALTH.put(player.getUUID(), hp);
		if (lastHp != null && hp > lastHp && player.isAlive() && s.helmetOn && tier(s.dominion) >= 3) {
			float bonus = Math.min(hp - lastHp, 4f);
			player.setHealth(Math.min(player.getMaxHealth(), hp + bonus));
			LAST_HEALTH.put(player.getUUID(), player.getHealth());
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		DoctorFateDominion.tick(player);
		DoctorFatePossession.tick(player);
		DoctorFateBargains.tick(player);
		DoctorFateMindscape.tick(player);
		DoctorFateAbilityManager.tick(player);
		DoctorFateSpells.tick(player);
		DoctorFateFlight.tick(player);
		if (now % DoctorFateConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}
}
