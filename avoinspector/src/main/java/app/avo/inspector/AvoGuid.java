package app.avo.inspector;

import java.util.UUID;

public class AvoGuid {
    public static String newGuid() {
        return UUID.randomUUID().toString();
    }
}
