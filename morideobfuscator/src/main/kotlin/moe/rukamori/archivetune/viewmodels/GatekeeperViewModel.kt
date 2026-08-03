package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Stub ViewModel provided by the morideobfuscator module on private builds.
 * On Mhsm, blockedMessages never emits; the gate is always open.
 */
class GatekeeperViewModel : ViewModel() {
    val blockedMessages: SharedFlow<String> = MutableSharedFlow()
}
