import com.example.calll.CallLogEntry
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("webhook/call-logs")
    fun sendCallLogs(@Body callLogs: List<CallLogEntry>): Call<List<CallLogEntry>>
}
