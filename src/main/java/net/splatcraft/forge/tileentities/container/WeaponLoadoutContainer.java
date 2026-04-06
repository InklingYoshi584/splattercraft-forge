package net.splatcraft.forge.tileentities.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.splatcraft.forge.items.weapons.SpecialWeaponItem;
import net.splatcraft.forge.items.weapons.SubWeaponItem;
import net.splatcraft.forge.items.weapons.WeaponBaseItem;
import net.splatcraft.forge.registries.SplatcraftTileEntities;

public class WeaponLoadoutContainer extends AbstractContainerMenu
{
    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer loadout;

    public WeaponLoadoutContainer(int id, Inventory playerInventory, FriendlyByteBuf buffer)
    {
        this(id, playerInventory, buffer.readEnum(InteractionHand.class));
    }

    public WeaponLoadoutContainer(int id, Inventory playerInventory, InteractionHand hand)
    {
        super(SplatcraftTileEntities.weaponLoadoutContainer.get(), id);
        this.player = playerInventory.player;
        this.hand = hand;
        this.loadout = new SimpleContainer(2)
        {
            @Override
            public void setChanged()
            {
                super.setChanged();
                saveToWeapon();
            }
        };

        ItemStack weaponStack = getWeaponStack();
        this.loadout.setItem(0, WeaponBaseItem.getStoredSubWeapon(weaponStack));
        this.loadout.setItem(1, WeaponBaseItem.getStoredSpecialWeapon(weaponStack));

        addSlot(new Slot(loadout, 0, 44, 20)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return stack.getItem() instanceof SubWeaponItem && !SubWeaponItem.singleUse(stack);
            }

            @Override
            public int getMaxStackSize()
            {
                return 1;
            }
        });
        addSlot(new Slot(loadout, 1, 116, 20)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return stack.getItem() instanceof SpecialWeaponItem;
            }

            @Override
            public int getMaxStackSize()
            {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    public static MenuProvider getMenuProvider(InteractionHand hand, Component title)
    {
        return new SimpleMenuProvider((id, inventory, player) -> new WeaponLoadoutContainer(id, inventory, hand), title);
    }

    private ItemStack getWeaponStack()
    {
        return player.getItemInHand(hand);
    }

    private void saveToWeapon()
    {
        ItemStack weaponStack = getWeaponStack();
        if (!(weaponStack.getItem() instanceof WeaponBaseItem<?>))
            return;

        WeaponBaseItem.setStoredSubWeapon(weaponStack, loadout.getItem(0));
        WeaponBaseItem.setStoredSpecialWeapon(weaponStack, loadout.getItem(1));
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player == this.player && getWeaponStack().getItem() instanceof WeaponBaseItem<?>;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < 2)
        {
            if (!moveItemStackTo(stack, 2, this.slots.size(), true))
                return ItemStack.EMPTY;
        }
        else if (stack.getItem() instanceof SubWeaponItem && !SubWeaponItem.singleUse(stack))
        {
            if (!moveItemStackTo(stack, 0, 1, false))
                return ItemStack.EMPTY;
        }
        else if (stack.getItem() instanceof SpecialWeaponItem)
        {
            if (!moveItemStackTo(stack, 1, 2, false))
                return ItemStack.EMPTY;
        }
        else
        {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty())
            slot.set(ItemStack.EMPTY);
        else
            slot.setChanged();

        loadout.setChanged();
        return original;
    }

    @Override
    public void removed(Player player)
    {
        saveToWeapon();
        super.removed(player);
    }
}
