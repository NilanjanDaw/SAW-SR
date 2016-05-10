import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Server to serve file to client based on Stop And Wait Protocol
 * @author Nilanjan2
 */
public class FServerSAW {
    private int serverPort, clientPort;
    private InetAddress clientIP;
    public byte[] packet;
    private DatagramSocket socket;
    private String fileName;
    public int nextSequenceNo = 0, prevSequenceNo = -1;
    private boolean isLastPacket = false, isLastPacketAck = false;
    private Random random;
    private int timeoutCounter = 0;
    
    /**
     * Main looper function to serve file to client
     * @param args 
     */
    public void run(String... args) {
        random = new Random();
        try {
            serverPort = (args.length > 0)? Integer.parseInt(args[0]): 8080;
            socket = new DatagramSocket(serverPort);
            System.out.println("Time: " + System.currentTimeMillis() + "\t" 
                    + " Server is up on Port: " + serverPort);
            DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
            // Block until the host receives a UDP packet request.
            socket.receive(request);
            clientIP = request.getAddress();
            clientPort = request.getPort();
            fileName = getFileName(request.getData());
            socket.setSoTimeout(30);
            /**
             * Serving file if file name is valid
             */
            if (fileName != null && !fileName.equalsIgnoreCase("")) {
                while (true) {
                    packet = formPacket(nextSequenceNo);
                    if (packet == null)
                        return;
                    DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, clientIP, clientPort);
                    DatagramPacket receivePacket = new DatagramPacket(
                            new byte[Constants.PACKET_SIZE], 
                            Constants.PACKET_SIZE
                    );
                    /**
                     * sending packets
                     */
                    if (!lossPacket()) {
                        socket.send(sendPacket);
                        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                "Packet sequence_no=" + nextSequenceNo);
                    } else 
                        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                "Simulating Packet Loss at sequence_no=" + nextSequenceNo);
                    
                    try {
                        /**
                         * Receiving acknowledgements and handling errors accordingly
                         */
                        socket.receive(receivePacket);
                        nextSequenceNo = parseAcknowledge(receivePacket);
                        if (nextSequenceNo == -1) {
                            System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                    "Invalid Packet Format");
                            nextSequenceNo = prevSequenceNo + 1;
                        } else {
                            if ((prevSequenceNo + 2) == nextSequenceNo) {
                                prevSequenceNo++;
                                if (isLastPacket) {
                                   isLastPacketAck = true;
                                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                            "Last Packet Acknowledged");
                                }
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                "Acknowledgement LOSS sequence_no=" + nextSequenceNo);
                    }
                    if (isLastPacket)
                        timeoutCounter++;
                    
                    if (isLastPacket && isLastPacketAck || timeoutCounter == 5) {
                        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                                "File Send Confirmed");
                        break;
                    }
                }
            } else {
                System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                        "Invalid File Name");
            }
        } catch (SocketException ex) {
            Logger.getLogger(FServerSAW.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FServerSAW.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    public static void main(String[] args) {
        FServerSAW server = new FServerSAW();
        server.run(args);
    }
    
    /**
     * Method to parse File Request packet
     * @param requestPacket
     * @return file name requested
     */
    private String getFileName(byte[] requestPacket) {
        String packetData = new String(requestPacket);
        System.out.println(packetData);
        String file = null;
        String[] information = packetData.split(" ");
        for(String info: information)
            System.out.print(info + " ");
        System.out.println("");
        if(information[0].trim().equalsIgnoreCase("REQUEST"))
            file = information[1].trim();
        return file;
    }

    /**
     * Forms data packet according to the sequence number
     * @param nextSequence
     * @return byte array of the data packet formed
     * @throws IOException 
     */
    private byte[] formPacket(int nextSequence) throws IOException {
        byte[] data, dataByte;
        data = new byte[Constants.PACKET_SIZE];
        String prefix;
        prefix = "RDT " + nextSequence + " ";
        System.arraycopy(prefix.getBytes(), 0, data, 0, prefix.getBytes().length);
        int length = prefix.getBytes().length;
        dataByte = new byte[Constants.DATA_SIZE];
         try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(fileName))) {
            if(stream.available() > 0) {
                stream.skip(nextSequence * Constants.DATA_SIZE);
                if(stream.available() >= Constants.DATA_SIZE) {
                    stream.read(dataByte, 0, Constants.DATA_SIZE);
                    if (stream.available() == 0)
                        isLastPacket = true;
                } else if(stream.available() > 0) {
                    dataByte = new byte[stream.available()];
                    stream.read(dataByte, 0, stream.available());
                    isLastPacket = true;
                }
            }
            //TODO start from here no conversion//
            System.arraycopy(dataByte, 0,data, length, dataByte.length);
            length += dataByte.length;
            String suffix = "";
            if (isLastPacket)
                suffix += " END";
            suffix += " \n\r";
            System.arraycopy(suffix.getBytes(), 0, data, length, suffix.getBytes().length);
            length += suffix.getBytes().length;
            
        } catch(FileNotFoundException ex) {
             System.out.println("No such file in directory\n Shutting down server");
             return null;
        }
        return data;
    }

    /**
     * Method to parse the acknowledgement
     * @param receivePacket
     * @return sequence number acknowledged
     */
    private int parseAcknowledge(DatagramPacket receivePacket) {
        byte[] acknowlegeByte = receivePacket.getData();
        int ackSequence = -1;
        String data = new String(acknowlegeByte);
        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                "Acknowledgement received: " + data);
        try {
        String[] information = data.split(" ");
        if(information[0].trim().equalsIgnoreCase("ACK")) 
            ackSequence = Integer.parseInt(information[1].trim());
        } catch(Exception e) {
            System.out.println("Packet parse Exception: ");
            //e.printStackTrace();
        }
        return ackSequence;
    }

    /**
     * Simulate packet loss
     * @return if packet should be lost
     */
    private boolean lossPacket() {
        return random.nextDouble() < Constants.LOSS_RATE;
    }
}
 