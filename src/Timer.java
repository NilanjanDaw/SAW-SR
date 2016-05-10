/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Nilanjan
 * @version 1.0.2
 */
public class Timer extends Thread {
    /**
     * Variable Section
     */
    private static double startTime;
    private double currentTime;
    public double timeoutPeriod;
    /**
     * flag to hold the different window packet timeouts
     */
    public static boolean[] flag;
    /**
     * Constructor to initialise the member variables
     * @param timeoutPeriod 
     */
    public Timer(double timeoutPeriod) {
        startTime = currentTime = 0;
        this.timeoutPeriod = timeoutPeriod;
        flag = new boolean[Constants.WINDOW_SIZE];
    }
    /**
     * Main Thread Looper to check for packet timeouts
     */
    @Override
    public void run() {
        startTime = System.currentTimeMillis();
        do {
            if (startTime != 0) {
                currentTime = System.currentTimeMillis();
                double difference = currentTime - startTime;
                for (int i = 1; i <= Constants.WINDOW_SIZE; i++) {
                    if (difference % (timeoutPeriod * i) == 0) {
                        //alert timeout for packet i
                        flag[i - 1] = true;
                    }
                }
            }
        } while(true);   
    }
    
    /**
     * Method to update the start time
     * Restart the timer and flush the previous window timeouts
     * @param updateTime 
     */
    public void updateStartTime(double updateTime) {
        System.out.println("Timer start time updated to: " + updateTime);
        startTime = updateTime;
        for (int i = 0; i < flag.length; i++)
            flag[i] = false;
    }
}
