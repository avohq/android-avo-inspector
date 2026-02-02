package app.avo.inspector;

public class AvoAnonymousId {
  private static String _anonymousId = null;
  
  public static String anonymousId() {
    if (AvoAnonymousId._anonymousId != null && AvoAnonymousId._anonymousId.length() != 0) {
      return AvoAnonymousId._anonymousId;
    }
    if (!AvoInspector.avoStorage.isInitialized()) {
      return "unknown";
    }
    String maybeAnonymousId = null;
    try {
      maybeAnonymousId = AvoInspector.avoStorage.getItem(AvoAnonymousId.storageKey);
    } catch (Exception e) {
      System.err.println("Avo Inspector: Error reading anonymous ID from storage. Please report to support@avo.app." + " " + e);
    }
    if (maybeAnonymousId == null || maybeAnonymousId.length() == 0) {
      AvoAnonymousId._anonymousId = AvoGuid.newGuid();
      try {
        AvoInspector.avoStorage.setItem(AvoAnonymousId.storageKey, AvoAnonymousId._anonymousId);
      } catch (Exception e) {
        System.err.println("Avo Inspector: Error saving anonymous ID to storage. Please report to support@avo.app." + " " + e);
      }
    } else {
      AvoAnonymousId._anonymousId = maybeAnonymousId;
    }
    return AvoAnonymousId._anonymousId;
  }
  
  public static void setAnonymousId(String id) {
    AvoAnonymousId._anonymousId = id;
    try {
      AvoInspector.avoStorage.setItem(AvoAnonymousId.storageKey, AvoAnonymousId._anonymousId);
    } catch (Exception e) {
      System.err.println("Avo Inspector: Error saving anonymous ID to storage. Please report to support@avo.app." + " " + e);
    }
  }
  
  public static final String storageKey = "AvoInspectorAnonymousId";
  
  public static void clearCache() {
    AvoAnonymousId._anonymousId = null;
  }
  
}
