package online.mythoscraft.core.rank;

/** Pure rank assignment rules (no Bukkit access), so they can be unit tested. */
public final class RankRules {

    private RankRules() {
    }

    /**
     * @param actorIsOwner     the command sender is MinePro641
     * @param actorIsAdmin     the command sender has mythos.rank.admin
     * @param targetIsOwner    the player whose rank changes is MinePro641
     * @param newRankIsMythos  the rank to give is MYTHOS
     * @param currentIsMythos  the player currently holds MYTHOS
     */
    public static RankManager.Check check(boolean actorIsOwner, boolean actorIsAdmin, boolean targetIsOwner,
                                          boolean newRankIsMythos, boolean currentIsMythos) {
        if (targetIsOwner) {
            // only MinePro641 himself may change his own rank (to any rank)
            return actorIsOwner ? RankManager.Check.OK : RankManager.Check.OWNER_LOCKED;
        }
        if (newRankIsMythos || currentIsMythos) {
            return actorIsOwner ? RankManager.Check.OK : RankManager.Check.MYTHOS_OWNER_ONLY;
        }
        if (!actorIsOwner && !actorIsAdmin) {
            return RankManager.Check.NO_PERMISSION;
        }
        return RankManager.Check.OK;
    }
}
