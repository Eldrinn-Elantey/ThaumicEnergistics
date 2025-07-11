package thaumicenergistics.common.items;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;

import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.core.localization.GuiText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import thaumicenergistics.api.storage.IAspectStack;
import thaumicenergistics.client.textures.BlockTextureManager;
import thaumicenergistics.common.ThEGuiHandler;
import thaumicenergistics.common.ThaumicEnergistics;
import thaumicenergistics.common.inventory.HandlerItemEssentiaCell;
import thaumicenergistics.common.inventory.HandlerItemEssentiaCellCreative;
import thaumicenergistics.common.registries.ThEStrings;
import thaumicenergistics.common.storage.AspectStack;
import thaumicenergistics.common.storage.AspectStackComparator;
import thaumicenergistics.common.storage.EnumEssentiaStorageTypes;

/**
 * Stores essentia.
 *
 * @author Nividica
 *
 */
public class ItemEssentiaCell extends Item implements ICellHandler {

    /**
     * Status of the cell.
     */
    private static final int CELL_STATUS_MISSING = 0;

    /**
     * Icons for each type.
     */
    private IIcon[] icons;

    public ItemEssentiaCell() {
        // Add the handler to AE2
        AEApi.instance().registries().cell().addCellHandler(this);

        // Set max stack size to 1
        this.setMaxStackSize(1);

        // No damage
        this.setMaxDamage(0);

        // Has sub-types
        this.setHasSubtypes(true);
    }

    /**
     * Adds the contents of the cell to the description tooltip.
     *
     * @param cellHandler
     * @param displayList
     * @param player
     */
    @SuppressWarnings("unchecked")
    private void addContentsToCellDescription(final HandlerItemEssentiaCell cellHandler,
            @SuppressWarnings("rawtypes") final List displayList, final EntityPlayer player) {
        // Get the list of stored aspects
        List<IAspectStack> cellAspects = cellHandler.getStoredEssentia();

        // Sort the list
        Collections.sort(cellAspects, new AspectStackComparator());

        for (IAspectStack currentStack : cellAspects) {
            if (currentStack != null) {
                // Get the chat color
                String aspectChatColor = currentStack.getChatColor();

                // Build the display string
                String aspectInfo = String.format(
                        "  %s%s%s x %d",
                        aspectChatColor,
                        currentStack.getAspectName(player),
                        EnumChatFormatting.WHITE,
                        currentStack.getStackSize());

                // Add to the list
                displayList.add(aspectInfo);
            }
        }
    }

    /**
     * Adds the partitions of the cell to the description tooltip.
     *
     * @param cellHandler
     * @param player
     */
    private List<String> addPartitionsToCellDescription(final HandlerItemEssentiaCell cellHandler,
            final EntityPlayer player) {
        return cellHandler.getPartitionAspects().stream().filter(Objects::nonNull)
                .map(aspect -> new AspectStack(aspect, 1)).sorted(new AspectStackComparator())
                .map(aspect -> String.format("  %s%s", aspect.getChatColor(), aspect.getAspectName(player)))
                .collect(Collectors.toList());
    }

