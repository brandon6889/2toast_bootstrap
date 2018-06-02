package net.minecraft.bootstrap;

import LZMA.LzmaInputStream;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.Proxy.Type;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import java.text.SimpleDateFormat;

import javax.swing.JOptionPane;

public final class Bootstrap extends Frame {
   public static final String LAUNCHER_URL = "http://2toast.net/minecraft/launcher/launcher.pack.lzma"; //change to HTTPS
   public static final SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
   private final File workDir;
   private final Proxy proxy;
   private final File launcherJar;
   private final File packedLauncherJar;
   private final File packedLauncherJarNew;
   private final PasswordAuthentication proxyAuth;
   private final String[] remainderArgs;
   private final StringBuilder outputBuffer = new StringBuilder();
   private final Toaster toaster;

   public Bootstrap(File workDir, Proxy proxy, PasswordAuthentication proxyAuth, String[] remainderArgs) {
      super("2Toast Minecraft Launcher");
      this.workDir = workDir;
      this.proxy = proxy;
      this.proxyAuth = proxyAuth;
      this.remainderArgs = remainderArgs;
      this.launcherJar = new File(workDir, "launcher.jar");
      this.packedLauncherJar = new File(workDir, "launcher.pack.lzma");
      this.packedLauncherJarNew = new File(workDir, "launcher.pack.lzma.new");
      this.setSize(854, 480);
      this.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent arg0) {
              new Thread() {
                  @Override
                  public void run() {
                      try {
                          Thread.sleep(5000L);
                      } catch (InterruptedException e) {
                          e.printStackTrace();
                      }
                      System.out.println("FORCING EXIT!");
                      System.exit(0);
                  }
              }.start();
              System.exit(0);
          }
      });
      this.setBackground(new Color(102, 0, 0));
      this.toaster = new Toaster();
      this.toaster.startThread();
      this.add(this.toaster);
      this.setLocationRelativeTo((Component)null);
      this.setVisible(true);
      this.print("\n== 2Toasty Bootstrap v1.1 ==\n\n");
      this.print("time : " + sdf.format(new Date()) + "\n");
      this.print("os   : " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + "\n");
      this.print("java : " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + " " + System.getProperty("sun.arch.data.model") + "\n\n");
   }

   public void execute(boolean force) {
      if(this.packedLauncherJarNew.isFile()) {
         this.println("Found cached update");
         this.renameNew();
      }

      Downloader.Controller controller = new Downloader.Controller();
      if(!force && this.packedLauncherJar.exists()) {
         String httpDate = this.getHttpDate(this.packedLauncherJar);
         Thread thread = new Thread(new Downloader(controller, this, this.proxy, httpDate, this.packedLauncherJarNew));
         thread.setName("Launcher downloader");
         thread.start();

         try {
            this.println("Looking for update");
            boolean e = controller.foundUpdateLatch.await(3L, TimeUnit.SECONDS);
            if(controller.foundUpdate.get()) {
               this.println("Found update in time, waiting to download");
               controller.hasDownloadedLatch.await();
               this.renameNew();
            } else if(!e) {
               this.println("Didn\'t find an update in time.");
            }
         } catch (InterruptedException var6) {
            this.toaster.message = "Error: " + var6.toString();
            throw new FatalBootstrapError("Got interrupted: " + var6.toString());
         }
      } else {
         Downloader md5 = new Downloader(controller, this, this.proxy, (String)null, this.packedLauncherJarNew);
         md5.run();
         if(controller.hasDownloadedLatch.getCount() != 0L) {
            this.toaster.message = "Unable to Download";
            throw new FatalBootstrapError("Unable to download while being forced");
         }

         this.renameNew();
      }
      this.toaster.message = "Extracting";

      this.unpack();
      this.toaster.setAnimationState(1);
      this.toaster.message = "Launching";
      synchronized (this.toaster) {
          try { this.toaster.wait(); } catch (Exception e) {}
      }
      this.startLauncher(this.launcherJar);
   }

   public void unpack() {
      File lzmaUnpacked = this.getUnpackedLzmaFile(this.packedLauncherJar);
      LzmaInputStream inputHandle = null;
      FileOutputStream outputHandle = null;
      this.println("Reversing LZMA on " + this.packedLauncherJar + " to " + lzmaUnpacked);

      try {
         inputHandle = new LzmaInputStream(new FileInputStream(this.packedLauncherJar));
         outputHandle = new FileOutputStream(lzmaUnpacked);
         byte[] jarOutputStream = new byte[65536];

         for(int e = inputHandle.read(jarOutputStream); e >= 1; e = inputHandle.read(jarOutputStream)) {
            outputHandle.write(jarOutputStream, 0, e);
         }
      } catch (Exception var18) {
         this.toaster.message = "Corrupted Download";
         throw new FatalBootstrapError("Unable to un-lzma: " + var18);
      } finally {
         closeSilently(inputHandle);
         closeSilently(outputHandle);
      }

      this.println("Unpacking " + lzmaUnpacked + " to " + this.launcherJar);
      JarOutputStream jarOutputStream1 = null;

      try {
         jarOutputStream1 = new JarOutputStream(new FileOutputStream(this.launcherJar));
         Pack200.newUnpacker().unpack(lzmaUnpacked, jarOutputStream1);
      } catch (Exception var16) {
         this.toaster.message = "Corrupted Download";
         throw new FatalBootstrapError("Unable to un-pack200: " + var16);
      } finally {
         closeSilently(jarOutputStream1);
      }

      this.println("Cleaning up " + lzmaUnpacked);
      lzmaUnpacked.delete();
   }

   public static void closeSilently(Closeable closeable) {
      if(closeable != null) {
         try {
            closeable.close();
         } catch (IOException var2) {
            ;
         }
      }
   }

   private File getUnpackedLzmaFile(File packedLauncherJar) {
      String filePath = packedLauncherJar.getAbsolutePath();
      if(filePath.endsWith(".lzma")) {
         filePath = filePath.substring(0, filePath.length() - 5);
      }

      return new File(filePath);
   }

   public String getMd5(File file) {
      DigestInputStream stream = null;

      Object read;
      try {
         stream = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("MD5"));
         byte[] ignored = new byte[65536];

         for(int read1 = stream.read(ignored); read1 >= 1; read1 = stream.read(ignored)) {
            ;
         }

         return String.format("%1$032x", new Object[]{new BigInteger(1, stream.getMessageDigest().digest())});
      } catch (Exception var8) {
         read = null;
      } finally {
         closeSilently(stream);
      }

      return (String)read;
   }
   
   public String getHttpDate(File file) {
      String s = "";
      try {
         s = sdf.format(file.lastModified());
      } catch (Exception ex) { }
      return s;
   }

   public void println(String string) {
      this.print("[bootstrap] " + string + "\n");
   }

   public void print(String string) {
      System.out.print(string);
      this.outputBuffer.append(string);
   }

   public void startLauncher(File launcherJar) {
      this.println("Starting launcher.");

      try {
         Class e = (new URLClassLoader(new URL[]{launcherJar.toURI().toURL()})).loadClass("net.minecraft.LauncherFrame");
         Constructor constructor = e.getConstructor(new Class[]{Frame.class, File.class, Proxy.class, PasswordAuthentication.class, String[].class, Integer.class});
         constructor.newInstance(new Object[]{this, this.workDir, this.proxy, this.proxyAuth, this.remainderArgs, Integer.valueOf(5)});
      } catch (Exception var4) {
         throw new FatalBootstrapError("Unable to start: " + var4);
      }
   }

   public void renameNew() {
      if(this.packedLauncherJar.exists() && !this.packedLauncherJar.isFile() && !this.packedLauncherJar.delete()) {
         throw new FatalBootstrapError("while renaming, target path: " + this.packedLauncherJar.getAbsolutePath() + " is not a file and we failed to delete it");
      } else {
         if(this.packedLauncherJarNew.isFile()) {
            this.println("Renaming " + this.packedLauncherJarNew.getAbsolutePath() + " to " + this.packedLauncherJar.getAbsolutePath());
            if(this.packedLauncherJarNew.renameTo(this.packedLauncherJar)) {
               this.println("Renamed successfully.");
            } else {
               if(this.packedLauncherJar.exists() && !this.packedLauncherJar.canWrite()) {
                  this.toaster.message = "Access Denied";
                  throw new FatalBootstrapError("unable to rename: target" + this.packedLauncherJar.getAbsolutePath() + " not writable");
               }

               this.println("Unable to rename - could be on another filesystem, trying copy & delete.");
               if(this.packedLauncherJarNew.exists() && this.packedLauncherJarNew.isFile()) {
                  try {
                     copyFile(this.packedLauncherJarNew, this.packedLauncherJar);
                     if(this.packedLauncherJarNew.delete()) {
                        this.println("Copy & delete succeeded.");
                     } else {
                        this.println("Unable to remove " + this.packedLauncherJarNew.getAbsolutePath() + " after copy.");
                     }
                  } catch (IOException var2) {
                     this.toaster.message = "Access Denied";
                     throw new FatalBootstrapError("unable to copy:" + var2);
                  }
               } else {
                  this.println("Nevermind... file vanished?");
               }
            }
         }
      }
   }

   public static void copyFile(File source, File target) throws IOException {
      if(!target.exists()) {
         target.createNewFile();
      }

      FileChannel sourceChannel = null;
      FileChannel targetChannel = null;

      try {
         sourceChannel = (new FileInputStream(source)).getChannel();
         targetChannel = (new FileOutputStream(target)).getChannel();
         targetChannel.transferFrom(sourceChannel, 0L, sourceChannel.size());
      } finally {
         if(sourceChannel != null) {
            sourceChannel.close();
         }

         if(targetChannel != null) {
            targetChannel.close();
         }

      }

   }

   public static void main(String[] args) throws IOException {
      if (System.getProperty("java.specification.version").equals("1.6") || System.getProperty("java.specification.version").equals("1.7")) {
         JOptionPane.showMessageDialog(null, "Java 7 and lower not supported. Please update to latest Java 8 64-bit.", "2Toast Launcher", JOptionPane.INFORMATION_MESSAGE);
         System.exit(2);
      }
      System.setProperty("java.net.preferIPv4Stack", "true");
      sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
      OptionParser optionParser = new OptionParser();
      optionParser.allowsUnrecognizedOptions();
      optionParser.accepts("help", "Show help").forHelp();
      optionParser.accepts("force", "Force updating");
      ArgumentAcceptingOptionSpec proxyHostOption = optionParser.accepts("proxyHost", "Optional").withRequiredArg();
      ArgumentAcceptingOptionSpec proxyPortOption = optionParser.accepts("proxyPort", "Optional").withRequiredArg().defaultsTo("8080", new String[0]).ofType(Integer.class);
      ArgumentAcceptingOptionSpec proxyUserOption = optionParser.accepts("proxyUser", "Optional").withRequiredArg();
      ArgumentAcceptingOptionSpec proxyPassOption = optionParser.accepts("proxyPass", "Optional").withRequiredArg();
      ArgumentAcceptingOptionSpec workingDirectoryOption = optionParser.accepts("workDir", "Optional").withRequiredArg().ofType(File.class).defaultsTo(Util.getWorkingDirectory(), new File[0]);
      NonOptionArgumentSpec nonOptions = optionParser.nonOptions();

      OptionSet optionSet;
      try {
         optionSet = optionParser.parse(args);
      } catch (OptionException var26) {
         optionParser.printHelpOn((OutputStream)System.out);
         System.out.println("(to pass in arguments to minecraft directly use: \'--\' followed by your arguments");
         return;
      }

      if(optionSet.has("help")) {
         optionParser.printHelpOn((OutputStream)System.out);
      } else {
         String hostName = (String)optionSet.valueOf((OptionSpec)proxyHostOption);
         Proxy proxy = Proxy.NO_PROXY;
         if(hostName != null) {
            try {
               proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(hostName, ((Integer)optionSet.valueOf((OptionSpec)proxyPortOption))));
            } catch (Exception var25) {
               ;
            }
         }

         String proxyUser = (String)optionSet.valueOf((OptionSpec)proxyUserOption);
         String proxyPass = (String)optionSet.valueOf((OptionSpec)proxyPassOption);
         PasswordAuthentication passwordAuthentication = null;
         if(!proxy.equals(Proxy.NO_PROXY) && stringHasValue(proxyUser) && stringHasValue(proxyPass)) {
            passwordAuthentication = new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
            Authenticator.setDefault(new Authenticator() {
               @Override
               protected PasswordAuthentication getPasswordAuthentication() {
                  //return passwordAuthentication;
                  return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
               }
            });
         }

         File workingDirectory = (File)optionSet.valueOf((OptionSpec)workingDirectoryOption);
         if(workingDirectory.exists() && !workingDirectory.isDirectory()) {
            throw new FatalBootstrapError("Invalid working directory: " + workingDirectory);
         } else if(!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            throw new FatalBootstrapError("Unable to create directory: " + workingDirectory);
         } else {
            List strings = optionSet.valuesOf((OptionSpec)nonOptions);
            String[] remainderArgs = (String[])strings.toArray(new String[strings.size()]);
            boolean force = optionSet.has("force");
            Bootstrap frame = new Bootstrap(workingDirectory, proxy, passwordAuthentication, remainderArgs);

            try {
               frame.execute(force);
            } catch (Throwable var24) {
               ByteArrayOutputStream stracktrace = new ByteArrayOutputStream();
               var24.printStackTrace(new PrintStream(stracktrace));
               StringBuilder report = new StringBuilder();
               report.append(stracktrace).append("\n\n-- Head --\nStacktrace:\n").append(stracktrace).append("\n\n").append(frame.outputBuffer);
               report.append("\tMinecraft.Bootstrap Version: 5");

               frame.println("FATAL ERROR: " + stracktrace.toString());
               frame.println("\nPlease fix the error and restart.");
            }
         }
      }
   }

   public static boolean stringHasValue(String string) {
      return string != null && !string.isEmpty();
   }
}
