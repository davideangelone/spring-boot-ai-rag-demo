package it.davideangelone.assistant.agent.orchestrator;

import it.davideangelone.assistant.agent.executor.ToolExecutionAgent;
import it.davideangelone.assistant.agent.grader.RelevanceGraderAgent;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.agent.planner.QueryPlannerAgent;
import it.davideangelone.assistant.agent.synthesizer.ResponseSynthesizerAgent;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgenticRagOrchestrator {

    private static final int MAX_ITERATIONS = 2;

    private final QueryPlannerAgent plannerAgent;
    private final ToolExecutionAgent executionAgent;
    private final RelevanceGraderAgent graderAgent;
    private final ResponseSynthesizerAgent synthesizerAgent;

    public AgenticRagOrchestrator(QueryPlannerAgent plannerAgent,
                                  ToolExecutionAgent executionAgent,
                                  RelevanceGraderAgent graderAgent,
                                  ResponseSynthesizerAgent synthesizerAgent) {
        this.plannerAgent = plannerAgent;
        this.executionAgent = executionAgent;
        this.graderAgent = graderAgent;
        this.synthesizerAgent = synthesizerAgent;
    }

    public AskResponse orchestrate(String question, String conversationId) {
        log.info("[ORCHESTRATOR] Avvio ciclo multi-agente per conversazione '{}'", conversationId);
        RetrievedDocumentsContext retrievedContext = new RetrievedDocumentsContext();

        // 1. Fase di Analisi Semantica e Pianificazione
        QueryPlan plan = plannerAgent.plan(question);

        // 2. Ciclo di Esecuzione Tool & Valutazione con Retroazione Correttiva
        ToolExecutionResult currentExecution = null;
        EvaluationResult currentEvaluation = null;
        String feedback = null;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info("[ORCHESTRATOR] Iterazione di esecuzione {}/{}", iteration, MAX_ITERATIONS);

            currentExecution = executionAgent.execute(question, plan, feedback, retrievedContext);
            currentEvaluation = graderAgent.evaluate(question, plan, currentExecution);

            if (currentEvaluation.isSufficient() || iteration == MAX_ITERATIONS) {
                if (!currentEvaluation.isSufficient()) {
                    log.warn("[ORCHESTRATOR] Raggiunto limite massimo iterazioni ({}) con dati ancora parziali.", MAX_ITERATIONS);
                } else {
                    log.info("[ORCHESTRATOR] Valutazione positiva al tentativo {}.", iteration);
                }
                break;
            }

            feedback = currentEvaluation.feedback();
            log.info("[ORCHESTRATOR] Dati insufficienti. Avvio ciclo correttivo con feedback: {}", feedback);
        }

        // 3. Fase di Sintesi Finale e Validazione Output
        return synthesizerAgent.synthesize(question, plan, currentExecution, currentEvaluation, retrievedContext, conversationId);
    }
}
