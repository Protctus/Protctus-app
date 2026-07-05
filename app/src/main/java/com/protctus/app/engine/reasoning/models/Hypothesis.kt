package com.protctus.app.engine.reasoning.models

enum class Hypothesis(val displayName: String) {
    BENIGN_ACTIVITY("Benign Activity"),
    POTENTIALLY_UNWANTED_ACTIVITY("Potentially Unwanted Activity (PUA)"),
    MALICIOUS_ACTIVITY("Malicious Activity"),
    UNKNOWN_THREAT_PATTERN("Unknown Threat Pattern")
}
