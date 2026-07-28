package com.ae2powertools.mixin.interfaceterminal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.networking.security.IActionHost;
import appeng.container.AEBaseContainer;


@Mixin(value = AEBaseContainer.class, remap = false)
public interface MixinAEBaseContainerAccessor {

    @Invoker("getActionHost")
    IActionHost ae2powertools$invokeGetActionHost();

    @Invoker("updateHeld")
    void ae2powertools$invokeUpdateHeld(EntityPlayerMP player);
}