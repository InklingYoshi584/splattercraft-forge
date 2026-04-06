package net.splatcraft.forge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

public class ChestLootModifier extends LootModifier
{
    protected final Item item;
    protected final int countMin;
    protected final int countMax;
    protected final float chance;
    protected final ResourceLocation parentTable;

    public static final Codec<ChestLootModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).and(inst.group(
            ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(ChestLootModifier::getItem),
            Codec.INT.fieldOf("countMin").forGetter(ChestLootModifier::getCountMin),
            Codec.INT.fieldOf("countMax").forGetter(ChestLootModifier::getCountMax),
            Codec.FLOAT.fieldOf("chance").forGetter(ChestLootModifier::getChance),
            ResourceLocation.CODEC.fieldOf("parent").forGetter(ChestLootModifier::getParentTable)
    )).apply(inst, ChestLootModifier::new));

    /**
     * Constructs a LootModifier.
     *
     * @param conditionsIn the ILootConditions that need to be matched before the loot is modified.
     */
    protected ChestLootModifier(LootItemCondition[] conditionsIn, Item itemIn, int countMin, int countMax, float chance, ResourceLocation parentTable)
    {
        super(conditionsIn);
        item = itemIn;
        this.countMin = countMin;
        this.countMax = countMax;
        this.chance = chance;
        this.parentTable = parentTable;
    }

    public Item getItem() {
        return item;
    }

    public int getCountMin() {
        return countMin;
    }

    public int getCountMax() {
        return countMax;
    }

    public float getChance() {
        return chance;
    }

    public ResourceLocation getParentTable() {
        return parentTable;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        if (!context.getQueriedLootTableId().equals(parentTable))
        {
            return generatedLoot;
        }

        float c = context.getRandom().nextFloat();

        if (c <= chance)
        {
            generatedLoot.add(new ItemStack(item, (countMax - countMin <= 0 ? 0 : context.getRandom().nextInt(countMax - countMin)) + countMin));
        }

        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
