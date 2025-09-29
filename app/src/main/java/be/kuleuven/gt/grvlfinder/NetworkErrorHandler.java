package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.widget.Toast;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLException;

public class NetworkErrorHandler {

    public static String getReadableErrorMessage(Throwable error) {
        if (error instanceof UnknownHostException) {
            return "No internet connection available";
        } else if (error instanceof SocketTimeoutException) {
            return "Connection timed out - please try again";
        } else if (error instanceof SSLException) {
            return "Secure connection failed";
        } else if (error.getMessage() != null) {
            if (error.getMessage().contains("401")) {
                return "Authentication required - please login again";
            } else if (error.getMessage().contains("403")) {
                return "Access denied - route may be private";
            } else if (error.getMessage().contains("404")) {
                return "Route not found";
            } else if (error.getMessage().contains("429")) {
                return "Too many requests - please wait and try again";
            }
            return error.getMessage();
        }
        return "Network error occurred";
    }

    public static void showNetworkError(Context context, Throwable error) {
        String message = getReadableErrorMessage(error);
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}