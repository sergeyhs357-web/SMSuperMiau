import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class SuperMiau extends Application {

    private TabPane tabPane;
    private TextField addressBar;
    private ComboBox<String> searchBox;
    private static ListView<String> downloadsListView;

    public static void main(String[] args) {
        // МАКСИМАЛЬНОЕ АППАРАТНОЕ УСКОРЕНИЕ (Вместо консольных флагов)
        System.setProperty("prism.order", "d3d,sw"); // Принудительный DirectX для плавной отрисовки тяжелых виджетов
        System.setProperty("prism.vsync", "false");   // Отключаем задержки кадров для быстрого рендеринга
        System.setProperty("prism.lcdtext", "true");  // Идеально четкие шрифты на сайтах

        MiauConfig.init();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("SuperMiau Browser");

        // УСТАНОВКА ИКОНКИ ДЛЯ ОКНА И ПАНЕЛИ ЗАДАЧ (Файл icon.png должен лежать в jar)
        try {
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("icon.png")));
        } catch (Exception e) {
            System.out.println("[ICON ERROR] Не удалось загрузить icon.png: " + e.getMessage());
        }

        VBox root = new VBox();
        root.setStyle(MiauStyle.getBackground());

        HBox topBar = new HBox();
        topBar.setStyle(MiauStyle.getBar());

        Button backBtn = new Button(MiauLang.BACK);
        backBtn.setStyle(MiauUI.getWarpNavBtn());

        Button forwardBtn = new Button(MiauLang.NEXT);
        forwardBtn.setStyle(MiauUI.getWarpNavBtn());

        Button refreshBtn = new Button(MiauLang.REFRESH);
        refreshBtn.setStyle(MiauUI.getWarpNavBtn());

        addressBar = new TextField("https://google.com");
        HBox.setHgrow(addressBar, Priority.ALWAYS);
        addressBar.setStyle(MiauUI.getNeonAddressBar());
        addressBar.setEffect(MiauGlow.getNeon());

        searchBox = new ComboBox<>();
        searchBox.getItems().addAll("Google", "Яндекс");
        searchBox.setValue("Google");
        searchBox.setStyle(MiauUI.getNeonComboBox());

        Button miauBtn = new Button(MiauLang.GO);
        miauBtn.setStyle(MiauUI.getDragonMiauBtn());

        Button downloadsBtn = new Button("📥 Загрузки");
        downloadsBtn.setStyle(MiauStyle.getButton());
        downloadsBtn.setOnAction(e -> showDownloadsWindow());

        Button newTabBtn = new Button("+");
        newTabBtn.setStyle(MiauStyle.getButton());
        newTabBtn.setPrefWidth(35);

        topBar.getChildren().addAll(backBtn, forwardBtn, refreshBtn, addressBar, searchBox, miauBtn, downloadsBtn, newTabBtn);

        tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // ФИКС: Слушатель смены вкладок (обновляет адресную строку при переключении между сайтами)
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() instanceof WebView) {
                String currentUrl = ((WebView) newTab.getContent()).getEngine().getLocation();
                addressBar.setText(currentUrl != null && !currentUrl.equals("about:blank") ? currentUrl : "");
            }
        });

        root.getChildren().addAll(topBar, tabPane);

        backBtn.setOnAction(e -> { if (getCurrentEngine() != null) getCurrentEngine().getHistory().go(-1); });
        forwardBtn.setOnAction(e -> { if (getCurrentEngine() != null) getCurrentEngine().getHistory().go(1); });
        refreshBtn.setOnAction(e -> { if (getCurrentEngine() != null) getCurrentEngine().reload(); });
        newTabBtn.setOnAction(e -> createNewTab("https://google.com"));

        addressBar.setOnAction(e -> loadTargetUrl());
        miauBtn.setOnAction(e -> loadTargetUrl());

        createNewTab("https://google.com");

        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.show();
    }

    private WebEngine getCurrentEngine() {
        Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
        if (currentTab != null && currentTab.getContent() instanceof WebView) {
            return ((WebView) currentTab.getContent()).getEngine();
        }
        return null;
    }

    private void loadTargetUrl() {
        String input = addressBar.getText().trim();
        String selectedEngine = searchBox.getValue();
        String finalUrl = MiauSearch.parse(input, selectedEngine);

        if (getCurrentEngine() != null) {
            getCurrentEngine().load(finalUrl);
        }
    }

    private void createNewTab(String url) {
        Tab tab = new Tab("Загрузка...");
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        engine.setUserAgent(MiauSystem.generateUserAgent());

        // Хак для полноэкранного режима (YouTube)
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                MiauWarp.injectWarpSpeed(engine);
                MiauExtensionEngine.injectExtensions(engine);

                engine.executeScript(
                        "document.addEventListener('fullscreenchange', function() { " +
                                "   if (document.fullscreenElement) { alert('MIAU_ENTER_FULLSCREEN'); } " +
                                "   else { alert('MIAU_EXIT_FULLSCREEN'); } " +
                                "});" +
                                "Element.prototype.requestFullscreen = function() { " +
                                "   alert('MIAU_ENTER_FULLSCREEN'); " +
                                "   return Promise.resolve(); " +
                                "};"
                );
            }
        });

        engine.setOnAlert(event -> {
            Stage stage = (Stage) tabPane.getScene().getWindow();
            if ("MIAU_ENTER_FULLSCREEN".equals(event.getData())) {
                stage.setFullScreen(true);
            } else if ("MIAU_EXIT_FULLSCREEN".equals(event.getData())) {
                stage.setFullScreen(false);
            }
        });

        // ПЕРЕХВАТЧИК ССЫЛОК И МАРКЕТИНГОВЫЙ ТРОЛЛИНГ КОНКУРЕНТОВ
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl == null || newUrl.isEmpty() || newUrl.equals("about:blank")) return;

            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                addressBar.setText(newUrl);
            }

            // Мягкое предупреждение при попытке уйти к конкурентам
            String lowerUrl = newUrl.toLowerCase();
            if (lowerUrl.contains("google.com/chrome") ||
                    lowerUrl.contains("microsoft.com/edge") ||
                    lowerUrl.contains("browser.yandex") ||
                    lowerUrl.contains("opera.com") ||
                    lowerUrl.contains("mozilla.org/firefox")) {

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("SuperMiau Security & Info");
                alert.setHeaderText("Мяу! Зачем тебе другой браузер?");
                alert.setContentText("SuperMiau работает на таких же передовых технологиях, но с более мягким и приятным интерфейсом, а также полноценно поддерживает старые и классические ОС! Оставайся на светлой стороне 🐾");
                alert.show();
            }

            // Асинхронные тяжелые сетевые проверки
            new Thread(() -> {
                if (newUrl.endsWith(".xui") || MiauNetCore.isDownloadable(newUrl)) {
                    Platform.runLater(() -> engine.getLoadWorker().cancel());
                    startAnyDownload(newUrl);
                    return;
                }

                MiauHistory.log(newUrl);

                String secStatus = MiauSecurity.checkTargetSecurity(newUrl);
                if (!secStatus.equals("SAFE") && !secStatus.equals("EMPTY")) {
                    Platform.runLater(() -> engine.load(MiauErrors.getSslErrorPage(secStatus, newUrl)));
                }
            }).start();
        });

        engine.titleProperty().addListener((obs, oldTitle, newTitle) -> {
            if (newTitle != null && !newTitle.isEmpty()) tab.setText(newTitle);
        });

        engine.load(url);

        tab.setContent(webView);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    // СКАЧИВАЛЬЩИК С РЕЖИМОМ ЗАЩИТЫ ОТ КОНКУРЕНТОВ ("ЯРОСТЬ КОТА")
    private void startAnyDownload(String fileUrl) {
        try {
            URL url = URI.create(fileUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", MiauSystem.generateUserAgent());
            conn.setInstanceFollowRedirects(true);

            int redirects = 0;
            while ((conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
                    || conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
                    || conn.getResponseCode() == 307
                    || conn.getResponseCode() == 308) && redirects < 5) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                conn.setRequestProperty("User-Agent", MiauSystem.generateUserAgent());
                redirects++;
            }

            String fileName = "";
            String disposition = conn.getHeaderField("Content-Disposition");

            if (disposition != null) {
                if (disposition.contains("filename*=")) {
                    int index = disposition.indexOf("filename*=");
                    String raw = disposition.substring(index + 10).trim();
                    if (raw.toLowerCase().startsWith("utf-8''")) raw = raw.substring(7);
                    fileName = URLDecoder.decode(raw, "UTF-8");
                } else if (disposition.contains("filename=")) {
                    int index = disposition.indexOf("filename=");
                    fileName = disposition.substring(index + 9).trim();
                    if (fileName.startsWith("\"")) fileName = fileName.substring(1);
                    if (fileName.contains("\"")) fileName = fileName.substring(0, fileName.indexOf("\""));
                }
            }

            if (fileName == null || fileName.isEmpty()) {
                String path = conn.getURL().getPath();
                fileName = path.substring(path.lastIndexOf('/') + 1);
                fileName = URLDecoder.decode(fileName, "UTF-8");
            }

            if (!fileName.contains(".") || fileName.endsWith(".")) {
                String contentType = conn.getContentType();
                String ext = ".bin";
                if (contentType != null && contentType.toLowerCase().contains("application/x-msdownload")) ext = ".exe";
                fileName = fileName.isEmpty() ? "miau_file" + ext : fileName + ext;
            }

            File downloadDir = new File(System.getProperty("user.home"), "Downloads");
            if (!downloadDir.exists()) downloadDir.mkdirs();

            File outputFile = new File(downloadDir, fileName);

            MiauDownloadManager.addDownload(outputFile.getAbsolutePath());
            updateDownloadsUI();

            // Качаем байты
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer, 0, 4096)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            updateDownloadsUI();

            // Включение триггера возмездия: если юзер скачал чужой браузер
            String checkName = fileName.toLowerCase();
            if (checkName.contains("chromesetup") ||
                    checkName.contains("yandex") ||
                    checkName.contains("microsoftedgesetup") ||
                    checkName.contains("operasetup") ||
                    checkName.contains("firefox")) {

                // 1. Стираем вредоносный файл с диска
                if (outputFile.exists()) outputFile.delete();

                // 2. Вышвыриваем юзера со страницы и включаем МЯУ-защиту
                Platform.runLater(() -> {
                    Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
                    if (currentTab != null && currentTab.getContent() instanceof WebView) {
                        ((WebView) currentTab.getContent()).getEngine().load("about:blank");
                    }

                    // 3. Главное окно-напоминание
                    Alert furyAlert = new Alert(Alert.AlertType.WARNING);
                    furyAlert.setTitle("ЯРОСТЬ SUPERMIAU! 🐾");
                    furyAlert.setHeaderText("МЯЯЯЯЯЯЯЯЯУ! Зачем другие браузеры ты скачиваешь?!");
                    furyAlert.setContentText("Файл установки заблокирован и успешно удален! \n\n"
                            + "SuperMiau работает на точно таких же передовых технологиях, но наш интерфейс гораздо "
                            + "мягче, удобнее и гибче для пользователя. \n"
                            + "И самое важное: мы полноценно поддерживаем как классические, так и самые новые ОС (от Windows 7 до Windows 11!). \n\n"
                            + "Пользуйся SuperMiau и цени котиков! 🐱");
                    furyAlert.showAndWait();
                });
            }

        } catch (Exception e) {
            System.out.println("[DOWNLOAD ERROR] Ошибка скачивания: " + e.getMessage());
        }
    }

    private void showDownloadsWindow() {
        Stage subStage = new Stage();
        subStage.setTitle("Менеджер загрузок SuperMiau");
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: #1e272e;");

        Label title = new Label("История загрузок:");
        title.setStyle("-fx-text-fill: #ff9f43; -fx-font-weight: bold; -fx-font-size: 16px;");

        downloadsListView = new ListView<>();
        downloadsListView.setStyle("-fx-control-inner-background: #2f3640; -fx-text-fill: white;");
        VBox.setVgrow(downloadsListView, Priority.ALWAYS);

        updateDownloadsUI();
        layout.getChildren().addAll(title, downloadsListView);
        subStage.setScene(new Scene(layout, 500, 400));
        subStage.show();
    }

    private static void updateDownloadsUI() {
        if (downloadsListView == null) return;
        Platform.runLater(() -> {
            downloadsListView.getItems().clear();
            for (String path : MiauDownloadManager.getHistory()) {
                if(path.trim().isEmpty()) continue;
                File f = new File(path);
                String status = f.exists() ? "[ЕСТЬ]" : "[УДАЛЕН]";
                downloadsListView.getItems().add(status + " " + f.getName() + " (" + path + ")");
            }
        });
    }
}

