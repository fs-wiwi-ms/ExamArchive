package ms.wiwi.examarchive;

import ms.wiwi.examarchive.model.Exam;
import ms.wiwi.examarchive.model.Professor;

public record ProfessorExamDTO(Exam exam, Professor professor) {
}
