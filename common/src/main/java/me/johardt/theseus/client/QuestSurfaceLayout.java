package me.johardt.theseus.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import me.johardt.theseus.core.QuestDefinition;

/**
 * Immutable layout and picking for the quest graph surface.
 *
 * <p>The authored position is always the center of a node. Rendering and input
 * share this value, so picking never depends on a render pass having populated
 * a cache first.</p>
 */
public final class QuestSurfaceLayout {
    public static final int DEFAULT_ICON_SIZE = 16;
    public static final int MIN_ICON_SIZE = 8;
    public static final int MAX_ICON_SIZE = 64;

    private QuestSurfaceLayout() {}

    public static Layout layout(
        Collection<QuestNode> quests,
        QuestGraphLayout.CanvasBounds canvas,
        QuestGraphLayout.ViewportState viewport
    ) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        if (quests != null) {
            for (QuestNode quest : quests) {
                if (quest == null) continue;
                nodes.put(quest.id(), node(quest));
            }
        }
        return new Layout(nodes, canvas, viewport);
    }

    static Node node(QuestNode quest) {
        Objects.requireNonNull(quest, "quest");
        int iconSize = clampIconSize(quest.iconSize());
        int containerSize = iconSize + 8;
        String background = quest.background() == null ? "" : quest.background();
        boolean diamonds = background.endsWith("/diamonds.png");
        boolean hearts = background.endsWith("/hearts.png");
        boolean pentagons = background.endsWith("/pentagons.png");
        int frameWidth = diamonds || hearts ? 32 : 24;
        int frameHeight = diamonds || hearts ? 32 : 24;
        int backgroundWidth = diamonds || hearts ? iconSize * 2 : containerSize;
        int backgroundHeight = diamonds || hearts ? iconSize * 2 : containerSize;
        int specialFrameOffset = 4 - iconSize / 2;
        int backgroundOffsetX = diamonds || hearts ? specialFrameOffset : 0;
        int backgroundOffsetY = diamonds ? specialFrameOffset
            : hearts ? 6 - iconSize / 2
            : pentagons ? scaledOffset(-2, iconSize) : 0;

        QuestGraphLayout.NodeBounds container = QuestGraphLayout.NodeBounds.centered(
            quest.centerX(), quest.centerY(), containerSize, containerSize
        );
        QuestGraphLayout.NodeBounds frame = new QuestGraphLayout.NodeBounds(
            container.x() + backgroundOffsetX,
            container.y() + backgroundOffsetY,
            backgroundWidth,
            backgroundHeight
        );
        QuestGraphLayout.NodeBounds icon = new QuestGraphLayout.NodeBounds(
            container.x() + 4,
            container.y() + 4,
            iconSize,
            iconSize
        );
        return new Node(
            quest.id(),
            container,
            frame,
            icon,
            union(container, frame),
            frameWidth,
            frameHeight
        );
    }

    public static int clampIconSize(int size) {
        return Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, size));
    }

    /** Builds the actionable explanation shown when a quest cannot be opened. */
    public static LockExplanation explainLock(
        QuestDefinition quest,
        Map<String, LockState> states,
        String currentChapter
    ) {
        List<LockBlocker> blockers = new ArrayList<>();
        for (String dependencyId : quest.dependencies()) {
            LockState dependency = states.get(dependencyId);
            if (dependency != null && dependency.complete()) continue;
            String title = dependency == null ? dependencyId : dependency.title();
            String chapter = dependency == null || dependency.chapters().isEmpty()
                ? "Unknown chapter"
                : dependency.chapters().stream().sorted().findFirst().orElse("Unknown chapter");
            boolean selectable = dependency != null && dependency.chapters().contains(currentChapter);
            blockers.add(new LockBlocker(dependencyId, chapter + " › " + title, selectable));
        }
        if (!blockers.isEmpty()) {
            return new LockExplanation(LockKind.DEPENDENCY, "Complete the prerequisite quests", blockers);
        }
        return new LockExplanation(
            LockKind.POLICY,
            "Locked by progression policy (visibility: " + friendly(quest.settings().hiddenUntil()) + ")",
            List.of()
        );
    }

    private static String friendly(QuestDefinition.Visibility visibility) {
        return visibility.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static int scaledOffset(int defaultOffset, int iconSize) {
        return (int) Math.round(defaultOffset * iconSize / (double) DEFAULT_ICON_SIZE);
    }

    private static QuestGraphLayout.NodeBounds union(
        QuestGraphLayout.NodeBounds first,
        QuestGraphLayout.NodeBounds second
    ) {
        double minX = Math.min(first.x(), second.x());
        double minY = Math.min(first.y(), second.y());
        double maxX = Math.max(first.maxX(), second.maxX());
        double maxY = Math.max(first.maxY(), second.maxY());
        return new QuestGraphLayout.NodeBounds(minX, minY, maxX - minX, maxY - minY);
    }

    public record QuestNode(
        String id,
        double centerX,
        double centerY,
        int iconSize,
        String background
    ) {
        public QuestNode {
            Objects.requireNonNull(id, "id");
        }
    }

    public record Hit(String questId) {
        public Hit {
            Objects.requireNonNull(questId, "questId");
        }
    }

    public enum LockKind { DEPENDENCY, POLICY }

    public record LockExplanation(LockKind kind, String summary, List<LockBlocker> blockers) {
        public LockExplanation { blockers = List.copyOf(blockers); }
    }

    public record LockBlocker(String questId, String label, boolean selectable) {}

    public record LockState(String title, boolean complete, Set<String> chapters) {
        public LockState { chapters = Set.copyOf(chapters); }
    }

    /** The result of {@link QuestSurfaceLayout#layout}; this is the picking interface. */
    public static final class Layout {
        private final Map<String, Node> nodes;
        private final QuestGraphLayout.CanvasBounds canvas;
        private final QuestGraphLayout.ViewportState viewport;

        private Layout(
            Map<String, Node> nodes,
            QuestGraphLayout.CanvasBounds canvas,
            QuestGraphLayout.ViewportState viewport
        ) {
            this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            this.canvas = canvas == null
                ? new QuestGraphLayout.CanvasBounds(0, 0, 0, 0)
                : canvas;
            this.viewport = viewport == null
                ? QuestGraphLayout.ViewportState.DEFAULT
                : viewport;
        }

        /** Picks the visually topmost node at a screen-space point. */
        public Optional<Hit> pick(double screenX, double screenY) {
            if (!canvas.contains(screenX, screenY)) return Optional.empty();
            QuestGraphLayout.Point world = QuestGraphLayout.screenToWorld(
                canvas, viewport, screenX, screenY
            );
            Hit hit = null;
            for (Node node : nodes.values()) {
                if (node.contains(world.x(), world.y())) hit = new Hit(node.id());
            }
            return Optional.ofNullable(hit);
        }

        public Optional<Node> find(String questId) {
            return Optional.ofNullable(nodes.get(questId));
        }

        public List<Node> nodes() {
            return List.copyOf(nodes.values());
        }

        public QuestGraphLayout.WorldBounds worldBounds(double padding) {
            return QuestGraphLayout.boundsOf(
                nodes.values().stream().map(Node::bounds).toList(),
                padding
            );
        }
    }

    /** Rendering geometry for one node, derived and owned by the surface layout. */
    public static final class Node {
        private final String id;
        private final QuestGraphLayout.NodeBounds container;
        private final QuestGraphLayout.NodeBounds background;
        private final QuestGraphLayout.NodeBounds icon;
        private final QuestGraphLayout.NodeBounds bounds;
        private final int textureFrameWidth;
        private final int textureFrameHeight;

        private Node(
            String id,
            QuestGraphLayout.NodeBounds container,
            QuestGraphLayout.NodeBounds background,
            QuestGraphLayout.NodeBounds icon,
            QuestGraphLayout.NodeBounds bounds,
            int textureFrameWidth,
            int textureFrameHeight
        ) {
            this.id = id;
            this.container = container;
            this.background = background;
            this.icon = icon;
            this.bounds = bounds;
            this.textureFrameWidth = textureFrameWidth;
            this.textureFrameHeight = textureFrameHeight;
        }

        public String id() { return id; }
        public QuestGraphLayout.NodeBounds bounds() { return bounds; }
        public QuestGraphLayout.NodeBounds backgroundBounds() { return background; }
        public QuestGraphLayout.NodeBounds iconBounds() { return icon; }
        public int textureFrameWidth() { return textureFrameWidth; }
        public int textureFrameHeight() { return textureFrameHeight; }
        public double centerX() { return container.x() + container.width() / 2.0; }
        public double centerY() { return container.y() + container.height() / 2.0; }
        public boolean contains(double worldX, double worldY) { return bounds.contains(worldX, worldY); }

        public int minimapMarkSize() {
            int iconSize = (int) Math.round(icon.width());
            return iconSize > 48 ? 9 : iconSize > 16 ? 7 : 5;
        }
    }
}
