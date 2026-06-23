import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.datatransfer.*;
import java.awt.Toolkit;

class Question {
    String mondai, kaisetsu, seikai,type;
    String choiceA, choiceB, choiceC, choiceD; // 四択用

    Question(String mondai, String kaisetsu, String seikai,String type) {
        this.mondai = mondai;
        this.kaisetsu = kaisetsu;
        this.seikai = seikai;
        this.type = type;

    }
}

public class QuizApp {
    static List<Question> questionList;
    static int currentIndex = 0;
    static JLabel statusLabel = new JLabel();
    static JPanel btnPanel = new JPanel();
    static JTextArea area = new JTextArea();
    static JTextArea kaisetsuArea = new JTextArea(5, 19);

    public static void main(String[] args) throws Exception {
        questionList = getAllQuestions();

        createFloatingPalette();

        JFrame frame = new JFrame("最強の問題学習ツール");
        frame.add(createQuizPanel());
        frame.setSize(700, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
    //---------------------------------------------------------------------------
    public static void createFloatingPalette() {
        JFrame palette = new JFrame("登録パレット");
        palette.setAlwaysOnTop(true);
        palette.setLayout(new GridLayout(0, 1));

        JPanel panel2 = new JPanel();
        JButton btnM2 = new JButton("①問題(2択)");
        JComboBox<String> cbS2 = new JComboBox<>(new String[]{"〇", "✖"});
        JButton btnK2 = new JButton("②解説＆保存(2択)");
        panel2.add(btnM2); panel2.add(cbS2); panel2.add(btnK2);

        JPanel panel4 = new JPanel();
        JButton btnM4 = new JButton("①問題(4択)");
        JComboBox<String> cbS4 = new JComboBox<>(new String[]{"A", "B", "C", "D"});
        JButton btnK4 = new JButton("②解説＆保存(4択)");
        panel4.add(btnM4); panel4.add(cbS4); panel4.add(btnK4);

        final String[] buffer = {"", ""};

        btnM2.addActionListener(e -> { buffer[0] = getClipboardText(); showAutoClosingDialog("問題セット"); });
        btnK2.addActionListener(e -> {
            buffer[1] = getClipboardText();
            String seikai = (String) cbS2.getSelectedItem();
            saveToDBWithMode(buffer[0], buffer[1], seikai, "2");
            questionList = getAllQuestions();
            showAutoClosingDialog("保存完了！(" + seikai + ")");
        });

        btnM4.addActionListener(e -> { buffer[0] = getClipboardText(); showAutoClosingDialog("問題セット"); });
        btnK4.addActionListener(e -> {
            buffer[1] = getClipboardText();
            String seikai = (String) cbS4.getSelectedItem();
            saveToDBWithMode(buffer[0], buffer[1], seikai, "4");
            questionList = getAllQuestions();
            showAutoClosingDialog("保存完了！(" + seikai + ")");
        });

        palette.add(panel2);
        palette.add(panel4);
        palette.pack();
        palette.setLocation(100, 100);
        palette.setVisible(true);
    }
    //-------------------------------------------------------------

    public static void showAutoClosingDialog(String message) {
        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        dialog.add(new JLabel("  " + message + "  "));
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        Timer timer = new Timer(500, e -> dialog.dispose());
        timer.setRepeats(false); timer.start();
    }

    public static String getClipboardText() {
        try { return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor); }
        catch (Exception e) { return ""; }
    }
    //-----------------------------------------------------

    public static JPanel createQuizPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        area.setEditable(false);
        area.setLineWrap(true);
        area.setFont(new Font("Yu Gothic", Font.PLAIN, 21));

        kaisetsuArea.setEditable(false);
        kaisetsuArea.setLineWrap(true);
        kaisetsuArea.setBorder(BorderFactory.createTitledBorder("解説"));
        kaisetsuArea.setFont(new Font("Yu Gothic", Font.PLAIN, 20));

        panel.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.add(new JScrollPane(kaisetsuArea), BorderLayout.CENTER);
        southContainer.add(btnPanel, BorderLayout.SOUTH);
        panel.add(southContainer, BorderLayout.SOUTH);

        refreshUI();
        return panel;
    }

    public static void refreshUI() {
        if (questionList.isEmpty()) return;
        Question q = questionList.get(currentIndex);
        area.setText(q.mondai);
        kaisetsuArea.setText("");
        statusLabel.setText("問題 " + (currentIndex + 1) + " / " + questionList.size());

        btnPanel.removeAll();
        btnPanel.add(statusLabel);

        if ("4".equals(q.type)) {
            String[] choices = {"A", "B", "C", "D"};
            for (String c : choices) {
                JButton btn = new JButton(c);
                btn.addActionListener(e -> {
                    if (c.equals(q.seikai)) kaisetsuArea.setText("正解！");
                    else kaisetsuArea.setText("不正解！\n解説: " + q.kaisetsu);
                });
                btnPanel.add(btn);
            }
        } else {
            JButton b1 = new JButton("〇"), b2 = new JButton("✖");
            b1.addActionListener(e -> kaisetsuArea.setText("〇".equals(q.seikai) ? "正解！" : "不正解！\n" + q.kaisetsu));
            b2.addActionListener(e -> kaisetsuArea.setText("✖".equals(q.seikai) ? "正解！" : "不正解！\n" + q.kaisetsu));
            btnPanel.add(b1); btnPanel.add(b2);
        }

        JButton prevBtn = new JButton("戻る"), nextBtn = new JButton("次へ");
        prevBtn.addActionListener(e -> { if(currentIndex > 0) { currentIndex--; refreshUI(); } });
        nextBtn.addActionListener(e -> { if(currentIndex < questionList.size()-1) { currentIndex++; refreshUI(); } });

        btnPanel.add(prevBtn); btnPanel.add(nextBtn);
        btnPanel.revalidate(); btnPanel.repaint();
    }

    public static List<Question> getAllQuestions() {
        List<Question> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT MONDAI, KAISETU, SEIKAI, type FROM unko");
            while (rs.next()) list.add(new Question(rs.getString("MONDAI"), rs.getString("KAISETU"), rs.getString("SEIKAI"), rs.getString("type")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    //-----------------------------------------------------------------------------------------

    // modeは "2" か "4"
    public static void saveToDBWithMode(String m, String k, String s, String mode) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            // type列もINSERTするように変更
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO unko (MONDAI, KAISETU, SEIKAI, type) VALUES (?, ?, ?, ?)");
            pstmt.setString(1, m);
            pstmt.setString(2, k);
            pstmt.setString(3, s);
            pstmt.setString(4, mode); // ここで2か4を保存
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    //-------------------------------------------------------------------

    public static int insertMondai(String m) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            // 問題だけINSERT
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO unko (MONDAI, KAISETU, SEIKAI) VALUES (?, '', '〇')", Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, m);
            pstmt.executeUpdate();

            // 最後に作成されたIDを取得
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void updateKaisetsu(int id, String k) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/Desktop/unko.db")) {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE unko SET KAISETU = ? WHERE id = ?");
            pstmt.setString(1, k);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}