package dev.nexus.launcher.ui;

import dev.nexus.launcher.assist.AiAssistant;
import dev.nexus.launcher.assist.AiAssistant.Provider;
import dev.nexus.launcher.assist.BuildDoctor;
import dev.nexus.launcher.assist.BuildDoctor.Finding;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

import static dev.nexus.launcher.ui.InstanceSettingsTab.card;
import static dev.nexus.launcher.ui.InstanceSettingsTab.hint;

/**
 * The "assistant" page: offline build doctor (always available), an optional cloud AI chat on top (the
 * player's own free key, Groq or OpenRouter), and FAQ cards below.
 */
final class AssistantPage extends VBox implements MainWindow.Page {
    private final MainWindow window;
    private final ComboBox<Instance> picker = new ComboBox<>();
    private final VBox results = new VBox(10);
    /** Container holding either the connect form or the chat — swapped by {@link #refreshAi()}. */
    private final VBox aiWrap = new VBox(10);
    private final VBox chatBox = new VBox(8);
    private ScrollPane chatScroll;

    AssistantPage(MainWindow window) {
        this.window = window;
        getStyleClass().add("page");

        List<Instance> list = InstanceManager.all();
        picker.getItems().setAll(list);
        Instance best = null;
        for (Instance i : list) if (best == null || i.lastPlayed > best.lastPlayed) best = i;
        if (best != null) picker.getSelectionModel().select(best);
        picker.getStyleClass().add("combo-hover");
        picker.setMaxWidth(300);
        picker.setConverter(new StringConverter<>() {
            @Override public String toString(Instance in) {
                return in == null ? "" : in.name + "  (" + in.mcVersion + ")";
            }
            @Override public Instance fromString(String s) { return null; }
        });

        Button check = Fx.button(I18n.t("doctor.check"), Icons.CHECK_CIRCLE, "primary");
        check.setOnAction(e -> runCheck(check));
        HBox controls = new HBox(10, picker, check);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label sub = Fx.label(I18n.t("doctor.sub"), "dim");
        sub.setWrapText(true);

        refreshAi();

        VBox content = new VBox(14,
                new VBox(4, Fx.label(I18n.t("doctor.title"), "h1"), sub),
                card(I18n.t("ai.section"), aiWrap),
                card(I18n.t("doctor.scan"), controls, results),
                faqCard());
        content.setMaxWidth(860);
        content.setStyle("-fx-padding: 0 10 10 0;");
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().add(sp);
    }

    /* ---------------------------------------------- AI cloud layer ---------------------------------------------- */

    /** Rebuilds the AI card: connect form when no key, chat when connected. */
    private void refreshAi() {
        aiWrap.getChildren().setAll(AiAssistant.connected() ? buildChat() : buildConnectForm());
    }

    private Node buildConnectForm() {
        Label text = Fx.label(I18n.t("ai.intro"), "dim", "small");
        text.setWrapText(true);

        ComboBox<Provider> provider = new ComboBox<>();
        provider.getItems().setAll(Provider.values());
        provider.getSelectionModel().select(Provider.ofId(dev.nexus.launcher.core.Settings.get().aiProvider));
        provider.getStyleClass().add("combo-hover");
        provider.setMaxWidth(170);
        provider.setConverter(new StringConverter<>() {
            @Override public String toString(Provider p) { return p == null ? "" : p.title; }
            @Override public Provider fromString(String s) { return null; }
        });
        provider.valueProperty().addListener((o, a, b) -> {
            dev.nexus.launcher.core.Settings.get().aiProvider = b.id;
            dev.nexus.launcher.core.Settings.save();
        });

        PasswordField key = new PasswordField();
        key.setPromptText(I18n.t("ai.keyPrompt"));
        key.setMinWidth(300);
        HBox.setHgrow(key, Priority.ALWAYS);
        // Auto-pick the provider in the combo as soon as the key's shape is recognisable (gsk_ - / sk-or-).
        key.textProperty().addListener((o, a, b) -> provider.getSelectionModel().select(AiAssistant.detect(b)));

        Button getKey = Fx.button(I18n.t("ai.getKey"), Icons.OPEN, "ghost");
        getKey.setOnAction(e -> window.openLink(provider.getValue().keysUrl));

        HBox row = new HBox(10, provider, key);
        row.setAlignment(Pos.CENTER_LEFT);

        CheckBox consent = new CheckBox(I18n.t("ai.consent"));
        consent.setWrapText(true);
        consent.setMaxWidth(640);

        Button connect = Fx.button(I18n.t("ai.connect"), Icons.SPARKLE, "primary");
        Label offline = Fx.label(I18n.t("ai.freeNote"), "faint", "tiny");
        offline.setWrapText(true);

        connect.setOnAction(e -> {
            if (!consent.isSelected()) {
                Toasts.error(I18n.t("ai.consentRequired"));
                return;
            }
            connect.setDisable(true);
            connect.setText(I18n.t("ai.connecting"));
            String k = key.getText();
            Provider chosen = provider.getValue() == null ? AiAssistant.detect(k) : provider.getValue();
            Fx.async(() -> {
                AiAssistant.connect(k, chosen);
                return chosen;
            }, p -> {
                Toasts.success(I18n.t("ai.connected", p.title));
                refreshAi();
            }, err -> {
                connect.setDisable(false);
                connect.setText(I18n.t("ai.connect"));
                Toasts.error(Fx.errorText(err));
            });
        });

        VBox box = new VBox(10, text, row, getKey, consent, connect, offline);
        return box;
    }

