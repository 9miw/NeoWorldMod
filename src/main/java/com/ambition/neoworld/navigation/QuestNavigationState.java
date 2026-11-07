package com.ambition.neoworld.navigation;

public final class QuestNavigationState {
    public enum Phase { NONE, OBJECTIVE, TURN_IN }
    private QuestNavigationState() {}
    public static NavigationSavedData.Target destination(Phase phase,NavigationSavedData.Binding binding,
            java.util.function.Supplier<NavigationSavedData.Target> automaticObjective,
            java.util.function.Supplier<NavigationSavedData.Target> automaticTurnIn) {
        if(phase==Phase.OBJECTIVE) {
            if(binding!=null && binding.objective()!=null) return binding.objective();
            return automaticObjective.get();
        }
        return destination(phase,binding,automaticTurnIn);
    }
    public static NavigationSavedData.Target destination(Phase phase,NavigationSavedData.Binding binding,
            java.util.function.Supplier<NavigationSavedData.Target> automaticTurnIn) {
        if(phase==Phase.OBJECTIVE) return binding==null ? null : binding.objective();
        if(phase!=Phase.TURN_IN) return null;
        if(binding!=null && binding.turnIn()!=null) return binding.turnIn();
        return automaticTurnIn.get();
    }
    public static Phase phase(boolean active,boolean completed,boolean finished) {
        return phase(active,completed,finished,false);
    }
    public static Phase phase(boolean active,boolean completed,boolean finished,boolean instant) {
        // A repeatable quest can be active while its previous completion remains in history.
        if(!active || (completed && instant)) return Phase.NONE;
        return completed ? Phase.TURN_IN : Phase.OBJECTIVE;
    }
}
