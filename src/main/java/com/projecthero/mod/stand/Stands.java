package com.projecthero.mod.stand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.stand.data.StandPoseState;
import com.projecthero.mod.stand.data.StandState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Stands (v0.17.2), the Hero-Tier power {@code stand}. Nothing else touches
 * {@link StandState}; every change re-saves through {@link ServerPlayer#setAttached}. Star Platinum's moves live in
 * {@link StarPlatinum}, the hit rules in {@link StandCombat}, the time stop in {@link TimeStop}, the numbers in
 * {@link StandConfig}.
 *
 * <h2>The user's rule: only Stand users perceive a Stand</h2>
 * The state is synced only to its owner and to other Stand users ({@link #SYNC_PREDICATE}); when someone new awakens a
 * Stand every Stand user's copy is re-sent ({@link #resyncAll}) so they see the others. Every Stand sound and particle
 * goes out per player to Stand users only ({@link #sound}, {@link #particles}) -- never {@code level.playSound(null, ...)}.
 * What a Stand DOES to a victim (the damage, the knockback) shows for everyone, as it should.
 */
public final class Stands {
	/**
	 * v0.17.2 (user: "i dont want the only stand to be star platinum so in the command dont make it power grant stand make it power
	 * grant stand_star_platinum"): each Stand is its own Hero-Tier key, {@code stand_<type id>}; Star Platinum is the only one yet.
	 */
	public static final String KEY = "stand_star_platinum";

	/** The Hero-Tier key of {@code type}: {@code stand_<id>}. */
	public static String keyFor(StandType type) {
		return "stand_" + type.id();
	}

	/** Holds this particular Stand. */
	public static boolean holds(Player player, StandType type) {
		StandState s = peek(player);
		return s != null && s.isUser() && type.id().equals(s.standId);
	}

	/** Who receives a player's {@link StandState}: the player, and other Stand users. */
	public static final AttachmentSyncPredicate SYNC_PREDICATE = (target, viewer) -> target == viewer || isStandUser(viewer);

	private static final ResourceLocation REACH_ID = ProjectHeroMod.id("stand_punch_reach");

	/** Stand purple and a pale violet. */
	public static final DustParticleOptions AURA = new DustParticleOptions(new Vector3f(0.6f, 0.36f, 1.0f), 1.2f);
	public static final DustParticleOptions AURA_BIG = new DustParticleOptions(new Vector3f(0.7f, 0.5f, 1.0f), 2.2f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
	/** Game time each Stand user last dealt or took combat damage (Stamina refills fast only out of a fight). */
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();

	private Stands() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		LAST_COMBAT.clear();
		StarPlatinum.clearSessionState();
		StandAbilityManager.clearSessionState();
		StandCombat.clearSessionState();
		TimeStop.clear();
	}

	// ---------------------------------------------------------------- state

	public static StandState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.STAND_STATE);
	}

	static void save(ServerPlayer player, StandState state) {
		player.setAttached(ModAttachments.STAND_STATE, state);
	}

	/** The synced state, or null (client-safe; another player's is null on a non-Stand user's client). */
	public static StandState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.STAND_STATE, null);
	}

	/** Has a Stand. Client-safe. */
	public static boolean isStandUser(Player player) {
		StandState s = peek(player);
		return s != null && s.isUser();
	}

	/** The Hero-Tier power check (same as {@link #isStandUser}). */
	public static boolean hasPower(Player player) {
		return isStandUser(player);
	}

	public static StandType type(Player player) {
		StandState s = peek(player);
		return s == null ? null : StandType.byId(s.standId);
	}

	/** The Stand is out. Client-safe. */
	public static boolean summoned(Player player) {
		StandState s = peek(player);
		return s != null && s.isUser() && s.summoned;
	}

	public static float stamina(Player player) {
		StandState s = peek(player);
		return s == null ? 0f : s.stamina;
	}

	public static int cooldownRemaining(Player player, String moveId) {
		StandState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(moveId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	public static boolean ready(ServerPlayer player, String moveId) {
		return cooldownRemaining(player, moveId) <= 0;
	}

	static void setCooldown(ServerPlayer player, String moveId, int ticks) {
		StandState n = state(player).copy();
		n.abilityReadyAt.put(moveId, player.level().getGameTime() + ticks);
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

	public static boolean spendStamina(ServerPlayer player, float cost) {
		if (cost <= 0f || TimeStop.isCaster(player)) {
			return true; // inside your own time stop every move is free (the stop itself drains 10 a second)
		}
		StandState s = state(player);
		if (s.stamina + 1.0e-3f < cost) {
			say(player, "message.projecthero.stand.no_stamina", ChatFormatting.GRAY);
			return false;
		}
		StandState n = s.copy();
		n.stamina = Math.max(0f, Math.min(StandConfig.STAMINA_MAX, s.stamina) - cost);
		save(player, n);
		return true;
	}

	public static void setStamina(ServerPlayer player, float value) {
		StandState n = state(player).copy();
		n.stamina = Math.max(0f, Math.min(StandConfig.STAMINA_MAX, value));
		save(player, n);
	}

	static void markCombat(ServerPlayer player) {
		LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
	}

	/** Dealt or took combat damage in the last 2 s. */
	static boolean inCombat(ServerPlayer player) {
		Long last = LAST_COMBAT.get(player.getUUID());
		long now = player.level().getGameTime();
		return last != null && now - last < 40 && now >= last;
	}

	static void setAnim(ServerPlayer player, int animId) {
		StandState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}

	// ---------------------------------------------------------------- only Stand users perceive it

	/** A Stand sound at {@code at}, sent one by one to the Stand users in range (nobody else hears it). */
	public static void sound(ServerLevel level, Vec3 at, SoundEvent event, float volume, float pitch) {
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event);
		long seed = level.getRandom().nextLong();
		double r2 = StandConfig.FX_RANGE * StandConfig.FX_RANGE;
		for (ServerPlayer viewer : level.players()) {
			if (isStandUser(viewer) && viewer.distanceToSqr(at) < r2) {
				viewer.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS, at.x, at.y, at.z, volume, pitch, seed));
			}
		}
	}

	/** Stand particles, sent one by one to the Stand users in the level (nobody else sees them). */
	public static void particles(ServerLevel level, ParticleOptions options, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		for (ServerPlayer viewer : level.players()) {
			if (isStandUser(viewer)) {
				level.sendParticles(viewer, options, false, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	/**
	 * v0.17.2: a rendered Stand effect ({@link com.projecthero.mod.stand.network.StandFxPayload}), sent to the Stand users in range
	 * (nobody else sees it) -- the flashes, rings and beams that replaced the violet dust.
	 */
	public static void vfx(ServerLevel level, int kind, Vec3 at, Vec3 dir, float size) {
		var payload = new com.projecthero.mod.stand.network.StandFxPayload(kind, at.x, at.y, at.z, dir.x, dir.y, dir.z, size);
		double r2 = StandConfig.FX_RANGE * StandConfig.FX_RANGE;
		for (ServerPlayer viewer : level.players()) {
			if (isStandUser(viewer) && viewer.distanceToSqr(at) < r2) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	/**
	 * Re-sends every online Stand user's state (a fresh copy, so Fabric syncs it again): called when someone becomes a Stand
	 * user, whose client then receives the others' Stands it was not allowed to see before.
	 */
	public static void resyncAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			StandState s = p.getAttachedOrElse(ModAttachments.STAND_STATE, null);
			if (s != null && s.isUser()) {
				save(p, s.copy());
			}
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The Stand Arrow / the command: awaken {@code type}. False if the player already has a Stand. */
	public static boolean grant(ServerPlayer player, StandType type) {
		if (state(player).isUser()) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, keyFor(type))) {
			return false;
		}
		HeroTiers.claimPrimary(player, keyFor(type));
		StandState s = new StandState();
		s.standId = type.id();
		s.stamina = StandConfig.STAMINA_MAX;
		save(player, s);
		resyncAll(player.getServer());
		TimeStop.sendStateTo(player); // a Stand user sees a running stop's colour drain
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		vfx(level, com.projecthero.mod.stand.network.StandFxPayload.AURA, player.position(), new Vec3(0, 1, 0), 1.4f);
		sound(level, c, StandSounds.AWAKEN, 1.0f, 1.0f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable(type.nameKey()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.stand.subtitle").withStyle(ChatFormatting.DARK_PURPLE)));
		player.displayClientMessage(Component.translatable("message.projecthero.stand.acquired", Component.translatable(type.nameKey()))
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.stand.acquired_hint").withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		StarPlatinum.clear(player);
		TimeStop.onCasterGone(player);
		save(player, new StandState());
		player.setAttached(ModAttachments.STAND_POSE, StandPoseState.NONE);
		reconcile(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		LAST_COMBAT.remove(id);
		StandAbilityManager.forget(id);
		StarPlatinum.forget(id);
	}

	// ---------------------------------------------------------------- H: summon / dismiss

	public static void toggleSummon(ServerPlayer player) {
		StandState s = state(player);
		if (!s.isUser() || !player.isAlive() || player.isSpectator() || TimeStop.frozen(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < StandConfig.SUMMON_TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.summoned) {
			dismiss(player);
		} else {
			summon(player);
		}
	}

	/** Star Platinum appears behind you with a menacing burst. */
	public static void summon(ServerPlayer player) {
		StandState s = state(player);
		if (!s.isUser() || s.summoned) {
			return;
		}
		StandState n = s.copy();
		n.summoned = true;
		n.animId = StandState.ANIM_SUMMON;
		n.animStart = player.level().getGameTime();
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		// v0.17.3 (user: "when star platinum gets summoned make him come out of the player"): the glow rises off its user's body,
		// where it steps out of them (StandRender slides it back to its spot)
		Vec3 c = player.position();
		vfx(level, com.projecthero.mod.stand.network.StandFxPayload.AURA, c, new Vec3(0, 1, 0), 1.0f);
		sound(level, c.add(0, 1.0, 0), StandSounds.SUMMON, 0.6f, 1.0f);
		sound(level, c.add(0, 1.0, 0), StandSounds.MENACING, 0.4f, 1.0f);
	}

	public static void dismiss(ServerPlayer player) {
		StandState s = state(player);
		if (!s.summoned) {
			return;
		}
		StarPlatinum.clear(player);
		StandState n = state(player).copy();
		n.summoned = false;
		n.animId = StandState.ANIM_DISMISS;
		n.animStart = player.level().getGameTime();
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		vfx(level, com.projecthero.mod.stand.network.StandFxPayload.AURA, c, new Vec3(0, -1, 0), 0.8f);
		sound(level, c.add(0, 1.0, 0), StandSounds.DISMISS, 0.5f, 1.0f);
	}

	/**
	 * v0.17.3 (user: "if player tries to mine a block with an empty hand while star platinum is active star platinum helps and breaks
	 * the block"): the Stand is out, at its user's side, and the main hand is empty -- the hand mines like iron tools
	 * ({@code StandMiningMixin}). Client-safe (both sides work the break speed out).
	 */
	public static boolean standMines(Player player) {
		StandState s = peek(player);
		return s != null && s.isUser() && s.summoned && !s.chasing() && player.getMainHandItem().isEmpty() && !player.isSpectator();
	}

	/** The Stand's passives on the body: +1 block of punch reach while it is out. */
	public static void reconcile(ServerPlayer player) {
		AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (reach == null) {
			return;
		}
		boolean want = summoned(player);
		boolean has = reach.getModifier(REACH_ID) != null;
		if (want && !has) {
			reach.addTransientModifier(new AttributeModifier(REACH_ID, StandConfig.PUNCH_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE));
		} else if (!want && has) {
			reach.removeModifier(REACH_ID);
		}
	}

	// ---------------------------------------------------------------- N: pose

	/** N: strike the chosen pose (or drop it). */
	public static void togglePose(ServerPlayer player) {
		StandState s = state(player);
		if (!s.isUser()) {
			return;
		}
		StandPoseState cur = player.getAttachedOrElse(ModAttachments.STAND_POSE, StandPoseState.NONE);
		if (cur.pose() != 0) {
			player.setAttached(ModAttachments.STAND_POSE, StandPoseState.NONE);
			return;
		}
		int pose = Math.max(1, Math.min(StandConfig.POSE_COUNT, s.pose));
		player.setAttached(ModAttachments.STAND_POSE, new StandPoseState(pose, player.level().getGameTime()));
	}

	public static void setPoseChoice(ServerPlayer player, int pose) {
		StandState n = state(player).copy();
		n.pose = Math.max(1, Math.min(StandConfig.POSE_COUNT, pose));
		save(player, n);
	}

	public static void setSettings(ServerPlayer player, boolean oraText, boolean autoFace) {
		StandState n = state(player).copy();
		n.oraText = oraText;
		n.autoFace = autoFace;
		save(player, n);
	}

	private static void tickPose(ServerPlayer player, long now) {
		StandPoseState pose = player.getAttachedOrElse(ModAttachments.STAND_POSE, StandPoseState.NONE);
		if (pose.pose() == 0) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		boolean moving = v.x * v.x + v.z * v.z > 0.004 || player.swinging || !player.onGround();
		if (moving && now - pose.start() > 5) {
			player.setAttached(ModAttachments.STAND_POSE, StandPoseState.NONE);
			return;
		}
		if (now - pose.start() >= StandConfig.POSE_RESIST_AFTER && now % 10 == 0) {
			// holding the pose: a nod to "menacing"
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, false, true));
		}
	}

	/** Any move drops the pose. */
	static void breakPose(ServerPlayer player) {
		if (player.getAttachedOrElse(ModAttachments.STAND_POSE, StandPoseState.NONE).pose() != 0) {
			player.setAttached(ModAttachments.STAND_POSE, StandPoseState.NONE);
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		StandState s = player.getAttachedOrElse(ModAttachments.STAND_STATE, null);
		if (s == null || !s.isUser()) {
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
			tickStamina(player, s);
		}
		tickPose(player, now);
		// v0.17.2 (user: "give stand users regen 1"): a constant Regeneration I, topped up before it runs out (it heals on
		// every 50th tick of its countdown, so it is kept above that)
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen == null || (regen.getAmplifier() == 0 && regen.getDuration() < 60)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, true, false, true));
		}
		StarPlatinum.tick(player);
	}

	/** v0.17.3 (user: "stand stamina regens 3 stamina per second"): Stamina per second right now (half for 20 s after a time stop). */
	public static float regenRate(Player player) {
		StandState s = peek(player);
		boolean slow = s != null && s.regenSlowUntil > player.level().getGameTime();
		return StandConfig.STAMINA_REGEN * (slow ? StandConfig.REGEN_SLOW_FACTOR : 1f);
	}

	private static void tickStamina(ServerPlayer player, StandState s) {
		if (s.rushing || TimeStop.isCaster(player) || TimeStop.windingUpFor(player)) {
			return;
		}
		float st = Float.isNaN(s.stamina) ? 0f : s.stamina;
		float next = Math.min(StandConfig.STAMINA_MAX, st + regenRate(player) / 4f);
		if (next != s.stamina) {
			StandState n = s.copy();
			n.stamina = next;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		StandState s = state(player);
		if (s.isUser()) {
			long now = player.level().getGameTime();
			StandState n = cleared(s);
			n.summoned = false;
			n.stamina = Float.isNaN(n.stamina) ? 0f : Math.max(0f, Math.min(StandConfig.STAMINA_MAX, n.stamina));
			n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + StandConfig.TIME_STOP_COOLDOWN + 20L);
			save(player, n);
			reconcile(player);
		}
		player.setAttached(ModAttachments.STAND_POSE, StandPoseState.NONE);
		TimeStop.sendStateTo(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		StandState s = state(player);
		if (!s.isUser()) {
			return;
		}
		StandState n = cleared(s);
		n.summoned = false;
		n.stamina = StandConfig.STAMINA_MAX;
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
	}

	private static StandState cleared(StandState s) {
		StandState n = s.copy();
		n.rushing = false;
		n.guarding = false;
		n.chargeStart = 0L;
		n.grabbedEntity = -1;
		n.grabbedBlock = 0;
		n.leapRebounds = -1;
		n.inhaleUntil = 0L;
		n.dashUntil = 0L;
		n.fingerEnd = java.util.List.of();
		n.comboStep = 0;
		n.smashStep = 0;
		n.chaseStart = 0L;
		n.chaseGrabAt = 0L;
		n.chaseTarget = -1;
		n.chasePos = java.util.List.of();
		n.grabCombo = 0;
		n.animId = StandState.ANIM_NONE;
		return n;
	}

	/** Death / logout / dimension change: drop everything transient (the Stand stays yours). */
	public static void clearTransient(ServerPlayer player) {
		StarPlatinum.clear(player);
		TimeStop.onCasterGone(player);
		forget(player.getUUID());
		StandState s = player.getAttachedOrElse(ModAttachments.STAND_STATE, null);
		if (s != null && s.isUser() && (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0
				|| s.leapRebounds != -1 || s.inhaleUntil != 0L || s.dashUntil != 0L || s.chaseStart != 0L || s.smashStep != 0)) {
			save(player, cleared(s));
		}
	}
}