    private Node buildChat() {
        Provider p = AiAssistant.provider();
        Label status = Fx.label(I18n.t("ai.statusConnected", p.title), "dim", "tiny");

        Button disconnect = Fx.button(I18n.t("ai.disconnect"), Icons.CLOSE, "ghost");
        disconnect.setOnAction(e -> {
            AiAssistant.disconnect();
            chatBox.getChildren().clear();
            refreshAi();
        });
        HBox top = new HBox(10, status, Fx.spacer(), disconnect);
        top.setAlignment(Pos.CENTER_LEFT);

        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setPrefViewportHeight(240);
        chatScroll.setMaxHeight(300);
        chatScroll.getStyleClass().add("chat-scroll");
        chatBox.heightProperty().addListener((o, a, b) -> Platform.runLater(() -> chatScroll.setVvalue(1.0)));
        if (chatBox.getChildren().isEmpty()) {
            chatBox.getChildren().add(hint(I18n.t("ai.empty")));
        }

        TextField input = new TextField();
        input.setPromptText(I18n.t("ai.inputPrompt"));
        HBox.setHgrow(input, Priority.ALWAYS);
        Button send = Fx.iconButton(Icons.SEND, I18n.t("ai.send"), "primary");
        Runnable sendRun = () -> sendQuestion(input, send);
        send.setOnAction(e -> sendRun.run());
        input.setOnAction(e -> sendRun.run());

        HBox bottom = new HBox(8, input, send);
        bottom.setAlignment(Pos.CENTER);

        Button askFinding = Fx.button(I18n.t("ai.askFindings"), Icons.SPARKLE, "ghost");
        askFinding.setOnAction(e -> sendDoctorDigest(input, send));

        return new VBox(8, top, chatScroll, askFinding, bottom);
    }

    private void sendDoctorDigest(TextField input, Button send) {
        Instance inst = picker.getValue();
        if (inst == null) {
            Toasts.info(I18n.t("doctor.noinst"));
            return;
        }
        if (input.getText().isBlank()) input.setText(I18n.t("ai.digestQuestion"));
        sendQuestion(input, send);
    }

    /** Appends the question bubble, runs the request off-FX, appends the answer (or an error bubble). */
    private void sendQuestion(TextField input, Button send) {
        String q = input.getText().trim();
        if (q.isEmpty()) return;
        Instance inst = picker.getValue();
        input.clear();
        input.setDisable(true);
        send.setDisable(true);
        clearEmptyHint();
        chatBox.getChildren().add(bubble(q, true));
        Label thinking = bubble(I18n.t("ai.thinking"), false);
        thinking.getStyleClass().add("faint");
        chatBox.getChildren().add(thinking);

        Fx.async(() -> {
            String digest = inst == null ? "" : doctorDigest(inst);
            return AiAssistant.ask(inst, q, digest);
        }, answer -> {
            chatBox.getChildren().remove(thinking);
            chatBox.getChildren().add(bubble(answer, false));
            input.setDisable(false);
            send.setDisable(false);
            input.requestFocus();
        }, err -> {
            chatBox.getChildren().remove(thinking);
            Label bad = bubble(I18n.t("ai.errorPrefix") + " " + Fx.errorText(err), false);
            bad.setStyle("-fx-border-color: -nx-danger; -fx-border-width: 0 0 0 3;");
            chatBox.getChildren().add(bad);
            input.setDisable(false);
            send.setDisable(false);
            input.requestFocus();
        });
    }

