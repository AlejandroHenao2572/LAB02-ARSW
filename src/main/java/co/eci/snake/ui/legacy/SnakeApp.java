package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameController;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final GameClock clock;
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();
  private final StatsPanel statsPanel;
  private final GameController controller;
  private final java.util.concurrent.ExecutorService executor;
  private boolean gameStarted = false;

  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(i, x, y, dir));
    }

    this.gamePanel = new GamePanel(board, () -> snakes);
    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));
    this.controller = new GameController(snakes, clock); // NUEVO
    this.statsPanel = new StatsPanel(controller); // NUEVO
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.actionButton = new JButton("Iniciar");

    // Layout
    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(statsPanel, BorderLayout.NORTH); // NUEVO
    add(actionButton, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    actionButton.addActionListener((ActionEvent e) -> togglePause());

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    var player = snakes.get(0);
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"), "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"), "up");
    im.put(KeyStroke.getKeyStroke("DOWN"), "down");
    am.put("left", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.LEFT);
      }
    });
    am.put("right", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.RIGHT);
      }
    });
    am.put("up", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.UP);
      }
    });
    am.put("down", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.DOWN);
      }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.LEFT);
        }
      });
      am.put("p2-right", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.RIGHT);
        }
      });
      am.put("p2-up", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.UP);
        }
      });
      am.put("p2-down", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.DOWN);
        }
      });
    }

    setVisible(true);
  }

  private void togglePause() {
    if (!gameStarted) {
      // Iniciar el juego por primera vez
      gameStarted = true;
      actionButton.setText("Pausar");
      snakes.forEach(s -> executor.submit(new SnakeRunner(s, board, controller)));
      clock.start();
    } else if ("Pausar".equals(actionButton.getText())) {
      // Pausar el juego
      actionButton.setText("Reanudar");
      controller.pause();
      statsPanel.updateStats();
    } else {
      // Reanudar el juego
      actionButton.setText("Pausar");
      controller.resume();
      statsPanel.updateStats();
    }
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleports (flechas rojas)
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo (rayos)
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes
      var snakes = snakesSupplier.get();
      for (int idx = 0; idx < snakes.size(); idx++) {
        Snake s = snakes.get(idx);
        var body = s.snapshot().toArray(new Position[0]);
        Color base = getSnakeColor(idx);
        
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
          
          // Dibujar número en la cabeza
          if (i == 0 && body.length > 0) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            String number = String.valueOf(idx);
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(number);
            int textHeight = fm.getAscent();
            g2.drawString(number, 
                p.x() * cell + (cell - textWidth) / 2, 
                p.y() * cell + (cell + textHeight) / 2 - 2);
          }
        }
      }
      g2.dispose();
    }
    
    private Color getSnakeColor(int index) {
      // Paleta de colores distintos para hasta 20 serpientes
      Color[] colors = {
          new Color(0, 170, 0),      // Verde
          new Color(0, 160, 180),    // Cyan
          new Color(220, 50, 50),    // Rojo
          new Color(255, 165, 0),    // Naranja
          new Color(138, 43, 226),   // Violeta
          new Color(255, 20, 147),   // Rosa
          new Color(70, 130, 180),   // Azul acero
          new Color(34, 139, 34),    // Verde bosque
          new Color(218, 165, 32),   // Dorado
          new Color(148, 0, 211),    // Púrpura oscuro
          new Color(0, 128, 128),    // Verde azulado
          new Color(210, 105, 30),   // Chocolate
          new Color(100, 149, 237),  // Azul cielo
          new Color(154, 205, 50),   // Verde amarillento
          new Color(199, 21, 133),   // Magenta medio
          new Color(32, 178, 170),   // Verde mar claro
          new Color(255, 99, 71),    // Tomate
          new Color(106, 90, 205),   // Azul pizarra
          new Color(72, 209, 204),   // Turquesa medio
          new Color(255, 140, 0)     // Naranja oscuro
      };
      
      if (index < colors.length) {
        return colors[index];
      }
      
      // Para más de 20 serpientes, generar colores usando HSB
      float hue = (index * 137.508f) % 360 / 360f; // Golden angle para distribución uniforme
      return Color.getHSBColor(hue, 0.7f, 0.8f);
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
