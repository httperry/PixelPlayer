import java.util.regex.*;

public class Test {
    public static void main(String[] args) {
        String regex = "player\\\\?/([a-zA-Z0-9_-]+)\\\\?/";
        Pattern pattern = Pattern.compile(regex);
        String text = "player\\/8456c9de\\/www-widgetapi.vflset\\/www-widgetapi.js";
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            System.out.println("Match: " + matcher.group(1));
        } else {
            System.out.println("No match");
        }
    }
}
