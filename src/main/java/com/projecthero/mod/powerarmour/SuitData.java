package com.projecthero.mod.powerarmour;

import java.util.Arrays;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * v0.17.5 Power Armour: everything one suit is -- its tier, integrity, power core (type + charge), the module in each upgrade
 * slot, its paint and the Capacitor Bank's reserve. The same record lives on a parked frame ({@code PowerArmourFrameEntity}), on
 * a frame item (the {@code projecthero:power_armour_suit} component) and on the player while sealed ({@link PowerArmourState}),
 * so picking a frame up, upgrading it at a smithing table or stepping in keeps everything.
 *
 * <p>Mutable, but every holder stores its own {@link #copy()}. Serialised as a compound tag (no item stacks inside: modules and
 * cores are plain ids, so it syncs without registry access).
 */
public final class SuitData {
	public static final int DEFAULT_PAINT = -1;
	/** The Capacitor Bank's reserve, used once the core is dry. */
	public static final float BANK_CAPACITY = 600f;
	public static final int OVERCLOCK_OUTPUT = 3;

	public ArmourTier tier;
	public float integrity;
	public CoreType core;
	public float coreCharge;
	public ModuleType[] modules;
	public int primary = DEFAULT_PAINT, secondary = DEFAULT_PAINT, glow = DEFAULT_PAINT;
	public float bank;

	public SuitData(ArmourTier tier) {
		this.tier = tier;
		this.integrity = tier.integrity;
		this.modules = new ModuleType[tier.slots];
	}

	public static SuitData fresh(ArmourTier tier) {
		return new SuitData(tier);
	}

	public SuitData copy() {
		SuitData d = new SuitData(tier);
		d.integrity = integrity;
		d.core = core;
		d.coreCharge = coreCharge;
		d.modules = Arrays.copyOf(modules, modules.length);
		d.primary = primary;
		d.secondary = secondary;
		d.glow = glow;
		d.bank = bank;
		return d;
	}

	/** This suit moved to {@code newTier} (a smithing upgrade): every module keeps its slot, integrity keeps its share. */
	public SuitData retiered(ArmourTier newTier) {
		if (newTier == tier) {
			return copy();
		}
		SuitData d = copy();
		float share = tier.integrity <= 0f ? 1f : integrity / tier.integrity;
		d.tier = newTier;
		d.integrity = Math.max(0f, Math.min(newTier.integrity, share * newTier.integrity));
		d.modules = new ModuleType[newTier.slots];
		for (int i = 0; i < Math.min(modules.length, d.modules.length); i++) {
			d.modules[i] = modules[i];
		}
		return d;
	}

	// ------------------------------------------------------------------ queries

	public boolean has(ModuleType m) {
		for (ModuleType x : modules) {
			if (x == m) {
				return true;
			}
		}
		return false;
	}

	/** The mounted arm weapon, or null. */
	public ModuleType weapon() {
		for (ModuleType x : modules) {
			if (x != null && x.weapon) {
				return x;
			}
		}
		return null;
	}

	/** The back module, or null. */
	public ModuleType back() {
		for (ModuleType x : modules) {
			if (x != null && x.part == BodyPart.BACK) {
				return x;
			}
		}
		return null;
	}

	public float capacity() {
		return core == null ? 0f : core.capacity;
	}

	public float bankCapacity() {
		return has(ModuleType.CAPACITOR_BANK) ? BANK_CAPACITY : 0f;
	}

	/** The core's output budget (Overclock adds {@link #OVERCLOCK_OUTPUT}). */
	public int output() {
		int base = core == null ? 0 : core.output;
		return base + (has(ModuleType.OVERCLOCK) ? OVERCLOCK_OUTPUT : 0);
	}

	/** The output budget an output of {@code coreType} would give this suit. */
	public int outputWith(CoreType coreType) {
		int base = coreType == null ? 0 : coreType.output;
		return base + (has(ModuleType.OVERCLOCK) ? OVERCLOCK_OUTPUT : 0);
	}

	/** The output the installed modules need. */
	public int load() {
		int n = 0;
		for (ModuleType x : modules) {
			if (x != null) {
				n += x.output;
			}
		}
		return n;
	}

	public float energy() {
		return coreCharge + bank;
	}

	/** Has charge to run (core or bank). An unpowered suit crawls and has no moves, but still opens. */
	public boolean powered() {
		return core != null && coreCharge > 0.01f || bank > 0.01f;
	}

	/** Takes {@code amount} from the core, then the bank. False (nothing taken) if there is not enough. */
	public boolean spend(float amount) {
		if (amount <= 0f) {
			return true;
		}
		if (energy() + 1e-4f < amount) {
			return false;
		}
		float fromCore = Math.min(coreCharge, amount);
		coreCharge -= fromCore;
		bank = Math.max(0f, bank - (amount - fromCore));
		return true;
	}

	/** Drains up to {@code amount} (idle draw, falls): whatever is there. */
	public void drain(float amount) {
		float fromCore = Math.min(coreCharge, amount);
		coreCharge -= fromCore;
		bank = Math.max(0f, bank - (amount - fromCore));
	}

