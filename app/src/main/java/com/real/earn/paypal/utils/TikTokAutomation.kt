package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

object TikTokAutomation {

    suspend fun runBatch(
        service: AccessibilityService,
        numbers: List<String>,
        countryName: String,
        dialingCode: String,
        autoFlightMode: Boolean,
        onNumberStart: suspend (String) -> Unit,
        onNumberSuccess: suspend (String) -> Unit,
        onNumberFail: suspend (String) -> Unit
    ): Pair<Int, Int> {

        var success = 0
        var fail = 0
        if (numbers.isEmpty()) return Pair(0, 0)

        // ① Flight Mode
        if (autoFlightMode) {
            StepUtils.retryStep("Flight cycle") { AirplaneModeController.strictToggle(service) }
        }
        
        // ② Clear Data (TikTok Package)
        val tiktokPkg = TikTokActions.getInstalledPackage(service)
        StepUtils.retryStep("Clear TikTok data") { 
            PayPalClearData.strictClear(service, tiktokPkg) 
        }
        StepUtils.sleepMs(800)

        // ③ Launch & Smart Setup
        if (!StepUtils.retryStep("Launch TikTok") { TikTokActions.launch(service) }) {
            return Pair(0, numbers.size)
        }

        if (!TikTokActions.handleStartupSequence(service)) {
            AutomationState.log("✘ Failed to reach login screen")
            return Pair(0, numbers.size)
        }

        // ④ Select Country
        if (!TikTokActions.selectCountry(service, countryName)) {
            AutomationState.log("⚠ Country selection failed, attempting to proceed anyway")
        }
        
        // Wait for phone screen to fully load after country select
        StepUtils.sleepMs(1500)

        // ⑤ Number Loop
        var consecutiveFails = 0

        for (num in numbers) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)

            if (consecutiveFails >= 3) {
                AutomationState.log("✘ 3 consecutive fails — ending round")
                break
            }

            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ $num")

            if (!TikTokActions.typeNumberAndContinue(service, num, dialingCode)) {
                AutomationState.log("✘ Type/Continue fail")
                onNumberFail(num); fail++
                consecutiveFails++
                TikTokActions.pressBackToLogin(service)
                continue
            }

            StepUtils.sleepMs(3000)

            if (TikTokActions.hasRedError(service)) {
                AutomationState.log("✘ Red error detected — trying next number")
                onNumberFail(num); fail++
                consecutiveFails++
                TikTokActions.pressBackToLogin(service)
                StepUtils.sleepMs(500)
            } else {
                AutomationState.log("✔ Number accepted (No red error)")
                onNumberSuccess(num); success++
                consecutiveFails = 0
                
                TikTokActions.pressBackToLogin(service)
                StepUtils.sleepMs(500)
            }
        }

        try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } catch (_: Exception) {}
        return Pair(success, fail)
    }
}
