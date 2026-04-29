package com.resourcecraft.util;

import net.minecraft.network.protocol.Packet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class PacketQueue {
    private static final Map<Object, List<Packet<?>>> QUEUES = new WeakHashMap<>();

    public static List<Packet<?>> get(Object listener) {
        return QUEUES.computeIfAbsent(listener, k -> new ArrayList<>());
    }

    public static void clear(Object listener) {
        QUEUES.remove(listener);
    }
}
