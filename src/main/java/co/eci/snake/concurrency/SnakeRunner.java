package co.eci.snake.concurrency;

import java.util.concurrent.ThreadLocalRandom;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameController;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Snake;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  private final GameController controller;

  public SnakeRunner(Snake snake, Board board, GameController controller) {
        this.snake = snake;
        this.board = board;
        this.controller = controller;
  }

  @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // PASO 1: Verificar si debe pausarse
                checkAndWaitIfPaused();
                
                // PASO 2: Lógica normal del juego
                maybeTurn();
                var res = board.step(snake);
                
                if (res == Board.MoveResult.HIT_OBSTACLE) {
                    snake.markDead(); // Registrar muerte
                    break; // Terminar el hilo
                } else if (res == Board.MoveResult.ATE_MOUSE) {
                    snake.recordMouseEaten(); // Registrar estadística
                } else if (res == Board.MoveResult.ATE_TURBO) {
                    turboTicks = 100;
                }
                
                int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
                if (turboTicks > 0) turboTicks--;
                Thread.sleep(sleep);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }

  private void checkAndWaitIfPaused() throws InterruptedException {
        // Si el estado es PAUSED, esperar aquí
        while (controller.getState() == GameState.PAUSED) {
            Thread.sleep(100); // Verificar cada 100ms
        }
  }
}
