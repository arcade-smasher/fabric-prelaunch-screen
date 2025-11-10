package com.arcadesmasher.prelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.arcadesmasher.prelaunch.PreLaunch;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntryInfo;

@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {
    // For Minecraft versions 1.20.5 and above
    @Inject(
        method = "Lnet/minecraft/registry/MutableRegistry;add(Lnet/minecraft/registry/RegistryKey;Ljava/lang/Object;Lnet/minecraft/registry/entry/RegistryEntryInfo;)Lnet/minecraft/registry/entry/RegistryEntry$Reference;",
        at = @At("HEAD"),
        require = 0
    )
    private void onAdd(RegistryKey<T> key, T value, RegistryEntryInfo entryInfo, CallbackInfoReturnable<T> cir) {
        if (!PreLaunch.running) return;
        PreLaunch.detailedStatus.update("Registering " + key.getValue());
    }
}