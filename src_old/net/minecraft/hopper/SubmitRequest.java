package net.minecraft.hopper;

import java.util.Map;

public class SubmitRequest {
   private String report;
   private String version;
   private String product;
   private Map environment;

   public SubmitRequest(String report, String product, String version, Map environment) {
      this.report = report;
      this.version = version;
      this.product = product;
      this.environment = environment;
   }
}
