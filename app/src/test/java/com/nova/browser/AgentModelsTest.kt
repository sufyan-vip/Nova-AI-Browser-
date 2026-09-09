package com.nova.browser

import com.google.common.truth.Truth.assertThat
import com.nova.browser.features.agent.engine.AgentAction
import com.nova.browser.features.agent.engine.AgentActionType
import com.nova.browser.features.agent.engine.AgentRun
import com.nova.browser.features.agent.engine.AgentStep
import com.nova.browser.features.agent.engine.StepStatus
import org.junit.Test

class AgentModelsTest {

    @Test
    fun `action type parsing is case and space tolerant`() {
        assertThat(AgentActionType.from("click_text")).isEqualTo(AgentActionType.CLICK_TEXT)
        assertThat(AgentActionType.from("CLICK TEXT")).isEqualTo(AgentActionType.CLICK_TEXT)
        assertThat(AgentActionType.from("Navigate")).isEqualTo(AgentActionType.NAVIGATE)
    }

    @Test
    fun `unknown actions fall back to finish rather than crashing`() {
        assertThat(AgentActionType.from("teleport")).isEqualTo(AgentActionType.FINISH)
        assertThat(AgentActionType.from(null)).isEqualTo(AgentActionType.FINISH)
    }

    @Test
    fun `every action describes itself without throwing`() {
        AgentActionType.entries.forEach { type ->
            val description = AgentAction(action = type.name.lowercase()).describe()
            assertThat(description).isNotEmpty()
        }
    }

    @Test
    fun `progress is zero for an empty run`() {
        assertThat(AgentRun().progress).isEqualTo(0f)
    }

    @Test
    fun `progress counts finished steps only`() {
        val run = AgentRun(
            steps = listOf(
                AgentStep(0, AgentAction(), StepStatus.SUCCESS),
                AgentStep(1, AgentAction(), StepStatus.FAILED),
                AgentStep(2, AgentAction(), StepStatus.RUNNING),
                AgentStep(3, AgentAction(), StepStatus.PENDING)
            )
        )
        assertThat(run.progress).isEqualTo(0.5f)
        assertThat(run.completedCount).isEqualTo(1)
        assertThat(run.currentIndex).isEqualTo(2)
    }

    @Test
    fun `step duration is null until it finishes`() {
        val running = AgentStep(0, AgentAction(), StepStatus.RUNNING, startedAt = 1_000)
        assertThat(running.durationMs).isNull()
        val done = running.copy(status = StepStatus.SUCCESS, finishedAt = 1_500)
        assertThat(done.durationMs).isEqualTo(500)
    }
}