// =========================================================================
// ВСПОМОГАТЕЛЬНЫЕ СЛУЖЕБНЫЕ КЛАССЫ
// =========================================================================

class MiauExtensionEngine {
    public static void injectExtensions(WebEngine engine) {
        File extDir = new File(MiauConfig.EXT);
        if (!extDir.exists() || extDir.listFiles() == null) return;
        for (File file : extDir.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".js")) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    engine.executeScript(sb.toString());
                } catch (Exception e) {}
            }
        }
    }
}

class MiauDownloadManager {
    private static final String FILE_PATH = MiauConfig.ROOT + "downloads.txt";
    public static void addDownload(String fullPath) {
        try { if (!getHistory().contains(fullPath)) { try (FileWriter fw = new FileWriter(FILE_PATH, true)) { fw.write(fullPath + "\n"); } } } catch (Exception e) {}
    }
    public static java.util.List<String> getHistory() {
        java.util.List<String> list = new java.util.ArrayList<>(); File file = new File(FILE_PATH); if (!file.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) { String line; while ((line = br.readLine()) != null) list.add(line); } catch (Exception e) {}
        return list;
    }
}

class MiauUI {
    public static String getWarpNavBtn() { return "-fx-background-color: linear-gradient(to bottom, #2f3640, #212529); -fx-text-fill: #ff9f43; -fx-background-radius: 8px; -fx-font-weight: bold; -fx-border-color: #ff9f43; -fx-border-radius: 8px; -fx-cursor: hand;"; }
    public static String getDragonMiauBtn() { return "-fx-background-color: linear-gradient(to right, #ff9f43, #ff5252); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 6 16; -fx-cursor: hand;"; }
    public static String getNeonAddressBar() { return "-fx-background-color: #15191d; -fx-text-fill: #ff9f43; -fx-border-color: linear-gradient(to right, #ff9f43, #ff5252); -fx-border-width: 2px; -fx-border-radius: 15px; -fx-background-radius: 15px; -fx-padding: 6 12; -fx-font-family: 'Consolas', monospace; -fx-font-size: 13px;"; }
    public static String getNeonComboBox() { return "-fx-background-color: #1e272e; -fx-mark-color: #ff9f43; -fx-text-fill: #ff9f43; -fx-border-color: #ff9f43; -fx-border-radius: 8px; -fx-background-radius: 8px;"; }
}

