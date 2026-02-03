package co.eci.pathfinder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Scanner;

public class Control extends Thread {
    
    private final static int NTHREADS = 3; //Cantidad de hilos a utilizar
    private final static int MAXVALUE = 300000000; //Valor maximo a evaluar
    private final static int TMILISECONDS = 5000; //Tiempo en milisegundos

    private final int NDATA = MAXVALUE / NTHREADS; //Cantidad de datos a evaluar por hilo

    private volatile boolean isPaused = false; //Variable para pausar la ejecucion de los hilos

    private AtomicInteger primesCount = new AtomicInteger(0); //Contador atomico para el numero de primos encontrados

    Scanner sc = new Scanner(System.in); //Scanner para leer la entrada por consola

    private PrimeFinderThread pft[];

    private Object lock = new Object(); //Objeto para sincronizacion de hilos
    
    private Control() {
        super();

        this.pft = new  PrimeFinderThread[NTHREADS]; //Arreglo de hilos

        int i;
        for(i = 0;i < NTHREADS - 1; i++) {
            PrimeFinderThread elem = new PrimeFinderThread(i*NDATA, (i+1)*NDATA, lock, primesCount, this); //Crear hilo e indicar rango de numeros a evaluar
            pft[i] = elem;
        }
        pft[i] = new PrimeFinderThread(i*NDATA, MAXVALUE + 1, lock, primesCount, this); //Crear hilo e indicar rango de numeros a evaluar
    }
    
    //Metodo factory estatico
    public static Control newControl() {
        return new Control();
    }

    @Override
    public void run() {
        //Poner en marcha los hilos
        for(int i = 0;i < NTHREADS;i++ ) {
            pft[i].start();
        }

        //Mientras haya hilos ejecutandose
        while (algunThreadVivo()) {

            try {
                Thread.sleep(TMILISECONDS); //Dormir el hilo por el tiempo indicado
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //Pausar la ejecucion de los hilos
            isPaused = true;
            System.out.println("Numero de primos encontrados hasta ahora: " + primesCount.get());
            System.out.println("Presione Enter para continuar...");
            sc.nextLine();

            //Reanudar la ejecucion de los hilos
            synchronized (lock) {
                isPaused = false;
                lock.notifyAll();
            }
        }
    }

    //Metodo para saber si hay hilos ejecuandose
    private boolean algunThreadVivo() {
        for(int i = 0; i < NTHREADS; i++) {
            if (pft[i].isAlive()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPaused() {
        return isPaused;
    }
    
}