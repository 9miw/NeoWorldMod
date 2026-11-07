package com.ambition.neoworld.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/** Reads the registered command tree so syntax and permission checks stay in sync. */
public final class CommandHelp {
    private static final int PAGE_SIZE = 4;
    private static final List<String> SYSTEMS = List.of("general", "eco", "level", "fashion", "party",
            "title", "dungeon", "pvp", "market", "shop", "questnav", "world", "profile", "trade", "economyreport");
    private static final Map<String, String> DETAILS = Map.ofEntries(
            Map.entry("general", "ทั่วไป: เปิด Hub ด้วย /neow hub หรือหน้ากิจกรรมด้วย /neow activity"),
            Map.entry("eco", "เศรษฐกิจ: ดูและจัดการยอดเงิน silver/diamond; setcoin ใช้เหรียญในมือหลัก; give/take ต้องมากกว่า 0 ส่วน set ใช้ 0 ได้"),
            Map.entry("level", "เลเวล: get ดูข้อมูลตนเอง; ไม่ใส่ target จะใช้กับตนเอง; item/expitem แก้ไอเทมในมือหลัก; maximum ต้องไม่น้อยกว่า minimum"),
            Map.entry("fashion", "แฟชั่น: set ใส่แฟชั่นที่ปลดล็อกแล้วจากไอเทมในมือ; clear ถอดช่องที่เลือก; กด Tab เพื่อดูชื่อช่อง"),
            Map.entry("party", "ปาร์ตี้: trio/3 รองรับ 3 คน raid/6 รองรับ 6 คน; invite ส่งคำเชิญ; accept/decline ระบุชื่อหัวหน้าที่เชิญ"),
            Map.entry("title", "ฉายา: list ดู ID ที่ปลดล็อกแล้ว; equip ใช้ ID ของฉายา; clear ถอดฉายา; admin reload โหลดรายการใหม่ และ unlockall ปลดล็อกให้ผู้เล่น"),
            Map.entry("dungeon", "ดันเจี้ยน: room create เลือก solo/trio/raid; สมาชิกกด ready ก่อนเริ่ม; admin ใช้ตั้งค่าดัน แผนที่ และรางวัล; clone_tab คือแท็บโคลน NPC 1–9"),
            Map.entry("pvp", "PvP: queue/unqueue เข้า/ออกคิวจัดอันดับ; room สร้างและเข้าห้อง; admin จัดการสนามและแผนที่; validate ตรวจความพร้อม"),
            Map.entry("market", "ตลาดผู้เล่น: sell ขายของในมือ ราคาต่อชิ้น; count เริ่มต้น 1 ระยะเวลาเริ่มต้น 2880 นาที สูงสุด 43200 นาที; buy ไม่ใส่ count ซื้อทั้งประกาศ; claim รับของ/เงินค้าง"),
            Map.entry("shop", "ร้านค้า: ใช้รหัสร้านหรือชื่อที่ครอบด้วยเครื่องหมายคำพูด; add ใช้สินค้าในมือ ราคา buy/sell ต่อชิ้น สกุลเงินเริ่มต้น silver หมวดเริ่มต้น ทั่วไป; bind เลือก NPC ที่ไม่ใช่ผู้เล่น"),
            Map.entry("world", "โลก: สำหรับ OP 2; import ใช้ชื่อโฟลเดอร์ต้นทางและ ID โลก; tp รองรับ ~ และ ^; random รัศมีเริ่มต้น 1000 สูงสุด 10000; reset now ล้างโลก ต้องยืนยัน confirm"),
            Map.entry("profile", "โปรไฟล์: เปิดข้อมูลตนเอง ใช้ไม่ได้ขณะตายหรือเป็นผู้ชม"),
            Map.entry("economyreport", "รายงานเศรษฐกิจ: สำหรับ OP 2; status ดูสถานะ และ export [days] ส่งออกรายงาน ค่าเริ่มต้น 28 วัน"),
            Map.entry("questnav", "นำทางเควสต์: track เลือกเควสต์ stop หยุด; admin bindnpc ใช้ NPC ที่มองอยู่ และ bind ใช้ตำแหน่งที่ยืนกับรัศมี; turnin ที่ตั้งเองมีลำดับก่อน NPC อัตโนมัติ"),
            Map.entry("trade", "แลกเปลี่ยน: ระบุชื่อผู้เล่นออนไลน์เพื่อส่งคำขอ; คำสั่งมีเมื่อระบบ Trade เปิดใช้งาน"));

