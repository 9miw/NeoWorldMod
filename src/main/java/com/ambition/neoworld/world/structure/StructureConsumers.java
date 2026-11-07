package com.ambition.neoworld.world.structure;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Feature-owned safety checks and relinking. The structure engine knows no game modes. */
public final class StructureConsumers {
    public interface Consumer {
        String checkUpdate(List<StructurePiece> original);
        String checkDelete(List<StructurePiece> original);
        void replaced(List<StructurePiece> original,List<StructurePiece> replacement);
        default String checkReplacement(List<StructurePiece> original,List<StructurePiece> replacement) { return checkUpdate(original); }
    }
    private static final Map<MinecraftServer,List<Consumer>> CONSUMERS = new WeakHashMap<>();
    private static final Map<MinecraftServer,List<java.util.function.Predicate<net.minecraft.commands.CommandSourceStack>>> ACCESS = new WeakHashMap<>();
    private static final Set<MinecraftServer> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private StructureConsumers() {}
    public static void activate(MinecraftServer server) { ACTIVE.add(server); }
    public static void registerAccess(MinecraftServer server,java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> access) {
        ACCESS.computeIfAbsent(server,ignored -> new ArrayList<>()).add(access);
    }
    public static boolean authorized(net.minecraft.commands.CommandSourceStack source) {
        return ACTIVE.contains(source.getServer()) && (source.hasPermission(2)
                || ACCESS.getOrDefault(source.getServer(),List.of()).stream().anyMatch(access -> access.test(source)));
    }
    public static void register(MinecraftServer server,Consumer consumer) {
        CONSUMERS.computeIfAbsent(server,ignored -> new ArrayList<>()).add(consumer);
    }
    public static String checkUpdate(MinecraftServer server,List<StructurePiece> original) {
        for(var consumer:CONSUMERS.getOrDefault(server,List.of())) { String error=consumer.checkUpdate(original);if(error!=null)return error; }
        return null;
    }
    public static String checkReplacement(MinecraftServer server,List<StructurePiece> original,List<StructurePiece> replacement) {
        for(var consumer:CONSUMERS.getOrDefault(server,List.of())) { String error=consumer.checkReplacement(original,replacement);if(error!=null)return error; }
        return null;
    }
    public static String checkDelete(MinecraftServer server,List<StructurePiece> original) {
        for(var consumer:CONSUMERS.getOrDefault(server,List.of())) { String error=consumer.checkDelete(original);if(error!=null)return error; }
        return null;
    }
    public static void replaced(MinecraftServer server,List<StructurePiece> original,List<StructurePiece> replacement) {
        CONSUMERS.getOrDefault(server,List.of()).forEach(consumer -> consumer.replaced(original,replacement));
    }
    public static void clear(MinecraftServer server) { CONSUMERS.remove(server);ACCESS.remove(server);ACTIVE.remove(server); }
}
