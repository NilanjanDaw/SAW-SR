
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
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Nilanjan2
 */
public class FClientSAW {
    
    private DatagramSocket socket;
    private InetAddress ip;
    private int port;
    private int nextSequenceNo = 0, prevSequenceNo = -1;
    private Random random;
    private boolean lastPacket = false;
    private String fileSavePath;
    private Helper helper;

    public FClientSAW() {
        random = new Random();
        helper = new Helper();
    }
     
    public void run(String... args) {
        try {
            socket = new DatagramSocket();
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
            //socket.setSoTimeout(1000);
            while (true) {
                DatagramPacket acknowledge;
                DatagramPacket packet = new DatagramPacket(new byte[Constants.PACKET_SIZE],
                        Constants.PACKET_SIZE);
                try {
                    socket.receive(packet);
                    acknowledge = parsePacket(packet);
                } catch (SocketTimeoutException e) {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "Timeout ");
                    String ackString = "ACK " + nextSequenceNo + " \n\r";
                    //System.out.println("Socket timeout ack=" + ackString);
                    byte[] ackPacket = ackString.getBytes();
            
                    acknowledge = new DatagramPacket(ackPacket,
                            ackPacket.length,
                            ip,
                            port
                    );
                }
                if (!helper.lossPacket()) {
                    socket.send(acknowledge);
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + 
                            "Sending Acknowledgement sequence_no=" + nextSequenceNo);
                } else {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + "Losing Acknowledge sequence_no=" + nextSequenceNo);
                }
                
                if (lastPacket) {
                    System.out.println("Time: " + System.currentTimeMillis() + "\t" + "File Received");
                    break;
                }
            }
        } catch (SocketException | UnknownHostException ex) {
            Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    public static void main(String[] args) {
        FClientSAW client = new FClientSAW();
        client.run(args);
    }

    private DatagramPacket getRequestPacket() {
        String requestFile = "REQUEST ", filePath;
        DatagramPacket packet;
        if (ip != null) {
            try {
                BufferedReader reader =  new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Enter file name: ");
                filePath = reader.readLine();
                fileSavePath = filePath.substring(0, filePath.indexOf(".")) + "_copy" 
                        + filePath.substring(filePath.indexOf("."));
                requestFile += filePath + " \n\r";
                byte[] data = requestFile.getBytes();
                packet = new DatagramPacket(data, data.length, ip, port);
                return packet;
            } catch (IOException ex) {
                Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    private DatagramPacket parsePacket(DatagramPacket packet) {
        byte[] dataPacket = packet.getData();
        String data = new String(dataPacket);
        /**
         * Porting code to handle data as byte array
         * Discarding String handling
         */
        byte[][] information = helper.parseData(dataPacket);
        System.out.println("Time: " + System.currentTimeMillis() + "\t" +
                "Received sequence_number=" + new String(information[1]));
        nextSequenceNo = Integer.parseInt(new String(information[1]).trim());
        if (prevSequenceNo + 1 == nextSequenceNo) {
            byte[] payload = information[2];
            if(new String(information[3]).trim().equalsIgnoreCase("END"))
                lastPacket = true;
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(fileSavePath, true))) {
                stream.write(payload, 0, payload.length);
                stream.close();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(FClientSAW.class.getName()).log(Level.SEVERE, null, ex);
            }
            prevSequenceNo = nextSequenceNo;
        } else {
            System.out.println("Time: " + System.currentTimeMillis() + "\t" +
                    "Packet mismatch " + prevSequenceNo + nextSequenceNo);
            nextSequenceNo = prevSequenceNo + 1;
        }
        String ackString = "ACK " + nextSequenceNo + " \n\r";
        byte[] ackPacket = ackString.getBytes();
            
        DatagramPacket acknowledge = new DatagramPacket(ackPacket,
                ackPacket.length,
                ip,
                port
        );
        return acknowledge;
    }
    
    
}