    private CommandHelp() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("help")
                .executes(c -> show(c.getSource(), "all", 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(c -> show(c.getSource(), "all", IntegerArgumentType.getInteger(c, "page"))))
                .then(Commands.argument("system", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SYSTEMS, b))
                        .executes(c -> show(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "system"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> show(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "system"),
                                        IntegerArgumentType.getInteger(c, "page")))));
    }

    private record Entry(String system, String syntax, String example, String action) {}

    private static List<Entry> entries(CommandSourceStack source, String filter) {
        var dispatcher = source.getServer().getCommands().getDispatcher();
        var result = new ArrayList<Entry>();
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("neow");
        if (root == null) return result;
        for (String system : SYSTEMS) {
            if (!filter.equals("all") && !filter.equals(system)) continue;
            if (system.equals("general")) {
                for (String name : List.of("hub", "activity")) {
                    var node = root.getChild(name);
                    if (node != null) collect(source, node, system, "/neow", "/neow", name, result);
                }
            } else {
                var node = root.getChild(system);
                if (node != null) collect(source, node, system, "/neow", "/neow", system, result);
            }
        }
        return result;
    }

    private static void collect(CommandSourceStack source, CommandNode<CommandSourceStack> node, String system,
            String syntax, String example, String action, List<Entry> result) {
        if (!node.canUse(source)) return;
        boolean argument = node instanceof ArgumentCommandNode<?, ?>;
        String nextSyntax = syntax + (syntax.isEmpty() ? "/" : " ") + (argument ? "<" + node.getName() + ">" : node.getName());
        String nextExample = example + (example.isEmpty() ? "/" : " ") + (argument ? example(source, node, system, syntax) : node.getName());
        String nextAction = argument ? action : node.getName();
        if (node.getCommand() != null) result.add(new Entry(system, nextSyntax, nextExample, nextAction));
        for (var child : node.getChildren()) collect(source, child, system, nextSyntax, nextExample, nextAction, result);
    }

    private static String example(CommandSourceStack source, CommandNode<CommandSourceStack> node, String system, String path) {
        String name = node.getName();
        if (system.equals("questnav")) {
            if (List.of("blocks", "radius", "radiusHere").contains(name)) return "5";
            if (name.equals("npc")) return "<UUIDของCustomNPCs>";
            if (List.of("pos", "from").contains(name)) return "~ ~ ~";
            if (name.equals("to")) return "~8 ~ ~";
        }
        String player = source.getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "PlayerName";
        if (List.of("player", "players", "target", "leader").contains(name)) return player;
        if (node instanceof ArgumentCommandNode<?, ?> arg) {
            if (arg.getType() instanceof IntegerArgumentType type) return Integer.toString(Math.max(1, type.getMinimum()));
            if (arg.getType() instanceof LongArgumentType type) return Long.toString(Math.max(1, type.getMinimum()));
            if (arg.getType() instanceof DoubleArgumentType type) return Double.toString(Math.max(25, type.getMinimum()));
        }
        return switch (name) {
            case "type" -> system.equals("eco") ? "silver" : "solo";
            case "size" -> "trio";
            case "slot" -> "head";
            case "title" -> "hero";
            case "shop" -> "\"ร้านตัวอย่าง\"";
            case "npc" -> "@e[type=minecraft:villager,limit=1,sort=nearest]";
            case "position" -> "~ ~ ~";
            case "world" -> "neoworld:resource_world";
            case "folder" -> "lobby";
            case "token" -> "<tokenที่ได้รับหลังขอลบ>";
            case "resource" -> path.contains("image") ? "neoworld:textures/gui/dungeon/previews/forgotten_mine.png"
                    : "neoworld:" + (system.equals("pvp") ? "pvp/arena01" : "dungeon/map01");
            case "loot_table" -> "slime_reward";
            case "clone_name" -> "Slime";
            case "values" -> "solo trio raid";
            case "value" -> "Forgotten Mine";
            case "category" -> "ทั่วไป";
            case "name" -> system.equals("world") ? "neoworld:lobby" : system.equals("shop") ? "ร้านตัวอย่าง"
                    : path.contains("loot") ? "slime_reward" : "map01";
            case "id", "dungeon" -> system.equals("pvp") ? "arena01" : "dung01";
            case "arena" -> "arena01";
            default -> "<" + name + ">";
        };
    }

    private static int show(CommandSourceStack source, String requested, int page) {
        String filter = requested.equals("economy") ? "eco" : requested;
        if (!filter.equals("all") && !SYSTEMS.contains(filter)) {
            source.sendFailure(Component.literal("ไม่พบระบบ ใช้ /neow help เพื่อดูสารบัญ"));
            return 0;
        }
        var entries = entries(source, filter);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            source.sendFailure(Component.literal("ไม่มีหน้านี้ มีทั้งหมด " + pages + " หน้า"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("━━ NeoWorld Help • " + filter + " • " + page + "/" + pages + " ━━")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("<ค่า> ต้องระบุ • รูปแบบสั้นและยาวแสดงแยกกัน • กด Tab ดูค่าที่รองรับ")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("ใช้ /neow help <หน้า> หรือ /neow help <ระบบ> <หน้า> • /neow ใช้แทน /neow ได้")
                .withStyle(ChatFormatting.GRAY), false);
        if (filter.equals("all")) {
            var index = Component.literal("ระบบ: ");
            for (String system : SYSTEMS) {
                if (entries.stream().anyMatch(e -> e.system().equals(system)))
                    index.append(button("[" + system + "] ", "/neow help " + system, true));
            }
            source.sendSuccess(() -> index, false);
        }
        String lastSystem = "";
        for (Entry entry : entries.subList(Math.min((page - 1) * PAGE_SIZE, entries.size()), Math.min(page * PAGE_SIZE, entries.size()))) {
            if (!entry.system().equals(lastSystem)) {
                source.sendSuccess(() -> Component.literal(DETAILS.get(entry.system())).withStyle(ChatFormatting.AQUA), false);
                lastSystem = entry.system();
            }
            source.sendSuccess(() -> button(entry.syntax(), entry.example(), false), false);
            source.sendSuccess(() -> Component.literal("  วิธีใช้: " + description(entry)), false);
            source.sendSuccess(() -> button("  ตัวอย่าง: " + entry.example(), entry.example(), false), false);
        }
        if (entries.isEmpty()) source.sendSuccess(() -> Component.literal("ไม่มีคำสั่งที่ใช้งานได้สำหรับระบบนี้"), false);
        source.sendSuccess(() -> Component.literal("ตัวอย่างใช้ชื่อ/ID สมมติ เปลี่ยนให้ตรงกับข้อมูลจริงก่อนส่ง; คลิกตัวอย่างเพื่อเติมในแชท")
                .withStyle(ChatFormatting.GRAY), false);
        String base = "/neow help " + (filter.equals("all") ? "" : filter + " ");
        var navigation = Component.literal("");
        if (page > 1) navigation.append(button("[ย้อนกลับ] ", base + (page - 1), true));
        navigation.append(button("[สารบัญ] ", "/neow help", true));
        if (page < pages) navigation.append(button("[ถัดไป]", base + (page + 1), true));
        source.sendSuccess(() -> navigation, false);
        return 1;
    }

    private static Component button(String label, String command, boolean run) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(run ? "คลิกเพื่อเปิดหน้า" : "คลิกเติมคำสั่ง แล้วตรวจค่าก่อนกด Enter"))));
    }

    private static String description(Entry entry) {
        String path = entry.syntax();
        if (entry.system().equals("questnav")) {
            return switch (entry.action()) {
                case "track" -> "เริ่มนำทางเควสต์ที่รับแล้วตาม quest ID ครั้งละหนึ่งเควสต์";
                case "stop" -> "หยุดนำทางเควสต์ที่ติดตามอยู่";
                case "objective", "turnin" -> path.contains("bindnpc")
                        ? "หันมอง Custom NPCs ภายใน 16 บล็อก หรือระบุ UUID; turnin ที่ตั้งเองใช้ก่อนการค้นหาอัตโนมัติ"
                        : "ใช้ตำแหน่งที่ยืนกับรัศมี 1–32 บล็อก หรือระบุพิกัดและรัศมี";
                case "view" -> "แสดงเส้นเชื่อมใกล้ตัวชั่วคราว";
                case "radius" -> "ตั้งระยะเชื่อมบล็อกนำทางอัตโนมัติ 1–16 บล็อก";
                case "rebuild" -> "จัดคิวตรวจและเชื่อมจุดใน chunk ที่โหลดอยู่ของมิติปัจจุบัน";
                case "link", "unlink" -> "เพิ่มหรือตัดเส้นเชื่อมระหว่างพิกัดบล็อกนำทางสองจุด";
                case "unbind" -> "ลบจุดทำภารกิจและจุดส่งที่ผูกไว้สำหรับ quest ID นี้";
                default -> "ดูวิธีใช้งานและเลือกคำสั่งนำทางเควสต์";
            };
        }
        if (entry.system().equals("fashion") && path.contains(" set"))
            return "ถือไอเทมแฟชั่นที่ปลดล็อกแล้ว เลือกช่อง head/face/back/body/legs/feet ที่ไอเทมรองรับ";
        if (entry.system().equals("fashion") && path.contains(" clear"))
            return "ถอดแฟชั่นออกจากช่อง head/face/back/body/legs/feet ที่ระบุ";
        if (entry.system().equals("eco") && path.contains(" admin set "))
            return "ตั้งยอดเงินผู้เล่นเป็นจำนวนที่ระบุ (ตั้งแต่ 0) เลือก silver/diamond";
        if (entry.system().equals("level") && path.contains(" expitem set"))
            return "ถือไอเทม NeoWorld EXP แล้วกำหนดช่วงสุ่มขั้นต่ำ/สูงสุดตั้งแต่ 1; สูงสุดต้องไม่น้อยกว่าขั้นต่ำ";
        if (entry.system().equals("level") && path.contains(" item clear"))
            return "ล้างเลเวลที่กำหนดเฉพาะไอเทมในมือ แล้วกลับไปใช้ค่าเลเวลจาก config";
        if (entry.system().equals("level") && path.contains(" item set"))
            return "ถือไอเทมแล้วกำหนดเลเวลขั้นต่ำสำหรับใช้งานไอเทมนั้น";
        if (entry.system().equals("level") && path.contains(" admin set"))
            return "ตั้งเลเวลผู้เล่น; ไม่ใส่ target จะใช้กับตนเอง";
        if (entry.system().equals("world") && path.contains(" random"))
            return "ย้ายผู้เล่นไปจุดสุ่มที่ปลอดภัยในโลกที่ระบุ; radius 1–10000 ค่าเริ่มต้น 1000";
        if (entry.system().equals("world") && path.contains(" reset add"))
            return "ตั้งรอบรีโลกทุก days วัน (1–3650) ตามเวลาจริง; การตั้งค่าเริ่มนับรอบใหม่";
        if (entry.system().equals("world") && path.contains("import add"))
            return "นำเข้าโฟลเดอร์โลกเป็น ID ที่ระบุและโหลดเข้าเกม; experimental อนุญาตรูปแบบทดลอง";
        if (entry.system().equals("dungeon") && path.contains(" image clear"))
            return "ล้างภาพตัวอย่างดันเจี้ยนเพื่อใช้ภาพเริ่มต้น";
        if (entry.system().equals("dungeon") && path.contains(" wave clear"))
            return "ล้างรายการมอนสเตอร์ทุก wave ของดันเจี้ยนที่ระบุ";
        if (entry.system().equals("dungeon") && path.contains(" wave add"))
            return "เพิ่ม wave มอนสเตอร์จาก clone_tab 1–9 และชื่อ NPC โคลน (ชื่อมีช่องว่างได้)";
        if (entry.system().equals("dungeon") && path.contains(" set ") && entry.action().equals("level"))
            return "กำหนดเลเวลขั้นต่ำสำหรับเข้าดันเจี้ยน ตั้งแต่ 1–100";
        if (entry.system().equals("shop") && path.contains(" admin add"))
            return "ถือสินค้าในมือ ระบุร้าน ราคา buy/sell ตั้งแต่ 0 แล้วเลือก silver/diamond และหมวดได้";
        return switch (entry.action()) {
            case "help", "admin" -> "แสดงรายการคำสั่งและวิธีใช้";
            case "hub", "activity", "gui", "open", "profile", "shop", "dungeon", "pvp", "room" -> "เปิดหน้าระบบหรือหน้าห้อง";
            case "eco", "balance" -> "ดูยอดเงินของตนเอง หรือผู้เล่นที่ระบุในคำสั่ง admin";
            case "level", "get" -> "ดูเลเวลและ EXP ของตนเอง";
            case "give" -> "เพิ่มยอดเงินให้ผู้เล่นตามสกุลเงินและจำนวน";
            case "take" -> "หักยอดเงินผู้เล่น ต้องมีเงินเพียงพอ";
            case "setcoin" -> "ถือเหรียญในมือหลัก แล้วระบุมูลค่าต่อเหรียญ";
            case "addexp" -> "เพิ่ม EXP; ไม่ระบุผู้เล่นจะเพิ่มให้ตนเอง";
            case "removeexp" -> "ลด EXP พร้อมเลเวลและยอดสะสม; ไม่ระบุผู้เล่นจะลดให้ตนเอง";
            case "set" -> "กำหนดค่าตามชนิดข้อมูลและลำดับพารามิเตอร์ที่แสดง";
            case "list", "my", "definitions", "instances", "info", "selection", "rank", "top" -> "แสดงรายการหรือข้อมูล; หมายเลข page ใช้เลือกหน้า";
            case "create" -> "สร้างร้าน ปาร์ตี้ ห้อง หรือข้อมูลระบบตามค่าที่ระบุ";
            case "invite", "trade" -> "ส่งคำเชิญไปยังชื่อผู้เล่นออนไลน์ที่ระบุ";
            case "accept", "decline" -> "ตอบรับหรือปฏิเสธคำเชิญจากหัวหน้าปาร์ตี้ที่ระบุ";
            case "join" -> "เข้าห้องตามหมายเลข ID";
            case "leave", "exit" -> "ออกจากปาร์ตี้ ห้อง หรือดันเจี้ยนปัจจุบัน";
            case "kick" -> "หัวหน้าปาร์ตี้นำสมาชิกที่ระบุออก";
            case "ready" -> "สลับสถานะพร้อมของตนเอง";
            case "start" -> "เริ่มห้องเมื่อสมาชิกและเงื่อนไขพร้อม";
            case "disband" -> "หัวหน้ายุบปาร์ตี้หรือห้อง";
            case "equip" -> "ใส่ฉายาที่ปลดล็อกแล้ว ระบุ ID จาก list";
            case "clear" -> "ถอดหรือล้างข้อมูลตามระบบและพารามิเตอร์; world/dungeon อาจล้างพื้นที่";
            case "reload" -> "โหลดรายการฉายาจากไฟล์ใหม่";
            case "unlockall" -> "ปลดล็อกฉายาทั้งหมดให้ผู้เล่นที่ระบุ";
            case "queue", "unqueue" -> "เข้าหรือออกคิว PvP จัดอันดับ";
            case "sell" -> "ลงขายของในมือ ระบุราคาต่อชิ้น จำนวน และระยะเวลาเป็นนาทีตามลำดับ";
            case "buy" -> "ซื้อประกาศตาม ID; ไม่ระบุจำนวนจะซื้อทั้งประกาศ";
            case "claim" -> "รับเงินและสินค้าที่ค้างอยู่";
            case "cancel" -> "ยกเลิกประกาศ ห้อง หรือการเลือกพื้นที่ตามระบบ";
            case "add" -> "เพิ่มข้อมูล; Shop ใช้สินค้าที่ถืออยู่และราคา buy/sell; reset ใช้จำนวนวัน";
            case "remove" -> "นำรายการที่ระบุออก; reset remove ยกเลิกรอบรีอัตโนมัติ";
            case "bind", "unbind" -> "ผูกหรือยกเลิกร้านค้ากับ NPC ที่เลือก";
            case "tool", "pos1", "pos2" -> "รับไม้เลือกพื้นที่ หรือกำหนดมุมตามตำแหน่งปัจจุบัน";
            case "save", "update" -> "บันทึกหรือแทนที่พื้นที่ที่เลือก; loot save ใช้กล่องที่เล็งอยู่";
            case "delete", "confirm" -> "ขอลบข้อมูล แล้วใช้ token จากข้อความตอบกลับเพื่อยืนยัน; reset confirm รีโลกทันที";
            case "validate", "enable", "disable" -> "ตรวจความพร้อม เปิด หรือปิดใช้งานข้อมูลที่ระบุ";
            case "build" -> "วางโครงสร้างดันเจี้ยนใน slot ที่ระบุ";
            case "chance" -> "กำหนดโอกาสรางวัลของช่อง 1–54 เป็นเปอร์เซ็นต์ 0–100";
            case "amount" -> "กำหนดจำนวนรางวัลต่ำสุดและสูงสุด 1–99; สูงสุดต้องไม่น้อยกว่าต่ำสุด";
            case "name", "image", "time", "modes", "structure", "boss", "reward", "scan" -> "ตั้งค่าดันเจี้ยนตามชื่อพารามิเตอร์; time เป็นวินาที; scan อ่าน structure blocks";
            case "round_robin", "each", "random" -> "สุ่มตำแหน่งโลก หรือกำหนดการเกิดมอนสเตอร์ตามคำสั่ง: round_robin วนจุด, each ต่อจุด, random สุ่มจุด";
            case "check", "experimental" -> "ตรวจหรือนำเข้าโฟลเดอร์โลก; experimental อนุญาตรูปแบบทดลอง";
            case "load", "unload" -> "โหลดหรือถอดโลกออกจากหน่วยความจำ";
            case "tp" -> "ย้ายผู้เล่นไปโลก; ไม่ใส่พิกัดใช้จุดล่าสุด/จุดเกิด";
            case "now" -> "แสดงวิธียืนยันรีโลกทันทีด้วย confirm";
            default -> "ใส่ค่าตามรูปแบบที่แสดง; อ่านรายละเอียดระบบด้านบนและใช้ Tab เลือกค่าจริง";
        };
    }
}
