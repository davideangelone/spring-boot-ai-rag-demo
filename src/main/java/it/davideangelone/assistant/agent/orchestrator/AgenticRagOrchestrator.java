package it.davideangelone.assistant.agent.orchestrator;

import it.davideangelone.assistant.agent.executor.ToolExecutionAgent;
import it.davideangelone.assistant.agent.grader.RelevanceGraderAgent;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.agent.planner.QueryPlannerAgent;
import it.davideangelone.assistant.agent.synthesizer.ResponseSynthesizerAgent;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.config.MessageTrackerAdvisor;
import it.davideangelone.assistant.model.RetrievedDocumentsContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgenticRagOrchestrator {

    private static final int MAX_ITERATIONS = 2;

    private final QueryPlannerAgent plannerAgent;
    private final ToolExecutionAgent executionAgent;
    private final RelevanceGraderAgent graderAgent;
    private final ResponseSynthesizerAgent synthesizerAgent;
    private final MessageTrackerAdvisor messageTrackerAdvisor;

    @Autowired
    public AgenticRagOrchestrator(QueryPlannerAgent plannerAgent,
                                  ToolExecutionAgent executionAgent,
                                  RelevanceGraderAgent graderAgent,
                                  ResponseSynthesizerAgent synthesizerAgent,
                                  MessageTrackerAdvisor messageTrackerAdvisor) {
        this.plannerAgent = plannerAgent;
        this.executionAgent = executionAgent;
        this.graderAgent = graderAgent;
        this.synthesizerAgent = synthesizerAgent;
        this.messageTrackerAdvisor = messageTrackerAdvisor;
    }

    public AskResponse orchestrate(String question, String conversationId) {
        int messageNumber = messageTrackerAdvisor.recordUserMessage(conversationId, question);
        log.info("[ORCHESTRATOR] - Msg {} - Avvio ciclo multi-agente per conversazione '{}'", messageNumber, conversationId);

        RetrievedDocumentsContext retrievedContext = new RetrievedDocumentsContext();

        // 1. Fase di Analisi Semantica e Pianificazione
        QueryPlan plan = plannerAgent.plan(question, messageNumber);

        // 2. Ciclo di Esecuzione Tool & Valutazione con Retroazione Correttiva
        ToolExecutionResult currentExecution = null;
        EvaluationResult currentEvaluation = null;
        String feedback = null;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info("[ORCHESTRATOR] - Msg {} - Iterazione di esecuzione {}/{}", messageNumber, iteration, MAX_ITERATIONS);

            currentExecution = executionAgent.execute(question, plan, feedback, retrievedContext, messageNumber);
            currentEvaluation = graderAgent.evaluate(question, plan, currentExecution, messageNumber);

            if (currentEvaluation.isSufficient() || iteration == MAX_ITERATIONS) {
                if (!currentEvaluation.isSufficient()) {
                    log.warn("[ORCHESTRATOR] - Msg {} - Raggiunto limite massimo iterazioni ({}) con dati ancora parziali.", messageNumber, MAX_ITERATIONS);
                } else {
                    log.info("[ORCHESTRATOR] - Msg {} - Valutazione positiva al tentativo {}.", messageNumber, iteration);
                }
                break;
            }

            feedback = currentEvaluation.feedback();
            log.info("[ORCHESTRATOR] - Msg {} - Dati insufficienti. Avvio ciclo correttivo con feedback: {}", messageNumber, feedback);
        }

        // 3. Fase di Sintesi Finale e Validazione Output
        AskResponse response = synthesizerAgent.synthesize(question, plan, currentExecution, currentEvaluation, retrievedContext, conversationId, messageNumber);
        messageTrackerAdvisor.recordAssistantAnswer(conversationId, response.answer(), response.reasoning(), response.sources(), response.model());

        return response;
    }
}