    /**
     * Creates the cell tooltip.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(final ItemStack essentiaCell, final EntityPlayer player,
            @SuppressWarnings("rawtypes") final List displayList, final boolean advancedItemTooltips) {
        // Get the contents of the cell
        IMEInventoryHandler<IAEFluidStack> handler = AEApi.instance().registries().cell()
                .getCellInventory(essentiaCell, null, StorageChannel.FLUIDS);

        // Ensure we have a cell inventory handler
        if (!(handler instanceof HandlerItemEssentiaCell)) {
            return;
        }

        // Cast to cell inventory handler
        HandlerItemEssentiaCell cellHandler = (HandlerItemEssentiaCell) handler;

        // Create the bytes tooltip
        displayList.add(
                EnumChatFormatting.WHITE + NumberFormat.getInstance(Locale.ENGLISH).format(cellHandler.getUsedBytes())
                        + EnumChatFormatting.GRAY
                        + " "
                        + GuiText.Of.getLocal()
                        + " "
                        + EnumChatFormatting.DARK_GREEN
                        + NumberFormat.getInstance(Locale.ENGLISH).format(cellHandler.getTotalBytes())
                        + " "
                        + EnumChatFormatting.GRAY
                        + ThEStrings.Tooltip_CellBytes.getLocalized());

        // Create the types tooltip
        displayList.add(
                EnumChatFormatting.WHITE + NumberFormat.getInstance(Locale.ENGLISH).format(cellHandler.getUsedTypes())
                        + EnumChatFormatting.GRAY
                        + " "
                        + GuiText.Of.getLocal()
                        + " "
                        + EnumChatFormatting.DARK_GREEN
                        + NumberFormat.getInstance(Locale.ENGLISH).format(cellHandler.getTotalTypes())
                        + " "
                        + EnumChatFormatting.GRAY
                        + ThEStrings.Tooltip_CellTypes.getLocalized());

        // Is the cell pre-formated?
        if (cellHandler.isPartitioned()) {
            displayList.add(GuiText.Partitioned.getLocal());

            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
                // Add information about the partitioned essentia types in the cell
                displayList.add(GuiText.Filter.getLocal() + ": ");
                displayList.addAll(addPartitionsToCellDescription(cellHandler, player));
            }
        }

        // Does the cell have anything stored?
        if (cellHandler.getUsedTypes() > 0) {
            // Is control being held?
            if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || (Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
                displayList.add(ThEStrings.Tooltip_CellContains.getLocalized() + ":");
                // Add information about the essentia types in the cell
                this.addContentsToCellDescription(cellHandler, displayList, player);
            } else {
                // Let the user know they can hold shift
                displayList.add(
                        EnumChatFormatting.WHITE + EnumChatFormatting.ITALIC.toString()
                                + ThEStrings.Tooltip_ItemStackDetails.getLocalized());
            }
        }
    }

    /**
     * How much power is required by the cell each tick.
     */
    @Override
    public double cellIdleDrain(final ItemStack itemStack, @SuppressWarnings("rawtypes") final IMEInventory handler) {
        return EnumEssentiaStorageTypes.fromIndex[itemStack.getItemDamage()].idleAEPowerDrain;
    }

    /**
     * Gets a handler for the cell.
     */
    @Override
    public IMEInventoryHandler<?> getCellInventory(final ItemStack essentiaCell, final ISaveProvider saveProvider,
            final StorageChannel channel) {
        // Ensure the channel is fluid and there is an appropriate item.
        if ((channel != StorageChannel.FLUIDS) || !(essentiaCell.getItem() instanceof ItemEssentiaCell)) {
            return null;
        }

        // Is the type creative?
        if (essentiaCell.getItemDamage() == EnumEssentiaStorageTypes.Type_Creative.index) {
            // Return a creative handler.
            return new HandlerItemEssentiaCellCreative(essentiaCell, saveProvider);
        }

        // Return a standard handler.
        return new HandlerItemEssentiaCell(essentiaCell, saveProvider);
    }

    /**
     * Gets the cell's icon.
     */
    @Override
    public IIcon getIconFromDamage(final int dmg) {
        // Clamp the index
        int index = MathHelper.clamp_int(dmg, 0, EnumEssentiaStorageTypes.fromIndex.length - 1);

        // Return the icon
        return this.icons[index];
    }

    /**
     * Gets the rarity of the cell.
     */
    @Override
    public EnumRarity getRarity(final ItemStack itemStack) {
        // Get the index based off of the meta data
        int index = MathHelper.clamp_int(itemStack.getItemDamage(), 0, EnumEssentiaStorageTypes.fromIndex.length - 1);

        // Return the rarity
        return EnumEssentiaStorageTypes.fromIndex[index].rarity;
    }

    /**
     * Gets the status of the cell. Full | Type Full | Has Room
     */
    @Override
    public int getStatusForCell(final ItemStack essentiaCell,
            @SuppressWarnings("rawtypes") final IMEInventory handler) {
        // Do we have a handler?
        if (handler == null) {
            return ItemEssentiaCell.CELL_STATUS_MISSING;
        }

        // Get the inventory handler
        return ((HandlerItemEssentiaCell) handler).getCellStatus();
    }

    /**
     * Gets the different cell sizes and places them on the creative tab.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void getSubItems(final Item item, final CreativeTabs creativeTab,
            @SuppressWarnings("rawtypes") final List listSubItems) {
        for (EnumEssentiaStorageTypes type : EnumEssentiaStorageTypes.fromIndex) {
            listSubItems.add(type.getCell());
        }
    }

    /**
     * ME Chest icon
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getTopTexture_Dark() {
        return BlockTextureManager.ESSENTIA_TERMINAL.getTextures()[0];
    }

    /**
     * ME Chest icon
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getTopTexture_Light() {
        return BlockTextureManager.ESSENTIA_TERMINAL.getTextures()[2];
    }

    /**
     * ME Chest icon
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getTopTexture_Medium() {
        return BlockTextureManager.ESSENTIA_TERMINAL.getTextures()[1];
    }

    @Override
    public String getUnlocalizedName() {
        return ThaumicEnergistics.MOD_ID + ".item.essentia.cell";
    }

    /**
     * Name of the cell.
     */
    @Override
    public String getUnlocalizedName(final ItemStack itemStack) {
        return EnumEssentiaStorageTypes.fromIndex[itemStack.getItemDamage()].cellName.getUnlocalized();
    }

