package com.aure.androidtuner.root

import com.aure.androidtuner.model.CpuPolicyInfo

class PerformanceCommandBuilder(
) {

    fun buildApplyScript(
        policies: List<CpuPolicyInfo>,
        selectedValues: Map<Int, Int>,
        isReset: Boolean,
    ): String {
        val lines = mutableListOf<String>()

        policies.forEach { policy ->
            val value = selectedValues[policy.id] ?: return@forEach
            val targetMode = if (isReset) "644" else "444"
            lines += "chmod 666 ${policy.scalingMaxPath}"
            lines += "echo $value > ${policy.scalingMaxPath}"
            lines += "chmod $targetMode ${policy.scalingMaxPath}"
        }

        return buildString {
            appendLine("#!/system/bin/sh")
            lines.forEach(::appendLine)
        }
    }
}
