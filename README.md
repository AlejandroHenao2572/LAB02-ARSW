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

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
- **Identifica** y documenta en **`el reporte de laboratorio`**:
  - Posibles **condiciones de carrera**.
  - **Colecciones** o estructuras **no seguras** en contexto concurrente.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

---

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**
