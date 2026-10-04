package com.ambition.neoworld.party;

public record PartyActionResult(boolean successful, Party party, String message) {
    public static PartyActionResult success(Party party, String message) {
        return new PartyActionResult(true, party, message);
    }

    public static PartyActionResult failure(String message) {
        return new PartyActionResult(false, null, message);
    }
}
