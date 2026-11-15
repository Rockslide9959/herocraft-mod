package com.projecthero.mod.targetdummy;

import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user): the <b>Target Dummy</b> -- a straw training dummy on a post that never dies and shows the damage it
 * takes (floating numbers, DPS, totals, armour reduction; drawn by {@code TargetDummyRenderer}).
 *
 * <ul>
 * <li>A {@link LivingEntity} (not a mob: no AI, no despawn, never moves), so every power, beam, AoE, DoT and projectile
 * that looks for living things can hit it. It is an {@link Enemy} so the powers' "hostile only" targeting
 * ({@code HeroTargets#isHostile}) picks it too, while vanilla golems (which hunt {@code Mob}s) and the bosses'
 * threat tables ({@code BossTargets#isVictim} skips every {@code Enemy}) leave it alone.</li>
 * <li><b>Infinite HP:</b> each hit is measured -- raw, then after armour / protection / Resistance -- recorded and
 * dropped; health never goes down (anything that sets health lower is recorded as "true damage" and undone). Falling,
 * suffocating, drowning and cramming are ignored; {@code /kill} and the void remove it without a drop.</li>
 * <li><b>Hit cooldown:</b> by default every hit counts (invulnerability frames are cleared after each hit), so rapid
 * multi-hit powers all register. Right-clicking it empty-handed switches to vanilla's half-second cooldown, which shows
 * exactly what a mob would take (per-tick beams are throttled by it on mobs).</li>
 * <li>Right-click armour onto it (to test reduction); empty hand takes the piece you click off. Sneak + right-click (with
 * anything) picks it up as its item, keeping its name, armour and cooldown setting. Nothing else removes it.</li>
 * <li>Stats live on the entity (no static maps). A combat ends {@link #COMBAT_TIMEOUT} ticks after the last hit; the
 * next hit starts a new one. Not saved (a reload starts fresh).</li>
 * </ul>
 */
public class TargetDummyEntity extends LivingEntity implements Enemy {
	/** No hit for 5 s ends the combat. */
	public static final int COMBAT_TIMEOUT = 100;

	/** Vanilla hit cooldown on (mob-accurate) or off (every hit counts); synced so the readout can say so. */
	private static final EntityDataAccessor<Boolean> DATA_COOLDOWN = SynchedEntityData.defineId(TargetDummyEntity.class, EntityDataSerializers.BOOLEAN);

	private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);

	// combat stats (server)
	private int hits;
	private float total;
	private float totalRaw;
	private float biggest;
	private float lastRaw;
	private float lastDealt;
	private int firstHitTick;
	private int lastHitTick = -100_000;
	private Component lastLabel = Component.empty();
	private DummyDamageKind lastKind = DummyDamageKind.PHYSICAL;

	// the player swing in flight (AttackEntityCallback -> hurt in the same tick)
	private UUID meleeBy;
	private int meleeTick = -1;
	private boolean meleeCrit;
	/** Guards our own setHealth calls from being recorded as "true damage". */
	private boolean healing;

	/** Client-only data (floating numbers, readout, wobble) -- set and read by the client renderer. */
	public Object clientData;

	public TargetDummyEntity(EntityType<? extends TargetDummyEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COOLDOWN, false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || isRemoved()) {
			return false;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			discard(); // /kill and the void: gone, no drop
			return true;
		}
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.STARVE) || source.is(DamageTypes.FLY_INTO_WALL)) {
			return false;
		}
		boolean landed = super.hurt(source, amount);
		if (!hitCooldown()) {
			invulnerableTime = 0; // every hit counts: rapid multi-hit powers all register
		}
		return landed;
	}

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		if (isInvulnerableTo(source)) {
			return;
		}
		float raw = Math.max(0.0f, amount);
		float dealt = raw;
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			// vanilla's armour formula, without wearing the armour down (a test dummy should not eat your gear)
			dealt = CombatRules.getDamageAfterAbsorb(this, dealt, source, getArmorValue(), (float) getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		}
		dealt = getDamageAfterMagicAbsorb(source, dealt); // Resistance + Protection enchantments
		boolean melee = meleeBy != null && meleeTick == tickCount && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() != null
				&& meleeBy.equals(source.getEntity().getUUID());
		boolean crit = melee && meleeCrit;
		if (melee) {
			meleeBy = null;
		}
		TargetDummyLabels.Label label = TargetDummyLabels.describe(this, source, melee, crit);
		record(raw, dealt, label, hitYaw(source));
	}

	@Override
	public void setHealth(float health) {
		if (healing || level() == null || level().isClientSide() || armorItems == null) {
			super.setHealth(health);
			return;
		}
		float max = getMaxHealth();
		float now = getHealth();
		if (health < now - 1.0e-4f) {
			// somebody set our health straight down (an execute / "true damage" effect): record it, stay whole
			record(now - health, now - health, new TargetDummyLabels.Label(Component.translatable("projecthero.target_dummy.source.direct"),
					DummyDamageKind.DIRECT, false, false), Float.NaN);
		}
		super.setHealth(max);
	}

	@Override
	public void die(DamageSource source) {
		// never dies (nothing should get here: health never drops)
	}

	@Override
	public void kill() {
		discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && getHealth() < getMaxHealth()) {
			healing = true;
			super.setHealth(getMaxHealth());
			healing = false;
		}
	}

	/** Direction the hit travelled (degrees, atan2(z, x)), from the projectile / attacker / source position. */
	private float hitYaw(DamageSource source) {
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 origin = from != null ? from.position() : source.getSourcePosition();
		if (origin == null) {
			return Float.NaN;
		}
		double dx = getX() - origin.x;
		double dz = getZ() - origin.z;
		if (dx * dx + dz * dz < 1.0e-4) {
			if (from != null) {
				Vec3 look = from.getLookAngle();
				return (float) Math.toDegrees(Math.atan2(look.z, look.x));
			}
			return Float.NaN;
		}
		return (float) Math.toDegrees(Math.atan2(dz, dx));
	}

	/** Adds one hit to the combat and tells the watching clients. */
	private void record(float raw, float dealt, TargetDummyLabels.Label label, float hitYaw) {
		int now = tickCount;
		boolean newCombat = hits == 0 || now - lastHitTick > COMBAT_TIMEOUT;
		if (newCombat) {
			resetStats();
			firstHitTick = now;
		}
		hits++;
		total += dealt;
		totalRaw += raw;
		biggest = Math.max(biggest, dealt);
		lastRaw = raw;
		lastDealt = dealt;
		lastHitTick = now;
		lastLabel = label.name();
		lastKind = label.kind();
		byte flags = 0;
		if (label.crit()) {
			flags |= DummyHitPayload.CRIT;
		}
		if (label.dot()) {
			flags |= DummyHitPayload.DOT;
		}
		if (newCombat) {
			flags |= DummyHitPayload.NEW_COMBAT;
		}
		if (hitCooldown()) {
			flags |= DummyHitPayload.COOLDOWN;
		}
		send(new DummyHitPayload(getId(), raw, dealt, (byte) label.kind().ordinal(), flags, label.name(), total, totalRaw, hits, biggest,
				now - firstHitTick, hitYaw));
	}

	private void resetStats() {
		hits = 0;
		total = 0.0f;
		totalRaw = 0.0f;
		biggest = 0.0f;
		lastRaw = 0.0f;
		lastDealt = 0.0f;
		lastLabel = Component.empty();
		lastKind = DummyDamageKind.PHYSICAL;
	}

	/** Clears the stats now and tells the clients (right-click). */
	public void clearStats() {
		resetStats();
		lastHitTick = -100_000;
		send(new DummyHitPayload(getId(), 0, 0, (byte) 0, (byte) (DummyHitPayload.RESET | (hitCooldown() ? DummyHitPayload.COOLDOWN : 0)),
				Component.empty(), 0, 0, 0, 0, 0, Float.NaN));
	}

	private void send(DummyHitPayload payload) {
		if (level().isClientSide()) {
			return;
		}
		for (ServerPlayer p : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** A player is swinging at us right now (from {@code AttackEntityCallback}): the matching hurt is a melee hit. */
	public void markMelee(Player player) {
		meleeBy = player.getUUID();
		meleeTick = tickCount;
		// vanilla's crit rule (Player#attack), read before the swing resets the attack timer
		meleeCrit = player.getAttackStrengthScale(0.5f) > 0.9f && player.fallDistance > 0.0f && !player.onGround() && !player.onClimbable()
				&& !player.isInWater() && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && !player.isPassenger()
				&& !player.isSprinting();
	}

	// ---------------------------------------------------------------- stats (gametests, readout)

	public int hits() {
		return hits;
	}

	public float totalDamage() {
		return total;
	}

	public float totalRaw() {
		return totalRaw;
	}

	public float biggestHit() {
		return biggest;
	}

	public float lastRaw() {
		return lastRaw;
	}

	public float lastDealt() {
		return lastDealt;
	}

	public Component lastLabel() {
		return lastLabel;
	}

	public DummyDamageKind lastKind() {
		return lastKind;
	}

	/** Ticks from the combat's first hit to its last. */
	public int combatTicks() {
		return hits == 0 ? 0 : lastHitTick - firstHitTick;
	}

	/** Damage per second over the combat (at least one second long). */
	public float dps() {
		return hits == 0 ? 0.0f : total / Math.max(1.0f, combatTicks() / 20.0f);
	}

	/** Is a combat running (hit within the last {@link #COMBAT_TIMEOUT} ticks)? */
	public boolean inCombat() {
		return hits > 0 && tickCount - lastHitTick <= COMBAT_TIMEOUT;
	}

	public boolean hitCooldown() {
		return entityData.get(DATA_COOLDOWN);
	}

	public void setHitCooldown(boolean on) {
		entityData.set(DATA_COOLDOWN, on);
	}

	// ---------------------------------------------------------------- interaction

	@Override
	public InteractionResult interactAt(Player player, Vec3 at, InteractionHand hand) {
		if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (player.isSecondaryUseActive()) {
			if (!level().isClientSide()) {
				pickUp(player);
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}
		EquipmentSlot heldSlot = held.isEmpty() ? null : getEquipmentSlotForItem(held);
		if (heldSlot != null && heldSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			if (!level().isClientSide()) {
				ItemStack old = getItemBySlot(heldSlot);
				if (!old.isEmpty() && EnchantmentHelper.has(old, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) && !player.getAbilities().instabuild) {
					return InteractionResult.FAIL;
				}
				setItemSlot(heldSlot, held.copyWithCount(1));
				if (!player.getAbilities().instabuild) {
					held.shrink(1);
				}
				give(player, old);
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}
		if (!held.isEmpty()) {
			return InteractionResult.PASS; // name tags and the like work as usual
		}
		if (!level().isClientSide()) {
			EquipmentSlot clicked = slotAt(at.y);
			ItemStack piece = getItemBySlot(clicked);
			if (!piece.isEmpty() && !(EnchantmentHelper.has(piece, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) && !player.getAbilities().instabuild)) {
				setItemSlot(clicked, ItemStack.EMPTY);
				give(player, piece);
			} else {
				setHitCooldown(!hitCooldown());
				clearStats();
				player.displayClientMessage(Component.translatable(hitCooldown() ? "projecthero.target_dummy.cooldown_on" : "projecthero.target_dummy.cooldown_off"), true);
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.NEUTRAL, 0.4f, hitCooldown() ? 0.8f : 1.2f);
			}
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	/** Which armour piece a click at {@code y} (above the feet) reaches. */
	static EquipmentSlot slotAt(double y) {
		if (y >= 1.5) {
			return EquipmentSlot.HEAD;
		}
		if (y >= 0.85) {
			return EquipmentSlot.CHEST;
		}
		if (y >= 0.4) {
			return EquipmentSlot.LEGS;
		}
		return EquipmentSlot.FEET;
	}

	private static void give(Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** Back into the inventory as the item (name, armour and cooldown setting kept). */
	public void pickUp(Player player) {
		ItemStack stack = TargetDummyItem.stackOf(this);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.NEUTRAL, 0.8f, 1.1f);
		discard();
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(TargetDummyMod.ITEM);
	}

	// ---------------------------------------------------------------- never moves

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public void push(double x, double y, double z) {
	}

	@Override
	public void setDeltaMovement(Vec3 motion) {
		// only gravity: no launches, pulls or knockback (it falls if its floor goes)
		super.setDeltaMovement(new Vec3(0.0, Math.min(0.0, motion.y), 0.0));
	}

	@Override
	public void setDeltaMovement(double x, double y, double z) {
		setDeltaMovement(new Vec3(x, y, z));
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean startRiding(Entity vehicle, boolean force) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMOR_STAND_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ARMOR_STAND_BREAK;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6f;
	}

	// ---------------------------------------------------------------- equipment

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return armorItems;
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? armorItems.get(slot.getIndex()) : ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
		if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			return;
		}
		verifyEquippedItem(stack);
		onEquipItem(slot, armorItems.set(slot.getIndex(), stack), stack);
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	// ---------------------------------------------------------------- saving

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		ListTag list = new ListTag();
		for (ItemStack stack : armorItems) {
			list.add(stack.saveOptional(registryAccess()));
		}
		tag.put("ArmorItems", list);
		tag.putBoolean("HitCooldown", hitCooldown());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("ArmorItems", Tag.TAG_LIST)) {
			ListTag list = tag.getList("ArmorItems", Tag.TAG_COMPOUND);
			for (int i = 0; i < armorItems.size() && i < list.size(); i++) {
				armorItems.set(i, ItemStack.parseOptional(registryAccess(), list.getCompound(i)));
			}
		}
		setHitCooldown(tag.getBoolean("HitCooldown"));
	}

	/** Snaps a yaw to 22.5-degree steps (placement). */
	static float snapYaw(float yaw) {
		return Mth.floor((Mth.wrapDegrees(yaw) + 11.25f) / 22.5f) * 22.5f;
	}
}
