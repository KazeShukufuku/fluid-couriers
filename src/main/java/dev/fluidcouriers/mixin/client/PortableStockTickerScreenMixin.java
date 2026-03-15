package dev.fluidcouriers.mixin.client;

import com.kreidev.cmpackagecouriers.stock_ticker.PortableStockTickerScreen;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.render.FluidSlotAmountRenderer;
import com.yision.fluidlogistics.render.FluidSlotRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
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

/**
 * Patches {@link PortableStockTickerScreen#renderItemEntry} so that
 * virtual {@link CompressedTankItem} stacks (which represent fluids from a
 * FluidPackager-linked inventory) are rendered as fluid icons rather than
 * Compressed Tank items.
 *
 * <p>Three things are patched inside {@code renderItemEntry}:
 * <ol>
 *   <li>{@link GenericKeyClientGuiHandler#renderSlot} – replaced with
 *       {@link FluidSlotRenderer#renderFluidSlot} for fluid stacks.</li>
 *   <li>{@link GenericKeyClientGuiHandler#renderDecorations} – replaced with
 *       {@link FluidSlotAmountRenderer#render} (shows mB amount).</li>
 *   <li>The HEAD injection caches the current FluidStack so that the two
 *       Redirects only need to call the helper once.</li>
 * </ol>
 *
 * <p>Additionally, {@code renderForeground}'s
 * {@link GenericKeyClientGuiHandler#tooltipBuilder} call is redirected so
 * that hovering a fluid slot shows the fluid name + amount instead of the
 * generic Compressed Tank tooltip.
 */
@Mixin(value = PortableStockTickerScreen.class, remap = false)
public class PortableStockTickerScreenMixin {

    /** Cached FluidStack for the item currently being rendered by renderItemEntry. */
    @Unique
    private FluidStack fluidcouriers$currentFluid = FluidStack.EMPTY;

    /** Cached amount (mB) for the currently rendered fluid, used by renderDecorations. */
    @Unique
    private int fluidcouriers$currentFluidAmount = 0;

    // -------------------------------------------------------------------------
    // HEAD: detect fluid BEFORE the rendering calls happen
    // -------------------------------------------------------------------------

    /**
     * Runs at the very start of every {@code renderItemEntry} call.
     * Inspects the {@link BigGenericStack} parameter; if it wraps a virtual
     * {@link CompressedTankItem} we cache the inner {@link FluidStack} so the
     * two Redirect handlers below can use it without repeating the check.
     */
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

    // -------------------------------------------------------------------------
    // REDIRECT: renderSlot
    // -------------------------------------------------------------------------

    /**
     * Replaces the {@code guiHandler.renderSlot(graphics, key, x, y)} call
     * inside {@code renderItemEntry} for fluid stacks.
     *
     * <p>{@code x=0, y=0} are relative to the PoseStack that the screen has
     * already translated to the correct slot position, so this renders the
     * fluid icon in exactly the same place as the item icon would have been.
     */
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

    // -------------------------------------------------------------------------
    // REDIRECT: renderDecorations (amount overlay)
    // -------------------------------------------------------------------------

    /**
     * Replaces the {@code guiHandler.renderDecorations(graphics, key, amount, x, y)}
     * call inside {@code renderItemEntry} for fluid stacks.
     *
     * <p>For fluids the amount is in mB; {@link FluidSlotAmountRenderer} knows
     * how to format mB values nicely (e.g. "1k", "50k").  Amounts ≤ 1 are
     * skipped (no overlay needed).
     */
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

    // -------------------------------------------------------------------------
    // REDIRECT: tooltipBuilder (hover tooltip)
    // -------------------------------------------------------------------------

    /**
     * Replaces the {@code guiHandler.tooltipBuilder(key, amount)} call inside
     * {@code renderForeground} for fluid stacks, returning a list that shows
     * the fluid's display name and its mB amount instead of the vanilla
     * Compressed Tank item tooltip.
     */
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
   // require = 0: silently skip if the tooltip call cannot be found in this method
   // (the tooltip may be rendered in a helper method in future screen versions)
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