    private void clearEmptyHint() {
        if (chatBox.getChildren().size() == 1 && chatBox.getChildren().get(0) instanceof Label l
                && I18n.t("ai.empty").equals(l.getText())) {
            chatBox.getChildren().clear();
        }
    }

    /** Short digest of the doctor's current findings, attached to the AI context. */
    private String doctorDigest(Instance inst) {
        try {
            List<Finding> fs = BuildDoctor.check(inst);
            StringBuilder b = new StringBuilder();
            for (Finding f : fs) b.append("· ").append(f.title()).append(" — ").append(f.text()).append('\n');
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Label bubble(String text, boolean mine) {
        Label l = Fx.label(text, "small");
        l.setWrapText(true);
        l.setMaxWidth(700);
        l.getStyleClass().add("card-flat");
        l.setStyle("-fx-padding: 8 12 8 12;");
        if (mine) l.setStyle("-fx-padding: 8 12 8 12; -fx-background-color: -nx-accent-soft;");
        return l;
    }

    /* ---------------------------------------------- offline doctor ---------------------------------------------- */

    private void runCheck(Button btn) {
        Instance inst = picker.getValue();
        results.getChildren().clear();
        if (inst == null) {
            results.getChildren().add(hint(I18n.t("doctor.noinst")));
            return;
        }
        btn.setDisable(true);
        btn.setText(I18n.t("doctor.checking"));
        Fx.async(() -> BuildDoctor.check(inst), findings -> {
            btn.setDisable(false);
            btn.setText(I18n.t("doctor.check"));
            paint(findings);
        }, err -> {
            btn.setDisable(false);
            btn.setText(I18n.t("doctor.check"));
            results.getChildren().add(hint(Fx.errorText(err)));
        });
    }

    private void paint(List<Finding> findings) {
        results.getChildren().clear();
        int problems = 0;
        for (Finding f : findings) {
            if (f.level() != BuildDoctor.Level.INFO) problems++;
            results.getChildren().add(findingCard(f));
        }
        if (problems > 0) {
            Label sum = Fx.label(I18n.t("doctor.found", problems), "dim", "small");
            results.getChildren().add(0, sum);
        }
    }

    private Node findingCard(Finding f) {
        Label title = Fx.label(f.title(), "strong");
        Label text = Fx.label(f.text(), "dim", "small");
        text.setWrapText(true);

        VBox body = new VBox(4, title, text);
        HBox actions = new HBox(6);
        if (f.fix() != null) {
            Button fix = Fx.button(f.fix().label(), Icons.SPEED, "ghost");
            fix.setOnAction(e -> {
                fix.setDisable(true);
                fix.setText(I18n.t("doctor.fixing"));
                Fx.async(() -> f.fix().action().call(), msg -> {
                    Toasts.success(msg);
                    fxLater();
                }, err -> {
                    fix.setDisable(false);
                    fix.setText(f.fix().label());
                    Toasts.error(Fx.errorText(err));
                });
            });
            actions.getChildren().add(fix);
        }
        if (!actions.getChildren().isEmpty()) body.getChildren().add(actions);
        String color = switch (f.level()) {
            case ERROR -> "-nx-danger";
            case WARN -> "-nx-warning";
            case INFO -> "-nx-success";
        };
        javafx.scene.layout.HBox cardRow = new javafx.scene.layout.HBox(body);
        cardRow.getStyleClass().add("card-flat");
        cardRow.setStyle("-fx-padding: 12; -fx-border-width: 0 0 0 4; -fx-border-color: " + color
                + "; -fx-border-radius: 4 14 14 4;");
        return cardRow;
    }

    /** After a successful fix, rerun the check so the issue disappears from the list. */
    private void fxLater() {
        Instance inst = picker.getValue();
        if (inst == null) return;
        Fx.async(() -> BuildDoctor.check(inst), this::paint, err -> { });
    }

    private Node faqCard() {
        VBox box = new VBox(8);
        String[] pairs = {"q1", "q2", "q3", "q4", "q5"};
        for (String k : pairs) {
            Label q = Fx.label(I18n.t("faq." + k), "strong", "small");
            q.setWrapText(true);
            Label a = Fx.label(I18n.t("faq.a" + k.substring(1)), "dim", "small");
            a.setWrapText(true);
            box.getChildren().add(new VBox(2, q, a));
        }
        return card(I18n.t("doctor.faq"), hint(I18n.t("doctor.faqSub")), box);
    }

    @Override
    public void onHide() { }
}
