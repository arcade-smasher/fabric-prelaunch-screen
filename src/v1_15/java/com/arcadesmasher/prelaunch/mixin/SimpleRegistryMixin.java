package com.arcadesmasher.prelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.arcadesmasher.prelaunch.PreLaunch;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.SimpleRegistry;

@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {
    // For Minecraft versions 1.14-1.15.2
    @Inject(
        method = "Lnet/minecraft/util/registry/MutableRegistry;add(Lnet/minecraft/util/Identifier;Ljava/lang/Object;)Ljava/lang/Object;",
        at = @At("HEAD"),
        require = 0
    )
    private void onAdd(Identifier id, T value, CallbackInfoReturnable<T> cir) {
        if (!PreLaunch.resourcesInitialized) return;
        PreLaunch.detailedStatus.update("Registering " + id);
    }
}