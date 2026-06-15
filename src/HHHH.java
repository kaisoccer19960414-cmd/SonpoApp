import javax.swing.*;
import java.sql.*;

public class HHHH {
    public static void main(String[] args) {
        JFrame frame = new JFrame("最強の問題登録ツール");
        JTextField textField = new JTextField(20);
        JButton button = new JButton("DBに保存");

        frame.setLayout(new java.awt.FlowLayout());
        frame.add(textField);
        frame.add(button);
        frame.setSize(300, 150);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // ボタンを押した時の「命令」
        button.addActionListener(e -> {
            String input = textField.getText();
            saveToDB(input);
        });
    }

    // データベースに書き込むための部品
    public static void saveToDB(String text) {
        String url = "jdbc:sqlite:C:/Users/user/Desktop/unko.db";
        try (Connection conn = DriverManager.getConnection(url)) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS unko (Field1 TEXT)");
            stmt.executeUpdate("INSERT INTO unko (Field1) VALUES ('" + text + "')");
            System.out.println("保存完了: " + text);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}