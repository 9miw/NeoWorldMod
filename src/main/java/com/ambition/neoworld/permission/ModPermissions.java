package com.ambition.neoworld.permission;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

// คลาสลงทะเบียนและจัดการ Permission Nodes ตามมาตรฐาน NeoForge Permission API (รองรับ LuckPerms)
@EventBusSubscriber(modid = NeoWorld.MODID)
public class ModPermissions {

    // === Economy Permission Nodes ===
    public static final PermissionNode<Boolean> COMMAND_ECO_BALANCE = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.balance",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> true
    );

    public static final PermissionNode<Boolean> COMMAND_ECO_BALANCE_OTHERS = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.balance.others",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> COMMAND_ECO_SETCOIN = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.setcoin",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> COMMAND_ECO_GIVECOIN = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.givecoin",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> COMMAND_ECO_GIVE = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.give",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> COMMAND_ECO_TAKE = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.take",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> COMMAND_ECO_SET = new PermissionNode<>(
            NeoWorld.MODID,
            "command.eco.set",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    // === Level Permission Nodes ===
    public static final PermissionNode<Boolean> COMMAND_LEVEL_GET = new PermissionNode<>(
            NeoWorld.MODID,
            "command.level.get",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> true
    );

    public static final PermissionNode<Boolean> COMMAND_LEVEL_ADDEXP = new PermissionNode<>(
            NeoWorld.MODID,
            "command.level.addexp",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> COMMAND_LEVEL_SET = new PermissionNode<>(
            NeoWorld.MODID,
            "command.level.set",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && player.hasPermissions(2)
    );

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(
                COMMAND_ECO_BALANCE,
                COMMAND_ECO_BALANCE_OTHERS,
                COMMAND_ECO_SETCOIN,
                COMMAND_ECO_GIVECOIN,
                COMMAND_ECO_GIVE,
                COMMAND_ECO_TAKE,
                COMMAND_ECO_SET,
                COMMAND_LEVEL_GET,
                COMMAND_LEVEL_ADDEXP,
                COMMAND_LEVEL_SET
        );
    }

    /**
     * ฟังก์ชันตรวจสอบสิทธิ์ รองรับทั้ง Vanilla OP Level / Singleplayer Cheats / Console และ LuckPerms
     */
    public static boolean hasPermission(CommandSourceStack source, PermissionNode<Boolean> node, int fallbackOpLevel) {
        // ตรวจผู้เล่นผ่าน PermissionAPI ก่อน เพื่อให้ค่า deny มีผลแม้เป็น OP
        if (source.getEntity() instanceof ServerPlayer player) {
            return PermissionAPI.getPermission(player, node);
        }

        // Console และ Command Block ใช้ระดับสิทธิ์ของแหล่งคำสั่ง
        return source.hasPermission(fallbackOpLevel);
    }
}
