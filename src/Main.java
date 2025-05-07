import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Scanner;

public class Main {
    // Authentication credentials (replace with your actual credentials)
    private static final String BASE_URL = "https://10.X.X.X/api/v1/";
    public static final String STORAGE_USER = "MP_USER";
    public static final String STORAGE_PASS = "MP_PASS";

    public static void disableSSLVerification() throws Exception {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = (hostname, session) -> true;

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    public static String getToken() throws Exception {

        URI uri = URI.create(BASE_URL + "credentials");
        URL url = uri.toURL();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // JSON payload for authentication
        String jsonInputString = "{\"user\": \"" + STORAGE_USER + "\", \"password\": \"" + STORAGE_PASS + "\"}";
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read the response
        int responseCode = connection.getResponseCode();
        //System.out.println("HTTP Response Code: " + responseCode);

        if (responseCode == 200 || responseCode == 201) { // 200 OK
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                String responseBody = response.toString();
                JsonObject data = JsonParser.parseString(responseBody).getAsJsonObject();
                String token = data.get("key").getAsString();
                // Comment the below print for real production
                //System.out.println("Response: " + token);
                System.out.println("\nToken generated successfully\n");
                return token;
            }
        }
        System.err.println("Failed to get token: " + connection.getResponseMessage());
        return null;
    }

    public static void removeSessionKey(String sessionKey) throws Exception {

        URI uri = URI.create(BASE_URL + "credentials/" + sessionKey);
        URL url = uri.toURL();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Content-Type", "application/json");

        int responseCode = connection.getResponseCode();
        System.out.println("\nToken deleted successfully. response code: " + responseCode);
    }

    // Method to get CPG (Common Provisioning Group) information
    public static int getTdvvCount(String cpgName) throws Exception {
        disableSSLVerification(); // Disable SSL verification (not recommended for production)

        Integer tdvvCount = 0;
        String token = getToken(); // Get the token from the API
        String endpoint = BASE_URL + "cpgs/" + cpgName;

        URI uri = URI.create(endpoint);
        URL url = uri.toURL();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Add token to headers
        connection.setRequestProperty("X-HP3PAR-WSAPI-SessionKey", token);
        connection.setRequestProperty("Content-Type", "application/json");

        // Get the HTTP response code
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String responseBody = response.toString();
            //System.out.println(responseBody);
            JsonObject cpgData = JsonParser.parseString(responseBody).getAsJsonObject();
            tdvvCount = cpgData.get("numTDVVs").getAsInt();
        } else {
            // If the response code is not 200, print the error message
            System.err.println("Error: Unable to retrieve CPG information. HTTP Code: " + responseCode);
        }
        removeSessionKey(token);
        return tdvvCount;
    }

    public static void main(String[] args) {
        try {
            // Call the method to get CPG information
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter the CPG Name: ");
            String cpgName = scanner.nextLine();
            Integer dedupVol = getTdvvCount(cpgName.trim());

            if (dedupVol != 0) {
                System.out.println("\nCPG " + cpgName + " has " + dedupVol + " TDVV volumes.");
            } else {
                System.out.println("\nCPG " + cpgName + " is either not present or has zero TDVV volumes");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}