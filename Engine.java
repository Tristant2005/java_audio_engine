// java Engine {file.wav}

// /Volumes/The\ Dark\ Side\ Of\ The\ Moon/Eclipse.aiff

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.*;
import java.io.RandomAccessFile;


// Audio engine to read aiff files
public class Engine {
    // class variables
    private File file;
    private long data_offset;
    private long data_length;

    // The audio format is the 
    private AudioFormat audio_format;
    private volatile SourceDataLine line;
    private volatile boolean line_exist; 

    /*
    The audio_format describes how to read the raw bytes of a track. 
        -> audio_format = new AudioFormat(frequency, bit_rate, num_channels, signed_numbers, big_endian);
            * frequency: frequency of music (for CDs always 44,100 samples per seccond)
            * bits: bit rate of song. eg. 16 bits per sample
            * channels: number of audio channels
            * signed_numbers: boolean 
            * big_endian: boolean
    
    The audio_line is the connection to the sound card, the stream in which the audio reaches the user.
        -> audio_line = new AudioSystem.getSourceDataLine(format);
            * format: the audio_format for the song

    */


    // only used if loading entire file first (deprecated)
    // private byte[] file_bytes;
    // private int data_offset;
    // private int data_length;

    // states (a volatile variable is shared between threads)
    private volatile boolean stop_requested = false;
    private volatile boolean finished = false;
    // private volatile double level = 0;   
 
    private Thread worker;

    public boolean is_finished(){
        return finished;
    }

    public void set_file(File songpath){
        file = songpath;
    }

    public void close(){
        line.drain();
        line.close();
    }

    // for a file, read its header info to get format information
    public void load_header(File f) throws IOException {
        file = f;
        byte[] head = new byte[12];

        try (RandomAccessFile raf = new RandomAccessFile(f, "r");) {
            raf.readFully(head);
            if (!new String(head, 0, 4).equals("FORM")) {
                throw new IOException("Not an AIFF file: " + f);
            }

            // file_type is either "AIFF" or "AIFC"
            String file_type = new String(head, 8, 4);

            // aiff specific filestructure
            int channels = 2;
            int bits = 16;
            boolean big_endian = true;
            long file_length = raf.length();
            long pos = 12; // must be long for raf.seek()

            // move through file and load chunck
            while (pos + 8 <= file_length) {
                raf.seek(pos);

                byte[] chunk_head = new byte[8];
                raf.readFully(chunk_head);

                // id is either SSND or COMM
                String id = new String(chunk_head, 0, 4);
                int size = ByteBuffer.wrap(chunk_head, 4, 4).getInt();   // AIFF numbers are big-endian
                long start = pos + 8;

                // predictable where the audio chunk is
                if (id.equals("SSND")) {
                    byte[] ssnd = new byte[8];
                    raf.readFully(ssnd);

                    int ssnd_offset = ByteBuffer.wrap(ssnd).getInt(0);
                    data_offset = start + 8 + ssnd_offset;
                    data_length = size - 8 - ssnd_offset;

                    // found the audio, stop scanning
                    break; 

                } else if (id.equals("COMM")) {
                    byte[] comm = new byte[Math.min(size, 64)];
                    raf.readFully(comm);
                    ByteBuffer cb = ByteBuffer.wrap(comm);
                    channels = cb.getShort(0) & 0xFFFF;
                    bits = cb.getShort(6) & 0xFFFF;

                    // 'sowt' = same PCM samples, but little-endian
                    if (file_type.equals("AIFC") && comm.length >= 22) {
                        big_endian = !new String(comm, 18, 4).equalsIgnoreCase("sowt");
                    }
                }

                // chunks are padded to eaven sizes
                pos = start + size + (size % 2);

            }

            // never run past the end of the file, and only use whole frames
            data_length = Math.min(data_length, file_length - data_offset);
            int frame_size = channels * (bits / 8);
            data_length -= data_length % frame_size;
    
            // Very importat. The format is dependant on the CD
            audio_format = new AudioFormat(44100f, bits, channels, true, big_endian); //CD audio is always 44100 Hz


            System.out.println("load header complete");
        }
    }
    
    
    // done by worker thread
    private void play_music_thread() {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {

        // build and start audio stream line
        if (!line_exist){
            line = AudioSystem.getSourceDataLine(audio_format);
            line.open(audio_format);
            line.start();
            line_exist = true;
        }

        // skip headder
        raf.seek(data_offset);

        // 4096 is roughtly ~93 ms of audio
        int audio_chunk_size = 4096;
        byte[] chunk = new byte[audio_format.getFrameSize() * audio_chunk_size]; 
        long bytes_to_read = data_length;

        // while remaining chunks exist
        while (bytes_to_read > 0 && !stop_requested) {
            // only read n chunks in memory (n is always an integer)
            int curr_chunk = (int) Math.min(chunk.length, bytes_to_read);  
            raf.readFully(chunk, 0, curr_chunk);

            line.write(chunk, 0, curr_chunk);

            // remove played chunks from total
            bytes_to_read -= curr_chunk;
        }

        // let the last buffered audio finish
        // if (!stop_requested) {
        //     line.drain();  
        // }

        // // close the audio line
        // line.close();

        } catch (Exception e){
            // for any error, print the stack trace
            if (!stop_requested){
                e.printStackTrace();
            }

        } finally {
            // end the task
            finished = true;
        }
    }


