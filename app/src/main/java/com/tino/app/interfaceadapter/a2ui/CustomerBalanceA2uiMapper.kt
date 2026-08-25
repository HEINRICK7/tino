package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.AgentResponse
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerBalanceA2uiMapper @Inject constructor() {
    fun map(response: AgentResponse.CustomerBalanceReady): A2uiMessage =
        A2uiMessage(
            messageId = UUID.randomUUID().toString(),
            component = A2uiComponent.CustomerBalanceCard(
                title = response.result.customerName,
                customerName = response.result.customerName,
                currentBalanceText = formatCents(response.result.currentBalanceCents),
                openText = "Em aberto: ${formatCents(response.result.openCents)}",
                overdueText = "Vencido: ${formatCents(response.result.overdueCents)}",
                oldestOpenText = response.result.oldestOpenDays?.let { "Em aberto há $it dias" },
                emptyMessage = if (response.result.currentBalanceCents == 0L) {
                    "Este cliente não tem saldo em aberto."
                } else {
                    null
                },
                dataSource = response.dataSource.name,
            ),
        )

    private fun formatCents(cents: Long): String =
        "R$ %.2f".format(java.util.Locale("pt", "BR"), cents / 100.0)
}
