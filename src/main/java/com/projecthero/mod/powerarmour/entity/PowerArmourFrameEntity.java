package com.projecthero.mod.powerarmour.entity;

import java.util.UUID;

import com.projecthero.mod.powerarmour.ArmourTier;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.item.PowerCoreItem;
import com.projecthero.mod.powerarmour.menu.PowerArmourEditorMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: a standing, empty suit. It never takes damage (explosions, fire and lava included) and only leaves
 * the world through the editor's Pick Up button (back to its item, everything kept) or by someone stepping into it.
 *
 * <ul>
 *   <li>Right-click: step in ({@link PowerArmour#tryEnter}) -- the side you clicked from opens ({@link #side()}): from the front
 *   you turn and step back in, from the back you walk straight in; it seals round you.</li>
 *   <li>Sneak + right-click with an empty hand: the Armour Editor (modules, core, paint, Pick Up).</li>
 *   <li>Right-click with its repair material (or a Suit Repair Kit): restores integrity.</li>
 *   <li>Right-click with a power core while it has none: puts the core in.</li>
 * </ul>
 * Its open / close animation is synced ({@link #anim()}); the player-side half of the sequence is {@link PowerArmour}'s.
 */
public class PowerArmourFrameEntity extends Entity {
	public static final int CLOSED = 0, OPENING = 1, OPEN = 2, CLOSING = 3;

	private static final EntityDataAccessor<CompoundTag> DATA_SUIT = SynchedEntityData.defineId(PowerArmourFrameEntity.class,
			EntityDataSerializers.COMPOUND_TAG);
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(PowerArmourFrameEntity.class,
			EntityDataSerializers.INT);
	/** Round 2: which side opens (PowerArmourState.SIDE_FRONT / SIDE_BACK) -- the side the player clicked from. */
	private static final EntityDataAccessor<Integer> DATA_SIDE = SynchedEntityData.defineId(PowerArmourFrameEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_ANIM_START = SynchedEntityData.defineId(PowerArmourFrameEntity.class,
			EntityDataSerializers.LONG);

	private SuitData suit = SuitData.fresh(ArmourTier.IRON);
	private SuitData clientSuit;
	private CompoundTag clientSuitTag;
	/** Whoever is stepping in right now (server). */
	private UUID occupant;
	private long occupantSince;

	public PowerArmourFrameEntity(EntityType<? extends PowerArmourFrameEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SUIT, SuitData.fresh(ArmourTier.IRON).toTag());
		builder.define(DATA_ANIM, CLOSED);
		builder.define(DATA_ANIM_START, 0L);
		builder.define(DATA_SIDE, PowerArmourState.SIDE_FRONT);
	}

	// ------------------------------------------------------------------ suit

	/** The suit (a live copy on the server; the synced one on the client). Change it through {@link #setSuit}. */
	public SuitData suit() {
		if (!level().isClientSide()) {
			return suit;
		}
		CompoundTag tag = entityData.get(DATA_SUIT);
		if (clientSuit == null || tag != clientSuitTag) {
			clientSuit = SuitData.fromTag(tag);
			clientSuitTag = tag;
		}
		return clientSuit;
	}

	public void setSuit(SuitData data) {
		suit = data.copy();
		entityData.set(DATA_SUIT, suit.toTag());
	}

	/** Pushes an in-place change of {@link #suit()} to clients. */
	public void suitChanged() {
		entityData.set(DATA_SUIT, suit.toTag());
	}

	public ArmourTier tier() {
		return suit().tier;
	}

	// ------------------------------------------------------------------ animation

	public int anim() {
		return entityData.get(DATA_ANIM);
	}

	public long animStart() {
		return entityData.get(DATA_ANIM_START);
	}

	public void setAnim(int anim) {
		entityData.set(DATA_ANIM, anim);
		entityData.set(DATA_ANIM_START, level().getGameTime());
	}

	/** How open it is (0 closed .. 1 open) at {@code time} (game ticks, partial allowed). */
	public float openness(float time) {
		float t = time - animStart();
		return switch (anim()) {
			case OPENING -> Math.min(1f, Math.max(0f, t / PowerArmourConfig.FRAME_OPEN_TICKS));
			case OPEN -> 1f;
			case CLOSING -> 1f - Math.min(1f, Math.max(0f, t / PowerArmourConfig.FRAME_CLOSE_TICKS));
			default -> 0f;
		};
	}

	/** The side that opens: {@link PowerArmourState#SIDE_FRONT} or {@link PowerArmourState#SIDE_BACK}. */
	public int side() {
		return entityData.get(DATA_SIDE);
	}

	public void setSide(int side) {
		entityData.set(DATA_SIDE, side == PowerArmourState.SIDE_BACK ? PowerArmourState.SIDE_BACK : PowerArmourState.SIDE_FRONT);
	}

	public boolean occupied() {
		return occupant != null;
	}

	public void claim(Player player) {
		occupant = player.getUUID();
		occupantSince = level().getGameTime();
	}

	public void release() {
		occupant = null;
	}

	public boolean claimedBy(Player player) {
		return player.getUUID().equals(occupant);
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		// falls like an armour stand, never pushed
		if (!isNoGravity()) {
			Vec3 v = getDeltaMovement();
			setDeltaMovement(0.0, onGround() ? 0.0 : Math.max(-1.5, v.y - 0.08), 0.0);
			move(MoverType.SELF, getDeltaMovement());
		}
		if (level().isClientSide()) {
			return;
		}
		long now = level().getGameTime();
		int anim = anim();
		if (anim == OPENING && now - animStart() >= PowerArmourConfig.FRAME_OPEN_TICKS) {
			entityData.set(DATA_ANIM, OPEN);
		} else if (anim == CLOSING && now - animStart() >= PowerArmourConfig.FRAME_CLOSE_TICKS) {
			entityData.set(DATA_ANIM, CLOSED);
		}
		// an opened frame nobody stepped into closes again
		if (occupant != null && now - occupantSince > PowerArmourConfig.FRAME_OCCUPANT_TIMEOUT) {
			occupant = null;
		}
		if (occupant == null && anim() == OPEN && now - animStart() > PowerArmourConfig.FRAME_STAY_OPEN_TICKS) {
			setAnim(CLOSING);
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 0.8f);
		}
		// v0.17.5: a Solar Plate keeps trickling while it stands in the sun
		if (now % 20 == 0 && suit.has(ModuleType.SOLAR_PLATE) && suit.core != null && PowerArmour.inSunlight(level(), blockPosition().above())) {
			float before = suit.coreCharge;
			suit.charge(PowerArmourConfig.SOLAR_PER_SECOND);
			if (suit.coreCharge != before && now % 100 == 0) {
				suitChanged();
			}
		}
	}

	// ------------------------------------------------------------------ interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerPlayer sp = (ServerPlayer) player;
		ItemStack held = player.getMainHandItem();
		if (player.isShiftKeyDown() && held.isEmpty()) {
			if (occupied() || anim() == OPENING) {
				return InteractionResult.FAIL;
			}
			openEditor(sp);
			return InteractionResult.CONSUME;
		}
		if (PowerArmour.isRepairItem(suit.tier, held)) {
			if (suit.integrity >= suit.tier.integrity - 0.01f) {
				player.displayClientMessage(Component.translatable("message.projecthero.power_armour.repaired_full")
						.withStyle(ChatFormatting.GRAY), true);
				return InteractionResult.CONSUME;
			}
			suit.integrity = Math.min(suit.tier.integrity, suit.integrity + suit.tier.integrity * PowerArmour.repairShare(suit.tier, held));
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			suitChanged();
			level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.3f);
			((ServerLevel) level()).sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 1.1, getZ(), 12, 0.35, 0.6, 0.35, 0.02);
			player.displayClientMessage(Component.translatable("message.projecthero.power_armour.repaired",
					Math.round(suit.integrityFraction() * 100)).withStyle(ChatFormatting.AQUA), true);
			return InteractionResult.CONSUME;
		}
		if (held.getItem() instanceof PowerCoreItem core && suit.core == null) {
			String why = suit.whyNotCore(core.type());
			if (why != null) {
				player.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.RED), true);
				return InteractionResult.FAIL;
			}
			suit.core = core.type();
			suit.coreCharge = PowerCoreItem.charge(held);
			held.shrink(1);
			suitChanged();
			level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
			return InteractionResult.CONSUME;
		}
		return PowerArmour.tryEnter(sp, this) ? InteractionResult.CONSUME : InteractionResult.FAIL;
	}

	public void openEditor(ServerPlayer player) {
		PowerArmourFrameEntity self = this;
		player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer p) {
				return self.getId();
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("container.projecthero.power_armour_editor");
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv,
					Player p) {
				return new PowerArmourEditorMenu(id, inv, self);
			}
		});
		level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6f, 0.8f);
	}

	// ------------------------------------------------------------------ never hurt

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	// ------------------------------------------------------------------ save

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("Suit")) {
			setSuit(SuitData.fromTag(tag.getCompound("Suit")));
		}
		int anim = tag.getInt("Anim");
		entityData.set(DATA_ANIM, anim == OPENING || anim == OPEN ? CLOSING : CLOSED);
		setSide(tag.getInt("Side"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.put("Suit", suit.toTag());
		tag.putInt("Anim", anim());
		tag.putInt("Side", side());
	}
}
