package ms.wiwi.examarchive.services;

public class AIService {
    /**
     * AI Exam flow:
     *  1.) Filter year, prof, ... in ui
     *  2.) Filter send to endpoint
     *  3.) Vision Scans fetched from DB
     *  4.) If Scan not available in DB, Scan via Azure Foundry or Azure Vision (Maybe async in parralel)
     *  5.) Send query to Azure GPT-5.4-Tera to create LaTeX
     *  6.) Send LaTeX to render service (some container, to be chosen)
     *  7.) Get PDF
     *  8.) Upload PDF to S3
     *  9.) Save entry in DB for generated Exam (Delete when user is deleted!)
     *  10.) Send presinged download link to client
     */
}
