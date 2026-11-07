package com.ambition.neoworld.killer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Checks our registered injection contracts against the installed named Minecraft bytecode. */
final class CombatLogoutHooksRegressionTest {
    static void run() {
        try (InputStream config = resource("neoworld.mixins.json")) {
            var registered = JsonParser.parseReader(new InputStreamReader(config, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("mixins");
            for (String name : List.of("CombatLogoutDisconnectMixin", "CombatLogoutJoinMixin", "CombatLogoutLoginMixin",
                    "CombatLogoutConfigurationMixin", "CombatLogoutConnectionMixin", "CombatLogoutPlayerAccessor")) {
                check(registered.asList().stream().anyMatch(entry -> entry.getAsString().equals(name)), "Missing combat lifecycle hook registration: " + name);
                validate(name);
            }
        } catch (IOException exception) { throw new AssertionError("Cannot inspect combat logout hooks", exception); }
        System.out.println("Combat logout mixin target contracts passed");
    }

    private static void validate(String name) throws IOException {
        ClassNode mixin = read("com/ambition/neoworld/mixin/" + name);
        AnnotationNode definition = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        @SuppressWarnings("unchecked")
        List<Type> owners = (List<Type>) value(definition, "value");
        ClassNode target = read(owners.getFirst().getInternalName());
        for (var field : mixin.fields) {
            if (annotation(field.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null
                    && annotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") == null) continue;
            check(target.fields.stream().anyMatch(actual -> actual.name.equals(field.name) && actual.desc.equals(field.desc)),
                    name + " shadows a missing or wrongly typed field: " + field.name);
        }
        int checked = 0;
        for (var handler : mixin.methods) {
            AnnotationNode accessor = annotation(handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Accessor;");
            if (accessor == null) accessor = annotation(handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Accessor;");
            if (accessor != null) {
                String field = (String) value(accessor, "value");
                Type[] args = Type.getArgumentTypes(handler.desc);
                String descriptor = (args.length == 0 ? Type.getReturnType(handler.desc) : args[0]).getDescriptor();
                check(target.fields.stream().anyMatch(actual -> actual.name.equals(field) && actual.desc.equals(descriptor)),
                        name + " accesses a missing or wrongly typed player field: " + field);
                checked++;
            }
            if (handler.visibleAnnotations == null) continue;
            for (AnnotationNode injection : handler.visibleAnnotations) {
                if (!injection.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
                        && !injection.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) continue;
                @SuppressWarnings("unchecked")
                var methods = (List<String>) value(injection, "method");
                Object rawAt = value(injection, "at");
                @SuppressWarnings("unchecked")
                var at = rawAt instanceof AnnotationNode location ? List.of(location) : (List<AnnotationNode>) rawAt;
                for (String selector : methods) {
                    var candidates = target.methods.stream().filter(method -> selector.equals(method.name) || selector.equals(method.name + method.desc)).toList();
                    check(candidates.size() == 1, name + " injection method must resolve unambiguously: " + selector);
                    for (AnnotationNode location : at) {
                        String invoke = (String) value(location, "target");
                        if (invoke == null) continue;
                        boolean found = false;
                        for (var instruction : candidates.getFirst().instructions) {
                            if (instruction instanceof MethodInsnNode call
                                    && invoke.equals("L" + call.owner + ";" + call.name + call.desc)) found = true;
                        }
                        check(found, name + " redirect invocation no longer exists: " + invoke);
                    }
                    checked++;
                }
            }
        }
        check(checked > 0, name + " has no validated lifecycle injections");
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) for (var entry : annotations) if (entry.desc.equals(descriptor)) return entry;
        return null;
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation != null && annotation.values != null)
            for (int i = 0; i < annotation.values.size(); i += 2)
                if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static ClassNode read(String name) throws IOException {
        try (InputStream input = resource(name + ".class")) {
            var result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static InputStream resource(String name) {
        InputStream result = CombatLogoutHooksRegressionTest.class.getClassLoader().getResourceAsStream(name);
        if (result == null) throw new AssertionError("Missing runtime resource: " + name);
        return result;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
