package org.client.scrcpy.utils;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
public class HttpRequest {
    /**
     * Send a GET request to the specified URL.
     *
     * @param url    target URL
     * @param params optional query parameters
     * @return response content
     */
    public static String sendGet(String url, Map<String, String> params) {
        StringBuilder result = new StringBuilder();
        InputStream is = null;
        try {
            String urlNameString = url + (params == null ? "" : ("?" + Util.getParamUrl(params)));
            URL realUrl = new URL(urlNameString);
            // Open the connection to the URL
            HttpURLConnection connection = (HttpURLConnection) realUrl.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            // Configure default request headers
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            // Enable automatic redirects
            connection.setInstanceFollowRedirects(true);
            // Establish the connection
            connection.connect();
            if (connection.getContentEncoding() != null &&
                    !"".equals(connection.getContentEncoding())) {
                String encode = connection.getContentEncoding().toLowerCase();
                if (encode.contains("gzip")) {
                    is = new GZIPInputStream(connection.getInputStream());
                }
            }
            if (null == is) {
                is = connection.getInputStream();
            }
            // Read all response headers
            Map<String, List<String>> map = connection.getHeaderFields();
            // Iterate over the response headers
            // for (String key : map.keySet()) {
            //     System.out.println(key + "--->" + map.get(key));
            // }
            // Handle redirects manually when necessary
            if (connection.getResponseCode() >= 300 && connection.getResponseCode() < 400) {
                String location = "";
                if (map.get("Location") != null && !map.get("Location").isEmpty()) {
                    location = map.get("Location").get(0);
                }
                if (map.get("location") != null && !map.get("location").isEmpty()) {
                    location = map.get("location").get(0);
                }
                if (location != null && !location.isEmpty()) {
                    if (location.startsWith("/")) {
                        return sendGet(url + location, null);
                    }
                    return sendGet(location, null);
                }
            }
            // Read the response via a BufferedReader
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String line = "";
            while ((line = in.readLine()) != null) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
        } catch (Exception e) {
            System.out.println("GET request failed: " + e);
            e.printStackTrace();
        }
        // Always close the input stream
        finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return result.toString();
    }

    /**
     * Send a POST request to the specified URL.
     *
     * @param url   target URL
     * @param param POST body, e.g. name1=value1&name2=value2
     * @return response content
     */
    public static String sendPost(String url, String param) {
        PrintWriter out = null;
        BufferedReader in = null;
        String result = "";
        try {
            URL realUrl = new URL(url);
            // Open the connection to the URL
            URLConnection conn = realUrl.openConnection();
            // Configure default request headers
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            // Required for POST requests
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // Obtain the output stream
            out = new PrintWriter(conn.getOutputStream());
            // Send the request body
            out.print(param);
            // Flush the output buffer
            out.flush();
            // Read the response via a BufferedReader
            in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            System.out.println("POST request failed: " + e);
            e.printStackTrace();
        }
        // Always close the streams
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return result;
    }
}
