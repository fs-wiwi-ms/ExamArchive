package ms.wiwi.examarchive.resource;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TranslationStringTest {

    private static final String LANGUAGE_FILE = "language.properties";
    private static final String LANG_DE_FILE = "translation/lang_de.properties";
    private static final String LANG_EN_FILE = "translation/lang_en.properties";

    /**
     * Every key defined in language.properties must also be present in both
     * lang_de.properties and lang_en.properties.
     */
    @Test
    void allLanguagePropertyKeysExistInTranslations() {
        Properties language = loadProperties(LANGUAGE_FILE);
        Properties en = loadProperties(LANG_EN_FILE);
        Properties de = loadProperties(LANG_DE_FILE);

        for (String key : language.stringPropertyNames()) {
            assertTrue(en.containsKey(key), "Missing key in lang_en: " + key);
            assertTrue(de.containsKey(key), "Missing key in lang_de: " + key);
        }
    }

    /**
     * lang_de.properties and lang_en.properties must not define any key that is
     * missing from language.properties.
     */
    @Test
    void translationsDoNotContainKeysMissingInLanguageProperties() {
        Properties language = loadProperties(LANGUAGE_FILE);
        Properties en = loadProperties(LANG_EN_FILE);
        Properties de = loadProperties(LANG_DE_FILE);

        Set<String> languageKeys = language.stringPropertyNames();

        assertTrue(languageKeys.containsAll(en.stringPropertyNames()),
                "lang_en contains keys missing in language.properties: " + difference(en.stringPropertyNames(), languageKeys));
        assertTrue(languageKeys.containsAll(de.stringPropertyNames()),
                "lang_de contains keys missing in language.properties: " + difference(de.stringPropertyNames(), languageKeys));
    }

    /**
     * No value in lang_de.properties or lang_en.properties may be the empty
     * string.
     */
    @Test
    void translationValuesAreNotEmpty() {
        assertNoEmptyValues(LANG_EN_FILE, "lang_en");
        assertNoEmptyValues(LANG_DE_FILE, "lang_de");
    }

    /**
     * No value in lang_de.properties or lang_en.properties may be blank, i.e.
     * empty or consisting only of whitespace.
     */
    @Test
    void translationValuesAreNotBlank() {
        assertNoBlankValues(LANG_EN_FILE, "lang_en");
        assertNoBlankValues(LANG_DE_FILE, "lang_de");
    }

    /**
     * Properties files silently keep the last value when a key is repeated, so a
     * duplicate key would hide a translation problem. None of the files may
     * contain duplicate keys.
     */
    @Test
    void translationFilesDoNotContainDuplicateKeys() {
        assertNoDuplicateKeys(LANGUAGE_FILE, "language.properties");
        assertNoDuplicateKeys(LANG_EN_FILE, "lang_en");
        assertNoDuplicateKeys(LANG_DE_FILE, "lang_de");
    }

    private static void assertNoEmptyValues(String resourcePath, String label) {
        Properties properties = loadProperties(resourcePath);
        for (String key : properties.stringPropertyNames()) {
            assertFalse(properties.getProperty(key).isEmpty(),
                    label + " contains an empty value for key: " + key);
        }
    }

    private static void assertNoBlankValues(String resourcePath, String label) {
        Properties properties = loadProperties(resourcePath);
        for (String key : properties.stringPropertyNames()) {
            assertFalse(properties.getProperty(key).isBlank(),
                    label + " contains a blank value for key: " + key);
        }
    }

    private static void assertNoDuplicateKeys(String resourcePath, String label) {
        Set<String> duplicates = duplicateKeys(resourcePath);
        assertTrue(duplicates.isEmpty(),
                label + " contains duplicate keys: " + duplicates);
    }

    private static Set<String> difference(Set<String> actual, Set<String> expected) {
        Set<String> diff = new HashSet<>(actual);
        diff.removeAll(expected);
        return diff;
    }

    private static Properties loadProperties(String resourcePath) {
        try (InputStream is = stream(resourcePath)) {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourcePath, e);
        }
    }

    private static Set<String> duplicateKeys(String resourcePath) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream(resourcePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue;
                }
                String key = keyOf(trimmed);
                if (!seen.add(key)) {
                    duplicates.add(key);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourcePath, e);
        }
        return duplicates;
    }

    /**
     * Extracts the key from a properties line, i.e. everything before the first
     * unescaped '=' or ':' separator.
     */
    private static String keyOf(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        int separator;
        if (equals == -1) {
            separator = colon;
        } else if (colon == -1) {
            separator = equals;
        } else {
            separator = Math.min(equals, colon);
        }
        String key = separator == -1 ? line : line.substring(0, separator);
        return key.trim();
    }

    private static InputStream stream(String resourcePath) {
        InputStream is = TranslationStringTest.class.getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Resource not found on classpath: " + resourcePath);
        return is;
    }
}
