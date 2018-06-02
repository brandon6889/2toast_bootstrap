package net.minecraft.bootstrap;

import java.io.File;

public class Util {
   public static final String APPLICATION_NAME = "minecraft2toast";

   public static Util.OS getPlatform() {
      String osName = System.getProperty("os.name").toLowerCase();
      return osName.contains("win")?Util.OS.WINDOWS:(osName.contains("mac")?Util.OS.MACOS:(osName.contains("linux")?Util.OS.LINUX:(osName.contains("unix")?Util.OS.LINUX:Util.OS.UNKNOWN)));
   }

   public static File getWorkingDirectory() {
      String userHome = System.getProperty("user.home", ".");
      File workingDirectory;
      switch(Util.SyntheticClass_1.$SwitchMap$net$minecraft$bootstrap$Util$OS[getPlatform().ordinal()]) {
      case 1:
      case 2:
         workingDirectory = new File(userHome, "."+APPLICATION_NAME+"/");
         break;
      case 3:
         String applicationData = System.getenv("LOCALAPPDATA");
         String folder = applicationData != null?applicationData:userHome;
         workingDirectory = new File(folder, "."+APPLICATION_NAME+"/");
         break;
      case 4:
         workingDirectory = new File(userHome, "Library/Application Support/"+APPLICATION_NAME);
         break;
      default:
         workingDirectory = new File(userHome, "."+APPLICATION_NAME+"/");
      }

      return workingDirectory;
   }

   static class SyntheticClass_1 {
      static final int[] $SwitchMap$net$minecraft$bootstrap$Util$OS = new int[Util.OS.values().length];

      static {
         try {
            $SwitchMap$net$minecraft$bootstrap$Util$OS[Util.OS.LINUX.ordinal()] = 1;
         } catch (NoSuchFieldError var4) {
            ;
         }

         try {
            $SwitchMap$net$minecraft$bootstrap$Util$OS[Util.OS.SOLARIS.ordinal()] = 2;
         } catch (NoSuchFieldError var3) {
            ;
         }

         try {
            $SwitchMap$net$minecraft$bootstrap$Util$OS[Util.OS.WINDOWS.ordinal()] = 3;
         } catch (NoSuchFieldError var2) {
            ;
         }

         try {
            $SwitchMap$net$minecraft$bootstrap$Util$OS[Util.OS.MACOS.ordinal()] = 4;
         } catch (NoSuchFieldError var1) {
            ;
         }

      }
   }

   public static enum OS {
      LINUX,
      SOLARIS,
      WINDOWS,
      MACOS,
      UNKNOWN;
   }
}
