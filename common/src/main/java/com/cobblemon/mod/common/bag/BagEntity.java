package com.cobblemon.mod.common.bag;

import com.cobblemon.mod.common.item.bag.CobblemonBagContainer;

public interface BagEntity {
    CobblemonBagContainer cobblemon$getBagContainer();
    void cobblemon$setBagContainer(CobblemonBagContainer bagContainer);
}
