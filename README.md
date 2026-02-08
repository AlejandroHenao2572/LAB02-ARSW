# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

- David Alejandro Patacon Henao

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

### Solución Implementada — Parte I

#### Diseño de Sincronización

##### **1. Monitor Compartido (Lock)**
```java
private Object lock = new Object();
```
- **Único objeto** de sincronización compartido entre `Control` y todos los `PrimeFinderThread`.
- Usado en todos los bloques `synchronized` y llamadas a `notifyAll()`.
- **Propósito**: Garantizar exclusión mutua y coordinar la pausa/reanudación de todos los hilos trabajadores.

##### **2. Variable de Estado (Condición de Pausa)**
```java
private volatile boolean isPaused = false;
```
- Bandera que indica si los hilos deben pausarse.
- Marcada como `volatile` para garantizar visibilidad entre hilos.
- **Control** la modifica; **PrimeFinderThread** la consulta.

##### **3. Contador Atómico de Primos**
```java
private AtomicInteger primesCount = new AtomicInteger(0);
```
- Contador thread-safe compartido entre todos los hilos.
- Se incrementa cada vez que se encuentra un primo usando `incrementAndGet()`.
- No requiere sincronización adicional gracias a la atomicidad intrínseca de `AtomicInteger`.

---

#### Flujo de Ejecución

##### **En Control.run():**

1. **Inicialización**: Inicia los 3 `PrimeFinderThread` con `start()`.

2. **Ciclo de pausas periódicas**:
   ```java
   while (algunThreadVivo()) {
       Thread.sleep(TMILISECONDS);           // Esperar 5000ms
       isPaused = true;                       // Señalar pausa
       System.out.println("Primos: " + primesCount.get());
       System.out.println("Presione Enter...");
       sc.nextLine();                         // Esperar input del usuario
       
       synchronized (lock) {
           isPaused = false;                  // Señalar reanudación
           lock.notifyAll();                  // Despertar a TODOS los hilos
       }
   }
   ```

##### **En PrimeFinderThread.run():**

Antes de procesar cada número, **verifica si debe pausarse**:
```java
for (int i = a; i < b; i++) {
    synchronized (lock) {
        while (control.isPaused()) {
            lock.wait();  // Se suspende aquí hasta notifyAll()
        }
    }
    
    if (isPrime(i)) {
        primes.add(i);
        primesCount.incrementAndGet();  // Thread-safe
    }
}
```

---

#### Mecanismos Clave

##### **1. Evitando Busy-Waiting**
**NO se usa** espera activa como `while(isPaused) {}`  
**SE usa** `wait()` que libera el lock y suspende el hilo hasta ser notificado

##### **2. Prevención de Lost Wakeups**
```java
while (control.isPaused()) {  // ← WHILE, no IF
    lock.wait();
}
```
- El **`while`** en lugar de `if` garantiza que al despertar se **re-evalúe la condición**.
- Protege contra:
  - **Spurious wakeups** (despertares falsos del sistema).
  - **Condiciones de carrera** donde `isPaused` podría cambiar entre el despertar y la verificación.

##### **3. Sincronización Correcta**
- **Mismo monitor**: Tanto `Control` como `PrimeFinderThread` usan el **mismo objeto `lock`**.
- **Atomicidad**: `notifyAll()` se llama dentro del bloque `synchronized` después de cambiar `isPaused`.
- **Orden correcto**:
  1. Control adquiere lock
  2. Control cambia `isPaused = false`
  3. Control llama `notifyAll()`
  4. Control libera lock
  5. Los trabajadores se despiertan, re-verifican condición, y continúan

##### **4. Broadcast vs. Signal**
- Se usa **`notifyAll()`** en lugar de `notify()` porque hay **múltiples hilos (3)** esperando.
- `notifyAll()` despierta a **todos** los hilos trabajadores simultáneamente.

---

#### Parámetros de Configuración

```java
private final static int NTHREADS = 3;          // Número de hilos trabajadores
private final static int MAXVALUE = 300000000;  // Rango máximo de búsqueda
private final static int TMILISECONDS = 5000;   // Intervalo de pausa (5s)
```

