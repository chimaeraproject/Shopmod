package com.shopmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SHOPS = BUILDER
            .comment("Whether player-created item shops are enabled.")
            .define("enableShops", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
