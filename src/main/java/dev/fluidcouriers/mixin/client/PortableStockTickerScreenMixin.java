package dev.fluidcouriers.mixin.client;

import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.render.FluidSlotAmountRenderer;
import com.yision.fluidlogistics.render.FluidSlotRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKeyClientGuiHandler;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.BigGenericStack;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = PortableStockTickerScreen.class, remap = false)
public class PortableStockTickerScreenMixin {

    @Unique
    private FluidStack fluidcouriers$currentFluid = FluidStack.EMPTY;

    @Unique
    private int fluidcouriers$currentFluidAmount = 0;

    @Inject(
            method = "renderItemEntry(" +
                    "Lnet/minecraft/client/gui/GuiGraphics;" +
                    "F" +
                    "Lru/zznty/create_factory_abstractions/generic/support/BigGenericStack;" +
                    "ZZ)V",
            at = @At("HEAD"),
            remap = false
    )
    private void fluidcouriers$detectFluidEntry(
            GuiGraphics guiGraphics,
            float partialTick,
            BigGenericStack bigGenericStack,
            boolean isHovered,
            boolean showOrder,
            CallbackInfo ci
    ) {
        fluidcouriers$currentFluid = FluidStack.EMPTY;
        fluidcouriers$currentFluidAmount = 0;

        GenericKey key = bigGenericStack.get().key();
        if (!(key instanceof ItemKey itemKey)) return;

        ItemStack stack = itemKey.stack();
        if (!CompressedTankItem.isVirtual(stack)) return;

        FluidStack fluid = CompressedTankItem.getFluid(stack);
        if (fluid.isEmpty()) return;

        fluidcouriers$currentFluid = fluid;
        fluidcouriers$currentFluidAmount = bigGenericStack.get().amount();
    }

    @Redirect(
            method = "renderItemEntry(" +
                    "Lnet/minecraft/client/gui/GuiGraphics;" +
                    "F" +
                    "Lru/zznty/create_factory_abstractions/generic/support/BigGenericStack;" +
                    "ZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lru/zznty/create_factory_abstractions/api/generic/key/" +
                             "GenericKeyClientGuiHandler;renderSlot(" +
                             "Lnet/minecraft/client/gui/GuiGraphics;" +
                             "Lru/zznty/create_factory_abstractions/api/generic/key/GenericKey;" +
                             "II)V"
            ),
            remap = false
    )
    private void fluidcouriers$redirectRenderSlot(
            GenericKeyClientGuiHandler handler,
            GuiGraphics graphics,
            GenericKey key,
            int x,
            int y
    ) {
        if (!fluidcouriers$currentFluid.isEmpty()) {
            FluidSlotRenderer.renderFluidSlot(graphics, x, y, fluidcouriers$currentFluid);
        } else {
            handler.renderSlot(graphics, key, x, y);
        }
    }

    @Redirect(
            method = "renderItemEntry(" +
                    "Lnet/minecraft/client/gui/GuiGraphics;" +
                    "F" +
                    "Lru/zznty/create_factory_abstractions/generic/support/BigGenericStack;" +
                    "ZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lru/zznty/create_factory_abstractions/api/generic/key/" +
                             "GenericKeyClientGuiHandler;renderDecorations(" +
                             "Lnet/minecraft/client/gui/GuiGraphics;" +
                             "Lru/zznty/create_factory_abstractions/api/generic/key/GenericKey;" +
                             "III)V"
            ),
            remap = false
    )
    private void fluidcouriers$redirectRenderDecorations(
            GenericKeyClientGuiHandler handler,
            GuiGraphics graphics,
            GenericKey key,
            int amount,
            int x,
            int y
    ) {
        if (!fluidcouriers$currentFluid.isEmpty()) {
            if (fluidcouriers$currentFluidAmount > 1) {
                FluidSlotAmountRenderer.render(graphics, fluidcouriers$currentFluidAmount);
            }
        } else {
            handler.renderDecorations(graphics, key, amount, x, y);
        }
    }

    @Redirect(
            method = "renderForeground(" +
                    "Lnet/minecraft/client/gui/GuiGraphics;" +
                    "II" +
                    "F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lru/zznty/create_factory_abstractions/api/generic/key/" +
                             "GenericKeyClientGuiHandler;tooltipBuilder(" +
                             "Lru/zznty/create_factory_abstractions/api/generic/key/GenericKey;" +
                             "I)Ljava/util/List;"
            ),
           remap = false,
           require = 0
    )
    private List<Component> fluidcouriers$redirectTooltipBuilder(
            GenericKeyClientGuiHandler handler,
            GenericKey key,
            int amount
    ) {
                if (key instanceof ItemKey itemKey) {
            ItemStack stack = itemKey.stack();
                        if (CompressedTankItem.isVirtual(stack)) {
                FluidStack fluid = CompressedTankItem.getFluid(stack);
                if (!fluid.isEmpty()) {
                                        List<Component> tooltip = new ArrayList<>();
                                        tooltip.add(fluid.getDisplayName());
                                        tooltip.add(Component.literal(fluid.getAmount() + "mB").withStyle(ChatFormatting.GRAY));
                    return tooltip;
                }
            }
        }
        return handler.tooltipBuilder(key, amount);
    }
}
