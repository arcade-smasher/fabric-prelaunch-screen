package com.arcadesmasher.prelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.arcadesmasher.prelaunch.PreLaunch;
import com.mojang.serialization.Lifecycle;

import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.SimpleRegistry;

@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {
    // For Minecraft versions 1.18.2-1.19.2
    @Inject(
        method = "Lnet/minecraft/util/registry/MutableRegistry;add(Lnet/minecraft/util/registry/RegistryKey;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/util/registry/RegistryEntry;",
        at = @At("HEAD"),
        require = 0
    )
    private void onAdd(RegistryKey<T> key, T value, Lifecycle lifecycle, CallbackInfoReturnable<T> cir) {
        if (!PreLaunch.resourcesInitialized) return;
        PreLaunch.detailedStatus.update("Registering " + key.getValue());
    }
}