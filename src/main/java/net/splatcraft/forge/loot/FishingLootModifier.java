package net.splatcraft.forge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import net.minecraft.advancements.critereon.FishingHookPredicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

public class FishingLootModifier extends LootModifier
{
    protected final Item item;
    protected final int countMin;
    protected final int countMax;
    protected final float chance;
    protected final int quality;
    protected final boolean isTreasure;

    public static final Codec<FishingLootModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).and(
            inst.group(
            ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(FishingLootModifier::getItem),
            Codec.INT.fieldOf("countMin").forGetter(FishingLootModifier::getCountMin),
            Codec.INT.fieldOf("countMax").forGetter(FishingLootModifier::getCountMax),
            Codec.FLOAT.fieldOf("chance").forGetter(FishingLootModifier::getChance),
            Codec.INT.fieldOf("quality").forGetter(FishingLootModifier::getQuality),
            Codec.BOOL.fieldOf("isTreasure").forGetter(FishingLootModifier::isTreasure)
                    )).apply(inst, FishingLootModifier::new));

    /**
     * Constructs a LootModifier.
     *
     * @param conditionsIn the ILootConditions that need to be matched before the loot is modified.
     */
    protected FishingLootModifier(LootItemCondition[] conditionsIn, Item itemIn, int countMin, int countMax, float chance, int quality, boolean isTreasure)
    {
        super(conditionsIn);
        item = itemIn;
        this.countMin = countMin;
        this.countMax = countMax;
        this.chance = chance;
        this.quality = quality;
        this.isTreasure = isTreasure;
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

    public int getQuality() {
        return quality;
    }

    public boolean isTreasure() {
        return isTreasure;
    }


    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof FishingHook) || isTreasure &&
                !FishingHookPredicate.inOpenWater(true).matches(Objects.requireNonNull(context.getParamOrNull(LootContextParams.THIS_ENTITY)), context.getLevel(), context.getParamOrNull(LootContextParams.ORIGIN)))
        {
            return generatedLoot;
        }

        float chanceMod = 0;
        if (context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof LivingEntity entity)
        {
            ItemStack stack = entity.getUseItem();
            int fishingLuck = EnchantmentHelper.getFishingLuckBonus(stack);
            float luck = entity instanceof Player ? ((Player) entity).getLuck() : 0;

            if (isTreasure)
            {
                chanceMod += fishingLuck;
            }
            chanceMod += luck;

            chanceMod *= quality * (chance / 2);
        }

        if (context.getRandom().nextInt(100) <= (chance + chanceMod) * 100)
        {
            if (generatedLoot.size() <= 1)
            {
                generatedLoot.clear();
            }
            generatedLoot.add(new ItemStack(item, (countMax - countMin <= 0 ? 0 : context.getRandom().nextInt(countMax - countMin)) + countMin));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
