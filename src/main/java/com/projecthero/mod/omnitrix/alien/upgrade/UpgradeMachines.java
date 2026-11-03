package com.projecthero.mod.omnitrix.alien.upgrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit.MergeKind;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "When the player takes over a machine they should become the machine, they should be able to move around and walk
 * around ... give all the things that upgrade can upgrade upgraded models with the black and white colour scheme with the green
 * wiring pattern on the black parts please, if the player detransforms while merged the block places wherever the player is, same
 * for if the player unmerges, merging should never destroy the tech").
 *
 * <p>Upgrade's C on a tech BLOCK (furnace / blast furnace / smoker, dispenser / dropper, piston / sticky piston, note block,
 * jukebox, observer, hopper) or a minecart / boat lifts it INTO him: its exact block state and block-entity NBT (inventory,
 * smelting progress, the disc...) -- or the vehicle's whole entity NBT (a chest minecart's contents...) -- are kept here and the
 * thing leaves the world WITHOUT dropping anything. He walks around as its upgraded form (the client's {@code UpgradeMachineBody})
 * with its job boosted on R. Unmerging (C), turning back, the merge running out, a shock, death or logging out puts it back
 * exactly where he stands (the nearest free spot if that is taken; he is lifted on top of it if it lands at his feet). Golems
 * are still piloted in the world (they walk where he walks) and Ally Upgrade is unchanged.
 */
public final class UpgradeMachines {
	/** R as a machine: one shot / blast per this many ticks. */
	public static final int DISPENSER_CD = 12, FURNACE_CD = 30, PISTON_CD = 12, NOTE_CD = 40, JUKEBOX_CD = 60, OBSERVER_CD = 100, HOPPER_CD = 40,
			RAM_CD = 40;
	public static final float VOLLEY_DAMAGE = 3f, BLAST_DAMAGE = 10f, SONIC_DAMAGE = 12f, BASS_DAMAGE = 10f, RAM_DAMAGE = 10f;
	public static final double SONIC_RANGE = 14, BASS_RADIUS = 5, SCAN_RADIUS = 32, VACUUM_RADIUS = 16, MAGNET_RADIUS = 5;
	private static final ResourceLocation STEP_ID = PowerToggles.id("omnitrix_upgrade_machine_step");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("omnitrix_upgrade_machine_speed");

	/** What he carries: a block's state + its block entity's NBT, or a vehicle's entity NBT. */
	public record Stored(@Nullable BlockState state, @Nullable CompoundTag data, boolean entity) {
	}

	private static final Map<UUID, Stored> STORED = new ConcurrentHashMap<>();

	private UpgradeMachines() {
	}

	public static void clearSessionState() {
		STORED.clear();
	}

	@Nullable
	public static Stored stored(ServerPlayer p) {
		return STORED.get(p.getUUID());
	}

	/** Every player still carrying something (a server stopping puts it all back first). */
	public static List<UUID> carriers() {
		return new ArrayList<>(STORED.keySet());
	}

	/** The synced variant: a block's state id + 1, a vehicle's entity type id + 1. */
	public static long variant(Stored s, @Nullable Entity e) {
		if (s.state() != null) {
			return Block.getId(s.state()) + 1L;
		}
		return e == null ? 0L : BuiltInRegistries.ENTITY_TYPE.getId(e.getType()) + 1L;
	}

	// ---------------------------------------------------------------- taking it in

	/**
	 * May he take the block at {@code pos} (the same rights as breaking it: build rights, spawn protection, adventure mode, other
	 * mods' protection via Fabric's break event; never an unbreakable block)?
	 */
	public static boolean mayTake(ServerPlayer p, ServerLevel l, BlockPos pos) {
		BlockState s = l.getBlockState(pos);
		if (s.isAir() || s.getDestroySpeed(l, pos) < 0f || p.isSpectator() || !l.mayInteract(p, pos) || !p.mayBuild()
				|| p.blockActionRestricted(l, pos, p.gameMode.getGameModeForPlayer())) {
			return false;
		}
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(l, p, pos, s, l.getBlockEntity(pos));
	}

	/** Lifts the block into him (state + NBT), leaving air and dropping nothing. */
	public static Stored takeBlock(ServerPlayer p, ServerLevel l, BlockPos pos) {
		BlockState s = l.getBlockState(pos);
		BlockEntity be = l.getBlockEntity(pos);
		CompoundTag data = be == null ? null : be.saveWithoutMetadata(l.registryAccess());
		if (s.getBlock() instanceof PistonBaseBlock && s.getValue(PistonBaseBlock.EXTENDED)) {
			// an extended piston comes in retracted; its head goes with it
			BlockPos head = pos.relative(s.getValue(PistonBaseBlock.FACING));
			if (l.getBlockState(head).is(Blocks.PISTON_HEAD)) {
				l.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			s = s.setValue(PistonBaseBlock.EXTENDED, false);
		}
		if (s.hasProperty(BlockStateProperties.POWERED)) {
			s = s.setValue(BlockStateProperties.POWERED, false);
		}
		// the block entity goes first, so the block's onRemove finds nothing to spill (no items, no furnace XP, no disc)
		if (be != null) {
			l.removeBlockEntity(pos);
		}
		l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		Stored st = new Stored(s, data, false);
		STORED.put(p.getUUID(), st);
		return st;
	}

	/** Takes a minecart / boat in (its whole NBT, contents too), removing it without a drop. */
	public static Stored takeEntity(ServerPlayer p, Entity e) {
		CompoundTag data = new CompoundTag();
		e.saveAsPassenger(data);
		if (e instanceof ContainerEntity ce) {
			ce.clearChestVehicleContent();
		} else if (e instanceof Container c) {
			c.clearContent();
		}
		e.discard();
		Stored st = new Stored(null, data, true);
		STORED.put(p.getUUID(), st);
		return st;
	}

	// ---------------------------------------------------------------- putting it back

	/** Puts what he carries back where he stands (never deletes it). True if there was something. */
	public static boolean release(ServerPlayer p, ServerLevel l) {
		Stored s = STORED.remove(p.getUUID());
		if (s == null) {
			return false;
		}
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
		if (s.entity()) {
			respawn(p, l, s);
		} else {
			place(p, l, s);
		}
		return true;
	}

	private static void respawn(ServerPlayer p, ServerLevel l, Stored s) {
		Vec3 at = p.position();
		float yaw = p.getYRot();
		Entity e = EntityType.loadEntityRecursive(s.data(), l, ent -> {
			ent.moveTo(at.x, at.y + 0.1, at.z, yaw, 0f);
			ent.setDeltaMovement(Vec3.ZERO);
			return ent;
		});
		if (e == null || !l.addFreshEntity(e)) {
			// a clash (its id is somehow still in the world): a fresh id, same everything else
			CompoundTag copy = s.data().copy();
			copy.remove("UUID");
			Entity again = EntityType.loadEntityRecursive(copy, l, ent -> {
				ent.moveTo(at.x, at.y + 0.1, at.z, yaw, 0f);
				return ent;
			});
			if (again != null) {
				l.addFreshEntity(again);
			}
		}
	}

	/** Where the block goes: his feet (he is lifted on top), else the nearest free spot that doesn't take his space. */
	@Nullable
	public static BlockPos spotFor(ServerPlayer p, ServerLevel l) {
		BlockPos feet = BlockPos.containing(p.getX(), p.getY() + 0.01, p.getZ());
		boolean present = p.isAlive() && !p.isRemoved();
		if (free(l, feet) && (!present || (free(l, feet.above()) && free(l, feet.above(2))))) {
			return feet;
		}
		AABB body = p.getBoundingBox();
		List<BlockPos> around = new ArrayList<>();
		for (BlockPos c : BlockPos.betweenClosed(feet.offset(-3, -2, -3), feet.offset(3, 3, 3))) {
			around.add(c.immutable());
		}
		around.sort(Comparator.comparingDouble(c -> c.distSqr(feet) + (c.getY() < feet.getY() ? 0.5 : 0)));
		for (BlockPos c : around) {
			if (free(l, c) && (!present || !new AABB(c).intersects(body))) {
				return c;
			}
		}
		for (int up = 1; up < 48; up++) {
			BlockPos c = feet.above(up);
			if (!l.isInWorldBounds(c)) {
				break;
			}
			if (free(l, c) && (!present || !new AABB(c).intersects(body))) {
				return c;
			}
		}
		return null;
	}

	private static boolean free(ServerLevel l, BlockPos c) {
		return l.isInWorldBounds(c) && l.getBlockState(c).canBeReplaced() && l.getBlockEntity(c) == null;
	}

	private static void place(ServerPlayer p, ServerLevel l, Stored s) {
		BlockPos at = spotFor(p, l);
		if (at == null) {
			// nowhere at all (walled in at the build limit): it pops out as items, contents included -- still never lost
			Block.popResource(l, p.blockPosition(), new ItemStack(s.state().getBlock()));
			if (s.data() != null && s.data().contains("Items")) {
				NonNullList<ItemStack> items = NonNullList.withSize(64, ItemStack.EMPTY);
				ContainerHelper.loadAllItems(s.data(), items, l.registryAccess());
				for (ItemStack it : items) {
					if (!it.isEmpty()) {
						Containers.dropItemStack(l, p.getX(), p.getY(), p.getZ(), it);
					}
				}
			}
			return;
		}
		boolean lift = at.equals(BlockPos.containing(p.getX(), p.getY() + 0.01, p.getZ())) && p.isAlive() && !p.isRemoved();
		l.setBlock(at, s.state(), Block.UPDATE_ALL);
		if (s.data() != null) {
			BlockEntity be = l.getBlockEntity(at);
			if (be != null) {
				be.loadWithComponents(s.data(), l.registryAccess());
				be.setChanged();
				l.sendBlockUpdated(at, s.state(), s.state(), Block.UPDATE_ALL);
			}
		}
		if (lift) {
			p.teleportTo(p.getX(), at.getY() + 1.0, p.getZ());
			p.resetFallDistance();
		}
		Vec3 c = Vec3.atCenterOf(at);
		l.sendParticles(UpgradeKit.SPARK, c.x, c.y, c.z, 20, 0.45, 0.45, 0.45, 0.0);
	}

	// ---------------------------------------------------------------- as the machine: passives

	/** Every tick he is a machine: the form's passives. */
	public static void tick(ServerPlayer p, ServerLevel l, MergeKind kind, long now) {
		switch (kind) {
			case FURNACE -> {
				if (now % 10 == 0) {
					p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false, false));
					p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false, false));
				}
			}
			case PISTON -> {
				if (now % 10 == 0) {
					p.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 1, false, false, false));
				}
			}
			case OBSERVER -> {
				if (now % 10 == 0) {
					p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));
				}
			}
			case JUKEBOX -> {
				// its music: him and his allies nearby move and work faster
				if (now % 20 == 0) {
					for (ServerPlayer o : l.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8),
							o -> o == p || com.projecthero.mod.squad.Squads.areAllies(p, o))) {
						o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, true));
						o.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, true));
					}
					l.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 1.4, p.getZ(), 1, 0.3, 0.2, 0.3, 0.6);
				}
			}
			case HOPPER -> {
				// a magnet: loose items and XP close by drift into him
				for (Entity e : l.getEntities(p, p.getBoundingBox().inflate(MAGNET_RADIUS), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
					Vec3 to = p.position().add(0, 0.5, 0).subtract(e.position());
					e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(0.25)));
				}
			}
			case MINECART, BOAT -> {
				// a hover-cart / hover-boat: quicker, steps up a block, skims water
				PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, kind == MergeKind.MINECART ? 0.6 : 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				PowerToggles.modifier(p, Attributes.STEP_HEIGHT, STEP_ID, 0.5, AttributeModifier.Operation.ADD_VALUE);
				if (p.isInWater()) {
					Vec3 v = p.getDeltaMovement();
					p.setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
					p.hurtMarked = true;
				}
				p.resetFallDistance();
			}
			default -> {
			}
		}
		if (now % 4 == 0) {
			l.sendParticles(UpgradeKit.SPARK, p.getX(), p.getY() + 0.6, p.getZ(), 1, 0.4, 0.4, 0.4, 0.0);
		}
	}

	// ---------------------------------------------------------------- as the machine: R

	/** R while he is a machine: its job, upgraded. True if it fired. */
	public static boolean fire(UpgradeKit kit, ServerPlayer p, ServerLevel l, MergeKind kind, @Nullable Stored s) {
		long now = l.getGameTime();
		if (kit.timer(p, "machine_cd") > now) {
			return false;
		}
		int cd = switch (kind) {
			case DISPENSER -> volley(p, l, s);
			case FURNACE -> furnace(kit, p, l, s);
			case PISTON -> punch(kit, p, l, s);
			case NOTE_BLOCK -> sonic(kit, p, l);
			case JUKEBOX -> bass(kit, p, l);
			case OBSERVER -> scan(p, l);
			case HOPPER -> vacuum(p, l);
			case MINECART, BOAT -> ram(kit, p, l);
			default -> 0;
		};
		if (cd <= 0) {
			return false;
		}
		kit.setTimer(p, "machine_cd", now + cd);
		kit.anim(p, UpgradeKit.ANIM_MACHINE);
		return true;
	}

	/** Dispenser turret: three arrows at once down the look (a dropper flings them the same). */
	private static int volley(ServerPlayer p, ServerLevel l, @Nullable Stored s) {
		for (int i = -1; i <= 1; i++) {
			Arrow a = new Arrow(l, p, new ItemStack(Items.ARROW), null);
			a.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			a.setPos(p.getX(), p.getEyeY() - 0.3, p.getZ());
			a.shootFromRotation(p, p.getXRot(), p.getYRot() + i * 3f, 0f, 3.2f, 0.5f);
			a.setBaseDamage(VOLLEY_DAMAGE);
			l.addFreshEntity(a);
		}
		l.playSound(null, p.blockPosition(), SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1f, 1.4f);
		return DISPENSER_CD;
	}

	/** Furnace walker: whatever he holds that it can cook is done at once; with nothing to cook, a blast of fire. */
	private static int furnace(UpgradeKit kit, ServerPlayer p, ServerLevel l, @Nullable Stored s) {
		ItemStack held = p.getMainHandItem();
		if (!held.isEmpty()) {
			RecipeType<? extends AbstractCookingRecipe> type = s != null && s.state() != null && s.state().is(Blocks.BLAST_FURNACE) ? RecipeType.BLASTING
					: s != null && s.state() != null && s.state().is(Blocks.SMOKER) ? RecipeType.SMOKING : RecipeType.SMELTING;
			SingleRecipeInput in = new SingleRecipeInput(held);
			Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> r = cook(l, type, in);
			if (r.isPresent()) {
				ItemStack out = r.get().value().assemble(in, l.registryAccess());
				out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * held.getCount()));
				float xp = r.get().value().getExperience() * held.getCount();
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, out);
				ExperienceOrb.award(l, p.position(), Math.round(xp));
				l.playSound(null, p.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.2f, 1.2f);
				l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
				return 10;
			}
		}
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(look.scale(7)).inflate(1.5),
				e -> e != p && e.isAlive() && cone(eye, look, e, 7, 0.8))) {
			e.invulnerableTime = 0;
			UpgradeKit.hurt(p, e, BLAST_DAMAGE, true);
			e.igniteForSeconds(5f);
		}
		QuirkFx.cone(l, eye.add(look.scale(0.6)), look).colours(0xFFF0C0, UpgradeKit.COLOUR).size(1.4f).length(7f).life(10).spawn();
		for (int i = 0; i < 24; i++) {
			Vec3 at = eye.add(look.scale(1 + i * 0.25));
			l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.15 + i * 0.03, 0.15 + i * 0.03, 0.15 + i * 0.03, 0.02);
		}
		l.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
		return FURNACE_CD;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> cook(ServerLevel l, RecipeType type, SingleRecipeInput in) {
		return l.getRecipeManager().getRecipeFor(type, in, l);
	}

	private static boolean cone(Vec3 eye, Vec3 look, Entity e, double range, double dot) {
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
		double d = to.length();
		return d <= range && (d < 1.0 || to.normalize().dot(look) >= dot);
	}

	/** Piston: a pile-driver punch down the look -- everything in front, up to 6 blocks, hurled away. */
	private static int punch(UpgradeKit kit, ServerPlayer p, ServerLevel l, @Nullable Stored s) {
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		boolean sticky = s != null && s.state() != null && s.state().is(Blocks.STICKY_PISTON);
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(look.scale(6)).inflate(1.0),
				e -> e != p && e.isAlive() && cone(eye, look, e, 6, 0.85))) {
			e.invulnerableTime = 0;
			UpgradeKit.hurt(p, e, UpgradeKit.PISTON_DAMAGE, false);
			double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			// a sticky piston yanks instead
			double k = (sticky ? -1.4 : 2.2) * res;
			e.push(look.x * k, 0.35, look.z * k);
			e.hurtMarked = true;
		}
		QuirkFx.cone(l, eye.add(look.scale(0.8)), look).colours(0xFFFFFF, UpgradeKit.COLOUR).size(0.9f).length(5f).life(6).style(QuirkFx.CONE_DEBRIS).spawn();
		l.playSound(null, p.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1f, 1.3f);
		return PISTON_CD;
	}

	/** Note block: a sonic blast down the look, through everything in a line. */
	private static int sonic(UpgradeKit kit, ServerPlayer p, ServerLevel l) {
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(look.scale(SONIC_RANGE)).inflate(1.0),
				e -> e != p && e.isAlive() && cone(eye, look, e, SONIC_RANGE, 0.93))) {
			e.invulnerableTime = 0;
			UpgradeKit.hurt(p, e, SONIC_DAMAGE, false);
			e.push(look.x * 0.8, 0.2, look.z * 0.8);
			e.hurtMarked = true;
		}
		for (int i = 1; i <= SONIC_RANGE; i += 2) {
			Vec3 at = eye.add(look.scale(i));
			l.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			l.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 1.0);
		}
		l.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
		l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6f, 1.6f);
		return NOTE_CD;
	}

	/** Jukebox: a bass drop -- everything around him knocked back. */
	private static int bass(UpgradeKit kit, ServerPlayer p, ServerLevel l) {
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(BASS_RADIUS),
				e -> e != p && e.isAlive() && e.distanceTo(p) <= BASS_RADIUS && !com.projecthero.mod.squad.Squads.areAllies(p, e))) {
			e.invulnerableTime = 0;
			UpgradeKit.hurt(p, e, BASS_DAMAGE, false);
			Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			e.push(away.x * 1.4, 0.5, away.z * 1.4);
			e.hurtMarked = true;
		}
		QuirkFx.ring(l, p.position().add(0, 0.2, 0)).style(QuirkFx.RING_SOFT).colours(UpgradeKit.GLOW, UpgradeKit.COLOUR).size((float) BASS_RADIUS * 2f)
				.thickness(0.2f).life(10).spawn();
		l.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 1, p.getZ(), 12, 2.0, 0.6, 2.0, 1.0);
		l.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 2f, 0.5f);
		return JUKEBOX_CD;
	}

	/** Observer: a scan -- everything living within 32 glows for 10 s. */
	private static int scan(ServerPlayer p, ServerLevel l) {
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SCAN_RADIUS), e -> e != p && e.isAlive())) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
		}
		QuirkFx.ring(l, p.position().add(0, 1, 0)).style(QuirkFx.RING_SOFT).colours(UpgradeKit.GLOW, UpgradeKit.COLOUR).size(12f).thickness(0.1f).life(14).spawn();
		l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
		return OBSERVER_CD;
	}

	/** Hopper: a vacuum -- every loose item and XP orb within 16 pulled straight into him. */
	private static int vacuum(ServerPlayer p, ServerLevel l) {
		for (Entity e : l.getEntities(p, p.getBoundingBox().inflate(VACUUM_RADIUS), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			l.sendParticles(UpgradeKit.SPARK, e.getX(), e.getY() + 0.2, e.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
			if (e instanceof ItemEntity ie) {
				ie.setNoPickUpDelay();
			}
			e.teleportTo(p.getX(), p.getY() + 0.3, p.getZ());
			e.setDeltaMovement(Vec3.ZERO);
		}
		l.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 0.6f);
		return HOPPER_CD;
	}

	/** Hover-cart / hover-boat: a ram -- he shoots forward and whatever is just ahead is bowled over. */
	private static int ram(UpgradeKit kit, ServerPlayer p, ServerLevel l) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		p.setDeltaMovement(look.scale(1.6).add(0, 0.15, 0));
		p.hurtMarked = true;
		for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(look.scale(4)).inflate(0.8), e -> e != p && e.isAlive())) {
			e.invulnerableTime = 0;
			UpgradeKit.hurt(p, e, RAM_DAMAGE, false);
			e.push(look.x * 1.5, 0.45, look.z * 1.5);
			e.hurtMarked = true;
		}
		l.playSound(null, p.blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 1f, 1.8f);
		return RAM_CD;
	}
}
