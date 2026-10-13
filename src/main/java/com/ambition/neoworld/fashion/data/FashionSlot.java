package com.ambition.neoworld.fashion.data;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public enum FashionSlot {
    HEAD("head", "Head"),
    FACE("face", "Face"),
    BACK("back", "Back"),
    BODY("body", "Body"),
    LEGS("legs", "Legs"),
    FEET("feet", "Feet");

    public static final Codec<FashionSlot> CODEC = Codec.STRING.xmap(
            name -> fromName(name).orElse(HEAD),
            FashionSlot::serializedName
    );
    public static final StreamCodec<FriendlyByteBuf, FashionSlot> STREAM_CODEC = StreamCodec.of(
            (buffer, slot) -> buffer.writeUtf(slot.serializedName),
            buffer -> fromName(buffer.readUtf()).orElse(HEAD)
    );

    private final String serializedName;
    private final String displayName;

    FashionSlot(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<FashionSlot> fromName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(slot -> slot.serializedName.equals(normalized))
                .findFirst();
    }

    public static String names() {
        return String.join(", ", Arrays.stream(values())
                .map(FashionSlot::serializedName)
                .toList());
    }
}
