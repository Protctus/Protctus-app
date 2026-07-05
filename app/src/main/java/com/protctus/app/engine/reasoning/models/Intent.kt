package com.protctus.app.engine.reasoning.models

enum class Intent(val displayName: String) {
    LEGITIMATE_UTILITY("Legitimate Utility"),
    DEVELOPER_TOOL("Developer Tool"),
    ACCESSIBILITY_SOFTWARE("Accessibility Software"),
    AUTOMATION_TOOL("Automation Tool"),
    ENTERPRISE_MANAGEMENT("Enterprise Management"),
    UNKNOWN_UTILITY("Unknown Utility"),
    MALICIOUS_CREDENTIAL_THEFT("Malicious Credential Theft"),
    MALICIOUS_PERSISTENCE("Malicious Persistence"),
    MALICIOUS_EXFILTRATION("Malicious Exfiltration"),
    UNKNOWN_MALICIOUS_INTENT("Unknown Malicious Intent")
}
