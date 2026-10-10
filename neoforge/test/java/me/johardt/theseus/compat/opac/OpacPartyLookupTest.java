package me.johardt.theseus.compat.opac;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import static org.junit.jupiter.api.Assertions.*;

class OpacPartyLookupTest {
    @Test
    void publicApiRosterIncludesOfflineMembersAndDeduplicatesOwner() {
        UUID owner = new UUID(0, 1);
        UUID offline = new UUID(0, 2);
        UUID partyId = new UUID(1, 1);
        for (boolean ownerInStream : new boolean[] {false, true}) {
            IServerPartyAPI party = (IServerPartyAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {IServerPartyAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMemberInfoStream" -> ownerInStream
                        ? Stream.of(member(owner), member(offline)) : Stream.of(member(offline));
                    case "getOwner" -> member(owner);
                    case "getId" -> partyId;
                    case "getDefaultName" -> "Builders";
                    default -> throw new AssertionError("Unexpected API use: " + method.getName());
                });
            var snapshot = OpacPartyLookup.snapshot(party);
            assertEquals(partyId, snapshot.id());
            assertEquals("Builders", snapshot.name());
            assertEquals(Set.of(owner, offline), snapshot.members());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.members().clear());
        }
    }

    private IPartyMemberAPI member(UUID id) {
        return (IPartyMemberAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {IPartyMemberAPI.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getUUID")) return id;
                throw new AssertionError("Unexpected member API use: " + method.getName());
            });
    }
}
