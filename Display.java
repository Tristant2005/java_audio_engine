import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Display {
    static final String RESET = "\033[0m";
    static final String PINK = "\033[95m";
    static final String PURPLE = "\u001B[35m";

    private static String art_path = "art/art2.txt";


    // // 256-colour palette: index 0-255
    static String colour(int n) {
        return "\033[38;5;" + n + "m";
    }

    public void set_path(String newpath){
        art_path = newpath;
    }
    

    // Counts every non-space character in the art, then lists the different ones
    // from MOST common to LEAST common. Most common = darkest colour, rarest = brightest.
    // (Code points are used instead of chars so emoji and other wide characters work too.)
    public static List<Integer> find_shades(List<String> art, Map<Integer, Integer> counts) {
        for (String line : art) {
            line.codePoints().forEach(cp -> {
                if (cp != ' ') {
                    counts.merge(cp, 1, Integer::sum);
                }
            });
        }
 
        List<Integer> shades = new ArrayList<>(counts.keySet());
        shades.sort((a, b) -> {
            int by_count = Integer.compare(counts.get(b), counts.get(a));   // most common first
            return by_count != 0 ? by_count : Integer.compare(a, b);        // ties: stable order
        });
        return shades;
    }

    // Prints the art stored in a text file, one line at a time.
    public static void draw() throws IOException {
        // read as UTF-8
        List<String> art = Files.readAllLines(Paths.get(art_path)); 

        for (String line : art) {
            System.out.println(line);
        }

        // always reset, or your shell prompt stays coloured
        System.out.print(RESET); 
    }


    // public static void main(String[] args)throws IOException {
    //     // boolean coloured = args.length > 0 && args[0].equals("colour");
    //     draw();
    // }
}




