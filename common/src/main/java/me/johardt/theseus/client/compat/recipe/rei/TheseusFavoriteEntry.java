package me.johardt.theseus.client.compat.recipe.rei;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.TheseusClient;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.favorites.FavoriteEntry;
import me.shedaniel.rei.api.client.favorites.FavoriteEntryType;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** REI favorite that returns the player to the quest screen. */
@SuppressWarnings("UnstableApiUsage")
public final class TheseusFavoriteEntry extends FavoriteEntry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "theseus");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/item/quest_book.png"
    );

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public Renderer getRenderer(boolean showcase) {
        return new Renderer() {
            @Override
            public void render(GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE,
                    bounds.getCenterX() - 8,
                    bounds.getCenterY() - 8,
                    0.0f,
                    0.0f,
                    16,
                    16,
                    16,
                    16
                );
            }

            @Override
            public Tooltip getTooltip(TooltipContext context) {
                return Tooltip.create(
                    context.getPoint(),
                    Component.translatable(ID.toLanguageKey("rei", "tooltip"))
                );
            }
        };
    }

    @Override
    public boolean doAction(MouseButtonEvent event) {
        if (event.input() != 0) return false;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
        TheseusClient.openQuestScreen();
        return true;
    }

    @Override
    public long hashIgnoreAmount() {
        return 31290831290L;
    }

    @Override
    public FavoriteEntry copy() {
        return new TheseusFavoriteEntry();
    }

    @Override
    public Identifier getType() {
        return ID;
    }

    @Override
    public boolean isSame(FavoriteEntry other) {
        return other instanceof TheseusFavoriteEntry;
    }

    public enum Type implements FavoriteEntryType<TheseusFavoriteEntry> {
        INSTANCE;

        @Override
        public DataResult<TheseusFavoriteEntry> read(CompoundTag object) {
            return DataResult.success(new TheseusFavoriteEntry(), Lifecycle.stable());
        }

        @Override
        public DataResult<TheseusFavoriteEntry> fromArgs(Object... args) {
            return DataResult.success(new TheseusFavoriteEntry(), Lifecycle.stable());
        }

        @Override
        public CompoundTag save(TheseusFavoriteEntry entry, CompoundTag tag) {
            return tag;
        }
    }
}
