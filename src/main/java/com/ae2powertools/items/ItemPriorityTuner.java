package com.ae2powertools.items;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.core.CreativeTab;
import appeng.helpers.IPriorityHost;

import com.ae2powertools.Tags;
import com.ae2powertools.features.tuner.GuiPriorityTuner;
import com.ae2powertools.network.PacketPriorityApplied;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Priority Tuner - allows setting priorities on AE2 storage blocks.
 * Shift-right click on a block to open the AE2 priority GUI.
 * Right click on a block to apply the stored priority.
 */
public class ItemPriorityTuner extends Item {

    private static final String NBT_PRIORITY = "storedPriority";

    public ItemPriorityTuner() {
        this.setRegistryName(Tags.MODID, "priority_tuner");
        this.setTranslationKey(Tags.MODID + ".priority_tuner");
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTab.instance);
    }

    /**
     * Get the stored priority from the tuner.
     */
    public int getStoredPriority(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_PRIORITY)) return 0;

        return nbt.getInteger(NBT_PRIORITY);
    }

    /**
     * Set the stored priority on the tuner.
     */
    public void setStoredPriority(ItemStack stack, int priority) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        nbt.setInteger(NBT_PRIORITY, priority);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        TileEntity te = world.getTileEntity(pos);
        IPriorityHost priorityHost = getPriorityHost(te, hitX, hitY, hitZ);
        if (priorityHost == null) return EnumActionResult.PASS;

        // On blocks, shift-click does nothing special (use shift-click in air for GUI)
        // Regular right click: apply stored priority
        if (player.isSneaking()) return EnumActionResult.PASS;

        if (world.isRemote) return EnumActionResult.PASS;

        // Server side: apply stored priority
        ItemStack stack = player.getHeldItem(hand);
        int storedPriority = getStoredPriority(stack);
        priorityHost.setPriority(storedPriority);
        te.markDirty();

        // Send highlight effect to client
        PowerToolsNetwork.INSTANCE.sendTo(
            new PacketPriorityApplied(pos, world.provider.getDimension(), storedPriority),
            (EntityPlayerMP) player
        );

        return EnumActionResult.SUCCESS;
    }

    /**
     * Get the IPriorityHost from the tile entity or from a part within a part host.
     */
    private IPriorityHost getPriorityHost(TileEntity te, float hitX, float hitY, float hitZ) {
        if (te == null) return null;

        // Check if this is a part host (cable bus) - parts like buses can have priority
        if (te instanceof IPartHost) {
            IPartHost partHost = (IPartHost) te;
            Vec3d hitVec = new Vec3d(hitX, hitY, hitZ);
            SelectedPart selectedPart = partHost.selectPart(hitVec);

            if (selectedPart.part instanceof IPriorityHost) return (IPriorityHost) selectedPart.part;
        }

        // Check if the tile entity itself is a priority host
        if (te instanceof IPriorityHost) return (IPriorityHost) te;

        return null;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        // Shift-right click in air: open Priority Tuner GUI to set stored priority
        if (player.isSneaking()) {
            // If tuner is in off-hand and main hand has an item, don't open GUI
            // (player is likely shift+right-clicking to place a block)
            if (hand == EnumHand.OFF_HAND && !player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()) {
                return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
            }

            if (world.isRemote) openGui(player, hand);

            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }

        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
    }

    @SideOnly(Side.CLIENT)
    private void openGui(EntityPlayer player, EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPriorityTuner(player.inventory, hand));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        int priority = getStoredPriority(stack);
        tooltip.add(TextFormatting.GOLD + I18n.format("item.ae2powertools.priority_tuner.stored",
            TextFormatting.WHITE.toString() + priority + TextFormatting.GOLD));
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.priority_tuner.tip1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.priority_tuner.tip2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.priority_tuner.tip3"));
    }
}
