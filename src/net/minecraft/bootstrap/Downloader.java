package net.minecraft.bootstrap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.BindException;
import java.net.Proxy;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import java.net.HttpURLConnection;

public class Downloader implements Runnable {
   private static final int MAX_RETRIES = 10;
   private final Proxy proxy;
   private final String currentMd5;
   private final File targetFile;
   private final Downloader.Controller controller;
   private final Bootstrap bootstrap;

   public Downloader(Downloader.Controller controller, Bootstrap bootstrap, Proxy proxy, String currentMd5, File targetFile) {
      this.controller = controller;
      this.bootstrap = bootstrap;
      this.proxy = proxy;
      this.currentMd5 = currentMd5;
      this.targetFile = targetFile;
   }

   @Override
   public void run() {
      int retries = 0;

      while(true) {
         ++retries;
         if(retries > 10) {
            this.log("Unable to download remote file. Check your internet connection/proxy settings.");
            return;
         }

         try {
            URL e = new URL(Bootstrap.LAUNCHER_URL);
            Object connectionObject = e.openConnection(this.proxy);
            HttpURLConnection connection = (HttpURLConnection)connectionObject;
            //HttpsURLConnection connection = this.getConnection(e);
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-store,max-age=0,no-cache");
            connection.setRequestProperty("Expires", "0");
            connection.setRequestProperty("Pragma", "no-cache");
            if(this.currentMd5 != null) {
               //connection.setRequestProperty("If-None-Match", this.currentMd5.toLowerCase());
               connection.setRequestProperty("If-Modified-Since", this.currentMd5);
            }

            connection.setConnectTimeout(30000);
            connection.setReadTimeout(10000);
            this.log("Downloading: " + this.bootstrap.LAUNCHER_URL + (retries > 1?String.format(" (try %d/%d)", new Object[]{Integer.valueOf(retries), Integer.valueOf(10)}):""));
            long start = System.nanoTime();
            connection.connect();
            long elapsed = System.nanoTime() - start;
            this.log("Got reply in: " + elapsed / 1000000L + "ms");
            int code = connection.getResponseCode() / 100;
            if(code == 2) {
               String eTag = connection.getHeaderField("ETag");
               if(eTag == null) {
                  eTag = "-";
               } else {
                  eTag = eTag.substring(1, eTag.length() - 1);
               }

               this.controller.foundUpdate.set(true);
               this.controller.foundUpdateLatch.countDown();
               InputStream inputStream = connection.getInputStream();
               FileOutputStream outputStream = new FileOutputStream(this.targetFile);
               MessageDigest digest = MessageDigest.getInstance("MD5");
               long startDownload = System.nanoTime();
               long bytesRead = 0L;
               byte[] buffer = new byte[65536];

               try {
                  for(int elapsedDownload = inputStream.read(buffer); elapsedDownload >= 1; elapsedDownload = inputStream.read(buffer)) {
                     bytesRead += (long)elapsedDownload;
                     digest.update(buffer, 0, elapsedDownload);
                     outputStream.write(buffer, 0, elapsedDownload);
                  }
               } finally {
                  inputStream.close();
                  outputStream.close();
               }

               long var27 = System.nanoTime() - startDownload;
               float elapsedSeconds = (float)(1L + var27) / 1.0E9F;
               float kbRead = (float)bytesRead / 1024.0F;
               this.log(String.format("Downloaded %.1fkb in %ds at %.1fkb/s", new Object[]{Float.valueOf(kbRead), Integer.valueOf((int)elapsedSeconds), Float.valueOf(kbRead / elapsedSeconds)}));
               String md5sum = String.format("%1$032x", new Object[]{new BigInteger(1, digest.digest())});
               if(eTag.contains("-") || eTag.equalsIgnoreCase(md5sum)) {
                  this.controller.hasDownloadedLatch.countDown();
                  return;
               }

               this.log("After downloading, the MD5 hash didn\'t match. Retrying");
            } else {
               if(code != 4) {
                  this.controller.foundUpdate.set(false);
                  this.controller.foundUpdateLatch.countDown();
                  this.log("No update found.");
                  return;
               }

               this.log("Remote file not found.");
            }
         } catch (Exception var26) {
            this.log("Exception: " + var26.toString());
            this.suggestHelp(var26);
         }
      }
   }

   public void suggestHelp(Throwable t) {
      if(t instanceof BindException) {
         this.log("Recognized exception: the likely cause is a broken ipv4/6 stack. Check your TCP/IP settings.");
      } else if(t instanceof SSLHandshakeException) {
         this.log("Recognized exception: the likely cause is a set of broken/missing root-certificates. Check your java install and perhaps reinstall it.");
      }

   }

   public void log(String str) {
      this.bootstrap.println(str);
   }

   public HttpsURLConnection getConnection(URL url) throws IOException {
      //this.log((url.openConnection(this.proxy).getClass()).toString());
      return (HttpsURLConnection)url.openConnection(this.proxy);
   }

   public static class Controller {
      public final CountDownLatch foundUpdateLatch = new CountDownLatch(1);
      public final AtomicBoolean foundUpdate = new AtomicBoolean(false);
      public final CountDownLatch hasDownloadedLatch = new CountDownLatch(1);
   }
}
