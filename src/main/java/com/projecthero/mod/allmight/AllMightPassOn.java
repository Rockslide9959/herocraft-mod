package com.projecthero.mod.allmight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.hero.UniquePowersData;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6 (user: "allow the ability to be passed on, also players should be able to temporarily lend another player their power but
 * give this a drawback"): handing One For All to another player.
 *
 * <h2>The offer</h2>
 * Sneak + right-click another player with an empty hand (Base or Power Form) opens a small client screen ({@code AllMightOfferScreen})
 * with <b>Pass On</b> and <b>Lend (5 min)</b>; the choice comes back through {@code AllMightActionPayload} to {@link #offer}. The other
 * player gets a clickable chat offer -- [Accept] / [Refuse] ({@code /oneforall accept|refuse}) -- and {@code OFFER_TICKS} to answer.
 * Nothing changes until they accept, and everything is re-checked when they do.
 *
 * <h2>Pass On (permanent)</h2>
 * The receiver is checked against {@link UniquePowers} (with the giver's own slot not counting) <em>before</em> anything changes; then
 * the giver is revoked (a normal player again, costume returned) and the receiver granted through {@link AllMight#grant}, i.e. through
 * {@link HeroTiers#claimPrimary} like every grant -- so it replaces whatever Primary power they had. "Eat this": a strand of hair, and a
 * gold glow travelling from one to the other.
 *
 * <h2>Lend (5 minutes)</h2>
 * The lender keeps their state (cooldowns, locker) but {@code hasPower} goes false and {@code loanRole = LOAN_LENDER}: they have no One
 * For All for the loan and are exhausted (Weakness I + Slowness I, refreshed for the whole loan). The borrower gets a fresh All Might
 * with {@code loanRole = LOAN_BORROWER}: it runs on the lender's timer as it stands, and cannot be passed on or re-lent.
 * <b>Existing-Primary rule:</b> a loan is refused to anyone who already holds a power (any Primary hero, any mutation, the Symbiote) --
 * powers are granted and revoked by each power's own routines and there is no generic way to save one and hand it back, so refusing is
 * the only rule that can never lose or duplicate anything. The loan ends on time, the borrower's death, either player logging out, the
 * lender sneak-right-clicking the borrower again, or the borrower losing the power any other way
 * ({@link AllMight#revoke} calls {@link #returnToLender}). The lender then gets it back (through {@code claimPrimary} again -- anything
 * they picked up during the loan is replaced) and must wait {@code LEND_COOLDOWN_TICKS} before lending again.
 *
 * <h2>Persistence / no duplication</h2>
 * Both sides are saved in their {@code AllMightState} (role, partner, end time), and at most one of the two ever has {@code hasPower}.
 * A loan never survives a logout -- it ends cleanly at the disconnect -- and if the server stopped mid-loan, {@link #onJoin} resolves
 * each side on its own: a borrower whose lender is not lending to them loses it, a lender whose borrower is not borrowing from them
 * gets it back. The only static state (pending offers, the glow beams) is cleared by {@code ServerStateReset} via
 * {@link AllMight#clearSessionState}.
 */
public final class AllMightPassOn {
	public enum Kind {
		PASS_ON, LEND
	}

	public enum EndReason {
		TIME, DEATH, LOGOUT, RECALL, LOST
	}

	/** A pending chat offer, keyed by the player it was made to. Expires on the server tick count. */
	private record Offer(UUID giver, String giverName, Kind kind, int expiresAt) {
	}

	/** The gold glow travelling from one player to the other (server ticks). */
	private record Glow(UUID from, UUID to, int start) {
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	private static final List<Glow> GLOWS = new ArrayList<>();
	/** Set while a loan is being ended, for the lender's message and (on a lender's own logout) the lender object itself. */
	private static EndReason ending = EndReason.LOST;
	@Nullable
	private static ServerPlayer knownLender;

	private AllMightPassOn() {
	}

	public static void clearSessionState() {
		OFFERS.clear();
		GLOWS.clear();
		ending = EndReason.LOST;
		knownLender = null;
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
					|| !(entity instanceof ServerPlayer target) || !sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			AllMightState s = sp.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, null);
			if (s == null) {
				return InteractionResult.PASS;
			}
			if (s.loanRole == AllMightState.LOAN_LENDER && s.loanPartner.equals(target.getUUID().toString())) {
				recall(sp, target);
				return InteractionResult.SUCCESS;
			}
			if (s.hasPower) {
				if (s.loanRole == AllMightState.LOAN_BORROWER) {
					sp.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.cannot_pass").withStyle(ChatFormatting.RED), true);
				}
				return InteractionResult.SUCCESS; // the screen opens client-side; nothing else happens on the server
			}
			return InteractionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("oneforall")
						.then(Commands.literal("accept").executes(c -> accept(c.getSource().getPlayerOrException()) ? 1 : 0))
						.then(Commands.literal("refuse").executes(c -> refuse(c.getSource().getPlayerOrException()) ? 1 : 0))));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				onDisconnect(handler.getPlayer());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer sp && AllMight.isBorrower(sp)) {
				endLoanFromBorrower(sp, EndReason.DEATH);
			}
		});
		ServerPlayerEvents.JOIN.register(AllMightPassOn::onJoin);
		ServerTickEvents.END_SERVER_TICK.register(AllMightPassOn::tickGlows);
	}

	// ---------------------------------------------------------------- offers

	/** The payload from the offer screen: {@code giver} offers {@code kind} to the player with entity id {@code targetId}. */
	public static void offerFromScreen(ServerPlayer giver, int targetId, Kind kind) {
		if (giver.level().getEntity(targetId) instanceof ServerPlayer target) {
			offer(giver, target, kind);
		}
	}

	/** Makes the offer (true if it was sent). Every refusal is explained to the giver. */
	public static boolean offer(ServerPlayer giver, ServerPlayer target, Kind kind) {
		String problem = check(giver, target, kind);
		if (problem != null) {
			giver.displayClientMessage(Component.translatable(problem, target.getDisplayName()).withStyle(ChatFormatting.RED), false);
			return false;
		}
		int expires = giver.getServer().getTickCount() + AllMightConfig.OFFER_TICKS;
		OFFERS.put(target.getUUID(), new Offer(giver.getUUID(), giver.getGameProfile().getName(), kind, expires));
		String what = kind == Kind.PASS_ON ? "pass_on" : "lend";
		target.displayClientMessage(Component.translatable("message.projecthero.all_might.offer." + what, giver.getDisplayName())
				.withStyle(ChatFormatting.GOLD), false);
		target.displayClientMessage(Component.translatable("message.projecthero.all_might.offer." + what + ".detail")
				.withStyle(ChatFormatting.YELLOW), false);
		MutableComponent accept = Component.translatable("message.projecthero.all_might.offer.accept").withStyle(s -> s
				.withColor(ChatFormatting.GREEN).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/oneforall accept"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.projecthero.all_might.offer.accept_hover"))));
		MutableComponent refuse = Component.translatable("message.projecthero.all_might.offer.refuse").withStyle(s -> s
				.withColor(ChatFormatting.RED).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/oneforall refuse"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.projecthero.all_might.offer.refuse_hover"))));
		target.displayClientMessage(Component.literal("   ").append(accept).append("   ").append(refuse)
				.append(Component.translatable("message.projecthero.all_might.offer.time", AllMightConfig.OFFER_TICKS / 20).withStyle(ChatFormatting.GRAY)), false);
		giver.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.sent", target.getDisplayName())
				.withStyle(ChatFormatting.GOLD), true);
		target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
		// the giver holds out a hand
		AllMightState gs = AllMight.state(giver);
		if (gs.hasPower && giver.level().getGameTime() >= gs.busyUntil) {
			AllMightState n = gs.copy();
			n.animId = AllMightState.ANIM_PASS_ON;
			n.animStart = giver.level().getGameTime();
			AllMight.save(giver, n);
		}
		return true;
	}

	/** Why {@code giver} cannot hand {@code kind} to {@code target} right now (a lang key taking the target's name), or null. */
	@Nullable
	static String check(ServerPlayer giver, ServerPlayer target, Kind kind) {
		AllMightState g = AllMight.state(giver);
		long now = giver.level().getGameTime();
		if (!g.hasPower) {
			return "message.projecthero.all_might.offer.no_power";
		}
		if (g.loanRole == AllMightState.LOAN_BORROWER) {
			return "message.projecthero.all_might.loan.cannot_pass";
		}
		if (target == giver || !target.isAlive() || target.isSpectator() || target.level() != giver.level()
				|| target.distanceTo(giver) > AllMightConfig.PASS_RANGE + 1.0) {
			return "message.projecthero.all_might.offer.too_far";
		}
		AllMightState t = AllMight.state(target);
		if (t.hasPower || t.loanRole != AllMightState.LOAN_NONE) {
			return "message.projecthero.all_might.offer.already";
		}
		if (kind == Kind.LEND) {
			if (g.lendReadyAt > now) {
				return "message.projecthero.all_might.offer.lend_cooldown";
			}
			// the existing-Primary rule: a loan needs an empty vessel (see the class comment)
			if (HeroTiers.hasHeroTier(target) || HeroTiers.hasExperimental(target)
					|| com.projecthero.mod.symbiote.Symbiote.hasSymbiote(target)) {
				return "message.projecthero.all_might.offer.has_power";
			}
		}
		if (uniqueBlocked(target, giver)) {
			return "message.projecthero.all_might.offer.unique";
		}
		return null;
	}

	/**
	 * {@link UniquePowers#blockingHolder} with the giver's own slot not counting (they are the one handing it over): true if somebody
	 * else entirely holds One For All while the rule is on.
	 */
	private static boolean uniqueBlocked(ServerPlayer target, ServerPlayer giver) {
		MinecraftServer server = target.getServer();
		if (server == null || !UniquePowers.enabled(server)) {
			return false;
		}
		for (UUID id : List.copyOf(UniquePowersData.get(server).holders(AllMight.KEY).keySet())) {
			if (id.equals(giver.getUUID()) || id.equals(target.getUUID())) {
				continue;
			}
			ServerPlayer online = findPlayer(server, id);
			if (online == null || UniquePowers.holds(online, AllMight.KEY)) {
				return true;
			}
		}
		return false;
	}

	/** [Accept] (or {@code /oneforall accept}). True if One For All changed hands. */
	public static boolean accept(ServerPlayer target) {
		Offer o = OFFERS.remove(target.getUUID());
		MinecraftServer server = target.getServer();
		if (o == null || server == null || server.getTickCount() > o.expiresAt()) {
			target.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.none").withStyle(ChatFormatting.GRAY), false);
			return false;
		}
		ServerPlayer giver = findPlayer(server, o.giver());
		if (giver == null) {
			target.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.gone", o.giverName()).withStyle(ChatFormatting.RED), false);
			return false;
		}
		String problem = check(giver, target, o.kind());
		if (problem != null) {
			target.displayClientMessage(Component.translatable(problem, target.getDisplayName()).withStyle(ChatFormatting.RED), false);
			giver.displayClientMessage(Component.translatable(problem, target.getDisplayName()).withStyle(ChatFormatting.RED), false);
			return false;
		}
		return o.kind() == Kind.PASS_ON ? passOn(giver, target) : lend(giver, target);
	}

	/** [Refuse]. Nothing changes. */
	public static boolean refuse(ServerPlayer target) {
		Offer o = OFFERS.remove(target.getUUID());
		if (o == null) {
			target.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.none").withStyle(ChatFormatting.GRAY), false);
			return false;
		}
		target.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.refused_you").withStyle(ChatFormatting.GRAY), false);
		ServerPlayer giver = target.getServer() == null ? null : findPlayer(target.getServer(), o.giver());
		if (giver != null) {
			giver.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.refused", target.getDisplayName())
					.withStyle(ChatFormatting.GRAY), false);
		}
		return true;
	}

	/** Test / tooling: whether {@code target} has an offer waiting. */
	public static boolean hasOffer(ServerPlayer target) {
		return OFFERS.containsKey(target.getUUID());
	}

	// ---------------------------------------------------------------- pass on

	private static boolean passOn(ServerPlayer giver, ServerPlayer target) {
		AllMight.revoke(giver); // the giver becomes a normal player for good (costume returned to them)
		if (!AllMight.grant(target)) {
			// should be impossible after check(); never leave One For All with nobody
			UniquePowers.forced(() -> AllMight.grant(giver));
			giver.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.failed").withStyle(ChatFormatting.RED), false);
			return false;
		}
		Component said = Component.translatable("message.projecthero.all_might.pass_on.eat_this", giver.getDisplayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
		Component strand = Component.translatable("message.projecthero.all_might.pass_on.strand", giver.getDisplayName(), target.getDisplayName())
				.withStyle(ChatFormatting.YELLOW);
		for (ServerPlayer p : List.of(giver, target)) {
			p.displayClientMessage(said, false);
			p.displayClientMessage(strand, false);
		}
		giver.displayClientMessage(Component.translatable("message.projecthero.all_might.pass_on.given", target.getDisplayName())
				.withStyle(ChatFormatting.GRAY), false);
		glow(giver, target);
		ServerLevel level = (ServerLevel) target.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- lend

	private static boolean lend(ServerPlayer lender, ServerPlayer borrower) {
		long now = lender.level().getGameTime();
		AllMightAbilities.cancelCharge(lender, false);
		AllMightAbilities.clear(lender.getUUID());
		if (AllMight.isFullPower(lender)) {
			AllMight.changeForm(lender, false, false); // the costume goes back into the locker
		}
		AllMightState g = AllMight.state(lender).copy();
		g.hasPower = false;
		g.fullPower = false;
		g.plusUltra = false;
		g.plusUltraUntil = 0L;
		g.animId = AllMightState.ANIM_NONE;
		g.busyUntil = 0L;
		g.transformUntil = 0L;
		g.loanRole = AllMightState.LOAN_LENDER;
		g.loanPartner = borrower.getUUID().toString();
		g.loanPartnerName = borrower.getGameProfile().getName();
		g.loanUntil = now + AllMightConfig.LOAN_TICKS;
		AllMight.save(lender, g);
		AllMight.reconcile(lender);
		applyLenderEffects(lender);

		HeroTiers.claimPrimary(borrower, AllMight.KEY); // they hold nothing (check() refused otherwise): this only records the claim
		AllMightState b = AllMight.fresh(borrower);
		b.loanRole = AllMightState.LOAN_BORROWER;
		b.loanPartner = lender.getUUID().toString();
		b.loanPartnerName = lender.getGameProfile().getName();
		b.loanUntil = g.loanUntil;
		// v0.16.7: the One For All timer applies to a borrower too, and the loan carries what the lender had left (you cannot lend
		// more than you have). The lender's own timer is frozen while they have no power and is theirs again when it comes back.
		AllMight.setTimeLeft(b, AllMight.timeLeft(g));
		AllMight.save(borrower, b);
		AllMight.reconcile(borrower);
		AllMight.acquiredFx(borrower);
		borrower.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.borrowed", lender.getDisplayName(),
				AllMightConfig.LOAN_TICKS / 1200).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		borrower.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.borrowed_hint").withStyle(ChatFormatting.YELLOW), false);
		lender.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.lent", borrower.getDisplayName(),
				AllMightConfig.LOAN_TICKS / 1200).withStyle(ChatFormatting.GOLD), false);
		lender.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.lent_hint").withStyle(ChatFormatting.GRAY), false);
		glow(lender, borrower);
		return true;
	}

	/** The lender sneak-right-clicks the borrower again: the loan ends now. */
	private static void recall(ServerPlayer lender, ServerPlayer borrower) {
		if (AllMight.isBorrower(borrower)) {
			endLoanFromBorrower(borrower, EndReason.RECALL, lender);
		} else {
			restoreLender(lender, EndReason.LOST);
		}
	}

	public static void endLoanFromBorrower(ServerPlayer borrower, EndReason reason) {
		endLoanFromBorrower(borrower, reason, null);
	}

	/** Ends a loan from the borrower's side: they lose the borrowed power and (via {@link #returnToLender}) the lender gets it back. */
	public static void endLoanFromBorrower(ServerPlayer borrower, EndReason reason, @Nullable ServerPlayer lender) {
		AllMightState s = AllMight.state(borrower);
		if (!s.hasPower || s.loanRole != AllMightState.LOAN_BORROWER) {
			return;
		}
		EndReason prevReason = ending;
		ServerPlayer prevLender = knownLender;
		ending = reason;
		knownLender = lender;
		try {
			borrower.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.ended_borrower." + reason.name().toLowerCase(java.util.Locale.ROOT),
					s.loanPartnerName).withStyle(ChatFormatting.GOLD), false);
			AllMight.revoke(borrower, reason != EndReason.DEATH); // a dead borrower's locker stays put (their inventory is already gone)
		} finally {
			ending = prevReason;
			knownLender = prevLender;
		}
	}

	/** Called by {@link AllMight#revoke} when a borrower loses the power, however it happened: it goes home. */
	static void returnToLender(ServerPlayer borrower, AllMightState old) {
		MinecraftServer server = borrower.getServer();
		ServerPlayer lender = knownLender;
		if (lender == null && server != null && !old.loanPartner.isEmpty()) {
			try {
				lender = findPlayer(server, UUID.fromString(old.loanPartner));
			} catch (IllegalArgumentException e) {
				lender = null;
			}
		}
		if (lender == null) {
			return; // offline: they get it back the moment they join (onJoin)
		}
		AllMightState l = AllMight.state(lender);
		if (l.loanRole != AllMightState.LOAN_LENDER || !l.loanPartner.equals(borrower.getUUID().toString())) {
			return; // not lending to this borrower any more (revoked by an admin, say): nothing to give back
		}
		restoreLender(lender, ending);
		if (lender.level() == borrower.level()) {
			glow(borrower, lender);
		}
	}

	/** The lender gets their One For All back: Base Form, H ready, the lend cooldown starts. */
	static void restoreLender(ServerPlayer lender, EndReason reason) {
		AllMightState s = AllMight.state(lender);
		if (s.loanRole != AllMightState.LOAN_LENDER) {
			return;
		}
		long now = lender.level().getGameTime();
		HeroTiers.claimPrimary(lender, AllMight.KEY); // anything picked up during the loan makes way, like any grant
		AllMightState n = AllMight.state(lender).copy();
		n.hasPower = true;
		n.fullPower = false;
		n.loanRole = AllMightState.LOAN_NONE;
		n.loanPartner = "";
		n.loanPartnerName = "";
		n.loanUntil = 0L;
		n.lendReadyAt = now + AllMightConfig.LEND_COOLDOWN_TICKS;
		n.formChangedAt = now - AllMightConfig.FORM_TOGGLE_DEBOUNCE_TICKS;
		n.busyUntil = 0L;
		n.transformUntil = 0L;
		n.animId = AllMightState.ANIM_NONE;
		AllMight.save(lender, n);
		AllMight.reconcile(lender);
		clearLenderEffects(lender);
		AllMight.acquiredFx(lender);
		lender.displayClientMessage(Component.translatable("message.projecthero.all_might.loan.returned." + reason.name().toLowerCase(java.util.Locale.ROOT),
				s.loanPartnerName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
	}

	// ---------------------------------------------------------------- the lender's drawback

	private static void applyLenderEffects(ServerPlayer lender) {
		keepWeak(lender, MobEffects.WEAKNESS, AllMightConfig.LENDER_WEAKNESS_AMPLIFIER);
		keepWeak(lender, MobEffects.MOVEMENT_SLOWDOWN, AllMightConfig.LENDER_SLOWNESS_AMPLIFIER);
	}

	/** Visible, short, topped up while it runs low -- it lapses by itself once the loan is over even if the clean-up is missed. */
	private static void keepWeak(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
		MobEffectInstance cur = p.getEffect(effect);
		if (cur != null && (cur.isInfiniteDuration() || cur.getAmplifier() > amp)) {
			return;
		}
		if (cur == null || cur.getDuration() <= 40) {
			p.addEffect(new MobEffectInstance(effect, 100, amp, false, true, true));
		}
	}

	public static void clearLenderEffects(ServerPlayer p) {
		clearShort(p, MobEffects.WEAKNESS, AllMightConfig.LENDER_WEAKNESS_AMPLIFIER);
		clearShort(p, MobEffects.MOVEMENT_SLOWDOWN, AllMightConfig.LENDER_SLOWNESS_AMPLIFIER);
	}

	private static void clearShort(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
		MobEffectInstance cur = p.getEffect(effect);
		if (cur != null && !cur.isInfiniteDuration() && cur.getAmplifier() == amp && cur.getDuration() <= 100) {
			p.removeEffect(effect);
		}
	}

	// ---------------------------------------------------------------- tick / lifecycle

	/** Every player, every tick (from {@code AllMightAbilityManager.serverTick}): offer expiry and both sides of a loan. */
	public static void tick(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		if (!OFFERS.isEmpty()) {
			Offer o = OFFERS.get(player.getUUID());
			if (o != null && server.getTickCount() > o.expiresAt()) {
				OFFERS.remove(player.getUUID());
				player.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.expired").withStyle(ChatFormatting.GRAY), false);
				ServerPlayer giver = findPlayer(server, o.giver());
				if (giver != null) {
					giver.displayClientMessage(Component.translatable("message.projecthero.all_might.offer.expired_giver", player.getDisplayName())
							.withStyle(ChatFormatting.GRAY), false);
				}
			}
		}
		AllMightState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || s.loanRole == AllMightState.LOAN_NONE || player.level().getGameTime() % 10L != 0L) {
			return;
		}
		long now = player.level().getGameTime();
		ServerPlayer partner = partner(server, s);
		if (s.loanRole == AllMightState.LOAN_LENDER && !s.hasPower) {
			applyLenderEffects(player);
			boolean partnerBorrowing = partner != null && AllMight.isBorrower(partner)
					&& AllMight.state(partner).loanPartner.equals(player.getUUID().toString());
			if (!partnerBorrowing) {
				restoreLender(player, EndReason.LOST);
			} else if (now >= s.loanUntil) {
				endLoanFromBorrower(partner, EndReason.TIME, player);
			}
		} else if (s.loanRole == AllMightState.LOAN_BORROWER && s.hasPower) {
			boolean partnerLending = partner != null && AllMight.isLender(partner)
					&& AllMight.state(partner).loanPartner.equals(player.getUUID().toString());
			if (!partnerLending) {
				endLoanFromBorrower(player, EndReason.LOST);
			} else if (now >= s.loanUntil) {
				endLoanFromBorrower(player, EndReason.TIME, partner);
			}
		}
	}

	/** The online player with this id -- from the player list, or (GameTest mock players are not in it) from any level. */
	@Nullable
	static ServerPlayer findPlayer(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null) {
			return p;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getPlayerByUUID(id) instanceof ServerPlayer sp && !sp.isRemoved()) {
				return sp;
			}
		}
		return null;
	}

	@Nullable
	private static ServerPlayer partner(MinecraftServer server, AllMightState s) {
		if (s.loanPartner.isEmpty()) {
			return null;
		}
		try {
			return findPlayer(server, UUID.fromString(s.loanPartner));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A loan never survives a logout: it ends now, from whichever side left. Their pending offers go too. */
	public static void onDisconnect(ServerPlayer player) {
		OFFERS.remove(player.getUUID());
		OFFERS.values().removeIf(o -> o.giver().equals(player.getUUID()));
		AllMightState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || player.getServer() == null) {
			return;
		}
		if (s.loanRole == AllMightState.LOAN_BORROWER) {
			endLoanFromBorrower(player, EndReason.LOGOUT);
		} else if (s.loanRole == AllMightState.LOAN_LENDER) {
			ServerPlayer borrower = partner(player.getServer(), s);
			if (borrower != null && AllMight.isBorrower(borrower)) {
				endLoanFromBorrower(borrower, EndReason.LOGOUT, player);
			} else {
				restoreLender(player, EndReason.LOGOUT);
			}
		}
	}

	/** A loan saved by a server that stopped mid-loan is resolved on join, each side on its own (never both holding it). */
	public static void onJoin(ServerPlayer player) {
		AllMightState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || s.loanRole == AllMightState.LOAN_NONE || player.getServer() == null) {
			return;
		}
		ServerPlayer partner = partner(player.getServer(), s);
		if (s.loanRole == AllMightState.LOAN_LENDER) {
			if (partner != null && AllMight.isBorrower(partner) && AllMight.state(partner).loanPartner.equals(player.getUUID().toString())) {
				endLoanFromBorrower(partner, EndReason.LOGOUT, player);
			} else {
				restoreLender(player, EndReason.LOGOUT);
			}
		} else if (s.loanRole == AllMightState.LOAN_BORROWER) {
			endLoanFromBorrower(player, EndReason.LOGOUT);
		}
	}

	// ---------------------------------------------------------------- the gold glow

	private static void glow(ServerPlayer from, ServerPlayer to) {
		if (from.getServer() == null) {
			return;
		}
		GLOWS.add(new Glow(from.getUUID(), to.getUUID(), from.getServer().getTickCount()));
		ServerLevel level = (ServerLevel) from.level();
		level.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.5f);
	}

	/** A gold light travels from the giver's hand to the receiver over {@code TRANSFER_GLOW_TICKS}, then bursts around them. */
	private static void tickGlows(MinecraftServer server) {
		if (GLOWS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.3f), 1.3f);
		GLOWS.removeIf(g -> {
			ServerPlayer a = findPlayer(server, g.from());
			ServerPlayer b = findPlayer(server, g.to());
			if (a == null || b == null || a.level() != b.level()) {
				return true;
			}
			ServerLevel level = (ServerLevel) a.level();
			float f = (now - g.start()) / (float) AllMightConfig.TRANSFER_GLOW_TICKS;
			Vec3 from = a.position().add(0, a.getBbHeight() * 0.6, 0);
			Vec3 to = b.position().add(0, b.getBbHeight() * 0.55, 0);
			Vec3 head = from.lerp(to, Math.min(1.0, f));
			double len = from.distanceTo(head);
			for (double d = 0; d < len; d += 0.35) {
				Vec3 q = from.lerp(head, len <= 0 ? 0 : d / len);
				level.sendParticles(gold, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			level.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 2, 0.05, 0.05, 0.05, 0.01);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 2, 0.1, 0.1, 0.1, 0.05);
			if (f >= 1.0f) {
				level.sendParticles(gold, to.x, to.y, to.z, 30, 0.45, 0.7, 0.45, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 12, 0.4, 0.6, 0.4, 0.04);
				level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
				return true;
			}
			return false;
		});
	}
}
