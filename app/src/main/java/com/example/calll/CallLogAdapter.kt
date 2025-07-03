package com.example.calll

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CallLogAdapter(private val callLogs: List<CallLogEntry>) :
    RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val callTypeIcon: ImageView = view.findViewById(R.id.call_type_icon)
        val fromNumber: TextView = view.findViewById(R.id.from_number)
        val callDate: TextView = view.findViewById(R.id.date)
        val callTime: TextView = view.findViewById(R.id.time)
        val duration: TextView = view.findViewById(R.id.duration)
        val callStatus: TextView = view.findViewById(R.id.server_status)
        val carrierName: TextView = view.findViewById(R.id.carrier_name)
        val simSerialNumber: TextView = view.findViewById(R.id.sim_serial_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = callLogs[position]

        // Set icon based on call status
        val callIcon = when (log.callStatus) {
            "Incoming" -> R.drawable.ic_incoming_call
            "Outgoing" -> R.drawable.ic_outgoing_call
            "Missed" -> R.drawable.ic_missed_call
            "Rejected" -> R.drawable.ic_rejected_call
            else -> R.drawable.ic_unknown_call
        }
        holder.callTypeIcon.setImageResource(callIcon)

        // Display number
        holder.fromNumber.text = log.fromNumber

        // Display date and time
        holder.callDate.text = "Date: ${log.date}"
        holder.callTime.text = "Time: ${log.time}"

        // Duration: 0 for missed, otherwise show formatted duration
        val durationText = if (log.callStatus == "Missed") {
            "Duration: 0 sec"
        } else {
            "Duration: ${formatDuration(log.duration)}"
        }
        holder.duration.text = durationText

        // Show server status with color
        when {
            log.serverStatus == "Pending" -> {
                holder.callStatus.text = "Server Status: Pending"
                holder.callStatus.setTextColor(Color.RED)
            }
            log.serverStatus == "Posted" -> {
                holder.callStatus.text = "Server Status: Posted"
                holder.callStatus.setTextColor(Color.GREEN)
            }
            else -> {
                holder.callStatus.text = "Server Status: Unknown"
                holder.callStatus.setTextColor(Color.GRAY)
            }
        }

        // Show carrier and SIM info
        holder.carrierName.text = "Carrier: ${log.carrierName ?: "Unknown"}"
        holder.simSerialNumber.text = "SIM: ${log.simSerialNumber ?: "Unknown"}"
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) {
            "${minutes} min ${secs} sec"
        } else {
            "${secs} sec"
        }
    }

    override fun getItemCount(): Int = callLogs.size
}
