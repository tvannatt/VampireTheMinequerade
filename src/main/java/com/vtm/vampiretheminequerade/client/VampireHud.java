package com.vtm.vampiretheminequerade.client;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.data.VampireData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Optional;

import static com.vtm.vampiretheminequerade.registry.ModAttachments.VAMPIRE_DATA;

@EventBusSubscriber (
        modid = "vampiretheminequerade",
        value = Dist.CLIENT
)

public final class VampireHud {

    @SubscribeEvent
    public static void bloodBarHandler(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                            ResourceLocation.fromNamespaceAndPath(VampireTheMinequerade.MODID, "blood_bar"),
                            VampireHud::renderBloodBar);
    }

    @SubscribeEvent
    public static void foodBarHandler(RenderGuiLayerEvent.Pre event) {
        /* Hide the food bar. */
        if (!event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) { return; }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getExistingData(VAMPIRE_DATA).isEmpty()) { return;}

        event.setCanceled(true);
    }

    private static void renderBloodBar(GuiGraphics graphics, DeltaTracker dt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) { return; }

        Optional<VampireData> vData = player.getExistingData(VAMPIRE_DATA);
        if (vData.isEmpty()) { return; }

        int rWidth = graphics.guiWidth() / 2 + 91;
        int lWidth = rWidth - 72;
        int hTop = graphics.guiHeight() - 38;
        int filled = 70 * vData.get().blood() / VampireData.BLOOD_MAX;

        graphics.fill(lWidth, hTop, rWidth, hTop + 5, 0xFF0E0405); //Black-red
        graphics.fill(lWidth + 1, hTop + 1, rWidth - 1, hTop +4, 0xFF2D1013); // Dark red-brown
        graphics.fill(lWidth + 1, hTop + 1, lWidth + 1 + filled, hTop + 4, 0xFFC4161C) ; // Red
    }
}


