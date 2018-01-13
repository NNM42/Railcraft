/*------------------------------------------------------------------------------
 Copyright (c) CovertJaguar, 2011-2017
 http://railcraft.info

 This code is the property of CovertJaguar
 and may only be used with explicit written
 permission unless otherwise specified on the
 license page at http://railcraft.info/wiki/info:license.
 -----------------------------------------------------------------------------*/
package mods.railcraft.common.blocks.ore;

import mods.railcraft.common.blocks.IRailcraftBlockContainer;
import mods.railcraft.common.blocks.IVariantEnumBlockSpecific;
import mods.railcraft.common.blocks.RailcraftBlocks;
import mods.railcraft.common.modules.ModuleWorld;

import javax.annotation.Nullable;

/**
 * @author CovertJaguar <http://www.railcraft.info>
 */
public enum EnumOre implements IVariantEnumBlockSpecific<EnumOre> {

    SULFUR("sulfur", "oreSulfur"),
    SALTPETER("saltpeter", "oreSaltpeter"),
    DARK_DIAMOND("dark_diamond", "oreDiamond"),
    DARK_EMERALD("dark_emerald", "oreEmerald"),
    DARK_LAPIS("dark_lapis", "oreLapis"),
    @Deprecated
    POOR_IRON("poor_iron", "orePoorIron"),
    @Deprecated
    POOR_GOLD("poor_gold", "orePoorGold"),
    @Deprecated
    POOR_COPPER("poor_copper", "orePoorCopper"),
    @Deprecated
    POOR_TIN("poor_tin", "orePoorTin"),
    @Deprecated
    POOR_LEAD("poor_lead", "orePoorLead"),
    @Deprecated
    POOR_SILVER("poor_silver", "orePoorSilver"),
    @Deprecated
    COPPER("copper", "oreCopper"),
    @Deprecated
    TIN("tin", "oreTin"),
    @Deprecated
    LEAD("lead", "oreLead"),
    @Deprecated
    SILVER("silver", "oreSilver"),;
    public static final EnumOre[] VALUES = values();
    private final Definition def;
    private final String oreTag;

    EnumOre(String tag, String oreTag) {
        this.def = new Definition(tag, ModuleWorld.class);
        this.oreTag = oreTag;
    }

    @Override
    public Definition getDef() {
        return def;
    }

    @Override
    public IRailcraftBlockContainer getContainer() {
        return RailcraftBlocks.ORE;
    }

    @Override
    public String getTag() {
        return "tile.railcraft.ore_" + getBaseTag();
    }

    public static EnumOre fromOrdinal(int meta) {
        if (meta < 0 || meta >= VALUES.length)
            return SULFUR;
        return VALUES[meta];
    }

    @Nullable
    @Override
    public String getOreTag() {
        return oreTag;
    }
}
