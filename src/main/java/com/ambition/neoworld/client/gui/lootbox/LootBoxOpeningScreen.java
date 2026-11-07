package com.ambition.neoworld.client.gui.lootbox;

import com.ambition.neoworld.client.gui.common.NeoWindowLayout;
import com.ambition.neoworld.client.gui.widget.NeoCloseButton;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.lootbox.LootBoxOpenedPayload;
import com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cosmetic only: the server has already exchanged the box for this reward. */
public final class LootBoxOpeningScreen extends Screen {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png");
    private List<ItemStack> rewards;
    private long started = Util.getMillis();
    private UUID opening;
    private static boolean effects = true;
    private static boolean preferencesLoaded;
    private static LootBoxPreferences preferences;
    private final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> sounds = new java.util.ArrayList<>();
    private int remaining;
    private boolean pending;
    private EditBox countInput;
    private NeoButton effectsButton, autoButton, stopButton;
    private String countText = "1";
    private ModelPart chest;
    private NeoWindowLayout window;
    private int playedSounds;

    private LootBoxOpeningScreen(LootBoxOpenedPayload payload) {
        super(Component.translatable("gui.neoworld.loot_box.title"));
        loadPreferences();
        receive(payload);
    }

    public static void open(LootBoxOpenedPayload payload) {
        var client = Minecraft.getInstance();
        if (client.screen instanceof LootBoxOpeningScreen screen) { screen.receive(payload); return; }
        if (payload.automatic()) {
            if (client.getConnection() != null) PacketDistributor.sendToServer(new LootBoxAutoRequestPayload(payload.opening(), 0));
            return; // Closing the result screen must not reopen it during an in-flight result.
        }
        client.setScreen(new LootBoxOpeningScreen(payload));
    }

    private void receive(LootBoxOpenedPayload payload) {
        if (minecraft != null) stopSounds();
        opening = payload.opening(); rewards = payload.rewards().stream().map(ItemStack::copy).toList();
        started = Util.getMillis() - (effects ? 0 : 3600); playedSounds = 0;
        if (!payload.automatic()) { remaining = 0; pending = false; }
    }

    private static void loadPreferences() {
        if (preferencesLoaded) return;
        preferencesLoaded = true;
        try {
            preferences = new LootBoxPreferences(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("neoworld/loot-box-client.properties"));
            effects = preferences.effects();
        } catch (java.io.IOException | IllegalArgumentException failure) {
            com.ambition.neoworld.NeoWorld.LOGGER.warn("Could not read loot box effect preference", failure);
        }
    }

    public static void autoState(int remaining) {
        var client = Minecraft.getInstance();
        if (client.screen instanceof LootBoxOpeningScreen screen) {
            screen.remaining = remaining; screen.pending = false;
            screen.updateControls();
        } else if (remaining > 0 && client.getConnection() != null) {
            PacketDistributor.sendToServer(new LootBoxAutoRequestPayload(new UUID(0,0), 0));
        }
    }

    @Override protected void init() {
        window = NeoWindowLayout.centered(width, height, 320, 320);
        chest = minecraft.getEntityModels().bakeLayer(ModelLayers.CHEST);
        addRenderableWidget(NeoCloseButton.create(window.closeX(), window.closeY(), window.closeSize(), this::onClose));
        int x = window.contentX(), available = window.contentWidth();
        int y = window.contentY() + animationHeight() + 4;
        effectsButton = addRenderableWidget(new NeoButton(Button.builder(effectLabel(), b -> toggleEffects()).bounds(x,y,available,20)));
        int inputWidth = Math.min(44, available / 4);
        int actionWidth = Math.max(1, (available - inputWidth - 8) / 2);
        countInput = new EditBox(font,x,y + 24,inputWidth,20,Component.translatable("gui.neoworld.loot_box.additional"));
        countInput.setMaxLength(2); countInput.setFilter(value -> value.matches("[0-9]{0,2}"));
        countInput.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.neoworld.loot_box.additional")));
        countInput.setValue(countText); countInput.setResponder(value -> { countText = value; updateControls(); });
        addRenderableWidget(countInput);
        autoButton = addRenderableWidget(new NeoButton(Button.builder(Component.translatable("gui.neoworld.loot_box.auto"), b -> {
            pending = true;
            PacketDistributor.sendToServer(new LootBoxAutoRequestPayload(opening,requestedCount(),!effects)); updateControls();
        }).bounds(x + inputWidth + 4,y + 24,actionWidth,20)).style(NeoButton.Style.PRIMARY));
        stopButton = addRenderableWidget(new NeoButton(Button.builder(Component.translatable("gui.neoworld.loot_box.stop"), b -> {
            pending = true; PacketDistributor.sendToServer(new LootBoxAutoRequestPayload(opening,0)); updateControls();
        }).bounds(x + inputWidth + actionWidth + 8,y + 24,actionWidth,20)));
        updateControls();
    }

