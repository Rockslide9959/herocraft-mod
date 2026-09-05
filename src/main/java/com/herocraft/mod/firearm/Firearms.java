package com.herocraft.mod.firearm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import static com.herocraft.mod.firearm.FirearmData.s;

/**
 * The firearm data registry. One entry per weapon id; {@link com.herocraft.mod.firearm.item.FirearmItem}
 * stores its id and looks its stats up here. Populated statically -- no init call needed.
 *
 * <p>Placeholder sounds: every gun currently borrows vanilla {@link SoundEvents}. The list of
 * bespoke {@code .ogg} assets that should eventually replace them is in {@code docs/PUNISHER_REFERENCE.md}.
 */
public final class Firearms {
	private static final Map<String, FirearmData> BY_ID = new LinkedHashMap<>();

	public static final String PISTOL = "punisher_pistol";
	public static final String RIFLE = "punisher_assault_rifle";
	public static final String SHOTGUN = "punisher_shotgun";
	public static final String SNIPER = "punisher_sniper";

	static {
		register(FirearmData.builder(PISTOL, AmmoKind.PISTOL)
				.magazine(12)
				.damage(5f, 7.5f)
				.fireInterval(7)              // ~3 shots/sec
				.automatic(false)
				.reload(44)                   // 2.2 s
				.spread(0.9f)
				.recoil(0.15f, 1.2f, 0.9f, 0.5f)
				.adsSpreadFactor(0.35f)
				.range(96.0)
				.knockback(0.15)
				.zoom(new float[] { 0.85f }, 1.0f, false)
				.sounds(s(SoundEvents.CROSSBOW_SHOOT), s(SoundEvents.ARMOR_EQUIP_IRON),
						s(SoundEvents.TRIPWIRE_CLICK_ON), s(SoundEvents.PISTON_CONTRACT))
				.firePitch(1.7f, 0.55f)
				.build());

		register(FirearmData.builder(RIFLE, AmmoKind.RIFLE)
				.magazine(30)
				.damage(4f, 6f)
				.fireInterval(4)             // 5 shots/sec
				.automatic(true)
				.reload(60)                  // 3 s
				.spread(1.1f)
				.recoil(0.55f, 6.0f, 0.7f, 0.45f)   // progressive bloom while held
				.adsSpreadFactor(0.4f)
				.range(96.0)
				.knockback(0.12)
				.zoom(new float[] { 0.8f }, 1.0f, false)
				.sounds(s(SoundEvents.CROSSBOW_SHOOT), s(SoundEvents.ARMOR_EQUIP_NETHERITE),
						s(SoundEvents.TRIPWIRE_CLICK_ON), s(SoundEvents.PISTON_CONTRACT))
				.firePitch(1.45f, 0.6f)
				.build());

		register(FirearmData.builder(SHOTGUN, AmmoKind.SHOTGUN)
				.magazine(6)
				.damage(3f, 4.5f)
				.pellets(6)
				.fireInterval(20)            // ~1 shot/sec
				.automatic(false)
				.cycle(16)                   // pump cycle before next fire
				.reload(20)                  // per-shell (shellReload) -> ~1 s/shell
				.shellReload(true)
				.spread(5.5f)                // wide cone
				.recoil(0.0f, 0.0f, 2.4f, 1.0f)
				.adsSpreadFactor(0.7f)
				.falloff(5.0, 20.0, 0.12f)   // strong 0-5, weak by 12, negligible 20+
				.range(24.0)
				.knockback(0.6)
				.zoom(new float[0], 1.0f, false)
				.sounds(s(SoundEvents.CROSSBOW_SHOOT), s(SoundEvents.ARMOR_EQUIP_IRON),
						s(SoundEvents.TRIPWIRE_CLICK_ON), s(SoundEvents.PISTON_CONTRACT))
				.firePitch(0.9f, 0.75f)
				.build());

		register(FirearmData.builder(SNIPER, AmmoKind.SNIPER)
				.magazine(8)
				.damage(28f, 40f)
				.fireInterval(60)            // 3 s between shots
				.automatic(false)
				.cycle(24)                   // bolt cycle
				.reload(100)                 // 5 s
				.spread(0.15f)               // very accurate
				.recoil(0.0f, 0.0f, 2.0f, 0.6f)
				.adsSpreadFactor(0.05f)
				.range(220.0)
				.knockback(0.3)
				.zoom(new float[] { 0.55f, 0.34f, 0.18f, 0.10f }, 0.55f, true)  // normal/3x/6x/10x
				.sounds(s(SoundEvents.CROSSBOW_SHOOT), s(SoundEvents.ARMOR_EQUIP_NETHERITE),
						s(SoundEvents.TRIPWIRE_CLICK_ON), s(SoundEvents.PISTON_CONTRACT))
				.firePitch(0.75f, 0.85f)
				.build());
	}

	private Firearms() {
	}

	private static void register(FirearmData data) {
		BY_ID.put(data.id, data);
	}

	public static FirearmData get(String id) {
		return BY_ID.get(id);
	}

	public static FirearmData of(ItemStack stack) {
		return stack.getItem() instanceof com.herocraft.mod.firearm.item.FirearmItem f ? get(f.firearmId()) : null;
	}

	public static Map<String, FirearmData> all() {
		return Collections.unmodifiableMap(BY_ID);
	}
}
