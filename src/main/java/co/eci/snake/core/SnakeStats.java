package co.eci.snake.core;

public record SnakeStats(
    int snakeId,
    int length,
    long startTime,
    Long deathTime,  // null si está viva
    int miceEaten,
    Position currentHead
) {
    public boolean isAlive() {
        return deathTime == null;
    }
    
    public long survivalTime() {
        long endTime = deathTime != null ? deathTime : System.currentTimeMillis();
        return endTime - startTime;
    }
}