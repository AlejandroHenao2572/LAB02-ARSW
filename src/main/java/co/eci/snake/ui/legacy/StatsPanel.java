// Archivo: src/main/java/co/eci/snake/ui/legacy/StatsPanel.java
package co.eci.snake.ui.legacy;

import co.eci.snake.core.*;
import javax.swing.*;
import java.awt.*;

public class StatsPanel extends JPanel {
    private final GameController controller;
    private JLabel longestLabel;
    private JLabel worstLabel;
    private JLabel stateLabel;

    public StatsPanel(GameController controller) {
        this.controller = controller;
        setLayout(new GridLayout(3, 1, 5, 5));
        setBackground(new Color(240, 240, 240));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        stateLabel = new JLabel("Estado: CORRIENDO");
        stateLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        longestLabel = new JLabel("Serpiente más larga: -");
        worstLabel = new JLabel("Peor serpiente: -");

        add(stateLabel);
        add(longestLabel);
        add(worstLabel);
    }

    public void updateStats() {
        GameState state = controller.getState();
        stateLabel.setText("Estado: " + state);
        
        if (state == GameState.PAUSED) {
            SnakeStats longest = controller.getLongestSnake();
            SnakeStats worst = controller.getWorstSnake();

            if (longest != null) {
                longestLabel.setText(String.format(
                    "🏆 Serpiente más larga: #%d (Longitud: %d, Ratones: %d)",
                    longest.snakeId(), longest.length(), longest.miceEaten()
                ));
            } else {
                longestLabel.setText("🏆 Serpiente más larga: Ninguna viva");
            }

            if (worst != null) {
                long survivalSeconds = worst.survivalTime() / 1000;
                worstLabel.setText(String.format(
                    "💀 Peor serpiente: #%d (Sobrevivió: %d segundos)",
                    worst.snakeId(), survivalSeconds
                ));
            } else {
                worstLabel.setText("💀 Peor serpiente: Ninguna muerta aún");
            }
        } else {
            longestLabel.setText("Serpiente más larga: (pausar para ver)");
            worstLabel.setText("Peor serpiente: (pausar para ver)");
        }
    }
}