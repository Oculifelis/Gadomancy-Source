package com.gadomancyawakened.node;

import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.nodes.NodeModifier;
import com.leclowndu93150.thaumaturge.api.nodes.NodeType;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * One possible result of a Randomization Focus run, read from {@code data/<ns>/gadomancyawakened/node_outcome/*.json}:
 * what it does, its weight, and whether it is good or bad for the node (a better-fed manipulator skips more bad
 * ones). Shipped files carry Gadomancy 1.5.17's weights. {@code switch_type} reads its own type weights.
 */
public record NodeOutcome(Action action, int weight, Polarity polarity, Map<String, Integer> types) {
    public static final ResourceKey<Registry<NodeOutcome>> REGISTRY_KEY = ResourceKey.createRegistryKey(GadomancyAwakened.id("node_outcome"));

    public static final Codec<NodeOutcome> CODEC = RecordCodecBuilder.create(i -> i.group(
            Action.CODEC.fieldOf("action").forGetter(NodeOutcome::action),
            Codec.intRange(0, 1000).fieldOf("weight").forGetter(NodeOutcome::weight),
            Polarity.CODEC.fieldOf("polarity").forGetter(NodeOutcome::polarity),
            Codec.unboundedMap(Codec.STRING, Codec.intRange(0, 1000)).optionalFieldOf("types", Map.of()).forGetter(NodeOutcome::types)
    ).apply(i, NodeOutcome::new));

    public enum Polarity implements StringRepresentable {
        POSITIVE, NEGATIVE, NEUTRAL;

        static final Codec<Polarity> CODEC = StringRepresentable.fromEnum(Polarity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Action implements StringRepresentable {
        BREAK_COMPOUNDS, COMBINE_ASPECTS, INCREASE_MODIFIER, DECREASE_MODIFIER, SWITCH_TYPE, APPLY_GROWING, GAIN_PRIMAL;

        static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Modifier order from worst to best; null is "no modifier". */
    private static final List<@Nullable NodeModifier> LADDER = java.util.Arrays.asList(NodeModifier.FADING, NodeModifier.PALE, null, NodeModifier.BRIGHT);

    /** Applies the outcome; false when it could not affect this node (the caller rolls again). */
    public boolean apply(BlockEntityNode node, RandomSource r, List<Holder<IAspect>> primals) {
        AspectList base = node.capacity();
        AspectList now = node.getAspects();
        switch (action) {
            case BREAK_COMPOUNDS -> {
                for (AspectInstance a : base.entries()) {
                    List<Holder<IAspect>> parts = a.aspect().value().components();
                    if (!a.aspect().value().isPrimal() && parts.size() == 2) {
                        int n = a.amount();
                        int cur = now.amountOf(a.aspect());
                        base = base.without(a.aspect()).add(parts.get(0), n).add(parts.get(1), n);
                        now = now.without(a.aspect()).add(parts.get(0), Math.max(cur, 0)).add(parts.get(1), Math.max(cur, 0));
                        set(node, base, now);
                        return true;
                    }
                }
                return false;
            }
            case COMBINE_ASPECTS -> {
                List<AspectInstance> entries = new ArrayList<>(base.entries());
                Collections.shuffle(entries, new java.util.Random(r.nextLong()));
                for (AspectInstance a : entries) {
                    for (AspectInstance b : entries) {
                        Holder<IAspect> c = compoundOf(node, a.aspect(), b.aspect());
                        if (a != b && c != null) {
                            int m = Math.min(a.amount(), b.amount());
                            int gain = m + r.nextInt(4);
                            base = base.reduce(a.aspect(), m).reduce(b.aspect(), m).add(c, gain);
                            now = now.reduce(a.aspect(), m).reduce(b.aspect(), m).add(c, gain);
                            set(node, base, now);
                            return true;
                        }
                    }
                }
                return false;
            }
            case INCREASE_MODIFIER, DECREASE_MODIFIER -> {
                int at = LADDER.indexOf(node.trait());
                int next = at + (action == Action.INCREASE_MODIFIER ? 1 : -1);
                if (next < 0 || next >= LADDER.size()) {
                    return false;
                }
                node.assignTrait(LADDER.get(next));
                node.invalidateRefill();
                return true;
            }
            case SWITCH_TYPE -> {
                int total = types.values().stream().mapToInt(Integer::intValue).sum();
                if (total <= 0) {
                    return false;
                }
                int roll = r.nextInt(total);
                for (Map.Entry<String, Integer> e : types.entrySet()) {
                    roll -= e.getValue();
                    if (roll < 0) {
                        if (e.getKey().equals("none")) {
                            return false;
                        }
                        NodeType type = NodeType.valueOf(e.getKey().toUpperCase(Locale.ROOT));
                        if (type == NodeType.HUNGRY && GrowingNodes.isGrowing(node)) {
                            return false;
                        }
                        node.reclassify(type);
                        node.invalidateRefill();
                        return true;
                    }
                }
                return false;
            }
            case APPLY_GROWING -> {
                // As in the original, newly making a node grow still lets another outcome be rolled.
                GrowingNodes.makeGrowing(node);
                return false;
            }
            case GAIN_PRIMAL -> {
                int start = r.nextInt(primals.size());
                for (int i = 0; i < primals.size(); i++) {
                    Holder<IAspect> p = primals.get((start + i) % primals.size());
                    if (base.amountOf(p) <= 0) {
                        int amount = base.totalAmount() / Math.max(1, base.size() * 2);
                        if (amount <= 0) {
                            return false;
                        }
                        set(node, base.add(p, amount), now.add(p, amount));
                        return true;
                    }
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private static void set(BlockEntityNode node, AspectList base, AspectList now) {
        node.applyNodeData(new com.leclowndu93150.thaumaturge.content.aura.node.NodeData(node.kind(),
                java.util.Optional.ofNullable(node.trait()), now, base));
        node.invalidateRefill();
    }

    private static @Nullable Holder<IAspect> compoundOf(BlockEntityNode node, Holder<IAspect> a, Holder<IAspect> b) {
        if (node.getLevel() == null) {
            return null;
        }
        return node.getLevel().registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).listElements()
                .filter(h -> {
                    List<Holder<IAspect>> parts = h.value().components();
                    return parts.size() == 2 && (parts.get(0).equals(a) && parts.get(1).equals(b) || parts.get(0).equals(b) && parts.get(1).equals(a));
                })
                .map(h -> (Holder<IAspect>) h).findFirst().orElse(null);
    }
}