    public void start(File f) throws Exception{
        // load header info from file to setup format
        load_header(f);

        // set class variables
        stop_requested = false;
        finished = false;

        // check line
        // if the current line's set format isn't equal to new incomming audio_format
        if (line_exist && !line.getFormat().matches(audio_format)){
            line.drain();
            line.close();
            line_exist = false;
            // System.out.println("song did not match format. New line being created.");
        } 
        
        // start thread
        worker = new Thread(this::play_music_thread);
        worker.start();
    }


    // public static void main(String[] args) throws IOException{

    //     // for now load in a file inside argument
    //     File f = new File(args[0]);

    //     Engine player = new Engine();
    //     player.load(f);

    //     System.out.println("File successfully loaded");


    // }
}




  // // fully loads file (deprecated)
    // public void load_full(File f) throws IOException{
    //     System.out.println("inside Engine.load()");

    //     // read bytes from file
    //     file_bytes = Files.readAllBytes(f.toPath());
    //     ByteBuffer buf = ByteBuffer.wrap(file_bytes).order(ByteOrder.BIG_ENDIAN);
 

    //     // Check "FORM"
    //     // Check if AIFF file, maybe later if we need to implement other filetypes
    //     String first_bytes = new String(file_bytes, 0, 4);
    //     if (!first_bytes.equals("FORM")) {
    //         throw new IOException("Not an AIFF file: " + f);
    //     }

    //     // file_type is either "AIFF" or "AIFC"
    //     String file_type = new String(file_bytes, 8, 4);

    //     // aiff specific filestructure
    //     int channels = 2;
    //     int bits = 16;
    //     boolean big_endian = true;
    //     int pos = 12;

    //     System.out.println("file type: " + file_type);

    //     // move through file and find audio chunk
    //     while (pos + 8 <= file_bytes.length) {
    //         // id is either SSND or COMM
    //         String id = new String(file_bytes, pos, 4);  
    //         int size = buf.getInt(pos + 4);
    //         int start = pos + 8;
 
    //         // predictable where the audio chunk is
    //         if (id.equals("SSND")) {
    //             int ssnd_offset = buf.getInt(start);
    //             data_offset = start + 8 + ssnd_offset;
    //             data_length = size - 8 - ssnd_offset;

    //             // found the audio, stop scanning
    //             break;  

    //         } else if (id.equals("COMM")) {
    //             channels = buf.getShort(start) & 0xFFFF;
    //             bits = buf.getShort(start + 6) & 0xFFFF;

    //             // 'sowt' = same PCM samples, but little-endian
    //             if (file_type.equals("AIFC") && size > 18) {
    //                 big_endian = !new String(file_bytes, start + 18, 4).equalsIgnoreCase("sowt");
    //             }
    //         }

    //         // chunks are padded to even sizes
    //         pos = start + size + (size % 2);
    //     }

    //     // never run past the end of the file, and only use whole frames
    //     data_length = Math.min(data_length, file_bytes.length - data_offset);
    //     int frame_size = channels * (bits / 8);
    //     data_length -= data_length % frame_size;
 
    //     // CD audio is always 44100 Hz
    //     audio_format = new AudioFormat(44100f, bits, channels, true, big_endian);


    //     System.out.println("load complete.");

    // }