package dev.fluidcouriers.mixin;

import de.theidler.create_mobile_packages.items.portable_stock_ticker.StockCheckingItem;
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

@Mixin(value = StockCheckingItem.class, remap = false)
public class StockCheckingItemMixin {

    private static final Logger FLUIDCOURIERS_LOGGER = LogManager.getLogger();

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

        UUID uuid = de.theidler.create_mobile_packages.items.portable_stock_ticker.LogisticallyLinkedItem
                .networkFromStack(tickerStack);
        if (uuid == null) {
            FLUIDCOURIERS_LOGGER.warn("[FluidCouriers] Fluid order aborted: ticker has no logistics network UUID.");
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        PackageOrderWithCrafts crafting = orderForBroadcast.asCrafting();

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
