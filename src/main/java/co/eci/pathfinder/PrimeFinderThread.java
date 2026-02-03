package co.eci.pathfinder;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PrimeFinderThread extends Thread{

	//Rango de numeros a evaluar
	int a,b;
	//Lista de numeros primos encontrados
	private List<Integer> primes;
    //Objeto para sincronizacion de hilos
    private Object lock;
    //Contador atomico para el numero de primos encontrados
    private AtomicInteger primesCount;
    //Referencia al controlador de los hilos
    private Control control;
	
	public PrimeFinderThread(int a, int b, Object lock, AtomicInteger primesCount, Control control) {
        super();
        this.primes = new LinkedList<>();
		this.a = a;
		this.b = b;
        this.lock = lock;
        this.primesCount = primesCount;
        this.control = control;
	}

    @Override
	public void run(){

        //Evaluar numeros en el rango indicado
        for (int i= a;i < b;i++){	
            
            //Verificar si esta pausado
            synchronized (lock){
                while(control.isPaused()){
                    try {
                        lock.wait(); //Esperar a que se reanude la ejecucion
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            //Si un numero es primo, agregarlo a la lista			
            if (isPrime(i)){
                primes.add(i);
                primesCount.incrementAndGet();
            }
        }
    }

    //Metodo para determinar si un numero es primo
	boolean isPrime(int n) {
	    boolean ans;
            if (n > 2) { 
                ans = n%2 != 0;
                for(int i = 3;ans && i*i <= n; i+=2 ) {
                    ans = n % i != 0;
                }
            } else {
                ans = n == 2;
            }
	    return ans;
	}

	public List<Integer> getPrimes() {
		return primes;
	}
	
}