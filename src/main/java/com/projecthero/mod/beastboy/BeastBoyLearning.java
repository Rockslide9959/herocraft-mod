package com.projecthero.mod.beastboy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.beastboy.data.BeastBoyState;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.3 Beast Boy: getting it -- the <b>Sakutia</b> infection.
 * <ul>
 *   <li>A rare <b>green ocelot</b>: one jungle ocelot in {@value BeastBoyConfig#GREEN_OCELOT_ONE_IN} is tagged Sakutia when it
 *   first loads ({@code SAKUTIA_OCELOT}, synced: the client draws it green). Feed it (cod or salmon), then crouch next to it:
 *   it bites you once and you get 60 s of <b>Sakutia fever</b> (slow, weak, dizzy at first). When the fever breaks you wake up
 *   as Beast Boy.</li>
 *   <li>The <b>Sakutia Serum</b> in jungle temple chests (1 in {@value BeastBoyConfig#SERUM_ONE_IN}): drink it (right-click)
 *   to become Beast Boy at once.</li>
 * </ul>
 * Random serums never give it ({@code PowerGrants.NOT_FROM_SERUMS}). Dying during the fever ends it.
 */
public final class BeastBoyLearning {
	public static final String CHECKED_TAG = "projecthero_sakutia_checked";

	private record Fed(UUID player, long at) {
	}

	/** green ocelot -> who fed it last and when. Ids only; cleared by ServerStateReset. */
	private static final Map<UUID, Fed> FED = new ConcurrentHashMap<>();

	private BeastBoyLearning() {
	}

	static void clearSessionState() {
		FED.clear();
	}

	public static boolean isGreenOcelot(Entity e) {
		return e instanceof Ocelot && Boolean.TRUE.equals(e.getAttached(ModAttachments.SAKUTIA_OCELOT));
	}

	public static void setGreen(Ocelot o, boolean green) {
		if (green) {
			o.setAttached(ModAttachments.SAKUTIA_OCELOT, true);
		} else {
			o.removeAttached(ModAttachments.SAKUTIA_OCELOT);
		}
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(BeastBoyItems.SAKUTIA_SERUM)) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				return BeastBoy.hasPower(player) ? InteractionResultHolder.fail(stack) : InteractionResultHolder.success(stack);
			}
			return drinkSerum((ServerPlayer) player, hand) ? InteractionResultHolder.consume(player.getItemInHand(hand))
					: InteractionResultHolder.fail(stack);
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && isGreenOcelot(entity) && player.getItemInHand(hand).is(net.minecraft.tags.ItemTags.OCELOT_FOOD)) {
				feed((ServerPlayer) player, (Ocelot) entity);
			}
			return InteractionResult.PASS; // vanilla still feeds it (and may trust him)
		});
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin() || !key.equals(BuiltInLootTables.JUNGLE_TEMPLE)) {
				return;
			}
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(BeastBoyItems.SAKUTIA_SERUM).setWeight(1))
					.add(EmptyLootItem.emptyItem().setWeight(BeastBoyConfig.SERUM_ONE_IN - 1)));
		});
	}

	/** {@code ENTITY_LOAD}: a jungle ocelot loading for the first time may be the green one. */
	public static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Ocelot o) || o.getTags().contains(CHECKED_TAG)) {
			return;
		}
		o.addTag(CHECKED_TAG);
		if (level.getBiome(o.blockPosition()).is(BiomeTags.IS_JUNGLE) && level.random.nextInt(BeastBoyConfig.GREEN_OCELOT_ONE_IN) == 0) {
			setGreen(o, true);
		}
	}

	/** {@code /projecthero power tune beast_boy ocelot}: a green ocelot next to him. */
	public static Ocelot spawnGreen(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Ocelot o = EntityType.OCELOT.create(level);
		if (o == null) {
			return null;
		}
		o.addTag(CHECKED_TAG);
		o.moveTo(player.getX() + player.getLookAngle().x * 2, player.getY(), player.getZ() + player.getLookAngle().z * 2, player.getYRot() + 180f, 0f);
		o.finalizeSpawn(level, level.getCurrentDifficultyAt(o.blockPosition()), MobSpawnType.COMMAND, null);
		setGreen(o, true);
		level.addFreshEntity(o);
		return o;
	}

	/** He fed a green ocelot: it remembers him for two minutes. */
	public static void feed(ServerPlayer player, Ocelot o) {
		FED.put(o.getUUID(), new Fed(player.getUUID(), player.level().getGameTime()));
		player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.fed").withStyle(ChatFormatting.GREEN), true);
	}

	/** Was {@code o} fed by {@code player} recently? */
	public static boolean fedBy(Ocelot o, ServerPlayer player) {
		Fed f = FED.get(o.getUUID());
		return f != null && f.player.equals(player.getUUID()) && player.level().getGameTime() - f.at <= BeastBoyConfig.FED_REMEMBER_TICKS;
	}

	/** Every tick for a player without the power (from {@code BeastBoy.tick}): the fever, and the bite when he crouches. */
	static void tickHuman(ServerPlayer player, BeastBoyState s) {
		long now = player.level().getGameTime();
		if (s != null && s.timers.feverUntil() > 0L) {
			if (now >= s.timers.feverUntil()) {
				BeastBoyState n = s.copy();
				n.timers = n.timers.withFever(0L);
				player.setAttached(ModAttachments.BEAST_BOY_STATE, n);
				BeastBoy.grant(player);
			} else if ((s.timers.feverUntil() - now) % 400 == 0) {
				player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.fever_tick").withStyle(ChatFormatting.DARK_GREEN), true);
			}
			if (now % 10 == 0) {
				((ServerLevel) player.level()).sendParticles(BeastBoy.dust(BeastBoy.GREEN, 0.8f), player.getX(), player.getY() + 1.0, player.getZ(), 3,
						0.3, 0.5, 0.3, 0);
			}
			return;
		}
		if (!player.isShiftKeyDown() || player.tickCount % 5 != 0 || !player.isAlive() || player.isSpectator()) {
			return;
		}
		for (Ocelot o : player.level().getEntitiesOfClass(Ocelot.class, player.getBoundingBox().inflate(BeastBoyConfig.BITE_RANGE),
				BeastBoyLearning::isGreenOcelot)) {
			if (fedBy(o, player)) {
				bite(player, o);
				return;
			}
		}
	}

	/** The green ocelot bites him once: the Sakutia fever starts. False if he already has it or is already fevered. */
	public static boolean bite(ServerPlayer player, Ocelot o) {
		BeastBoyState s = player.getAttachedOrCreate(ModAttachments.BEAST_BOY_STATE);
		if (s.hasPower || s.timers.feverUntil() > 0L) {
			return false;
		}
		FED.remove(o.getUUID());
		o.swing(InteractionHand.MAIN_HAND);
		o.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
		player.hurt(player.damageSources().mobAttack(o), 1f);
		player.level().playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 0.6f, 1.2f);
		startFever(player);
		return true;
	}

	public static void startFever(ServerPlayer player) {
		BeastBoyState n = player.getAttachedOrCreate(ModAttachments.BEAST_BOY_STATE).copy();
		n.timers = n.timers.withFever(player.level().getGameTime() + BeastBoyConfig.FEVER_TICKS);
		player.setAttached(ModAttachments.BEAST_BOY_STATE, n);
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BeastBoyConfig.FEVER_TICKS, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BeastBoyConfig.FEVER_TICKS, 0, false, false, true));
		player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.bitten").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.fever").withStyle(ChatFormatting.GRAY), false);
	}

	/** The Sakutia Serum: Beast Boy at once. False if he already is (or unique powers refuse). */
	public static boolean drinkSerum(ServerPlayer player, InteractionHand hand) {
		if (BeastBoy.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.already").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!BeastBoy.grant(player)) {
			return false;
		}
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		player.removeEffect(MobEffects.WEAKNESS);
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.6f, 1.0f);
		return true;
	}
}
