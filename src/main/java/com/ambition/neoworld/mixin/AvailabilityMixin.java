package com.ambition.neoworld.mixin;

import com.ambition.neoworld.client.ClientLevelData;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Availability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Availability.class, remap = false)
public class AvailabilityMixin {

    /**
     * ดักจับการอ่านค่าเลเวลของผู้เล่น (เดิมคือ player.experienceLevel) ในเงื่อนไข Availability ของ Custom NPCs
     * และเปลี่ยนให้ดึงค่าเลเวลจากระบบ NeoWorld แทน
     */
    @Redirect(
            method = "isAvailable(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
                    remap = true
            )
    )
    private int neoworld$redirectExperienceLevel(Player player) {
        if (player == null) {
            return 0;
        }

        // กรณีอยู่ฝั่ง Client ให้ดึงจากข้อมูลชั่วคราว ClientLevelData สำหรับเรนเดอร์หน้าต่าง GUI/Dialog
        if (player.level().isClientSide()) {
            return ClientLevelData.getLevel();
        }

        // กรณีอยู่ฝั่ง Server ให้ดึงจาก NeoWorld PlayerData Attachments
        if (player.hasData(ModAttachments.PLAYER_DATA)) {
            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            return data.getLevel();
        }

        return player.experienceLevel;
    }
}
