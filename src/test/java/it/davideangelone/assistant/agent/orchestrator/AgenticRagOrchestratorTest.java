package it.davideangelone.assistant.agent.orchestrator;

import java.util.List;
import it.davideangelone.assistant.agent.executor.ToolExecutionAgent;
import it.davideangelone.assistant.agent.grader.RelevanceGraderAgent;
import it.davideangelone.assistant.agent.model.EvaluationResult;
import it.davideangelone.assistant.agent.model.QueryPlan;
import it.davideangelone.assistant.agent.model.ToolExecutionResult;
import it.davideangelone.assistant.agent.planner.QueryPlannerAgent;
import it.davideangelone.assistant.agent.synthesizer.ResponseSynthesizerAgent;
import it.davideangelone.assistant.api.AskResponse;
import it.davideangelone.assistant.config.MessageTrackerAdvisor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class AgenticRagOrchestratorTest {

    @Test
    void testOrchestrationFlowSuccessfulOnFirstTry() {
        QueryPlannerAgent planner = mock(QueryPlannerAgent.class);
        ToolExecutionAgent executor = mock(ToolExecutionAgent.class);
        RelevanceGraderAgent grader = mock(RelevanceGraderAgent.class);
        ResponseSynthesizerAgent synthesizer = mock(ResponseSynthesizerAgent.class);
        MessageTrackerAdvisor advisor = mock(MessageTrackerAdvisor.class);

        QueryPlan mockPlan = new QueryPlan("Test", List.of(), List.of(), "Test");
        ToolExecutionResult mockResult = new ToolExecutionResult("Dati", "Dati", List.of());
        EvaluationResult mockEval = new EvaluationResult(true, true, "HIGH", "OK");
        AskResponse mockResponse = new AskResponse("Risposta", "Ragionamento", List.of(), "model", "conv-1");

        when(advisor.recordUserMessage("conv-1", "domanda")).thenReturn(1);
        when(planner.plan("domanda")).thenReturn(mockPlan);
        when(executor.execute(eq("domanda"), eq(mockPlan), isNull(), any(), eq(1))).thenReturn(mockResult);
        when(grader.evaluate("domanda", mockPlan, mockResult)).thenReturn(mockEval);
        when(synthesizer.synthesize(eq("domanda"), eq(mockPlan), eq(mockResult), eq(mockEval), any(), eq("conv-1")))
                .thenReturn(mockResponse);

        AgenticRagOrchestrator orchestrator = new AgenticRagOrchestrator(planner, executor, grader, synthesizer, advisor);
        AskResponse response = orchestrator.orchestrate("domanda", "conv-1");

        assertNotNull(response);
        assertEquals("Risposta", response.answer());
        verify(executor, times(1)).execute(any(), any(), any(), any(), anyInt());
        verify(advisor).recordUserMessage("conv-1", "domanda");
        verify(advisor).recordAssistantAnswer(eq("conv-1"), eq("Risposta"), any(), any(), any());
    }

    @Test
    void testOrchestrationFlowRetriesWhenInsufficient() {
        QueryPlannerAgent planner = mock(QueryPlannerAgent.class);
        ToolExecutionAgent executor = mock(ToolExecutionAgent.class);
        RelevanceGraderAgent grader = mock(RelevanceGraderAgent.class);
        ResponseSynthesizerAgent synthesizer = mock(ResponseSynthesizerAgent.class);
        MessageTrackerAdvisor advisor = mock(MessageTrackerAdvisor.class);

        QueryPlan mockPlan = new QueryPlan("Test", List.of(), List.of(), "Test");
        ToolExecutionResult mockResult1 = new ToolExecutionResult("Dati parziali", "Dati parziali", List.of());
        ToolExecutionResult mockResult2 = new ToolExecutionResult("Dati completi", "Dati completi", List.of());
        EvaluationResult mockFail = new EvaluationResult(true, false, "LOW", "Cerca anche stato ordine");
        EvaluationResult mockPass = new EvaluationResult(true, true, "HIGH", "OK");
        AskResponse mockResponse = new AskResponse("Risposta", "Ragionamento", List.of(), "model", "conv-1");

        when(advisor.recordUserMessage("conv-1", "domanda")).thenReturn(1);
        when(planner.plan("domanda")).thenReturn(mockPlan);
        when(executor.execute(eq("domanda"), eq(mockPlan), isNull(), any(), eq(1))).thenReturn(mockResult1);
        when(executor.execute(eq("domanda"), eq(mockPlan), eq("Cerca anche stato ordine"), any(), eq(1))).thenReturn(mockResult2);
        when(grader.evaluate("domanda", mockPlan, mockResult1)).thenReturn(mockFail);
        when(grader.evaluate("domanda", mockPlan, mockResult2)).thenReturn(mockPass);
        when(synthesizer.synthesize(any(), any(), any(), any(), any(), any())).thenReturn(mockResponse);

        AgenticRagOrchestrator orchestrator = new AgenticRagOrchestrator(planner, executor, grader, synthesizer, advisor);
        AskResponse response = orchestrator.orchestrate("domanda", "conv-1");

        assertNotNull(response);
        verify(executor, times(2)).execute(any(), any(), any(), any(), anyInt());
        verify(advisor).recordUserMessage("conv-1", "domanda");
        verify(advisor).recordAssistantAnswer(eq("conv-1"), eq("Risposta"), any(), any(), any());
    }
}
