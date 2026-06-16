import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.datatransfer.*;
import java.awt.Toolkit;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

class Question {
    String mondai, kaisetsu, seikai;
    Question(String mondai, String kaisetsu, String seikai) {
        this.mondai = mondai;
        this.kaisetsu = kaisetsu;
        this.seikai = seikai;
    }
}

public class QuizApp {
    static List<Question> questionList;
    static int currentIndex = 0;
    static JLabel statusLabel = new JLabel();
    static int lastInsertedId = -1;

    public static void main(String[] args) throws Exception {
        //----------------------------
        System.out.println("サーバー起動中...");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/add", exchange -> {
            // Chromeから送られてきたデータ(JSON)を読み取る
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            String json = new String(bytes);

            // 簡易的に「選択したテキスト」だけを抽出するロジック
            String text = json.split("\"text\":\"")[1].split("\"}")[0];
            String type = json.split("\"type\":\"")[1].split("\"")[0];

            //---------------------------------------
            if ("regMondai".equals(type)) {
                lastInsertedId = insertMondai(text); // ここでIDを保存！
                showAutoClosingDialog("問題をセットしました");
            } else {
                updateKaisetsu(lastInsertedId, text); // 覚えていたIDを更新！
                questionList = getAllQuestions(); // DB更新後に再読み込み
                showAutoClosingDialog("解説を保存しました！");
            }
            //-------------------------------

            String response = "OK";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });


        //-----------------------------------
        server.start();

        //-------------------------
        questionList = getAllQuestions();
        createFloatingPalette();

        JFrame frame = new JFrame("最強の問題学習ツール");
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("試験モード", createQuizPanel(frame));
        tabbedPane.addTab("問題登録", createRegistrationPanel());
        frame.add(tabbedPane);
        frame.setSize(700, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public static void deleteCurrentQuestion(int id) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            PreparedStatement pstmt = conn.prepareStatement("DELETE FROM unko WHERE id = ?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }


    // --- 通知用メソッド（0.5秒で自動消去） ---
    public static void showAutoClosingDialog(String message) {
        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        dialog.setTitle("通知");
        dialog.add(new JLabel("  " + message + "  "));
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        Timer timer = new Timer(500, e -> dialog.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    // --- 登録パレット ---
    public static void createFloatingPalette() {
        JFrame palette = new JFrame("登録パレット");
        palette.setAlwaysOnTop(true);
        palette.setLayout(new FlowLayout());

        JButton btnM = new JButton("①問題セット");
        JButton btnK = new JButton("②解説セット＆保存");
        String[] options = {"〇", "✖"};
        JComboBox<String> cbS = new JComboBox<>(options);

        final String[] buffer = {"", ""};

        btnM.addActionListener(e -> {
            buffer[0] = getClipboardText();
            showAutoClosingDialog("問題をセットしました");
        });

        btnK.addActionListener(e -> {
            buffer[1] = getClipboardText();
            String seikaiValue = (String) cbS.getSelectedItem();
            saveToDB(buffer[0], buffer[1], seikaiValue);
            questionList = getAllQuestions();
            showAutoClosingDialog("保存完了！(正解:" + seikaiValue + ")");
        });

        palette.add(btnM); palette.add(cbS); palette.add(btnK);
        palette.pack();
        palette.setLocation(100, 100);
        palette.setVisible(true);
    }

    public static String getClipboardText() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) { return ""; }
    }

    // --- 試験モード ---
    public static JPanel createQuizPanel(JFrame frame) {
        JPanel panel = new JPanel(new BorderLayout(50,5));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setFont(new Font("Yu Gothic", Font.PLAIN, 21));
        //------------------------------------------
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), // 薄い枠線
                BorderFactory.createEmptyBorder(12, 15, 15, 15)           // 中に余白
        ));

        //-----------------------------
        JTextArea kaisetsuArea = new JTextArea(5, 19);
        kaisetsuArea.setEditable(false);
        kaisetsuArea.setLineWrap(true);
        kaisetsuArea.setBackground(new Color(255, 255, 255));
        kaisetsuArea.setBorder(BorderFactory.createTitledBorder("解説"));

        // ★ここを追加：フォントサイズを今の1.5倍にする（今のフォントサイズが20なら30に設定）
        kaisetsuArea.setFont(new Font("Yu Gothic", Font.PLAIN, 20));

        Runnable updateText = () -> {
            if (!questionList.isEmpty()) {
                area.setText(questionList.get(currentIndex).mondai);
                kaisetsuArea.setText("");
                statusLabel.setText("表示中: 問題 " + (currentIndex + 1) + " / " + questionList.size());
            }
        };
        updateText.run();

        JButton btn1 = new JButton("〇");
        JButton btn2 = new JButton("✖");
        JButton prevBtn = new JButton("戻る");
        JButton nextBtn = new JButton("次へ");


        btn1.addActionListener(e -> {
            Question q = questionList.get(currentIndex);
            if ("〇".equals(q.seikai)) kaisetsuArea.setText("正解！その調子！");
            else kaisetsuArea.setText("不正解！ \n解説: " + q.kaisetsu);//正解は「" + q.seikai + "」です。
        });

        btn2.addActionListener(e -> {
            Question q = questionList.get(currentIndex);
            if ("✖".equals(q.seikai)) kaisetsuArea.setText("正解！その調子！");
            else kaisetsuArea.setText("不正解！ \n解説: " + q.kaisetsu);//正解は「" + q.seikai + "」です。
        });

        prevBtn.addActionListener(e -> { if (currentIndex > 0) { currentIndex--; updateText.run(); } });
        nextBtn.addActionListener(e -> { if (currentIndex < questionList.size() - 1) { currentIndex++; updateText.run(); } });

        JPanel btnPanel = new JPanel();
        btnPanel.add(statusLabel); btnPanel.add(prevBtn); btnPanel.add(btn1); btnPanel.add(btn2); btnPanel.add(nextBtn);

        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.add(new JScrollPane(kaisetsuArea), BorderLayout.CENTER);
        southContainer.add(btnPanel, BorderLayout.SOUTH);

        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(southContainer, BorderLayout.SOUTH);
        return panel;
    }

    // --- 問題登録タブ ---
    public static JPanel createRegistrationPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 1));
        JTextField tfM = new JTextField("問題");
        JTextField tfK = new JTextField("解説");
        JComboBox<String> cbS = new JComboBox<>(new String[]{"〇", "✖"});
        JButton btn = new JButton("保存");

        btn.addActionListener(e -> {
            saveToDB(tfM.getText(), tfK.getText(), (String)cbS.getSelectedItem());
            questionList = getAllQuestions();
            showAutoClosingDialog("保存完了！");
        });
        panel.add(tfM); panel.add(tfK); panel.add(cbS); panel.add(btn);
        return panel;
    }

    public static List<Question> getAllQuestions() {
        List<Question> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT MONDAI, KAISETU, SEIKAI FROM unko");
            while (rs.next()) list.add(new Question(rs.getString("MONDAI"), rs.getString("KAISETU"), rs.getString("SEIKAI")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static void saveToDB(String m, String k, String s) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO unko (MONDAI, KAISETU, SEIKAI) VALUES (?, ?, ?)");
            pstmt.setString(1, m);
            pstmt.setString(2, k);
            pstmt.setString(3, s);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public static int insertMondai(String m) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            // 問題だけINSERT
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO unko (MONDAI, KAISETU, SEIKAI) VALUES (?, '', '〇')", Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, m);
            pstmt.executeUpdate();

            // 最後に作成されたIDを取得
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }
    public static void updateKaisetsu(int id, String k) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE unko SET KAISETU = ? WHERE id = ?");
            pstmt.setString(1, k);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

}