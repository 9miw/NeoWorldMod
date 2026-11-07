package com.ambition.neoworld.dungeon;

import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.dungeon.definition.DungeonLootPreview;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class DungeonBrowserRegressionTest {
    public static void main(String[] args) throws Exception {
        catalog();
        dungeonSounds();
        waitingRoom();
        waitingRoomActions();
        waitingRoomWideLayout();
        waitingRoomContent();
        createRoomLayout();
        createRoomV5Layout();
        lootRandomRules();
        chestRewardPresets();
        itemOnlyRewards();
        rewardTooltipDetails();
        browserIcons();
        browserV5Assets();
        browserRouteLayout();
        crystalAssets();
        soloDeathCountdown();
        dungeonTitles();
        dungeonDeathWaypoints();
        DungeonResultsRegressionTest.run();
        waveSpawns();
        codec();
        scrollbars();
        dungeonHud();
        dungeonLoading();
        imageConfiguration();
        System.out.println("Dungeon browser catalog, packet and scrollbar regression checks passed");
    }

    private static void dungeonDeathWaypoints() throws Exception {
        var position = new net.minecraft.core.BlockPos(2000, 64, 0);
        var dungeon = net.minecraft.core.GlobalPos.of(
                com.ambition.neoworld.registry.ModDimensionRegistries.DUNGEON_LEVEL, position);
        check(com.ambition.neoworld.dungeon.room.DungeonDeathWaypoints.suppress(dungeon),
                "Dungeon deaths do not create FTB death waypoints");
        for (var dimension : List.of(net.minecraft.world.level.Level.OVERWORLD,
                net.minecraft.world.level.Level.NETHER, net.minecraft.world.level.Level.END,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("neoworld", "spawn")))) {
            check(!com.ambition.neoworld.dungeon.room.DungeonDeathWaypoints.suppress(
                    net.minecraft.core.GlobalPos.of(dimension, position)),
                    "Deaths outside dungeon retain FTB waypoints: " + dimension.location());
        }
        check(!com.ambition.neoworld.dungeon.room.DungeonDeathWaypoints.suppress(null),
                "Missing death location is not treated as dungeon death");
        var hook = dev.ftb.mods.ftbchunks.FTBChunks.class.getDeclaredMethod("playerCloned",
                net.minecraft.server.level.ServerPlayer.class, net.minecraft.server.level.ServerPlayer.class,
                boolean.class);
        check(hook.getReturnType() == void.class, "Installed FTB clone hook matches mixin signature");
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/neoworld.mixins.json"))).getAsJsonObject();
        check(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("FtbChunksDungeonDeathMixin")),
                "Death waypoint hook is registered on server, not client only");
    }

    private static void dungeonTitles() {
        var start = com.ambition.neoworld.dungeon.room.DungeonTitles.start("Slime");
        check(start.subtitle().equals("Slime"), "Start title keeps dungeon name dynamic");
        var wave = com.ambition.neoworld.dungeon.room.DungeonTitles.wave(3);
        check(wave.title().equals("WAVE 3"), "Wave title uses current wave number");
        check(com.ambition.neoworld.dungeon.room.DungeonTitles.cleared(true).subtitle().contains("เวฟถัดไป"),
                "Cleared wave announces next wave");
        check(com.ambition.neoworld.dungeon.room.DungeonTitles.cleared(false).subtitle().contains("บอส"),
                "Final wave announces boss instead of nonexistent next wave");
        check(com.ambition.neoworld.dungeon.room.DungeonTitles.boss("Slime King").subtitle().equals("Slime King"),
                "Boss title keeps clone name dynamic");
        check(com.ambition.neoworld.dungeon.room.DungeonTitles.death(true).subtitle().contains("10"),
                "Solo death retains ten-second exit");
        check(com.ambition.neoworld.dungeon.room.DungeonTitles.death(false).subtitle().contains("/neow dungeon exit"),
                "Team death gives valid exit command");
        for (var notice : List.of(start, wave,
                com.ambition.neoworld.dungeon.room.DungeonTitles.victory(),
                com.ambition.neoworld.dungeon.room.DungeonTitles.defeat(),
                com.ambition.neoworld.dungeon.room.DungeonTitles.timeout())) {
            check(notice.stayTicks() > 0,
                    "Title has a positive display duration");
        }
        check(start.stayTicks() + 15 <= 60 &&
                com.ambition.neoworld.dungeon.room.DungeonTitles.cleared(true).stayTicks() + 15 <= 60,
                "Transition titles finish before the next scheduled spawn");
    }

    private static void createRoomV5Layout() {
        for (int[] viewport : new int[][] {{320,240}, {426,240}, {640,360}, {1280,720}, {1920,1080}}) {
            var layout = com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fitV5(viewport[0], viewport[1]);
            int x = (viewport[0]-layout.width())/2, y = (viewport[1]-layout.height())/2;
            check(x+layout.width()+20 <= viewport[0], "Create-room external close control stays on-screen");
            check(layout.bodyTop() >= Math.round(layout.width()*120F/1468F)+6,
                    "Content clears the shared V5 lantern plaque");
            check(layout.bodyTop()+layout.bodyHeight()+12 <= layout.footerTop(),
                    "Scrolled content and reward track cannot cover the fixed footer");
            check(y+layout.footerTop()+24 == y+layout.height()-16,
                    "Create-room footer uses the browser's bottom inset");
            check(layout.slotSize() >= 18 && layout.slotSize() <= 24,
                    "Create-room reward slots stay compact without shrinking native item artwork");
            check(Math.round(178*layout.spacing())+layout.slotSize()+10 <= layout.contentHeight(),
                    "Vertical scrolling exposes the complete reward strip and scrollbar");
            check(12+layout.bodyWidth() < layout.width()-18,
                    "Create-room body leaves room for the independent vertical scrollbar");
        }
    }

    private static void rewardTooltipDetails() {
        var random = com.ambition.neoworld.network.dungeon.DungeonRewardDetail.preset(
                new com.ambition.neoworld.dungeon.definition.DungeonLootRule(25.5,2,5),3);
        var fixed = com.ambition.neoworld.network.dungeon.DungeonRewardDetail.preset(
                com.ambition.neoworld.dungeon.definition.DungeonLootRule.DEFAULT,3);
        var unknown = com.ambition.neoworld.network.dungeon.DungeonRewardDetail.UNKNOWN;
        check(random.exact() && random.chance() == 25.5 && random.minimum() == 2 && random.maximum() == 5,
                "Reward previews preserve the original per-slot chance and random amount bounds");
        check(fixed.chance() == 100 && fixed.minimum() == 3 && fixed.maximum() == 3,
                "Default rules expose the saved stack count, not the preview maximum or a guessed count");
        for (var detail : List.of(random,fixed,unknown)) {
            var b = buffer();
            try {
                com.ambition.neoworld.network.dungeon.DungeonRewardDetail.STREAM_CODEC.encode(b,detail);
                check(com.ambition.neoworld.network.dungeon.DungeonRewardDetail.STREAM_CODEC.decode(b).equals(detail)
                        && b.readableBytes() == 0, "Reward detail codec round trips known and unknown rules");
            } finally { b.release(); }
        }
        var lines = com.ambition.neoworld.client.gui.dungeon.DungeonRewardTooltip.lines(random);
        check(lines.size() == 5 && lines.get(2).getString().contains("25.5%")
                && lines.get(3).getString().contains("2 - 5") && lines.get(4).getString().contains("แยกรายคน"),
                "Reward footer uses exact percentages, conditional quantity ranges and actual personal chest behavior");
        var unknownLines = com.ambition.neoworld.client.gui.dungeon.DungeonRewardTooltip.lines(unknown);
        check(unknownLines.get(2).getString().contains("Loot Table") && !unknownLines.get(2).getString().contains("%"),
                "Static loot previews never advertise invented probabilities");
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonRewardDetail(true,Double.NaN,1,2));
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonRewardDetail(true,101,1,2));
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonRewardDetail(true,25,5,2));
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonRewardDetail(false,100,1,1));
        rejects(() -> new DungeonBrowserDataPayload.Entry("test","Test",1,0,1,0,"","","",0,0,
                List.of(),false,"",List.of(random)));
        var b = buffer();
        try {
            for (var detail : List.of(random,fixed,unknown))
                com.ambition.neoworld.network.dungeon.DungeonRewardDetail.STREAM_CODEC.encode(b,detail);
            for (var expected : List.of(random,fixed,unknown))
                check(com.ambition.neoworld.network.dungeon.DungeonRewardDetail.STREAM_CODEC.decode(b).equals(expected),
                        "Adjacent reward slots retain independent detail order");
            check(b.readableBytes() == 0, "Ordered reward details consume their complete wire data");
        } finally { b.release(); }
    }

    private static void waitingRoomActions() {
        var solo = com.ambition.neoworld.client.gui.dungeon.DungeonRoomActions.forMember(true, true, 1);
        check(solo.start() && solo.leave() && !solo.party() && !solo.disband() && !solo.ready(),
                "Solo owner sees only start and leave");
        for (int capacity : new int[] {3,6}) {
            var owner = com.ambition.neoworld.client.gui.dungeon.DungeonRoomActions.forMember(true, true, capacity);
            check(owner.start() && owner.party() && owner.disband() && !owner.leave() && !owner.ready(),
                    "Team owner sees start, party management and disband without duplicate leave/ready");
            var member = com.ambition.neoworld.client.gui.dungeon.DungeonRoomActions.forMember(true, false, capacity);
            check(member.ready() && member.leave() && !member.party() && !member.disband() && !member.start(),
                    "Non-owner sees ready and leave only");
        }
        var absent = com.ambition.neoworld.client.gui.dungeon.DungeonRoomActions.forMember(false, false, 3);
        check(!absent.start() && !absent.ready() && !absent.leave() && !absent.disband() && !absent.party(),
                "Non-members see no room actions");
        for (int width : new int[] {320,426,640,1280}) {
            var layout = com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fitWaiting(width, 360);
            int frameX = (width-layout.width())/2;
            check(frameX+layout.width()+2+18 <= width, "Outside X hitbox stays on-screen");
        }
    }

    private static void waitingRoomContent() {
        for (int[] viewport : new int[][] {{320,240}, {426,240}, {640,360}, {1280,720}, {1920,1080}}) {
            var frame = com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fitWaiting(viewport[0], viewport[1]);
            var content = com.ambition.neoworld.client.gui.dungeon.DungeonWaitingContentLayout.fit(frame.bodyHeight());
            check(content.memberHeight() == 3*content.rowHeight(), "Exactly three visible member rows precede independent scrolling");
            check(content.capacityTop()+10 <= content.metadataDivider(), "Metadata clears the first divider");
            check(content.previewHeight() < content.metadataDivider(), "Preview stays above the member table");
            check(2-1 >= 0 && 2+content.previewHeight()+1 <= content.metadataDivider(),
                    "Inset preview border stays inside the body clip and above the metadata divider");
            check(content.tableHeading()+10 <= content.memberTop(), "Column headings no longer clip behind the first row");
            check(content.memberTop()+content.memberHeight() <= content.statusTop(), "Ready summary clears all three rows");
            check(content.statusTop()+10 <= content.rewardDivider(), "Ready summary clears the reward divider");
            check(content.rewardHeading()+10 <= content.rewardTop(), "Reward heading and item strip do not overlap");
            check(content.rewardTop()+content.slotSize()+10 <= content.contentHeight(), "All reward artwork and scrolling controls are reachable");
            check(content.slotSize() <= 24, "Waiting-room item previews retain compact sizing");
            if (viewport[0] >= 640) check(content.contentHeight() <= frame.bodyHeight(),
                    "Normal viewports show metadata, three members, ready summary and rewards without body scrolling");
            else check(content.contentHeight() > frame.bodyHeight(), "Small viewports retain body scrolling instead of shrinking text");
        }
    }

    private static void waitingRoomWideLayout() {
        for (int[] viewport : new int[][] {{320,240}, {426,240}, {640,360}, {1280,720}, {1920,1080}}) {
            var layout = com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fitWaiting(viewport[0], viewport[1]);
            check(layout.width() == Math.min(734, viewport[0]-48), "Waiting room uses browser-width geometry");
            check(layout.height() <= viewport[1]-28, "Wide waiting room stays inside the viewport");
            check(layout.bodyTop() >= Math.round(layout.width()*120F/1468F)+8,
                    "Content stays below the proportional V5 title band");
            check(layout.bodyTop()+layout.bodyHeight()+12 <= layout.footerTop(),
                    "Body scrollbar and fixed footer remain separate");
            int leaveWidth = Math.min(132, (layout.width()-100)/2), mainWidth = Math.min(160, layout.width()/3);
            check(28+leaveWidth+28 <= layout.width()-22-mainWidth,
                    "Leave, disband and start/ready buttons do not overlap");
            check(layout.footerTop()+28 <= layout.height()-14, "Footer buttons clear the bottom frame");
            check(layout.contentHeight() >= 240+layout.slotSize()+10,
                    "Compact body scrolling still exposes the reward scrollbar");
        }
    }

    private static void browserRouteLayout() {
        for (int[] viewport : new int[][] {{320,240}, {426,240}, {640,360}, {1280,720}, {1920,1080}}) {
            int panelWidth = Math.min(734, viewport[0]-48);
            int panelHeight = Math.min(viewport[1]-28, Math.max(280, Math.round(panelWidth*417F/734F)));
            int panelY = (viewport[1]-panelHeight)/2;
            int panelX = (viewport[0]-panelWidth)/2;
            int detailLeft = panelX+24+Math.round(214*(panelWidth-48)/686F)+8;
            int detailRight = panelX+panelWidth-12;
            int rewardWidth = Math.round(275*(detailRight-detailLeft)/442F);
            check(panelX+panelWidth+20 <= viewport[0], "External close button remains entirely visible");
            check(detailRight == panelX+panelWidth-12, "Right content uses the fixed inner frame margin");
            for (int lineHeight : new int[] {9,10}) {
                int metadataBottom = panelY + Math.max(Math.round(187*panelHeight/417F),
                        Math.round(168*panelHeight/417F)+lineHeight+2);
                var layout = com.ambition.neoworld.client.gui.dungeon.DungeonBrowserRouteLayout.fit(
                        metadataBottom, panelHeight, lineHeight);
                int slot = Math.clamp(Math.min((rewardWidth-20)/6,
                        panelY+panelHeight-layout.rewardY()-26),18,40);
                int nativeIconSize = layout.iconSize();
                layout = layout.fillTo(panelY+panelHeight-16, slot);
                check(layout.iconSize() == nativeIconSize, "Filling unused height must not stretch route icons");
                check(layout.routeTop() > metadataBottom && layout.iconSize() >= 16,
                        "Every viewport retains route icons below the metadata");
                check(layout.bossTop() >= layout.routeTop()+layout.iconSize()
                                +(layout.inlineCaptions() ? 8 : 3+lineHeight+8),
                        "Boss frame clears route artwork and captions");
                check(layout.rewardHeadingTop() >= layout.bossTop()+layout.bossHeight()+4,
                        "Rewards cannot overlap the boss frame");
                check(layout.rewardY()+slot+10 <= panelY+panelHeight-16,
                        "Native item slots and overflow scrollbar clear the frame bottom");
                check(layout.rewardY()+slot+10 == panelY+panelHeight-16,
                        "Reward row and overflow track fill down to the shared bottom inset");
                check(panelY+panelHeight-40+24 == panelY+panelHeight-16,
                        "Primary action aligns with the reward section bottom");
            }
        }
    }

    private static void browserV5Assets() throws Exception {
        Path root = Path.of("src/main/resources/assets/neoworld/textures/gui/dungeon/browser");
        String[] names = {"shell", "selected", "primary", "entrance", "route_entrance", "divider"};
        int[][] dimensions = {{1468,834}, {1206,246}, {1134,280}, {82,85}, {70,67}, {884,20}};
        for (int i = 0; i < names.length; i++) {
            var bitmap = javax.imageio.ImageIO.read(root.resolve(names[i] + ".png").toFile());
            check(bitmap != null && bitmap.getWidth() == dimensions[i][0] && bitmap.getHeight() == dimensions[i][1],
                    "V5 runtime source bounds: " + names[i]);
            var metadata = JsonParser.parseString(Files.readString(root.resolve(names[i] + ".png.mcmeta")))
                    .getAsJsonObject().getAsJsonObject("texture");
            check(!metadata.get("blur").getAsBoolean() && metadata.get("clamp").getAsBoolean(),
                    "V5 artwork must retain sharp sampling: " + names[i]);
        }
    }

    private static void crystalAssets() throws Exception {
        Path root = Path.of("src/main/resources/assets/neoworld/textures/gui/dungeon/crystal");
        var manifest = JsonParser.parseString(Files.readString(root.resolve("assets.json"))).getAsJsonArray();
        check(manifest.size() == 23, "Crystal pack must contain all independently exported components");
        for (var element : manifest) {
            var asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            var bitmap = javax.imageio.ImageIO.read(root.resolve(name + ".png").toFile());
            check(bitmap != null && bitmap.getWidth() == asset.get("width").getAsInt()
                    && bitmap.getHeight() == asset.get("height").getAsInt(), "Measured crystal asset bounds: " + name);
            var texture = JsonParser.parseString(Files.readString(root.resolve(name + ".png.mcmeta")))
                    .getAsJsonObject().getAsJsonObject("texture");
            check(!texture.get("blur").getAsBoolean() && texture.get("clamp").getAsBoolean(),
                    "Crystal assets must use sharp clamped sampling: " + name);
            if (List.of("door", "star", "swords", "skull", "member", "clock", "chest").contains(name)) {
                check(bitmap.getWidth() == 16 && bitmap.getHeight() == 16,
                        "Runtime icons use native 16-pixel canvases, not large mockup crops: " + name);
                check(bitmap.getColorModel().hasAlpha(), "Icons retain transparent independent canvases: " + name);
                boolean transparent = false, opaque = false;
                for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
                    int alpha = bitmap.getRGB(x,y) >>> 24;
                    transparent |= alpha == 0; opaque |= alpha == 255;
                }
                check(transparent && opaque, "Extracted icons contain artwork and transparent background: " + name);
            }
        }
    }

    private static void dungeonLoading() {
        for (boolean visible : new boolean[] {true, false}) {
            var payload = new com.ambition.neoworld.network.dungeon.DungeonLoadingPayload(
                    visible, 42, visible ? "Forgotten Mine" : "");
            var buffer = buffer();
            try {
                com.ambition.neoworld.network.dungeon.DungeonLoadingPayload.STREAM_CODEC.encode(buffer, payload);
                check(com.ambition.neoworld.network.dungeon.DungeonLoadingPayload.STREAM_CODEC.decode(buffer).equals(payload),
                        "Loading visibility, room identity and title must round trip");
                check(buffer.readableBytes() == 0, "Loading codec must consume the whole packet");
            } finally { buffer.release(); }
        }
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonLoadingPayload(true, 0, "Mine"));
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonLoadingPayload(true, 1, "x".repeat(32768)));
    }

    private static void waveSpawns() throws Exception {
        var each = new com.ambition.neoworld.dungeon.definition.DungeonMonsterWave(1, "Zombie", 5,
                com.ambition.neoworld.dungeon.definition.DungeonMonsterWave.SpawnMode.EACH);
        var random = new com.ambition.neoworld.dungeon.definition.DungeonMonsterWave(1, "Zombie", 20,
                com.ambition.neoworld.dungeon.definition.DungeonMonsterWave.SpawnMode.RANDOM);
        var legacy = new com.ambition.neoworld.dungeon.definition.DungeonMonsterWave(1, "Zombie", 7);
        var rng = net.minecraft.util.RandomSource.create(12345);
        int[] perPoint = new int[4];
        for (int i = 0; i < each.totalCount(4); i++) perPoint[each.markerIndex(i, 4, rng)]++;
        check(java.util.Arrays.equals(perPoint, new int[] {5, 5, 5, 5}), "Each mode spawns exactly five per marker");
        int[] randomPoints = new int[4];
        for (int i = 0; i < random.totalCount(4); i++) randomPoints[random.markerIndex(i, 4, rng)]++;
        check(java.util.Arrays.stream(randomPoints).sum() == 20, "Random count is total, not multiplied by markers");
        check(java.util.Arrays.stream(randomPoints).filter(n -> n > 0).count() > 1, "Seeded random uses multiple markers");
        for (int i = 0; i < legacy.totalCount(3); i++) check(legacy.markerIndex(i, 3, rng) == i % 3, "Legacy round robin preserved");
        check(random.totalCount(1) == 20 && random.markerIndex(0, 1, rng) == 0, "Single marker is supported");
        rejects(() -> each.totalCount(0));
        rejects(() -> each.totalCount(1000));
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonMonsterWave(1, "Zombie", 0));

        var data = new com.ambition.neoworld.dungeon.definition.DungeonDefinitionData();
        var definition = data.create("wave_test").orElseThrow();
        data.addMonsterWave(definition, each);
        data.addMonsterWave(definition, random);
        data.addMonsterWave(definition, legacy);
        data.updateStatus(definition, com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.ENABLED);
        data.updateMonsterWave(definition, 0, each);
        check(definition.status() == com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.DRAFT,
                "Changing wave spawn configuration requires revalidation");
        var saved = data.save(new net.minecraft.nbt.CompoundTag(), null);
        var load = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.class.getDeclaredMethod("load",
                net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(restored.get("wave_test").orElseThrow().monsterWaves().equals(definition.monsterWaves()),
                "Every spawn mode and count must persist");
        saved.getList("definitions", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .getList("monsterWaves", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("spawnMode");
        var oldSave = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(oldSave.get("wave_test").orElseThrow().monsterWaves().getFirst().spawnMode()
                == com.ambition.neoworld.dungeon.definition.DungeonMonsterWave.SpawnMode.ROUND_ROBIN,
                "Old NBT without spawnMode must default to original distribution");
    }

    private static void soloDeathCountdown() throws Exception {
        var remaining = com.ambition.neoworld.dungeon.room.DungeonRoomManager.class.getDeclaredMethod(
                "soloDeathSecondsRemaining", long.class, long.class);
        remaining.setAccessible(true);
        long deadline = 1200L;
        for (int elapsed = 0; elapsed <= 220; elapsed++) {
            int expected = Math.max(0, (200 - elapsed + 19) / 20);
            check((int) remaining.invoke(null, deadline, 1000L + elapsed) == expected,
                    "Solo death countdown must last 200 ticks and round seconds upward");
        }
    }

    private static void browserIcons() throws Exception {
        var image = javax.imageio.ImageIO.read(Path.of(
                "src/main/resources/assets/neoworld/textures/gui/dungeon/browser/icons.png").toFile());
        check(image != null && image.getWidth() == 2172 && image.getHeight() == 724,
                "Browser icon source dimensions must match runtime UVs");
        check(image.getColorModel().hasAlpha() && (image.getRGB(0, 0) >>> 24) == 0,
                "Icon atlas must retain transparent backing");
        int[][] regions = {{110, 73, 539, 562}, {824, 86, 525, 527}, {1558, 104, 499, 524}};
        for (int[] region : regions) {
            int opaque = 0;
            for (int y = region[1]; y < region[1] + region[3]; y += 4)
                for (int x = region[0]; x < region[0] + region[2]; x += 4)
                    if ((image.getRGB(x, y) >>> 24) > 128) opaque++;
            check(opaque > 100, "Every installed icon region must contain visible pixels");
        }
        var metadata = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/neoworld/textures/gui/dungeon/browser/icons.png.mcmeta")))
                .getAsJsonObject().getAsJsonObject("texture");
        check(!metadata.get("blur").getAsBoolean() && metadata.get("clamp").getAsBoolean(),
                "Browser icons use crisp clamped sampling");
    }

    private static void itemOnlyRewards() throws Exception {
        var data = new com.ambition.neoworld.dungeon.definition.DungeonDefinitionData();
        var definition = data.create("item_rewards").orElseThrow();
        var table = net.minecraft.resources.ResourceLocation.parse("neoworld:chests/dungeon/test_reward");
        data.updateReward(definition, new com.ambition.neoworld.dungeon.definition.DungeonRewardConfig(table));
        var saved = data.save(new net.minecraft.nbt.CompoundTag(), null);
        var reward = saved.getList("definitions", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getCompound("reward");
        check(!reward.contains("experience") && !reward.contains("money"), "Item-only reward saves omit automatic payouts");
        reward.putInt("experience", 100000);
        reward.putLong("money", 999999999L);
        var load = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.class.getDeclaredMethod("load",
                net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(restored.get("item_rewards").orElseThrow().reward().lootTableId().equals(table),
                "Legacy reward NBT keeps its loot table");
        var migrated = restored.save(new net.minecraft.nbt.CompoundTag(), null)
                .getList("definitions", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getCompound("reward");
        check(!migrated.contains("experience") && !migrated.contains("money"),
                "Loading legacy NBT discards old EXP and money amounts");
        var rewardNode = com.ambition.neoworld.command.dungeon.DungeonCommand.buildCommand().build()
                .getChild("admin").getChild("set").getChild("id").getChild("reward").getChild("loot_table");
        check(rewardNode.getCommand() != null && rewardNode.getChildren().isEmpty(),
                "Reward command ends at the loot table without payout arguments");
    }

    private static void chestRewardPresets() throws Exception {
        var data = new com.ambition.neoworld.dungeon.definition.DungeonDefinitionData();
        var id = com.ambition.neoworld.command.dungeon.DungeonLootCommands.presetId("slime_reward");
        check(id.toString().equals("neoworld:dungeon/rewards/slime_reward"), "Short reward names resolve to preset namespace");
        check(com.ambition.neoworld.command.dungeon.DungeonLootCommands.presetId(id.toString()).equals(id),
                "Explicit reward namespace is preserved");
        var item = new net.minecraft.nbt.CompoundTag();
        item.putString("id", "minecraft:diamond_sword");
        item.putInt("count", 1);
        var components = new net.minecraft.nbt.CompoundTag();
        components.putString("minecraft:custom_name", "{\"text\":\"Slime Sword\"}");
        var custom = new net.minecraft.nbt.CompoundTag();
        custom.putInt("upgrade_level", 15);
        components.put("minecraft:custom_data", custom);
        item.put("components", components);
        var original = item.copy();
        data.addLootPreset(id, List.of(item, new net.minecraft.nbt.CompoundTag()));
        item.putInt("count", 10);
        check(data.all().isEmpty(), "Saving chest rewards never creates a dungeon");
        rejects(() -> data.addLootPreset(id, List.of(original)));
        rejects(() -> data.addLootPreset(net.minecraft.resources.ResourceLocation.parse("test:empty"),
                List.of(new net.minecraft.nbt.CompoundTag())));
        rejects(() -> data.addLootPreset(net.minecraft.resources.ResourceLocation.parse("test:oversized"),
                Collections.nCopies(55, original)));
        var definition = data.create("chest_reward").orElseThrow();
        data.updateReward(definition, new com.ambition.neoworld.dungeon.definition.DungeonRewardConfig(null, id));
        var serialized = data.save(new net.minecraft.nbt.CompoundTag(), null);
        check(serialized.getList("lootPresets", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).equals(original),
                "Saved preset owns a copy with original count and full component NBT");
        var load = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.class.getDeclaredMethod("load",
                net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, serialized, null);
        check(restored.get("chest_reward").orElseThrow().reward().presetId().equals(id), "Dungeon preset link survives reload");
        check(restored.save(new net.minecraft.nbt.CompoundTag(), null).get("lootPresets").equals(serialized.get("lootPresets")),
                "All serialized slots, counts and components survive world reload");
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonRewardConfig(null, null));
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonRewardConfig(id, id));
        var root = com.ambition.neoworld.command.dungeon.DungeonCommand.buildCommand().build();
        var argument = (com.mojang.brigadier.tree.ArgumentCommandNode<?, ?>) root.getChild("admin").getChild("loot")
                .getChild("save").getChild("name");
        var reader = new com.mojang.brigadier.StringReader(id.toString());
        argument.getType().parse(reader);
        check(!reader.canRead() && argument.getCommand() != null, "Chest reward save accepts full namespace and ends at name");
    }

    private static void lootRandomRules() throws Exception {
        var rng = net.minecraft.util.RandomSource.create(54123);
        var never = new com.ambition.neoworld.dungeon.definition.DungeonLootRule(0, 2, 5);
        var always = new com.ambition.neoworld.dungeon.definition.DungeonLootRule(100, 2, 5);
        var chance = new com.ambition.neoworld.dungeon.definition.DungeonLootRule(25, 2, 5);
        int hits = 0;
        var amounts = new java.util.HashSet<Integer>();
        for (int i = 0; i < 20000; i++) {
            check(never.roll(rng, 10) == 0, "Zero percent never drops");
            int guaranteed = always.roll(rng, 10);
            check(guaranteed >= 2 && guaranteed <= 5, "Guaranteed rewards stay within inclusive quantity bounds");
            amounts.add(guaranteed);
            int rolled = chance.roll(rng, 10);
            if (rolled != 0) { hits++; check(rolled >= 2 && rolled <= 5, "Chance and amount combine independently"); }
        }
        check(amounts.equals(java.util.Set.of(2, 3, 4, 5)), "Both endpoints and intermediate quantities are reachable");
        check(hits > 4700 && hits < 5300, "Seeded 25-percent sampling stays near expected frequency");
        check(com.ambition.neoworld.dungeon.definition.DungeonLootRule.DEFAULT.roll(rng, 17) == 17,
                "Legacy slots retain original amounts");
        check(new com.ambition.neoworld.dungeon.definition.DungeonLootRule(100, 1, 1).roll(rng, 9) == 1,
                "Fixed ranges return exactly that amount");
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonLootRule(Double.NaN, 0, 0));
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonLootRule(101, 0, 0));
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonLootRule(-1, 0, 0));
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonLootRule(50, 5, 2));
        rejects(() -> new com.ambition.neoworld.dungeon.definition.DungeonLootRule(50, 0, 5));
        var data = new com.ambition.neoworld.dungeon.definition.DungeonDefinitionData();
        var id = net.minecraft.resources.ResourceLocation.parse("test:random_rewards");
        var item = new net.minecraft.nbt.CompoundTag();
        item.putString("id", "minecraft:slime_ball"); item.putInt("count", 7);
        data.addLootPreset(id, List.of(item, new net.minecraft.nbt.CompoundTag()));
        check(data.lootRule(id, 0).equals(com.ambition.neoworld.dungeon.definition.DungeonLootRule.DEFAULT),
                "Saved chests start at 100 percent and original count");
        rejects(() -> data.setLootRule(id, 1, chance));
        rejects(() -> data.setLootRule(id, 54, chance));
        var definition = data.create("random_test").orElseThrow();
        data.updateReward(definition, new com.ambition.neoworld.dungeon.definition.DungeonRewardConfig(null, id));
        data.updateStatus(definition, com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.ENABLED);
        data.setLootRule(id, 0, chance);
        check(definition.status() == com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.DRAFT,
                "Changing rules resets linked definitions to DRAFT");
        var saved = data.save(new net.minecraft.nbt.CompoundTag(), null);
        var load = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.class.getDeclaredMethod("load",
                net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(restored.lootRule(id, 0).equals(chance), "Chance and amount survive reload");
        saved.getList("lootPresets", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("rules");
        var legacy = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(legacy.lootRule(id, 0).equals(com.ambition.neoworld.dungeon.definition.DungeonLootRule.DEFAULT),
                "Older presets without rules retain fixed rewards");
        var loot = com.ambition.neoworld.command.dungeon.DungeonCommand.buildCommand().build().getChild("admin").getChild("loot");
        check(loot.getChild("chance").getChild("name").getChild("slot").getChild("percent").getCommand() != null,
                "Chance command is registered");
        check(loot.getChild("amount").getChild("name").getChild("slot").getChild("minimum").getChild("maximum").getCommand() != null,
                "Amount command is registered");
    }

    private static void createRoomLayout() throws Exception {
        for (int[] viewport : new int[][] {{320, 240}, {426, 240}, {640, 360}, {1280, 720}, {1920, 1080}}) {
            var layout = com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fit(viewport[0], viewport[1]);
            check(layout.width() <= viewport[0] - 12 && layout.height() <= viewport[1] - 12,
                    "Create-room frame stays within viewport margins");
            check(layout.bodyTop() + layout.bodyHeight() <= layout.footerTop() - 12,
                    "Scrolling content never overlaps the fixed footer");
            check(layout.footerTop() + 28 <= layout.height() - 10, "Footer buttons stay inside the frame");
            check(layout.slotSize() >= 18 && layout.slotSize() <= 40, "Item previews retain native minimum dimensions");
            check(layout.rewardWidth() + 6 < layout.bodyWidth(), "Reward clipping stays inside the body");
            int scroll = Math.max(0, layout.contentHeight() - layout.bodyHeight());
            int rewardBottom = Math.round(178 * layout.spacing()) + layout.slotSize() + 10;
            check(rewardBottom - scroll <= layout.bodyHeight(), "Last reward and horizontal scrollbar remain reachable");
        }
        check(com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fit(320, 240).contentHeight()
                > com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.fit(320, 240).bodyHeight(),
                "Compact viewports use content scrolling instead of shrinking text");
        for (int i = 0; i < 3; i++) check(com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.modeIndex(1 << i) == i,
                "Fixed dungeon capacities map to existing command identifiers");
        for (int mask : new int[] {0, 3, 7, 8, -1}) check(
                com.ambition.neoworld.client.gui.dungeon.DungeonCreateRoomLayout.modeIndex(mask) == -1,
                "Missing, ambiguous and unsupported modes cannot create a room");
        var image = javax.imageio.ImageIO.read(Path.of(
                "src/main/resources/assets/neoworld/textures/gui/dungeon/create_room_v2_frame.png").toFile());
        check(image.getWidth() == 1448 && image.getHeight() == 1086 && (image.getRGB(0, 0) >>> 24) == 0,
                "Create-room frame has correct UV dimensions and transparent outer margin");
        check((image.getRGB(724, 543) >>> 24) > 240, "Installed frame retains its opaque empty interior");
        var sampling = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/neoworld/textures/gui/dungeon/create_room_v2_frame.png.mcmeta")))
                .getAsJsonObject().getAsJsonObject("texture");
        check(!sampling.get("blur").getAsBoolean() && sampling.get("clamp").getAsBoolean(), "New frame sampling is crisp");
    }

    private static void waitingRoom() {
        var owner = java.util.UUID.randomUUID();
        var second = java.util.UUID.randomUUID();
        var third = java.util.UUID.randomUUID();
        var members = List.of(
                new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.Member(owner, "Owner", 20, true, true, true),
                new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.Member(second, "Alex", 18, false, true, true),
                new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.Member(third, "Steve", 16, false, true, true));
        var entry = new DungeonBrowserDataPayload.Entry("slime", "Slime", 1, 1200, 2, 1, "Boss", "test:map",
                "test:reward", 0, 0, List.of(), true, "test:textures/slime.png");
        var payload = new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(true, 12, "Slime", "test:map",
                3, 1, 1200, 1, true, members, new DungeonBrowserDataPayload(List.of(entry)));
        check(payload.readyCount() == 3 && payload.canStart(owner), "Owner can start when all three online members are ready");
        check(!payload.canStart(second), "Members cannot use the owner's start control");
        var pending = new java.util.ArrayList<>(members);
        pending.set(2, new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.Member(third, "Steve", 16, false, false, true));
        var waiting = new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(false, 12, "Slime", "test:map", 3, 1, 1200, 1, true, pending);
        check(waiting.readyCount() == 2 && !waiting.canStart(owner), "One unready member blocks start");
        pending.set(2, new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.Member(third, "Steve", 16, false, true, false));
        var offline = new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(false, 12, "Slime", "test:map", 3, 1, 1200, 1, true, pending);
        check(offline.readyCount() == 2 && !offline.canStart(owner), "Offline ready flags do not count");
        var incomplete = new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(false, 12, "Slime", "test:map", 3, 1, 1200, 1, true, members.subList(0, 2));
        check(!incomplete.canStart(owner), "Room must be full");
        var active = new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(false, 12, "Slime", "test:map", 3, 1, 1200, 1, false, members);
        check(!active.canStart(owner), "Nonwaiting rooms cannot start again");
        var empty = new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(false, 0, "", "", 0, 0, 0, 0, false, List.of());
        for (var packet : List.of(payload, waiting, empty)) {
            var buffer = buffer();
            try {
                com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.STREAM_CODEC.encode(buffer, packet);
                check(com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.STREAM_CODEC.decode(buffer).equals(packet),
                        "Room codec preserves membership, image and reward preview data");
                check(buffer.readableBytes() == 0, "Room codec consumes the entire packet");
            } finally { buffer.release(); }
        }
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload(false, 12, "Slime", "test:map",
                3, 1, 1200, 1, true, members, new DungeonBrowserDataPayload(List.of(entry, entry))));
        var bar = new NeoScrollbar(0, 0, 6, 66, Component.literal("Members")).range(66, 6 * 22);
        check(bar.isScrollable(), "Six-member rooms scroll within the stable three-row viewport");
        bar.value(1000);
        check(bar.getValue() == 66, "Member scrolling reaches the sixth row exactly");
        bar.range(66, 22);
        check(!bar.isScrollable() && bar.getValue() == 0, "Solo member lists clear stale scroll offsets");
    }

    private static void dungeonSounds() {
        for (var cue : com.ambition.neoworld.dungeon.room.DungeonSoundEffects.Cue.values()) {
            check(!cue.steps().isEmpty() && cue.steps().getFirst().delay() == 0, "Every sound cue starts immediately");
            int previous = -1;
            for (var step : cue.steps()) {
                check(step.sound().getNamespace().equals("minecraft"), "Dungeon cues use vanilla sound resources");
                check(step.delay() >= previous && step.delay() <= 40, "Sound sequences are short and ordered");
                check(step.volume() > 0 && step.volume() <= .5F, "Dungeon sounds stay at restrained volume");
                previous = step.delay();
            }
        }
        var victory = com.ambition.neoworld.dungeon.room.DungeonSoundEffects.Cue.VICTORY.steps();
        check(victory.stream().filter(step -> step.sound().getPath().equals("entity.firework_rocket.blast")).count() == 2,
                "Victory has two timed firework blasts");
        check(victory.getLast().delay() == 18, "Victory ends with a short chime within one second");
        check(com.ambition.neoworld.dungeon.room.DungeonSoundEffects.Cue.READY.steps().getFirst().pitch()
                > com.ambition.neoworld.dungeon.room.DungeonSoundEffects.Cue.UNREADY.steps().getFirst().pitch(),
                "Ready and cancel-ready use distinct high and low feedback");
        rejects(() -> new com.ambition.neoworld.dungeon.room.DungeonSoundEffects.Step(-1,
                net.minecraft.resources.ResourceLocation.parse("minecraft:test"), .5F, 1));
        rejects(() -> new com.ambition.neoworld.dungeon.room.DungeonSoundEffects.Step(0,
                net.minecraft.resources.ResourceLocation.parse("minecraft:test"), Float.NaN, 1));
    }

    private static void catalog() throws Exception {
        JsonElement fixture = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/neoworld/loot_table/chests/dungeon/test_reward.json")));
        var preview = DungeonLootPreview.collect(fixture, id -> null, id -> List.of());
        check(preview.items().equals(List.of("minecraft:diamond", "minecraft:emerald", "minecraft:iron_ingot", "minecraft:golden_apple")),
                "Preview must include every possible fixture item, including conditional drops");
        check(preview.incomplete(), "Random count functions must flag approximate item previews");

        AtomicInteger calls = new AtomicInteger();
        var table = JsonParser.parseString("""
                {"pools":[{"entries":[
                  {"type":"minecraft:item","name":"minecraft:diamond"},
                  {"type":"minecraft:loot_table","value":"test:cycle"},
                  {"type":"minecraft:tag","name":"test:metals"},
                  {"type":"minecraft:alternatives","children":[
                    {"type":"minecraft:item","name":"minecraft:diamond"},
                    {"type":"minecraft:item","name":"minecraft:emerald"}]}]}]}
                """);
        var nested = DungeonLootPreview.collect(table, id -> { calls.incrementAndGet(); return table; },
                id -> List.of("minecraft:iron_ingot", "minecraft:diamond"));
        check(calls.get() == 1 && nested.items().size() == 3, "Cycles must terminate and duplicate types collapse");

        JsonArray entries = new JsonArray();
        for (int i = 0; i < 100; i++) {
            JsonObject item = new JsonObject(); item.addProperty("type", "minecraft:item"); item.addProperty("name", "test:item_" + i);
            entries.add(item);
        }
        JsonObject pool = new JsonObject(); pool.add("entries", entries);
        var bounded = DungeonLootPreview.collect(pool, id -> null, id -> List.of());
        check(bounded.items().size() == 64 && bounded.incomplete(), "Preview cap must mark partial catalogs");
        check(DungeonLootPreview.collect(JsonParser.parseString("{\"type\":\"test:dynamic\"}"),
                id -> null, id -> List.of()).incomplete(), "Unsupported entries must not claim a complete preview");
    }

    private static void codec() {
        var entry = new DungeonBrowserDataPayload.Entry("test", "Dungeon", 10, 0, 2, 3, "Boss",
                "test:mine", "test:reward", 500, 1000, List.of(), true, "test:textures/screenshots/mine.png");
        var payload = new DungeonBrowserDataPayload(List.of(entry));
        var buffer = buffer();
        try {
            DungeonBrowserDataPayload.STREAM_CODEC.encode(buffer, payload);
            check(DungeonBrowserDataPayload.STREAM_CODEC.decode(buffer).equals(payload), "Codec must preserve unlimited time and partial preview flag");
            check(buffer.readableBytes() == 0, "Codec must consume the whole packet");
        } finally { buffer.release(); }
        rejects(() -> new DungeonBrowserDataPayload(Collections.nCopies(257, entry)));
        for (int count : new int[] {-1, 257}) {
            var b = buffer();
            try { b.writeVarInt(count); rejects(() -> DungeonBrowserDataPayload.STREAM_CODEC.decode(b)); }
            finally { b.release(); }
        }
        for (int count : new int[] {-1, 65}) {
            var b = buffer();
            try {
                b.writeVarInt(1); b.writeUtf("test"); b.writeUtf("Dungeon");
                b.writeVarInt(1); b.writeVarInt(0); b.writeByte(1); b.writeVarInt(0);
                b.writeUtf(""); b.writeUtf(""); b.writeUtf(""); b.writeVarInt(0); b.writeVarLong(0);
                b.writeVarInt(count);
                rejects(() -> DungeonBrowserDataPayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
    }

    private static void scrollbars() {
        var vertical = new NeoScrollbar(20, 30, 6, 100, Component.literal("List")).range(100, 400);
        vertical.value(300);
        check(vertical.getThumbY() + vertical.getThumbHeight() == 129, "Vertical thumb must reach track end");
        vertical.range(100, 20);
        check(!vertical.isScrollable() && vertical.getValue() == 0, "Shrinking list clears stale scroll offset");
        var horizontal = new NeoScrollbar(40, 50, 100, 6, Component.literal("Rewards"))
                .orientation(NeoScrollbar.Orientation.HORIZONTAL).range(100, 400).scrollStep(20);
        horizontal.value(100);
        int thumb = horizontal.getThumbY();
        horizontal.onClick(thumb + 3, 52);
        horizontal.mouseDragged(1000, 52, 0, 800, 0);
        check(horizontal.getValue() == 300, "Horizontal drag clamps at final item");
        check(horizontal.getThumbY() + horizontal.getThumbHeight() == 139, "Horizontal thumb must reach right edge");
        horizontal.onRelease(1000, 52);
        horizontal.scroll(1);
        check(horizontal.getValue() == 280, "Wheel moves horizontal row by one slot");
        horizontal.active = false;
        check(!horizontal.scroll(1) && horizontal.getValue() == 280, "Disabled scrollbar cannot scroll");
    }


    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static void imageConfiguration() throws Exception {
        var data = new com.ambition.neoworld.dungeon.definition.DungeonDefinitionData();
        var definition = data.create("mine").orElseThrow();
        var image = net.minecraft.resources.ResourceLocation.parse("test:textures/screenshots/mine.png");
        data.updateStatus(definition, com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.ENABLED);
        data.updateImage(definition, image);
        check(definition.status() == com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.ENABLED,
                "Changing an image must preserve enabled status");
        var saved = data.save(new net.minecraft.nbt.CompoundTag(), null);
        var load = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.class.getDeclaredMethod("load",
                net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var restored = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(image.equals(restored.get("mine").orElseThrow().imageId()), "Image persists across saved-data reloads");
        saved.getList("definitions", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("imageId");
        var legacy = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(legacy.get("mine").orElseThrow().imageId() == null, "Older worlds without image fields retain default previews");
        data.updateImage(definition, null);
        check(definition.imageId() == null, "Clearing an image restores fallback");
        rejects(() -> data.updateImage(definition, net.minecraft.resources.ResourceLocation.parse("test:textures/mine.jpg")));
        rejects(() -> data.updateImage(definition, net.minecraft.resources.ResourceLocation.parse("test:mine.png")));
        var square = com.ambition.neoworld.client.gui.dungeon.DungeonPreviewImages.crop(1920, 1080, 52, 52);
        check(square.u() == 420 && square.v() == 0 && square.width() == 1080 && square.height() == 1080,
                "F2 landscape screenshots center-crop to square list thumbnails");
        var wide = com.ambition.neoworld.client.gui.dungeon.DungeonPreviewImages.crop(1920, 1080, 192, 108);
        check(wide.width() == 1920 && wide.height() == 1080, "Matching aspect ratio retains the full screenshot");
    }
    private static void dungeonHud() {
        for (var phase : com.ambition.neoworld.network.dungeon.DungeonHudPayload.Phase.values()) {
            var payload = new com.ambition.neoworld.network.dungeon.DungeonHudPayload(true, "Dungeon", phase,
                    2, 5, 8, "Boss", 68, 0, true, 2, 3);
            var view = com.ambition.neoworld.client.gui.overlay.DungeonHudPresentation.from(payload);
            check(!view.status().isBlank() && view.time().equals("ไม่จำกัด") && view.survivors().equals("2 / 3"),
                    "Each HUD phase has a status, explicit unlimited time and surviving member count");
            check(view.progress() >= 0 && view.progress() <= 1, "HUD progress remains bounded");
            if (phase == com.ambition.neoworld.network.dungeon.DungeonHudPayload.Phase.BOSS)
                check(view.objective().equals("Boss") && view.remaining().equals("HP 68%") && view.progress() == .68F,
                        "Boss presentation displays the actual boss name and remaining health");
            else if (phase == com.ambition.neoworld.network.dungeon.DungeonHudPayload.Phase.WAVE)
                check(view.objective().equals("Wave 2 / 5") && view.remaining().equals("ศัตรูเหลือ 8 ตัว"),
                        "Wave presentation labels the wave and remaining enemies separately");
            else check(view.remaining().isEmpty(), "Non-combat phases do not show stale enemy counts");
            var b = buffer();
            try {
                com.ambition.neoworld.network.dungeon.DungeonHudPayload.STREAM_CODEC.encode(b, payload);
                check(com.ambition.neoworld.network.dungeon.DungeonHudPayload.STREAM_CODEC.decode(b).equals(payload),
                        "HUD codec preserves each phase, unlimited time, boss HP and alive count");
                check(b.readableBytes() == 0, "HUD codec consumes the packet");
            } finally { b.release(); }
            com.ambition.neoworld.client.state.ClientDungeonHudData.update(payload);
            check(com.ambition.neoworld.client.state.ClientDungeonHudData.get().visible(), "Client accepts active HUD");
            com.ambition.neoworld.client.state.ClientDungeonHudData.reset();
            check(!com.ambition.neoworld.client.state.ClientDungeonHudData.get().visible(), "Disconnect clears HUD state");
        }
        rejects(() -> new com.ambition.neoworld.network.dungeon.DungeonHudPayload(true, "D",
                com.ambition.neoworld.network.dungeon.DungeonHudPayload.Phase.BOSS, 0, 0, 0, "Boss", 101, 0, false, 1, 1));
        var noWaves = new com.ambition.neoworld.network.dungeon.DungeonHudPayload(true, "D",
                com.ambition.neoworld.network.dungeon.DungeonHudPayload.Phase.WAITING_BOSS,
                0,0,0,"",0,65,false,1,1);
        var noWavesView = com.ambition.neoworld.client.gui.overlay.DungeonHudPresentation.from(noWaves);
        check(noWavesView.objective().equals("เตรียมบอส") && noWavesView.time().equals("01:05")
                && noWavesView.progress() == 1F, "Boss-only dungeons avoid Wave 0/0 and format timed runs correctly");
        var hidden = com.ambition.neoworld.network.dungeon.DungeonHudPayload.HIDDEN;
        var b = buffer();
        try {
            com.ambition.neoworld.network.dungeon.DungeonHudPayload.STREAM_CODEC.encode(b, hidden);
            check(com.ambition.neoworld.network.dungeon.DungeonHudPayload.STREAM_CODEC.decode(b).equals(hidden), "Hide packet round trips");
        } finally { b.release(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid packet length must be rejected");
    }
}
