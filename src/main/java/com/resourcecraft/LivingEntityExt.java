package com.resourcecraft;

import java.util.List;

public interface LivingEntityExt {
    void resourceCraft$addStuckArrow(int durability);
    List<Integer> resourceCraft$getStuckArrows();
    void resourceCraft$clearStuckArrows();
}
