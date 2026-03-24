package ms.wiwi.examarchive.services;

import gg.jte.Content;
import gg.jte.support.LocalizationSupport;
import java.util.Locale;
import java.util.ResourceBundle;

public class JteLocalizer {

    private static final ThreadLocal<LocalizationSupport> localizer = new ThreadLocal<>();

    public static void setLocale(Locale locale) {
        localizer.set(new LocalizationSupport() {
            private final ResourceBundle bundle = ResourceBundle.getBundle("translation.lang", locale);

            @Override
            public String lookup(String key) {
                if (bundle.containsKey(key)) {
                    return bundle.getString(key);
                }
                return "!" + key + "!";
            }
        });
    }

    public static Content get(String key) {
        return localizer.get().localize(key);
    }

    public static String lookup(String key){
        return localizer.get().lookup(key);
    }

    public static Content get(String key, Object... params) {
        return localizer.get().localize(key, params);
    }

    public static void clear() {
        localizer.remove();
    }
}