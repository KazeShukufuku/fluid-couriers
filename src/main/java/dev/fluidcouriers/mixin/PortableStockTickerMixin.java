package dev.fluidcouriers.mixin;

import de.theidler.create_mobile_packages.items.portable_stock_ticker.LogisticallyLinkedItem;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.yision.fluidlogistics.item.CompressedTankItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.GenericOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = PortableStockTicker.class, remap = false)
public class PortableStockTickerMixin {

    private static final Logger FLUIDCOURIERS_LOGGER = LogManager.getLogger();

    @Inject(
            method = "broadcastPackageRequest(Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour$RequestType;"
                    + "Lru/zznty/create_factory_abstractions/generic/support/GenericOrder;"
                    + "Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;"
                    + "Ljava/lang/String;"
                    + "Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void fluidcouriers$routePortableFluidOrder(
            ItemStack tickerStack,
            LogisticallyLinkedBehaviour.RequestType requestType,
            GenericOrder order,
            IdentifiedInventory identifiedInventory,
            String address,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        int compressedTankCount = 0;
        int virtualCompressedTankCount = 0;
        List<String> stackSummary = new ArrayList<>();

        for (GenericStack genericStack : order.stacks()) {
            GenericKey key = genericStack.key();
            if (key instanceof ItemKey itemKey) {
                ItemStack stack = itemKey.stack();
                stackSummary.add(stack.getDescriptionId() + " x" + genericStack.amount());
                if (stack.getItem() instanceof CompressedTankItem) {
                    compressedTankCount++;
                    if (CompressedTankItem.isVirtual(stack)) {
                        virtualCompressedTankCount++;
                    }
                }
            } else {
                stackSummary.add(key.getClass().getSimpleName() + " x" + genericStack.amount());
            }
        }

        FLUIDCOURIERS_LOGGER.info(
                "[FluidCouriers] Portable ticker order received: stacks={}, crafts={}, compressedTanks={}, virtualCompressedTanks={}, address={}, summary={}",
                order.stacks().size(),
                order.crafts().size(),
                compressedTankCount,
                virtualCompressedTankCount,
                address,
                stackSummary
        );

        if (compressedTankCount <= 0) {
            return;
        }

        GenericOrder orderForBroadcast = order;
        boolean normalizedToVirtual = false;
        if (virtualCompressedTankCount < compressedTankCount) {
            List<GenericStack> normalizedStacks = new ArrayList<>(order.stacks().size());
            for (GenericStack genericStack : order.stacks()) {
                GenericKey key = genericStack.key();
                if (key instanceof ItemKey itemKey) {
                    ItemStack stack = itemKey.stack();
                    if (stack.getItem() instanceof CompressedTankItem
                            && !CompressedTankItem.isVirtual(stack)
                            && !CompressedTankItem.getFluid(stack).isEmpty()) {
                        ItemStack virtualStack = stack.copy();
                        CompressedTankItem.setFluidVirtual(virtualStack, CompressedTankItem.getFluid(stack));
                        normalizedStacks.add(new GenericStack(new ItemKey(virtualStack), genericStack.amount()));
                        normalizedToVirtual = true;
                        continue;
                    }
                }
                normalizedStacks.add(genericStack);
            }

            if (normalizedToVirtual) {
                orderForBroadcast = new GenericOrder(normalizedStacks, order.crafts());
            }
        }

        UUID uuid = LogisticallyLinkedItem.networkFromStack(tickerStack);
        if (uuid == null) {
            FLUIDCOURIERS_LOGGER.warn("[FluidCouriers] Portable fluid order aborted: ticker has no logistics network UUID.");
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        PackageOrderWithCrafts crafting = orderForBroadcast.asCrafting();
        boolean result = LogisticsManager.broadcastPackageRequest(
                uuid,
                requestType,
                crafting,
                identifiedInventory,
                address
        );

        ((PortableStockTicker) (Object) this).saveAddressToStack(tickerStack, address);

        FLUIDCOURIERS_LOGGER.info(
                "[FluidCouriers] Portable ticker routed fluid order: result={}, normalizedToVirtual={}, requestType={}, address={}",
                result,
                normalizedToVirtual,
                requestType,
                address
        );

        cir.setReturnValue(result);
        cir.cancel();
    }
}
