package dev.nexus.client.ui.page;

import dev.nexus.client.util.Lang;
import dev.nexus.client.Nexus;
import dev.nexus.client.config.ProfileInfo;
import dev.nexus.client.config.ProfileManager;
import dev.nexus.client.render.Icon;
import dev.nexus.client.render.IconRender;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.render.TextRender;
import dev.nexus.client.ui.NexusScreen;
import dev.nexus.client.ui.UiContext;
import dev.nexus.client.ui.component.Button;
import dev.nexus.client.ui.component.Component;
import dev.nexus.client.ui.component.IconButton;
import dev.nexus.client.ui.component.ScrollPanel;
import dev.nexus.client.ui.component.TextField;
import dev.nexus.client.ui.theme.Theme;
import dev.nexus.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.util.List;

/** Create, load, rename, duplicate and delete profiles. */
public final class ProfilesPage extends Page {
    private final TextField name;
    private final Button create, createEmpty;
    private final IconButton reload, folder;
    private final ScrollPanel list = new ScrollPanel().gap(4);
    private String error = "";
    private int signature = Integer.MIN_VALUE;

    public ProfilesPage(NexusScreen screen) {
        super(screen);
        name = new TextField().placeholder("New profile name").icon(Icon.PLUS).maxLength(24)
                .onChange(t -> error = "")
                .onSubmit(t -> create(true));
        create = new Button("Create", Icon.PLUS, Button.Style.PRIMARY, () -> create(true))
                .tooltip("New profile with a copy of the current settings");
        createEmpty = new Button("Empty", null, Button.Style.SECONDARY, () -> create(false))
                .tooltip("New profile with default settings");
        reload = new IconButton(Icon.REFRESH, "Reload the active profile from disk", () -> profiles().reload());
        folder = new IconButton(Icon.FOLDER, "Open the config folder", () -> Util.getOperatingSystem().open(Nexus.config().root()));
    }

    private static ProfileManager profiles() {
        return Nexus.config().profiles();
    }

    private void create(boolean copy) {
        if (profiles().isBusy()) return;
        String n = name.getText();
        String err = profiles().validateNewName(n, null);
        if (err != null) {
            error = err;
            return;
        }
        profiles().create(n, copy, e -> error = e);
        name.setText("");
        UiContext.unfocus(name);
    }

    @Override public String title() { return "Profiles"; }
    @Override public Icon icon() { return Icon.PROFILES; }

    @Override
    public String subtitle() {
        return profiles().isBusy() ? "Working…" : "Each profile stores modules, settings, keybinds and HUD layout";
    }

    @Override
    public void onShow() {
        profiles().rescan();
    }

    private int computeSignature() {
        List<ProfileInfo> l = profiles().list();
        int h = profiles().getActive().hashCode() * 31 + l.size();
        for (ProfileInfo p : l) h = h * 31 + p.name().hashCode() + Long.hashCode(p.modified());
        return h;
    }

    private void rebuildIfNeeded() {
        int sig = computeSignature();
        if (sig == signature) return;
        signature = sig;
        list.clear();
        for (ProfileInfo p : profiles().list()) list.add(new Row(p));
    }

