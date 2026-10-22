package me.johardt.theseus.core;

import java.util.Set;
import java.util.UUID;

/** Server-side membership lookup. Snapshots include offline members, never invitees or allies. */
public interface PartyLookup {
    PartyLookup NONE = new PartyLookup() {
        public boolean available() { return false; }
        public Party find(UUID member) { return null; }
    };

    boolean available();

    /** Null means solo. A lookup failure must throw, rather than masquerade as solo. */
    Party find(UUID member);

    record Party(UUID id, String name, Set<UUID> members) {
        public Party {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(name);
            members = Set.copyOf(members);
        }
    }
}
