package com.kidswatch.tv.data

sealed class PairingState {
    data object Loading : PairingState()
    data class Unpaired(val code: String) : PairingState()
    data class Paired(val familyId: String) : PairingState()
}
