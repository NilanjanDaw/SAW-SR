
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * File Transfer client using Selective Repeat
 * @author Nilanjan
 * @version 0.0.9
 */
public class FClientSR {
    
    /**
     * Variable Section
     */
    private HashMap<Integer, byte[]> receivedPacketList;
    private List<Integer> expectedList;
    private DatagramSocket socket;
    private InetAddress ip;
    private int port;
    private int sequenceNo = 0;
    private boolean lastPacket = false;
    private String fileSavePath;
    private Helper helper;
    private Random random;
    /**
     * Constructor to initialise certain member variables
     */
    public FClientSR() {
        random = new Random();
        helper = new Helper();
        receivedPacketList = new HashMap<>();
        expectedList = new ArrayList<>();
        loadList(expectedList);
    }
    
    /**
     * Main loop function to receive data from server and
     * send back acknowledgements
     * @param args Takes the IP address and port number of the server
     */
    public void run(String... args) {
        try {
            socket = new DatagramSocket();
            /**
             * Connects to server with IP and port given as arguments 
             * defaults to localhost and port 8080 if nothing is given
             */
            if(args.length > 0) {
                ip = InetAddress.getByName(args[0]);
                port = Integer.parseInt(args[1]);
            } else {
                ip = InetAddress.getByName("127.0.0.1");
                port = Integer.parseInt("8080");
            }
            socket.setSoTimeout(30);
            System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                    "Client is connecting to " + ip + ": " + port);
            DatagramPacket requestPacket = getRequestPacket();
            socket.send(requestPacket);
            while (true) {
                DatagramPacket acknowledge;
                DatagramPacket packet = new DatagramPacket(
                        new byte[Constants.PACKET_SIZE],
                        Constants.PACKET_SIZE
                );
                try {
                    socket.receive(packet);
                    acknowledge = parsePacket(packet);
                    /**
                     * Sending acknowledgement back if allowed by lossPacket()
                     */
                    if (!lossPacket()) {
                        socket.send(acknowledge);
                        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                "Sending Acknowledgement sequence_no=" + sequenceNo);
                    } else {
                        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                "Losing Acknowledge sequence_no=" + sequenceNo);
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "Timeout sequence_no=" + sequenceNo);
                }
                /**
                 * If all packets in a window has been received or
                 * the last packet has been received, try to write to file
                 */
                if (expectedList.isEmpty() || lastPacket) {
                    writeToFile();
                    if (!lastPacket) {
                        loadList(expectedList);
                    } else {
                        expectedList.stream().forEach((i) -> {
                            System.out.print(" " + i);
                        });
                    }
                }
                /**
                 * If all data has been written along with last packet
                 * then client is shut down
                 */
                if (lastPacket && expectedList.isEmpty()) {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "File Received");
                    break;
                }
            }
        } catch (SocketException | UnknownHostException ex) {
            Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Method to get the initial data packet to request file from server
     * @return Initial data packet
     */
    private DatagramPacket getRequestPacket() {
        String requestFile = "REQUEST ", filePath;
        DatagramPacket packet;
        if (ip != null) {
            try {
                BufferedReader reader =  new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Enter file name: ");
                filePath = reader.readLine();
                try {
                fileSavePath = filePath.substring(0, filePath.indexOf(".")) + "_copy" 
                        + filePath.substring(filePath.indexOf("."));
                } catch(StringIndexOutOfBoundsException e) {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "Warning file has no extension");
                    fileSavePath = filePath + "_copy";
                }
                requestFile += filePath + "\n\r";
                byte[] data = requestFile.getBytes();
                packet = new DatagramPacket(data, data.length, ip, port);
                return packet;
            } catch (IOException ex) {
                Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
    
    /**
     * Method to parse the received data packet, form acknowledgement packet
     * and return it to calling function
     * @param packet
     * @return Acknowledgement packet
     */
    private DatagramPacket parsePacket(DatagramPacket packet) {
        byte[] dataPacket = packet.getData();
        /**
         * Code ported to use the new parseData(byte[]) function
         */
        byte[][] information = helper.parseData(dataPacket);
        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                "sequence_number=" + new String(information[1]));
        sequenceNo = Integer.parseInt(new String(information[1]));
        byte[] payload = information[2];
        if(new String(information[3]).trim().equalsIgnoreCase("END"))
            lastPacket = true;
        if (!receivedPacketList.containsKey(sequenceNo)) {
            receivedPacketList.put(sequenceNo, payload);
            expectedList.remove(Integer.valueOf(sequenceNo));
        }
        else
            System.out.println("Time: " + System.currentTimeMillis() + "\t" + "Packet repeat");
        sequenceNo = (sequenceNo + 1) % Constants.WINDOW_SIZE;
        String ackString = "ACK " + sequenceNo + " \n\r";
        byte[] ackPacket = ackString.getBytes();
        DatagramPacket acknowledge = new DatagramPacket(ackPacket,
                ackPacket.length,
                ip,
                port
        );
        return acknowledge;
    }
    
    
    
    /**
     * Writing all the packets all at once
     */
    private void writeToFile() {
        boolean flag = true;
        int expectedPacket = (lastPacket)? sequenceNo: Constants.WINDOW_SIZE;
        for (int i = 0; i < expectedPacket; i++) {
            if(!receivedPacketList.containsKey(i)) {
                    flag = false;
            }
        }
        if (flag) {
            System.out.println("Time: " + System.currentTimeMillis() + "\t" + "Write to file Possible");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(fileSavePath, true))) {
                for (int i = 0; i < receivedPacketList.size(); i++) {
                    //System.out.println(new String(receivedPacketList.get(i)));
                    stream.write(receivedPacketList.get(i));
                }
                receivedPacketList.clear();
                stream.close();
            } catch (FileNotFoundException ex) {
                
            } catch (IOException ex) {
                
            }
            receivedPacketList.clear();
            expectedList.clear();
        }
    }
    
    public static void main(String[] args) {
        FClientSR client = new FClientSR();
        client.run(args);
    }

    /**
     * Reloads the packet expectation list
     * @param expectedList 
     */
    private void loadList(List<Integer> expectedList) {
        expectedList.clear();
        for (int i = 0; i < Constants.WINDOW_SIZE; i++)
            expectedList.add(i);
    }
    
    /**
     * Test method: not currently used
     */
    private void printExpectedList() {
        expectedList.stream().forEach((Integer i) -> {
            System.out.print(i + " ");
        });
        System.out.println("");
    }
    
    /**
     * Simulate packet loss
     * @return if packet should be lost
     */
    public boolean lossPacket() {
        return false;
    }

}
