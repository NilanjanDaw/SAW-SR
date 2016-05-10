
import java.util.Arrays;
import java.util.Random;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Nilanjan
 */
public class Helper {
    
    private Random random;

    public Helper() {
        random = new Random();
    }
               
    /**
     * Simulate packet loss
     * @return if packet should be lost
     */
    public boolean lossPacket() {
        return random.nextDouble() < Constants.LOSS_RATE;
    }

    /**
     * Method has been deprecated, consider using the newer parseData(byte[]) function
     * @param data
     * @return 
     * @deprecated 
     */
    public String[] parseData(String data) {
        String[] info = new String[4];
        //RDT
        info[0] = data.substring(0, data.indexOf(" ")).trim();
        data = data.substring(data.indexOf(" ") + 1);
        //Sequence No
        info[1] = data.substring(0, data.indexOf(" ")).trim();        
        data = data.substring(data.indexOf(" ") + 1);
        String temp = data.substring(0, data.lastIndexOf("\n\r") - 1);
        temp = temp.substring(temp.length() - 3);
        if (temp.trim().equalsIgnoreCase("END")) {
            info[3] = temp;
            temp = data.substring(0, data.lastIndexOf("\n\r") - 1);
            info[2] = temp.substring(0, temp.lastIndexOf(" "));
            
        } else {
            info[3] = "!";
            info[2] = data.substring(0, data.lastIndexOf("\n\r") - 1);
        }
        return info;
    }
    
    /**
     * New method to handle parsing of incoming data packets.
     * Capable of parsing non-encoded pure binary data
     * @param dataByte
     * @return parsed data as 2D byte array
     */
    public byte[][] parseData(byte[] dataByte) {
        byte[][] infoByte = new byte[4][];
        byte space = " ".getBytes()[0];
        byte _n = "\n".getBytes()[0];
        byte _r = "\r".getBytes()[0];
        
        infoByte[0] = Arrays.copyOfRange(dataByte, 0, indexOf(dataByte, space));
        dataByte = Arrays.copyOfRange(dataByte, indexOf(dataByte, space) + 1, dataByte.length);
        
        infoByte[1] = Arrays.copyOfRange(dataByte, 0, indexOf(dataByte, space));
        dataByte = Arrays.copyOfRange(dataByte, indexOf(dataByte, space) + 1, dataByte.length);
        
        int index_n = lastIndexOf(dataByte, _n);
        int index_r = lastIndexOf(dataByte, _r);
        
        byte[] temp = Arrays.copyOfRange(dataByte, 0, index_n - 1);
        temp = Arrays.copyOfRange(temp, temp.length - 3, temp.length);
        if (new String(temp).equalsIgnoreCase("END")) {
            infoByte[3] = temp;
            temp = Arrays.copyOfRange(dataByte, 0, index_n - 1);
            infoByte[2] = Arrays.copyOfRange(temp, 0, lastIndexOf(temp, space));
        } else {
            infoByte[3] = "!".getBytes();
            infoByte[2] = Arrays.copyOfRange(dataByte, 0, index_n - 1);
        }
        return infoByte;
    }
    
    /**
     * Method to find the first occurrence of a byte element in a byte array
     * @param array
     * @param search
     * @return index of the search element if present else -1
     */
    public int indexOf(byte[] array, byte search) {
        int index = -1;
        for(int i = 0; i < array.length; i++)
            if (array[i] == search) {
                index = i;
                break;
            }
        return index;
    }
    
    /**
    * Method to find the last occurrence of a byte element in a byte array
    * @param array
    * @param search
    * @return index of the search element if present else -1
    */
    public int lastIndexOf(byte[] array, byte search) {
        int index = -1;
        for(int i = array.length - 1; i > 0; i--)
            if (array[i] == search) {
                index = i;
                break;
            }
        return index;
    }
}