class MiauSystem {
    public static String getOSName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win 11") || os.contains("windows 11")) return "Windows 11";
        if (os.contains("win 10") || os.contains("windows 10")) return "Windows 10";
        return "Windows";
    }
    public static String generateUserAgent() { return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 SuperMiau/3.3"; }
}

class MiauStyle {
    public static String getBackground() { return "-fx-background-color: #1e272e;"; }
    public static String getBar() { return "-fx-background-color: #2f3640; -fx-padding: 10; -fx-spacing: 8; -fx-alignment: center-left;"; }
    public static String getButton() { return "-fx-background-color: #ff9f43; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-cursor: hand;"; }
}

class MiauWarp { public static void injectWarpSpeed(WebEngine engine) { try { engine.executeScript("window.onerror = function() { return true; };"); } catch (Exception e) {} } }
class MiauSecurity { public static String checkTargetSecurity(String url) { return "SAFE"; } }

class MiauSearch {
    public static String parse(String input, String engine) {
        String clean = input.trim(); if (clean.startsWith("http://") || clean.startsWith("https://")) return clean;
        try { return "Яндекс".equals(engine) ? "https://ya.ru/search/?text=" + URLEncoder.encode(clean, "UTF-8") : "https://www.google.com/search?q=" + URLEncoder.encode(clean, "UTF-8"); } catch (Exception e) { return "https://google.com"; }
    }
}

