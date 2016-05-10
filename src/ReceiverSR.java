
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Thread to receive acknowledgements from the client
 * @author Nilanjan
 * @version 0.0.5
 */
public class ReceiverSR extends Thread {
    private final DatagramSocket socket;
    private final FServerSR server;

    /**
     * Constructor to initialise the member variables
     * @param socket
     * @param server 
     */
    public ReceiverSR(DatagramSocket socket, FServerSR server) {
        this.socket = socket;
        this.server = server;
    }
    
    /**
     * Main looper function to receive acknowledgements from the client
     */
    @Override
    public void run() {
        try {
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(
                        new byte[Constants.PACKET_SIZE],
                        Constants.PACKET_SIZE
                );
                socket.receive(receivePacket);
                /**
                 * Updating client IP and Port as per received acknowledgements
                 * Required because the UDP port of the client change mid-transmission
                 * causing the receiver and sender thread to block
                 */
                server.clientIP = receivePacket.getAddress();
                server.clientPort = receivePacket.getPort();
                int ackReceived = parseAcknowledge(receivePacket);
                server.packetList.remove(ackReceived); // On successful acknowledgement remove packet from error list
                Timer.flag[ackReceived] = false; // clearing flag for the packet
                if (FServerSR.isLastPacket) {
                    FServerSR.isLastPacketAck = true;
                }
            }      
        } catch (IOException ex) {
            Logger.getLogger(ReceiverSR.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Method to parse the data received from client as acknowledgement
     * @param receivePacket
     * @return The acknowledged sequence number
     */
    private int parseAcknowledge(DatagramPacket receivePacket) {
        byte[] acknowlegeByte = receivePacket.getData();
        int ackSequence = -1;
        String data = new String(acknowlegeByte);
        try {
        String[] information = data.split(" ");
        if(information[0].trim().equalsIgnoreCase("ACK")) 
            ackSequence = Integer.parseInt(information[1].trim());
        ackSequence--;
        ackSequence = (ackSequence >= 0)? ackSequence: Constants.WINDOW_SIZE + ackSequence;
        System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                "Acknowledgement received: " + ackSequence);
        } catch(Exception e) {
            System.out.println("Packet parse Exception: ");
            e.printStackTrace();
        }
        return ackSequence;
    }
}
