package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.AgentResponse
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerTimelineA2uiMapper @Inject constructor() {
    fun map(response: AgentResponse.CustomerTimelineReady): A2uiMessage =
        A2uiMessage(
            messageId = UUID.randomUUID().toString(),
            component = A2uiComponent.CustomerTimelineCard(
                title = "Conta de ${response.result.customerName}",
                customerName = response.result.customerName,
                currentBalanceText = response.result.currentBalanceText,
                items = response.result.items.map {
                    A2uiTimelineItem(
                        dateText = it.dateText,
                        label = it.label,
                        amountText = it.amountText,
                    )
                },
                emptyMessage = response.result.emptyMessage,
                dataSource = response.dataSource.name,
            ),
        )
}
