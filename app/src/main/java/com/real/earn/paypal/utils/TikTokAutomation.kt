package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

object TikTokAutomation {

    suspend fun runBatch(
        service: AccessibilityService,
        numbers: List<String>,
        dialingCode: String,
        autoFlightMode: Boolean,
        onNumberStart: suspend (String) -> Unit,
        onNumberSuccess: suspend (String) -> Unit,
        onNumberFail: suspend (String) -> Unit
    ): Pair<Int, Int> {

        var success = 0
        var fail = 0
        if (numbers.isEmpty()) return Pair(0, 0)

        // ① Flight & Clear Data (Standard)
        if (autoFlightMode) {
            StepUtils.retryStep("Flight cycle") { AirplaneModeController.strictToggle(service) }
        }
        
        // Note: For TikTok, we use its package name for clear data
        val clearOk = StepUtils.retryStep("Clear data") { 
            // You might need to adjust PayPalClearData to accept package name dynamically
            // For now, assuming you have a dynamic clear data function. 
            PayPalClearData.strictClear(service) 
        }
        StepUtils.sleepMs(800)

        // ② Launch & Setup
        if (!StepUtils.retryStep("Launch TikTok") { TikTokActions.launch(service) }) {
            return Pair(0, numbers.size)
        }

        // Wait for login page
        StepUtils.sleepMs(2000)
        TikTokActions.tapUsePhoneOrEmail(service)
        TikTokActions.selectCountry(service, "pakistan") // Default country from prompt

        // ③ Number Loop
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

            // Type & Continue
            if (!TikTokActions.typeNumberAndContinue(service, num, dialingCode)) {
                AutomationState.log("✘ Type/Continue fail")
                onNumberFail(num); fail++
                consecutiveFails++
                TikTokActions.pressBackToLogin(service)
                continue
            }

            // Check Result (Wait 3s for TikTok's UI response)
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
                
                // TikTok showed OTP screen or similar. Go back for next number.
                TikTokActions.pressBackToLogin(service)
                StepUtils.sleepMs(500)
            }
        }

        // Cleanup
        try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } catch (_: Exception) {}
        return Pair(success, fail)
    }
}
