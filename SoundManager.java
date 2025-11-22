import javax.sound.sampled.*;
import java.io.File;

public class SoundManager {

    // Standard play (Background - doesn't pause game)
    public static void play(String fileName) {
        new Thread(() -> {
            try {
                File f = new File(fileName);
                if (!f.exists()) return;
                
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(f);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clip.start();
                
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
            } catch (Exception e) {
                System.err.println("[Audio] Error: " + e.getMessage());
            }
        }).start();
    }

    // New: Blocking play (Pauses game until sound finishes)
    public static void playBlocking(String fileName) {
        try {
            File f = new File(fileName);
            if (!f.exists()) {
                System.out.println("[Audio] Missing blocking file: " + fileName);
                return;
            }

            AudioInputStream audioIn = AudioSystem.getAudioInputStream(f);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();

            // Calculate duration in milliseconds
            long durationMs = clip.getMicrosecondLength() / 1000;
            
            // Sleep the thread for the exact duration of the sound
            Thread.sleep(durationMs + 50); // +50ms buffer to be safe

            clip.close();
            audioIn.close();
        } catch (Exception e) {
            System.err.println("[Audio] Error: " + e.getMessage());
        }
    }
}