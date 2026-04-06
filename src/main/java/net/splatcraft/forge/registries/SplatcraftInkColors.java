package net.splatcraft.forge.registries;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.splatcraft.forge.Splatcraft;
import net.splatcraft.forge.util.ColorUtils;
import net.splatcraft.forge.util.InkColor;

public class SplatcraftInkColors {
    private static final SimpleInkColorRegistry REGISTRY_INSTANCE = new SimpleInkColorRegistry();
    public static final Supplier<SimpleInkColorRegistry> REGISTRY = () -> REGISTRY_INSTANCE;

    private static InkColor register(InkColor color) {
        return REGISTRY_INSTANCE.register(color);
    }

    public static final InkColor orange = register(new InkColor("orange", 0xDF641A));
    public static final InkColor blue = register(new InkColor("blue", 0x26229F));
    public static final InkColor pink = register(new InkColor("pink", 0xC83D79));
    public static final InkColor green = register(new InkColor("green", 0x409D3B));

    public static final InkColor lightBlue = register(new InkColor("light_blue", 0x228cff));
    public static final InkColor turquoise = register(new InkColor("turquoise", 0x048188));
    public static final InkColor yellow = register(new InkColor("yellow", 0xe1a307));
    public static final InkColor lilac = register(new InkColor("lilac", 0x4d24a3));
    public static final InkColor lemon = register(new InkColor("lemon", 0x91b00b));
    public static final InkColor plum = register(new InkColor("plum", 0x830b9c));

    public static final InkColor cyan = register(new InkColor("cyan", 0x4ACBCB));
    public static final InkColor peach = register(new InkColor("peach", 0xEA8546));
    public static final InkColor mint = register(new InkColor("mint", 0x08B672));
    public static final InkColor cherry = register(new InkColor("cherry", 0xE24F65));

    public static final InkColor neonPink = register(new InkColor("neon_pink", 0xcf0466));
    public static final InkColor neonGreen = register(new InkColor("neon_green", 0x17a80d));
    public static final InkColor neonOrange = register(new InkColor("neon_orange", 0xe85407));
    public static final InkColor neonBlue = register(new InkColor("neon_blue", 0x2e0cb5));

    public static final InkColor squid = register(new InkColor("hero_yellow", 0xD3F526));
    public static final InkColor octo = register(new InkColor("octo_pink", 0xE51B5E));

    public static final InkColor mojang = register(new InkColor("mojang", 0xDF242F));
    public static final InkColor cobalt = register(new InkColor("cobalt", 0x005682));
    public static final InkColor ice = register(new InkColor("ice", 0x88ffc1));
    public static final InkColor floral = register(new InkColor("floral", 0xFF9BEE));
    public static final InkColor omniGreen = register(new InkColor("omni_green", 0x93E720));
    public static final InkColor mana = register(new InkColor("mana", 0xF33EF1));

    public static final InkColor colorLockA = register(new InkColor("color_lock_friendly", 0xDEA801));
    public static final InkColor colorLockB = register(new InkColor("color_lock_hostile", 0x4717A9));

    public static final InkColor dyeWhite = register(new InkColor("dye_white", 0xFAFAFA, DyeColor.WHITE));
    public static final InkColor dyeOrange = register(new InkColor("dye_orange", 16351261, DyeColor.ORANGE));
    public static final InkColor dyeMagenta = register(new InkColor("dye_magenta", 13061821, DyeColor.MAGENTA));
    public static final InkColor dyeLightBlue = register(new InkColor("dye_light_blue", 3847130, DyeColor.LIGHT_BLUE));
    public static final InkColor dyeYellow = register(new InkColor("dye_yellow", 16701501, DyeColor.YELLOW));
    public static final InkColor dyeLime = register(new InkColor("dye_lime", 8439583, DyeColor.LIME));
    public static final InkColor dyePink = register(new InkColor("dye_pink", 15961002, DyeColor.PINK));
    public static final InkColor dyeGray = register(new InkColor("dye_gray", 4673362, DyeColor.GRAY));
    public static final InkColor dyeLightGray = register(new InkColor("dye_light_gray", 10329495, DyeColor.LIGHT_GRAY));
    public static final InkColor dyeCyan = register(new InkColor("dye_cyan", 1481884, DyeColor.CYAN));
    public static final InkColor dyePurple = register(new InkColor("dye_purple", 8991416, DyeColor.PURPLE));
    public static final InkColor dyeBlue = register(new InkColor("dye_blue", 3949738, DyeColor.BLUE));
    public static final InkColor dyeBrown = register(new InkColor("dye_brown", 8606770, DyeColor.BROWN));
    public static final InkColor dyeGreen = register(new InkColor("dye_green", 6192150, DyeColor.GREEN));
    public static final InkColor dyeRed = register(new InkColor("dye_red", 11546150, DyeColor.RED));
    public static final InkColor dyeBlack = register(new InkColor("dye_black", 1908001, DyeColor.BLACK));

    public static final InkColor royalBlue = register(new InkColor("royal_blue", 0x525CF5));
    public static final InkColor mothGreen = register(new InkColor("moth_green", 0x425113));
    public static final InkColor lightGreen = register(new InkColor("light_green", 0x85E378));
    public static final InkColor purple = register(new InkColor("purple", 0x6C0676));
    public static final InkColor mustard = register(new InkColor("mustard", 0xCE8003));
    public static final InkColor lumigreen = register(new InkColor("lumigreen", 0x60AB43));
    public static final InkColor darkBlue = register(new InkColor("dark_blue", 0x0D195E));
    public static final InkColor soda = register(new InkColor("soda", 0x65B799));
    public static final InkColor deepBlue = register(new InkColor("deep_blue", 0x0D37C3));
    public static final InkColor fuchsia = register(new InkColor("fuchsia", 0xE532D6));
    public static final InkColor winterGreen = register(new InkColor("winter_green", 0x4DE29D));
    public static final InkColor pumpkin = register(new InkColor("pumpkin", 0xDD6900));
    public static final InkColor redwood = register(new InkColor("redwood", 0x5B342E));

    public static final InkColor undyed = register(new InkColor("default", ColorUtils.DEFAULT));

    public static final class SimpleInkColorRegistry implements Iterable<InkColor> {
        private final LinkedHashMap<ResourceLocation, InkColor> values = new LinkedHashMap<>();

        private InkColor register(InkColor color) {
            ResourceLocation key = new ResourceLocation(Splatcraft.MODID, color.getName());
            color.setRegistryName(key);
            values.put(key, color);
            return color;
        }

        public InkColor getValue(ResourceLocation key) {
            return values.get(key);
        }

        public boolean containsKey(ResourceLocation key) {
            return values.containsKey(key);
        }

        public Collection<ResourceLocation> getKeys() {
            return values.keySet();
        }

        public Collection<InkColor> getValues() {
            return values.values();
        }

        @Override
        public Iterator<InkColor> iterator() {
            return values.values().iterator();
        }
    }
}
