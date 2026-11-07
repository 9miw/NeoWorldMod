package com.ambition.neoworld.pvp;

import java.util.UUID;
import com.ambition.neoworld.pvp.room.PvpInvitationBook;
import com.ambition.neoworld.pvp.room.PvpRoom;
import com.ambition.neoworld.pvp.ranked.MatchConfirmation;

public final class PvpInvitationRegressionTest {
    public static void run() {
        UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),other=UUID.randomUUID();
        var book=new PvpInvitationBook();
        var invitation=book.create(1,owner,guest,100);
        reject(() -> book.take(invitation.token(),other,101));
        reject(() -> book.create(1,owner,other,101));
        reject(() -> book.create(2,other,guest,101));
        check(book.take(invitation.token(),guest,699).room()==1,"Only intended recipient consumes a still-valid invite");
        reject(() -> book.take(invitation.token(),guest,699));
        var expired=book.create(1,owner,guest,1000);
        reject(() -> book.take(expired.token(),guest,1600));
        var closed=book.create(1,owner,guest,1700); book.revokeRoom(1);
        reject(() -> book.take(closed.token(),guest,1701));
        var disconnected=book.create(1,owner,guest,1800); book.revokePlayer(owner);
        reject(() -> book.take(disconnected.token(),guest,1801));
        var guestLeft=book.create(1,owner,guest,1900); book.revokePlayer(guest);
        reject(() -> book.take(guestLeft.token(),guest,1901));
        reject(() -> book.create(1,owner,owner,2000));

        var room=new PvpRoom(1,"arena_1",owner); room.join(guest);
        var confirm=new MatchConfirmation(owner,guest,0);
        check(room.canStart() && room.state()==PvpRoom.State.WAITING,"Lobby has no readiness phase and stays waiting during confirmation");
        confirm.accept(owner,1);
        check(confirm.status(2)==MatchConfirmation.Status.WAITING,"One confirmation does not start the match");
        check(confirm.status(600)==MatchConfirmation.Status.EXPIRED && room.canStart(),"Expired confirmation leaves the private lobby reusable");
        var retry=new MatchConfirmation(owner,guest,601);
        retry.accept(owner,602); retry.accept(guest,603);
        check(retry.status(603)==MatchConfirmation.Status.ACCEPTED,"Both players confirm the retried match");
        room.beginLoading();
        check(room.state()==PvpRoom.State.LOADING,"Confirmed lobby waits for the complete map before countdown");
        reject(room::begin);
        room.awaitClients();
        check(room.state()==PvpRoom.State.CLIENT_LOADING,"Placed arena still waits for both clients");
        room.begin(); check(room.state()==PvpRoom.State.COUNTDOWN,"Client-ready lobby can enter countdown");
        System.out.println("PvP invitations and private confirmation regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid invitation accepted");
    }
}
