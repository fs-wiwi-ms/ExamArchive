package ms.wiwi.examarchive.admin;

import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.Module;
import ms.wiwi.examarchive.model.Professor;
import ms.wiwi.examarchive.model.User;

public record AdminExamList(Module module, Exam exam, Professor professor, User user) {
}
