import jssc.SerialPort;
import jssc.SerialPortException;

/**
 * Wrapper class for the JSSC (Java Simple Serial Connector) library.
 * Manages opening/closing the COM port and reading/writing raw bytes.
 */
public class SerialPortHandle {
    SerialPort sp;
    String path;

    public SerialPortHandle(String path) {
        this.sp = new SerialPort(path);
        this.path = path;
        try {
            sp.openPort();
            // Set Params: 9600 Baud, 8 Data bits, 1 Stop bit, No Parity
            sp.setParams(9600, 8, 1, 0);
            
            // Safe flush: Clear any old data sitting in the buffer on startup
            try {
                 if(sp.getInputBufferBytesCount() > 0) {
                     sp.readBytes(); 
                 }
            } catch (Exception e) { }

        } catch (SerialPortException e) {
            e.printStackTrace();
        } 
    }

    /**
     * Reads a single byte from the serial port.
     * This method is non-blocking for the most part, returning -1 if no data is available.
     * It handles the 0xFF masking to ensure correct integer values.
     */
    public int readRawByte() {
        try {
            if (sp.getInputBufferBytesCount() > 0) {
                byte[] buffer = sp.readBytes(1);
                if (buffer != null && buffer.length > 0) {
                    return buffer[0] & 0xFF; // Mask to unsigned int
                }
            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        return -1; // No data
    }

    /**
     * Sends a single byte to the serial port.
     */
    public void writeByte(byte b) {
        try {
            sp.writeByte(b);
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
    }
    
    // Helper to send strings (useful for debugging, mostly unused in protocol)
    public void printLine(String s) {
        try {
            sp.writeBytes(s.getBytes());
            sp.writeByte((byte) '\n');
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
    }
    
    public void close() {
        try {
            if(sp.isOpened()) sp.closePort();
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
    }
}