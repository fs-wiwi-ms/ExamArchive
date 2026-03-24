package ms.wiwi.examarchive.services;

import ms.wiwi.examarchive.Repository;
import ms.wiwi.examarchive.model.Motd;

public class MotdService {

    private static Motd motd = null;
    private final Repository repository;

    public MotdService(Repository repository) {
        this.repository = repository;
        motd = repository.getMotdText();
    }

    public void updateMotd(Motd motd){
        MotdService.motd = motd;
        repository.updateMotd(motd);
    }

    public static Motd getMotd(){
        if(motd != null){
            if(motd.isExpired()){
                motd = null;
            }
        }
        return motd;
    }
}
