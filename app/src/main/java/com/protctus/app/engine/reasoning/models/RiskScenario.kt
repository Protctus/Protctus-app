package com.protctus.app.engine.reasoning.models

enum class RiskScenario(val displayName: String) {
    CREDENTIAL_EXPOSURE("Credential Exposure"),
    UNAUTHORIZED_EXECUTION("Unauthorized Execution"),
    PRIVILEGE_ABUSE("Privilege Abuse"),
    DEFENSE_EVASION("Defense Evasion"),
    SENSITIVE_DATA_ACCESS("Sensitive Data Access"),
    UNKNOWN_TECHNICAL_RISK("Unknown Technical Risk")
}
