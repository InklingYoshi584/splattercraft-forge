package net.splatcraft.forge.items.weapons;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.splatcraft.forge.data.capabilities.playerinfo.PlayerInfoCapability;
import net.splatcraft.forge.network.SplatcraftPacketHandler;
import net.splatcraft.forge.network.s2c.UpdatePlayerInfoPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpecialWeaponItem extends Item
{
    private final int pointsRequired;

    public SpecialWeaponItem()
    {
        this(60);
    }

    public SpecialWeaponItem(int pointsRequired)
    {
        super(new Properties().stacksTo(1));
        this.pointsRequired = pointsRequired;
    }

    public int getPointsRequired(ItemStack stack)
    {
        return pointsRequired;
    }

    public int getWindupTicks(ItemStack stack)
    {
        return 0;
    }

    public int getActiveTicks(ItemStack stack)
    {
        return 0;
    }

    public boolean canUseSpecial(Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return WeaponBaseItem.getSpecialPoints(mainWeapon) >= getPointsRequired(specialStack)
                && player != null
                && PlayerInfoCapability.hasCapability(player)
                && !PlayerInfoCapability.get(player).hasActiveSpecial();
    }

    public boolean useSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        WeaponBaseItem.setSpecialPoints(mainWeapon, 0);
        player.displayClientMessage(Component.translatable("status.special.dummy"), false);
        return true;
    }

    public void onSpecialWindupTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int windupTicksRemaining)
    {
    }

    public void onSpecialActiveTick(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, int activeTicksRemaining)
    {
    }

    public void onSpecialEnd(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon, boolean interrupted)
    {
    }

    public boolean shouldInterruptActiveSpecial(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return false;
    }

    public boolean grantsInfiniteInk(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return false;
    }

    public boolean replacesMainWeapon(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return false;
    }

    public ItemStack getMainWeaponReplacementRenderStack(Level level, Player player, ItemStack specialStack, ItemStack mainWeapon)
    {
        return specialStack;
    }

    public void onMainWeaponUseTick(Level level, LivingEntity entity, ItemStack specialStack, ItemStack mainWeapon, int timeLeft)
    {
    }

    protected void appendSpecialTooltip(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.splatcraft.dummy_special.tooltip"));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        appendSpecialTooltip(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.splatcraft.special.points", getPointsRequired(stack)));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack)
    {
        return 40;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @NotNull
    @Override
    public ItemStack finishUsingItem(@NotNull ItemStack specialStack, @NotNull Level level, @NotNull LivingEntity entity)
    {
        if (!(entity instanceof Player player) || level.isClientSide)
            return specialStack;

        SpecialWeaponItem specialWeapon = (SpecialWeaponItem) specialStack.getItem();
        ItemStack mainWeapon = findMainWeapon(player);

        if (mainWeapon.isEmpty())
        {
            player.displayClientMessage(Component.translatable("status.special.no_main_weapon"), true);
            return specialStack;
        }

        WeaponBaseItem.setStoredSpecialWeapon(mainWeapon, specialStack);
        WeaponBaseItem.setSpecialPoints(mainWeapon, specialWeapon.getPointsRequired(specialStack));
        specialWeapon.useSpecial(level, player, specialStack, mainWeapon);

        if (player instanceof ServerPlayer serverPlayer && PlayerInfoCapability.hasCapability(player))
            SplatcraftPacketHandler.sendToTrackersAndSelf(new UpdatePlayerInfoPacket(serverPlayer), serverPlayer);

        return specialStack;
    }

    private static ItemStack findMainWeapon(Player player)
    {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof WeaponBaseItem<?>)
                return stack;
        }
        return ItemStack.EMPTY;
    }
}
