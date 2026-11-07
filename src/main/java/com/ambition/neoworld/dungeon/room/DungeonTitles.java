package com.ambition.neoworld.dungeon.room;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonTitles {
    private DungeonTitles() {}

    public record Notice(String title, String subtitle, ChatFormatting color, int stayTicks) {
        public void send(ServerPlayer player) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, stayTicks, 10));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color)));
        }
    }

    public static Notice start(String name) {
        return new Notice("เริ่มการผจญภัย!", name, ChatFormatting.GOLD, 40);
    }

    public static Notice wave(int number) {
        return new Notice("WAVE " + number, "กำจัดศัตรูทั้งหมด", ChatFormatting.YELLOW, 35);
    }

    public static Notice cleared(boolean nextWave) {
        return new Notice("ผ่านเวฟแล้ว!", nextWave ? "เวฟถัดไปใน 3 วินาที" : "บอสจะปรากฏใน 3 วินาที",
                ChatFormatting.GREEN, 40);
    }

    public static Notice boss(String name) {
        return new Notice("บอสปรากฏตัว!", name, ChatFormatting.RED, 50);
    }

    public static Notice victory() {
        return new Notice("สำเร็จ!", "รับรางวัลที่กล่อง แล้วไปยังทางออก", ChatFormatting.GREEN, 70);
    }

    public static Notice death(boolean solo) {
        return new Notice("คุณเสียชีวิตแล้ว", solo ? "ออกจากดันใน 10 วินาที" : "รอชมเพื่อน หรือใช้ /neow dungeon exit",
                ChatFormatting.RED, 60);
    }

    public static Notice defeat() {
        return new Notice("ปาร์ตี้พ่ายแพ้", "กำลังกลับออกจากดันเจี้ยน", ChatFormatting.RED, 60);
    }

    public static Notice timeout() {
        return new Notice("หมดเวลา!", "การท้าทายสิ้นสุดลง", ChatFormatting.RED, 60);
    }
}
