package com.protctus.app.engine.reasoning.models

enum class Capability(val displayName: String) {
    DYNAMIC_CODE_EXECUTION("Dynamic Code Execution"),
    CREDENTIAL_CAPTURE("Credential Capture"),
    NETWORK_COMMUNICATION("Network Communication"),
    PRIVILEGE_USAGE("Privilege Usage"),
    PERSISTENCE("Persistence"),
    STEALTH("Stealth"),
    DATA_COLLECTION("Data Collection"),
    USER_INTERFACE_MANIPULATION("UI Manipulation")
}
