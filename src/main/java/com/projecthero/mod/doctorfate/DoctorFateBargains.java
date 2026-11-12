package com.projecthero.mod.doctorfate;

import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.data.FateState.Bargain;
import com.projecthero.mod.doctorfate.network.FateBargainPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3 Doctor Fate: Nabu's bargains, offered on a small two-button screen that pauses nothing ({@code FateBargainScreen}).
 * Ten seconds to choose; ignoring one counts as refusing. Each refusal makes the next offer meaner (bigger reward, bigger cost) for
 * the rest of that Minecraft day.
 * <ul>
 *   <li><b>Death's door</b> (3 hearts or fewer, helmet on): full heal and a golden shield (Absorption), +35 Dominion.</li>
 *   <li><b>Order's command</b> (sometimes at night): banish 5 creatures of Chaos before dawn (kills count). Success: Dominion -30 and
 *   an Ankh Charm. Failure: Dominion +40.</li>
 *   <li><b>Forbidden spell</b> (Shift+Z asks every time): he always says yes; the Avatar of Order rises and Dominion goes to 90+.</li>
 * </ul>
 */
public final class DoctorFateBargains {
	private DoctorFateBargains() {
	}

	static long day(ServerPlayer p) {
		return p.level().getDayTime() / 24000L;
	}

	/** Today's refusals (0 once the day has turned). */
	public static int refusals(ServerPlayer p) {
		Bargain b = DoctorFate.state(p).bargain;
		return b.refusalDay() == day(p) ? b.refusals() : 0;
	}

	public static int pending(ServerPlayer p) {
		Bargain b = DoctorFate.state(p).bargain;
		return b.pendingUntil() > p.level().getGameTime() ? b.pending() : Bargain.NONE_KIND;
	}

	// ---------------------------------------------------------------- the terms (meaner after refusals)

	public static float deathsDoorCost(int refusals) {
		return DoctorFateConfig.DEATHS_DOOR_DOMINION + DoctorFateConfig.MEANER_COST * refusals;
	}

	/** Death's door's golden shield: Absorption of this amplifier (4 health per level). */
	public static int deathsDoorShield(int refusals) {
		return 1 + refusals;
	}

	public static int questNeed(int refusals) {
		return DoctorFateConfig.QUEST_NEED + DoctorFateConfig.MEANER_QUEST_NEED * refusals;
	}

	public static float questReward(int need) {
		int r = Math.max(0, (need - DoctorFateConfig.QUEST_NEED) / DoctorFateConfig.MEANER_QUEST_NEED);
		return DoctorFateConfig.QUEST_REWARD + DoctorFateConfig.MEANER_REWARD * r;
	}

	public static float questPenalty(int need) {
		int r = Math.max(0, (need - DoctorFateConfig.QUEST_NEED) / DoctorFateConfig.MEANER_QUEST_NEED);
		return DoctorFateConfig.QUEST_PENALTY + DoctorFateConfig.MEANER_COST * r;
	}

	public static float forbiddenFloor(int refusals) {
		return Math.min(DoctorFateConfig.DOMINION_MAX - 1f, DoctorFateConfig.FORBIDDEN_DOMINION + 3f * refusals);
	}

	// ---------------------------------------------------------------- offers

	/** Shift+Z: ask Nabu for the Avatar of Order. False if it's cooling down or another offer is open. */
	static boolean offerForbidden(ServerPlayer p) {
		if (DoctorFate.cooldownRemaining(p, DoctorFateAbilityManager.AVATAR) > 0 || DoctorFate.avatarActive(p)) {
			return false;
		}
		return offer(p, Bargain.FORBIDDEN);
	}

