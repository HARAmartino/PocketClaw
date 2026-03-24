package com.pocketclaw.agent.security

import com.pocketclaw.agent.validator.ActionValidatorImpl
import com.pocketclaw.agent.validator.PendingAction
import com.pocketclaw.agent.validator.ValidationResult
import javax.inject.Inject

/**
 * Abstraction for security policy rules.
 *
 * Default: [HardcodedSecurityPolicy] delegates to [ActionValidatorImpl].
 *
 * Future: YamlSecurityPolicy loads declarative rules from
 * PocketClaw_Workspace/security_policy.json for NemoClaw-style policy management.
 *
 * To roll back any policy change: git revert the policy file commit.
 * To swap policies: change 1 line in AppModule.kt Hilt binding.
 */
interface SecurityPolicy {
    fun isHardDenied(action: PendingAction): Boolean
    fun isSoftDenied(action: PendingAction): Boolean
    val policyVersion: String
}

/** Default implementation: delegates all policy decisions to [ActionValidatorImpl]. */
class HardcodedSecurityPolicy @Inject constructor(
    private val validator: ActionValidatorImpl,
) : SecurityPolicy {
    override fun isHardDenied(action: PendingAction): Boolean =
        validator.validate(action) is ValidationResult.HardDeny

    override fun isSoftDenied(action: PendingAction): Boolean =
        validator.validate(action) is ValidationResult.SoftDeny

    override val policyVersion: String = "hardcoded-v1"
}
