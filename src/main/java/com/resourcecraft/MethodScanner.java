package com.resourcecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MethodScanner {
    public static void scan() {
        System.out.println("--- ServerTickEvents ---");
        for (Field f : ServerTickEvents.class.getFields()) {
            System.out.println("Field: " + f.getName());
        }
        
        System.out.println("\n--- Arrow Classes ---");
        try {
            Class<?> arrowClass = Class.forName("net.minecraft.world.entity.projectile.arrow.Arrow");
            System.out.println("Found: " + arrowClass.getName());
            for (java.lang.reflect.Constructor<?> c : arrowClass.getConstructors()) {
                System.out.println("Constructor: " + c);
            }
        } catch (Exception e) {
            System.out.println("Arrow not found, trying ArrowEntity...");
            try {
                Class<?> arrowEntityClass = Class.forName("net.minecraft.entity.projectile.ArrowEntity");
                System.out.println("Found: " + arrowEntityClass.getName());
            } catch (Exception e2) {
                System.out.println("ArrowEntity not found either.");
            }
        }
    }
}
