package com.opendash.app.assistant.router

sealed class RoutingPolicy {
    data class Manual(val providerId: String) : RoutingPolicy()
    data object Auto : RoutingPolicy()
    data class Failover(val ordered: List<String>) : RoutingPolicy()
    data object LowestLatency : RoutingPolicy()
}
