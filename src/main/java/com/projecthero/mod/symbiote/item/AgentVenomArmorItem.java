package com.projecthero.mod.symbiote.item;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * v0.13.11: one piece of the Agent Venom suit -- what the Symbiote becomes on a bonded Punisher
 * ({@link com.projecthero.mod.symbiote.SymbioteHostType#AGENT_VENOM}). Synthesised by
 * {@link com.projecthero.mod.symbiote.SymbioteSuit} when the Punisher presses H, removed on retract;
 * never crafted, no durability, curse-locked -- exactly like the other two Symbiote suits. Renders the
 * user-supplied Agent Venom skin on {@code geo/agent_venom.geo.json} (a skin-shaped rig, see
 * {@code scratchpad/gen_agent_venom.js}).
 */
public class AgentVenomArmorItem extends SuperheroArmorItem {
	public AgentVenomArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return "agent_venom";
	}
}
