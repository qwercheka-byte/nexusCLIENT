package dev.nexus.launcher.ui;

import com.google.gson.reflect.TypeToken;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static dev.nexus.launcher.ui.InstanceSettingsTab.card;
import static dev.nexus.launcher.ui.InstanceSettingsTab.hint;

/**
 * Resource-pack hub: recommended picks with screenshots, the user's own packs (added and stored locally)
 * and install instructions with the resourcepacks-folder shortcut.
 */
final class ResourcePacksPage extends VBox implements MainWindow.Page {

    /** One custom pack saved by the user. */
    static final class MyPack {
        String name = "";
        String desc = "";
        String link = "";
        String image = "";
        String file = "";
    }

    private static final Type LIST = new TypeToken<List<MyPack>>() {}.getType();
    private final MainWindow window;
    private FlowPane myGrid;

    ResourcePacksPage(MainWindow window) {
        this.window = window;
        getStyleClass().add("page");

        Button add = Fx.button(I18n.t("packs.add"), Icons.ADD, "primary");
        add.setOnAction(e -> addDialog());
        Label sub = Fx.label(I18n.t("packs.sub"), "dim");
        sub.setWrapText(true);
        Region grow = new Region();
        javafx.scene.layout.HBox.setHgrow(grow, Priority.ALWAYS);
        javafx.scene.layout.HBox head = new javafx.scene.layout.HBox(12,
                new VBox(4, Fx.label(I18n.t("packs.title"), "h1"), sub), grow, add);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("page-header");

        FlowPane buildGrid = new FlowPane(16, 16);
        buildGrid.getChildren().addAll(
                modrinthTile("fresh-animations", "Fresh Animations", "respacks.fresh", "fresh-animations"),
                modrinthTile("bare-bones", "Bare Bones", "respacks.bare", "bare-bones"),
                modrinthTile("better-leaves", "Motschen's Better Leaves", "respacks.leaves", "better-leaves"),
                modrinthTile("dramatic-skys", "Dramatic Skys", "respacks.skys", "dramatic-skys"));

        FlowPane pvpGrid = new FlowPane(16, 16);
        pvpGrid.getChildren().addAll(
                modrinthTile("low-on-fire", "Low On Fire", "respacks.lowfire", "low-on-fire"),
                modrinthTile("low-shield", "Low Shield", "respacks.lowshield", "low-shield-pack"),
                modrinthTile("night-vision", "Night Vision", "respacks.nightvision", "night-vision"),
                modrinthTile("spunky-pvp", "Spunky PVP Texture Pack", "respacks.spunky", "spunky-pvp-texture-pack"));

        myGrid = new FlowPane(16, 16);
        rebuildMine();

        Button openPacks = Fx.button(I18n.t("respacks.open"), Icons.FOLDER, "primary");
        openPacks.setOnAction(e -> openResourcepacks());
        Button morePacks = Fx.button(I18n.t("respacks.more"), Icons.OPEN, "ghost");
        morePacks.setOnAction(e -> window.openLink("https://modrinth.com/resourcepacks"));

        VBox content = new VBox(14, head,
                card(I18n.t("packs.forBuilds"),
                        hint(I18n.t("packs.forBuildsSub")), buildGrid),
                card(I18n.t("packs.forPvp"),
                        hint(I18n.t("packs.forPvpSub")), pvpGrid),
                card(I18n.t("packs.mine"),
                        hint(I18n.t("packs.mineSub")), myGrid),
                card(I18n.t("respacks.howTitle"),
                        hint(I18n.t("respacks.how1")),
                        hint(I18n.t("respacks.how2")),
                        hint(I18n.t("respacks.how3")),
                        Fx.row(8, openPacks, morePacks)));
        content.setMaxWidth(860);
        content.setStyle("-fx-padding: 0 10 10 0;");
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().add(sp);
    }

    /* ------------------------------------------------ my packs ------------------------------------------------ */

    private static Path storeFile() {
        return LauncherDirs.root().resolve("resourcepack-recs.json");
    }

    private static Path imageDir() {
        return LauncherDirs.root().resolve("pack-images");
    }

    private static Path fileDir() {
        return LauncherDirs.root().resolve("pack-files");
    }

