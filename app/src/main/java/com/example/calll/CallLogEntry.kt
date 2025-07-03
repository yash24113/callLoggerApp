package com.example.calll


data class CallLogEntry(
    val fromNumber: String,
    val duration: Int,
    val callStatus: String,
    val date: String,
    val time: String,
    val serverStatus: String,
    val simSerialNumber: String?,
    val carrierName: String?
)