	/** Opens an offer on his screen. False if one is already open or he's possessed. */
	public static boolean offer(ServerPlayer p, int kind) {
		if (!DoctorFate.hasPower(p) || DoctorFate.possessed(p) || pending(p) != Bargain.NONE_KIND) {
			return false;
		}
		long now = p.level().getGameTime();
		int r = refusals(p);
		FateState n = DoctorFate.state(p).copy();
		n.bargain = n.bargain.withPending(kind, now + DoctorFateConfig.BARGAIN_SECONDS * 20L).withLastOffer(now);
		if (kind == Bargain.ORDERS_COMMAND) {
			n.bargain = n.bargain.withNightOffer(day(p));
		}
		DoctorFate.save(p, n);
		float a, b = 0f, c = 0f;
		switch (kind) {
			case Bargain.DEATHS_DOOR -> {
				a = deathsDoorCost(r);
				b = 4f * (deathsDoorShield(r) + 1);
			}
			case Bargain.ORDERS_COMMAND -> {
				int need = questNeed(r);
				a = need;
				b = questReward(need);
				c = questPenalty(need);
			}
			default -> a = forbiddenFloor(r);
		}
		DoctorFate.send(p, new FateBargainPayload(kind, DoctorFateConfig.BARGAIN_SECONDS, a, b, c, r));
		DoctorFate.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.6f);
		return true;
	}

	/** His answer from the screen (or the timeout, as a refusal). */
	public static void answer(ServerPlayer p, int kind, boolean accept) {
		if (pending(p) != kind || kind == Bargain.NONE_KIND) {
			return;
		}
		int r = refusals(p);
		FateState n = DoctorFate.state(p).copy();
		n.bargain = n.bargain.withPending(Bargain.NONE_KIND, 0L);
		DoctorFate.save(p, n);
		if (!accept) {
			refuse(p, r);
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		switch (kind) {
			case Bargain.DEATHS_DOOR -> {
				p.setHealth(p.getMaxHealth());
				p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, DoctorFateConfig.DEATHS_DOOR_SHIELD_TICKS, deathsDoorShield(r), false, true));
				DoctorFate.addDominion(p, deathsDoorCost(r));
				level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.6f), p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 1.0, 0.6, 0.0);
				DoctorFate.sound(p, SoundEvents.TOTEM_USE, 0.35f, 1.4f);
				tell(p, "message.projecthero.doctor_fate.bargain.saved");
			}
			case Bargain.ORDERS_COMMAND -> {
				long dayTime = p.level().getDayTime() % 24000L;
				long toDawn = 24000L - dayTime;
				FateState q = DoctorFate.state(p).copy();
				q.bargain = q.bargain.withQuest(0, questNeed(r), p.level().getGameTime() + toDawn);
				DoctorFate.save(p, q);
				tell(p, "message.projecthero.doctor_fate.bargain.quest_start", questNeed(r));
			}
			case Bargain.FORBIDDEN -> {
				float floor = forbiddenFloor(r);
				if (DoctorFate.dominion(p) < floor) {
					DoctorFate.setDominion(p, floor);
				}
				DoctorFateSpells.raiseAvatar(p);
				tell(p, "message.projecthero.doctor_fate.bargain.avatar");
			}
			default -> {
			}
		}
	}

	private static void refuse(ServerPlayer p, int r) {
		FateState n = DoctorFate.state(p).copy();
		n.bargain = n.bargain.withRefusals(r + 1, day(p));
		DoctorFate.save(p, n);
		tell(p, "message.projecthero.doctor_fate.bargain.refused");
	}

	private static void tell(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GOLD), false);
	}

	// ---------------------------------------------------------------- hooks

	/** After he takes a hit: death's door. */
	public static void onHurt(ServerPlayer p) {
		if (!DoctorFate.wearsHelmet(p) || !p.isAlive() || p.getHealth() > DoctorFateConfig.DEATHS_DOOR_HEALTH) {
			return;
		}
		Bargain b = DoctorFate.state(p).bargain;
		if (p.level().getGameTime() - b.lastOffer() < DoctorFateConfig.OFFER_GAP_TICKS && b.lastOffer() > 0L) {
			return;
		}
		offer(p, Bargain.DEATHS_DOOR);
	}

	/** He killed something. A creature of Chaos counts for Order's command. */
	public static void onKill(ServerPlayer p, net.minecraft.world.entity.LivingEntity dead) {
		if (DoctorFateChaos.isChaos(dead)) {
			onChaosBanished(p);
		}
	}

	static void onChaosBanished(ServerPlayer p) {
		Bargain b = DoctorFate.state(p).bargain;
		if (!b.questActive()) {
			return;
		}
		int kills = b.questKills() + 1;
		FateState n = DoctorFate.state(p).copy();
		if (kills >= b.questNeed()) {
			n.bargain = n.bargain.withQuest(0, 0, 0L);
			DoctorFate.save(p, n);
			DoctorFate.addDominion(p, -questReward(b.questNeed()));
			ItemStack charm = new ItemStack(DoctorFateItems.ANKH_CHARM);
			if (!p.getInventory().add(charm)) {
				p.drop(charm, false, false);
			}
			DoctorFate.sound(p, SoundEvents.PLAYER_LEVELUP, 0.4f, 1.2f);
			tell(p, "message.projecthero.doctor_fate.bargain.quest_done");
		} else {
			n.bargain = n.bargain.withQuest(kills, b.questNeed(), b.questUntil());
			DoctorFate.save(p, n);
			DoctorFate.say(p, "message.projecthero.doctor_fate.bargain.quest_progress", ChatFormatting.GOLD, kills, b.questNeed());
		}
	}

	/** Tests: one bargain tick now. */
	public static void tickForTest(ServerPlayer p) {
		tick(p);
	}

	static void tick(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		Bargain b = s.bargain;
		long now = p.level().getGameTime();
		// an ignored offer counts as a refusal
		if (b.pending() != Bargain.NONE_KIND && now >= b.pendingUntil()) {
			int r = refusals(p);
			FateState n = s.copy();
			n.bargain = n.bargain.withPending(Bargain.NONE_KIND, 0L);
			DoctorFate.save(p, n);
			refuse(p, r);
			return;
		}
		// Order's command runs out at dawn
		if (b.questActive() && now >= b.questUntil()) {
			FateState n = s.copy();
			n.bargain = n.bargain.withQuest(0, 0, 0L);
			DoctorFate.save(p, n);
			DoctorFate.addDominion(p, questPenalty(b.questNeed()));
			tell(p, "message.projecthero.doctor_fate.bargain.quest_failed");
			return;
		}
		// sometimes at night, Order's command (once a night)
		if (s.helmetOn && !b.questActive() && b.pending() == Bargain.NONE_KIND && now % DoctorFateConfig.QUEST_CHECK_TICKS == 0
				&& p.level().isNight() && b.lastNightOffer() != day(p) && !DoctorFate.possessed(p)
				&& p.getRandom().nextInt(DoctorFateConfig.QUEST_ONE_IN) == 0) {
			offer(p, Bargain.ORDERS_COMMAND);
		}
	}
}
