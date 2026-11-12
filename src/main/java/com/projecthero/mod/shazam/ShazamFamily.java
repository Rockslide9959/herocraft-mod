package com.projecthero.mod.shazam;

import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.shazam.data.ShazamState;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam: <b>Share the Power</b> (Shift+V) and the <b>Shazam Family</b>. The transformed champion aims at a squadmate within
 * 16 blocks: for 10 minutes that player can say the word too and becomes a family member -- half the champion's health and damage,
 * the same keys except Eternity Strike (and Share the Power), gold trim on the suit. Up to 5 at once; 10 minute cooldown. When the
 * time is up a transformed member turns back (no lightning).
 */
public final class ShazamFamily {
	private ShazamFamily() {
	}

	/** Shift+V: shares with the squadmate he aims at. */
	static boolean shareAimed(ServerPlayer champion) {
		LivingEntity aimed = ShazamMoves.aimed(champion, ShazamConfig.SHARE_RANGE, 0.6, e -> ShazamMoves.squadmate(champion, e));
		if (!(aimed instanceof ServerPlayer mate)) {
			Shazam.say(champion, "message.projecthero.shazam.share_aim", ChatFormatting.GRAY);
			return false;
		}
		return share(champion, mate);
	}

	/** {@code champion} shares the power with {@code mate}. False (with the reason) if he can't. */
	public static boolean share(ServerPlayer champion, ServerPlayer mate) {
		if (!Shazam.hasPower(champion) || !Shazam.transformed(champion)) {
			return false;
		}
		if (mate == champion || !Squads.areAllies(champion, mate)) {
			Shazam.say(champion, "message.projecthero.shazam.share_aim", ChatFormatting.GRAY);
			return false;
		}
		if (Shazam.hasPower(mate)) {
			Shazam.say(champion, "message.projecthero.shazam.share_champion", ChatFormatting.GRAY);
			return false;
		}
		long now = champion.level().getGameTime();
		ShazamState cs = Shazam.state(champion);
		boolean already = cs.family.getOrDefault(mate.getUUID().toString(), 0L) > now;
		if (!already && cs.familySize(now) >= ShazamConfig.FAMILY_MAX) {
			Shazam.say(champion, "message.projecthero.shazam.share_full", ChatFormatting.GRAY, ShazamConfig.FAMILY_MAX);
			return false;
		}
		long until = now + ShazamConfig.FAMILY_TICKS;
		ShazamState cn = cs.copy();
		cn.family.put(mate.getUUID().toString(), until);
		Shazam.save(champion, cn);
		ShazamState mn = Shazam.state(mate).copy();
		mn.familyUntil = until;
		mn.familyChampion = champion.getUUID().toString();
		Shazam.save(mate, mn);
		Shazam.reconcile(mate);
		// a thread of golden lightning between them
		ShazamMoves.arc(champion.serverLevel(), champion.position().add(0, 1.2, 0), mate.position().add(0, 1.2, 0));
		Vec3 c = mate.position();
		champion.serverLevel().sendParticles(Shazam.dust(Shazam.GOLD, 1.4f), c.x, c.y + 1, c.z, 30, 0.5, 0.9, 0.5, 0.0);
		champion.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 20, 0.5, 0.9, 0.5, 0.1);
		Shazam.sound(mate, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
		mate.displayClientMessage(Component.translatable("message.projecthero.shazam.family_joined", champion.getDisplayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		mate.displayClientMessage(Component.translatable("message.projecthero.shazam.family_hint").withStyle(ChatFormatting.GRAY), false);
		champion.displayClientMessage(Component.translatable("message.projecthero.shazam.family_shared", mate.getDisplayName())
				.withStyle(ChatFormatting.GOLD), true);
		Shazam.startCooldown(champion, ShazamAbilityManager.SHARE_POWER, ShazamConfig.SHARE_COOLDOWN);
		return true;
	}

	/** The member's 10 minutes are up: he turns back and can't say the word any more. */
	static void expire(ServerPlayer member) {
		Shazam.forceUntransform(member);
		ShazamState n = Shazam.state(member).copy();
		n.familyUntil = 0L;
		n.familyChampion = "";
		n.transformed = false;
		Shazam.save(member, n);
		Shazam.reconcile(member);
		member.displayClientMessage(Component.translatable("message.projecthero.shazam.family_ended").withStyle(ChatFormatting.GRAY), false);
	}

	/** Drops members whose time is up from the champion's list. */
	static void prune(ServerPlayer champion) {
		long now = champion.level().getGameTime();
		ShazamState s = Shazam.state(champion);
		if (s.family.values().stream().anyMatch(until -> until <= now)) {
			ShazamState n = s.copy();
			n.family.values().removeIf(until -> until <= now);
			Shazam.save(champion, n);
		}
	}

	/** The champion lost the power: every online family member loses it too. */
	static void disband(ServerPlayer champion) {
		ShazamState s = Shazam.peek(champion);
		if (s == null || s.family.isEmpty() || champion.getServer() == null) {
			return;
		}
		for (Map.Entry<String, Long> e : s.family.entrySet()) {
			try {
				ServerPlayer member = champion.getServer().getPlayerList().getPlayer(UUID.fromString(e.getKey()));
				if (member != null && Shazam.isFamily(member)) {
					expire(member);
				}
			} catch (IllegalArgumentException ignored) {
				// a bad id: nothing to do
			}
		}
	}
}
