package com.projecthero.mod.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.network.SquadInfoPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The squad feature's server half: friendly-fire suppression, the live sync that feeds the squad
 * screen, and the small helpers the command uses.
 *
 * <h2>Friendly fire</h2>
 * Hooked on {@link ServerLivingEntityEvents#ALLOW_DAMAGE} rather than on this mod's own
 * {@link com.projecthero.mod.hero.power.HeroDamageRules}, deliberately: that class only ever runs for a
 * player who currently has an experimental power selected, whereas a squad has to protect ordinary
 * swords, arrows, TNT and every hero ability alike, for members with no powers at all.
 *
 * <p>Indirect damage counts. The attacker is resolved through {@link DamageSource#getEntity()} (the
 * <em>owner</em>, not the projectile), so a squadmate's arrow, thrown grenade, repulsor beam or falling
 * boulder is just as harmless as their sword. Explicit self-harm is untouched, and so is anything with
 * no attacker behind it -- a squad does not make you fireproof.
 */
public final class Squads {
	/** How often each squad member's client is refreshed with the roster. */
	private static final int SYNC_EVERY_TICKS = 10;

	private Squads() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !blocks(entity, source));
	}

	/** True when this hit is one squadmate striking another and must not land. */
	private static boolean blocks(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayer hurt)) {
			return false;
		}
		// Never intercept the "you cannot survive this" sources -- /kill and the void are not attacks.
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof Player dealer) || dealer.getUUID().equals(hurt.getUUID())) {
			return false;
		}
		return SquadManager.get(hurt.server).sameSquad(hurt.getUUID(), dealer.getUUID());
	}

	// ---------------- sync ----------------

	/** Called once per player per server tick from the mod's own tick hook. */
	public static void serverTick(ServerPlayer player) {
		if (player.tickCount % SYNC_EVERY_TICKS != 0) {
			return;
		}
		SquadManager manager = SquadManager.get(player.server);
		Squad squad = manager.squadOf(player.getUUID());
		if (squad == null) {
			// Only tell a squadless client so once, when it has just left one, rather than every tick.
			if (player.tickCount % (SYNC_EVERY_TICKS * 10) == 0) {
				ServerPlayNetworking.send(player, SquadInfoPayload.none());
			}
			return;
		}
		ServerPlayNetworking.send(player, snapshot(player, squad));
	}

	/** Build this viewer's view of their squad: who is where, how hurt they are, and what they are. */
	public static SquadInfoPayload snapshot(ServerPlayer viewer, Squad squad) {
		List<SquadInfoPayload.Member> out = new ArrayList<>();
		for (UUID id : squad.members()) {
			ServerPlayer member = viewer.server.getPlayerList().getPlayer(id);
			if (member == null) {
				out.add(new SquadInfoPayload.Member(id, offlineName(viewer, id), false, 0.0f, 0.0f, 0.0f,
						0, 0, 0, "", "", id.equals(squad.leader())));
				continue;
			}
			out.add(new SquadInfoPayload.Member(
					id,
					member.getGameProfile().getName(),
					true,
					member.getHealth(),
					member.getMaxHealth(),
					member.getAbsorptionAmount(),
					member.getBlockX(), member.getBlockY(), member.getBlockZ(),
					member.level().dimension().location().getPath(),
					HeroIdentity.describe(member),
					id.equals(squad.leader())));
		}
		return new SquadInfoPayload(squad.name(), out);
	}

	private static String offlineName(ServerPlayer viewer, UUID id) {
		var cached = viewer.server.getProfileCache();
		if (cached != null) {
			var profile = cached.get(id);
			if (profile.isPresent()) {
				return profile.get().getName();
			}
		}
		return id.toString().substring(0, 8);
	}

	// ---------------- messaging ----------------

	/** Tell everybody currently online in {@code squad} something. */
	public static void broadcast(ServerPlayer anyMember, Squad squad, Component message) {
		for (UUID id : squad.members()) {
			ServerPlayer member = anyMember.server.getPlayerList().getPlayer(id);
			if (member != null) {
				member.sendSystemMessage(message);
			}
		}
	}
}
