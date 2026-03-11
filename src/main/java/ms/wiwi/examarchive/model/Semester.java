package ms.wiwi.examarchive.model;

public enum Semester {
    WINTER,
    SUMMER;

    public String getReadableSemesterGER(int year) {
        switch (this) {
            case SUMMER -> {
                return  "SoSe " + year;
            }
            case WINTER -> {
                return  "WiSe " + ("" + year).substring(2) + "/" + ("" + (year + 1)).substring(2);
            }

        }
        return "Unbekannt";
    }

    public String getReadableSemesterENG(int year) {
        switch (this) {
            case SUMMER -> {
                return  "Summer " + year;
            }
            case WINTER -> {
                return  "Winter  " + ("" + year).substring(2) + "/" + ("" + (year + 1)).substring(2);
            }
        }
        return "Unknown";
    }
}