#### Ejecución

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="co.eci.pathfinder.Main"
```

**Salida esperada:**
```
Numero de primos encontrados hasta ahora: 12345
Presione Enter para continuar...
[usuario presiona ENTER]
Numero de primos encontrados hasta ahora: 45678
Presione Enter para continuar...
...
```

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

#### 1.1) Uso de hilos para autonomía de serpientes y estrategia de concurrencia

El diseño de SnakeRace implementa un modelo de **concurrencia basado en hilos independientes**, donde cada serpiente opera de forma autónoma en su propio hilo de ejecución. A continuación se analiza en detalle cómo cada clase contribuye a esta arquitectura concurrente:

##### **A. SnakeRunner — Autonomía mediante Runnable**

La clase `SnakeRunner` es el **núcleo de la autonomía** de cada serpiente. Implementa la interfaz `Runnable`, lo que permite que cada instancia se ejecute en un hilo separado.

**Características clave:**

1. **Bucle de ejecución independiente:**
   ```java
   public void run() {
       try {
           while (!Thread.currentThread().isInterrupted()) {
               maybeTurn();
               var res = board.step(snake);
               // Lógica de respuesta a colisiones y turbos
               Thread.sleep(sleep);
           }
       } catch (InterruptedException ie) {
           Thread.currentThread().interrupt();
       }
   }
   ```
   - Cada serpiente ejecuta su propio ciclo infinito **sin interferencia directa** de otras serpientes.
   - El hilo verifica `isInterrupted()` para detectar señales de terminación limpia.
   - `Thread.sleep()` controla la velocidad de movimiento (80ms base, 40ms en turbo).

2. **Decisiones autónomas:**
   - **`maybeTurn()`**: Genera giros aleatorios con probabilidad del 10% (5% en turbo).
   - **`randomTurn()`**: Selecciona una dirección aleatoria al chocar con obstáculos.
   - La IA de cada serpiente es completamente independiente y no coordina con otras.

3. **Gestión de estado local:**
   - `turboTicks`: Contador local que controla la duración del modo turbo (100 ticks).
   - No requiere sincronización porque es **privado al hilo**.

---

##### **B. Snake — Estado mutable con visibilidad garantizada**

La clase `Snake` representa el **estado de una serpiente individual** y es accedida concurrentemente por:
1. El hilo de `SnakeRunner` (movimientos autónomos).
2. El hilo de eventos de UI (control de jugador vía teclado).
3. El hilo de renderizado (lectura del estado para dibujado).

**Estrategia de concurrencia:**

1. **Dirección volátil:**
   ```java
   private volatile Direction direction;
   ```
   - **`volatile`** garantiza **visibilidad inmediata** entre hilos.
   - Cuando el jugador presiona una tecla (hilo UI), el cambio en `direction` es visible instantáneamente para el hilo `SnakeRunner`.

2. **Validación de giros:**
   ```java
   public void turn(Direction dir) {
       if ((direction == Direction.UP && dir == Direction.DOWN) ||
           (direction == Direction.DOWN && dir == Direction.UP) ||
           (direction == Direction.LEFT && dir == Direction.RIGHT) ||
           (direction == Direction.RIGHT && dir == Direction.LEFT)) {
           return; // Evitar giros de 180°
       }
       this.direction = dir;
   }
   ```
   - **No usa `synchronized`** porque `volatile` es suficiente para lecturas/escrituras atómicas de referencias.
   - El método `turn()` previene giros ilegales (180°).

3. **Cuerpo de la serpiente (no sincronizado):**
   ```java
   private final Deque<Position> body = new ArrayDeque<>();
   ```
   - **No está sincronizado internamente** porque solo se modifica desde el hilo `SnakeRunner` via `advance()`.
   - `snapshot()` retorna una **copia defensiva** para renderizado seguro:
     ```java
     public Deque<Position> snapshot() { 
         return new ArrayDeque<>(body); 
     }
     ```


##### **C. Board — Coordinación mediante Sincronización de Monitor**

El `Board` es el **recurso compartido** donde todas las serpientes interactúan. Gestiona:
- Posiciones de ratones, obstáculos, turbos y teleports.
- Detección de colisiones y eventos de juego.

**Estrategia de sincronización:**

1. **Método `step()` sincronizado:**
   ```java
   public synchronized MoveResult step(Snake snake) {
       // 1. Calcular siguiente posición
       Position next = new Position(head.x() + dir.dx, head.y() + dir.dy)
                           .wrap(width, height);
       
       // 2. Verificar colisiones con obstáculos
       if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;
       
       // 3. Procesar teleports
       if (teleports.containsKey(next)) {
           next = teleports.get(next);
       }
       
       // 4. Consumir ratones/turbos
       boolean ateMouse = mice.remove(next);
       boolean ateTurbo = turbo.remove(next);
       
       // 5. Avanzar serpiente
       snake.advance(next, ateMouse);
       
       // 6. Generar nuevos elementos
       if (ateMouse) {
           mice.add(randomEmpty());
           obstacles.add(randomEmpty());
       }
       
       return /* resultado */;
   }
   ```

2. **Monitor:**
   - **`synchronized`** en el método completo usa el lock intrínseco del objeto `Board`.
   - **Exclusión mutua**: Solo una serpiente puede ejecutar `step()` a la vez.
   - **Atomicidad**: Toda la secuencia (verificar colisión → modificar estado → generar elementos) es atómica.

3. **Getters sincronizados con copias defensivas:**
   ```java
   public synchronized Set<Position> mice() { 
       return new HashSet<>(mice); 
   }
   public synchronized Set<Position> obstacles() { 
       return new HashSet<>(obstacles); 
   }
   ```
   - Los hilos de renderizado obtienen **snapshots consistentes** del tablero.
   - Previene `ConcurrentModificationException` durante iteración.

4. **Colecciones internas (no thread-safe):**
   ```java
   private final Set<Position> mice = new HashSet<>();
   private final Map<Position, Position> teleports = new HashMap<>();
   ```
   - Usa colecciones **ordinarias** (no `ConcurrentHashMap`/`CopyOnWriteArraySet`).
   - La sincronización externa via `synchronized` es suficiente y más eficiente.


##### **D. GameClock — Control de ciclo de juego con estados atómicos**


`GameClock` gestiona el **ciclo de actualización global** del juego

**Estrategia de concurrencia:**

1. **Estado atómico:**
   ```java
   private final AtomicReference<GameState> state = 
       new AtomicReference<>(GameState.STOPPED);
   ```
   - **`AtomicReference`** permite cambios de estado **sin locks explícitos**.
   - Garantiza que lecturas/escrituras de `state` sean atómicas.

2. **Operación Compare-And-Set (CAS):**
   ```java
   public void start() {
       if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
           scheduler.scheduleAtFixedRate(/* ... */);
       }
   }
   ```
   - **CAS** previene múltiples inicios concurrentes (solo el primero tiene éxito).
   - Implementa el patrón **"check-then-act"** de forma thread-safe.

3. **ScheduledExecutorService:**
   ```java
   private final ScheduledExecutorService scheduler = 
       Executors.newSingleThreadScheduledExecutor();
   ```
   - Ejecuta el tick del juego a **intervalos regulares** (periodMillis).
   - Usa un **único hilo del executor** (no múltiples hilos para ticks).

4. **Control de pausa sin detener hilos:**
   ```java
   scheduler.scheduleAtFixedRate(() -> {
       if (state.get() == GameState.RUNNING) tick.run();
   }, 0, periodMillis, TimeUnit.MILLISECONDS);
   ```
   - El scheduler **siempre corre**, pero el tick solo se ejecuta si `state == RUNNING`.
   - `pause()` y `resume()` simplemente **modifican el estado**:
     ```java
     public void pause()  { state.set(GameState.PAUSED); }
     public void resume() { state.set(GameState.RUNNING); }
     ```

#### 1.2) Identificación de condiciones de carrera

A pesar de la estrategia de sincronización implementada, existen **condiciones de carrera potenciales y reales** en el código.


#### ** A. Operación compuesta check-then-act en Snake.turn()**

**Ubicación:** [Snake.java](src/main/java/co/eci/snake/core/Snake.java) líneas 21-30

**Código problemático:**
```java
public void turn(Direction dir) {
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
        return;  // ← CHECK: lee direction
    }
    this.direction = dir;  // ← ACT: escribe direction
}
```

**Escenario de race condition:**

1. **Hilo UI (teclado)** llama `turn(Direction.LEFT)` cuando `direction == Direction.UP`
2. **Hilo SnakeRunner** llama `turn(Direction.DOWN)` simultáneamente
3. **Posible secuencia intercalada:**

   ```
   T=0: UI lee direction (UP) → Pasa validación 
   T=1: SnakeRunner lee direction (UP) → Pasa validación
   T=2: UI escribe direction = LEFT
   T=3: SnakeRunner escribe direction = DOWN  ← SOBRESCRIBE
   ```

4. **Resultado:** La dirección `LEFT` del jugador se pierde, aplicándose `DOWN` en su lugar.

- **Impacto en UX:** El jugador presiona una tecla y la serpiente no responde o gira incorrectamente.
- **Frecuencia:** Baja en juego normal, pero aumenta con múltiples serpientes controladas manualmente.


##### **B. Acceso no sincronizado a Snake.body desde múltiples hilos**

**Ubicación:** [Snake.java](src/main/java/co/eci/snake/core/Snake.java)

**Código problemático:**
```java
private final Deque<Position> body = new ArrayDeque<>();  // NO thread-safe

// Método llamado desde SnakeRunner (hilo autónomo)
public void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);                        // ← ESCRITURA
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();  // ← MODIFICACIÓN
}

// Método llamado desde hilo de UI/renderizado
public Deque<Position> snapshot() { 
    return new ArrayDeque<>(body);  // ← LECTURA durante iteración
}
```

**Escenario de race condition:**

1. **Hilo SnakeRunner** ejecuta `advance()` → modifica `body` (addFirst, removeLast)
2. **Hilo de renderizado** ejecuta `snapshot()` → itera sobre `body` para copiar
3. **ArrayDeque NO es thread-safe** → estructura interna puede estar en estado inconsistente


