package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Snake {
  private final Deque<Position> body = new ArrayDeque<>();
  private volatile Direction direction;
  private int maxLength = 5;
  private final int id;
  private final long startTime;
  private Long deathTime = null;
  private int miceEaten = 0;

  private Snake(int id, Position start, Direction dir) {
    this.id = id;
    this.startTime = System.currentTimeMillis();
    body.addFirst(start);
    this.direction = dir;
  }

  public static Snake of(int id, int x, int y, Direction dir) {
        return new Snake(id, new Position(x, y), dir);
  }

  public Direction direction() { return direction; }

  public synchronized void turn(Direction dir) {
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  public synchronized Position head() { return body.peekFirst(); }

  public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }

  public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();
  }

  // Método para registrar evento
  public synchronized void recordMouseEaten() {
        miceEaten++;
  }

  public synchronized void markDead() {
        if (deathTime == null) {
            deathTime = System.currentTimeMillis();
        }
  }

  //Crear snapshot thread-safe
  public synchronized SnakeStats getStats() {
        return new SnakeStats(
            id,
            body.size(),
            startTime,
            deathTime,
            miceEaten,
            head()
        );
    }

}
