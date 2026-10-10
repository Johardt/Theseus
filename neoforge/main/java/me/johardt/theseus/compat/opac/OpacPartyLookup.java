package me.johardt.theseus.compat.opac;

import java.util.LinkedHashSet;
import java.util.UUID;
import me.johardt.theseus.core.PartyLookup;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;

/** Loaded only when OPAC is installed; uses its public built-in party API. */
public final class OpacPartyLookup implements PartyLookup {
    private final MinecraftServer server;

    public OpacPartyLookup(MinecraftServer server) { this.server = server; }

    @Override
    public boolean available() { return true; }

    @Override
    public Party find(UUID member) {
        var party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(member);
        if (party == null) return null;
        Party snapshot = snapshot(party);
        if (!snapshot.members().contains(member)) throw new IllegalStateException("OPAC returned an inconsistent party roster");
        return snapshot;
    }

    static Party snapshot(xaero.pac.common.server.parties.party.api.IServerPartyAPI party) {
        var members = new LinkedHashSet<UUID>();
        party.getMemberInfoStream().forEach(info -> members.add(info.getUUID()));
        members.add(party.getOwner().getUUID());
        return new Party(party.getId(), party.getDefaultName(), members);
    }
}
