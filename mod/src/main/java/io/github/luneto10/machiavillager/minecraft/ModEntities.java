package io.github.luneto10.machiavillager.minecraft;

import io.github.luneto10.machiavillager.Machiavillager;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    private static final ResourceKey<EntityType<?>> MACHIAVILLAGER_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Machiavillager.MOD_ID, "machiavillager"));

    public static final EntityType<MachiavillagerEntity> MACHIAVILLAGER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            MACHIAVILLAGER_KEY,
            EntityType.Builder.of(MachiavillagerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .build(MACHIAVILLAGER_KEY));

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(MACHIAVILLAGER, MachiavillagerEntity.createAttributes());
    }
}
