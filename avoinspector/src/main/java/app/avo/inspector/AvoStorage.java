package app.avo.inspector;

public interface AvoStorage {
    boolean isInitialized();
    String getItem(String key);
    void setItem(String key, String value);
}
