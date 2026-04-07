package ms.wiwi.examarchive.model;

public record Professor(String professorID, String firstName, String lastName) {
    public String getFullName() {
        if(firstName.equals("Unbekannt")){
            return lastName;
        }
        return firstName + " " + lastName;
    }
}
