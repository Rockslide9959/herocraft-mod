package com.projecthero.mod.doctorfate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.doctorfate.data.FateState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: Nabu's side of the Dominion Gauge, tier by tier ({@link DoctorFate#tier}).
 * <ul>
 *   <li><b>0-24 Whispers</b>: faint golden lines every 40-80 s while the helmet is on.</li>
 *   <li><b>25-49 Override</b>: 1 attack spell in 6 is his pick ({@link #nabuPick}); villagers, golems and tamed animals can't be
 *   hurt (the spell fizzles, {@link DoctorFateSpells#fizzles}).</li>
 *   <li><b>50-74 Pull</b>: the aim drifts toward Chaos (client, {@code DoctorFateClient}); spells cost 10% less; chat gets a gold
 *   "[Nabu]" tag ({@link #chatTag}).</li>
 *   <li><b>75-99 Taking hold</b>: gold screen edges and lagging keys (client); spells cost health; healing is doubled
 *   ({@link DoctorFate#tick}).</li>
 *   <li><b>100 Possession</b>: {@link DoctorFatePossession}.</li>
 * </ul>
 * Each tier counts from its own threshold up (Override still holds at Pull, and so on).
 */
public final class DoctorFateDominion {
	/** Nabu's pick for an Override: a spell and its target. */
	public record Pick(String id, LivingEntity target) {
	}

	private static final Map<UUID, Long> NEXT_WHISPER = new ConcurrentHashMap<>();

	private DoctorFateDominion() {
	}

	static void clearSessionState() {
		NEXT_WHISPER.clear();
	}

	static void forget(UUID id) {
		NEXT_WHISPER.remove(id);
	}

	static void tick(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		if (!s.helmetOn || DoctorFate.possessed(p)) {
			return;
		}
		long now = p.level().getGameTime();
		Long next = NEXT_WHISPER.get(p.getUUID());
		if (next == null) {
			NEXT_WHISPER.put(p.getUUID(), now + DoctorFateConfig.WHISPER_MIN_TICKS / 2);
			return;
		}
		if (now < next) {
			return;
		}
		NEXT_WHISPER.put(p.getUUID(), now + DoctorFateConfig.WHISPER_MIN_TICKS + p.getRandom().nextInt(DoctorFateConfig.WHISPER_SPREAD_TICKS));
		int tier = DoctorFate.tier(s.dominion);
		int line = p.getRandom().nextInt(DoctorFateConfig.WHISPER_LINES);
		String key = "message.projecthero.doctor_fate.whisper." + Math.min(tier, 3) + "." + line;
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
	}

	/** At Pull and up, his chat lines carry a gold "[Nabu]" tag. */
	public static Component chatTag(ServerPlayer sender, Component message) {
		if (!DoctorFate.wearsHelmet(sender) || DoctorFate.dominion(sender) < DoctorFateConfig.TIER_PULL) {
			return message;
		}
		return Component.translatable("chat.projecthero.doctor_fate.nabu_tag").withStyle(ChatFormatting.GOLD).append(" ").append(message);
	}

	/**
	 * Nabu's choice for an Override: Golden Chains on the most chaotic creature near (the strongest creature of Chaos within 24
	 * blocks); with no Chaos about, a Glyph Storm on a pack of three or more threats; otherwise an Ankh Bolt at the strongest threat.
	 * Null when there's nothing worth his attention (the spell goes off as cast).
	 */
	public static Pick nabuPick(ServerPlayer p) {
		double r = DoctorFateConfig.AIM_RANGE;
		List<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> e != p && e.distanceTo(p) <= r && DoctorFateSpells.targetable(p, e, true) && !DoctorFateChaos.isProtected(e));
		LivingEntity chaos = near.stream().filter(DoctorFateChaos::isChaos).max(Comparator.comparingDouble(LivingEntity::getMaxHealth)).orElse(null);
		if (chaos != null) {
			return new Pick(DoctorFateAbilityManager.GOLDEN_CHAINS, chaos);
		}
		List<LivingEntity> threats = near.stream().filter(e -> HeroTargets.isHostile(p, e)).toList();
		if (threats.size() >= 3) {
			Vec3 c = Vec3.ZERO;
			for (LivingEntity e : threats) {
				c = c.add(e.position());
			}
			c = c.scale(1.0 / threats.size());
			final Vec3 centre = c;
			LivingEntity mid = threats.stream().min(Comparator.comparingDouble(e -> e.position().distanceToSqr(centre))).orElse(threats.get(0));
			return new Pick(DoctorFateAbilityManager.GLYPH_STORM, mid);
		}
		LivingEntity strongest = threats.stream().max(Comparator.comparingDouble(LivingEntity::getMaxHealth)).orElse(null);
		return strongest == null ? null : new Pick(DoctorFateAbilityManager.ANKH_BOLT, strongest);
	}
}
