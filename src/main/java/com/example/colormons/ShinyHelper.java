package com.example.colormons;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** The Shiny finish swaps in the _shiny texture when one exists. */
public class ShinyHelper {
    public static ResourceLocation shinyVariant(ResourceLocation base) {
        if (base == null) return null;
        String path = base.getPath();
        if (path.endsWith("_shiny.png") || !path.endsWith(".png")) return base;
        String shinyPath = path.substring(0, path.length() - 4) + "_shiny.png";
        ResourceLocation shiny = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), shinyPath);
        return Minecraft.getInstance().getResourceManager().getResource(shiny).isPresent() ? shiny : base;
    }
}
