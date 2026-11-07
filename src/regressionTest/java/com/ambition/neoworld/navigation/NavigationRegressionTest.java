package com.ambition.neoworld.navigation;

import java.util.List;

/** Hand-built roads catch accidental shortcuts, lost overrides and repeatable-quest regressions. */
public final class NavigationRegressionTest {
    public static void main(String[] args) {
        var automaticLocation = new NavigationSavedData.Target("minecraft:overworld", 10, 64, 10, 3, null);
        check(QuestNavigationState.destination(QuestNavigationState.Phase.OBJECTIVE,null,
                () -> automaticLocation, () -> null) == automaticLocation,
                "Unbound Location quests navigate to their automatic Waypoint objective");
        check(QuestNavigationState.destination(QuestNavigationState.Phase.OBJECTIVE,
                new NavigationSavedData.Binding(automaticLocation,null),
                () -> { throw new AssertionError("Manual objective must win"); }, () -> null) == automaticLocation,
                "Explicit objective overrides automatic Waypoint lookup");
        var graph = new NavigationGraph();
        var a = node("a", 0, 0); var b = node("b", 8, 0);
        var c = node("c", 8, 8); var d = node("d", 0, 8);
        graph.add(a); graph.add(b); graph.add(c); graph.add(d);
        graph.connect("a", "b"); graph.connect("b", "c"); graph.connect("c", "d");
        check(graph.route("a", "d").equals(List.of(a,b,c,d)), "Nearby disconnected roads must not become shortcuts");
        graph.override("b", "c", false);
        graph.connect("b", "c");
        check(graph.route("a", "d").isEmpty(), "Automatic rebuild must preserve blocked links");
        graph.override("b", "c", true);
        check(graph.route("a", "d").size()==4, "Admin can restore a blocked connection");
        graph.connect("a","d");
        check(graph.route("a","d",(x,y)->!(x.id().equals("a")&&y.id().equals("d")))
                .equals(List.of(a,b,c,d)),"Obstructed shortest road must use alternate connected road");
        graph.disconnect("a","d");
        graph.remove("b");
        check(graph.route("a", "d").isEmpty(), "Removed markers invalidate routes");
        check(graph.route("a", "a").equals(List.of(a)), "Arrival at same marker is valid");
        graph.add(new NavigationGraph.Node("other", "other_dimension",0,0,0));
        reject(() -> graph.connect("a", "other"));
        reject(() -> graph.add(node("a",3,4)));
        reject(() -> graph.connect("missing", "a"));
        reject(() -> graph.add(new NavigationGraph.Node("bad", "world",Double.NaN,0,0)));
        var near = new NavigationGraph(); near.add(a); near.add(b); near.add(c);
        near.autoConnect("a",10,(x,y)->false);
        check(near.route("a","b").isEmpty(),"Walls reject automatic connection");
        near.autoConnect("a",10,(x,y)->true);
        check(near.route("a","b").equals(List.of(a,b)),"Walkable nearby markers connect");
        check(near.route("a","c").isEmpty(),"Markers outside radius do not connect");
        check(QuestNavigationState.phase(true,false,true)==QuestNavigationState.Phase.OBJECTIVE,
                "Previously finished repeatable quest remains trackable when active again");
        check(QuestNavigationState.phase(true,true,false)==QuestNavigationState.Phase.TURN_IN,"Completed objectives lead to NPC");
        check(QuestNavigationState.phase(false,false,true)==QuestNavigationState.Phase.NONE,"Turned-in quest clears navigation");
        check(QuestNavigationState.phase(false,false,false)==QuestNavigationState.Phase.NONE,"Cancelled quest clears navigation");
        var points = NavigationCurve.sample(List.of(a,b,c),1,p -> true);
        check(points.size()>10 && points.stream().anyMatch(p->p.x()>0 && p.z()<0),"Trail rounds bends");
        var safe = NavigationCurve.sample(List.of(a,b,c),1,p -> p.z()>=0 && p.x()<=8);
        check(safe.stream().allMatch(p->p.z()>=0 && p.x()<=8),"Blocked corner falls back to authored segments");
        var indexed=new NavigationGraph();
        indexed.add(node("negative",-1,-1)); indexed.add(node("far",100,100));
        indexed.add(new NavigationGraph.Node("stacked","other_dimension",-1,0,-1));
        check(indexed.nearby("world",0,0,0,10).stream().map(NavigationGraph.Node::id).toList().equals(List.of("negative")),
                "Spatial lookup crosses negative cell boundaries without leaking dimensions");
        indexed.remove("negative");
        check(indexed.nearby("world",0,0,0,10).isEmpty(),"Removed marker must leave spatial index");
        persistence();
        footing();
        automaticTurnIn();
        aimedNpc();
        commandTree();
        NavigationRibbonRegressionTest.run();
        QuestAutoTrackingRegressionTest.run();
        NavigationClientRoutingRegressionTest.run();
        QuestCommandPacketRegressionTest.run();
        locationObjectives();
        System.out.println("Quest navigation regression checks passed");
    }
    private static void locationObjectives() {
        var directory=new NavigationLocationDirectory();
        var near=new net.minecraft.core.BlockPos(5,64,0);
        directory.observe("Town","minecraft:overworld",near,10);
        directory.observe("Town","minecraft:overworld",new net.minecraft.core.BlockPos(50,64,0),10);
        directory.observe("Town","minecraft:the_nether",new net.minecraft.core.BlockPos(0,64,0),10);
        directory.observe("Other","minecraft:overworld",new net.minecraft.core.BlockPos(0,64,0),10);
        var selected=directory.select(List.of("town"),"minecraft:overworld",0,64,0);
        check(selected.x()==5.5 && selected.dimension().equals("minecraft:overworld"),
                "Location names match ignoring case, duplicates prefer nearest in this dimension");
        check(directory.select(List.of("Missing"),"minecraft:overworld",0,64,0)==null,
                "Location lookup never substitutes an unrelated quest block");
        var restored=NavigationLocationDirectory.load(directory.save(new net.minecraft.nbt.CompoundTag(),null),null);
        check(restored.select(List.of("Town"),"minecraft:overworld",0,64,0).equals(selected),
                "Waypoint name, position and radius survive a save/reload");
        restored.observe("Renamed","minecraft:overworld",near,7);
        check(restored.select(List.of("Town"),"minecraft:overworld",0,64,0).x()==50.5,
                "Renaming a Waypoint removes its previous name at that position");
        restored.observe("","minecraft:overworld",near,1);
        check(restored.select(List.of("Renamed"),"minecraft:overworld",0,64,0)==null,
                "Removed or empty Waypoints are forgotten");
        var location=new noppes.npcs.quests.QuestLocation();
        location.location="Town"; location.location2="Forest"; location.location3="Mine";
        var data=new noppes.npcs.controllers.data.QuestData(null);
        check(CustomNpcQuestBridge.pendingLocations(location,data).equals(List.of("Town","Forest","Mine")),
                "All three configured Location objectives are supported");
        data.extraData.putBoolean("LocationFound",true);
        data.extraData.putBoolean("Location2Found",true);
        check(CustomNpcQuestBridge.pendingLocations(location,data).equals(List.of("Mine")),
                "Custom NPCs completed flags advance navigation to remaining locations");
        data.extraData.putBoolean("Location3Found",true);
        check(CustomNpcQuestBridge.pendingLocations(location,data).isEmpty(),
                "Completed locations never remain navigation objectives");
        check(QuestNavigationState.destination(QuestNavigationState.Phase.TURN_IN,
                new NavigationSavedData.Binding(null,selected),
                () -> { throw new AssertionError("Turn-in must not resolve objectives"); },
                () -> { throw new AssertionError("Manual turn-in must win"); }).equals(selected),
                "Location integration preserves explicit turn-in priority");
    }
    private static NavigationGraph.Node node(String id,double x,double z) { return new NavigationGraph.Node(id,"world",x,0,z); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid navigation data accepted");
    }
    private static void persistence() {
        var data=new NavigationSavedData();
        var a=new NavigationGraph.Node("a","minecraft:overworld",.5,64,.5);
        var b=new NavigationGraph.Node("b","minecraft:overworld",8.5,64,.5);
        data.graph().add(a); data.graph().add(b); data.graph().override("a","b",false);
        var target=new NavigationSavedData.Target("minecraft:overworld",9,64,1,3,java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        data.bind(42,false,target); data.bind(42,true,target); data.radius(12);
        var saved=data.save(new net.minecraft.nbt.CompoundTag(),null);
        var restored=NavigationSavedData.load(saved,null);
        restored.graph().autoConnect("a",12,(x,y)->true);
        check(restored.graph().route("a","b").isEmpty(),"Blocked link survives save/load and automatic rebuild");
        check(restored.binding(42).objective().equals(target) && restored.binding(42).turnIn().equals(target),"NPC destination and quest binding survive world reload");
        check(restored.linkRadius()==12,"Configured link radius survives reload");
        restored.graph().override("a","b",true);
        var linked=NavigationSavedData.load(restored.save(new net.minecraft.nbt.CompoundTag(),null),null);
        check(linked.graph().route("a","b").size()==2,"Manual link survives world reload");
    }
    private static void footing() {
        var ground=new net.minecraft.world.phys.AABB(-10,-1,-10,10,0,10);
        var stair=new net.minecraft.world.phys.AABB(1,0,-1,2,.5,1);
        check(NavigationFooting.height(.8,0,0,List.of(ground,stair)).orElseThrow()==.5,
                "Approaching a half-step raises the player's footprint before its center crosses the step");
        var ceiling=new net.minecraft.world.phys.AABB(-10,1.5,-10,10,2,10);
        check(NavigationFooting.height(0,0,0,List.of(ground,ceiling)).isEmpty(),"Low ceilings reject the walking corridor");
        check(NavigationFooting.height(0,0,0,List.of()).isEmpty(),"A gap has no walkable support");
        check(NavigationFooting.height(0,0,0,List.of(ground)).orElseThrow()==0,"Normal ground keeps the expected foot height");
    }
    private static void automaticTurnIn() {
        check(QuestNavigationState.phase(true,true,false,true)==QuestNavigationState.Phase.NONE,
                "Instant completion never requests a turn-in destination");
        check(QuestNavigationState.phase(true,false,true,true)==QuestNavigationState.Phase.OBJECTIVE,
                "Repeatable Instant quest still navigates while objectives are incomplete");
        var directory=new NavigationNpcDirectory();
        var near=new NavigationSavedData.Target("minecraft:overworld",2,64,0,3,java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var far=new NavigationSavedData.Target("minecraft:overworld",20,64,0,3,java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var unrelated=new NavigationSavedData.Target("minecraft:overworld",1,64,0,3,java.util.UUID.fromString("00000000-0000-0000-0000-000000000003"));
        directory.observe("Blacksmith",far); directory.observe("Blacksmith",near); directory.observe("Guard",unrelated);
        var binding=new NavigationSavedData.Binding(near,far);
        check(QuestNavigationState.destination(QuestNavigationState.Phase.TURN_IN,binding,()->{
            throw new AssertionError("Explicit turnin must bypass automatic NPC lookup");
        }).equals(far),"Explicit turnin takes priority over nearer automatic NPC");
        check(QuestNavigationState.destination(QuestNavigationState.Phase.TURN_IN,new NavigationSavedData.Binding(near,null),()->unrelated).equals(unrelated),
                "Missing explicit turnin falls back to automatic NPC");
        check(QuestNavigationState.destination(QuestNavigationState.Phase.OBJECTIVE,binding,()->{
            throw new AssertionError("Incomplete objectives must not resolve turnin");
        }).equals(near),"Explicit turnin does not override objective navigation");
        check(QuestNavigationState.destination(QuestNavigationState.Phase.NONE,binding,()->{
            throw new AssertionError("Finished or Instant completion must not resolve turnin");
        })==null,"Instant completion does not navigate to an explicit turnin");
        check(directory.select("Blacksmith","minecraft:overworld",0,64,0).target().equals(near),"Duplicate names select nearest matching NPC, not a closer unrelated NPC");
        var fallback=directory.select("Missing","minecraft:overworld",0,64,0);
        check(fallback.fallback() && fallback.target().equals(unrelated),"Missing name falls back to the nearest known NPC");
        check(directory.select("Missing","minecraft:the_nether",0,64,0)==null,"Do not compare fallback distances between dimensions");
        var remote=new NavigationSavedData.Target("minecraft:the_nether",0,64,0,3,java.util.UUID.fromString("00000000-0000-0000-0000-000000000004"));
        directory.observe("Blacksmith",remote);
        check(directory.select("Blacksmith","minecraft:overworld",0,64,0).target().equals(near),"Duplicate names prefer the player's dimension over smaller coordinates in another dimension");
        check(directory.select("Blacksmith","minecraft:the_end",0,64,0)!=null,"A matching NPC in another dimension remains a valid destination");
        var restored=NavigationNpcDirectory.load(directory.save(new net.minecraft.nbt.CompoundTag(),null),null);
        check(restored.select("Blacksmith","minecraft:overworld",0,64,0).target().equals(near),"Known NPC positions survive unload and world reload");
        restored.forget(near.npc());
        check(restored.select("Blacksmith","minecraft:overworld",0,64,0).target().equals(far),"Deleted NPC is no longer selected");
        restored.observe("Renamed",far);
        restored.forget(remote.npc());
        check(restored.select("Blacksmith","minecraft:overworld",0,64,0).fallback(),"Renaming removes stale name lookup");
        var moved=new NavigationSavedData.Target("minecraft:overworld",.5,64,0,3,far.npc());
        restored.observe("Renamed",moved);
        check(restored.select("Renamed","minecraft:overworld",0,64,0).target().equals(moved),"Observed movement replaces old NPC coordinates");
    }
    private static void aimedNpc() {
        var eye=new net.minecraft.world.phys.Vec3(0,1.6,0);
        var forward=new net.minecraft.world.phys.Vec3(0,0,1);
        var near=new NavigationAim.Candidate<>("near",new net.minecraft.world.phys.AABB(-.3,0,3,.3,2,3.6),true);
        var far=new NavigationAim.Candidate<>("far",new net.minecraft.world.phys.AABB(-.3,0,7,.3,2,7.6),true);
        check(NavigationAim.pick(eye,forward,16,List.of(far,near)).equals(near),"Looking at overlapping NPCs selects the first hit");
        check(NavigationAim.pick(eye,forward,2,List.of(near))==null,"Walls before the NPC prevent selecting through blocks");
        check(NavigationAim.pick(eye,new net.minecraft.world.phys.Vec3(1,0,0),16,List.of(near))==null,"A nearby NPC outside the crosshair must not be selected");
        var player=new NavigationAim.Candidate<>("player",new net.minecraft.world.phys.AABB(-.3,0,1,.3,2,1.6),false);
        check(!NavigationAim.pick(eye,forward,16,List.of(near,player)).npc(),"An intervening non-NPC blocks selection of the NPC behind it");
    }
    private static void commandTree() {
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        var central=dispatcher.register(net.minecraft.commands.Commands.literal("neow")
                .then(com.ambition.neoworld.command.QuestNavigationCommand.buildCommand()));
        var root=(com.mojang.brigadier.tree.LiteralCommandNode<net.minecraft.commands.CommandSourceStack>)central.getChild("questnav");
        check(root.getChild("track")!=null && root.getChild("stop")!=null,"Player navigation commands remain available");
        var admin=root.getChild("admin");
        check(admin!=null && admin.getChild("bind")!=null && admin.getChild("bindnpc")!=null,"Navigation setup belongs under admin like existing systems");
        check(root.getChild("bind")==null,"Canonical player root must not contain flat admin commands");
        check(admin.getChild("bindnpc").getChild("quest").getChild("objective").getCommand()!=null,"Looking at an NPC remains an executable command");
        check(admin.getChild("bind").getChild("quest").getChild("objective").getChild("radiusHere").getCommand()!=null,"Standing-position binding remains executable");
        check(dispatcher.getRoot().getChild("questnav")==null,"Standalone questnav alias is removed");
        var op=new net.minecraft.commands.CommandSourceStack(net.minecraft.commands.CommandSource.NULL,
                net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec2.ZERO,null,2,"test",net.minecraft.network.chat.Component.literal("test"),null,null);
        for(String command:List.of("neow questnav admin bindnpc 42 objective", "neow questnav admin bind 42 objective 5",
                "neow questnav admin view", "neow questnav admin bind 42 objective 100 64 200 5", "neow questnav track 42")) {
            var parsed=dispatcher.parse(command,op);
            check(!parsed.getReader().canRead() && parsed.getContext().getLastChild().getCommand()!=null,"Command form must resolve to executable handler: "+command);
        }
        var player=new net.minecraft.commands.CommandSourceStack(net.minecraft.commands.CommandSource.NULL,
                net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec2.ZERO,null,0,"player",net.minecraft.network.chat.Component.literal("player"),null,null);
        check(dispatcher.parse("neow questnav admin bindnpc 42 objective",player).getReader().canRead(),"Non-admin cannot enter canonical setup branch");
        check(dispatcher.parse("questnav bindnpc 42 objective",player).getReader().canRead(),"Legacy alias is unavailable");
        var buttonCommand=dispatcher.parse(com.ambition.neoworld.command.QuestNavigationCommand.trackingCommand(42),player);
        check(!buttonCommand.getReader().canRead() && buttonCommand.getContext().getLastChild().getCommand()!=null
                && buttonCommand.getContext().getLastChild().getArguments().get("quest").getResult().equals(42),
                "Quest-log button request reaches the player track handler with the selected quest ID");
        reject(()->com.ambition.neoworld.command.QuestNavigationCommand.trackingCommand(-1));
    }
}