	/** Adds charge to the core (capped), the rest into the bank. */
	public void charge(float amount) {
		if (amount <= 0f) {
			return;
		}
		float room = Math.max(0f, capacity() - coreCharge);
		float toCore = Math.min(room, amount);
		coreCharge += toCore;
		bank = Math.min(bankCapacity(), bank + (amount - toCore));
	}

	public float integrityFraction() {
		return tier.integrity <= 0f ? 0f : Math.max(0f, Math.min(1f, integrity / tier.integrity));
	}

	/** Below this share of integrity the frame is a wreck: it will not seal until repaired. */
	public static final float WRECK_SHARE = 0.2f;

	public boolean wrecked() {
		return integrityFraction() < WRECK_SHARE;
	}

	public int primaryColour() {
		return primary == DEFAULT_PAINT ? tier.primary : primary;
	}

	public int secondaryColour() {
		return secondary == DEFAULT_PAINT ? tier.secondary : secondary;
	}

	public int glowColour() {
		return glow == DEFAULT_PAINT ? tier.glow : glow;
	}

	/** Why {@code m} may not go into slot {@code index}, or null when it fits. Keys under {@code projecthero.power_armour.fit.*}. */
	public String whyNot(int index, ModuleType m) {
		if (index < 0 || index >= modules.length) {
			return "projecthero.power_armour.fit.no_slot";
		}
		if (tier.slotPart(index) != m.part) {
			return "projecthero.power_armour.fit.wrong_part";
		}
		for (int i = 0; i < modules.length; i++) {
			if (i == index || modules[i] == null) {
				continue;
			}
			if (modules[i] == m) {
				return "projecthero.power_armour.fit.duplicate";
			}
			if (m.weapon && modules[i].weapon) {
				return "projecthero.power_armour.fit.one_weapon";
			}
		}
		if (core != null) {
			SuitData h = copy();
			h.modules[index] = m;
			if (h.load() > h.output()) {
				return "projecthero.power_armour.fit.over_budget";
			}
		}
		return null;
	}

	/** Why the module in slot {@code index} may not come out (taking out Overclock can leave the load over budget). */
	public String whyNotRemove(int index) {
		if (index < 0 || index >= modules.length || modules[index] == null || core == null) {
			return null;
		}
		SuitData h = copy();
		h.modules[index] = null;
		return h.load() > h.output() ? "projecthero.power_armour.fit.over_budget" : null;
	}

	/** Why a core of {@code type} may not go in, or null. */
	public String whyNotCore(CoreType type) {
		if (type != null && load() > outputWith(type)) {
			return "projecthero.power_armour.fit.core_too_weak";
		}
		return null;
	}

	// ------------------------------------------------------------------ serialisation

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		t.putString("Tier", tier.id());
		t.putFloat("Integrity", integrity);
		if (core != null) {
			t.putString("Core", core.id());
			t.putFloat("Charge", coreCharge);
		}
		ListTag mods = new ListTag();
		for (ModuleType m : modules) {
			mods.add(StringTag.valueOf(m == null ? "" : m.id()));
		}
		t.put("Modules", mods);
		t.putInt("Primary", primary);
		t.putInt("Secondary", secondary);
		t.putInt("Glow", glow);
		t.putFloat("Bank", bank);
		return t;
	}

	public static SuitData fromTag(CompoundTag t) {
		ArmourTier tier = ArmourTier.byId(t.getString("Tier"));
		SuitData d = new SuitData(tier);
		d.integrity = t.contains("Integrity") ? Math.max(0f, Math.min(tier.integrity, t.getFloat("Integrity"))) : tier.integrity;
		d.core = CoreType.byId(t.getString("Core"));
		d.coreCharge = d.core == null ? 0f : Math.max(0f, Math.min(d.core.capacity, t.getFloat("Charge")));
		ListTag mods = t.getList("Modules", Tag.TAG_STRING);
		for (int i = 0; i < Math.min(mods.size(), d.modules.length); i++) {
			d.modules[i] = ModuleType.byId(mods.getString(i));
		}
		d.primary = t.contains("Primary") ? t.getInt("Primary") : DEFAULT_PAINT;
		d.secondary = t.contains("Secondary") ? t.getInt("Secondary") : DEFAULT_PAINT;
		d.glow = t.contains("Glow") ? t.getInt("Glow") : DEFAULT_PAINT;
		d.bank = Math.max(0f, Math.min(d.bankCapacity(), t.getFloat("Bank")));
		return d;
	}

	public static final Codec<SuitData> CODEC = CompoundTag.CODEC.xmap(SuitData::fromTag, SuitData::toTag);
	public static final StreamCodec<RegistryFriendlyByteBuf, SuitData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG
			.<RegistryFriendlyByteBuf>cast().map(SuitData::fromTag, SuitData::toTag);

	@Override
	public boolean equals(Object o) {
		return o instanceof SuitData d && d.toTag().equals(toTag());
	}

	@Override
	public int hashCode() {
		return toTag().hashCode();
	}
}
