package com.projecthero.mod.miraculous.hawkmoth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.hawkmoth.entity.AkumaEntity;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothMarksPayload;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: everything Hawk Moth has corrupted -- akumatized mobs, Champion mobs, the Champion player, the open offers and
 * the akumas still in the air. Server only, static (dropped by {@link #clearSessionState} from {@code ServerStateReset}
 * through the kit), ticked once a server tick from {@link HawkMothMod}.
 *
 * <h2>An akumatized mob</h2>
 * Its own target goals are lifted out (saved, put back on release) and the akuma picks its fights instead: the target
 * Hawk Moth gave it (Akuma Link), what he is hitting, what hit him, else the nearest threat to him
 * ({@link HeroTargets#isHostile}) within {@link HawkMothConfig#AKUMA_GUARD_RADIUS}. Never him, his squad, his pets or
 * his other akumas -- and a damage veto ({@link #allowDamage}) makes sure even a stray arrow or a creeper's blast can't
 * hurt them. A mob with no attack of its own (a cow) gets a plain melee goal. Health and damage go up through TRANSIENT
 * attribute modifiers (never saved: an unloaded chunk or a server stop can't leave a mob changed); hits that aren't its
 * own melee (arrows, fireballs, explosions) are scaled in the veto.
 *
 * <h2>A Champion</h2>
 * The same, plus 1.5x size, 3x health, 2x damage and one themed power ({@link Power}). A player only becomes one by
 * clicking [Accept] on the offer ({@code /hawkmoth accept}); everything is taken back off exactly when it ends.
 */
public final class HawkMothAkumas {
	/** A Champion's one themed power. */
	public enum Power {
		FIRE("fire"), ICE("ice"), STORM("storm");

		public final String id;

		Power(String id) {
			this.id = id;
		}
	}

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("hawk_moth_akuma_health");
	private static final ResourceLocation DAMAGE_ID = PowerToggles.id("hawk_moth_akuma_damage");
	private static final ResourceLocation SCALE_ID = PowerToggles.id("hawk_moth_champion_scale");

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	/** One akumatized (or Champion) mob. */
	public static final class Bond {
		public final UUID owner;
		ServerPlayer ownerRef;
		public final Mob mob;
		public boolean champion;
		public long until;
		public Power power;
		final List<WrappedGoal> savedTargets = new ArrayList<>();
		Goal melee;
		long nextPick;
		long nextPower;
		long nextFollow;

		Bond(ServerPlayer owner, Mob mob) {
			this.owner = owner.getUUID();
			this.ownerRef = owner;
			this.mob = mob;
		}
	}

	/** The Champion player. */
	public static final class PlayerChampion {
		public final UUID owner;
		public final UUID player;
		public long until;
		public final Power power;
		long nextPower;

		PlayerChampion(UUID owner, UUID player, long until, Power power) {
			this.owner = owner;
			this.player = player;
			this.until = until;
			this.power = power;
		}
	}

	/** An offer waiting for a player's answer. */
	public record Offer(UUID owner, UUID player, long until, Power power) {
	}

	/** Mob UUID -> its bond. */
	private static final Map<UUID, Bond> BONDS = new ConcurrentHashMap<>();
	/** Champion player UUID -> the Champion. */
	private static final Map<UUID, PlayerChampion> CHAMPIONS = new ConcurrentHashMap<>();
	/** Player UUID -> the offer waiting for them. */
	private static final Map<UUID, Offer> OFFERS = new ConcurrentHashMap<>();
	/** Owner UUID -> akumas in the air. */
	private static final Map<UUID, Set<AkumaEntity>> FLYING = new ConcurrentHashMap<>();
	/** Owner UUID -> the target he pointed his akumas at (Akuma Link). */
	private static final Map<UUID, LivingEntity> LINKED = new ConcurrentHashMap<>();
	/** Dimension -> the last marks list sent there (ids then kinds), so unchanged lists are not re-sent every time. */
	private static final Map<String, List<Integer>> LAST_SENT = new HashMap<>();

	private HawkMothAkumas() {
	}

	public static void clearSessionState() {
		BONDS.clear();
		CHAMPIONS.clear();
		OFFERS.clear();
		FLYING.clear();
		LINKED.clear();
		LAST_SENT.clear();
	}

	// ---------------------------------------------------------------- queries

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= HawkMothConfig.BOSS_HEALTH || TitanCombat.isBoss(e);
	}

	public static Bond bond(Entity e) {
		return e == null ? null : BONDS.get(e.getUUID());
	}

	public static boolean isAkumatized(Entity e) {
		return bond(e) != null;
	}

	/** Is {@code e} one of {@code owner}'s (an akumatized or Champion mob, or his Champion player)? */
	public static boolean isOwnedBy(Entity e, UUID owner) {
		if (e == null || owner == null) {
			return false;
		}
		Bond b = BONDS.get(e.getUUID());
		if (b != null) {
			return b.owner.equals(owner);
		}
		PlayerChampion c = CHAMPIONS.get(e.getUUID());
		return c != null && c.owner.equals(owner);
	}

	public static PlayerChampion champion(Player p) {
		return p == null ? null : CHAMPIONS.get(p.getUUID());
	}

	public static Offer offer(Player p) {
		return p == null ? null : OFFERS.get(p.getUUID());
	}

	/** {@code owner}'s akumatized mobs (Champion included), alive. */
	public static List<Bond> bondsOf(UUID owner) {
		List<Bond> out = new ArrayList<>();
		for (Bond b : BONDS.values()) {
			if (b.owner.equals(owner) && b.mob.isAlive() && !b.mob.isRemoved()) {
				out.add(b);
			}
		}
		return out;
	}

	/** Plain akumas out: bonded (not the Champion) plus SEND akumas in the air. Capped by {@link HawkMothConfig#AKUMA_MAX}. */
	public static int akumasOut(UUID owner) {
		int n = 0;
		for (Bond b : bondsOf(owner)) {
			if (!b.champion) {
				n++;
			}
		}
		for (AkumaEntity a : flying(owner)) {
			if (a.mode() == AkumaEntity.SEND) {
				n++;
			}
		}
		return n;
	}

	/** Whether {@code owner} already has a Champion (mob or player), an offer open, or a Rise akuma in the air. */
	public static boolean championOut(UUID owner) {
		for (Bond b : bondsOf(owner)) {
			if (b.champion) {
				return true;
			}
		}
		for (PlayerChampion c : CHAMPIONS.values()) {
			if (c.owner.equals(owner)) {
				return true;
			}
		}
		for (Offer o : OFFERS.values()) {
			if (o.owner().equals(owner)) {
				return true;
			}
		}
		for (AkumaEntity a : flying(owner)) {
			if (a.mode() == AkumaEntity.RISE) {
				return true;
			}
		}
		return false;
	}

	/** {@code owner}'s Champion player, online (null if none). */
	public static ServerPlayer championPlayerOf(ServerPlayer owner) {
		for (PlayerChampion c : CHAMPIONS.values()) {
			if (c.owner.equals(owner.getUUID()) && owner.server.getPlayerList().getPlayer(c.player) instanceof ServerPlayer p) {
				return p;
			}
			if (c.owner.equals(owner.getUUID()) && owner.level().getPlayerByUUID(c.player) instanceof ServerPlayer p) {
				return p;
			}
		}
		return null;
	}

	private static Set<AkumaEntity> flying(UUID owner) {
		Set<AkumaEntity> s = FLYING.get(owner);
		if (s == null) {
			return Set.of();
		}
		s.removeIf(a -> a.isRemoved());
		return s;
	}

	// ---------------------------------------------------------------- sending

	/** Sends an akuma from {@code from} at {@code target}. */
	public static AkumaEntity send(ServerPlayer owner, Vec3 from, LivingEntity target, byte mode) {
		AkumaEntity a = AkumaEntity.launch(owner, from, target, mode);
		if (a != null) {
			FLYING.computeIfAbsent(owner.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(a);
		}
		return a;
	}

	/** An akuma reached what it was flying at (server). */
	public static void arrive(AkumaEntity akuma, Entity t) {
		ServerPlayer owner = akuma.owner();
		Set<AkumaEntity> set = owner == null ? null : FLYING.get(owner.getUUID());
		if (set != null) {
			set.remove(akuma);
		}
		if (!(t.level() instanceof ServerLevel level)) {
			return;
		}
		if (akuma.mode() == AkumaEntity.RECALL) {
			level.sendParticles(ParticleTypes.WITCH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
			return;
		}
		if (owner == null || !Miraculous.transformedAs(owner, Kwami.NOOROO) || !(t instanceof LivingEntity target)) {
			flightFailed(akuma);
			return;
		}
		flash(level, target);
		if (akuma.mode() == AkumaEntity.SEND) {
			if (target instanceof Mob mob && !isBoss(mob) && bond(mob) == null && akumasOut(owner.getUUID()) < HawkMothConfig.AKUMA_MAX) {
				akumatize(owner, mob, false);
			}
			return;
		}
		// Rise, Akuma!
		if (target instanceof ServerPlayer player) {
			if (!makeOffer(owner, player)) {
				HawkMothKit.riseFailed(owner);
			}
		} else if (target instanceof Mob mob && !isBoss(mob)) {
			Bond old = bond(mob);
			if (old != null && !old.owner.equals(owner.getUUID())) {
				HawkMothKit.riseFailed(owner);
				return;
			}
			if (old != null) {
				release(old, false);
			}
			akumatize(owner, mob, true);
			owner.displayClientMessage(Component.translatable("message.projecthero.hawk_moth.champion_mob", mob.getDisplayName())
					.withStyle(ChatFormatting.DARK_PURPLE), true);
		} else {
			HawkMothKit.riseFailed(owner);
		}
	}

	/** An akuma lost its target or ran out of time. */
	public static void flightFailed(AkumaEntity akuma) {
		UUID id = akuma.ownerId();
		if (id != null) {
			Set<AkumaEntity> set = FLYING.get(id);
			if (set != null) {
				set.remove(akuma);
			}
		}
		if (akuma.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SMOKE, akuma.getX(), akuma.getY(), akuma.getZ(), 5, 0.1, 0.1, 0.1, 0.01);
		}
		if (akuma.mode() == AkumaEntity.RISE && akuma.owner() != null) {
			HawkMothKit.riseFailed(akuma.owner());
		}
	}

	/** The purple-black flash of an akuma sinking in. */
	private static void flash(ServerLevel level, LivingEntity t) {
		Vec3 c = t.getEyePosition();
		level.sendParticles(new DustParticleOptions(new Vector3f(0.35f, 0.05f, 0.5f), 1.6f), c.x, c.y, c.z, 30, 0.35, 0.35, 0.35, 0.05);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 10, 0.25, 0.25, 0.25, 0.04);
		level.sendParticles(ParticleTypes.WITCH, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 0.9f, 0.7f);
	}

	// ---------------------------------------------------------------- bonding / releasing mobs

	/** Akumatizes {@code mob} for {@code owner}: an akuma ({@code champion} false) or a Champion. */
	public static Bond akumatize(ServerPlayer owner, Mob mob, boolean champion) {
		Bond b = new Bond(owner, mob);
		long now = mob.level().getGameTime();
		b.champion = champion;
		b.until = now + (champion ? HawkMothConfig.CHAMPION_TICKS : HawkMothConfig.AKUMA_TICKS);
		b.power = champion ? Power.values()[mob.getRandom().nextInt(Power.values().length)] : null;
		b.nextPower = now + 20;
		// its own target picking comes out (saved for the release); the akuma decides
		for (WrappedGoal w : new ArrayList<>(targets(mob).getAvailableGoals())) {
			b.savedTargets.add(w);
		}
		targets(mob).removeAllGoals(g -> true);
		forgetTarget(mob);
		if (mob instanceof PathfinderMob pm && !fightsAlready(mob)) {
			b.melee = new AkumaMeleeGoal(pm);
			goals(mob).addGoal(0, b.melee);
		}
		double health = champion ? HawkMothConfig.CHAMPION_HEALTH_BONUS : HawkMothConfig.AKUMA_HEALTH_BONUS;
		double damage = champion ? HawkMothConfig.CHAMPION_DAMAGE_BONUS : HawkMothConfig.AKUMA_DAMAGE_BONUS;
		float fraction = mob.getHealth() / Math.max(1f, mob.getMaxHealth());
		transientModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		transientModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (champion) {
			transientModifier(mob, Attributes.SCALE, SCALE_ID, HawkMothConfig.CHAMPION_SCALE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
		mob.setHealth(fraction * mob.getMaxHealth());
		BONDS.put(mob.getUUID(), b);
		pick(b, now, true);
		return b;
	}

	/** Frees a mob: modifiers off, health capped, its own target goals back. {@code recall}: an akuma flies home. */
	public static void release(Bond b, boolean recall) {
		BONDS.remove(b.mob.getUUID());
		Mob mob = b.mob;
		clearModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID);
		clearModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
		clearModifier(mob, Attributes.SCALE, SCALE_ID);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
		if (b.melee != null) {
			goals(mob).removeGoal(b.melee);
		}
		targets(mob).removeAllGoals(g -> true);
		for (WrappedGoal w : b.savedTargets) {
			targets(mob).addGoal(w.getPriority(), w.getGoal());
		}
		forgetTarget(mob);
		if (mob.isAlive() && !mob.isRemoved() && mob.level() instanceof ServerLevel level) {
			Vec3 c = mob.getEyePosition();
			level.sendParticles(new DustParticleOptions(new Vector3f(0.35f, 0.05f, 0.5f), 1.0f), c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.03);
			ServerPlayer owner = ownerOf(b);
			if (recall && owner != null && owner.level() == level) {
				AkumaEntity.launch(owner, c, owner, AkumaEntity.RECALL);
			}
		}
	}

	/** Every akuma of {@code owner} flies home (Shift+G): Champions stay. Returns how many. */
	public static int recall(ServerPlayer owner) {
		int n = 0;
		for (Bond b : bondsOf(owner.getUUID())) {
			if (!b.champion) {
				release(b, true);
				n++;
			}
		}
		for (AkumaEntity a : new ArrayList<>(flying(owner.getUUID()))) {
			if (a.mode() == AkumaEntity.SEND) {
				a.discard();
				n++;
			}
		}
		LINKED.remove(owner.getUUID());
		return n;
	}

	/** Detransformation, logout, death: every akuma, the Champion, the offers and the akumas in the air go. */
	public static void releaseAll(ServerPlayer owner) {
		UUID id = owner.getUUID();
		for (Bond b : new ArrayList<>(BONDS.values())) {
			if (b.owner.equals(id)) {
				release(b, false);
			}
		}
		for (PlayerChampion c : new ArrayList<>(CHAMPIONS.values())) {
			if (c.owner.equals(id)) {
				ServerPlayer p = findPlayer(owner.server, owner.serverLevel(), c.player);
				endChampion(c, p, "message.projecthero.hawk_moth.champion_freed");
			}
		}
		for (Offer o : new ArrayList<>(OFFERS.values())) {
			if (o.owner().equals(id)) {
				OFFERS.remove(o.player());
				ServerPlayer p = findPlayer(owner.server, owner.serverLevel(), o.player());
				if (p != null) {
					p.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.offer_withdrawn").withStyle(ChatFormatting.GRAY));
				}
			}
		}
		Set<AkumaEntity> set = FLYING.remove(id);
		if (set != null) {
			set.forEach(Entity::discard);
		}
		LINKED.remove(id);
	}

	/** Forget everything tied to one player (logout): as an owner and as a Champion / offer target. */
	public static void forgetPlayer(ServerPlayer player) {
		PlayerChampion c = CHAMPIONS.get(player.getUUID());
		if (c != null) {
			endChampion(c, player, null);
		}
		Offer o = OFFERS.remove(player.getUUID());
		if (o != null) {
			ServerPlayer owner = findPlayer(player.server, player.serverLevel(), o.owner());
			if (owner != null) {
				HawkMothKit.riseFailed(owner);
			}
		}
	}

	private static boolean fightsAlready(Mob mob) {
		for (WrappedGoal w : goals(mob).getAvailableGoals()) {
			Goal g = w.getGoal();
			if (g instanceof MeleeAttackGoal || g instanceof RangedAttackGoal || g instanceof RangedBowAttackGoal<?>
					|| g instanceof RangedCrossbowAttackGoal<?> || g instanceof SwellGoal) {
				return true;
			}
		}
		// brain mobs with an attack of their own (piglins, hoglins) fight off their ATTACK_TARGET memory
		return mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)
				&& mob.getAttribute(Attributes.ATTACK_DAMAGE) != null && !(mob instanceof net.minecraft.world.entity.npc.AbstractVillager);
	}

	private static void forgetTarget(Mob mob) {
		mob.setTarget(null);
		if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
		if (mob.getBrain().checkMemory(MemoryModuleType.ANGRY_AT, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
		}
	}

	private static void transientModifier(LivingEntity e, net.minecraft.core.Holder<Attribute> attr, ResourceLocation id, double amount,
			AttributeModifier.Operation op) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		inst.addTransientModifier(new AttributeModifier(id, amount, op));
	}

	static net.minecraft.world.entity.ai.goal.GoalSelector goals(Mob mob) {
		return ((com.projecthero.mod.mixin.MobGoalsAccessor) mob).projecthero$goalSelector();
	}

	static net.minecraft.world.entity.ai.goal.GoalSelector targets(Mob mob) {
		return ((com.projecthero.mod.mixin.MobGoalsAccessor) mob).projecthero$targetSelector();
	}

	private static void clearModifier(LivingEntity e, net.minecraft.core.Holder<Attribute> attr, ResourceLocation id) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- the player offer

	/** The akuma reached a player: the classic offer, to them alone. False if they can't take one. */
	public static boolean makeOffer(ServerPlayer owner, ServerPlayer player) {
		if (player == owner || CHAMPIONS.containsKey(player.getUUID()) || OFFERS.containsKey(player.getUUID()) || !player.isAlive()) {
			return false;
		}
		Power power = Power.values()[player.getRandom().nextInt(Power.values().length)];
		Offer o = new Offer(owner.getUUID(), player.getUUID(), player.level().getGameTime() + HawkMothConfig.OFFER_TICKS, power);
		OFFERS.put(player.getUUID(), o);
		player.sendSystemMessage(offerText(power));
		owner.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.offer_sent", player.getDisplayName(),
				HawkMothConfig.OFFER_TICKS / 20).withStyle(ChatFormatting.DARK_PURPLE));
		return true;
	}

	/** "I am Hawk Moth. I offer you the power to ..." with [Accept] / [Refuse]. */
	public static Component offerText(Power power) {
		MutableComponent text = Component.translatable("message.projecthero.hawk_moth.offer",
				Component.translatable("message.projecthero.hawk_moth.offer_power." + power.id)).withStyle(ChatFormatting.LIGHT_PURPLE,
						ChatFormatting.ITALIC);
		MutableComponent accept = Component.translatable("message.projecthero.hawk_moth.offer_accept").withStyle(Style.EMPTY
				.withColor(ChatFormatting.GREEN).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hawkmoth accept"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.projecthero.hawk_moth.offer_accept_hover"))));
		MutableComponent refuse = Component.translatable("message.projecthero.hawk_moth.offer_refuse").withStyle(Style.EMPTY
				.withColor(ChatFormatting.RED).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hawkmoth refuse"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.projecthero.hawk_moth.offer_refuse_hover"))));
		return Component.empty().append(text).append(Component.literal("\n")).append(accept).append(Component.literal("  ")).append(refuse);
	}

	/** [Accept]: true if the player became a Champion. */
	public static boolean accept(ServerPlayer player) {
		Offer o = OFFERS.remove(player.getUUID());
		if (o == null || player.level().getGameTime() > o.until()) {
			player.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.offer_none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerPlayer owner = findPlayer(player.server, player.serverLevel(), o.owner());
		if (owner == null || !Miraculous.transformedAs(owner, Kwami.NOOROO)) {
			player.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.offer_none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		PlayerChampion c = new PlayerChampion(o.owner(), player.getUUID(), player.level().getGameTime() + HawkMothConfig.CHAMPION_TICKS, o.power());
		CHAMPIONS.put(player.getUUID(), c);
		float fraction = player.getHealth() / Math.max(1f, player.getMaxHealth());
		transientModifier(player, Attributes.MAX_HEALTH, HEALTH_ID, HawkMothConfig.CHAMPION_HEALTH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		transientModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, HawkMothConfig.CHAMPION_DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		transientModifier(player, Attributes.SCALE, SCALE_ID, HawkMothConfig.CHAMPION_SCALE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		player.setHealth(fraction * player.getMaxHealth());
		if (player.level() instanceof ServerLevel level) {
			flash(level, player);
		}
		Component name = Component.translatable("message.projecthero.hawk_moth.champion_name." + o.power().id);
		player.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.accepted", name).withStyle(ChatFormatting.DARK_PURPLE));
		owner.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.accepted_owner", player.getDisplayName(), name)
				.withStyle(ChatFormatting.DARK_PURPLE));
		return true;
	}

	/** [Refuse] (or the offer ran out): nothing changes. */
	public static void refuse(ServerPlayer player, boolean timedOut) {
		Offer o = OFFERS.remove(player.getUUID());
		if (o == null) {
			if (!timedOut) {
				player.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.offer_none").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		player.sendSystemMessage(Component.translatable(timedOut ? "message.projecthero.hawk_moth.offer_expired"
				: "message.projecthero.hawk_moth.refused").withStyle(ChatFormatting.GRAY));
		ServerPlayer owner = findPlayer(player.server, player.serverLevel(), o.owner());
		if (owner != null) {
			owner.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.refused_owner", player.getDisplayName())
					.withStyle(ChatFormatting.GRAY));
			HawkMothKit.riseFailed(owner);
		}
	}

	/** A Champion player's time is up (or Hawk Moth let go, or they died / left): everything back exactly. */
	public static void endChampion(PlayerChampion c, ServerPlayer p, String messageKey) {
		CHAMPIONS.remove(c.player);
		if (p == null) {
			return;
		}
		clearModifier(p, Attributes.MAX_HEALTH, HEALTH_ID);
		clearModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
		clearModifier(p, Attributes.SCALE, SCALE_ID);
		if (p.getHealth() > p.getMaxHealth()) {
			p.setHealth(p.getMaxHealth());
		}
		if (messageKey != null) {
			p.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
		}
		if (p.isAlive() && p.level() instanceof ServerLevel level) {
			Vec3 c0 = p.getEyePosition();
			level.sendParticles(new DustParticleOptions(new Vector3f(0.35f, 0.05f, 0.5f), 1.0f), c0.x, c0.y, c0.z, 16, 0.3, 0.3, 0.3, 0.03);
		}
	}

	private static ServerPlayer findPlayer(MinecraftServer server, ServerLevel level, UUID id) {
		if (server != null && server.getPlayerList().getPlayer(id) instanceof ServerPlayer p) {
			return p;
		}
		// mock players (game tests) are not in the player list
		return level != null && level.getPlayerByUUID(id) instanceof ServerPlayer p ? p : null;
	}

	// ---------------------------------------------------------------- Akuma Link

	/** Points every one of {@code owner}'s akumatized mobs at {@code target}. Returns how many took it. */
	public static int link(ServerPlayer owner, LivingEntity target) {
		LINKED.put(owner.getUUID(), target);
		int n = 0;
		long now = owner.level().getGameTime();
		for (Bond b : bondsOf(owner.getUUID())) {
			pick(b, now, true);
			if (b.mob.getTarget() == target) {
				n++;
			}
		}
		return n;
	}

	public static void unlink(ServerPlayer owner) {
		LINKED.remove(owner.getUUID());
	}

	// ---------------------------------------------------------------- the tick

	public static void serverTick(MinecraftServer server) {
		for (Bond b : new ArrayList<>(BONDS.values())) {
			tickBond(b);
		}
		for (PlayerChampion c : new ArrayList<>(CHAMPIONS.values())) {
			tickChampion(server, c);
		}
		for (Offer o : new ArrayList<>(OFFERS.values())) {
			ServerPlayer p = server.getPlayerList().getPlayer(o.player());
			if (p == null) {
				for (ServerLevel l : server.getAllLevels()) {
					if (l.getPlayerByUUID(o.player()) instanceof ServerPlayer mp) {
						p = mp;
					}
				}
			}
			if (p == null) {
				OFFERS.remove(o.player());
			} else if (p.level().getGameTime() > o.until()) {
				refuse(p, true);
			}
		}
		FLYING.values().forEach(s -> s.removeIf(Entity::isRemoved));
		FLYING.values().removeIf(Set::isEmpty);
		LINKED.values().removeIf(t -> !t.isAlive() || t.isRemoved());
		if (server.getTickCount() % 10 == 0) {
			syncMarks(server);
		}
	}

	private static ServerPlayer ownerOf(Bond b) {
		if (b.ownerRef != null && !b.ownerRef.isRemoved()) {
			return b.ownerRef;
		}
		ServerPlayer p = findPlayer(b.mob.getServer(), b.mob.level() instanceof ServerLevel l ? l : null, b.owner);
		b.ownerRef = p;
		return p;
	}

	private static void tickBond(Bond b) {
		Mob mob = b.mob;
		if (!mob.isAlive() || mob.isRemoved()) {
			BONDS.remove(mob.getUUID());
			return;
		}
		ServerPlayer owner = ownerOf(b);
		long now = mob.level().getGameTime();
		if (owner == null || owner.level() != mob.level() || now >= b.until || !Miraculous.transformedAs(owner, Kwami.NOOROO)) {
			release(b, owner != null && owner.level() == mob.level() && now >= b.until);
			return;
		}
		// never its own side: drop a friendly target at once, whoever chose it
		LivingEntity t = mob.getTarget();
		if (t != null && !validTarget(b, owner, t)) {
			forgetTarget(mob);
			t = null;
		}
		if (now >= b.nextPick || t == null && now % 5 == 0) {
			pick(b, now, false);
			t = mob.getTarget();
		}
		if (t == null && mob instanceof PathfinderMob pm && now >= b.nextFollow
				&& mob.distanceToSqr(owner) > HawkMothConfig.AKUMA_LEASH * HawkMothConfig.AKUMA_LEASH) {
			b.nextFollow = now + 20;
			pm.getNavigation().moveTo(owner, 1.15);
		}
		if (b.champion && t != null && now >= b.nextPower && mob.distanceToSqr(t) <= HawkMothConfig.CHAMPION_POWER_RANGE
				* HawkMothConfig.CHAMPION_POWER_RANGE && mob.hasLineOfSight(t)) {
			b.nextPower = now + HawkMothConfig.CHAMPION_POWER_INTERVAL;
			mobPower(b, owner, t);
		}
		if (mob.level() instanceof ServerLevel level) {
			if (b.champion && now % 3 == 0) {
				aura(level, mob, 4);
			} else if (!b.champion && now % 12 == 0) {
				aura(level, mob, 1);
			}
		}
	}

	/** Purple-black wisps round an akumatized creature. */
	private static void aura(ServerLevel level, LivingEntity e, int count) {
		double w = e.getBbWidth() * 0.6;
		level.sendParticles(new DustParticleOptions(new Vector3f(0.30f, 0.04f, 0.42f), 1.2f), e.getX(), e.getY() + e.getBbHeight() * 0.5,
				e.getZ(), count, w, e.getBbHeight() * 0.4, w, 0.01);
		if (count > 2) {
			level.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + e.getBbHeight() * 0.3, e.getZ(), 1, w, e.getBbHeight() * 0.3, w, 0.0);
		}
	}

	/** Could {@code b}'s mob fight {@code t}? Never its owner's side. */
	private static boolean validTarget(Bond b, ServerPlayer owner, LivingEntity t) {
		if (t == null || t == b.mob || !t.isAlive() || t.isRemoved() || t == owner) {
			return false;
		}
		if (t instanceof Player tp && (Squads.areAllies(owner, tp) || tp.isCreative() || tp.isSpectator())) {
			return false;
		}
		if (HeroTargets.isFriendlyPet(owner, t) || isOwnedBy(t, owner.getUUID())) {
			return false;
		}
		if (t.distanceToSqr(owner) > 40.0 * 40.0) {
			return false;
		}
		return HeroTargets.canHarm(owner, t);
	}

	/** Chooses the mob's fight (see the class javadoc). {@code force}: right now, even if it has one. */
	private static void pick(Bond b, long now, boolean force) {
		b.nextPick = now + 10;
		ServerPlayer owner = ownerOf(b);
		if (owner == null) {
			return;
		}
		Mob mob = b.mob;
		LivingEntity choice = null;
		LivingEntity linked = LINKED.get(owner.getUUID());
		if (linked != null && validTarget(b, owner, linked)) {
			choice = linked;
		}
		if (choice == null) {
			LivingEntity hit = owner.getLastHurtMob();
			if (hit != null && owner.tickCount - owner.getLastHurtMobTimestamp() <= HeroTargets.RECENT_ATTACK_TICKS && validTarget(b, owner, hit)) {
				choice = hit;
			}
		}
		if (choice == null) {
			LivingEntity by = owner.getLastHurtByMob();
			if (by != null && validTarget(b, owner, by)) {
				choice = by;
			}
		}
		if (choice == null && mob.getTarget() != null && !force && validTarget(b, owner, mob.getTarget())
				&& HeroTargets.isHostile(owner, mob.getTarget())) {
			return; // keep the fight it is in
		}
		if (choice == null) {
			double best = Double.MAX_VALUE;
			for (LivingEntity e : AbilityHelpers.living(owner.serverLevel(), owner.position(), HawkMothConfig.AKUMA_GUARD_RADIUS,
					e -> e != mob && HeroTargets.isHostile(owner, e) && validTarget(b, owner, e))) {
				double d = e.distanceToSqr(mob);
				if (d < best) {
					best = d;
					choice = e;
				}
			}
		}
		if (choice != mob.getTarget()) {
			if (choice == null) {
				forgetTarget(mob);
			} else {
				mob.setTarget(choice);
				if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
					mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, choice);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the themed powers

	private static void mobPower(Bond b, ServerPlayer owner, LivingEntity t) {
		Mob mob = b.mob;
		ServerLevel level = (ServerLevel) mob.level();
		Vec3 eye = mob.getEyePosition();
		Vec3 at = t.getEyePosition().subtract(0, t.getBbHeight() * 0.2, 0);
		Vec3 dir = at.subtract(eye).normalize();
		float mult = (float) (1.0 + HawkMothConfig.CHAMPION_DAMAGE_BONUS);
		switch (b.power) {
			case FIRE -> {
				SmallFireball ball = new SmallFireball(level, mob, dir.scale(1.0));
				ball.setPos(eye.x + dir.x * 0.8, eye.y + dir.y * 0.8, eye.z + dir.z * 0.8);
				level.addFreshEntity(ball);
				level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1f, 1f);
			}
			case ICE -> {
				AbilityHelpers.line(level, eye, at, ParticleTypes.SNOWFLAKE, 3.0);
				guarded(() -> t.hurt(level.damageSources().source(DamageTypes.FREEZE, mob), HawkMothConfig.ICE_DAMAGE * mult));
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HawkMothConfig.ICE_SLOW_TICKS, 1), mob);
				t.setTicksFrozen(Math.min(t.getTicksRequiredToFreeze() + 40, t.getTicksFrozen() + 60));
				level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8f, 1.6f);
			}
			case STORM -> strike(level, t.position(), mob, owner, HawkMothConfig.STORM_DAMAGE * mult);
		}
	}

	/** A lightning strike (a look only) that hurts what stands round {@code at} -- never Hawk Moth's side. */
	private static void strike(ServerLevel level, Vec3 at, LivingEntity source, ServerPlayer owner, float damage) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		DamageSource ds = level.damageSources().source(DamageTypes.LIGHTNING_BOLT, source);
		for (LivingEntity e : AbilityHelpers.living(level, at.add(0, 0.8, 0), HawkMothConfig.STORM_RADIUS, e -> e != source)) {
			if (owner != null && (e == owner || Squads.areAllies(owner, e) || isOwnedBy(e, owner.getUUID()) || HeroTargets.isFriendlyPet(owner, e))) {
				continue;
			}
			if (source instanceof ServerPlayer sp) {
				AbilityHelpers.hurt(sp, e, ds, damage);
			} else {
				guarded(() -> e.hurt(ds, damage));
			}
		}
	}

	private static void tickChampion(MinecraftServer server, PlayerChampion c) {
		ServerPlayer p = server.getPlayerList().getPlayer(c.player);
		if (p == null) {
			for (ServerLevel l : server.getAllLevels()) {
				if (l.getPlayerByUUID(c.player) instanceof ServerPlayer mp) {
					p = mp;
				}
			}
		}
		if (p == null || !p.isAlive()) {
			endChampion(c, p, null);
			return;
		}
		long now = p.level().getGameTime();
		ServerPlayer owner = findPlayer(server, p.serverLevel(), c.owner);
		if (now >= c.until) {
			endChampion(c, p, "message.projecthero.hawk_moth.champion_over");
			return;
		}
		if (owner == null || !Miraculous.transformedAs(owner, Kwami.NOOROO)) {
			endChampion(c, p, "message.projecthero.hawk_moth.champion_freed");
			return;
		}
		if (now % 3 == 0) {
			aura(p.serverLevel(), p, 3);
		}
		// the themed power on the attack key: a fresh swing of the main hand
		if (p.swinging && p.swingTime == 0 && now >= c.nextPower) {
			c.nextPower = now + HawkMothConfig.CHAMPION_PLAYER_POWER_COOLDOWN;
			playerPower(p, owner, c.power);
		}
	}

	/** A Champion player's power along their look (also called by the tests). */
	public static void playerPower(ServerPlayer p, ServerPlayer owner, Power power) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		switch (power) {
			case FIRE -> {
				SmallFireball ball = new SmallFireball(level, p, look.scale(1.2));
				ball.setPos(eye.x + look.x * 0.9, eye.y + look.y * 0.9 - 0.1, eye.z + look.z * 0.9);
				level.addFreshEntity(ball);
				level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 1f);
			}
			case ICE -> {
				LivingEntity t = AbilityHelpers.raycastEntity(p, HawkMothConfig.CHAMPION_POWER_RANGE);
				Vec3 end = t != null ? t.getEyePosition() : AbilityHelpers.aimPoint(p, HawkMothConfig.CHAMPION_POWER_RANGE);
				AbilityHelpers.line(level, eye.add(look.scale(0.6)), end, ParticleTypes.SNOWFLAKE, 3.0);
				if (t != null && (owner == null || (t != owner && !isOwnedBy(t, owner.getUUID())))) {
					AbilityHelpers.hurt(p, t, level.damageSources().source(DamageTypes.FREEZE, p), HawkMothConfig.ICE_DAMAGE);
					AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, HawkMothConfig.ICE_SLOW_TICKS, 1);
				}
				level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.6f);
			}
			case STORM -> strike(level, AbilityHelpers.aimPoint(p, HawkMothConfig.CHAMPION_POWER_RANGE), p, owner, HawkMothConfig.STORM_DAMAGE);
		}
	}

	private static void guarded(Runnable r) {
		boolean was = REENTRANT.get();
		REENTRANT.set(true);
		try {
			r.run();
		} finally {
			REENTRANT.set(was);
		}
	}

	// ---------------------------------------------------------------- the outlines everyone sees

	private static void syncMarks(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<Integer> ids = new ArrayList<>();
			List<Integer> kinds = new ArrayList<>();
			for (Bond b : BONDS.values()) {
				if (b.mob.level() == level && b.mob.isAlive()) {
					ids.add(b.mob.getId());
					kinds.add(b.champion ? 2 : 1);
				}
			}
			for (PlayerChampion c : CHAMPIONS.values()) {
				if (level.getPlayerByUUID(c.player) instanceof ServerPlayer p) {
					ids.add(p.getId());
					kinds.add(2);
				}
			}
			String key = level.dimension().location().toString();
			List<Integer> last = LAST_SENT.get(key);
			List<Integer> sig = new ArrayList<>(ids);
			sig.addAll(kinds);
			boolean changed = last == null || !last.equals(sig);
			// unchanged lists still go out every 2 s for anyone who just came near; an empty one only once
			if (!changed && (ids.isEmpty() || server.getTickCount() % 40 != 0)) {
				continue;
			}
			LAST_SENT.put(key, sig);
			HawkMothMarksPayload payload = new HawkMothMarksPayload(ids, kinds);
			for (ServerPlayer p : level.players()) {
				if (ServerPlayNetworking.canSend(p, HawkMothMarksPayload.TYPE)) {
					ServerPlayNetworking.send(p, payload);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the damage veto

	/** ALLOW_DAMAGE: akumas never hurt their side; hits that aren't their own melee are scaled. */
	public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f) {
			return true;
		}
		Entity culprit = source.getEntity();
		Bond b = culprit == null ? null : BONDS.get(culprit.getUUID());
		if (b == null) {
			return true;
		}
		ServerPlayer owner = ownerOf(b);
		if (owner != null && (victim == owner || Squads.areAllies(owner, victim) || isOwnedBy(victim, owner.getUUID())
				|| HeroTargets.isFriendlyPet(owner, victim))) {
			return false;
		}
		boolean ownMelee = source.getDirectEntity() == culprit && b.mob.getAttribute(Attributes.ATTACK_DAMAGE) != null;
		if (ownMelee) {
			return true; // the attribute already carries the bonus
		}
		double bonus = b.champion ? HawkMothConfig.CHAMPION_DAMAGE_BONUS : HawkMothConfig.AKUMA_DAMAGE_BONUS;
		float scaled = (float) (amount * (1.0 + bonus));
		if (victim instanceof ServerPlayer player && source.scalesWithDifficulty()) {
			scaled = switch (player.level().getDifficulty()) {
				case HARD -> scaled / 1.5f;
				case EASY -> scaled >= 2f ? 2f * (scaled - 1f) : scaled;
				default -> scaled;
			};
		}
		float fin = scaled;
		guarded(() -> victim.hurt(source, fin));
		return false;
	}

	/** Melee for a mob with no attack of its own (a cow, a villager): walk up and hit (the damage is already +50% / x2). */
	static final class AkumaMeleeGoal extends Goal {
		private final PathfinderMob mob;
		private int cooldown;

		AkumaMeleeGoal(PathfinderMob mob) {
			this.mob = mob;
			setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = mob.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity t = mob.getTarget();
			if (t == null) {
				return;
			}
			mob.getLookControl().setLookAt(t, 30f, 30f);
			if (mob.tickCount % 10 == 0) {
				mob.getNavigation().moveTo(t, 1.25);
			}
			if (cooldown > 0) {
				cooldown--;
			}
			double reach = mob.getBbWidth() * 2.0 * mob.getBbWidth() * 2.0 + t.getBbWidth();
			if (cooldown <= 0 && mob.distanceToSqr(t) <= Math.max(reach, 2.6)) {
				cooldown = 20;
				mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				Bond b = BONDS.get(mob.getUUID());
				double bonus = b != null && b.champion ? HawkMothConfig.CHAMPION_DAMAGE_BONUS : HawkMothConfig.AKUMA_DAMAGE_BONUS;
				if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
					mob.doHurtTarget(t);
				} else {
					float dmg = (float) (HawkMothConfig.AKUMA_BARE_HIT * (1.0 + bonus));
					guarded(() -> t.hurt(mob.damageSources().mobAttack(mob), dmg));
				}
			}
		}
	}

	/** Tests: makes {@code p}'s open offer run out at {@code until} (game time). */
	public static void setOfferUntil(Player p, long until) {
		Offer o = OFFERS.get(p.getUUID());
		if (o != null) {
			OFFERS.put(p.getUUID(), new Offer(o.owner(), o.player(), until, o.power()));
		}
	}

	/** For the tests and the guide: every live bond (read only). */
	public static Set<UUID> bondedIds() {
		return new HashSet<>(BONDS.keySet());
	}
}
