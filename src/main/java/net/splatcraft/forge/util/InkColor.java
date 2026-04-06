package net.splatcraft.forge.util;

import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.splatcraft.forge.Splatcraft;
import org.jetbrains.annotations.Nullable;

public class InkColor implements Comparable<InkColor> {
    private static final TreeMap<Integer, InkColor> colorMap = new TreeMap<>();
    private static int idIndex = 0;

    private final int hexCode;
    private final DyeColor dyeColor;
    private final int id;
    private String name;
    private ResourceLocation registryName;

    public InkColor(String name, int color, @Nullable DyeColor dyeColor) {
        this.hexCode = color;
        this.name = name;
        this.dyeColor = dyeColor;
        this.id = idIndex++;
        colorMap.put(color, this);
    }

    public InkColor(String name, int color) {
        this(name, color, null);
    }

    public static InkColor getByHex(int hexCode) {
        return colorMap.get(hexCode);
    }

    public MutableComponent getLocalizedName() {
        return Component.translatable(getUnlocalizedName());
    }

    public String getUnlocalizedName() {
        ResourceLocation key = getRegistryName();
        return "ink_color." + key.getNamespace() + "." + key.getPath();
    }

    public String getHexCode() {
        return String.format("%06X", hexCode);
    }

    public int getColor() {
        return hexCode;
    }

    public @Nullable DyeColor getDyeColor() {
        return dyeColor;
    }

    public String getName() {
        return name;
    }

    public ResourceLocation getRegistryName() {
        return registryName != null ? registryName : new ResourceLocation(Splatcraft.MODID, name);
    }

    public InkColor setRegistryName(ResourceLocation name) {
        this.name = name.getPath();
        this.registryName = name;
        return this;
    }

    @Override
    public String toString() {
        return name + ": #" + getHexCode().toUpperCase();
    }

    @Override
    public int compareTo(InkColor other) {
        return id - other.id;
    }

    public static class DummyType extends InkColor {
        public DummyType() {
            super("dummy", ColorUtils.DEFAULT);
        }
    }
}
