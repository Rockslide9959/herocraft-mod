package com.projecthero.mod.omnitrix;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix, Ben 10's watch, as a Hero-Tier power ({@link #KEY} {@code "omnitrix"}).
 *
 * <h2>The lock</h2>
 * Once it is on the wrist it cannot be swapped out: while it is worn every way of gaining another power refuses with "The
 * Omnitrix rejects it." -- {@code UniquePowers.refuse} (every hero's grant routine and every acquisition item ask it first),
 * {@code PowerGrants.grantHero} / {@code grantExperimental} (the random serums, the admin grant), the admin command without
 * {@code force}, mutation exposure ({@code MutationManager}) and the power wheel's swap. It survives death (the attachment is
 * {@code copyOnDeath}). The only way off is the Omnitrix Containment Pod ({@code OmnitrixExtraction}), which pops it off as an
 * item; whoever puts that on is locked in the same way. An operator's {@code force} grant is the one override.
 *
 * <h2>Transforming (Alien Evo style: everything happens on the watch, no screen)</h2>
 * V as a human raises the wrist: the core pops up and the face shows the selected alien's silhouette as a little hologram
 * ({@link #activate}). The scroll wheel turns the dial through the aliens ({@link #cycle}); a left-click (or right-click) slaps
 * the core down ({@link #slap}) -- the green DNA helix spirals up the body and the wearer becomes that alien. V again lowers the
 * watch ({@link #deactivate}); V while transformed reverts early (Shift+V is an alias). A transformation lasts {@link OmnitrixConfig#TRANSFORM_TICKS} (3 min);
 * the watch light flashes red for the last 15 s, then the wearer times out with the chime and a red flash, and the watch
 * recharges for 90 s. The alien's body ({@code AlienStats}) applies while transformed and health converts by fraction.
 */
public final class Omnitrix {
	public static final String KEY = "omnitrix";

	/** Framework poses (kit poses use 1..999). */
	public static final int ANIM_TRANSFORM = 1000;
	public static final int ANIM_REVERT = 1001;
	public static final int ANIM_TIMEOUT = 1002;
	public static final int ANIM_SLAP = 1003;
	/** The dial turning one alien on (the scroll wheel). */
	public static final int ANIM_TURN = 1004;
	/** V during the recharge: the face flashes red with the time left. */
	public static final int ANIM_DENIED = 1005;
	/** Ticks between the slap and the transformation (the core going down). */
	public static final int SLAP_TICKS = 5;
	private static final String SLAP_TIMER = "omnitrix.slap";

	/** Why the wearer turned back. */
	public enum Reason {
		SHIFT_V, TIMEOUT, DEATH, LOGOUT, EXTRACTION, REVOKE, SUPPRESSED
	}

	private static final OmnitrixState DEFAULT = new OmnitrixState();
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 0.9f);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.12f), 0.9f);
	/** player -> last game time the "rejects it" line was shown (a Mjolnir on the ground is touched every tick). */
	private static final Map<UUID, Long> LAST_REJECT = new ConcurrentHashMap<>();

	private Omnitrix() {
	}

	// ---------------------------------------------------------------- state

	/** Client-safe: the synced state (a shared empty default when there is none -- never mutate it, always {@code copy()}). */
	public static OmnitrixState state(Player p) {
		return p.getAttachedOrElse(OmnitrixAttachments.STATE, DEFAULT);
	}

	public static void save(ServerPlayer p, OmnitrixState s) {
		p.setAttached(OmnitrixAttachments.STATE, s);
	}

	public static boolean hasPower(Player p) {
		return p != null && state(p).hasPower;
	}

	/** Client-safe: the alien the wearer is right now, or "" (human / no watch). */
	public static String active(Player p) {
		return hasPower(p) ? state(p).active : "";
	}

	public static boolean transformed(Player p) {
		return !active(p).isEmpty();
	}

	/** Ticks left in the current transformation (0 when human). */
	public static int timeLeft(Player p) {
		OmnitrixState s = state(p);
		return s.transformed() ? (int) Math.max(0L, s.endsAt - p.level().getGameTime()) : 0;
	}

	/** Ticks of recharge left (0 = ready). */
	public static int rechargeLeft(Player p) {
		return (int) Math.max(0L, state(p).rechargeUntil - p.level().getGameTime());
	}

	/** Client-safe: whether the pod is pulling the watch off this player right now. */
	public static boolean extracting(Player p) {
		return p.getAttached(OmnitrixAttachments.EXTRACTION) != null;
	}

	// ---------------------------------------------------------------- the lock

	/**
	 * The lock: true (and "The Omnitrix rejects it." shown, throttled) if {@code p} wears the Omnitrix and {@code key} is any
	 * other power. An operator's {@code force} ({@code UniquePowers.forced}) lets it through.
	 */
	public static boolean rejects(ServerPlayer p, String key) {
		if (p == null || !hasPower(p) || KEY.equals(key) || com.projecthero.mod.hero.UniquePowers.isForced()) {
			return false;
		}
		tellRejected(p);
		return true;
	}

	/** {@link #rejects} for anything that is not a named power (a mutation, a power-wheel swap). */
	public static boolean rejectsAny(ServerPlayer p) {
		return rejects(p, "");
	}

	public static net.minecraft.network.chat.MutableComponent rejectMessage() {
		return Component.translatable("message.projecthero.omnitrix.rejects").withStyle(ChatFormatting.GREEN);
	}

	private static void tellRejected(ServerPlayer p) {
		long now = p.server == null ? 0L : p.server.getTickCount();
		Long last = LAST_REJECT.get(p.getUUID());
		if (last == null || now - last >= 20 || now < last) {
			LAST_REJECT.put(p.getUUID(), now);
			p.displayClientMessage(rejectMessage(), false);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), OmnitrixSounds.DIAL, SoundSource.PLAYERS, 0.8f, 0.6f);
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	/**
	 * The watch jumps onto the wrist (the pod's touch, the popped item, the command). Claims the one Primary slot (whatever was
	 * held goes). False if already worn or the power is taken (unique powers).
	 */
	public static boolean grant(ServerPlayer p) {
		if (hasPower(p)) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(p, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(p, KEY);
		OmnitrixState s = new OmnitrixState();
		s.hasPower = true;
		s.selected = Aliens.playable().isEmpty() ? Aliens.HEATBLAST.id() : Aliens.playable().get(0).id();
		s.animId = ANIM_SLAP;
		s.animStart = p.level().getGameTime();
		save(p, s);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(GREEN, c.x, c.y, c.z, 30, 0.6, 0.9, 0.6, 0.0);
		level.playSound(null, c.x, c.y, c.z, OmnitrixSounds.SLAP, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(6, 60, 20));
		p.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.omnitrix.subtitle").withStyle(ChatFormatting.GRAY)));
		p.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.omnitrix.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.acquired").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/** Tears the power down (the pod's success, an admin revoke, a forced replacement). Turns the wearer back first. */
	public static void revoke(ServerPlayer p) {
		if (!hasPower(p)) {
			return;
		}
		if (state(p).transformed()) {
			revert(p, Reason.REVOKE);
		}
		save(p, new OmnitrixState());
		p.removeAttached(OmnitrixAttachments.EXTRACTION);
		forget(p.getUUID());
	}

	// ---------------------------------------------------------------- transform / revert

	/** V: become the alien picked on the dial. False (with the reason on the action bar) if it cannot happen now. */
	public static boolean transform(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.hasPower || s.transformed()) {
			return false;
		}
		long now = p.level().getGameTime();
		if (extracting(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.held_by_pod").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.suppressed").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (now < s.rechargeUntil) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recharging",
					(s.rechargeUntil - now + 19) / 20).withStyle(ChatFormatting.RED), true);
			p.playNotifySound(OmnitrixSounds.DIAL, SoundSource.PLAYERS, 0.7f, 0.5f);
			return false;
		}
		AlienKit kit = Aliens.kit(s.selected);
		if (kit == null) {
			Aliens.Alien a = Aliens.byId(s.selected);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(a == null ? "projecthero.omnitrix.alien.unknown" : a.nameKey())).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		float frac = p.getMaxHealth() > 0f ? p.getHealth() / p.getMaxHealth() : 1f;
		OmnitrixState n = s.copy();
		n.active = kit.id();
		n.transformedAt = now;
		n.endsAt = now + OmnitrixConfig.TRANSFORM_TICKS;
		n.animId = ANIM_TRANSFORM;
		n.animStart = now;
		n.humanHealth = frac;
		n.kitFlags = 0;
		n.heldEntity = 0;
		n.selecting = false;
		n.timers.remove(SLAP_TIMER);
		save(p, n);
		OmnitrixBody.apply(p, kit.stats(), frac);
		helixFx(p, false);
		kit.onTransform(p);
		Aliens.Alien a = Aliens.byId(kit.id());
		p.displayClientMessage(Component.translatable(a.nameKey()).withStyle(st -> st.withColor(a.colour()).withBold(true)), true);
		return true;
	}

	/** Turns the wearer back to human. A timeout starts the 90 s recharge; Shift+V a short one. */
	public static void revert(ServerPlayer p, Reason reason) {
		OmnitrixState s = state(p);
		if (!s.transformed()) {
			return;
		}
		AlienKit kit = Aliens.kit(s.active);
		if (kit != null) {
			try {
				kit.onRevert(p);
			} catch (RuntimeException e) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.error("[Omnitrix] {} onRevert failed", s.active, e);
			}
		}
		OmnitrixBody.remove(p, reason != Reason.DEATH);
		long now = p.level().getGameTime();
		OmnitrixState n = state(p).copy();
		n.active = "";
		n.kitFlags = 0;
		n.heldEntity = 0;
		n.selecting = false;
		n.animId = reason == Reason.TIMEOUT ? ANIM_TIMEOUT : ANIM_REVERT;
		n.animStart = now;
		n.endsAt = 0L;
		n.rechargeUntil = switch (reason) {
			case TIMEOUT -> now + OmnitrixConfig.RECHARGE_TICKS;
			case SHIFT_V -> now + OmnitrixConfig.EARLY_RECHARGE_TICKS;
			default -> 0L;
		};
		save(p, n);
		if (reason == Reason.DEATH || reason == Reason.LOGOUT) {
			return;
		}
		helixFx(p, reason == Reason.TIMEOUT);
		if (reason == Reason.TIMEOUT) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.timeout").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					true);
		}
	}

	// ---------------------------------------------------------------- the watch up (selection)

	public static boolean selecting(Player p) {
		return hasPower(p) && state(p).selecting;
	}

	/** V as a human: the wrist comes up, the core pops, the face shows the selected alien. False (and why) if it cannot. */
	public static boolean activate(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.hasPower || s.transformed() || s.selecting) {
			return false;
		}
		long now = p.level().getGameTime();
		if (now < s.rechargeUntil) {
			OmnitrixState n = s.copy();
			n.animId = ANIM_DENIED;
			n.animStart = now;
			save(p, n);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recharging",
					(s.rechargeUntil - now + 19) / 20).withStyle(ChatFormatting.RED), true);
			p.playNotifySound(OmnitrixSounds.WARNING, SoundSource.PLAYERS, 0.8f, 0.5f);
			return false;
		}
		if (extracting(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.held_by_pod").withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.suppressed").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		OmnitrixState n = s.copy();
		if (!Aliens.playable(n.selected) && !Aliens.playable().isEmpty()) {
			n.selected = Aliens.playable().get(0).id();
		}
		n.selecting = true;
		n.selectStart = now;
		n.timers.remove(SLAP_TIMER);
		save(p, n);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), OmnitrixSounds.CORE_UP, SoundSource.PLAYERS, 0.9f, 1.0f);
		showSelected(p, n.selected);
		return true;
	}

	/** V again: the core sinks back, the face goes back to normal, the arm comes down. No cost. */
	public static boolean deactivate(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.selecting) {
			return false;
		}
		OmnitrixState n = s.copy();
		n.selecting = false;
		n.selectStart = p.level().getGameTime();
		n.timers.remove(SLAP_TIMER);
		save(p, n);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), OmnitrixSounds.CORE_DOWN, SoundSource.PLAYERS, 0.8f, 1.0f);
		return true;
	}

	/** The scroll wheel while the watch is up: turns the dial {@code dir} (+1 / -1) to the next alien that has a kit. */
	public static boolean cycle(ServerPlayer p, int dir) {
		OmnitrixState s = state(p);
		java.util.List<Aliens.Alien> pool = Aliens.playable();
		if (!s.selecting || pool.isEmpty() || dir == 0) {
			return false;
		}
		int i = 0;
		for (int k = 0; k < pool.size(); k++) {
			if (pool.get(k).id().equals(s.selected)) {
				i = k;
			}
		}
		String next = pool.get(Math.floorMod(i + Integer.signum(dir), pool.size())).id();
		OmnitrixState n = s.copy();
		n.selected = next;
		n.animId = ANIM_TURN;
		n.animStart = p.level().getGameTime();
		save(p, n);
		p.playNotifySound(OmnitrixSounds.DIAL, SoundSource.PLAYERS, 0.8f, dir > 0 ? 1.15f : 0.95f);
		showSelected(p, next);
		return true;
	}

	/** Left-click (or right-click) while the watch is up: the core is slapped down; {@link #SLAP_TICKS} later, the alien. */
	public static boolean slap(ServerPlayer p) {
		OmnitrixState s = state(p);
		if (!s.selecting || s.timers.containsKey(SLAP_TIMER)) {
			return false;
		}
		if (!Aliens.playable(s.selected)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(Aliens.byId(s.selected) == null ? "projecthero.omnitrix.alien.unknown" : Aliens.byId(s.selected).nameKey()))
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		long now = p.level().getGameTime();
		OmnitrixState n = s.copy();
		n.animId = ANIM_SLAP;
		n.animStart = now;
		n.timers.put(SLAP_TIMER, now + SLAP_TICKS);
		save(p, n);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), OmnitrixSounds.SLAP, SoundSource.PLAYERS, 1.0f, 1.0f);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		return true;
	}

	private static void showSelected(ServerPlayer p, String id) {
		Aliens.Alien a = Aliens.byId(id);
		if (a != null) {
			p.displayClientMessage(Component.translatable(a.nameKey()).withStyle(st -> st.withColor(0x7CFF6A)), true);
		}
	}

	/** Shift+V (an alias of V while transformed). */
	public static boolean revertEarly(ServerPlayer p) {
		if (!state(p).transformed()) {
			return false;
		}
		revert(p, Reason.SHIFT_V);
		return true;
	}

	/** Picks {@code alienId} on the dial (the dial screen's payload). False for an unknown or "coming soon" alien. */
	public static boolean select(ServerPlayer p, String alienId) {
		OmnitrixState s = state(p);
		if (!s.hasPower || Aliens.byId(alienId) == null) {
			return false;
		}
		if (!Aliens.playable(alienId)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.coming_soon",
					Component.translatable(Aliens.byId(alienId).nameKey())).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!alienId.equals(s.selected)) {
			OmnitrixState n = s.copy();
			n.selected = alienId;
			save(p, n);
		}
		p.playNotifySound(OmnitrixSounds.SELECT, SoundSource.PLAYERS, 0.8f, 1.0f);
		return true;
	}

	/** The pod's "fighting back": the wearer flickers into {@code alienId} for {@code ticks} (shown only: no body change). */
	public static void flicker(ServerPlayer p, String alienId, int ticks) {
		OmnitrixState n = state(p).copy();
		n.flicker = alienId;
		n.flickerUntil = p.level().getGameTime() + ticks;
		save(p, n);
		helixFx(p, true);
	}

	/** The green (or, timing out, red) helix flash: server particles + a sound; the client draws the strands from the anim. */
	private static void helixFx(ServerPlayer p, boolean red) {
		ServerLevel level = p.serverLevel();
		double h = p.getBbHeight();
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + h * 0.5, p.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), red ? OmnitrixSounds.TIMEOUT : OmnitrixSounds.TRANSFORM, SoundSource.PLAYERS, 1.0f, 1.0f);
		if (!red) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), OmnitrixSounds.SLAP, SoundSource.PLAYERS, 0.9f, 1.0f);
		}
	}

	// ---------------------------------------------------------------- tick

	/** Every server tick (from {@code AbilityRouter.serverTick}). */
	public static void tick(ServerPlayer p) {
		OmnitrixState s = p.getAttached(OmnitrixAttachments.STATE);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = p.level().getGameTime();
		if (s.selecting && s.timers.containsKey(SLAP_TIMER) && now >= s.readyAt(SLAP_TIMER)) {
			if (!transform(p)) {
				OmnitrixState n = state(p).copy();
				n.timers.remove(SLAP_TIMER);
				n.selecting = false;
				save(p, n);
			}
			return;
		}
		if (s.transformed()) {
			AlienKit kit = Aliens.kit(s.active);
			if (kit == null) {
				revert(p, Reason.REVOKE);
				return;
			}
			if (!OmnitrixBody.applied(p)) {
				OmnitrixBody.apply(p, kit.stats(), p.getHealth() / Math.max(1f, p.getMaxHealth()));
			}
			if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
				revert(p, Reason.SUPPRESSED);
				return;
			}
			if (now >= s.endsAt) {
				revert(p, Reason.TIMEOUT);
				return;
			}
			long left = s.endsAt - now;
			if (left <= OmnitrixConfig.WARN_TICKS && left % 20 == 0) {
				p.playNotifySound(OmnitrixSounds.WARNING, SoundSource.PLAYERS, 0.6f, 1.0f + 0.6f * (1f - left / (float) OmnitrixConfig.WARN_TICKS));
			}
			kit.tick(p, state(p), now);
		} else if (s.rechargeUntil > 0L && now >= s.rechargeUntil) {
			OmnitrixState n = s.copy();
			n.rechargeUntil = 0L;
			save(p, n);
			p.playNotifySound(OmnitrixSounds.RECHARGED, SoundSource.PLAYERS, 0.8f, 1.0f);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recharged").withStyle(ChatFormatting.GREEN), true);
		}
		OmnitrixState cur = state(p);
		if (cur.flickerUntil != 0L && now >= cur.flickerUntil) {
			OmnitrixState n = cur.copy();
			n.flicker = "";
			n.flickerUntil = 0L;
			save(p, n);
		}
		OmnitrixAbilityManager.tick(p);
	}

	// ---------------------------------------------------------------- lifecycle

	static void onDeath(ServerPlayer p) {
		if (hasPower(p) && state(p).transformed()) {
			revert(p, Reason.DEATH);
		}
		OmnitrixState s = state(p);
		if (s.hasPower && (s.flickerUntil != 0L || s.rechargeUntil != 0L || s.selecting)) {
			OmnitrixState n = s.copy();
			n.selecting = false;
			n.timers.remove(SLAP_TIMER);
			n.flicker = "";
			n.flickerUntil = 0L;
			n.rechargeUntil = 0L;
			save(p, n);
		}
		p.removeAttached(OmnitrixAttachments.EXTRACTION);
		forget(p.getUUID());
	}

	static void onLogout(ServerPlayer p) {
		if (hasPower(p) && state(p).transformed()) {
			revert(p, Reason.LOGOUT);
		}
		if (selecting(p)) {
			OmnitrixState n = state(p).copy();
			n.selecting = false;
			n.timers.remove(SLAP_TIMER);
			save(p, n);
		}
		p.removeAttached(OmnitrixAttachments.EXTRACTION);
		forget(p.getUUID());
	}

	static void onDimensionChange(ServerPlayer p) {
		forget(p.getUUID());
	}

	/** Per-player static state (the key map, the message throttle). */
	public static void forget(UUID id) {
		LAST_REJECT.remove(id);
		OmnitrixAbilityManager.forget(id);
	}

	/** {@code ServerStateReset}. */
	public static void clearSessionState() {
		LAST_REJECT.clear();
		OmnitrixAbilityManager.clearSessionState();
	}
}
