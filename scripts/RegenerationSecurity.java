package eapdf;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import eapdf.security.SecurityVerification;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compiled once per regeneration run against the selected creator JAR. */
class RegenerationSecurity {
  public static void main(String[] args) throws Exception {
    var signing = EapdfConfig.get().signing;
    if (!signing.enabled || !signing.c2pa || !"PADES_B_LTA".equals(signing.protection))
      throw new IllegalArgumentException("Experimental regeneration requires C2PA and PAdES B-LTA.");
    if (args.length == 1 && "preflight".equals(args[0])) {
      EaPdfSecurity.preflight();
      System.out.println("C2PA/PAdES B-LTA signing preflight passed.");
      return;
    }
    if (args.length != 2) throw new IllegalArgumentException("Expected preflight, or PDF and report paths.");
    JsonObject report = SecurityVerification.inspect(Path.of(args[0]), signing.policyPath, "PADES_B_LTA");
    Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    var checks = report.getAsJsonObject("checks");
    boolean valid = report.has("requestedProtectionMet") && report.get("requestedProtectionMet").getAsBoolean()
        && report.has("padesBLta") && report.get("padesBLta").getAsBoolean()
        && !SecurityVerification.unsuccessful(report) && checks != null;
    for (String key : new String[]{"c2pa.signature", "c2pa.binding"})
      valid &= checks != null && checks.has(key)
          && "PASS".equals(checks.getAsJsonObject(key).get("status").getAsString());
    if (!valid) throw new IllegalStateException("C2PA/PAdES B-LTA verification failed; see " + args[1]);
    System.out.println("C2PA/PAdES B-LTA verified: " + args[0]);
  }
}
