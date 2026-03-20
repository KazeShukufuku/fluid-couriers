package dev.fluidcouriers.mixin.client;

import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.BigGenericStack;
import ru.zznty.create_factory_abstractions.generic.support.GenericOrder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = PortableStockTickerScreen.class, remap = false)
public abstract class PortableStockTickerScreenSendMixin {

    private static final Logger FLUIDCOURIERS_LOGGER = LogManager.getLogger();

    @Shadow
    public List<BigGenericStack> itemsToOrder;

    @Redirect(
            method = "sendIt",
            at = @At(
                    value = "INVOKE",
                    target = "Lru/zznty/create_factory_abstractions/generic/support/GenericOrder;of(Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;)Lru/zznty/create_factory_abstractions/generic/support/GenericOrder;"
            ),
            remap = false
    )
    private GenericOrder fluidcouriers$preservePortableOrder(PackageOrderWithCrafts orderWithCrafts) {
        GenericOrder converted = GenericOrder.of(orderWithCrafts);
        int uiEntries = itemsToOrder == null ? 0 : itemsToOrder.size();

        if (itemsToOrder == null || itemsToOrder.isEmpty()) {
            if (!converted.isEmpty()) {
                FLUIDCOURIERS_LOGGER.info(
                        "[FluidCouriers] Portable send conversion kept (no UI fallback): convertedStacks={}, convertedCrafts={}, uiEntries={}",
                        converted.stacks().size(),
                        converted.crafts().size(),
                        uiEntries
                );
            } else {
                FLUIDCOURIERS_LOGGER.warn(
                        "[FluidCouriers] Portable send conversion returned empty order and UI list is empty."
                );
            }
            return converted;
        }

        List<GenericStack> stacks = new ArrayList<>(itemsToOrder.size());
        List<String> summary = new ArrayList<>(itemsToOrder.size());
        for (BigGenericStack bigGenericStack : itemsToOrder) {
            GenericStack stack = bigGenericStack.get();
            GenericKey key = stack.key();

            BigItemStack bigItemStack = bigGenericStack.asStack();
            ItemStack itemStack = bigItemStack.stack;
            if (bigItemStack.count <= 0) {
                summary.add(key.getClass().getSimpleName() + " -> skipped");
                continue;
            }

            stacks.add(new GenericStack(new ItemKey(itemStack), bigItemStack.count));
            summary.add(key.getClass().getSimpleName() + " -> ItemKey x" + bigItemStack.count);
        }

        if (stacks.isEmpty()) {
            FLUIDCOURIERS_LOGGER.warn(
                    "[FluidCouriers] Portable send fallback produced no network-safe stacks. convertedStacks={}, convertedCrafts={}, uiEntries={}, summary={}",
                    converted.stacks().size(),
                    converted.crafts().size(),
                    uiEntries,
                    summary
            );
            return converted;
        }

        FLUIDCOURIERS_LOGGER.info(
                "[FluidCouriers] Portable send using network-safe ItemKey order: rebuiltStacks={}, convertedStacks={}, convertedCrafts={}, uiEntries={}, summary={}",
                stacks.size(),
                converted.stacks().size(),
                converted.crafts().size(),
                uiEntries,
                summary
        );

        return new GenericOrder(stacks, converted.crafts());
    }
}