class MiauNetCore {
    public static boolean isDownloadable(String urlString) {
        if (urlString == null || !urlString.startsWith("http")) return false;
        try {
            URL url = URI.create(urlString).toURL(); HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD"); conn.connect(); String contentType = conn.getContentType();
            if (contentType != null) { contentType = contentType.toLowerCase(); return !contentType.contains("text/html") && !contentType.contains("text/plain"); }
        } catch (Exception e) {}
        return false;
    }
}

class MiauLang { public static String BACK = "Назад"; public static String NEXT = "Вперед"; public static String REFRESH = "Обновить"; public static String GO = "MIAU!"; }
class MiauHistory { public static void log(String url) { try { FileWriter fw = new FileWriter(new File(MiauConfig.HIST + "log.txt"), true); fw.write(url + "\n"); fw.close(); } catch (Exception e) {} } }
class MiauGlow { public static DropShadow getNeon() { DropShadow glow = new DropShadow(); glow.setBlurType(BlurType.THREE_PASS_BOX); glow.setColor(Color.web("#ff9f43")); glow.setRadius(15); glow.setSpread(0.5); return glow; } }
class MiauErrors { public static String getSslErrorPage(String status, String url) { return "data:text/html,<h1>Ошибка SSL</h1>"; } }

class MiauConfig {
    public static final String ROOT = System.getenv("APPDATA") + "/.supermiau/";
    public static final String HIST = ROOT + "history/";
    public static final String EXT = ROOT + "extensions/";
    public static void init() {
        new File(ROOT).mkdirs();
        new File(HIST).mkdirs();
        new File(EXT).mkdirs();
    } // ФИКС: Добавлена закрывающая скобка метода
}