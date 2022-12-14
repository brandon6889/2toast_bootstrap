package net.minecraft.bootstrap;

import LZMA.LzmaInputStream;
import java.awt.Component;
import java.awt.Font;
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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.bootstrap.Downloader;
import net.minecraft.bootstrap.FatalBootstrapError;
import net.minecraft.bootstrap.Util;
//import net.minecraft.hopper.HopperService;
import java.text.SimpleDateFormat;

public class Bootstrap extends JFrame {
   private static final Font MONOSPACED = new Font("Monospaced", 0, 12);
   public static final String LAUNCHER_URL = "http://2toast.net/minecraft/launcher/launcher.pack.lzma"; //change to HTTPS
   public static final SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
   private final File workDir;
   private final Proxy proxy;
   private final File launcherJar;
   private final File packedLauncherJar;
   private final File packedLauncherJarNew;
   private final JTextArea textArea;
   private final JScrollPane scrollPane;
   private final PasswordAuthentication proxyAuth;
   private final String[] remainderArgs;
   private final StringBuilder outputBuffer = new StringBuilder();

   public Bootstrap(File workDir, Proxy proxy, PasswordAuthentication proxyAuth, String[] remainderArgs) {
      super("Minecraft Launcher");
      this.workDir = workDir;
      this.proxy = proxy;
      this.proxyAuth = proxyAuth;
      this.remainderArgs = remainderArgs;
      this.launcherJar = new File(workDir, "launcher.jar");
      this.packedLauncherJar = new File(workDir, "launcher.pack.lzma");
      this.packedLauncherJarNew = new File(workDir, "launcher.pack.lzma.new");
      this.setSize(854, 480);
      this.setDefaultCloseOperation(3);
      this.textArea = new JTextArea();
      this.textArea.setLineWrap(true);
      this.textArea.setEditable(false);
      this.textArea.setFont(MONOSPACED);
      ((DefaultCaret)this.textArea.getCaret()).setUpdatePolicy(1);
      this.scrollPane = new JScrollPane(this.textArea);
      this.scrollPane.setBorder((Border)null);
      this.scrollPane.setVerticalScrollBarPolicy(22);
      this.add(this.scrollPane);
      this.setLocationRelativeTo((Component)null);
      this.setVisible(true);
      this.print("\n== 2Toasty Bootstrap v5.1 ==\n\n");
      this.print("time : " + sdf.format(new Date()) + "\n");
      //this.print("time : " + DateFormat.getDateTimeInstance(2, 2, Locale.US).format(new Date()) + "\n");
      this.print("os   : " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + "\n");
      this.print("java : " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " " + System.getProperty("sun.arch.data.model") + "\n\n");
   }

   public void execute(boolean force) {
      if(this.packedLauncherJarNew.isFile()) {
         this.println("Found cached update");
         this.renameNew();
      }

      Downloader.Controller controller = new Downloader.Controller();
      if(!force && this.packedLauncherJar.exists()) {
         //String md51 = this.getMd5(this.packedLauncherJar);
         String httpDate = this.getHttpDate(this.packedLauncherJar);
         //this.println("HTTP Date : " + httpDate);
         Thread thread = new Thread(new Downloader(controller, this, this.proxy, /*md51*/ httpDate, this.packedLauncherJarNew));
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
            throw new FatalBootstrapError("Got interrupted: " + var6.toString());
         }
      } else {
         Downloader md5 = new Downloader(controller, this, this.proxy, (String)null, this.packedLauncherJarNew);
         md5.run();
         if(controller.hasDownloadedLatch.getCount() != 0L) {
            throw new FatalBootstrapError("Unable to download while being forced");
         }

         this.renameNew();
      }

      this.unpack();
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
      Document document = this.textArea.getDocument();
      final JScrollBar scrollBar = this.scrollPane.getVerticalScrollBar();
      boolean shouldScroll = (double)scrollBar.getValue() + scrollBar.getSize().getHeight() + (double)(MONOSPACED.getSize() * 2) > (double)scrollBar.getMaximum();

      try {
         document.insertString(document.getLength(), string, (AttributeSet)null);
      } catch (BadLocationException var6) {
         ;
      }

      if(shouldScroll) {
         SwingUtilities.invokeLater(new Runnable() {
            public void run() {
               scrollBar.setValue(Integer.MAX_VALUE);
            }
         });
      }

   }

   public void startLauncher(File launcherJar) {
      this.println("Starting launcher.");

      try {
         Class e = (new URLClassLoader(new URL[]{launcherJar.toURI().toURL()})).loadClass("net.minecraft.launcher.Launcher");
         Constructor constructor = e.getConstructor(new Class[]{JFrame.class, File.class, Proxy.class, PasswordAuthentication.class, String[].class, Integer.class});
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
               proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(hostName, ((Integer)optionSet.valueOf((OptionSpec)proxyPortOption)).intValue()));
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

               //try {
               //   HopperService.submitReport(proxy, report.toString(), "Minecraft.Bootstrap", "5");
               //} catch (Throwable var23) {
               //   ;
               //}

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
