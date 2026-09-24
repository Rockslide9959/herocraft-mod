package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.spider.data.SpiderManState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The single server-side API for the Spider-Man Hero-Class power. Nothing else pokes
 * {@link SpiderManState} directly; every mutator re-saves via {@link ServerPlayer#setAttached} so the
 * change is persisted and synced.
 *
 * <p>Peer to {@link com.projecthero.mod.ironman.TonyStark} and {@code ThorPassives}: its own
 * attachment, granted server-authoritatively, and it survives logout / restart / world reload /
 * dimension change / death for the same reason worthiness does ({@code persistent} +
 * {@code copyOnDeath}).
 *
 * <h2>Progression</h2>
 * Spider-Man is never obtained directly. The player first mutates into the ordinary
 * {@link #SPIDER_ADHESION_KEY Spider Climbing / Adhesion} experimental power, and only then can an
 * {@link com.projecthero.mod.spider.item.ArachnidMutagenItem Arachnid Mutagen} evolve it. The upgrade
 * <em>consumes</em> Spider Adhesion -- the two are never held at once, and everything Adhesion could
 * do Spider-Man does better (see {@link SpiderClimb}).
 */
public final class SpiderMan {
	/** The experimental power Spider-Man evolves out of. */
	public static final String SPIDER_ADHESION_KEY = "power_16_spider_climbing_adhesion";

	private SpiderMan() {
	}

	// ---------------- state ----------------

	public static SpiderManState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SPIDER_MAN_STATE);
	}

	static void save(ServerPlayer player, SpiderManState state) {
		player.setAttached(ModAttachments.SPIDER_MAN_STATE, state);
	}

	/**
	 * Save a reserve-only change, but only when it is worth a resync: the HUD draws whole points, so
	 * writing every 0.2-per-tick regeneration step would put a packet on the wire twenty times a
	 * second for nothing. The fractional progress is still kept in the live object; only the sync is
	 * deferred until the number actually crosses a point boundary or reaches either end of the range.
	 */
	static void saveReserve(ServerPlayer player, SpiderManState updated, SpiderManState previous) {
		boolean edge = updated.webReserve <= 0.0f || updated.webReserve >= SpiderWebReserve.maxFor(player);
		if (edge || (int) updated.webReserve != (int) previous.webReserve
				|| updated.lastWebUseTick != previous.lastWebUseTick) {
			save(player, updated);
		} else {
			previous.webReserve = updated.webReserve;
		}
	}

	/**
	 * Whether the player has the Spider-Man power. Safe on the client too -- the attachment is synced,
	 * so a client can read it for rendering, but a modified client cannot make the <em>server</em>
	 * believe it has the power (every gameplay check runs against the server-side attachment).
	 */
	public static boolean hasPower(Player player) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s != null && s.hasPower;
	}

	/** Whether the player owns the basic Spider Adhesion experimental power (the upgrade prerequisite). */
	public static boolean hasSpiderAdhesion(ServerPlayer player) {
		return ExperimentalPowers.state(player).ownedPowers.contains(SPIDER_ADHESION_KEY);
	}

	// ---------------- the evolution ----------------

	/**
	 * Evolve Spider Adhesion into Spider-Man. Returns false -- consuming nothing -- when the player
	 * lacks the arachnid adaptation the mutagen needs to act on, or already is Spider-Man.
	 *
	 * <p>On success the old power is removed from the experimental store entirely (so the two are
	 * never both live, and the mutation slot it occupied is genuinely freed), the Hero-Class power is
	 * granted, and the transformation plays.
	 */
	public static boolean evolveFromAdhesion(ServerPlayer player) {
		if (hasPower(player) || !hasSpiderAdhesion(player)) {
			return false;
		}
		// Cleanly stand every experimental power down first: toggles off, passives off, hero flight
		// stopped, then drop ownership of ALL of them. Experimental mutations and a Hero-Tier power
		// cannot be mixed, so becoming Spider-Man consumes not just Spider Adhesion but any other
		// mutation the player was carrying (the mutagen is a total rewrite of their biology).
		com.projecthero.mod.hero.HeroTiers.claimPrimary(player, "spider_man");
		ExperimentalPowers.setActive(player, null);
		if (com.projecthero.mod.hero.power.HeroFlight.isFlying(player)) {
			com.projecthero.mod.hero.power.HeroFlight.setFlying(player, false);
		}
		for (Power owned : new java.util.ArrayList<>(ExperimentalPowers.ownedPowers(player))) {
			ExperimentalPowers.forget(player, owned);
		}
		PowerPassives.reconcileActive(player);

		SpiderManState s = state(player).copy();
		s.hasPower = true;
		s.webReserve = SpiderWebReserve.MAX;
		s.climbState = 0;
		s.swinging = false;
		save(player, s);
		SpiderPassives.reconcile(player);

		transformationFx(player);
		return true;
	}

	/** Testing/admin only -- see {@link com.projecthero.mod.command.SpiderManCommand}. */
	public static void revoke(ServerPlayer player) {
		SpiderSwing.detach(player, false);
		// The Symbiote cannot exist without a Spider-Man host: retract the suit and drop its buffs. The
		// bond (hasSymbiote) is kept -- re-granting Spider-Man lets the player press H and get it back.
		com.projecthero.mod.symbiote.Symbiote.clearTransient(player);
		SpiderManState s = state(player).copy();
		s.hasPower = false;
		s.climbState = 0;
		s.swinging = false;
		s.abilityReadyAt.clear();
		save(player, s);
		SpiderPassives.reconcile(player);
	}

	private static void transformationFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 0.8f);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 70, 0.45, 0.9, 0.45, 0.12);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 45, 0.5, 0.9, 0.5, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 25, 0.35, 0.8, 0.35, 0.05);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);

		// brief pulse while the mutation settles in
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, false));

		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.evolved")
				.withStyle(ChatFormatting.DARK_RED), false);
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.acquired")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
	}

	/**
	 * Set the reserve outright. Admin and testing only -- ordinary play goes through
	 * {@link SpiderWebReserve}.
	 *
	 * <p>Also lifts the post-ability regeneration hold. Setting the reserve by hand and then watching
	 * it sit frozen for a second and a half because of a web fired before the command is not what
	 * anyone means by it.
	 */
	public static void setWebReserve(ServerPlayer player, float amount) {
		SpiderManState c = state(player).copy();
		c.webReserve = Math.max(0.0f, Math.min(SpiderWebReserve.maxFor(player), amount));
		c.lastWebUseTick = player.level().getGameTime() - SpiderWebReserve.REGEN_DELAY_TICKS;
		save(player, c);
	}

	// ---------------- ability cooldowns (absolute ready-at game time) ----------------

	public static boolean abilityReady(ServerPlayer player, String abilityId) {
		Long readyAt = state(player).abilityReadyAt.get(abilityId);
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int abilityCooldownRemaining(Player player, String abilityId) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (s == null) {
			return 0;
		}
		Long readyAt = s.abilityReadyAt.get(abilityId);
		return readyAt == null ? 0 : (int) Math.max(0L, readyAt - player.level().getGameTime());
	}

	public static void triggerCooldown(ServerPlayer player, String abilityId, int ticks) {
		if (ticks <= 0) {
			return;
		}
		SpiderManState c = state(player).copy();
		c.abilityReadyAt.put(abilityId, player.level().getGameTime() + ticks);
		save(player, c);
	}

	// ---------------- lifecycle ----------------

	/**
	 * Drop every transient traversal state. Called on death, respawn, dimension change and logout
	 * (spec sections 39-41) so no phantom anchor, rope or surface attachment can survive into a world
	 * where its coordinates mean nothing. The <em>power itself</em> is never removed here -- the mod's
	 * other permanent powers do not vanish on death and neither does this one.
	 */
	public static void clearTransient(ServerPlayer player) {
		SpiderSwing.clearFlightGrant(player);
		// Symbiote becomes inactive on death / logout / dimension change (the bond is kept) -- no stuck
		// suit, no lingering armour or webbing buff on the corpse or the fresh entity.
		com.projecthero.mod.symbiote.Symbiote.clearTransient(player);
		if (player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0) != 0) {
			player.setAttached(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		}
		SpiderManState s = state(player);
		if (!s.swinging && s.climbState == 0) {
			return;
		}
		SpiderManState c = s.copy();
		c.swinging = false;
		c.climbState = 0;
		c.artificialAnchor = false;
		save(player, c);
	}

	/** Join / respawn safety net: a fresh player entity needs its passives re-established. */
	public static void onPlayerJoin(ServerPlayer player) {
		clearTransient(player);
		SpiderPassives.reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		onPlayerJoin(player);
	}
}
