package com.projecthero.mod.hero;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Erasure (Eraser Head) -- a creature whose power is cancelled. While {@link #isSuppressed} a PLAYER can not use
 * any power at all (every system's keys: mutations, Hero-Tier powers, the Miraculous, the Quirks, H / N actions; running
 * toggles / flight / forms are paused by their owners checking this), and a MOB loses its abilities (creepers do not
 * swell, skeletons do not shoot, endermen do not teleport, bosses' special moves are held).
 *
 * <p>Server-authoritative: the Erasure kit calls {@link #suppress} every tick it keeps its stare on someone (a short
 * window, so the effect ends a moment after the stare breaks). The core syncs a player's own flag to that client
 * ({@link #clientUntil}) so the HUD can grey out. Static state: cleared by {@code ServerStateReset}.
 *
 * <p><b>Enforcement (players)</b> -- one choke point per input path, so no power has to be rewritten:
 * <ul>
 *   <li>{@code AbilityRouter.handleInput}: key PRESSES are refused for every system ({@link #blocksAbility}); releases
 *       still pass so held moves end;</li>
 *   <li>every power's H / N / action payload receiver: {@link #refuse} (presses) / {@link #refuse(ServerPlayer, Enum)}
 *       (lets a *_STOP / *_OFF / *_RELEASE through);</li>
 *   <li>{@link #tickPlayer} (every player, every tick): power flight is cut and kept off, mutation passives
 *       ({@code PowerPassives.reconcileActive}) are off while erased, the "Erased" status effect shows the time left and
 *       the owner's client is told ({@link SyncPayload}).</li>
 * </ul>
 * <b>Machines are not powers</b>: an Iron Man suit (its keys, its flight, Call Armour), the Punisher's guns and kit and
 * Stark gadgets keep working ({@link #isTech}).
 */
public final class PowerSuppression {
	/** entity UUID -> game time the suppression ends. */
	private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();
	/** player UUID -> the "until" last sent to that player's client (0 = told "not erased"). */
	private static final Map<UUID, Long> SENT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	/** Client mirror for the local player (set by the sync payload). */
	public static volatile long clientUntil;
	/** Client: whose flag {@link #clientUntil} is (the local player); null until the first sync. */
	public static volatile UUID clientPlayer;

	/** The client copy runs this many ticks past the server's end, so a stare renewed every tick never flickers. */
	private static final int SYNC_PAD = 20;
	/** Re-sent once the server's end has moved this far past what the client was told. */
	private static final int SYNC_STEP = 10;

	private PowerSuppression() {
	}

	/** From {@code QuirkMod.initialize}: the sync payload and the "Erased" effect. */
	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(SyncPayload.TYPE, SyncPayload.CODEC);
		ErasedEffect.initialize();
	}

	/** Cancel {@code target}'s power for {@code ticks} more ticks from now (extends, never shortens). */
	public static void suppress(LivingEntity target, int ticks) {
		long end = target.level().getGameTime() + ticks;
		UNTIL.merge(target.getUUID(), end, Math::max);
	}

	/** Lift it at once. */
	public static void release(Entity target) {
		UNTIL.remove(target.getUUID());
	}

	/** Server side; client side for the LOCAL player too (via {@link #clientUntil}). */
	public static boolean isSuppressed(Entity e) {
		if (e == null) {
			return false;
		}
		if (e.level().isClientSide()) {
			UUID local = clientPlayer;
			return e instanceof Player && local != null && local.equals(e.getUUID()) && clientUntil > e.level().getGameTime();
		}
		Long end = UNTIL.get(e.getUUID());
		if (end == null) {
			return false;
		}
		if (end <= e.level().getGameTime()) {
			UNTIL.remove(e.getUUID());
			return false;
		}
		return true;
	}

	/** Ticks left (server). */
	public static int remaining(Entity e) {
		Long end = UNTIL.get(e.getUUID());
		return end == null ? 0 : (int) Math.max(0L, end - e.level().getGameTime());
	}

	/** Client: ticks left for the local player (0 when not erased). */
	public static int clientRemaining(Player localPlayer) {
		return isSuppressed(localPlayer) ? (int) Math.max(0L, clientUntil - SYNC_PAD - localPlayer.level().getGameTime()) : 0;
	}

	public static void clearSessionState() {
		UNTIL.clear();
		SENT.clear();
		LAST_MESSAGE.clear();
		clientUntil = 0L;
		clientPlayer = null;
	}

	// ---------------------------------------------------------------- enforcement

	/**
	 * A power input from {@code player}: true (and the action bar says "Your power has been erased!") if it must be
	 * dropped. Every power's H / N / action payload receiver calls this first.
	 */
	public static boolean refuse(ServerPlayer player) {
		if (!isSuppressed(player)) {
			return false;
		}
		say(player);
		return true;
	}

	/**
	 * {@link #refuse(ServerPlayer)} for a payload with an action enum: an action whose name ends a running thing
	 * ({@code *_STOP}, {@code *_OFF}, {@code *_RELEASE}) always passes, so a hold that began before the stare can end.
	 */
	public static boolean refuse(ServerPlayer player, Enum<?> action) {
		if (action != null) {
			String n = action.name();
			if (n.endsWith("_STOP") || n.endsWith("_OFF") || n.endsWith("RELEASE")) {
				return false;
			}
		}
		return refuse(player);
	}

	private static void say(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 20 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable("message.projecthero.power.erased").withStyle(ChatFormatting.RED), true);
	}

	/**
	 * Machines, not powers: an Iron Man suit (worn and driving the keys, or Tony's unsuited C to call one) and the
	 * Punisher's kit keep working under Erasure.
	 */
	public static boolean isTech(ServerPlayer player, AbilitySlot slot) {
		if (com.projecthero.mod.ironman.ability.IronManAbilityManager.hasContext(player)) {
			return true;
		}
		if (slot == AbilitySlot.SLOT_6 && com.projecthero.mod.ironman.TonyStark.hasPower(player)
				&& !com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(player)) {
			return true;
		}
		return com.projecthero.mod.punisher.PunisherAbilityManager.hasContext(player);
	}

	/** {@code AbilityRouter.handleInput}: this key edge must be dropped (a press, erased, not a machine). */
	public static boolean blocksAbility(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed || !isSuppressed(player) || isTech(player, slot)) {
			return false;
		}
		say(player);
		return true;
	}

	private static boolean techFlying(ServerPlayer player) {
		return com.projecthero.mod.ironman.IronManFlight.isFlying(player) || com.projecthero.mod.ironman.RepulsorBoots.isFlying(player);
	}

	/**
	 * Every player, every server tick (from {@code AbilityRouter.serverTick}, last): the client sync, the status effect,
	 * power flight held off, mutation passives paused / restored on the edges.
	 */
	public static void tickPlayer(ServerPlayer player) {
		boolean erased = isSuppressed(player);
		UUID id = player.getUUID();
		Long sent = SENT.get(id);
		boolean was = sent != null && sent != 0L;
		if (erased) {
			long end = UNTIL.getOrDefault(id, 0L);
			if (!was || end + SYNC_PAD - sent >= SYNC_STEP) {
				long until = end + SYNC_PAD;
				SENT.put(id, until);
				send(player, until);
			}
			int left = remaining(player);
			MobEffectInstance cur = player.getEffect(ErasedEffect.HOLDER);
			if (cur == null || Math.abs(cur.getDuration() - left) > 30) {
				player.addEffect(new MobEffectInstance(ErasedEffect.HOLDER, Math.max(left, 20) + 10, 0, false, false, true));
			}
			if (!was) {
				onErased(player);
			}
			if (!player.isCreative() && !player.isSpectator() && !techFlying(player)
					&& (player.getAbilities().flying || player.getAbilities().mayfly)) {
				player.getAbilities().flying = false;
				player.getAbilities().mayfly = false;
				player.onUpdateAbilities();
			}
		} else if (was) {
			SENT.put(id, 0L);
			send(player, 0L);
			player.removeEffect(ErasedEffect.HOLDER);
			PowerPassives.reconcileActive(player);
		}
	}

	/** The stare just landed on {@code player}: hero flight ends, mutation passives go off. */
	private static void onErased(ServerPlayer player) {
		if (com.projecthero.mod.hero.power.HeroFlight.isFlying(player)) {
			com.projecthero.mod.hero.power.HeroFlight.setFlying(player, false);
		}
		PowerPassives.reconcileActive(player);
	}

	/** Logout / respawn: the next tick re-sends from scratch. */
	public static void forget(ServerPlayer player) {
		SENT.remove(player.getUUID());
		LAST_MESSAGE.remove(player.getUUID());
	}

	private static void send(ServerPlayer player, long until) {
		try {
			if (ServerPlayNetworking.canSend(player, SyncPayload.TYPE)) {
				ServerPlayNetworking.send(player, new SyncPayload(until));
			}
		} catch (RuntimeException e) {
			// a gametest mock player has no real connection
		}
	}

	// ---------------------------------------------------------------- sync + effect

	/** Server to the erased player's own client: Erased until this game time ({@code 0} = not erased). */
	public record SyncPayload(long until) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("power_suppression"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, SyncPayload::until, SyncPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** "Erased": a display-only status effect (no attribute changes) so the player sees it in the inventory. */
	public static final class ErasedEffect extends MobEffect {
		public static final Holder<MobEffect> HOLDER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
				ProjectHeroMod.id("erased"), new ErasedEffect());

		private ErasedEffect() {
			super(MobEffectCategory.HARMFUL, 0xE04040);
		}

		static void initialize() {
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return false;
		}
	}
}