    @Override
    public void render(DrawContext ctx, double mx, double my) {
        rebuildIfNeeded();
        boolean busy = profiles().isBusy();
        create.setEnabled(!busy);
        createEmpty.setEnabled(!busy);
        reload.setEnabled(!busy);

        float cw = create.naturalWidth(), ew = createEmpty.naturalWidth();
        float fieldW = width - cw - ew - 12 - 48;
        name.setBounds(x, y, fieldW, 20);
        name.render(ctx, mx, my);
        create.setBounds(x + fieldW + 6, y, cw, 20);
        create.render(ctx, mx, my);
        createEmpty.setBounds(x + fieldW + 12 + cw, y, ew, 20);
        createEmpty.render(ctx, mx, my);
        reload.setBounds(x + width - 42, y + 1, 18, 18);
        reload.render(ctx, mx, my);
        folder.setBounds(x + width - 20, y + 1, 18, 18);
        folder.render(ctx, mx, my);

        float ly = y + 26;
        if (!error.isEmpty()) {
            TextRender.draw(ctx, error, x + 2, ly, 0xFFFCA5A5);
        } else if (!name.getText().isEmpty()) {
            String live = profiles().validateNewName(name.getText(), null);
            if (live != null) TextRender.draw(ctx, live, x + 2, ly, Theme.TEXT_DIM);
        }
        ly += 14;
        list.setBounds(x, ly, width, y + height - ly);
        list.render(ctx, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (name.mouseClicked(mx, my, button)) return true;
        if (create.mouseClicked(mx, my, button) || createEmpty.mouseClicked(mx, my, button)) return true;
        if (reload.mouseClicked(mx, my, button) || folder.mouseClicked(mx, my, button)) return true;
        return list.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean h = create.mouseReleased(mx, my, button);
        h |= createEmpty.mouseReleased(mx, my, button);
        h |= reload.mouseReleased(mx, my, button);
        h |= folder.mouseReleased(mx, my, button);
        return list.mouseReleased(mx, my, button) || h;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return list.mouseScrolled(mx, my, amount);
    }

    /** One stored profile. */
    private final class Row extends Component {
        private final ProfileInfo info;
        private final Button load;
        private final IconButton rename, duplicate, delete;
        private final TextField renameField;
        private boolean renaming;
        private boolean confirmDelete;
        private long confirmUntil;
        private long agoSecond = -1;
        private String ago = "";
        private String renameError = "";

        Row(ProfileInfo info) {
            this.info = info;
            this.height = 32;
            load = new Button("Load", null, Button.Style.SECONDARY, () -> profiles().switchTo(info.name()));
            rename = new IconButton(Icon.EDIT, "Rename", this::startRename);
            duplicate = new IconButton(Icon.COPY, "Duplicate", () -> profiles().duplicate(info.name()));
            delete = new IconButton(Icon.TRASH, "Delete", this::deleteClicked).activeColor(Theme.DANGER);
            renameField = new TextField().maxLength(24).onSubmit(t -> confirmRename()).onChange(t -> renameError = "");
        }

        private boolean isActive() {
            return info.name().equals(profiles().getActive());
        }

        private void startRename() {
            renaming = true;
            renameError = "";
            renameField.setText(info.name());
            renameField.focus();
        }

        private void confirmRename() {
            String n = renameField.getText().trim();
            if (n.equals(info.name())) {
                renaming = false;
                UiContext.unfocus(renameField);
                return;
            }
            String err = profiles().validateNewName(n, info.name());
            if (err != null) {
                renameError = err;
                return;
            }
            renaming = false;
            UiContext.unfocus(renameField);
            profiles().rename(info.name(), n, e -> renameError = e);
        }

        private void deleteClicked() {
            long now = System.currentTimeMillis();
            if (confirmDelete && now < confirmUntil) {
                confirmDelete = false;
                profiles().delete(info.name());
            } else {
                confirmDelete = true;
                confirmUntil = now + 3000;
                delete.tooltip("Click again to delete");
            }
        }

        @Override
        public float preferredHeight(float width) {
            return 32;
        }

        private String ago() {
            long now = System.currentTimeMillis();
            if (now / 1000 != agoSecond) {
                agoSecond = now / 1000;
                long s = Math.max(0, (now - info.modified()) / 1000);
                if (info.modified() <= 0) ago = "Never saved";
                else if (s < 60) ago = "Saved just now";
                else if (s < 3600) ago = Lang.fmt("Saved {0} min ago", s / 60);
                else if (s < 86400) ago = Lang.fmt("Saved {0} h ago", s / 3600);
                else ago = Lang.fmt("Saved {0} d ago", s / 86400);
            }
            return ago;
        }

        @Override
        public void render(DrawContext ctx, double mx, double my) {
            if (confirmDelete && System.currentTimeMillis() > confirmUntil) {
                confirmDelete = false;
                delete.tooltip("Delete");
            }
            if (renaming && !renameField.isFocused()) renaming = false; // Esc or click elsewhere cancels
            boolean active = isActive();
            boolean busy = profiles().isBusy();
            float h = updateHover(mx, my);
            Render2D.roundedRect(ctx, x, y, width, height, Theme.RADIUS_SMALL,
                    active ? ColorUtil.lerp(Theme.CARD, Theme.accent(), 0.08f + 0.04f * h) : ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, h));
            Render2D.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_SMALL, 1f,
                    active ? ColorUtil.withAlpha(Theme.accent(), 0x80) : Theme.BORDER);
            IconRender.draw(ctx, Icon.PROFILES, x + 10, y + 11, 10, active ? Theme.accentBright() : Theme.TEXT_DIM);

            float right = x + width - 8;
            delete.setBounds(right - 18, y + 7, 18, 18);
            duplicate.setBounds(right - 40, y + 7, 18, 18);
            rename.setBounds(right - 62, y + 7, 18, 18);
            delete.setEnabled(!busy && profiles().list().size() > 1);
            duplicate.setEnabled(!busy);
            rename.setEnabled(!busy);
            float buttonsLeft = right - 66;
            if (!active) {
                float lw = load.naturalWidth();
                load.setBounds(buttonsLeft - lw, y + 7, lw, 18);
                load.setEnabled(!busy);
                load.render(ctx, mx, my);
                buttonsLeft -= lw + 6;
            }
            if (confirmDelete) {
                Render2D.roundedRect(ctx, delete.getX() - 1, delete.getY() - 1, 20, 20, Theme.RADIUS_SMALL, 0x40F87171);
            }
            rename.render(ctx, mx, my);
            duplicate.render(ctx, mx, my);
            delete.render(ctx, mx, my);

            float tx = x + 26;
            float textW = buttonsLeft - tx - 4;
            if (renaming) {
                renameField.setBounds(tx - 2, y + 7, Math.min(160, textW), 18);
                renameField.render(ctx, mx, my);
                if (!renameError.isEmpty()) {
                    TextRender.drawScaled(ctx, renameError, tx + Math.min(160, textW) + 6, y + 13, 0.7f, 0xFFFCA5A5, false);
                }
            } else {
                String n = TextRender.ellipsize(info.name(), (int) (textW - (active ? 44 : 0)));
                TextRender.draw(ctx, n, tx, y + 6, Theme.TEXT);
                if (active) {
                    float bx = tx + TextRender.width(n) + 6;
                    Render2D.roundedRect(ctx, bx, y + 5, 38, 11, 5.5f, Theme.accentSoft());
                    TextRender.drawScaled(ctx, "ACTIVE", bx + 7, y + 8, 0.7f, Theme.accentBright(), false);
                }
                TextRender.drawScaled(ctx, ago(), tx, y + 18, 0.7f, Theme.TEXT_DIM, false);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (renaming && renameField.mouseClicked(mx, my, button)) return true;
            if (!isActive() && load.mouseClicked(mx, my, button)) return true;
            if (rename.mouseClicked(mx, my, button) || duplicate.mouseClicked(mx, my, button) || delete.mouseClicked(mx, my, button)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean h = load.mouseReleased(mx, my, button);
            h |= rename.mouseReleased(mx, my, button);
            h |= duplicate.mouseReleased(mx, my, button);
            h |= delete.mouseReleased(mx, my, button);
            return h;
        }
    }
}
