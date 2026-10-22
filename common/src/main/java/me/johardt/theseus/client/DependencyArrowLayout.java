package me.johardt.theseus.client;

/** Places a bounded set of looping markers on a visible dependency segment. */
final class DependencyArrowLayout {
    private DependencyArrowLayout() {}

    static long offset(long visibleLength, int markerCount, int marker, double travel) {
        double spacing = (double) visibleLength / markerCount;
        // The last-to-first interval is a full spacing too. A moving lattice
        // loops every spacing, so there is no extra empty slot at the seam.
        return (long) ((marker - 1) * spacing + travel % spacing);
    }
}