    /**
     * True if the specified item is an Essentia cell.
     */
    @Override
    public boolean isCell(final ItemStack itemStack) {
        return itemStack.getItem() == this;
    }

    /**
     * Maximum storage, in bytes, the cell can hold.
     *
     * @param essentiaCell
     * @return
     */
    public long maxStorage(final ItemStack essentiaCell) {
        return EnumEssentiaStorageTypes.fromIndex[essentiaCell.getItemDamage()].capacity;
    }

    /**
     * The maximum number of types each cell can hold.
     *
     * @param essentiaCell
     * @return
     */
    public int maxTypes(final ItemStack essentiaCell) {
        return EnumEssentiaStorageTypes.fromIndex[essentiaCell.getItemDamage()].maxStoredTypes;
    }

    /**
     * Attempts to remove the storage component.
     */
    @Override
    public ItemStack onItemRightClick(final ItemStack essentiaCell, final World world, final EntityPlayer player) {
        // Ensure the player is sneaking(holding shift)
        if (!player.isSneaking()) {
            return essentiaCell;
        }

        // Ensure this is not a creative cell
        if (essentiaCell.getItemDamage() == EnumEssentiaStorageTypes.Type_Creative.index) {
            return essentiaCell;
        }
        if (essentiaCell.getItemDamage() == EnumEssentiaStorageTypes.Type_QUANTUM.index) {
            return essentiaCell;
        }
        if (essentiaCell.getItemDamage() == EnumEssentiaStorageTypes.Type_SINGULARITY.index) {
            return essentiaCell;
        }

        // Get the handler
        @SuppressWarnings("unchecked")
        IMEInventoryHandler<IAEFluidStack> handler = AEApi.instance().registries().cell()
                .getCellInventory(essentiaCell, null, StorageChannel.FLUIDS);

        // Is it the correct handler type?
        if (!(handler instanceof HandlerItemEssentiaCell)) {
            return essentiaCell;
        }

        // Cast
        HandlerItemEssentiaCell cellHandler = (HandlerItemEssentiaCell) handler;

        // If the cell is empty, and the player can hold the casing
        if ((cellHandler.getUsedBytes() == 0)
                && (player.inventory.addItemStackToInventory(ItemEnum.STORAGE_CASING.getStack()))) {
            // Return the storage component
            return EnumEssentiaStorageTypes.fromIndex[essentiaCell.getItemDamage()].getComponent(1);
        }

        // Can not remove storage component, return the current cell as is.
        return essentiaCell;
    }

    /**
     * Shows the cell GUI.
     */
    @Override
    public void openChestGui(final EntityPlayer player, final IChestOrDrive chest, final ICellHandler cellHandler,
            @SuppressWarnings("rawtypes") final IMEInventoryHandler inv, final ItemStack itemStack,
            final StorageChannel channel) {
        // Ensure this is the fluid channel
        if (channel != StorageChannel.FLUIDS) {
            return;
        }

        // Ensure we have a chest
        if (chest != null) {
            // Get a reference to the chest's inventories
            IStorageMonitorable monitorable = ((IMEChest) chest)
                    .getMonitorable(ForgeDirection.UNKNOWN, new PlayerSource(player, chest));

            // Ensure we got the inventories
            if (monitorable != null) {
                // Get the chest tile entity
                TileEntity chestEntity = (TileEntity) chest;

                // Show the terminal gui
                ThEGuiHandler.launchGui(
                        ThEGuiHandler.ESSENTIA_CELL_ID,
                        player,
                        chestEntity.getWorldObj(),
                        chestEntity.xCoord,
                        chestEntity.yCoord,
                        chestEntity.zCoord);
            }
        }
    }

    @Override
    public void registerIcons(final IIconRegister iconRegister) {
        // Create the icon array
        this.icons = new IIcon[EnumEssentiaStorageTypes.fromIndex.length];

        // Add each type
        for (int i = 0; i < this.icons.length; i++) {
            this.icons[i] = iconRegister.registerIcon(
                    ThaumicEnergistics.MOD_ID + ":essentia.cell." + EnumEssentiaStorageTypes.fromIndex[i].suffix);
        }
    }
}
