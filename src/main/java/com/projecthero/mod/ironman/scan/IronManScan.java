package com.projecthero.mod.ironman.scan;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;

/**
 * v0.16.7 (user): the server half of the Iron Man target scanner. The wearer's client asks about the living thing it is
 * scanning ({@link Request}, at most a few times a second); the server answers only that wearer ({@link Result}) with
 * what the client cannot know: what one of its hits does to you (through your armour and the suit's integrity
 * absorption), how many of your hits it takes, whether it is armed, and -- for a player -- which power they hold and that
 * power's moves. Nothing is put on the entity: no glow flag, no team, nothing other players can see.
 */
public final class IronManScan {
	/** Result flag bits. */
	public static final int BOSS = 1;
	public static final int EXPLODES = 2;
	public static final int RANGED = 4;
	public static final int HOSTILE = 8;
	public static final int HAS_POWER = 16;

	/** How far the scanner answers (the client's own range is 100; a little slack for movement). */
	public static final double MAX_RANGE = 110.0;

	private IronManScan() {
	}

	/** Client -&gt; server: "tell me about entity {@code entityId}". */
	public record Request(int entityId) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_scan_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Request::entityId, Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -&gt; the wearer: the assessment of {@code entityId}. {@code toYou} = health one of its hits takes off you,
	 * {@code hull} = integrity it takes off your suit, {@code yourHits} = your hits to bring it down (0 = you cannot hurt
	 * it), {@code weapon} = the description id of a held weapon ("" for none), {@code identity} = a player's power name
	 * key ("" for none), {@code moves} = that power's move name keys.
	 */
	public record Result(int entityId, int level, int flags, float toYou, float hull, int yourHits, String weapon, String identity,
			List<String> moves) implements CustomPacketPayload {
		public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_scan_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of((buf, r) -> {
			ByteBufCodecs.VAR_INT.encode(buf, r.entityId);
			ByteBufCodecs.VAR_INT.encode(buf, r.level);
			ByteBufCodecs.VAR_INT.encode(buf, r.flags);
			ByteBufCodecs.FLOAT.encode(buf, r.toYou);
			ByteBufCodecs.FLOAT.encode(buf, r.hull);
			ByteBufCodecs.VAR_INT.encode(buf, r.yourHits);
			ByteBufCodecs.STRING_UTF8.encode(buf, r.weapon);
			ByteBufCodecs.STRING_UTF8.encode(buf, r.identity);
			ByteBufCodecs.VAR_INT.encode(buf, r.moves.size());
			for (String m : r.moves) {
				ByteBufCodecs.STRING_UTF8.encode(buf, m);
			}
		}, buf -> {
			int id = ByteBufCodecs.VAR_INT.decode(buf);
			int level = ByteBufCodecs.VAR_INT.decode(buf);
			int flags = ByteBufCodecs.VAR_INT.decode(buf);
			float toYou = ByteBufCodecs.FLOAT.decode(buf);
			float hull = ByteBufCodecs.FLOAT.decode(buf);
			int hits = ByteBufCodecs.VAR_INT.decode(buf);
			String weapon = ByteBufCodecs.STRING_UTF8.decode(buf);
			String identity = ByteBufCodecs.STRING_UTF8.decode(buf);
			int n = Math.min(8, ByteBufCodecs.VAR_INT.decode(buf));
			List<String> moves = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				moves.add(ByteBufCodecs.STRING_UTF8.decode(buf));
			}
			return new Result(id, level, flags, toYou, hull, hits, weapon, identity, moves);
		});

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.playS2C().register(Result.TYPE, Result.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			Entity e = player.serverLevel().getEntity(payload.entityId());
			if (!(e instanceof LivingEntity target) || !target.isAlive() || target == player
					|| target.distanceToSqr(player) > MAX_RANGE * MAX_RANGE) {
				return;
			}
			IronManSuit suit = IronManHighlight.helmetSuit(player);
			TonyStarkState state = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
			if (suit == null || state == null || !state.hasPower) {
				return;
			}
			ServerPlayNetworking.send(player, assess(player, suit, target));
		});
	}

	/** The assessment of {@code target} for {@code player} wearing {@code suit}. Public for the gametests. */
	public static Result assess(ServerPlayer player, IronManSuit suit, LivingEntity target) {
		int flags = 0;
		float maxHp = target.getMaxHealth();
		if (maxHp >= IronManThreat.BOSS_HEALTH) {
			flags |= BOSS;
		}
		boolean hostile = target instanceof Enemy || target instanceof Player
				|| (target instanceof Mob m && m.getTarget() == player);
		if (hostile) {
			flags |= HOSTILE;
		}

		// ---- one of its hits, as it lands on you
		ItemStack held = target.getMainHandItem();
		String weapon = "";
		float attack = target.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
				? (float) target.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0f;
		if (held.getItem() instanceof ProjectileWeaponItem || held.getItem() instanceof TridentItem) {
			flags |= RANGED;
			attack = Math.max(attack, held.getItem() instanceof TridentItem ? 8f : 4f); // an arrow at full draw ~ 4
		}
		if ((flags & RANGED) != 0 || held.getItem() instanceof net.minecraft.world.item.SwordItem
				|| held.getItem() instanceof net.minecraft.world.item.AxeItem || held.getItem() instanceof net.minecraft.world.item.MaceItem) {
			weapon = held.getDescriptionId();
		}
		if (target instanceof Creeper creeper) {
			flags |= EXPLODES;
			attack = creeper.isPowered() ? 40f : 20f; // a point-blank blast before armour
		}
		if (!(target instanceof Player)) {
			attack = scaleForDifficulty(attack, player.level().getDifficulty());
		}
		DamageSource source = target instanceof Player p ? player.damageSources().playerAttack(p) : player.damageSources().mobAttack(target);
		float afterArmour = attack <= 0f ? 0f : CombatRules.getDamageAfterAbsorb(player, attack, source, player.getArmorValue(),
				(float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		float integrity = IronManEnergy.integrity(player, suit.id());
		float hull = integrity <= 0f ? 0f : Math.min(integrity, afterArmour * Math.max(0f, Math.min(1f, suit.absorbShare())));
		float toYou = Math.max(0f, afterArmour - hull);

		// ---- your hits on it (your melee, or the suit's repulsor when that hits harder)
		float yours = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		yours = Math.max(yours, suit.repulsorDamage());
		float yoursLanded = target.isInvulnerable() ? 0f : CombatRules.getDamageAfterAbsorb(target, yours,
				player.damageSources().playerAttack(player), target.getArmorValue(),
				target.getAttributes().hasAttribute(Attributes.ARMOR_TOUGHNESS) ? (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0f);
		int yourHits = IronManThreat.hitsToDown(target.getHealth() + target.getAbsorptionAmount(), yoursLanded);

		// ---- a player's power and its moves
		String identity = "";
		List<String> moves = new ArrayList<>();
		if (target instanceof ServerPlayer other) {
			identity = com.projecthero.mod.squad.HeroIdentity.describe(other);
			// a Quirk / Miraculous reads better as "Hellflame (Endeavor)" than as the bare hero name
			com.projecthero.mod.quirk.Quirk quirk = com.projecthero.mod.quirk.Quirks.quirk(other);
			com.projecthero.mod.miraculous.Kwami kwami = com.projecthero.mod.miraculous.Miraculous.kwami(other);
			if (quirk != null && identity.equals("projecthero.quirk." + quirk.id + ".hero")) {
				identity = "projecthero.hero_tier.quirk_" + quirk.id;
			} else if (kwami != null && identity.equals("projecthero.squad.identity." + kwami.heroKey)) {
				identity = "projecthero.hero_tier." + kwami.heroKey;
			}
			moves = moves(other);
			if (!identity.isEmpty()) {
				flags |= HAS_POWER;
			}
		}
		IronManThreat.Level level = IronManThreat.level(toYou, player.getHealth(), yourHits, (flags & BOSS) != 0,
				(flags & HAS_POWER) != 0, hostile);
		return new Result(target.getId(), level.ordinal(), flags, toYou, hull, yourHits, weapon, identity, moves);
	}

	/** Easy halves a mob's hit (+1), Hard adds half again, Peaceful stops it -- vanilla's own scaling. */
	public static float scaleForDifficulty(float damage, Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> 0f;
			case EASY -> Math.min(damage / 2f + 1f, damage);
			case HARD -> damage * 1.5f;
			default -> damage;
		};
	}

	/** Up to six move name keys of the power a player holds (Quirk, Miraculous, or a mutation); empty for the rest. */
	private static List<String> moves(ServerPlayer other) {
		List<String> out = new ArrayList<>();
		AbilitySlot[] six = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 };
		com.projecthero.mod.quirk.Quirk quirk = com.projecthero.mod.quirk.Quirks.quirk(other);
		com.projecthero.mod.miraculous.Kwami kwami = com.projecthero.mod.miraculous.Miraculous.kwami(other);
		if (quirk != null) {
			var kit = com.projecthero.mod.quirk.QuirkKits.of(quirk);
			for (AbilitySlot s : six) {
				String[] ids = kit.idsOf(s);
				if (ids.length > 0) {
					out.add("projecthero.quirk.ability." + ids[0]);
				}
			}
		} else if (kwami != null) {
			var kit = com.projecthero.mod.miraculous.MiraculousKits.of(kwami);
			for (AbilitySlot s : six) {
				String[] ids = kit.idsOf(s);
				if (ids.length > 0) {
					out.add("projecthero.miraculous.ability." + ids[0]);
				}
			}
		} else {
			com.projecthero.mod.hero.Power active = com.projecthero.mod.hero.ExperimentalPowers.getActive(other);
			if (active != null && com.projecthero.mod.hero.ExperimentalPowers.owns(other, active)) {
				for (var a : active.abilities()) {
					if (out.size() < 6) {
						out.add(a.nameKey());
					}
				}
			}
		}
		return out;
	}
}
