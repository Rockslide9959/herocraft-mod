package com.projecthero.mod.ultron.contagion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.contagion.item.FirewallChip;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.1: Ultron in the suit. A Tony Stark wearing an Iron Man suit inside a Nexus's corruption:
 * <ul>
 *   <li>the HUD glitches (a 1 s red static flash every 30-60 s; the client draws it, {@link #glitchFlag});</li>
 *   <li>JARVIS now and then speaks in Ultron's voice;</li>
 *   <li>once per Nexus visit Ultron tries a <b>Hijack</b>: a red "OVERRIDE ATTEMPT" Hairline fills over 4 s. Leaving the
 *       corruption, pressing any suit ability key (or hitting something) 6 times, a Firewall Chip in the helmet, the
 *       permanent JARVIS Firewall protocol or a fuelled Firewall Beacon nearby breaks it. If it completes the suit locks for
 *       5 s -- no flight, no abilities, red visor -- then JARVIS fights him off. Annoying, never deadly.</li>
 * </ul>
 * Per-player scratch state is static and cleared in {@code ServerStateReset}.
 */
public final class SuitHijack {
	/** The JARVIS "Firewall" protocol: hijack-immune in every suit (first Omega win). Persistent. */
	public static final AttachmentType<Boolean> FIREWALL_PROTOCOL = AttachmentRegistry.create(ProjectHeroMod.id("jarvis_firewall"),
			b -> b.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

	private static final class State {
		UUID visited;
		UUID inside;
		long enteredAt;
		long attemptStart = -1;
		int presses;
		long lockedUntil = -1;
		long nextGlitchAt;
		long glitchUntil;
		long nextVoiceAt;
		boolean jarvisRecovered = true;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private SuitHijack() {
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				State s = STATES.get(sp.getUUID());
				if (s != null && s.attemptStart >= 0) {
					s.presses++;
				}
			}
			return InteractionResult.PASS;
		});
	}

	public static void clearSessionState() {
		STATES.clear();
	}

	/** Is {@code player} hijack-immune right now (chip, protocol)? Beacon protection is checked by position. */
	public static boolean immune(Player player) {
		if (player.getAttachedOrElse(FIREWALL_PROTOCOL, false)) {
			return true;
		}
		return FirewallChip.hasChip(player.getItemBySlot(EquipmentSlot.HEAD));
	}

	public static void tick(ServerLevel level, ContagionState st) {
		long now = level.getGameTime();
		ContagionConfig.Hijack cfg = ContagionConfig.hijack();
		for (ServerPlayer p : level.players()) {
			if (!TonyStark.hasPower(p) || IronManArmor.wornSuitId(p) == null || p.isSpectator()) {
				State gone = STATES.remove(p.getUUID());
				if (gone != null && gone.lockedUntil > now) {
					p.displayClientMessage(Component.empty(), true);
				}
				continue;
			}
			State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
			if (s.lockedUntil > now) {
				if (IronManFlight.isFlying(p)) {
					IronManFlight.setFlying(p, false);
				}
				continue;
			}
			if (s.lockedUntil >= 0 && !s.jarvisRecovered) {
				s.jarvisRecovered = true;
				JarvisDialogue.speak(p, "contagion_hijack_end");
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
			}
			if ((now + p.getId()) % 5 != 0 && s.attemptStart < 0) {
				continue;
			}
			ContagionState.NexusRecord rec = Contagion.corruptionAt(level, p.blockPosition());
			boolean shielded = immune(p) || st.protectedByBeacon(p.blockPosition(), now);
			if (rec == null) {
				if (s.attemptStart >= 0) {
					breakAttempt(p, s, "contagion_hijack_escaped");
				}
				s.inside = null;
				continue;
			}
			if (!rec.id.equals(s.inside)) {
				s.inside = rec.id;
				s.enteredAt = now;
				s.nextGlitchAt = now + 100 + level.random.nextInt(200);
				s.nextVoiceAt = now + 300 + level.random.nextInt(600);
			}
			if (now >= s.nextGlitchAt) {
				s.glitchUntil = now + 20;
				s.nextGlitchAt = now + cfg.glitchMinTicks + level.random.nextInt(Math.max(1, cfg.glitchMaxTicks - cfg.glitchMinTicks));
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.9f);
			}
			if (now >= s.nextVoiceAt) {
				s.nextVoiceAt = now + 1200 + level.random.nextInt(1200);
				JarvisDialogue.speak(p, "contagion_ultron_" + (1 + level.random.nextInt(4)));
			}
			if (s.attemptStart >= 0) {
				if (shielded) {
					breakAttempt(p, s, "contagion_hijack_firewall");
				} else if (s.presses >= cfg.breakPresses) {
					breakAttempt(p, s, "contagion_hijack_broken");
				} else if (now - s.attemptStart >= cfg.attemptTicks) {
					lock(p, s, now);
				}
				continue;
			}
			if (!rec.id.equals(s.visited) && !shielded && now - s.enteredAt >= 100) {
				s.visited = rec.id;
				s.attemptStart = now;
				s.presses = 0;
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.4f);
				p.displayClientMessage(Component.translatable("message.projecthero.contagion.hijack_attempt").withStyle(ChatFormatting.RED,
						ChatFormatting.BOLD), true);
			}
		}
	}

	private static void breakAttempt(ServerPlayer p, State s, String line) {
		s.attemptStart = -1;
		s.presses = 0;
		JarvisDialogue.speak(p, line);
		p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
	}

	private static void lock(ServerPlayer p, State s, long now) {
		s.attemptStart = -1;
		s.presses = 0;
		s.lockedUntil = now + ContagionConfig.hijack().lockTicks;
		s.jarvisRecovered = false;
		if (IronManFlight.isFlying(p)) {
			IronManFlight.setFlying(p, false);
		}
		p.displayClientMessage(Component.translatable("message.projecthero.contagion.hijack_locked").withStyle(ChatFormatting.DARK_RED,
				ChatFormatting.BOLD), true);
		p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.5f);
		JarvisDialogue.speak(p, "contagion_hijack_locked");
	}

	// ---------------------------------------------------------------- hooks (IronManAbilityManager / IronManFlight)

	/**
	 * Called for every suit ability key press: during an attempt a press counts towards breaking it (and does nothing
	 * else); while locked nothing fires. True = swallow the press.
	 */
	public static boolean blocksAbilities(ServerPlayer player, boolean pressed) {
		State s = STATES.get(player.getUUID());
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		if (s.lockedUntil > now) {
			if (pressed) {
				player.displayClientMessage(Component.translatable("message.projecthero.contagion.hijack_locked").withStyle(ChatFormatting.DARK_RED), true);
			}
			return true;
		}
		if (s.attemptStart >= 0) {
			if (pressed) {
				s.presses++;
			}
			return true;
		}
		return false;
	}

	/** No take-off while the suit is locked. */
	public static boolean blocksFlight(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.lockedUntil > player.level().getGameTime();
	}

	// ---------------------------------------------------------------- HUD / tests

	/** 0..1 through a running attempt, or -1. */
	public static float attemptProgress(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s == null || s.attemptStart < 0) {
			return -1f;
		}
		return Math.min(1f, (player.level().getGameTime() - s.attemptStart) / (float) Math.max(1, ContagionConfig.hijack().attemptTicks));
	}

	public static boolean locked(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.lockedUntil > player.level().getGameTime();
	}

	/** True while this player's HUD should show the red static flash. */
	public static boolean glitchFlag(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.glitchUntil > player.level().getGameTime();
	}

	/** Test hook: start an attempt now. */
	public static void startAttemptForTest(ServerPlayer player) {
		State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		s.attemptStart = player.level().getGameTime();
		s.presses = 0;
	}

	/** Test hook: is an attempt running? */
	public static boolean attempting(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.attemptStart >= 0;
	}
}
