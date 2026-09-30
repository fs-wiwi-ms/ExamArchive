package ms.wiwi.examarchive.services;

import io.javalin.http.Context;
import ms.wiwi.examarchive.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class AzureService {
    private static final Logger logger = LoggerFactory.getLogger(AzureService.class);
    private final JsonMapper jsonMapper = new JsonMapper();
    private final Repository repository;
    private final String authToken;
    private double currentCredits;
    private double currentAmount;
    private Instant lastUpdate;

    public AzureService(String authToken, Repository repository){
        this.authToken = authToken;
        this.repository = repository;
    }

    public void loadFromDatabase(){
        AzureCreditDTO azureCreditDTO = repository.getAzureCredits();
        currentCredits = azureCreditDTO.credits();
        currentAmount = azureCreditDTO.currentAmount();
        lastUpdate = azureCreditDTO.lastUpdate();
    }

    public double getCurrentCredits() {
        if(isOutdated()){
            logger.warn("AzureService: credits outdated");
            return 0;
        }
        return currentCredits;
    }

    public double getCurrentAmount() {
        if(isOutdated()){
            logger.warn("AzureService: CurrentAmount outdated");
            return 0;
        }
        return currentAmount;
    }

    public void handleWebhook(Context ctx){
        if(ctx.header("Authorization") == null || !ctx.header("Authorization").equals("Bearer " + authToken)){
            ctx.status(403);
            ctx.result("Invalid Authorization");
            logger.info("Invalid Authorization "  + ctx.header("Authorization"));
            return;
        }
        String body = ctx.body();
        JsonNode jsonNode = jsonMapper.readTree(body);
        currentCredits = jsonNode.get("credits").asDouble();
        currentAmount = jsonNode.get("currentAmount").asDouble();
        lastUpdate = Instant.now();
        repository.updateLastKnownAzureCredits(currentAmount, currentCredits);
    }

    private boolean isOutdated(){
        return lastUpdate.until(Instant.now()).getSeconds() > 60*60*2;
    }

    public record AzureCreditDTO(double credits, double currentAmount, Instant lastUpdate){
    }
}