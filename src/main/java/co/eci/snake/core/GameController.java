// Archivo: src/main/java/co/eci/snake/core/GameController.java
package co.eci.snake.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import co.eci.snake.core.engine.GameClock;


public class GameController {
    private final List<Snake> snakes;
    private final GameClock clock;
    private final CountDownLatch pauseLatch;
    private final AtomicReference<GameState> state;
    
    // Estadísticas calculadas
    private volatile SnakeStats longestSnake;
    private volatile SnakeStats worstSnake;
    
    public GameController(List<Snake> snakes, GameClock clock) {
        this.snakes = snakes;
        this.clock = clock;
        this.pauseLatch = new CountDownLatch(0); // Inicialmente no esperando
        this.state = new AtomicReference<>(GameState.RUNNING);
    }
    
    // PASO 1: Iniciar pausa coordinada
    public void pause() {
        state.set(GameState.PAUSED);
        clock.pause();
        // Esperar a que todos los SnakeRunners lleguen a la barrera
        // PASO 2: Calcular estadísticas con estado consistente
        calculateStats();
    }
    
    public void resume() {
        state.set(GameState.RUNNING);
        clock.resume();
        // Liberar a todos los SnakeRunners esperando
    }
    
    private void calculateStats() {
        // Recolectar estadísticas de todas las serpientes
        List<SnakeStats> allStats = snakes.stream()
            .map(Snake::getStats)  // Thread-safe porque es synchronized
            .toList();
        
        // Encontrar la más larga (viva)
        longestSnake = allStats.stream()
            .filter(SnakeStats::isAlive)
            .max(Comparator.comparingInt(SnakeStats::length))
            .orElse(null);
        
        // Encontrar la peor (primera en morir)
        worstSnake = allStats.stream()
            .filter(s -> !s.isAlive())
            .min(Comparator.comparingLong(s -> s.deathTime()))
            .orElse(null);
    }
    
    public SnakeStats getLongestSnake() { return longestSnake; }
    public SnakeStats getWorstSnake() { return worstSnake; }
    public GameState getState() { return state.get(); }
}