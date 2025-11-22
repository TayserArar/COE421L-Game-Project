import javax.sound.sampled.*;
import java.io.File;

/**
 * Utility for playing WAV audio files.
 * Supports both asynchronous (non-blocking) and synchronous (blocking) playback modes.
 */
public class SoundManager {

    /**
     * Plays a sound asynchronously in a background thread.
     * Does not pause program execution.
     */
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

    /**
     * Plays a sound synchronously, blocking the calling thread until playback completes.
     * Used for critical timing events (e.g., Countdown).
     */
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

            long durationMs = clip.getMicrosecondLength() / 1000;
            
            Thread.sleep(durationMs + 50); 

            clip.close();
            audioIn.close();
        } catch (Exception e) {
            System.err.println("[Audio] Error: " + e.getMessage());
        }
    }
}