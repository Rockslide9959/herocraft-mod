package com.projecthero.mod.punisher.ability;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.PunisherIntelPayload;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherFeedback;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * v0.15.18 -- R and Shift+R.
 *
 * <ul>
 *   <li><b>R -- Target Designation</b>: mark the enemy you are aiming at (48 blocks, living things only, never a
 *       squadmate or your own pet) for 30 s. It takes +30% damage from everything <em>you</em> hit it with -- guns,
 *       melee, grenades, abilities ({@code LivingEntityPunisherMarkMixin}). One mark per Punisher: marking another
 *       clears the old one. It glows red, and a red marker hangs over it, for you alone. 5 s cooldown.</li>
 *   <li><b>Shift+R -- Threat Assessment</b>: every living thing within 18 blocks glows for 10 s, for you alone.
 *       12 s cooldown.</li>
 * </ul>
 *
 * <p>The glow is never put on the entity: the server sends the ids to the Punisher only ({@link PunisherIntelPayload})
 * and his client draws it ({@code EntityGlowMixin}). Marks are a static map, cleared on death / logout
 * ({@link Punisher#clearTransient}) and server stop ({@code ServerStateReset}).
 */
public final class PunisherMark {
	public static final String ABILITY = "target_designation";
	public static final String THREAT = "threat_assessment";

	private record Mark(LivingEntity target, long until) {
	}

	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	private static final DustParticleOptions MARKER = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.08f), 1.4f);

	private PunisherMark() {
	}

	public static void clearSessionState() {
		MARKS.clear();
	}

	// ---------------- R: Target Designation ----------------

	public static void designate(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, PunisherConfig.MARK_RANGE);
		if (target == null || !canMark(player, target)) {
			PunisherFeedback.message(player, "mark_none");
			player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 0.6f);
			return;
		}
		mark(player, target);
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.MARK_COOLDOWN_TICKS);
	}

	/** Living, harmable, not a squadmate. */
	public static boolean canMark(ServerPlayer player, LivingEntity target) {
		return target != player && !(target instanceof ArmorStand) && target.isAlive()
				&& HeroTargets.canHarm(player, target) && !Squads.areAllies(player, target);
	}

	/** Mark {@code target} for {@code player}, replacing any mark he had. */
	public static void mark(ServerPlayer player, LivingEntity target) {
		long until = player.level().getGameTime() + PunisherConfig.MARK_DURATION_TICKS;
		MARKS.put(player.getUUID(), new Mark(target, until));
		send(player, new PunisherIntelPayload(target.getId(), PunisherConfig.MARK_DURATION_TICKS, new int[0], 0));
		player.playNotifySound(SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.7f, 1.6f);
		player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		PunisherFeedback.message(player, "mark_set");
	}

	/** Who {@code player} has marked right now, or null. */
	public static LivingEntity markOf(ServerPlayer player) {
		Mark m = MARKS.get(player.getUUID());
		return m != null && live(m) ? m.target : null;
	}

	/** Drop {@code player}'s mark (death, logout, power loss). */
	public static void clear(ServerPlayer player) {
		if (MARKS.remove(player.getUUID()) != null) {
			send(player, new PunisherIntelPayload(PunisherIntelPayload.CLEAR_MARK, 0, new int[0], 0));
		}
	}

	/** The +30%: {@code amount} raised when {@code source}'s attacker is the Punisher who marked {@code victim}. */
	public static float scale(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0f || MARKS.isEmpty() || !(source.getEntity() instanceof ServerPlayer attacker)) {
			return amount;
		}
		Mark m = MARKS.get(attacker.getUUID());
		if (m == null || m.target != victim || victim.level().getGameTime() >= m.until) {
			return amount;
		}
		return amount * (1f + PunisherConfig.MARK_DAMAGE_BONUS);
	}

	private static boolean live(Mark m) {
		return !m.target.isRemoved() && m.target.isAlive() && m.target.level().getGameTime() < m.until;
	}

	// ---------------- Shift+R: Threat Assessment ----------------

	public static void assess(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, THREAT)) {
			return;
		}
		List<LivingEntity> seen = threatsAround(player);
		int[] ids = seen.stream().mapToInt(LivingEntity::getId).toArray();
		send(player, new PunisherIntelPayload(PunisherIntelPayload.KEEP_MARK, 0, ids, PunisherConfig.THREAT_DURATION_TICKS));
		player.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.9f, 0.8f);
		player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				"message.projecthero.punisher.threats", ids.length).withStyle(net.minecraft.ChatFormatting.GRAY), true);
		Punisher.triggerCooldown(player, THREAT, PunisherConfig.THREAT_COOLDOWN_TICKS);
	}

	/** Every living thing within 18 blocks but the Punisher himself. */
	public static List<LivingEntity> threatsAround(ServerPlayer player) {
		return AbilityHelpers.living(player.serverLevel(), player.position(), PunisherConfig.THREAT_RADIUS,
				e -> e != player && !(e instanceof ArmorStand) && !e.isSpectator());
	}

	// ---------------- upkeep ----------------

	/** Once per server tick: expire marks, and hang the red marker over each live one (its owner sees it, no one else). */
	public static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Mark> e = it.next();
			Mark m = e.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
			if (owner == null || !live(m)) {
				it.remove();
				if (owner != null) {
					send(owner, new PunisherIntelPayload(PunisherIntelPayload.CLEAR_MARK, 0, new int[0], 0));
				}
				continue;
			}
			if (m.target.level() == owner.level() && m.target.tickCount % 4 == 0 && m.target.level() instanceof ServerLevel level) {
				double y = m.target.getY() + m.target.getBbHeight() + 0.6;
				level.sendParticles(owner, MARKER, true, m.target.getX(), y, m.target.getZ(), 2, 0.08, 0.12, 0.08, 0.0);
			}
		}
	}

	private static void send(ServerPlayer player, PunisherIntelPayload payload) {
		if (player.connection != null && ServerPlayNetworking.canSend(player, PunisherIntelPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