    static List<MyPack> loadMine() {
        try {
            return Json.parse(Files.readString(storeFile()), LIST);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    private static void saveMine(List<MyPack> packs) {
        try {
            Json.write(storeFile(), packs);
        } catch (IOException e) {
            Toasts.error(e.getMessage());
        }
    }

    private void rebuildMine() {
        myGrid.getChildren().clear();
        List<MyPack> mine = loadMine();
        if (mine.isEmpty()) {
            Label empty = hint(I18n.t("packs.empty"));
            empty.setStyle("-fx-padding: 6 0 6 0;");
            myGrid.getChildren().add(empty);
            return;
        }
        for (MyPack p : mine) myGrid.getChildren().add(myTile(p));
    }

    /** Tile for a user-added pack: image (or placeholder), name, description, delete button on hover. */
    private Node myTile(MyPack p) {
        Node pic = null;
        if (p.image != null && !p.image.isBlank()) {
            ImageView iv = new ImageView(new Image(imageDir().resolve(p.image).toUri().toString(), false));
            if (!iv.getImage().isError()) {
                iv.setFitWidth(300);
                iv.setFitHeight(169);
                Rectangle clip = new Rectangle(300, 169);
                clip.setArcWidth(14);
                clip.setArcHeight(14);
                iv.setClip(clip);
                pic = iv;
            }
        }
        if (pic == null) {
            Region puzzle = Icons.icon(Icons.PUZZLE, 44);
            puzzle.setStyle("-fx-background-color: -nx-text-faint;");
            StackPane fallback = new StackPane(puzzle);
            fallback.setMinSize(300, 169);
            fallback.setPrefSize(300, 169);
            fallback.setMaxSize(300, 169);
            fallback.setStyle("-fx-background-color: -nx-surface-2; -fx-background-radius: 14;");
            pic = fallback;
        }

        Label title = Fx.label(p.name.isBlank() ? "—" : p.name, "strong");
        Label desc = Fx.label(p.desc == null ? "" : p.desc, "faint", "tiny");
        desc.setWrapText(true);
        desc.setMaxWidth(300);

        Node extra = null;
        if (p.file != null && !p.file.isBlank()) {
            Button install = Fx.button(I18n.t("packs.install"), Icons.DOWNLOAD, "ghost", "icon-only");
            install.setOnAction(e -> installPack(p));
            extra = Fx.row(2, install);
        }

        Button del = Fx.button("", Icons.CLOSE, "ghost", "icon-only");
        del.setOnAction(e -> {
            List<MyPack> list = loadMine();
            list.removeIf(x -> x.image.equals(p.image) && x.name.equals(p.name) && x.file.equals(p.file));
            saveMine(list);
            try {
                if (p.image != null && !p.image.isBlank()) Files.deleteIfExists(imageDir().resolve(p.image));
                if (p.file != null && !p.file.isBlank()) Files.deleteIfExists(fileDir().resolve(p.file));
            } catch (IOException ignored) { }
            rebuildMine();
        });
        StackPane picWrap = new StackPane(pic, del);
        StackPane.setAlignment(del, Pos.TOP_RIGHT);
        StackPane.setMargin(del, new Insets(4));

        VBox box = new VBox(8, picWrap, title, desc);
        if (extra != null) box.getChildren().add(extra);
        box.setMaxWidth(312);
        box.setPadding(new Insets(6));
        if (p.link != null && !p.link.isBlank()) {
            box.setCursor(Cursor.HAND);
            String base = "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 1.5; -fx-border-color: transparent;";
            box.setStyle(base);
            box.setOnMouseEntered(e -> box.setStyle("-fx-border-color: -nx-accent; " + base));
            box.setOnMouseExited(e -> box.setStyle(base));
            box.setOnMouseClicked(e -> window.openLink(p.link));
        }
        return box;
    }

    /** Copies the pack's stored zip into the resourcepacks folder of the last played instance. */
    private void installPack(MyPack p) {
        List<Instance> all = InstanceManager.all();
        if (all.isEmpty()) {
            Toasts.error(I18n.t("respacks.noInstance"));
            return;
        }
        Instance best = all.stream().max(Comparator.comparingLong(i -> i.lastPlayed)).orElse(all.get(0));
        try {
            Path target = best.gameDir().resolve("resourcepacks");
            Files.createDirectories(target);
            String zipName = p.name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if (zipName.isEmpty()) zipName = "pack";
            Files.copy(fileDir().resolve(p.file), target.resolve(zipName + ".zip"), StandardCopyOption.REPLACE_EXISTING);
            Toasts.success(I18n.t("packs.installed"));
        } catch (IOException e) {
            Toasts.error(e.getMessage());
        }
    }

    /* ------------------------------------------------ add dialog ------------------------------------------------ */

    private void addDialog() {
        Modal m = Modal.of(I18n.t("packs.addTitle")).width(560);
        TextField name = new TextField();
        name.setPromptText(I18n.t("packs.namePrompt"));
        TextField desc = new TextField();
        desc.setPromptText(I18n.t("packs.descPrompt"));
        TextField link = new TextField();
        link.setPromptText(I18n.t("packs.linkPrompt"));

        Label imgName = Fx.label(I18n.t("packs.imgNone"), "faint", "small");
        Button pickImg = Fx.button(I18n.t("packs.imgPick"), Icons.FOLDER, "ghost");
        Path[] chosen = new Path[1];
        pickImg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(I18n.t("packs.imgPick"));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
            java.io.File f = fc.showOpenDialog(windowStage());
            if (f != null) {
                chosen[0] = f.toPath();
                imgName.setText(f.getName());
            }
        });

        Label zipName = Fx.label(I18n.t("packs.fileNone"), "faint", "small");
        Button pickZip = Fx.button(I18n.t("packs.filePick"), Icons.FOLDER, "ghost");
        Path[] chosenZip = new Path[1];
        pickZip.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(I18n.t("packs.filePick"));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ресурс-паки", "*.zip"));
            java.io.File f = fc.showOpenDialog(windowStage());
            if (f != null) {
                chosenZip[0] = f.toPath();
                zipName.setText(f.getName());
            }
        });

        final Runnable[] save = new Runnable[1];
        Button create = m.button(I18n.t("packs.addCreate"), "primary", () -> save[0].run());
        create.setDisable(true);
        Runnable update = () -> create.setDisable(name.getText().isBlank());
        name.textProperty().addListener((o, a, b) -> update.run());

        save[0] = () -> {
            MyPack p = new MyPack();
            p.name = name.getText().trim();
            p.desc = desc.getText().trim();
            p.link = link.getText().trim();
            if (chosen[0] != null) {
                try {
                    Files.createDirectories(imageDir());
                    String ext = chosen[0].getFileName().toString();
                    ext = ext.contains(".") ? ext.substring(ext.lastIndexOf('.')) : ".png";
                    p.image = UUID.randomUUID().toString().substring(0, 8) + ext;
                    Files.copy(chosen[0], imageDir().resolve(p.image), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    Toasts.error(ex.getMessage());
                    return;
                }
            }
            if (chosenZip[0] != null) {
                try {
                    Files.createDirectories(fileDir());
                    p.file = UUID.randomUUID().toString().substring(0, 8) + ".zip";
                    Files.copy(chosenZip[0], fileDir().resolve(p.file), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    Toasts.error(ex.getMessage());
                    return;
                }
            }
            List<MyPack> list = loadMine();
            list.add(p);
            saveMine(list);
            rebuildMine();
            m.close();
        };

        m.closeButton(I18n.t("common.cancel"), "ghost", null);
        m.content(
                field(I18n.t("packs.name"), name),
                field(I18n.t("packs.desc"), desc),
                field(I18n.t("packs.link"), link),
                field(I18n.t("packs.img"), new javafx.scene.layout.HBox(8, pickImg, imgName)),
                field(I18n.t("packs.file"), new javafx.scene.layout.HBox(8, pickZip, zipName)));
        m.show();
    }

    private static Node field(String label, Node control) {
        return new VBox(5, Fx.label(label, "dim", "small"), control);
    }

    private javafx.stage.Window windowStage() {
        return getScene() == null ? null : getScene().getWindow();
    }

    /* ------------------------------------------------ shared ------------------------------------------------ */

    /** Recommended tile: bundled screenshot, name, description, click opens Modrinth. */
    private Node modrinthTile(String img, String name, String descKey, String slug) {
        ImageView iv = new ImageView(new Image(getClass()
                .getResourceAsStream("/dev/nexus/launcher/img/packs/" + img + ".jpg")));
        iv.setFitWidth(300);
        iv.setFitHeight(169);
        Rectangle clip = new Rectangle(300, 169);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        iv.setClip(clip);

        Label title = Fx.label(name, "strong");
        Label desc = Fx.label(I18n.t(descKey), "faint", "tiny");
        desc.setWrapText(true);
        desc.setMaxWidth(300);

        VBox box = new VBox(8, iv, title, desc);
        box.setMaxWidth(312);
        box.setPadding(new Insets(6));
        box.setCursor(Cursor.HAND);
        String base = "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 1.5; -fx-border-color: transparent;";
        box.setStyle(base);
        box.setOnMouseEntered(e -> box.setStyle("-fx-border-color: -nx-accent; " + base));
        box.setOnMouseExited(e -> box.setStyle(base));
        box.setOnMouseClicked(e -> window.openLink("https://modrinth.com/resourcepack/" + slug));
        return box;
    }

    /** Opens the resourcepacks folder of the most recently played instance. */
    private void openResourcepacks() {
        List<Instance> all = InstanceManager.all();
        if (all.isEmpty()) {
            Toasts.error(I18n.t("respacks.noInstance"));
            return;
        }
        Instance best = all.stream().max(Comparator.comparingLong(i -> i.lastPlayed)).orElse(all.get(0));
        Fx.openPath(best.gameDir().resolve("resourcepacks"));
    }

    @Override
    public void onHide() { }
}
