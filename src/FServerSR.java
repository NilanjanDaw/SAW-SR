
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Main Server Thread to serve files to clients using Selective Repeat Protocol
 * @author Nilanjan
 * @version 0.0.9
 */
public class FServerSR extends Thread {
    /**
     * Member Variable Section
     */
    int serverPort, clientPort;
    InetAddress clientIP;
    public byte[] packet;
    private DatagramSocket socket;
    private String fileName, args[];
    public int sequenceNo = 0, segmentNo = 0;
    public static boolean isLastPacket = false, isLastPacketAck = false;
    private Random random;
    private Thread receiver;
    public static int mutexHashMap = 0;
    
    /**
     * ConcurrentHashMap to hold the window packets sent to the client
     * Concurrent to allow multiple thread access to the common resource without deadlock
     */
    public ConcurrentHashMap<Integer, DatagramPacket> packetList;
    public final Timer timer;
    
    /**
     * Constructor to initialise certain member functions
     * and set the timer as a Daemon thread
     */
    public FServerSR() {
        packetList = new ConcurrentHashMap<>();
        timer = new Timer(Constants.TIMEOUT);
        timer.setDaemon(true);
    }
    
    /**
     * Main looper function to transmit data packet to the client
     */
    @Override
    public void run() {
        random = new Random();
        try {
            
            /**
             * Server port binding to the port number given as arguments
             * Defaults to server port 8080 if nothing is given
             */
            serverPort = (args.length > 0)? Integer.parseInt(args[0]): 8080;
            socket = new DatagramSocket(serverPort);
            System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                    "Server is up on Port: " + serverPort);
            DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
            // Block until the host receives a UDP file request.
            socket.receive(request);
            clientIP = request.getAddress();
            clientPort = request.getPort();
            fileName = getFileName(request.getData());
            receiver = new ReceiverSR(socket, this);
            /**
             * Starting the acknowledgement receiver and timer threads
             */
            receiver.start();
            timer.start();
            while(true) {
                
                /**
                 * Checking if error occurred in the last window
                 * before starting new window transmission
                 */
                if (sequenceNo == 0 && !packetList.isEmpty()) {
                    handleError();
                }
                
                /**
                 * forming packet to be sent to the client
                 */
                packet = formPacket(sequenceNo, segmentNo);
                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, clientIP, clientPort);
                packetList.put(sequenceNo, sendPacket);
                
                /**
                 * Synchronising packet at the start of the window
                 * Considering using it inside a synchronised block in future implementations
                 * to prevent timer inconsistencies.
                 */
                if (sequenceNo == 0) {
                    timer.updateStartTime(System.currentTimeMillis());
                }
                
                /**
                 * Sending packet to client if allowed by lossPacket() method
                 */
                if (!lossPacket()) {
                    socket.send(sendPacket);
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                    "Sending Packet sequence_no=" + sequenceNo);
                } else 
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "Simulating Packet Loss at sequence_no=" + sequenceNo);
                
                /**
                 * Waiting for last window packet acknowledgement to be received
                 */
                if (sequenceNo == Constants.WINDOW_SIZE - 1) {
                    Thread.sleep(Constants.TIMEOUT / 3);
                }
                
                sequenceNo = (sequenceNo + 1) % Constants.WINDOW_SIZE;
                segmentNo++;
                
                /**
                 * if last packet has been sent, handle any errors made in last window
                 * then shut down server
                 */
                if (isLastPacket) {
                    handleError();
                    break;
                }
                if (isLastPacket && isLastPacketAck) {
                    break;
                }
            }
        } catch (SocketException ex) {
            Logger.getLogger(FServerSAW.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FServerSAW.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(FServerSR.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Parsing file request from client
     * @param requestPacket
     * @return file name
     */
    private String getFileName(byte[] requestPacket) {
        String packetData = new String(requestPacket);
        System.out.println(packetData);
        String file = null;
        String[] information = packetData.split(" ");
        if(information[0].trim().equalsIgnoreCase("REQUEST"))
            file = information[1].trim();
        return file;
    }

    /**
     * Method to form data packet, reading data from the file
     * @param nextSequence
     * @param segmentNo
     * @return data packet formed is returned as byte array, null on any error
     * @throws IOException 
     */
    private byte[] formPacket(int nextSequence, int segmentNo) throws IOException {
        byte[] data, dataByte;
        data = new byte[Constants.PACKET_SIZE];
        String prefix;
        prefix = "RDT " + nextSequence + " ";
        /**
         * Converting prefix into a byte[] and copying it to the packet
         */
        System.arraycopy(prefix.getBytes(), 0, data, 0, prefix.getBytes().length);
        int length = prefix.getBytes().length;
        dataByte = new byte[Constants.DATA_SIZE];
        /**
         * Reading data from file
         */
         try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(fileName))) {
            if(stream.available() > 0) {
                stream.skip(segmentNo * Constants.DATA_SIZE);
                if(stream.available() >= Constants.DATA_SIZE) {
                    stream.read(dataByte, 0, Constants.DATA_SIZE);
                    if (stream.available() == 0)
                        isLastPacket = true;
                } else if(stream.available() > 0) {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "Last packet Sent");
                    dataByte = new byte[stream.available()];
                    stream.read(dataByte, 0, stream.available());
                    isLastPacket = true;
                }
            }
            System.arraycopy(dataByte, 0,data, length, dataByte.length);
            length += dataByte.length;
            String suffix = "";
            if (isLastPacket)
                suffix += " END";
            suffix += " \n\r";
            System.arraycopy(suffix.getBytes(), 0, data, length, suffix.getBytes().length);
            length += suffix.getBytes().length;
            
            
        }
        return data; // final packet formed is returned 
    }

    /**
     * Simulate packet loss
     * @return if packet should be lost
     */
    private boolean lossPacket() {
        return random.nextDouble() < Constants.LOSS_RATE;
    }

    /**
     * Handling error according to Selective Repeat Logic
     */
    private void handleError() {
        int prevSequence = sequenceNo;
        System.out.print("Time: " + System.currentTimeMillis() + "\t" + 
                "Handle error for packets: ");
        /**
         * Creating packet error list
         */
        List<Integer> errorList = new ArrayList<>();
        packetList.keySet().stream().forEach((key) -> {
            System.out.print(key + "  ");
            errorList.add(key);
        });
        System.out.println("");
        try {
            /**
             * Sending error packets one by one
             */
            for (Integer error: errorList) {
                while (packetList.containsKey(error)) {
                    socket.send(packetList.get(error));
                    /**
                     * Sleep to allow receiver to run, preventing receiver
                     * thread deadlock
                     */
                    Thread.sleep(1); 
                }
            }
        } catch (IOException | InterruptedException e) {
            
        }
        /**
         * Clearing error packet list (not needed just as a safeguard)
         */
        packetList.clear();
        sequenceNo = prevSequence; // restoring sequence number to the its pre-method call state
        System.out.println("Time: " + System.currentTimeMillis() + "\t" + "Sequence No restored to: " + sequenceNo);
        System.out.println("Leaving error handler");
    }
    
    /**
     * Main method to start the server
     * @param args 
     */
    public static void main(String[] args) {
        FServerSR server = new FServerSR();
        server.args = args;
        server.start();
    }
}
