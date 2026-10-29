// Imports
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import java.io.StringReader;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
 
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/*
How to use:

1-2-3-...-n = P
1-2-3-4 = 1:4
1-2-1-2 = 1-2-L

Ideas:
- Show the run length of a chosen song sequence √
- Build my own playback engine
- Audio visualizer (wavy bar thing) (later)
- Slowed reverb setting??

- Include the instructions


Inprovement
- Better UI?
- Better optomization?

*/


// class
public class FM{
    // set a permanent CD directory
    final public static String CD_DIRECTORY = "/Volumes";
    static final String RESET = "\033[0m";
    static final String PINK = "\033[95m";
    static final String PURPLE = "\u001B[35m";

    // Everything we need from the TOC, in one place
    static class TocInfo {
        int firstTrack;
        int lastTrack;
        int leadoutBlock;
        ArrayList<Integer> offsets = new ArrayList<Integer>();
    }

    // function to clear terminal screen
    static void cls(){
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    static void display_tracks(ArrayList<File> tracks){
        System.out.println("--- All Tracks ---");
        for (File f : tracks){
            System.out.println(f.getName());
        }
    }


    static void display_tracks(ArrayList<File> tracks, String title, int current_position){
        System.out.println("--- " + title + " ---");

        int index = 1;
        for (File f : tracks){
            if (index == current_position){
                System.out.println(PINK + "> " + f.getName() + RESET);
            } else {
                System.out.println(f.getName());
            }
            index++;
        }
    }

    // return a number of secconds into a propper string format
    static String format_time(double seconds) {
        int s = (int) Math.round(seconds);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;

        if (h > 0) {
            return String.format("%02d:%02d:%02ds", h, m, sec);
        }
        
        return String.format("%02d:%02ds", m, sec);
    }

    static void print_clock(long playlist_start, String total_str) {
        double elapsed = (System.nanoTime() - playlist_start) / 1_000_000_000.0;
        // floor so it behaves like a stopwatch (00:01 appears after a full second)
        System.out.print("\r" + "[" + format_time(Math.floor(elapsed)) + " / " + total_str + "] ");
        System.out.flush();
    }

    // all child "elements" of a node (skips whitespace text nodes)
    static List<Element> child_elements(Element parent) {
        List<Element> result = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                result.add((Element) n);
            }
        }
        return result;
    }

    // In a plist <dict>, a <key> is immediately followed by its value elemen
    static Element dict_value(Element dict, String key) {
        List<Element> kids = child_elements(dict);
        for (int i = 0; i < kids.size() - 1; i++) {
            Element e = kids.get(i);
            if (e.getTagName().equals("key") && e.getTextContent().trim().equals(key)) {
                return kids.get(i + 1);
            }
        }
        throw new IllegalArgumentException("Key not found in plist: " + key);
    }
 
    static int int_value(Element dict, String key) {
        return Integer.parseInt(dict_value(dict, key).getTextContent().trim());
    }

    // Java version of read_TOC_info 
    static TocInfo read_TOC_info(String cd_path) throws Exception {
        // find the file ending in ".plist"
        File plist = new File(cd_path, ".TOC.plist");
 
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        // Apple plists reference a DTD hosted on apple.com. Don't try to download it.
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document doc = builder.parse(plist);
 

        Element top_dict = child_elements(doc.getDocumentElement()).get(0);

        // the TOC calls each track "sessions"
        Element sessions = dict_value(top_dict, "Sessions");
        Element session = child_elements(sessions).get(0);   // data['Sessions'][0]
 
        TocInfo toc = new TocInfo();
        // record disk specific info
        toc.firstTrack = int_value(session, "First Track");
        toc.lastTrack = int_value(session, "Last Track");
        toc.leadoutBlock = int_value(session, "Leadout Block");
 
        for (Element track : child_elements(dict_value(session, "Track Array"))) {
            toc.offsets.add(int_value(track, "Start Block"));
        }
 
        return toc;
    }

    // Java version of compute_disk_ID 
    static String compute_disk_ID(String cd_path, TocInfo toc) throws Exception {
        // Build out hexadecimal representation
        StringBuilder hex = new StringBuilder();
        hex.append(String.format("%02X%02X%08X", toc.firstTrack, toc.lastTrack, toc.leadoutBlock));
 
        for (int i = 0; i < 99; i++) {
            int offset = (i < toc.offsets.size()) ? toc.offsets.get(i) : 0;
            hex.append(String.format("%08X", offset));
        }
 
        // Hash into 20-byte Binary data encoding
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
 
        // Turn the bytes into compact strings
        byte[] digest = sha1.digest(hex.toString().getBytes(StandardCharsets.US_ASCII));
        String b64 = Base64.getEncoder().encodeToString(digest);

        String disk_ID = b64.replace('+', '.').replace('/', '_').replace('=', '-');

        return disk_ID;
    }

    // retrieve and return an array to all the paths on the cd
    static ArrayList<File> get_tracklist(String cd_path){
        File cd_dir = new File(cd_path);
        File[] cd_files = cd_dir.listFiles();

        ArrayList<File> tracks = new ArrayList<File>();
        for (File f : cd_files){
            tracks.add(f);
        }

        // display_tracks(tracks);

        return tracks;
    }


    // get the true cd path (used for all futher operations)
    static String get_CD(Scanner scan){
        //retruns path of the CD to look into
        File dir = new File(CD_DIRECTORY);
        File[] files = dir.listFiles();

        System.out.println("--- Volumes to Brouse ---");

        int index = 0;
        for (File f : files){
            System.out.print(String.valueOf(index) + ". ");
            System.out.println(f.getName());

            index++;
        }

        // Get first user prompt
        System.out.print("> ");

        // Read user input
        String user_input = scan.nextLine();
        int volume = Integer.valueOf(user_input);
        // System.out.println("Looking in folder " + files[volume]);

        String cd_path = String.valueOf(files[volume]);
        return cd_path;
    }


    static ArrayList<File> build_playlist(ArrayList<File> tracklist, ArrayList<Integer> songs_to_play){
        ArrayList<File> playlist = new ArrayList<File>();

        for (int index : songs_to_play){
            playlist.add(tracklist.get(index));
        }

        return playlist;
    }


    // actually play the song by calling MacOS's afplay
    static int playsong(File songpath) {
        try {
            // start a new process and play song
            Process process = new ProcessBuilder("afplay", songpath.getAbsolutePath()).inheritIO().start();

            System.out.println("Currently Playing...");
            process.waitFor();

            return process.exitValue() == 0 ? 1 : 0;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }



    // playsong with clock
    static int playsong(File songpath, Engine engine, long playlist_start, String total_str) {
        try {


            // Engine engine = new Engine();
            // engine.load_header(songpath)

            System.out.println("about to start song");
            engine.start(songpath);

            // int ticks = 0;
            while (!engine.is_finished()) {
                print_clock(playlist_start, total_str);
                Thread.sleep(250);
            }



            System.out.println("song ended");
            


        

            // // piss();

            // waitFor(timeout) returns true once the process has exited,
            // false if the timeout expired first, so this loop ticks 4x per second
            // while (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
            //     print_clock(playlist_start, total_str);
            // }

            // print_clock(playlist_start, total_str);
            // return process.exitValue() == 0 ? 1 : 0;

            return 1;

        // } catch (InterruptedException e) {
        //     Thread.currentThread().interrupt();
        //     return 0;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    
    static int start_playlist(ArrayList<File> playlist, ArrayList<Integer> song_numbers, double total_time){
        int status = 1;
        int index = 1;

        // one stopwatch for the whole playlist
        String time_estimation = format_time(total_time);
        long playlist_start = System.nanoTime();

        // make engine class and load header. This should remain consistent for all songs on a CD
        Engine engine = new Engine();

        for (File song : playlist){
            cls();
            display_tracks(playlist, "Playlist", index);
            System.out.println();

            // System.out.println(song_numbers);
            String songname = String.valueOf(song.getName());
            System.out.println("Songname: " + songname);
            System.out.println("Starting Track: " + String.valueOf(index) + "/" + String.valueOf(playlist.size()));

            status = playsong(song, engine, playlist_start, time_estimation);
            index++;
        }

        engine.close();

        return status;
    }



    // return a list of song indexes the player wants to listen to
    static ArrayList<Integer> parse_input(String user_input, int tracklength) {
        // build new empty arraylist
        ArrayList<Integer> songs_to_play = new ArrayList<Integer>();

        // notable inputs
        if (user_input.equals("P")){
            for (int index = 1; index < tracklength; index++){
                songs_to_play.add(index);
            }
            return songs_to_play;
        }

        String code1 = "-";
        String[] broken_input = user_input.split(code1);

        // break input
        for (String s : broken_input) {
            if (s.contains(":")){
                // get interval
                // System.out.println(s);
                String code2 = ":";
                String[] interval = s.split(code2);

                String start = interval[0];
                String end = interval[1];

                int true_end = Integer.valueOf(end);
                int true_start = Integer.valueOf(start);

                if (true_start <= tracklength){
                    if (true_end >= tracklength){
                        true_end = tracklength - 1;
                    }

                    for (int index = true_start; index <= true_end; index++){
                        songs_to_play.add(index);
                    }

                }


            } else {
                try{
                    int song_number = Integer.valueOf(s);

                    if (song_number < tracklength){
                        songs_to_play.add(song_number);
                    }

                } catch (NumberFormatException e) {
                    if (s.startsWith("L")) {
                        int extra_loops = 1;
                        if (s.length() > 1) {
                            try {
                                extra_loops = Integer.parseInt(s.substring(1));
                            } catch (NumberFormatException e2) {
                                System.out.println("Bad loop count: " + s);
                                continue;
                            }
                        }
                        ArrayList<Integer> duplicate = new ArrayList<Integer>(songs_to_play);

                        for (int i = 0; i < extra_loops; i++) {
                            songs_to_play.addAll(duplicate);
                        }
                    }
                }
            }

        }

        return songs_to_play;
    }


    // CDs store audio in 1/75-second blocks, so length = block difference / 75
    static double track_seconds(TocInfo toc, int track_number) {
        int i = track_number - toc.firstTrack;
        int start = toc.offsets.get(i);
        int end = (i + 1 < toc.offsets.size()) ? toc.offsets.get(i + 1) : toc.leadoutBlock;
        return (end - start) / 75.0;
    }


    // calculates the total number of secconds a playlist takes to play
    static double total_seconds(TocInfo toc, ArrayList<Integer> track_numbers) {
        double total = 0;
        for (int t : track_numbers) {
            total += track_seconds(toc, t);
        }
        return total;
    }



    public static void handle_user_input(Scanner scan, ArrayList<File> tracks, String disk_ID, TocInfo toc){
        int tracklength = tracks.size();

        boolean exit_condition_met = false;

        while (!exit_condition_met) {
            display_tracks(tracks);

            System.out.println("\n--- Input ---");
            System.out.println("(P) = Play from top, (L) = Loop, (S) = Shuffle, (D) = Disk info");

            // Get song start point
            System.out.print("> ");

            String user_input = scan.nextLine();

            if (user_input.equals("D")){
                cls();
                System.out.println("Disk ID: " + disk_ID);

                System.out.print("> ");
                user_input = scan.nextLine();
                cls();
                continue;
            }

            // get the list of songs from the user
            ArrayList<Integer> songs_to_play = parse_input(user_input, tracklength);

            // using the list of song indexes, build playlist
            ArrayList<File> playlist = build_playlist(tracks, songs_to_play);


            System.out.println();
            display_tracks(playlist, "Playlist", 1);


            // get time estimation
            double total_time = total_seconds(toc, songs_to_play);
            String time_estimation = format_time(total_time);


            System.out.print("\nTotal time estimation: " + time_estimation);
            System.out.print("\nIs this ok? (yes, no): ");
            user_input = scan.nextLine();

            if (user_input.equals("yes")){
                exit_condition_met = true;

                // System.out.println(("coolio"));

                // Play the song
                int status = start_playlist(playlist, songs_to_play, total_time);

                if (status == 1){
                    System.out.println("Successfull Exit");
                }

            } else {
                cls();
            }

        }

    }



    public static void main(String[] args) throws Exception{
        // build scanner
        Scanner scan = new Scanner(System.in);

        cls();

        // get machine's path of CD
        String cd_path = get_CD(scan);

        // Extract unique disk data from TOC
        TocInfo toc = read_TOC_info(cd_path);

        // get the disk ID of the CD
        String disk_ID = compute_disk_ID(cd_path, toc);

        cls();

        System.out.println(cd_path);

        // get all tracks (full paths)
        ArrayList<File> tracks = get_tracklist(cd_path);

        // handle user's song requests
        handle_user_input(scan, tracks, disk_ID, toc);
    

        

        scan.close();

        return;
    }


    // static final String RESET = "\033[0m";

    // // 256-colour palette: index 0-255
    // static String color(int n) {
    //     return "\033[38;5;" + n + "m";
    // }

    // // swaps the ${c1}/${c2} placeholders for real colour codes
    // static String colorize(String line, String c1, String c2, String c3, String c4, String c5) {
    //     return line.replace("${c1}", c1).replace("${c2}", c2).replace("${c3}", c3).replace("${c4}", c4).replace("${c5}", c5);
    // }

    // static void piss(){
    //     String c1 = color(2);   // 
    //     String c2 = color(5);   //
    //     String c3 = color(2);   //
    //     String c4 = color(5);   //
    //     String c5 = color(13);   //

    //     // String[] art = {
    //     //     "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
    //     //     "${c2}⠀⠀⠀⠀⠀⠀⢀⣀⣀⣀⡀⢉⣉⠉⠉⠋⠐⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⠰⠒⠒⠂⠀⠄⠀⠀⠀⠀⠀⠀⠀",
    //     //     "⠀⠀⠀⠀⠀⣠⣾⣿⣿⡿⠿⠿⣶⣶⣥⣴⠢⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢊⡀⢤⣤⣤⣶⣶⣶⣖⠃⠀⠀⠀⠀⠀",
    //     //     "⠀⠀⠠⢴⣾⣿⡿⠋⠀⠀⠀${c1}⣴⣾⣿⣿⣿⣗⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣥⣾⣿⣟${c2}⠉⠉⠙⠻⣿⣿⣦⣀⠀",
    //     //     "⠀⠀⣠⣾⣿⠋⠀⠀⠀⠀${c1}⠠⣿⣜⢶⡗⣹⡯⠣⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠰⣟⢭⣛⢹⣧${c2}⠀⠀⠀⠀⠻⣿⣧⡀⠀⠀",
    //     //     "⠲⠿⠿⠿⣿⡀⠀⠀⠀⠀⠀${c1}⠻⠿⣷⡼⠟⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢿⣯⣫⣼⡏${c2}⠀⠀⠀⠀⠀⣹⣿⠿⠷⠦",
    //     //     "⠀⠀⠀⠀⠈⠉⠒⠀⠀⡀⢀⣠⠶⠖⠒⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠭⢭⢥⣄⠀⠀⠀⠀⠊⠁⠀⠀⠀⠀"

    //     // };

    //     String[] art = {

    //         "${c3}⠀⠀⣠⣿⣿⠿⠿⠿⠷⠶⣦⣤⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣠⣤⡶⠶⠿⠿⠿⢿⣿⣧⡀⠀⠀",
    //         "⠲⣿⡟⠉⠀⠀${c2}⢀⣤⣶⣶⣶⣍${c3}⠛⠇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠠⠟⢋${c2}⣴⣶⣶⣶⣄${c3}⠀⠀⠈⠙⣿⡷⠂",
    //         "⠀⢹⡇⠀⠀⠀${c2}⠈⠈⢰⣤⣾⡟⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⢽⣦⣴⠞⠙${c3}⠀⠀⠀⠀⣿⠃⠀",
    //         "⠀⠀⢷⠀⠀⠀⠀${c2}⠘⠂⠉⠁${c3}⠠⠇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠣⠂${c2}⠉⠁⠈${c3}⠀⠀⠀⢰⠃⠀⠀",
    //         "${c1}⠀⠀⠀⠀⠀⠀⠀⠀⠁⠀⢀⠀⢀⠔⢋⡠⢀⡀⠀⣀⠄⣀⠔⠀⠀⠀⠀⠀⠀⠀⠀⢀⡤⠀⠀⠀⠀⠀",
    //         "⠀⢀⠔⢀⡤⠀⡠⠒⣀⠝⡡⠚⢁⠔⢋⠔⠉⡠⠞⡡⠞⠁⡠⠚⠀⣀⠄⠉⢁⠤⠊⠁⡠⠔⠉⠀⠀⠀",
    //         "⠐⡡⠚⢁⠔⠉⡠⢊⠵⠊⡠⠖⡡⠚⢁⠤⢊⡠⠊⢀⠔⠊⢀⠴⠊⣠⠔⠊⢁⡤⠒⢉⠤⠂⠀⠀⠀⠀",
    //         "⠈⠀⠞⠁⡴⠊⠀⠁⠠⠊⠔⠋⢀⠔⢁⠔⠉⣠⠾⣁⠀⠐⠁⠒⠉⠀⠤⠚⠁⠀⠊⠁⠀⠀⠀⠀⠀⠀",
    //         "${c5}⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠁⠀⠁⢀⣌⣀⣀⣀⣡⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
    //         "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⡾⠿⢿⣿⡿⠿⢿⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
    //         "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠆⠀⠀⠀⠀⣂⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
    //         "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠸⢄⠀⠀⠀⠀⠀⢀⠇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
    //         "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀${c4}⠀⠃${c5}⠑⠀⠀⠀⠐⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
    //         "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀${c4}⠸⡄"
    //     };

    //     for (String line : art) {
    //         System.out.println(colorize(line, c1, c2, c3, c4, c5));
    //     }
    //     System.out.print(RESET);   // always reset, or your shell prompt stays coloured
    // }

}



