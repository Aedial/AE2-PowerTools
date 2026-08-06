package com.ae2powertools.features.crafter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.PowerToolsCreativeTab;
import com.ae2powertools.Tags;


/**
 * The AE2 AutoCrafter block.
 * Automatically crafts items using patterns with support for reusable/catalyst items.
 */
public class BlockAutoCrafter extends Block {

    public static final String NAME = "auto_crafter";

    /**
     * Block property for the upgrade tier (0 = no upgrade, 1-4 = tier I-IV).
     * This determines which model variant to render.
     */
    public static final PropertyInteger TIER = PropertyInteger.create("tier", 0, 4);

    public BlockAutoCrafter() {
        super(Material.IRON);
        setRegistryName(Tags.MODID, NAME);
        setTranslationKey(Tags.MODID + "." + NAME);
        setCreativeTab(PowerToolsCreativeTab.instance);
        setHardness(2.0F);
        setResistance(10.0F);
        setDefaultState(blockState.getBaseState().withProperty(TIER, 0));
    }

    @Override
    @Nonnull
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TIER);
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        // Tier is stored in TileEntity, not in metadata
        return 0;
    }

    @Override
    @Nonnull
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    @Nonnull
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAutoCrafter) {
            int tier = ((TileAutoCrafter) te).getUpgradeTier();
            return state.withProperty(TIER, tier);
        }

        return state;
    }

    // === Render Layer Configuration for Transparency ===

    @Override
    @Nonnull
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        // Primary layer for item rendering - CUTOUT_MIPPED for binary transparency
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean canRenderInLayer(@Nonnull IBlockState state, @Nonnull BlockRenderLayer layer) {
        // Render in both CUTOUT_MIPPED (binary transparency for frame) and TRANSLUCENT (alpha blending for colored overlay)
        return layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    public boolean isOpaqueCube(@Nonnull IBlockState state) {
        // Required for transparency - tells Minecraft this block has transparent parts
        return false;
    }

    @Override
    public boolean isFullCube(@Nonnull IBlockState state) {
        // The block is visually a full cube, but has transparent textures
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
            @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add(TextFormatting.AQUA + I18n.format("tile.ae2powertools.auto_crafter.tooltip"));
        tooltip.add(TextFormatting.YELLOW + I18n.format("tile.ae2powertools.auto_crafter.tooltip2"));
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new TileAutoCrafter();
    }

    @Override
    public boolean onBlockActivated(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state,
                                    @Nonnull EntityPlayer player, @Nonnull EnumHand hand,
                                    @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileAutoCrafter)) return false;

        TileAutoCrafter crafter = (TileAutoCrafter) te;

        // Handle memory card interaction (save/restore settings and patterns)
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof IMemoryCard) {
            handleMemoryCard(world, player, heldItem, crafter);
            return true;
        }

        // Try to quick-insert a held crafting pattern into the first available slot
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof ICraftingPatternItem) {
            ICraftingPatternItem patternItem = (ICraftingPatternItem) heldItem.getItem();
            ICraftingPatternDetails details = patternItem.getPatternForItem(heldItem, world);

            // Only insert valid crafting patterns (not processing patterns)
            if (details != null && details.isCraftable()) {
                // Find the first empty entry slot
                List<CrafterEntry> entries = crafter.getEntries();
                for (int i = 0; i < entries.size(); i++) {
                    if (entries.get(i).isEmpty()) {
                        // Insert one pattern into the slot
                        ItemStack singlePattern = heldItem.splitStack(1);
                        crafter.simulatePattern(i, singlePattern);
                        return true;
                    }
                }
            }
            // Fall through to open GUI if pattern is invalid or crafter is full
        }

        if (!heldItem.isEmpty() && crafter.tryQuickInsertCatalyst(heldItem)) {
            return true;
        }

        player.openGui(AE2PowerTools.instance, CrafterGuiHandler.GUI_CRAFTER, world,
                pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    // ==================== Memory Card ====================

    private void handleMemoryCard(World world, EntityPlayer player, ItemStack memCardStack, TileAutoCrafter crafter) {
        IMemoryCard memoryCard = (IMemoryCard) memCardStack.getItem();
        String name = getTranslationKey();

        if (player.isSneaking()) {
            saveToMemoryCard(player, memCardStack, memoryCard, name, crafter);
        } else {
            loadFromMemoryCard(world, player, memCardStack, memoryCard, name, crafter);
        }
    }

    /**
     * Saves speed, batch size, and all patterns to the memory card.
     */
    private void saveToMemoryCard(EntityPlayer player, ItemStack memCardStack, IMemoryCard memoryCard,
                                   String name, TileAutoCrafter crafter) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("speed", crafter.getSpeedTicks());
        data.setInteger("batch", crafter.getBatchSize());

        // Save pattern NBT data for each entry that has a pattern
        NBTTagList patternList = new NBTTagList();
        int patternCount = 0;

        for (CrafterEntry entry : crafter.getEntries()) {
            NBTTagCompound entryTag = new NBTTagCompound();

            if (entry.hasPattern()) {
                ItemStack pattern = entry.getPatternStack();
                if (pattern != null && !pattern.isEmpty() && pattern.hasTagCompound()) {
                    entryTag.setTag("patternNBT", pattern.getTagCompound().copy());
                    patternCount++;
                }
            }

            patternList.appendTag(entryTag);
        }

        data.setTag("patterns", patternList);

        // Add tooltip so the memory card shows what it contains
        data.setString("tooltip", "tile.ae2powertools.auto_crafter.memory_card.tooltip");

        memoryCard.setMemoryCardContents(memCardStack, name, data);
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);

        // Tell the player explicitly that patterns were saved
        player.sendMessage(new TextComponentTranslation(
                "tile.ae2powertools.auto_crafter.memory_card.saved", patternCount));
    }

    /**
     * Restores speed, batch size, and patterns from the memory card.
     * Patterns are encoded onto blank patterns found in the player's inventory.
     */
    private void loadFromMemoryCard(World world, EntityPlayer player, ItemStack memCardStack, IMemoryCard memoryCard,
                                     String name, TileAutoCrafter crafter) {
        String savedName = memoryCard.getSettingsName(memCardStack);
        NBTTagCompound data = memoryCard.getData(memCardStack);

        if (!name.equals(savedName)) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }

        // Restore speed and batch size
        if (data.hasKey("speed")) crafter.setSpeedTicks(data.getInteger("speed"));
        if (data.hasKey("batch")) crafter.setBatchSize(data.getInteger("batch"));

        // Restore patterns
        if (!data.hasKey("patterns")) {
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            return;
        }

        NBTTagList patternList = data.getTagList("patterns", 10);
        List<CrafterEntry> entries = crafter.getEntries();

        // Collect all pattern NBTs that need to be encoded
        List<NBTTagCompound> patternsToEncode = new ArrayList<>();
        for (int i = 0; i < patternList.tagCount(); i++) {
            NBTTagCompound entryTag = patternList.getCompoundTagAt(i);
            if (entryTag.hasKey("patternNBT")) {
                patternsToEncode.add(entryTag.getCompoundTag("patternNBT"));
            }
        }

        if (patternsToEncode.isEmpty()) {
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            return;
        }

        // Count available free slots in the crafter
        int freeSlots = 0;
        for (CrafterEntry entry : entries) {
            if (entry.isEmpty()) freeSlots++;
        }

        // Count available blank patterns in the player's inventory
        int blankPatternsAvailable = countBlankPatterns(player);

        // Determine how many patterns we can actually encode
        int canEncode = Math.min(patternsToEncode.size(), Math.min(freeSlots, blankPatternsAvailable));
        int encoded = 0;

        Optional<ItemStack> encodedPatternTemplate = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
        if (!encodedPatternTemplate.isPresent()) {
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            return;
        }

        // Encode patterns into blank patterns and insert into free slots
        int entryIndex = 0;
        for (int i = 0; i < canEncode; i++) {
            // Find the next free slot
            while (entryIndex < entries.size() && !entries.get(entryIndex).isEmpty()) {
                entryIndex++;
            }
            if (entryIndex >= entries.size()) break;

            // Consume a blank pattern from the player's inventory
            if (!consumeBlankPattern(player)) break;

            // Create the encoded pattern with the saved NBT
            ItemStack newPattern = encodedPatternTemplate.get().copy();
            newPattern.setTagCompound(patternsToEncode.get(i).copy());

            // Insert and simulate the pattern
            crafter.simulatePattern(entryIndex, newPattern);
            encoded++;
            entryIndex++;
        }

        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);

        int notEncoded = patternsToEncode.size() - encoded;

        // Report what happened to the player
        if (notEncoded > 0) {
            int missingBlanks = Math.max(0, patternsToEncode.size() - blankPatternsAvailable);
            int missingSlots = Math.max(0, patternsToEncode.size() - freeSlots);

            if (encoded > 0) {
                player.sendMessage(new TextComponentTranslation(
                        "tile.ae2powertools.auto_crafter.memory_card.partial", encoded, notEncoded));
            }

            if (missingBlanks > 0) {
                player.sendMessage(new TextComponentTranslation(
                        "tile.ae2powertools.auto_crafter.memory_card.no_blanks", missingBlanks));
            }

            if (missingSlots > 0) {
                player.sendMessage(new TextComponentTranslation(
                        "tile.ae2powertools.auto_crafter.memory_card.no_slots", missingSlots));
            }
        } else if (encoded > 0) {
            player.sendMessage(new TextComponentTranslation(
                    "tile.ae2powertools.auto_crafter.memory_card.restored", encoded));
        }
    }

    /**
     * Counts the total number of blank patterns in the player's inventory.
     */
    private int countBlankPatterns(EntityPlayer player) {
        IItemDefinition blankDef = AEApi.instance().definitions().materials().blankPattern();

        int count = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && blankDef.isSameAs(stack)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    /**
     * Consumes one blank pattern from the player's inventory.
     * @return true if a blank pattern was consumed, false if none were found
     */
    private boolean consumeBlankPattern(EntityPlayer player) {
        IItemDefinition blankDef = AEApi.instance().definitions().materials().blankPattern();

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && blankDef.isSameAs(stack)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public void breakBlock(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntity te = world.getTileEntity(pos);

        if (te instanceof TileAutoCrafter) {
            TileAutoCrafter crafter = (TileAutoCrafter) te;

            // Drop patterns and catalyst items
            for (CrafterEntry entry : crafter.getEntries()) {
                if (entry.hasPattern()) {
                    ItemStack pattern = entry.getPatternStack();
                    if (pattern != null && !pattern.isEmpty()) spawnAsEntity(world, pos, pattern);
                }

                for (int i = 0; i < CrafterEntry.CATALYST_SLOTS; i++) {
                    ItemStack catalyst = entry.getCatalystStack(i);
                    if (!catalyst.isEmpty()) spawnAsEntity(world, pos, catalyst);
                }

                // Drop pending outputs
                for (IAEItemStack pending : entry.getPendingOutputs()) {
                    if (pending != null && pending.getStackSize() > 0) {
                        // Spawn the pending output as large stacks to avoid excessive entity counts when breaking
                        ItemStack pendingStack = pending.createItemStack();
                        spawnAsEntity(world, pos, pendingStack);
                    }
                }
            }

            // Drop upgrade items
            for (int i = 0; i < TileAutoCrafter.UPGRADE_SLOTS; i++) {
                ItemStack upgrade = crafter.getUpgradeStack(i);
                if (!upgrade.isEmpty()) spawnAsEntity(world, pos, upgrade);
            }
        }

        super.breakBlock(world, pos, state);
    }
}
