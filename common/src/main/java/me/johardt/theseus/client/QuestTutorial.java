package me.johardt.theseus.client;

/** Pure tutorial display policy. */
public final class QuestTutorial {
    private QuestTutorial() {}

    public static boolean shouldAutoShow(
        boolean canEdit,
        boolean tutorialAutoShow,
        boolean tutorialSeen
    ) {
        return canEdit && tutorialAutoShow && !tutorialSeen;
    }
}