    private int animationHeight() { return Math.max(1,window.contentHeight() - 56); }
    private int requestedCount() {
        try { return Integer.parseInt(countText); } catch (NumberFormatException invalid) { return 0; }
    }
    private Component effectLabel() { return Component.translatable(effects ? "gui.neoworld.loot_box.effects_on" : "gui.neoworld.loot_box.effects_off"); }
    private void toggleEffects() {
        effects = !effects;
        if (!effects) { started = Util.getMillis() - 3600; playedSounds = -1; stopSounds(); }
        effectsButton.setMessage(effectLabel());
        try {
            if (preferences == null) preferences = new LootBoxPreferences(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("neoworld/loot-box-client.properties"));
            preferences.setEffects(effects);
        } catch (java.io.IOException | IllegalArgumentException failure) {
            com.ambition.neoworld.NeoWorld.LOGGER.warn("Could not save loot box effect preference", failure);
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("gui.neoworld.loot_box.save_failed"),true);
        }
    }
    private void updateControls() {
        if (autoButton == null) return;
        autoButton.active = !pending && remaining == 0 && requestedCount() >= 1 && requestedCount() <= 64;
        effectsButton.active = !pending && remaining == 0;
        autoButton.setMessage(Component.translatable(remaining > 0 ? "gui.neoworld.loot_box.remaining" : "gui.neoworld.loot_box.auto",remaining));
        stopButton.active = remaining > 0 || pending;
        countInput.setEditable(remaining == 0 && !pending);
    }
    private void stopSounds() {
        for (var sound : sounds) minecraft.getSoundManager().stop(sound);
        sounds.clear();
    }
    @Override public void removed() {
        stopSounds();
        if (minecraft.getConnection() != null) PacketDistributor.sendToServer(new LootBoxAutoRequestPayload(opening,0));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (window.contentWidth() > 0 && window.contentHeight() > 0) {
            graphics.enableScissor(window.contentX(), window.contentY(),
                    window.contentX() + window.contentWidth(), window.contentY() + window.contentHeight());
            try { renderContent(graphics, mouseX, mouseY, partialTick); }
            finally { graphics.disableScissor(); }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!effects || Util.getMillis() - started >= 3600) renderRewardTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the world dimmer once, before the animation, without a frame or title plaque.
    }

    @Override public boolean isPauseScreen() { return false; }

    private void renderRewardTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        float scale = Math.min(1F, Math.min(window.contentWidth() / 280F, animationHeight() / 190F));
        int cx = window.contentX() + window.contentWidth() / 2;
        int cy = window.contentY() + animationHeight() / 2;
        int rows = (rewards.size() + 2) / 3;
        float half = (rewards.size() == 1 ? 19.2F : 12F) * scale;
        for (int index = 0; index < rewards.size(); index++) {
            int row = index / 3;
            int columns = Math.min(3, rewards.size() - row * 3);
            float x = cx + (index % 3 - (columns - 1) / 2F) * 32 * scale;
            float y = cy + (-62 + (row - (rows - 1) / 2F) * 28) * scale;
            if (Math.abs(mouseX - x) <= half && Math.abs(mouseY - y) <= half) {
                graphics.renderTooltip(font, rewards.get(index), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public void tick() {
        if (!effects) return;
        float elapsed = (Util.getMillis() - started) / 1000F;
        playCue(elapsed, .35F, 0, SoundEvents.CHEST_LOCKED, .85F, .35F);
        playCue(elapsed, 1.05F, 1, SoundEvents.CHEST_LOCKED, 1.15F, .4F);
        playCue(elapsed, 1.7F, 2, SoundEvents.CHEST_OPEN, 1F, .7F);
        playCue(elapsed, 2.1F, 3, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2F, .3F);
        playCue(elapsed, 2.35F, 4, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.1F, .45F);
        playCue(elapsed, 2.6F, 5, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25F, .5F);
    }

    private void playCue(float elapsed, float at, int cue, SoundEvent sound, float pitch, float volume) {
        int bit = 1 << cue;
        if (elapsed < at || (playedSounds & bit) != 0) return;
        playedSounds |= bit;
        // Skip stale cues after a stalled frame instead of playing them all together.
        if (elapsed - at < .3F) {
            var instance = SimpleSoundInstance.forUI(sound, pitch, volume);
            sounds.add(instance); minecraft.getSoundManager().play(instance);
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float seconds = effects ? Math.min(3.6F, Math.max(0, (Util.getMillis() - started) / 1000F)) : 3.6F;
        float opening = Math.clamp((seconds - 1.7F) / .7F, 0F, 1F);
        float rise = Math.clamp((seconds - 2.1F) / .9F, 0F, 1F);
        float easedRise = 1F - (1F - rise) * (1F - rise);
        int cx = window.contentX() + window.contentWidth() / 2;
        int cy = window.contentY() + animationHeight() / 2;
        // Preserve the whole motion and captions on smaller GUI viewports.
        float scale = Math.min(1F, Math.min(window.contentWidth() / 280F, animationHeight() / 190F));
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.pose().pushPose();
        float shake = seconds < .35F || seconds >= 1.7F ? 0F
                : (float) Math.sin(seconds * 65) * (1F + seconds * 1.5F);
        graphics.pose().translate(shake, 28, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(shake));
        renderChest(graphics, opening);
        graphics.pose().popPose();
        if (effects) { renderOpeningLight(graphics, seconds, opening, shake); renderCelebration(graphics, seconds); }
        if (rise > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 5 - easedRise * 67, 200);
            float itemScale = .6F + easedRise * (rewards.size() == 1 ? 1.8F : .9F);
            int rows = (rewards.size() + 2) / 3;
            for (int index = 0; index < rewards.size(); index++) {
                int row = index / 3;
                int columns = Math.min(3, rewards.size() - row * 3);
                graphics.pose().pushPose();
                graphics.pose().translate((index % 3 - (columns - 1) / 2F) * 32, (row - (rows - 1) / 2F) * 28, 0);
                graphics.pose().scale(itemScale, itemScale, 1F);
                graphics.renderItem(rewards.get(index), -8, -8);
                graphics.renderItemDecorations(font, rewards.get(index), -8, -8);
                graphics.pose().popPose();
            }
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
        boolean finished = seconds >= 3.6F;
        Component label = finished
                ? rewards.size() == 1
                    ? Component.translatable("gui.neoworld.loot_box.reward", rewards.getFirst().getHoverName(), rewards.getFirst().getCount())
                    : Component.translatable(rewards.isEmpty() ? "gui.neoworld.loot_box.empty" : "gui.neoworld.loot_box.rewards", rewards.size())
                : Component.translatable(seconds < 1.7F ? "gui.neoworld.loot_box.shaking" : "gui.neoworld.loot_box.opening");
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), Math.max(1, window.contentWidth() - 8)),
                cx, window.contentY() + animationHeight() - 12, finished ? 0xEAC978 : 0xE7ECEF);
    }

    private void renderCelebration(GuiGraphics graphics, float seconds) {
        if (seconds <= 1.7F || seconds >= 3.6F) return;
        float fade = 1F - Math.clamp((seconds - 2.8F) / .8F, 0F, 1F);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 6, 185);
            // Shafts revolve around the vertical opening axis, always pointing upward.
            for (int ray = 0; ray < 6; ray++) {
                float phase = seconds * 3F + ray * (float) Math.PI / 3F;
                graphics.pose().pushPose();
                graphics.pose().translate(Math.cos(phase) * 10, 0, 0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) Math.sin(phase) * 38F));
                for (int distance = 0; distance < 84; distance += 4) {
                    int half = 1 + distance / 24;
                    int alpha = Math.round(100F * fade * (1F - distance / 84F));
                    graphics.fill(-half, -distance - 4, half, -distance, (alpha << 24) | 0xFFF0B5);
                }
                graphics.pose().popPose();
            }
            float burst = seconds - 2.3F;
            if (burst > 0) {
                for (int spark = 0; spark < 28; spark++) {
                    double angle = spark * Math.PI * 2 / 28;
                    float speed = 30F + (spark % 5) * 5F;
                    int x = (int) Math.round(Math.cos(angle) * speed * burst);
                    int y = (int) Math.round(-48 + Math.sin(angle) * speed * burst + 24 * burst * burst);
                    int alpha = Math.round(230F * Math.max(0F, 1F - burst / 1.3F));
                    int color = (alpha << 24) | (spark % 3 == 0 ? 0x86E9DF : 0xFFE5A0);
                    graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
                    if (spark % 4 == 0) {
                        graphics.fill(x - 3, y, x + 4, y + 1, color);
                        graphics.fill(x, y - 3, x + 1, y + 4, color);
                    }
                }
            }
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderOpeningLight(GuiGraphics graphics, float seconds, float opening, float shake) {
        if (seconds <= .8F) return;
        float strength = seconds < 1.7F ? (seconds - .8F) / .9F * .25F : .55F + opening * .3F;
        graphics.pose().pushPose();
        try {
            // Composite above the chest (Z=100), below the reward (Z=200).
            // Anchor at its opening, rather than letting the opaque model hide the beam.
            graphics.pose().translate(shake, 6, 180);
            for (int distance = 0; distance < 105; distance += 3) {
                int half = 8 + distance / 2;
                float fade = 1F - distance / 105F;
                int alpha = Math.round(strength * fade * 175);
                graphics.fill(-half, -distance - 3, half, -distance, (alpha << 24) | 0xEAC978);
            }
            int coreAlpha = Math.round(strength * 210);
            graphics.fill(-12, -3, 12, 0, (coreAlpha << 24) | 0xFFF0B5);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderChest(GuiGraphics graphics, float opening) {
        // Same baked geometry, hinge, lock movement and cubic easing as Vanilla ChestRenderer.
        float eased = 1F - (float) Math.pow(1F - opening, 3);
        chest.getChild("lid").xRot = -eased * (float) Math.PI / 2F;
        chest.getChild("lock").xRot = chest.getChild("lid").xRot;
        graphics.flush();
        graphics.pose().pushPose();
        Lighting.setupForEntityInInventory();
        try {
            graphics.pose().translate(0, 0, 100);
            graphics.pose().scale(67.5F, -67.5F, 67.5F);
            graphics.pose().mulPose(Axis.XP.rotationDegrees(20));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(-25));
            graphics.pose().translate(-.5F, -.45F, -.5F);
            var vertices = graphics.bufferSource().getBuffer(RenderType.entityCutoutNoCull(CHEST));
            chest.render(graphics.pose(), vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            // GuiGraphics.flush disables depth testing for 2D layers. Submit this 3D
            // batch directly so the lid, lock and body occlude each other correctly.
            graphics.bufferSource().endBatch();
        } finally {
            graphics.pose().popPose();
            Lighting.setupFor3DItems();
        }
    }
}
