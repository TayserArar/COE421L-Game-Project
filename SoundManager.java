import javax.sound.sampled.*;
import java.io.File;

/**
 * Utility class for playing .wav audio files.
 * Supports both asynchronous (non-blocking) and synchronous (blocking) playback.
 */
public class SoundManager {

    /**
     * Standard Play: Plays a sound in a new thread.
     * Does NOT pause the game execution. Used for UI sounds, steps, etc.
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
                
                // Add listener to release resources when sound finishes
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
     * Blocking Play: Pauses the calling thread until the sound finishes.
     * Used for critical game flow sounds like the "Go" countdown where
     * the game logic must wait for the audio to complete.
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