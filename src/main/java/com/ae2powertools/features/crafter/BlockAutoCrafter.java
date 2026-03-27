package com.ae2powertools.features.crafter;

import java.util.List;

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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.Tags;
import com.ae2powertools.items.ItemCardsDistributor;


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
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.REDSTONE);
        setHardness(2.0F);
        setResistance(10.0F);
        setDefaultState(blockState.getBaseState().withProperty(TIER, 0));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TIER);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        // Tier is stored in TileEntity, not in metadata
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAutoCrafter) {
            int tier = ((TileAutoCrafter) te).getUpgradeTier();
            return state.withProperty(TIER, tier);
        }

        return state;
    }

    // === Render Layer Configuration for Transparency ===

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        // Primary layer for item rendering - CUTOUT_MIPPED for binary transparency
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        // Render in both CUTOUT_MIPPED (binary transparency for frame) and TRANSLUCENT (alpha blending for colored overlay)
        return layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        // Required for transparency - tells Minecraft this block has transparent parts
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        // The block is visually a full cube, but has transparent textures
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add(TextFormatting.AQUA + I18n.format("tile.ae2powertools.auto_crafter.tooltip"));
        tooltip.add(TextFormatting.YELLOW + I18n.format("tile.ae2powertools.auto_crafter.tooltip2"));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAutoCrafter();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                     EntityPlayer player, EnumHand hand,
                                     EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAutoCrafter) {
            player.openGui(AE2PowerTools.instance, CrafterGuiHandler.GUI_CRAFTER, world,
                    pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
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
