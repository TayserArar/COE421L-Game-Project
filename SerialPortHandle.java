import jssc.SerialPort;
import jssc.SerialPortException;

public class SerialPortHandle {
    SerialPort sp;
    String path;

    public SerialPortHandle(String path) {
        this.sp = new SerialPort(path);
        this.path = path;
        try {
            sp.openPort();
            sp.setParams(9600, 8, 1, 0);
            // Safe flush
            try {
                 if(sp.getInputBufferBytesCount() > 0) {
                     sp.readBytes(); 
                 }
            } catch (Exception e) { }

        } catch (SerialPortException e) {
            e.printStackTrace();
        } 
    }

    // READ RAW BYTE (Fixes the hanging issue)
    public int readRawByte() {
        try {
            if (sp.getInputBufferBytesCount() > 0) {
                byte[] buffer = sp.readBytes(1);
                if (buffer != null && buffer.length > 0) {
                    return buffer[0] & 0xFF; 
                }
            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
        return -1; 
    }

    // WRITE RAW BYTE
    public void writeByte(byte b) {
        try {
            sp.writeByte(b);
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
    }
    
    // Only for debug strings if needed
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