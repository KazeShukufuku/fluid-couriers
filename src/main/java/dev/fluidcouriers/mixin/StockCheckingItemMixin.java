package dev.fluidcouriers.mixin;

import com.kreidev.cmpackagecouriers.stock_ticker.StockCheckingItem;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.yision.fluidlogistics.item.CompressedTankItem;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.GenericOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intercepts {@link StockCheckingItem#broadcastPackageRequest} when the
 * {@link GenericOrder} contains virtual {@link CompressedTankItem} stacks
 * (i.e. fluid requests from a FluidPackager-linked inventory).
 *
 * <p>The default path goes through {@code GenericLogisticsManager}, which does
 * NOT call {@code LogisticsManager.broadcastPackageRequest()}.  FluidLogistics
 * patches the latter to recognise virtual CompressedTankItem stacks and route
 * them to {@code IFluidPackager.processFluidRequest()}.  We therefore redirect
 * any order that includes fluids through {@code LogisticsManager} so that the
 * existing FluidLogistics mixin can handle them correctly.
 *
 * <p>Non-fluid-only orders are left alone so that the standard CFA path runs.
 */
@Mixin(value = StockCheckingItem.class, remap = false)
public class StockCheckingItemMixin {

    private static final Logger FLUIDCOURIERS_LOGGER = LogManager.getLogger();

    /**
     * Broad method signature:
     * {@code boolean broadcastPackageRequest(ItemStack, RequestType, GenericOrder, IdentifiedInventory, String)}
     *
     * <p>We intercept at HEAD.  If the order has no fluid items we return
     * immediately (the original body continues).  Otherwise we cancel the
     * original body and delegate to {@code LogisticsManager.broadcastPackageRequest()}
     * where FluidLogistics' own Mixin picks up the fluid stacks.
     */
    @Inject(
            method = "broadcastPackageRequest(Lnet/minecraft/world/item/ItemStack;" +
                    "Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour$RequestType;" +
                    "Lru/zznty/create_factory_abstractions/generic/support/GenericOrder;" +
                    "Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;" +
                    "Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void fluidcouriers$routeFluidOrder(
            ItemStack tickerStack,
            LogisticallyLinkedBehaviour.RequestType requestType,
            GenericOrder order,
            IdentifiedInventory identifiedInventory,
            String address,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // Intercept when the order contains compressed tanks. Some environments
        // provide non-virtual tank stacks; we normalize them to virtual before
        // delegating so FluidLogistics can detect them reliably.
        boolean hasCompressedTankStack = false;
        boolean hasVirtualCompressedTankStack = false;
        for (GenericStack gs : order.stacks()) {
            GenericKey key = gs.key();
            if (key instanceof ItemKey itemKey) {
                ItemStack is = itemKey.stack();
                if (is.getItem() instanceof CompressedTankItem) {
                    hasCompressedTankStack = true;
                    if (CompressedTankItem.isVirtual(is)) {
                        hasVirtualCompressedTankStack = true;
                    }
                }
            }
        }

        if (!hasCompressedTankStack) {
            // No fluids – let the normal GenericLogisticsManager path run.
            return;
        }

        GenericOrder orderForBroadcast = order;
        boolean normalizedToVirtual = false;
        if (!hasVirtualCompressedTankStack) {
            List<GenericStack> normalizedStacks = new ArrayList<>(order.stacks().size());
            for (GenericStack gs : order.stacks()) {
                GenericKey key = gs.key();
                if (key instanceof ItemKey itemKey) {
                    ItemStack stack = itemKey.stack();
                    if (stack.getItem() instanceof CompressedTankItem
                            && !CompressedTankItem.isVirtual(stack)
                            && !CompressedTankItem.getFluid(stack).isEmpty()) {
                        ItemStack virtualStack = stack.copy();
                        CompressedTankItem.setFluidVirtual(virtualStack, CompressedTankItem.getFluid(stack));
                        normalizedStacks.add(new GenericStack(new ItemKey(virtualStack), gs.amount()));
                        normalizedToVirtual = true;
                        continue;
                    }
                }
                normalizedStacks.add(gs);
            }

            if (normalizedToVirtual) {
                orderForBroadcast = new GenericOrder(normalizedStacks, order.crafts());
            }
        }

        // Retrieve the logistics network UUID stored on the ticker item.
        UUID uuid = com.kreidev.cmpackagecouriers.stock_ticker.LogisticallyLinkedItem
                .networkFromStack(tickerStack);
        if (uuid == null) {
            FLUIDCOURIERS_LOGGER.warn("[FluidCouriers] Fluid order aborted: ticker has no logistics network UUID.");
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // Convert GenericOrder → PackageOrderWithCrafts.
        // asCrafting() maps each GenericStack(ItemKey(stack), count) to
        // BigItemStack(stack, count), preserving the virtual CompressedTankItem
        // stacks with their mB counts.
        PackageOrderWithCrafts crafting = orderForBroadcast.asCrafting();

        // Delegate to Create's LogisticsManager.  FluidLogistics has a Mixin
        // on this method (LogisticsManagerMixin) that detects virtual
        // CompressedTankItem stacks and routes them to IFluidPackager.
        // Non-fluid stacks in the same order are handled by Create normally.
        boolean result = LogisticsManager.broadcastPackageRequest(
                uuid, requestType, crafting, identifiedInventory, address);

        FLUIDCOURIERS_LOGGER.info(
            "[FluidCouriers] Routed fluid order via LogisticsManager: result={}, normalizedToVirtual={}, requestType={}, address={}, stackCount={}",
            result,
            normalizedToVirtual,
            requestType,
            address,
            orderForBroadcast.stacks().size()
        );

        cir.setReturnValue(result);
        cir.cancel();
    }
}
