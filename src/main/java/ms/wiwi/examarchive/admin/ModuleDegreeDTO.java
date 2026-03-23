package ms.wiwi.examarchive.admin;

import ms.wiwi.examarchive.model.Degree;

import java.util.List;

public record ModuleDegreeDTO(String moduleID, List<Degree> degrees){
}
