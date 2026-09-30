package com.projecthero.mod.hero;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * The full data-driven definition of one experimental power. Everything the power system, guide, and
 * reference doc need is here; runtime ability behaviour is attached separately via
 * {@link AbilityHandlers}.
 *
 * @param id          stable registry id (e.g. {@code projecthero:power_01_super_strength})
 * @param nameKey     translation key for the display name
 * @param category    broad family
 * @param descKey     translation key for the flavour/summary line
 * @param abilities   six (slots 1..6) or, since v0.13.22, eight (plus the H / N utility slots 7..8); since v0.14.5
 *                    zero for a passive-only power ({@link Builder#passiveOnly()})
 * @param passiveKeys translation keys for passive-trait lines
 * @param serum       brewing recipe data
 * @param trigger     mutation exposure event data
 * @param comboKeys   translation keys for documented synergies with other powers
 */
public record Power(
		ResourceLocation id,
		String nameKey,
		PowerCategory category,
		String descKey,
		List<Ability> abilities,
		List<String> passiveKeys,
		SerumRecipe serum,
		MutationTrigger trigger,
		List<String> comboKeys) {

	public Power {
		if (abilities.size() != 0 && abilities.size() != 6 && abilities.size() != 8) {
			throw new IllegalArgumentException(id + " must define 0, 6 or 8 abilities, got " + abilities.size());
		}
	}

	/**
	 * The power's tier. Every power defined here is an <b>Experimental Tier</b> mutation (as opposed to
	 * the Hero-Tier powers -- Thor, Iron Man, Spider-Man, Max Steel, the Punisher -- which live in their
	 * own systems and are mutually exclusive with each other and with mutations). Experimental Tier is
	 * the tier whose owned powers <em>stack</em>: their passive buffs and toggled modes all stay live
	 * at once, and switching the selected slot-set never disturbs another owned power's passives or
	 * modes (see {@link ExperimentalPowers}).
	 */
	public PowerTier tier() {
		return PowerTier.EXPERIMENTAL;
	}

	/** The ability in {@code slot}, or {@code null} for a utility slot (H / N) this power does not define. */
	public Ability ability(AbilitySlot slot) {
		return slot.index() < abilities.size() ? abilities.get(slot.index()) : null;
	}

	/** Whether the power defines an ability for {@code slot}. */
	public boolean hasSlot(AbilitySlot slot) {
		return slot.index() < abilities.size();
	}

	/** The short path segment of the id, e.g. {@code power_01_super_strength}. */
	public String key() {
		return id.getPath();
	}

	public static final class Builder {
		private final ResourceLocation id;
		private final PowerCategory category;
		private final Ability[] abilities = new Ability[8];
		private List<String> passiveKeys = List.of();
		private SerumRecipe serum;
		private MutationTrigger trigger;
		private List<String> comboKeys = List.of();
		private boolean passiveOnly;

		private Builder(ResourceLocation id, PowerCategory category) {
			this.id = id;
			this.category = category;
		}

		public static Builder of(ResourceLocation id, PowerCategory category) {
			return new Builder(id, category);
		}

		public Builder ability(Ability ability) {
			abilities[ability.slot().index()] = ability;
			return this;
		}

		public Builder passives(String... keys) {
			this.passiveKeys = List.of(keys);
			return this;
		}

		public Builder serum(SerumRecipe serum) {
			this.serum = serum;
			return this;
		}

		public Builder trigger(MutationTrigger trigger) {
			this.trigger = trigger;
			return this;
		}

		public Builder combos(String... keys) {
			this.comboKeys = List.of(keys);
			return this;
		}

		/**
		 * v0.14.5: a passive-only power with no ability keys at all (Super Regeneration). The HUD then shows
		 * no keybind boxes and every key press is ignored by the router.
		 */
		public Builder passiveOnly() {
			this.passiveOnly = true;
			return this;
		}

		public Power build() {
			String base = "projecthero.power." + id.getPath();
			if (passiveOnly) {
				for (Ability a : abilities) {
					if (a != null) {
						throw new IllegalStateException(id + " is passive-only but defines " + a.id());
					}
				}
				return new Power(id, base + ".name", category, base + ".desc",
						List.of(), passiveKeys, serum, trigger, comboKeys);
			}
			for (int i = 0; i < 6; i++) {
				if (abilities[i] == null) {
					throw new IllegalStateException(id + " missing ability for slot " + (i + 1));
				}
			}
			if ((abilities[6] == null) != (abilities[7] == null)) {
				throw new IllegalStateException(id + " must define both utility slots (H and N) or neither");
			}
			List<Ability> list = java.util.Arrays.asList(abilities).subList(0, abilities[6] == null ? 6 : 8);
			return new Power(id, base + ".name", category, base + ".desc",
					List.copyOf(list), passiveKeys, serum, trigger, comboKeys);
		}
	}
}
