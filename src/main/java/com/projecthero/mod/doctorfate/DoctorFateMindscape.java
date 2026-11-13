package com.projecthero.mod.doctorfate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.network.FateFxPayload;
import com.projecthero.mod.doctorfate.network.FateSpellFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate, the <b>Tower of Fate mindscape</b> (tester: "maybe add a mindscape where you can speak directly to Nabu").
 * <b>Hold Shift+V for 1 s</b> (a tap is still the Amulet of Anubis): Kent closes his hand on the amulet and his mind goes into the
 * Tower, where Nabu's helmet speaks and he answers through dialogue choices ({@code client.doctorfate.FateMindscapeScreen}). The
 * screen pauses nothing: the body stands where it was. Every choice is checked here; the answer goes back as a
 * {@link FateFxPayload#MIND_REPLY}. The choices keep the power's tension -- control against power:
 * <ul>
 *   <li><b>How strong is your hold?</b> -- Nabu tells you your Dominion and what comes next. Free.</li>
 *   <li><b>Lend me your strength</b> (Blessing of Order) -- helmet on: spells deal +30% for 30 s, for +15 Dominion. 3 min.</li>
 *   <li><b>Leave my mind be</b> (Silence) -- 60 s with no Override, no Pull, no lagging keys, no whispers, but every spell's
 *   Dominion counts x1.5 ("he keeps count"). He refuses from Taking hold (75) up. 5 min.</li>
 *   <li><b>Take my future, not my present</b> (Debt) -- Dominion -25 now, but 10 minutes later he collects 35. One debt at a
 *   time; nothing to take below 10 Dominion.</li>
 *   <li><b>Settle my debt now</b> -- pay 30 now instead of 35 later.</li>
 *   <li><b>Lore</b>: the Lords of Order, the Helmet of Fate, Kent Nelson. Free.</li>
 * </ul>
 * Static state: who has the Tower open (UUIDs only), dropped by {@code ServerStateReset} through {@link DoctorFate#clearSessionState}.
 */
public final class DoctorFateMindscape {
	/** The dialogue choices (ids are synced: append only). */
	public enum Choice {
		STATUS, BLESSING, SILENCE, DEBT, REPAY, LORE_ORDER, LORE_HELMET, LORE_KENT, LEAVE;

		public static Choice byId(int id) {
			Choice[] v = values();
			return id >= 0 && id < v.length ? v[id] : LEAVE;
		}
	}

	/** Nabu's answers (ids are synced: append only). */
	public enum Reply {
		GREETING, STATUS, BLESSED, BLESS_NEEDS_HELMET, SILENCED, SILENCE_REFUSED, DEBT_MADE, DEBT_OWED, DEBT_NOTHING, REPAID, NO_DEBT,
		LORE_ORDER, LORE_HELMET, LORE_KENT, COOLING, FAREWELL, DEBT_COLLECTED
	}

	public static final String BLESSING_CD = "mind_blessing";
	public static final String SILENCE_CD = "mind_silence";

	private static final Map<UUID, Long> OPEN = new ConcurrentHashMap<>();

	private DoctorFateMindscape() {
	}

	static void clearSessionState() {
		OPEN.clear();
	}

	static void forget(UUID id) {
		OPEN.remove(id);
	}

	public static boolean isOpen(ServerPlayer p) {
		return OPEN.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- client-safe queries

	public static boolean blessed(net.minecraft.world.entity.player.Player p) {
		FateState s = DoctorFate.peek(p);
		return s != null && s.hasPower && s.mind.blessingUntil() > DoctorFate.now(p);
	}

	public static boolean silenced(net.minecraft.world.entity.player.Player p) {
		FateState s = DoctorFate.peek(p);
		return s != null && s.hasPower && s.mind.silenceUntil() > DoctorFate.now(p);
	}

	public static float debt(net.minecraft.world.entity.player.Player p) {
		FateState s = DoctorFate.peek(p);
		return s == null ? 0f : s.mind.debt();
	}

	public static int debtTicksLeft(net.minecraft.world.entity.player.Player p) {
		FateState s = DoctorFate.peek(p);
		return s == null || s.mind.debt() <= 0f ? 0 : (int) Math.max(0L, s.mind.debtDueAt() - DoctorFate.now(p));
	}

	// ---------------------------------------------------------------- entering and leaving

	/** Opens the Tower for {@code p}. False when he can't go (no power, possessed, dead). */
	public static boolean open(ServerPlayer p) {
		if (!DoctorFate.hasPower(p) || DoctorFate.possessed(p) || !p.isAlive() || p.isSpectator()) {
			return false;
		}
		OPEN.put(p.getUUID(), DoctorFate.now(p));
		FateState n = DoctorFate.state(p).copy();
		n.mind = n.mind.withVisits(n.mind.visits() + 1);
		DoctorFate.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		DoctorFate.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
		DoctorFate.sound(p, SoundEvents.BEACON_AMBIENT, 0.6f, 0.7f);
		DoctorFateFx.send(level, FateSpellFxPayload.ANKH, p.position().add(0, 1.2, 0), p.getLookAngle(), 0.9f, 14, -1, DoctorFate.BLUE);
		reply(p, Reply.GREETING, 0, 0);
		return true;
	}

	/** The screen closed (Esc or Leave). */
	public static void close(ServerPlayer p) {
		OPEN.remove(p.getUUID());
	}

	// ---------------------------------------------------------------- the choices

	/** A choice from the open Tower. Returns Nabu's answer (also sent to the client); null when the Tower isn't open. */
	public static Reply choose(ServerPlayer p, Choice c) {
		if (!isOpen(p) || !DoctorFate.hasPower(p)) {
			return null;
		}
		if (DoctorFate.possessed(p)) {
			close(p);
			return null;
		}
		long now = DoctorFate.now(p);
		FateState s = DoctorFate.state(p);
		Reply r;
		int a = 0, b = 0;
		switch (c) {
			case STATUS -> {
				r = Reply.STATUS;
				a = Math.round(s.dominion);
				b = DoctorFate.tier(s.dominion);
			}
			case BLESSING -> {
				int cd = DoctorFate.cooldownRemaining(p, BLESSING_CD);
				if (cd > 0) {
					r = Reply.COOLING;
					a = (cd + 19) / 20;
				} else if (!s.helmetOn) {
					r = Reply.BLESS_NEEDS_HELMET;
				} else {
					FateState n = s.copy();
					n.mind = n.mind.withBlessing(now + DoctorFateConfig.MIND_BLESSING_TICKS);
					n.abilityReadyAt.put(BLESSING_CD, now + DoctorFateConfig.MIND_BLESSING_COOLDOWN);
					DoctorFate.save(p, n);
					DoctorFate.addDominion(p, DoctorFateConfig.MIND_BLESSING_DOMINION);
					r = Reply.BLESSED;
					a = Math.round(DoctorFateConfig.MIND_BLESSING_DOMINION);
					DoctorFate.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.2f);
				}
			}
			case SILENCE -> {
				int cd = DoctorFate.cooldownRemaining(p, SILENCE_CD);
				if (cd > 0) {
					r = Reply.COOLING;
					a = (cd + 19) / 20;
				} else if (s.dominion >= DoctorFateConfig.TIER_HOLD) {
					r = Reply.SILENCE_REFUSED;
				} else {
					FateState n = s.copy();
					n.mind = n.mind.withSilence(now + DoctorFateConfig.MIND_SILENCE_TICKS);
					n.abilityReadyAt.put(SILENCE_CD, now + DoctorFateConfig.MIND_SILENCE_COOLDOWN);
					DoctorFate.save(p, n);
					r = Reply.SILENCED;
					DoctorFate.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.6f);
				}
			}
			case DEBT -> {
				if (s.mind.debt() > 0f) {
					r = Reply.DEBT_OWED;
					a = Math.round(s.mind.debt());
					b = (debtTicksLeft(p) + 19) / 20;
				} else if (s.dominion < DoctorFateConfig.MIND_DEBT_MIN) {
					r = Reply.DEBT_NOTHING;
				} else {
					float cut = Math.min(DoctorFateConfig.MIND_DEBT_CUT, s.dominion);
					FateState n = s.copy();
					n.dominion = Math.max(0f, s.dominion - cut);
					n.mind = n.mind.withDebt(DoctorFateConfig.MIND_DEBT_OWED, now + DoctorFateConfig.MIND_DEBT_TICKS);
					DoctorFate.save(p, n);
					r = Reply.DEBT_MADE;
					a = Math.round(cut);
					b = Math.round(DoctorFateConfig.MIND_DEBT_OWED);
					DoctorFate.sound(p, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.7f);
				}
			}
			case REPAY -> {
				if (s.mind.debt() <= 0f) {
					r = Reply.NO_DEBT;
				} else {
					float pay = Math.max(0f, s.mind.debt() - DoctorFateConfig.MIND_REPAY_DISCOUNT);
					FateState n = s.copy();
					n.mind = n.mind.withDebt(0f, 0L);
					DoctorFate.save(p, n);
					DoctorFate.addDominion(p, pay);
					r = Reply.REPAID;
					a = Math.round(pay);
				}
			}
			case LORE_ORDER -> r = Reply.LORE_ORDER;
			case LORE_HELMET -> r = Reply.LORE_HELMET;
			case LORE_KENT -> r = Reply.LORE_KENT;
			default -> {
				r = Reply.FAREWELL;
				close(p);
			}
		}
		reply(p, r, a, b);
		return r;
	}

	private static void reply(ServerPlayer p, Reply r, int a, int b) {
		int tier = DoctorFate.tier(DoctorFate.dominion(p));
		DoctorFate.send(p, new FateFxPayload(FateFxPayload.MIND_REPLY, p.getId(), 0, new int[] { r.ordinal(), a, b, tier }));
	}

	// ---------------------------------------------------------------- the debt coming due (from DoctorFate.tick)

	static void tick(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		long now = DoctorFate.now(p);
		if (s.mind.debt() > 0f && now >= s.mind.debtDueAt()) {
			float owed = s.mind.debt();
			FateState n = s.copy();
			n.mind = n.mind.withDebt(0f, 0L);
			DoctorFate.save(p, n);
			DoctorFate.addDominion(p, owed);
			p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.debt_collected", Math.round(owed))
					.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
			ServerLevel level = (ServerLevel) p.level();
			Vec3 c = p.position().add(0, 1.0, 0);
			DoctorFateFx.send(level, FateSpellFxPayload.ANKH, c, p.getLookAngle().scale(-1), 1.4f, 20);
			DoctorFate.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.25f, 1.4f);
			if (isOpen(p)) {
				reply(p, Reply.DEBT_COLLECTED, Math.round(owed), 0);
			}
		}
	}
}
