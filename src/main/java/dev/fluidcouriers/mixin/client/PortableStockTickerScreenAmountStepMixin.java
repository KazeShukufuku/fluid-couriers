package dev.fluidcouriers.mixin.client;

import com.kreidev.cmpackagecouriers.stock_ticker.PortableStockTickerScreen;
import com.yision.fluidlogistics.item.CompressedTankItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.BigGenericStack;

import java.util.List;

/**
 * For virtual fluid stacks in the portable ticker, switch order adjustment
 * from mB to B-sized steps when available amount is at least 1000mB.
 */
@Mixin(value = PortableStockTickerScreen.class, remap = false)
public abstract class PortableStockTickerScreenAmountStepMixin {

    @Shadow
    public List<List<BigGenericStack>> displayedItems;

    @Redirect(
            method = "m_6375_",
            at = @At(
                    value = "INVOKE",
                    target = "Lru/zznty/create_factory_abstractions/generic/support/BigGenericStack;setAmount(I)V"
            ),
            remap = false
    )
    private void fluidcouriers$bucketStepOnClick(BigGenericStack stack, int requestedAmount) {
        stack.setAmount(fluidcouriers$normalizeAmountStep(stack, requestedAmount));
    }

    @Redirect(
            method = "m_6050_",
            at = @At(
                    value = "INVOKE",
                    target = "Lru/zznty/create_factory_abstractions/generic/support/BigGenericStack;setAmount(I)V"
            ),
            remap = false
    )
    private void fluidcouriers$bucketStepOnScroll(BigGenericStack stack, int requestedAmount) {
        stack.setAmount(fluidcouriers$normalizeScrollAmountStep(stack, requestedAmount));
    }

    @Unique
    private int fluidcouriers$normalizeAmountStep(BigGenericStack stack, int requestedAmount) {
        if (!fluidcouriers$shouldUseBucketUnit(stack)) {
            return requestedAmount;
        }

        GenericStack genericStack = stack.get();
        int current = genericStack.amount();
        int deltaUnits = requestedAmount - current;
        if (deltaUnits == 0) {
            return current;
        }

        final int bucket = 1000;
        int target = current + Integer.signum(deltaUnits) * Math.abs(deltaUnits) * bucket;
        int available = fluidcouriers$getAvailableAmount(genericStack.key());
        if (available <= 0) {
            available = genericStack.amount();
        }
        if (available > 0) {
            target = Math.min(target, available);
        }

        return Math.max(target, 0);
    }

    @Unique
    private int fluidcouriers$normalizeScrollAmountStep(BigGenericStack stack, int requestedAmount) {
        if (!fluidcouriers$shouldUseBucketUnit(stack)) {
            return requestedAmount;
        }

        GenericStack genericStack = stack.get();
        int current = genericStack.amount();
        int deltaUnits = requestedAmount - current;
        if (deltaUnits == 0) {
            return current;
        }

        final int bucket = 1000;
        int target = current + Integer.signum(deltaUnits) * Math.abs(deltaUnits) * bucket;
        int available = fluidcouriers$getAvailableAmount(genericStack.key());
        if (available <= 0) {
            available = genericStack.amount();
        }
        if (available > 0) {
            target = Math.min(target, available);
        }

        return Math.max(target, 0);
    }

    @Unique
    private boolean fluidcouriers$shouldUseBucketUnit(BigGenericStack stack) {
        GenericStack genericStack = stack.get();
        GenericKey key = genericStack.key();
        if (!(key instanceof ItemKey itemKey)) {
            return false;
        }

        ItemStack itemStack = itemKey.stack();
        if (!CompressedTankItem.isVirtual(itemStack)) {
            return false;
        }

        int available = fluidcouriers$getAvailableAmount(key);
        if (available <= 0) {
            available = genericStack.amount();
        }

        return available >= 1000;
    }

    @Unique
    private int fluidcouriers$getAvailableAmount(GenericKey key) {
        if (displayedItems == null) {
            return -1;
        }

        for (List<BigGenericStack> category : displayedItems) {
            if (category == null) {
                continue;
            }
            for (BigGenericStack entry : category) {
                GenericStack genericStack = entry.get();
                if (genericStack.key().equals(key)) {
                    return genericStack.amount();
                }
            }
        }

        return -1;
    }
}
