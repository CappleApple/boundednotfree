package com.cappleapple.boundednotfree.selector;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SelectorResolver<T> {
    private final Registry<T> registry;
    private final Map<String, List<String>> groups;

    public SelectorResolver(Registry<T> registry, Map<String, List<String>> groups) {
        this.registry = registry;
        this.groups = groups;
    }

    public Set<Holder<T>> resolve(String selector) {
        return resolve(selector, new ArrayDeque<>());
    }

    public Set<Holder<T>> resolveAll(Collection<String> selectors) {
        LinkedHashSet<Holder<T>> result = new LinkedHashSet<>();
        selectors.forEach(selector -> result.addAll(resolve(selector)));
        return Set.copyOf(result);
    }

    public List<String> validateGroups() {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        for (String group : groups.keySet()) {
            try { resolve("group:" + group); }
            catch (IllegalArgumentException exception) { errors.add(exception.getMessage()); }
        }
        return errors;
    }

    private Set<Holder<T>> resolve(String selector, ArrayDeque<String> stack) {
        if (selector == null || selector.isBlank()) return Set.of();
        if (selector.startsWith("group:")) {
            String name = selector.substring("group:".length());
            List<String> members = groups.get(name);
            if (members == null) return Set.of();
            if (stack.contains(name)) throw new IllegalArgumentException("Recursive selector group: " + String.join(" -> ", stack) + " -> " + name);
            stack.addLast(name);
            LinkedHashSet<Holder<T>> result = new LinkedHashSet<>();
            members.forEach(member -> result.addAll(resolve(member, stack)));
            stack.removeLast();
            return Set.copyOf(result);
        }
        if (selector.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(selector.substring(1));
            if (id == null) return Set.of();
            TagKey<T> tag = TagKey.create(registry.key(), id);
            LinkedHashSet<Holder<T>> result = new LinkedHashSet<>();
            registry.getTag(tag).ifPresent(set -> set.forEach(result::add));
            return Set.copyOf(result);
        }
        ResourceLocation id = ResourceLocation.tryParse(selector);
        if (id == null) return Set.of();
        return registry.getHolder(id).<Set<Holder<T>>>map(Set::of).orElseGet(Set::of);
    }
}
