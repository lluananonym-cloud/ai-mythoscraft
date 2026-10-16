package online.mythoscraft.core.rank;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankRulesTest {

    @Test
    void onlyTheOwnerMayGiveMythos() {
        // admin (not owner) tries to give MYTHOS
        assertEquals(RankManager.Check.MYTHOS_OWNER_ONLY, RankRules.check(false, true, false, true, false));
        // console/normal player
        assertEquals(RankManager.Check.MYTHOS_OWNER_ONLY, RankRules.check(false, false, false, true, false));
        // MinePro641 gives MYTHOS – allowed even without the admin permission
        assertEquals(RankManager.Check.OK, RankRules.check(true, false, false, true, false));
    }

    @Test
    void onlyTheOwnerMayTakeMythosAway() {
        assertEquals(RankManager.Check.MYTHOS_OWNER_ONLY, RankRules.check(false, true, false, false, true));
        assertEquals(RankManager.Check.OK, RankRules.check(true, false, false, false, true));
    }

    @Test
    void ownersOwnRankIsLocked() {
        assertEquals(RankManager.Check.OWNER_LOCKED, RankRules.check(false, true, true, false, true));
        assertEquals(RankManager.Check.OK, RankRules.check(true, true, true, false, true), "MinePro641 may change his own rank");
    }

    @Test
    void normalRanksNeedTheAdminPermission() {
        assertEquals(RankManager.Check.OK, RankRules.check(false, true, false, false, false));
        assertEquals(RankManager.Check.NO_PERMISSION, RankRules.check(false, false, false, false, false));
    }

    @Test
    void ownerIdentity() {
        assertEquals("MinePro641", MythosOwner.NAME);
        assertEquals(UUID.fromString("9f799033-c0f0-4d2a-aa6f-b68ce9207af4"), MythosOwner.ONLINE_UUID);
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:MinePro641".getBytes(StandardCharsets.UTF_8)), MythosOwner.OFFLINE_UUID);
    }
}
