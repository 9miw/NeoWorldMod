package com.ambition.neoworld.client.gui.widget;

public final class NeoButtonScaleRegressionTest {
    public static void run() {
        check(NeoButton.resolveTextScale(5.98F,203,9,true),5.98F);
        check(NeoButton.resolveTextScale(0.35F,12,9,true),0.35F);
        check(NeoButton.resolveTextScale(5.98F,203,9,false),3F);
        check(NeoButton.resolveTextScale(0.35F,12,9,false),0.5F);
        System.out.println("Button scale regression passed");
    }
    private static void check(float actual,float expected) {
        if(Math.abs(actual-expected)>0.0001F) throw new AssertionError("Expected "+expected+", got "+actual);
    }
}
