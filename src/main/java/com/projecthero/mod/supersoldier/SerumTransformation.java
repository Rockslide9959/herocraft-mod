package com.projecthero.mod.supersoldier;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import org.joml.Vector3f;

/**
 * v0.17.7 (user: "upon drinking the super soldier serum you get nauseous for 1 minute, you get weak and slow aswell and you keep
 * taking tick damage but you wont drop below half a heart, a bar at the bottom right of your screen will show the timer completion
 * ... after the minute you get the powers"): the one-minute transformation between a serum that takes (the refined one, or a lucky
 * unrefined one) and the power.
 *
 * <ul>
 *   <li>{@link #DURATION} ticks (60 s). The elapsed ticks live in one attachment ({@code projecthero:serum_transformation}):
 *       persistent (a relog carries on where it stopped), <b>not</b> copied on death, synced to the drinker only (the HUD's blue bar).
 *       No attachment = no transformation.</li>
 *   <li>Nausea, Weakness and Slowness the whole minute -- given back the tick they go missing (milk does not skip it).</li>
 *   <li>{@link #STING} damage every {@link #STING_INTERVAL} ticks ({@link #SERUM_TRANSFORMATION}, its own death message), never
 *       taking him under {@link #FLOOR} health -- the amount is cut to what is left above it, and an {@code ALLOW_DEATH} guard
 *       stands behind that in case anything scales it up.</li>
 *   <li>At the end: the effects come off and {@link SuperSoldier#grant} runs (the one-Primary-power rule, title and chat).</li>
 *   <li>Death cancels it (nothing is granted); one at a time -- another serum during it is refused and kept.</li>
 * </ul>
 * Ticked for every online player from the server tick; mock players in gametests are driven through {@link #tick} directly.
 * No static state (nothing for {@code ServerStateReset}).
 */
public final class SerumTransformation {
	/** One minute. */
	public static final int DURATION = 20 * 60;
	/** One sting every 2 s. */
	public static final int STING_INTERVAL = 40;
	/** Half a heart a sting. */
	public static final float STING = 1.0f;
	/** He never goes below half a heart from it. */
	public static final float FLOOR = 1.0f;

	public static final ResourceKey<DamageType> SERUM_TRANSFORMATION = ResourceKey.create(Registries.DAMAGE_TYPE,
			ProjectHeroMod.id("serum_transformation"));

	/** Elapsed ticks of the transformation (0..DURATION); absent when there is none. */
	public static final AttachmentType<Integer> TICKS = AttachmentRegistry.create(ProjectHeroMod.id("serum_transformation"),
			builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	@SuppressWarnings("unchecked")
	private static final Holder<MobEffect>[] EFFECTS = new Holder[] { MobEffects.CONFUSION, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN };

	private SerumTransformation() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.hasAttached(TICKS)) {
					tick(p);
				}
			}
		});
		// the sting can never be the thing that kills him
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (source.is(SERUM_TRANSFORMATION)) {
				entity.setHealth(FLOOR);
				return false;
			}
			return true;
		});
		// dying mid-transformation ends it: nothing is granted
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && p.hasAttached(TICKS)) {
				p.removeAttached(TICKS);
			}
		});
	}

	// ---------------------------------------------------------------- queries (safe on the client: the attachment is synced to him)

	public static boolean active(Player player) {
		return player.getAttachedOrElse(TICKS, null) != null;
	}

	/** Elapsed ticks, or -1 when there is no transformation. */
	public static int elapsed(Player player) {
		Integer t = player.getAttachedOrElse(TICKS, null);
		return t == null ? -1 : t;
	}

	/** 0..1 through the minute (0 when there is none). */
	public static float progress(Player player) {
		int t = elapsed(player);
		return t < 0 ? 0f : Math.min(1f, t / (float) DURATION);
	}

	// ---------------------------------------------------------------- server

	/** Starts the minute. False (nothing changes) if one is already running or he already has the power. */
	public static boolean start(ServerPlayer player) {
		if (active(player) || SuperSoldier.hasPower(player)) {
			return false;
		}
		player.setAttached(TICKS, 0);
		applyEffects(player, DURATION);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.super_soldier.transforming")
				.withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** One tick of the transformation (the server tick calls it for every online player who has one). */
	public static void tick(ServerPlayer player) {
		Integer t = player.getAttachedOrElse(TICKS, null);
		if (t == null) {
			return;
		}
		if (!player.isAlive()) {
			player.removeAttached(TICKS); // died: nothing is granted
			return;
		}
		if (SuperSoldier.hasPower(player)) {
			end(player); // granted some other way (the admin command) -- the minute is moot
			return;
		}
		int n = t + 1;
		if (n >= DURATION) {
			end(player);
			SuperSoldier.grant(player);
			return;
		}
		applyEffects(player, DURATION - n);
		if (n % STING_INTERVAL == 0) {
			sting(player);
		}
		player.setAttached(TICKS, n);
	}

	/** Cancels it without a grant (and takes the effects off). */
	public static void cancel(ServerPlayer player) {
		if (active(player)) {
			end(player);
		}
	}

	private static void end(ServerPlayer player) {
		player.removeAttached(TICKS);
		for (Holder<MobEffect> e : EFFECTS) {
			player.removeEffect(e);
		}
	}

	/** Nausea, Weakness and Slowness for (at least) the rest of the minute -- re-applied whenever one is gone or shorter (milk). */
	private static void applyEffects(ServerPlayer player, int remaining) {
		for (Holder<MobEffect> e : EFFECTS) {
			MobEffectInstance cur = player.getEffect(e);
			if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < remaining)) {
				player.addEffect(new MobEffectInstance(e, remaining + 5, 0, false, true, true));
			}
		}
	}

	/** Half a heart, cut to what is left above the floor (so it never takes him under half a heart). */
	private static void sting(ServerPlayer player) {
		float amount = Math.min(STING, player.getHealth() - FLOOR);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(new DustParticleOptions(new Vector3f(0.25f, 0.45f, 1.0f), 1.0f),
				player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
		if (amount <= 0.01f) {
			return;
		}
		player.hurt(source(level), amount);
		if (player.getHealth() < FLOOR && player.isAlive()) {
			player.setHealth(FLOOR);
		}
	}

	public static DamageSource source(ServerLevel level) {
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SERUM_TRANSFORMATION));
	}
}
