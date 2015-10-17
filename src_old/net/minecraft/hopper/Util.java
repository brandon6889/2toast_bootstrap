package net.minecraft.hopper;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;

public class Util {
   public static String performPost(URL url, String parameters, Proxy proxy, String contentType, boolean returnErrorPage) throws IOException {
      HttpURLConnection connection = (HttpURLConnection)url.openConnection(proxy);
      byte[] paramAsBytes = parameters.getBytes(Charset.forName("UTF-8"));
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(15000);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
      connection.setRequestProperty("Content-Length", "" + paramAsBytes.length);
      connection.setRequestProperty("Content-Language", "en-US");
      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setDoOutput(true);
      DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
      writer.write(paramAsBytes);
      writer.flush();
      writer.close();

      BufferedReader reader;
      try {
         reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      } catch (IOException var11) {
         if(!returnErrorPage) {
            throw var11;
         }

         InputStream response = connection.getErrorStream();
         if(response == null) {
            throw var11;
         }

         reader = new BufferedReader(new InputStreamReader(response));
      }

      StringBuilder response1 = new StringBuilder();

      String line;
      while((line = reader.readLine()) != null) {
         response1.append(line);
         response1.append('\r');
      }

      reader.close();
      return response1.toString();
   }

   public static URL constantURL(String input) {
      try {
         return new URL(input);
      } catch (MalformedURLException var2) {
         throw new Error(var2);
      }
   }
}
