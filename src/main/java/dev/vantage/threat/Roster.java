package dev.vantage.threat;

import dev.vantage.game.TeamColour;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who is in the current game, remembered across rebuilds.
 *
 * <p>The threat list used to be rebuilt from scratch several times a second straight off the tab
 * list, which meant any single bad read dropped rows: a frame mid-update, a player briefly missing
 * while the server reshuffled the list, a moment where the scoreboard had not caught up. Rows
 * appeared and vanished, which is exactly what you do not want from a panel you are glancing at
 * mid-fight.
 *
 * <p>So this holds the roster instead. A player seen in any recent rebuild stays listed, and their
 * last known gear is kept after they walk out of render distance rather than being forgotten. They
 * are only dropped once they have been genuinely absent several rebuilds running.
 *
 * <p>No Minecraft references, so the stickiness is pinned by tests rather than by playing a game.
 */
public final class Roster {

    /** Consecutive rebuilds a player can be missing from the tab list before they are dropped. */
    private static final int ABSENT_REBUILDS_BEFORE_DROP = 4;

    /** One sighting of a player during a rebuild. */
    public static final class Sighting {
        public final UUID uuid;
        public final String name;
        public final boolean self;
        public final TeamColour team;
        /** Their gear if they were close enough to read it, or null if they were not. */
        public final Gear gear;

        public Sighting(UUID uuid, String name, boolean self, TeamColour team, Gear gear) {
            this.uuid = uuid;
            this.name = name;
            this.self = self;
            this.team = team == null ? TeamColour.UNKNOWN : team;
            this.gear = gear;
        }
    }

    /** What is known about one player, carried between rebuilds. */
    public static final class Member {
        private final UUID uuid;
        private final String name;
        private final boolean self;
        private TeamColour team = TeamColour.UNKNOWN;
        private Gear gear;
        private long gearSeenAt;
        private int absentRebuilds;

        private Member(UUID uuid, String name, boolean self) {
            this.uuid = uuid;
            this.name = name;
            this.self = self;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public boolean isSelf() {
            return self;
        }

        public TeamColour getTeam() {
            return team;
        }

        /** Their last known gear, or null if they have never been close enough to read. */
        public Gear getGear() {
            return gear;
        }

        /** How long ago that gear was read. Meaningless when {@link #getGear()} is null. */
        public long gearAgeMillis(long now) {
            return Math.max(0L, now - gearSeenAt);
        }

        /** How many consecutive rebuilds they have been missing from the tab list. */
        public int getAbsentRebuilds() {
            return absentRebuilds;
        }
    }

    private final Map<String, Member> members = new HashMap<String, Member>();

    /**
     * Folds one rebuild's worth of sightings in.
     *
     * <p>Players seen now are refreshed. Players not seen are kept, with their absence counted, and
     * dropped only once they have missed {@link #ABSENT_REBUILDS_BEFORE_DROP} rebuilds in a row.
     */
    public void update(List<Sighting> sightings, long nowMillis) {
        Set<String> present = new HashSet<String>();
        if (sightings != null) {
            for (Sighting sighting : sightings) {
                if (sighting == null || sighting.name == null) {
                    continue;
                }
                present.add(sighting.name);

                Member member = members.get(sighting.name);
                if (member == null) {
                    member = new Member(sighting.uuid, sighting.name, sighting.self);
                    members.put(sighting.name, member);
                }
                member.absentRebuilds = 0;

                // Keep the last real assignment. The scoreboard drops team colours between rounds
                // and during the countdown, and forgetting them would grey out the whole list.
                if (sighting.team != TeamColour.UNKNOWN) {
                    member.team = sighting.team;
                }
                if (sighting.gear != null) {
                    member.gear = sighting.gear;
                    member.gearSeenAt = nowMillis;
                }
            }
        }

        Iterator<Map.Entry<String, Member>> iterator = members.entrySet().iterator();
        while (iterator.hasNext()) {
            Member member = iterator.next().getValue();
            if (present.contains(member.name)) {
                continue;
            }
            if (++member.absentRebuilds > ABSENT_REBUILDS_BEFORE_DROP) {
                iterator.remove();
            }
        }
    }

    public Collection<Member> members() {
        return new ArrayList<Member>(members.values());
    }

    public Member get(String name) {
        return members.get(name);
    }

    public int size() {
        return members.size();
    }

    public void clear() {
        members.clear();
    }
}
