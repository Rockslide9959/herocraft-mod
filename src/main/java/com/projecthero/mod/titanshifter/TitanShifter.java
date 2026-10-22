package com.projecthero.mod.titanshifter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanShifterEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The single server-side API for the Titan Shifter Hero-Tier power (v0.12.31). Nothing else pokes
 * {@link TitanShifterState}; every mutator re-saves via {@link ServerPlayer#setAttached}, and every phase
 * change goes through {@link #setPhase}, which refuses transitions {@link TitanPhase#canGoTo} does not allow.
 *
 * <pre>
 *   Titan Serum -> the Titan picker -> {@link #choose}  (unlock + pick Attack / Armoured / Female / Beast; a later serum re-picks)
 *   Titan Shift key -> {@link #requestToggle}: HUMAN -> TRANSFORMING -> TITAN, or TITAN -> REVERTING (3 s of steam) -> HUMAN
 *   Titan HP 0 -> {@link #onTitanDefeated}: TITAN -> DEFEATED -> RECOVERING -> HUMAN
 *   a heavy nape hit -> {@link #cutOut}: TITAN -> RECOVERING (ejected at once)
 * </pre>
 *
 * <h2>Steam (v0.16.7, was "Titan Energy")</h2>
 * A normal shift needs the full bar. Leaving the Titan refunds the Steam it had x its remaining HP fraction (a quick
 * exit at full HP costs nothing); only a defeat or a cut-out empties it -- and those also drop the shifter to 4 hearts
 * and lock shifting for 60 s. Out of combat the Titan heals ~2 HP/s by burning Steam; the human form heals from its own
 * small pool (shown on the HUD), never from the bar.
 *
 * Everything is server-authoritative: the client sends key requests only, and the Titan itself is a real
 * entity the shifter rides (see {@link TitanFormEntity}).
 */
public final class TitanShifter {
	public static final String KEY = "titan_shifter";

	/** v0.16.7: the human regen pool's spent amount (x100) and the Crystal Cocoon's end, kept in abilityReadyAt under these keys. */
	public static final String POOL_KEY = "~pool_spent";
	public static final String COCOON_KEY = "~cocoon";

	/** Per-player, per-message throttle for action-bar feedback so a held key cannot spam (one per key per second). */
	private static final Map<UUID, Map<String, Long>> LAST_MESSAGE = new HashMap<>();
	/** Shifters whose Sprint key is currently held (sent by the client: a rider never reports sprinting on its own). */
	private static final java.util.Set<UUID> SPRINT_HELD = new java.util.HashSet<>();
	/** v0.12.39: shifters holding H for an emergency shift -> game time the hold began. */
	private static final Map<UUID, Long> EMERGENCY_HOLD = new HashMap<>();
	/** v0.14.16: the game tick each shifter's bar last refilled on -- a player ticked twice in one tick refills once. */
	private static final Map<UUID, Long> LAST_REFILL = new HashMap<>();
	/** v0.16.7: players who drank a Titan Serum and have the picker open -> game time the offer runs out. */
	private static final Map<UUID, Long> PENDING_PICK = new HashMap<>();

	private TitanShifter() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		SPRINT_HELD.clear();
		EMERGENCY_HOLD.clear();
		LAST_REFILL.clear();
		PENDING_PICK.clear();
	}

	public static void setSprintHeld(ServerPlayer player, boolean held) {
		if (held) {
			SPRINT_HELD.add(player.getUUID());
		} else {
			SPRINT_HELD.remove(player.getUUID());
		}
	}

	public static boolean sprintHeld(UUID id) {
		return SPRINT_HELD.contains(id);
	}

	// ---------------- state access ----------------

	public static TitanShifterState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.TITAN_SHIFTER_STATE);
	}

	static void save(ServerPlayer player, TitanShifterState state) {
		player.setAttached(ModAttachments.TITAN_SHIFTER_STATE, state);
	}

	/** Safe on the client too (the attachment is synced). */
	public static boolean isShifter(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s != null && s.unlocked;
	}

	public static TitanPhase phase(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null || !s.unlocked ? TitanPhase.HUMAN : s.phase();
	}

	public static boolean inTitan(Player player) {
		return phase(player) == TitanPhase.TITAN;
	}

	/** v0.16.7: the Titan this shifter becomes (both sides). */
	public static TitanType selectedType(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null ? TitanType.GENERIC_TITAN : TitanType.byId(s.typeId);
	}

	/** The form the player is currently riding as their own Titan (either side), or null. */
	public static TitanFormEntity formOf(Player player) {
		return player.getVehicle() instanceof TitanFormEntity f && player.getUUID().equals(f.ownerId()) ? f : null;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	public static int transformCooldownRemaining(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null ? 0 : (int) Math.max(0L, s.cooldownUntil - player.level().getGameTime());
	}

	static void startCooldown(ServerPlayer player, String abilityId, int ticks, int actionSlot) {
		long now = player.level().getGameTime();
		TitanShifterState s = state(player).copy();
		s.abilityReadyAt.put(abilityId, now + ticks);
		s.lastAction = actionSlot;
		s.lastActionTick = now;
		save(player, s);
	}

	/** Rate-limited action-bar message: each message key at most once a second per player (v0.16.7: per key, as intended). */
	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Map<String, Long> mine = LAST_MESSAGE.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		Long last = mine.get(key);
		if (last != null && now - last < 20 && now >= last) {
			return;
		}
		mine.put(key, now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	private static void setPhase(ServerPlayer player, TitanPhase next, long until) {
		TitanShifterState s = state(player).copy();
		TitanPhase current = s.phase();
		if (current != next && !current.canGoTo(next)) {
			return;
		}
		s.phase = next.name();
		s.phaseStartedAt = player.level().getGameTime();
		s.phaseUntil = until;
		save(player, s);
	}

	// ---------------- Steam ----------------

	/** Steam (0..max) as the HUD and the transformation gate see it. */
	public static float energy(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null ? 0f : s.energy;
	}

	/** v0.16.7: spends {@code amount} Steam if the bar has it. */
	public static boolean spendSteam(ServerPlayer player, float amount) {
		TitanShifterState s = state(player);
		if (s.energy + 1.0e-3f < amount) {
			return false;
		}
		TitanShifterState n = s.copy();
		n.energy = Math.max(0f, s.energy - amount);
		save(player, n);
		return true;
	}

	/** v0.16.7: the human form's own healing pool (0..humanPoolMax), shown on the HUD. Safe on the client. */
	public static float humanPool(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		double max = TitanShifterConfig.energy().humanPoolMax;
		if (s == null) {
			return (float) max;
		}
		Long spent = s.abilityReadyAt.get(POOL_KEY);
		return (float) Math.max(0.0, max - (spent == null ? 0.0 : spent / 100.0));
	}

	private static void setHumanPool(TitanShifterState s, double pool) {
		double max = TitanShifterConfig.energy().humanPoolMax;
		long spent = Math.round(Math.max(0.0, max - Math.min(max, pool)) * 100.0);
		if (spent <= 0L) {
			s.abilityReadyAt.remove(POOL_KEY);
		} else {
			s.abilityReadyAt.put(POOL_KEY, spent);
		}
	}

	/** v0.16.7: inside the Crystal Cocoon (Shift+X in human form) -- untouchable and healing. Safe on the client. */
	public static boolean cocooned(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null) {
			return false;
		}
		Long until = s.abilityReadyAt.get(COCOON_KEY);
		return until != null && until > player.level().getGameTime();
	}

	// ---------------- unlock / the Titan picker ----------------

	/** Titan Serum / command: unlock Titan Shifting as the Attack Titan. Returns false if already unlocked. */
	public static boolean grant(ServerPlayer player) {
		return grant(player, TitanType.GENERIC_TITAN);
	}

	/** v0.16.7: unlock Titan Shifting as {@code type}. Returns false if already unlocked (or refused). */
	public static boolean grant(ServerPlayer player, TitanType type) {
		if (state(player).unlocked) {
			return false;
		}
		// v0.16.1: projectheroUniquePowers -- refused (nothing granted) while someone else holds it
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		TitanShifterState s = state(player).copy();
		s.unlocked = true;
		s.typeId = type.id;
		s.phase = TitanPhase.HUMAN.name();
		s.cooldownUntil = 0L;
		s.abilityReadyAt.clear();
		s.energy = (float) TitanShifterConfig.energy().max; // a fresh shifter starts with a full bar of Steam
		s.emergencyUntil = 0L;
		s.emergencyPenalty = false;
		save(player, s);

		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
				SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
				SoundSource.PLAYERS, 2.0f, 0.7f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 50, 0.5, 0.9, 0.5, 0.3);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 20, 0.5, 0.9, 0.5, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.unlocked")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.chosen",
				Component.translatable(type.nameKey())).withStyle(ChatFormatting.GOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.unlocked_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		if (TitanShifterConfig.transformation().serumTransformsImmediately) {
			transform(player);
		}
		return true;
	}

	/**
	 * v0.16.7: a Titan Serum was drunk -- the picker opens on the client. The serum is only used up when a Titan is picked.
	 * Returns false (with feedback) when the shifter cannot re-pick right now (inside a Titan).
	 */
	public static boolean offerPick(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (s.unlocked && s.phase() != TitanPhase.HUMAN) {
			say(player, "message.projecthero.titan_shifter.pick_not_now", ChatFormatting.GRAY);
			return false;
		}
		PENDING_PICK.put(player.getUUID(), player.level().getGameTime() + 20L * 120L);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new com.projecthero.mod.network.TitanPickerPayload(s.unlocked ? TitanType.byId(s.typeId).ordinal() : -1));
		return true;
	}

	/** True while the picker the player was offered is still valid (for tests and the network handler). */
	public static boolean pickPending(ServerPlayer player) {
		Long until = PENDING_PICK.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	/**
	 * v0.16.7: the picker's answer. A new shifter is unlocked as {@code type}; an existing one (in human form) re-forges into
	 * it. Either way one Titan Serum is used up (from the hands first, then the inventory; never in creative).
	 */
	public static boolean choose(ServerPlayer player, TitanType type) {
		if (!pickPending(player)) {
			return false;
		}
		net.minecraft.world.item.ItemStack serum = findSerum(player);
		if (serum.isEmpty() && !player.getAbilities().instabuild) {
			PENDING_PICK.remove(player.getUUID());
			return false;
		}
		TitanShifterState s = state(player);
		boolean ok;
		if (!s.unlocked) {
			ok = grant(player, type);
		} else if (s.phase() == TitanPhase.HUMAN) {
			TitanShifterState n = s.copy();
			n.typeId = type.id;
			save(player, n);
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
			level.sendParticles(YELLOW_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.2);
			player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.reforged",
					Component.translatable(type.nameKey())).withStyle(ChatFormatting.GOLD), false);
			ok = true;
		} else {
			say(player, "message.projecthero.titan_shifter.pick_not_now", ChatFormatting.GRAY);
			ok = false;
		}
		PENDING_PICK.remove(player.getUUID());
		if (ok && !serum.isEmpty() && !player.getAbilities().instabuild) {
			serum.shrink(1);
		}
		return ok;
	}

	private static net.minecraft.world.item.ItemStack findSerum(ServerPlayer player) {
		var item = com.projecthero.mod.titanshifter.item.TitanShifterItems.TITAN_SERUM;
		for (var hand : net.minecraft.world.InteractionHand.values()) {
			if (player.getItemInHand(hand).is(item)) {
				return player.getItemInHand(hand);
			}
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return inv.getItem(i);
			}
		}
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	/** Admin / power suppressor: remove the power and any active Titan. */
	public static void revoke(ServerPlayer player) {
		forceEnd(player, false);
		TitanShifterState s = state(player).copy();
		s.unlocked = false;
		s.phase = TitanPhase.HUMAN.name();
		s.cooldownUntil = 0L;
		s.regenUntil = 0L;
		s.hardenUntil = 0L;
		s.titanHealth = 0f;
		s.energy = 0f;
		s.emergencyUntil = 0L;
		s.emergencyPenalty = false;
		s.abilityReadyAt.clear();
		save(player, s);
	}

	// ---------------- transformation ----------------

	/** Steam needed to transform: the whole bar by default. */
	public static float energyNeeded() {
		var e = TitanShifterConfig.energy();
		return (float) (e.max * e.transformMinFraction);
	}

	/** The Titan Shift key: transform when human, revert when a Titan. */
	public static void requestToggle(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked) {
			return;
		}
		switch (s.phase()) {
			case HUMAN -> {
				long now = player.level().getGameTime();
				if (now < s.cooldownUntil) {
					say(player, "message.projecthero.titan_shifter.cooldown", ChatFormatting.RED,
							String.format(java.util.Locale.ROOT, "%.0f", (s.cooldownUntil - now) / 20.0));
					return;
				}
				// v0.12.39: a full bar shifts at once; between the emergency minimum and a full bar, HOLD H for an emergency shift
				if (s.energy + 1.0e-3f >= energyNeeded() || !emergencyAllowed(s)) {
					if (s.energy + 1.0e-3f < energyNeeded() && s.emergencyPenalty) {
						say(player, "message.projecthero.titan_shifter.emergency_locked", ChatFormatting.RED);
					} else {
						transform(player);
					}
				} else {
					beginEmergencyHold(player);
				}
			}
			case TITAN -> revert(player);
			case TRANSFORMING, REVERTING, DEFEATED -> say(player, "message.projecthero.titan_shifter.busy", ChatFormatting.GRAY);
			case RECOVERING -> say(player, "message.projecthero.titan_shifter.recovering", ChatFormatting.GRAY);
		}
	}

	/** True if the bar alone would allow an emergency shift (at least the configured fraction, and no penalty from the last one). */
	public static boolean emergencyAllowed(TitanShifterState s) {
		return !s.emergencyPenalty && s.energy + 1.0e-3f >= TitanShifterConfig.energy().max * TitanShifterConfig.emergency().minFraction;
	}

	/** H pressed with an emergency-range bar: start the hold (the electricity, and the shift itself, are driven by {@link #tickEmergencyHold}). */
	private static void beginEmergencyHold(ServerPlayer player) {
		if (EMERGENCY_HOLD.containsKey(player.getUUID())) {
			return;
		}
		if (player.isSpectator() || player.isSleeping() || player.isPassenger() || !player.isAlive()
				|| com.projecthero.mod.hulk.Hulk.isHulk(player)) { // v0.13.12: no Titan while he is the Hulk
			say(player, "message.projecthero.titan_shifter.cannot_now", ChatFormatting.RED);
			return;
		}
		EMERGENCY_HOLD.put(player.getUUID(), player.level().getGameTime());
		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
				SoundSource.PLAYERS, 1.2f, 1.7f);
	}

	/** v0.12.43: Shift+N (base form) switches the passive healing off / on. Off = no Regeneration II and no pool spent. */
	public static void toggleRegen(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked || s.phase() != TitanPhase.HUMAN) {
			return;
		}
		TitanShifterState n = s.copy();
		n.regenOff = !s.regenOff;
		save(player, n);
		player.displayClientMessage(Component.translatable(n.regenOff ? "message.projecthero.titan_shifter.regen_off"
				: "message.projecthero.titan_shifter.regen_on").withStyle(n.regenOff ? ChatFormatting.GRAY : ChatFormatting.GREEN), true);
	}

	/** H released before the hold completed (or a stray release): the emergency shift is cancelled. */
	public static void cancelEmergencyHold(ServerPlayer player) {
		if (EMERGENCY_HOLD.remove(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.emergency_cancelled")
					.withStyle(ChatFormatting.GRAY), true);
		}
	}

	private static void tickEmergencyHold(ServerPlayer player, TitanShifterState s) {
		Long start = EMERGENCY_HOLD.get(player.getUUID());
		if (start == null) {
			return;
		}
		if (player.containerMenu != player.inventoryMenu) {
			// v0.16.7: opening a chest / furnace mid-hold breaks the concentration -- and now says so
			EMERGENCY_HOLD.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.emergency_screen")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!emergencyAllowed(s) || s.phase() != TitanPhase.HUMAN || !player.isAlive() || player.isSpectator()
				|| player.isPassenger()) {
			EMERGENCY_HOLD.remove(player.getUUID());
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		int need = TitanShifterConfig.emergency().holdTicks;
		long held = now - start;
		float progress = Math.min(1.0f, held / (float) Math.max(1, need));
		double h = player.getBbHeight();
		// v0.13.11: yellow electricity crackles round the shifter, building as the hold goes on
		level.sendParticles(YELLOW_SPARK, player.getX(), player.getY() + h * 0.5, player.getZ(),
				2 + (int) (7 * progress), 0.55, h * 0.5, 0.55, 0.12 + 0.2 * progress);
		if (held % 6 == 0) {
			level.sendParticles(YELLOW_GLOW, player.getX(), player.getY() + h * 0.5, player.getZ(), 1, 0.5, h * 0.45, 0.5, 0.05);
		}
		if (held % 8 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
					0.25f + 0.35f * progress, 1.4f + 0.6f * progress);
		}
		if (held % 10 == 0 && held < need) {
			player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.emergency_hold",
					String.format(java.util.Locale.ROOT, "%.1f", (need - held) / 20.0)).withStyle(ChatFormatting.AQUA), true);
		}
		if (held >= need) {
			EMERGENCY_HOLD.remove(player.getUUID());
			transform(player, true);
		}
	}

	public static boolean transform(ServerPlayer player) {
		return transform(player, false);
	}

	public static boolean transform(ServerPlayer player, boolean emergency) {
		// v0.16.7 (user, Titan fixes): the Emergency Titan's rushed build is shorter (2 s against 3 s)
		return transform(player, emergency, emergency ? Math.max(10, TitanShifterConfig.emergency().transformTicks)
				: TitanShifterConfig.transformation().transformTicks);
	}

	/**
	 * {@code emergency}: the pale Emergency Titan -- needs only the emergency minimum of the bar, but a form that is smaller
	 * (~60% of the type's height), weaker and time-limited. {@code ticks}: how long the transformation takes (Bite Shift is fast).
	 */
	public static boolean transform(ServerPlayer player, boolean emergency, int ticks) {
		TitanShifterState s = state(player);
		if (!s.unlocked) {
			return false;
		}
		if (s.phase() != TitanPhase.HUMAN) {
			say(player, "message.projecthero.titan_shifter.already", ChatFormatting.GRAY);
			return false;
		}
		long now = player.level().getGameTime();
		if (now < s.cooldownUntil) {
			say(player, "message.projecthero.titan_shifter.cooldown", ChatFormatting.RED,
					String.format(java.util.Locale.ROOT, "%.0f", (s.cooldownUntil - now) / 20.0));
			return false;
		}
		if (emergency ? !emergencyAllowed(s) : s.energy + 1.0e-3f < energyNeeded()) {
			say(player, "message.projecthero.titan_shifter.low_energy", ChatFormatting.RED,
					(int) Math.ceil(s.energy), (int) Math.ceil(energyNeeded()));
			return false;
		}
		if (player.isSpectator() || player.isSleeping() || player.isPassenger() || !player.isAlive()
				|| com.projecthero.mod.hulk.Hulk.isHulk(player)) { // v0.13.12: no Titan while he is the Hulk
			say(player, "message.projecthero.titan_shifter.cannot_now", ChatFormatting.RED);
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		TitanType type = TitanType.byId(s.typeId);
		TitanFormEntity form = TitanShifterEntities.TITAN_FORM.create(level);
		if (form == null) {
			return false;
		}
		form.bind(player, type, emergency);
		if (!placeForm(level, form, player)) {
			form.discard();
			say(player, "message.projecthero.titan_shifter.no_room", ChatFormatting.RED);
			return false;
		}
		form.setFormState(TitanFormEntity.FORM_TRANSFORMING, ticks);
		if (!level.addFreshEntity(form)) {
			form.discard();
			return false;
		}
		if (!player.startRiding(form, true)) {
			form.discard();
			say(player, "message.projecthero.titan_shifter.cannot_now", ChatFormatting.RED);
			return false;
		}
		player.fallDistance = 0.0f;
		player.clearFire();

		TitanShifterState next = state(player).copy();
		next.titanHealth = form.getHealth();
		next.titanMaxHealth = form.getMaxHealth();
		next.regenUntil = 0L;
		next.hardenUntil = 0L;
		next.emergencyUntil = emergency ? now + TitanShifterConfig.emergency().durationTicks : 0L;
		next.abilityReadyAt.remove(COCOON_KEY);
		save(player, next);
		setPhase(player, TitanPhase.TRANSFORMING, now + ticks);

		transformFx(level, player, form, ticks);
		return true;
	}

	/** Finds a free spot for the Titan's hit-box near the player, clearing foliage first. */
	private static boolean placeForm(ServerLevel level, TitanFormEntity form, ServerPlayer player) {
		double[][] offsets = { { 0, 0 }, { 1.5, 0 }, { -1.5, 0 }, { 0, 1.5 }, { 0, -1.5 }, { 1.5, 1.5 }, { -1.5, 1.5 },
				{ 1.5, -1.5 }, { -1.5, -1.5 }, { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 } };
		for (double dy : new double[] { 0.0, 1.0 }) {
			for (double[] o : offsets) {
				form.moveTo(player.getX() + o[0], player.getY() + dy, player.getZ() + o[1], player.getYRot(), 0.0f);
				form.setYBodyRot(player.getYRot());
				form.setYHeadRot(player.getYRot());
				if (TitanShifterConfig.transformation().clearWeakBlocksOnTransform) {
					// only clear when this spot is otherwise clear of solid blocks
					var box = form.getBoundingBox();
					if (level.noCollision(form, box)) {
						return true;
					}
					if (blockedOnlyByFoliage(level, box)) {
						TitanCombat.breakWeakBlocks(level, box, 600);
						if (level.noCollision(form, form.getBoundingBox())) {
							return true;
						}
					}
				} else if (level.noCollision(form, form.getBoundingBox())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean blockedOnlyByFoliage(ServerLevel level, net.minecraft.world.phys.AABB box) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
			for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
				for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					var state = level.getBlockState(pos);
					if (!state.getCollisionShape(level, pos).isEmpty() && !TitanCombat.isFoliage(state)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** v0.13.11: the transformation's electricity is yellow (the bolt itself is drawn yellow by the client). */
	static final net.minecraft.core.particles.DustParticleOptions YELLOW_SPARK =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.86f, 0.12f), 1.0f);
	private static final net.minecraft.core.particles.DustParticleOptions YELLOW_GLOW =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.95f, 0.55f), 1.8f);

	static void lightning(ServerLevel level, Vec3 at) {
		// v0.13.11: the shifter's own bolt type, which the client draws yellow
		LightningBolt bolt = TitanShifterEntities.TITAN_LIGHTNING.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true); // no fire, no damage, no terrain
			level.addFreshEntity(bolt);
		}
	}

	private static void transformFx(ServerLevel level, ServerPlayer player, TitanFormEntity form, int ticks) {
		Vec3 p = form.position();
		lightning(level, p);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.45f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 1.0, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLASH, p.x, p.y + 2.0, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(YELLOW_SPARK, p.x, p.y + 4.0, p.z, 120, 1.6, 4.0, 1.6, 0.4);
		level.sendParticles(YELLOW_GLOW, p.x, p.y + 4.0, p.z, 30, 1.4, 3.5, 1.4, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1.0, p.z, 60, 1.8, 0.6, 1.8, 0.15);
		form.steamBurst(level, 8);
		// a shockwave that shoves bystanders clear (no damage) so nobody is standing inside the Titan
		for (LivingEntity e : AbilityHelpers.living(level, p, 6.0, x -> x != player && x != form)) {
			AbilityHelpers.knockbackFrom(e, p, 1.2);
		}
		TitanCombat.shake(level, p, 1.1f, ticks);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.transforming")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	// ---------------- reversion ----------------

	/**
	 * Manual reversion (v0.16.7: the REVERTING phase is back): the Titan stops dead and steams for {@code revertTicks}
	 * (~3 s) while the shifter climbs out of the nape; then the shifter pops out and the body is left as a corpse.
	 */
	public static void revert(ServerPlayer player) {
		TitanShifterState s = state(player);
		TitanFormEntity form = formOf(player);
		if (s.phase() != TitanPhase.TITAN || form == null) {
			return;
		}
		form.setHardened(false);
		form.endCharge();
		form.releaseHeld();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5f, 0.5f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 3.0f, 0.5f);
		TitanCombat.shake(level, p, 0.5f, 20);
		int ticks = Math.max(1, TitanShifterConfig.transformation().revertTicks);
		// the HP mirror the refund is worked out from
		TitanShifterState n = s.copy();
		n.titanHealth = form.getHealth();
		n.titanMaxHealth = form.getMaxHealth();
		save(player, n);
		setPhase(player, TitanPhase.REVERTING, level.getGameTime() + ticks);
		form.setFormState(TitanFormEntity.FORM_REVERTING, ticks);
		form.steamBurst(level, 10);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.reverting")
				.withStyle(ChatFormatting.GRAY), true);
	}

	/** While REVERTING: steam pours off the whole body and out of the nape as the shifter climbs out. */
	private static void tickReverting(ServerPlayer player, TitanFormEntity form) {
		if (player.tickCount % 3 != 0) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		double h = form.getBbHeight();
		double w = form.getBbWidth();
		Vec3 back = Vec3.directionFromRotation(0.0f, form.getYRot()).scale(-1.0);
		Vec3 nape = p.add(0.0, h * 0.8, 0.0).add(back.scale(w * 0.2));
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + h * 0.5, p.z, 6, w * 0.4, h * 0.35, w * 0.4, 0.03);
		level.sendParticles(ParticleTypes.CLOUD, nape.x, nape.y, nape.z, 4, 0.4, 0.3, 0.4, 0.05);
		form.steamBurst(level, 2);
		if (player.tickCount % 12 == 0) {
			level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.6f, 0.6f);
		}
	}

	private static void finishRevert(ServerPlayer player, TitanFormEntity form) {
		float frac = form.getMaxHealth() > 0f ? form.getHealth() / form.getMaxHealth() : 0f;
		exitThroughNape(player, form, TitanCorpseEntity.CAUSE_REVERT);
		TitanShifterState s = state(player);
		float refund = TitanShifterConfig.energy().refundByHealth ? s.energy * Math.max(0f, Math.min(1f, frac)) : 0f;
		finishToHuman(player, TitanPhase.HUMAN, 0, refund, false);
	}

	/** The shifter pops out of the back of the neck, drifting down under Slow Falling; the body stays behind as a corpse. */
	private static void exitThroughNape(ServerPlayer player, TitanFormEntity form, int cause) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		float yaw = form.getYRot();
		double height = form.getBbHeight();
		double width = form.getBbWidth();
		release(player, form, false);
		spawnCorpse(level, form, p, yaw, cause);
		Vec3 back = Vec3.directionFromRotation(0.0f, yaw).scale(-1.0);
		Vec3 neck = p.add(0.0, height * 0.82, 0.0).add(back.scale(width * 0.2));
		player.teleportTo(level, neck.x, neck.y, neck.z, yaw, 0.0f);
		player.setDeltaMovement(back.x * 0.28, 0.42, back.z * 0.28);
		player.hurtMarked = true;
		player.fallDistance = 0.0f;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 14, 0, false, false, false));
		steamPoof(level, p.add(0.0, height * 0.55, 0.0), (float) height * 0.5f);
		level.playSound(null, neck.x, neck.y, neck.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, neck.x, neck.y, neck.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4f, 0.6f);
	}

	private static void spawnCorpse(ServerLevel level, TitanFormEntity form, Vec3 p, float yaw, int cause) {
		TitanCorpseEntity corpse = TitanShifterEntities.TITAN_CORPSE.create(level);
		if (corpse != null) {
			corpse.bind(form.titanType(), form.isEmergency());
			corpse.setCause(cause);
			corpse.moveTo(p.x, p.y, p.z, yaw, 0.0f);
			level.addFreshEntity(corpse);
		}
	}

	// ---------------- defeat / cut out ----------------

	/** Called by the Titan when its health hits zero. */
	public static void onTitanDefeated(ServerPlayer player, TitanFormEntity form) {
		TitanShifterState s = state(player);
		if (s.phase() != TitanPhase.TITAN) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		setPhase(player, TitanPhase.DEFEATED, now + TitanShifterConfig.transformation().defeatTicks);
		form.setFormState(TitanFormEntity.FORM_DEFEATED);
		form.setHardened(false);
		form.endCharge();
		Vec3 p = form.position();
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 2.5f, 0.55f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.4f);
		form.steamBurst(level, 12);
		TitanCombat.shake(level, p, 0.9f, 30);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.defeated")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
	}

	private static void finishDefeat(ServerPlayer player, TitanFormEntity form) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		float yaw = form.getYRot();
		release(player, form);
		spawnCorpse(level, form, p, yaw, TitanCorpseEntity.CAUSE_DEFEAT);
		steamPoof(level, p, form.getBbHeight());
		var t = TitanShifterConfig.transformation();
		finishToHuman(player, TitanPhase.RECOVERING, t.recoveryTicks, 0f, true);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t.recoveryTicks, 2, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t.recoveryTicks, 1, false, true, true));
	}

	/**
	 * v0.16.7 (user): a heavy nape hit (or any nape hit under 20% HP) cuts the shifter out of the Titan at once: they are
	 * thrown out of the back of the neck, the body is left behind, Steam empties, they drop to 4 hearts and cannot shift
	 * for 60 s.
	 */
	public static void cutOut(ServerPlayer player, TitanFormEntity form) {
		TitanShifterState s = state(player);
		if (s.phase() != TitanPhase.TITAN || formOf(player) != form) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		double h = form.getBbHeight();
		Vec3 back = Vec3.directionFromRotation(0.0f, form.getYRot()).scale(-1.0);
		Vec3 nape = p.add(0.0, h * 0.8, 0.0).add(back.scale(form.getBbWidth() * 0.25));
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, nape.x, nape.y, nape.z, 40, 0.6, 0.6, 0.6, 0.08);
		level.sendParticles(ParticleTypes.CRIT, nape.x, nape.y, nape.z, 40, 0.6, 0.6, 0.6, 0.4);
		level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, nape.x, nape.y, nape.z, SoundEvents.ENDER_DRAGON_HURT, SoundSource.PLAYERS, 3.0f, 0.5f);
		TitanCombat.shake(level, p, 0.8f, 20);
		exitThroughNape(player, form, TitanCorpseEntity.CAUSE_CUT_OUT);
		var t = TitanShifterConfig.transformation();
		finishToHuman(player, TitanPhase.RECOVERING, t.recoveryTicks, 0f, true);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t.recoveryTicks, 2, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t.recoveryTicks, 1, false, true, true));
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.cut_out")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
	}

	// ---------------- shared ending ----------------

	/** Steps the shifter off the Titan onto the ground where it stood and removes the form without any death logic. */
	private static void release(ServerPlayer player, TitanFormEntity form) {
		release(player, form, true);
	}

	private static void release(ServerPlayer player, TitanFormEntity form, boolean toGround) {
		Vec3 p = form.position();
		float yaw = form.getYRot();
		SPRINT_HELD.remove(player.getUUID());
		form.releaseHeld();
		player.stopRiding();
		form.discard();
		if (toGround) {
			player.teleportTo((ServerLevel) player.level(), p.x, p.y, p.z, yaw, player.getXRot());
			player.fallDistance = 0.0f;
			player.setDeltaMovement(Vec3.ZERO);
			player.hurtMarked = true;
		}
	}

	/**
	 * Back to a human. {@code steamAfter}: the bar left (the HP refund after a reversion, 0 after a defeat / cut-out).
	 * {@code penalty}: a defeat or cut-out -- 4 hearts and a 60 s shifting lock.
	 */
	private static void finishToHuman(ServerPlayer player, TitanPhase next, int recoveryTicks, float steamAfter, boolean penalty) {
		long now = player.level().getGameTime();
		TitanShifterState s = state(player).copy();
		TitanPhase cur = s.phase();
		s.phase = (cur.canGoTo(next) ? next : TitanPhase.HUMAN).name();
		s.phaseStartedAt = now;
		s.phaseUntil = now + recoveryTicks;
		s.cooldownUntil = now + (penalty ? TitanShifterConfig.nape().lockoutTicks : TitanShifterConfig.transformation().cooldownTicks);
		s.regenUntil = 0L;
		s.hardenUntil = 0L;
		s.titanHealth = 0f;
		s.energy = Math.max(0f, Math.min((float) TitanShifterConfig.energy().max, steamAfter));
		s.endEmergency(); // an Emergency Titan ending starts the slow-refill / no-passive-regen penalty
		save(player, s);
		if (penalty) {
			float cap = (float) TitanShifterConfig.nape().penaltyHealth;
			if (player.getHealth() > cap) {
				player.setHealth(cap);
			}
		}
	}

	private static void steamPoof(ServerLevel level, Vec3 p, float height) {
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1.0, p.z, 50, 1.6, 0.5, 1.6, 0.08);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + height * 0.5, p.z, 30, 1.5, height * 0.35, 1.5, 0.03);
		level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 25, 1.4, 0.4, 1.4, 0.05);
	}

	/**
	 * Cleanly ends whatever Titan state the player is in, immediately: the form is removed, the shifter is
	 * put on the ground where the Titan stood, and the state returns to HUMAN. Used for logout, death,
	 * dimension change, admin commands and any inconsistent state. v0.16.7: Steam is refunded by the Titan's HP,
	 * as for an ordinary reversion.
	 *
	 * @param withCooldown whether the normal transformation cooldown applies afterwards
	 */
	public static void forceEnd(ServerPlayer player, boolean withCooldown) {
		TitanShifterState s = state(player);
		if (!s.unlocked || (s.phase() == TitanPhase.HUMAN && formOf(player) == null)) {
			return;
		}
		TitanFormEntity form = formOf(player);
		float frac = s.titanMaxHealth > 0f ? s.titanHealth / s.titanMaxHealth : 0f;
		if (form != null) {
			frac = form.getMaxHealth() > 0f ? form.getHealth() / form.getMaxHealth() : 0f;
			release(player, form);
		} else {
			// a stray form (e.g. the owner changed dimension) is removed by its own watchdog
			player.fallDistance = 0.0f;
		}
		boolean inside = s.phase().insideForm();
		long now = player.level().getGameTime();
		TitanShifterState n = state(player).copy();
		n.phase = TitanPhase.HUMAN.name();
		n.phaseStartedAt = now;
		n.phaseUntil = now;
		n.cooldownUntil = withCooldown ? Math.max(s.cooldownUntil, now + TitanShifterConfig.transformation().cooldownTicks) : 0L;
		n.regenUntil = 0L;
		n.hardenUntil = 0L;
		n.titanHealth = 0f;
		if (inside) {
			n.energy = TitanShifterConfig.energy().refundByHealth ? s.energy * Math.max(0f, Math.min(1f, frac)) : 0f;
		}
		n.endEmergency();
		save(player, n);
	}

	// ---------------- per-tick ----------------

	public static void tick(ServerPlayer player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null || !s.unlocked) {
			return;
		}
		TitanPhase phase = s.phase();
		if (phase == TitanPhase.HUMAN) {
			tickEnergy(player, s);
			tickBaseFormRegen(player);
			tickEmergencyHold(player, state(player));
			TitanHumanKit.tick(player);
			return;
		}
		EMERGENCY_HOLD.remove(player.getUUID());
		long now = player.level().getGameTime();
		if (phase == TitanPhase.RECOVERING) {
			tickEnergy(player, s);
			if (now >= s.phaseUntil) {
				setPhase(player, TitanPhase.HUMAN, now);
			}
			return;
		}
		TitanFormEntity form = formOf(player);
		if (form == null || !form.isAlive()) {
			// the Titan or the seat is gone (teleport, dimension change, /ride ...): end cleanly
			forceEnd(player, true);
			return;
		}
		switch (phase) {
			case TRANSFORMING -> {
				if (now >= s.phaseUntil) {
					finishTransform(player, form);
				} else if (form.isEmergency()) {
					tickEmergencyBuild(player, form, now);
				}
			}
			case TITAN -> tickTitan(player, s, form, now);
			case REVERTING -> {
				if (now >= s.phaseUntil) {
					finishRevert(player, form);
				} else {
					tickReverting(player, form);
				}
			}
			case DEFEATED -> {
				if (now >= s.phaseUntil) {
					finishDefeat(player, form);
				}
			}
			default -> {
			}
		}
		player.fallDistance = 0.0f;
		if (player.isOnFire()) {
			player.clearFire();
		}
	}

	/**
	 * v0.16.7 (user, Titan fixes): the Emergency Titan's rushed build is wracked by heavier, erratic lightning -- bolts at
	 * uneven intervals round the body (now on one side, now the other, sometimes two at once), crackling sparks off random
	 * points of the frame and the odd extra shudder.
	 */
	private static void tickEmergencyBuild(ServerPlayer player, TitanFormEntity form, long now) {
		ServerLevel level = (ServerLevel) player.level();
		var r = level.random;
		double w = form.getBbWidth(), h = form.getBbHeight();
		float p = form.formStateProgress(0.0f);
		// bolts: bursty -- ~1 in 4 ticks early, thinning as the body settles
		if (r.nextFloat() < 0.32f * (1.0f - 0.55f * p)) {
			int n = r.nextFloat() < 0.3f ? 2 : 1;
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Math.PI * 2.0, d = w * (0.3 + r.nextDouble() * 0.9);
				lightning(level, form.position().add(Math.cos(a) * d, 0.0, Math.sin(a) * d));
			}
			level.playSound(null, form.getX(), form.getY() + h * 0.5, form.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
					1.6f, 0.7f + r.nextFloat() * 0.6f);
		}
		if (now % 2 == 0) {
			Vec3 at = form.position().add((r.nextDouble() - 0.5) * w, r.nextDouble() * h, (r.nextDouble() - 0.5) * w);
			level.sendParticles(YELLOW_SPARK, at.x, at.y, at.z, 8 + r.nextInt(10), w * 0.12, h * 0.08, w * 0.12, 0.35);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, w * 0.1, h * 0.06, w * 0.1, 0.4);
		}
		if (r.nextFloat() < 0.08f) {
			TitanCombat.shake(level, form.position(), 0.5f, 5);
			level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
		}
	}

	private static void finishTransform(ServerPlayer player, TitanFormEntity form) {
		ServerLevel level = (ServerLevel) player.level();
		setPhase(player, TitanPhase.TITAN, 0L);
		form.setFormState(TitanFormEntity.FORM_NORMAL);
		if (form.isEmergency()) {
			// v0.16.7 (user, Titan fixes): the rushed body staggers as it takes its own weight, then finds its feet
			form.play("stumble");
			form.footstep(level, 1.4f, false);
			form.schedule(9, () -> {
				if (form.isAlive()) {
					form.footstep(level, 1.1f, true);
				}
			});
		}
		Vec3 p = form.position();
		lightning(level, p);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.5f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 4, 1.5, 0.2, 1.5, 0.0);
		TitanCombat.ring(level, p, 4.0, ParticleTypes.CLOUD, 30);
		TitanCombat.shake(level, p, 0.8f, 20);
		form.steamBurst(level, 8);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.transformed")
				.withStyle(ChatFormatting.GOLD), true);
	}

	private static void tickTitan(ServerPlayer player, TitanShifterState s, TitanFormEntity form, long now) {
		ServerLevel level = (ServerLevel) player.level();
		if (s.inEmergency() && now >= s.emergencyUntil) {
			// the Emergency Titan cannot hold its shape any longer: forced back out
			player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.emergency_over")
					.withStyle(ChatFormatting.RED), true);
			revert(player);
			return;
		}
		var a = TitanShifterConfig.abilities();
		if (now < s.regenUntil) {
			form.heal((float) (a.regenPerSecond / 20.0));
			if (player.tickCount % 6 == 0) {
				Vec3 p = form.position();
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + form.getBbHeight() * 0.5, p.z, 6,
						form.getBbWidth() * 0.5, form.getBbHeight() * 0.35, form.getBbWidth() * 0.5, 0.0);
				form.steamBurst(level, 2);
			}
		} else if (now % 20L == 0L) {
			tickTitanPassiveRegen(player, form);
		}
		if (now < form.biteRegenUntil) {
			form.heal((float) (a.biteRegenPerSecond / 20.0));
			if (player.tickCount % 8 == 0) {
				Vec3 bp = form.position();
				level.sendParticles(ParticleTypes.HEART, bp.x, bp.y + form.getBbHeight() * 0.6, bp.z, 3,
						form.getBbWidth() * 0.4, form.getBbHeight() * 0.25, form.getBbWidth() * 0.4, 0.0);
			}
		}
		if (player.tickCount % 10 == 0) {
			redirectMobs(level, player, form);
		}
		if (form.isHardened() && now >= s.hardenUntil) {
			form.setHardened(false);
			level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
					SoundSource.PLAYERS, 2.0f, 0.5f);
		}
		if (player.tickCount % 10 == 0) {
			TitanShifterState cur = state(player);
			float hp = form.getHealth();
			float max = form.getMaxHealth();
			if (Math.abs(hp - cur.titanHealth) >= 1.0f || Math.abs(max - cur.titanMaxHealth) >= 1.0f) {
				TitanShifterState n = cur.copy();
				n.titanHealth = hp;
				n.titanMaxHealth = max;
				save(player, n);
			}
		}
	}

	/** v0.16.7 (user): out of combat the Titan knits itself back together, ~2 HP a second, burning Steam (once a second). */
	private static void tickTitanPassiveRegen(ServerPlayer player, TitanFormEntity form) {
		var e = TitanShifterConfig.energy();
		if (e.titanRegenPerSecond <= 0.0 || form.getHealth() >= form.getMaxHealth() || !form.outOfCombat(e.outOfCombatTicks)) {
			return;
		}
		Long last = LAST_REFILL.put(player.getUUID(), player.level().getGameTime());
		if (last != null && last == player.level().getGameTime()) {
			return; // ticked twice this tick
		}
		float heal = (float) Math.min(e.titanRegenPerSecond, form.getMaxHealth() - form.getHealth());
		float cost = (float) (heal * e.titanRegenSteamPerHp);
		TitanShifterState s = state(player);
		if (s.energy <= 0f) {
			return;
		}
		if (s.energy < cost) {
			heal *= s.energy / Math.max(1.0e-4f, cost);
			cost = s.energy;
		}
		form.heal(heal);
		TitanShifterState n = s.copy();
		n.energy = Math.max(0f, s.energy - cost);
		save(player, n);
		if (form.tickCount % 40 == 0 && player.level() instanceof ServerLevel sl) {
			form.steamBurst(sl, 1);
		}
	}

	/**
	 * v0.12.34 -- mobs that were hunting the shifter cannot reach a rider sitting 10 blocks up, so they are re-aimed at the
	 * Titan itself (whose hit-box they can reach); its hurt() then lets their hits through armour.
	 */
	private static void redirectMobs(ServerLevel level, ServerPlayer player, TitanFormEntity form) {
		var box = form.getBoundingBox().inflate(28.0, 6.0, 28.0);
		for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
				m -> m.getTarget() == player)) {
			mob.setTarget(form);
		}
	}

	/**
	 * Outside the Titan the bar refills: {@code regenPerSecond} (1%) every second, in whole steps so it syncs rarely.
	 * v0.16.7: the human healing pool refills here too while it is not being spent.
	 */
	private static void tickEnergy(ServerPlayer player, TitanShifterState s) {
		var e = TitanShifterConfig.energy();
		if (s.emergencyPenalty && s.energy >= e.max) {
			TitanShifterState cleared = s.copy();
			cleared.emergencyPenalty = false; // back at a full bar: the emergency penalty is over
			save(player, cleared);
			return;
		}
		long now = player.level().getGameTime();
		if (now % 20L != 0L) {
			return;
		}
		Long last = LAST_REFILL.put(player.getUUID(), now);
		if (last != null && last == now) {
			return; // already refilled this tick (the player was ticked twice)
		}
		boolean healing = baseRegenActive(player, s);
		float pool = humanPool(player);
		boolean steamFull = s.energy >= e.max;
		boolean poolFull = pool >= e.humanPoolMax;
		if (steamFull && (poolFull || healing)) {
			return;
		}
		TitanShifterState n = s.copy();
		if (!steamFull) {
			double gain = s.emergencyPenalty ? e.regenPerSecond / Math.max(1.0, TitanShifterConfig.emergency().regenSlowdown) : e.regenPerSecond;
			n.energy = (float) Math.min(e.max, s.energy + gain);
		}
		if (!healing && !poolFull) {
			setHumanPool(n, pool + e.humanPoolRefillPerSecond);
		}
		save(player, n);
	}

	/**
	 * v0.12.34 -- the passive regeneration belongs to the shifter's BASE (human) form only: a short, hidden Regeneration
	 * effect kept topped up while the phase is HUMAN. v0.16.7: it spends the human form's own pool, never Steam.
	 */
	private static void tickBaseFormRegen(ServerPlayer player) {
		var e = TitanShifterConfig.energy();
		int amp = e.baseFormRegenAmplifier;
		TitanShifterState s = state(player);
		if (amp < 0 || !baseRegenActive(player, s)) {
			return; // the effect already on the player just lapses; nothing renews it
		}
		if (player.level().getGameTime() % 10L == 0L) {
			TitanShifterState n = s.copy();
			setHumanPool(n, humanPool(player) - e.humanPoolDrainPerSecond / 2.0);
			save(player, n);
		}
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		if (cur == null || (!cur.isInfiniteDuration() && cur.getAmplifier() <= amp && cur.getDuration() <= 20)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, amp, false, false, false));
		}
	}

	/** True while the base-form regeneration should run: a hurt human shifter with some of the healing pool left. */
	public static boolean baseRegenActive(Player player, TitanShifterState s) {
		var e = TitanShifterConfig.energy();
		// v0.12.39: after an Emergency Titan there is no passive regeneration until the bar is back to half
		boolean penalised = s.emergencyPenalty && s.energy < e.max * TitanShifterConfig.emergency().passiveRegenMinFraction;
		return e.baseFormRegenAmplifier >= 0 && humanPool(player) > 0f && !penalised && !s.regenOff
				&& s.phase() == TitanPhase.HUMAN && player.getHealth() < player.getMaxHealth();
	}

	// ---------------- lifecycle hooks ----------------

	/** Reconnect: a Titan never survives a logout, so put the shifter back on the ground as a human. */
	public static void onPlayerJoin(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked || (s.phase() == TitanPhase.HUMAN && s.titanHealth == 0f)) {
			return;
		}
		if (s.phase().insideForm() || s.phase() == TitanPhase.TITAN) {
			snapToGround(player);
		}
		long now = player.level().getGameTime();
		TitanShifterState n = s.copy();
		if (s.phase().insideForm() && s.titanMaxHealth > 0f) {
			// v0.16.7: refunded by the Titan's last HP, as for a reversion
			n.energy = s.energy * Math.max(0f, Math.min(1f, s.titanHealth / s.titanMaxHealth));
		}
		n.phase = TitanPhase.HUMAN.name();
		n.phaseUntil = now;
		n.regenUntil = 0L;
		n.hardenUntil = 0L;
		n.titanHealth = 0f;
		n.endEmergency();
		// game time is per-world: keep a sane cooldown that cannot exceed the configured one
		n.cooldownUntil = Math.min(n.cooldownUntil, now + Math.max(TitanShifterConfig.transformation().cooldownTicks,
				TitanShifterConfig.nape().lockoutTicks));
		n.abilityReadyAt.remove(COCOON_KEY);
		n.abilityReadyAt.entrySet().removeIf(e -> !e.getKey().startsWith("~") && e.getValue() > now + 20L * 60L * 5L);
		save(player, n);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked) {
			return;
		}
		TitanShifterState n = s.copy();
		n.phase = TitanPhase.HUMAN.name();
		n.phaseUntil = 0L;
		n.cooldownUntil = 0L;
		n.regenUntil = 0L;
		n.hardenUntil = 0L;
		n.titanHealth = 0f;
		n.endEmergency();
		n.abilityReadyAt.clear();
		save(player, n);
	}

	/** Drop straight down to the first solid ground below (used after a logout from inside a Titan). */
	private static void snapToGround(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
		for (int i = 0; i < 40 && pos.getY() > level.getMinBuildHeight(); i++) {
			if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				player.teleportTo(level, player.getX(), pos.getY(), player.getZ(), player.getYRot(), player.getXRot());
				player.fallDistance = 0.0f;
				return;
			}
			pos.move(0, -1, 0);
		}
	}

	public static void clearTransient(ServerPlayer player) {
		LAST_MESSAGE.remove(player.getUUID());
		EMERGENCY_HOLD.remove(player.getUUID());
		PENDING_PICK.remove(player.getUUID());
		forceEnd(player, true);
	}

	public static void onServerStopping(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			forceEnd(p, true);
		}
	}
}
