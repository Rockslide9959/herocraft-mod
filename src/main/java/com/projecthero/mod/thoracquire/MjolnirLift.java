package com.projecthero.mod.thoracquire;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: lifting a free Mjolnir as an outsider. Hero of the Village no longer opens it; the gate, in order:
 * <ol>
 * <li>a hammer bound to someone else never budges for an outsider;</li>
 * <li>Valor must be full ({@link Valor}) -- else "you are not worthy", your Valor %, a heavy clunk and a small shake;</li>
 * <li>it must be thundering where the hammer stands ({@link Storms}) -- else "the storm has not answered";</li>
 * <li>a player who has already won the {@link TrialOfAsgard} lifts it at once; anyone else has to win the trial first (faced
 * without powers, one attempt per storm).</li>
 * </ol>
 * The lift is a short cutscene ({@link #DURATION} ticks): the hammer goes into the main hand and the arm raises it skyward
 * (client pose from the synced {@link ThorAcquireAttachments#MJOLNIR_LIFT} clock), a lightning column strikes the player at
 * {@link #STRIKE}, light bursts at {@link #BIND} -- the same bind as before ({@link Worthiness#ascend}: Thor becomes the
 * Primary power, the hammer binds) -- and Thor's Armour forms around them.
 *
 * <p>The running lifts are a small static map of player UUIDs (no worlds or entities), cleared by {@code ServerStateReset}.
 */
public final class MjolnirLift {
	public static final int DURATION = 76;
	public static final int STRIKE = 26;
	public static final int BIND = 46;
	private static final int FEEDBACK_COOLDOWN = 20;

	public enum Result {
		BOUND_ELSEWHERE, UNIQUE, NOT_WORTHY, NO_STORM, POWERED, RETRY_NEXT_STORM, TRIAL_RUNNING, TRIAL_STARTED, LIFTING, BUSY
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private record Run(UUID player, Vec3 anchor, long start) {
	}

	private MjolnirLift() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(MjolnirLift::tick);
	}

	public static void clearSessionState() {
		RUNS.clear();
	}

	public static boolean isLifting(Player player) {
		return player.getAttachedOrElse(ThorAcquireAttachments.MJOLNIR_LIFT, 0L) != 0L;
	}

	/** An outsider right-clicked a resting Mjolnir: run the gate above. */
	public static Result tryLift(ServerPlayer player, MjolnirEntity hammer) {
		if (isLifting(player)) {
			return Result.BUSY;
		}
		if (Worthiness.boundToSomeoneElse(player, hammer.getItem())) {
			feedback(player, hammer.position(), Component.translatable("message.projecthero.mjolnir.bound_elsewhere"), true);
			return Result.BOUND_ELSEWHERE;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, "thor")) {
			return Result.UNIQUE;
		}
		if (!Valor.isFull(player)) {
			feedback(player, hammer.position(),
					Component.translatable("message.projecthero.mjolnir.not_worthy", Valor.percent(player)), true);
			return Result.NOT_WORTHY;
		}
		ServerLevel level = player.serverLevel();
		if (!Storms.isStorming(level, hammer.blockPosition())) {
			feedback(player, hammer.position(), Component.translatable("message.projecthero.mjolnir.no_storm")
					.append(Component.literal(" ")).append(Component.translatable("message.projecthero.mjolnir.no_storm_hint")
							.withStyle(ChatFormatting.GRAY)), false);
			return Result.NO_STORM;
		}
		if (player.getAttachedOrElse(ThorAcquireAttachments.TRIAL_PASSED, false)) {
			begin(player, hammer);
			return Result.LIFTING;
		}
		return TrialOfAsgard.tryStart(player, hammer);
	}

	/** The hammer won't budge: the reason (with Valor % where it applies), a heavy clunk, smoke and a small shake. */
	static void feedback(ServerPlayer player, Vec3 at, Component message, boolean clunk) {
		long now = player.level().getGameTime();
		if (now < player.getAttachedOrElse(ThorAcquireAttachments.LIFT_FEEDBACK_READY, 0L)) {
			return;
		}
		player.setAttached(ThorAcquireAttachments.LIFT_FEEDBACK_READY, now + FEEDBACK_COOLDOWN);
		player.displayClientMessage(message.copy().withStyle(s -> s.getColor() == null ? s.withColor(ChatFormatting.GOLD) : s), true);
		ServerLevel level = player.serverLevel();
		if (clunk) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.45f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 0.5f);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 14, 0.25, 0.2, 0.25, 0.02);
			if (ServerPlayNetworking.canSend(player, TitanShakePayload.TYPE)) {
				ServerPlayNetworking.send(player, new TitanShakePayload(0.35f, 8));
			}
		} else {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.25f, 0.6f);
		}
	}

	/** Starts the cutscene: the hammer leaves the ground for the player's main hand and the clock starts. */
	public static void begin(ServerPlayer player, MjolnirEntity hammer) {
		ItemStack stack = hammer.getItem().copy();
		hammer.discard();
		begin(player, stack);
	}

	/** Test / trial hook: starts the cutscene with {@code stack} (a Mjolnir) going into the main hand. */
	public static void begin(ServerPlayer player, ItemStack stack) {
		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty()) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			if (!player.getInventory().add(held)) {
				player.drop(held, false);
			}
		}
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		long now = player.level().getGameTime();
		player.setAttached(ThorAcquireAttachments.MJOLNIR_LIFT, now);
		RUNS.put(player.getUUID(), new Run(player.getUUID(), player.position(), now));
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.8f, 0.55f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.35f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 30, 0.8, 0.05, 0.8, 0.04);
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		Iterator<Run> it = RUNS.values().iterator();
		while (it.hasNext()) {
			Run run = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(run.player());
			if (p == null || !p.isAlive()) {
				if (p != null) {
					p.setAttached(ThorAcquireAttachments.MJOLNIR_LIFT, 0L);
				}
				it.remove();
				continue;
			}
			int t = (int) (p.level().getGameTime() - run.start());
			if (step(p, run, t)) {
				p.setAttached(ThorAcquireAttachments.MJOLNIR_LIFT, 0L);
				it.remove();
			}
		}
	}

	/** One tick of the cutscene; true when it is over. */
	private static boolean step(ServerPlayer p, Run run, int t) {
		ServerLevel level = p.serverLevel();
		// held in place for the scene
		p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
		p.hurtMarked = true;
		if (p.position().distanceToSqr(run.anchor()) > 0.09) {
			p.connection.teleport(run.anchor().x, run.anchor().y, run.anchor().z, p.getYRot(), p.getXRot());
		}
		Vec3 head = p.position().add(0, 2.9, 0); // the hammer head held high
		if (t < STRIKE) {
			float build = t / (float) STRIKE;
			if (t % 2 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 2 + (int) (build * 8), 0.2, 0.2, 0.2, 0.05 + build * 0.1);
			}
			if (t % 4 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 1.2, 0.02, 1.2, 0.03);
			}
			if (t == 10) {
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.6f);
			}
			if (t == 18) {
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.2f, 0.8f);
			}
		}
		if (t >= STRIKE && t <= BIND - 2 && (t - STRIKE) % 5 == 0) {
			bolt(level, p.position());
		}
		if (t == STRIKE) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.5f, 0.8f);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0f, 0.9f);
			level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.5, p.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.4, p.getZ(), 90, 0.6, 1.2, 0.6, 0.45);
			shake(level, p, 0.9f, 16);
		}
		if (t > STRIKE && t < BIND && t % 2 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.4, 1.0, 0.4, 0.25);
		}
		if (t == BIND) {
			level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.1, 0.1, 0.1, 0);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 140, 0.2, 0.2, 0.2, 0.55);
			level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.3, 0.3, 0.3, 0.35);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4f, 0.8f);
			shake(level, p, 0.5f, 10);
			bind(p);
		}
		return t >= DURATION;
	}

	/** The bind itself: the stack in the hand is bound in place, Thor becomes the Primary power, Thor's Armour forms. */
	private static void bind(ServerPlayer p) {
		ItemStack stack = p.getMainHandItem();
		if (!stack.is(ModItems.MJOLNIR)) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(ModItems.MJOLNIR)) {
					stack = p.getInventory().getItem(i);
					break;
				}
			}
		}
		if (!stack.is(ModItems.MJOLNIR)) {
			return; // somebody took it mid-scene: nothing to bind
		}
		Worthiness.ascend(p, stack);
		p.setAttached(ThorAcquireAttachments.TRIAL_PASSED, false);
		try {
			com.projecthero.mod.thorarmor.ThorArmor.toggle(p);
		} catch (RuntimeException e) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[MjolnirLift] Thor's Armour did not form", e);
		}
	}

	public static void bolt(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	public static void shake(ServerLevel level, net.minecraft.world.entity.Entity at, float intensity, int ticks) {
		for (ServerPlayer q : level.getPlayers(q -> q.distanceToSqr(at) < 32 * 32)) {
			if (ServerPlayNetworking.canSend(q, TitanShakePayload.TYPE)) {
				ServerPlayNetworking.send(q, new TitanShakePayload(intensity, ticks));
			}
		}
	}
}
