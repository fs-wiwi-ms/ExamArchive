package ms.wiwi.examarchive.model;

public enum Semester {
    WINTER,
    SUMMER;

    public static String getReadableSemesterGER(Semester semester, int year) {
        switch (semester) {
            case SUMMER -> {
                return  "SoSe " + year;
            }
            case WINTER -> {
                return  "WiSe " + ("" + year).substring(2) + "/" + ("" + year + 1).substring(2);
            }

        }
        return "Unbekannt";
    }

    public static String getReadableSemesterENG(Semester semester, int year) {
        switch (semester) {
            case SUMMER -> {
                return  "Summer " + year;
            }
            case WINTER -> {
                return  "Winter  " + ("" + year).substring(2) + "/" + ("" + year + 1).substring(2);
            }
        }
        return "Unknown";
    }
}