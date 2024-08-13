package app.avo.inspector;

public enum AvoInspectorEnv {

    Prod("prod"),
    Dev("dev"),
    Staging("staging");

    private final String name;

    AvoInspectorEnv(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
