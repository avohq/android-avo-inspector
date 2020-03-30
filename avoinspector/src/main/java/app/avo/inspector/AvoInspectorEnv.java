package app.avo.inspector;

public enum AvoInspectorEnv {

    Prod("prod"),
    Dev("dev"),
    Staging("staging");

    private String name;

    AvoInspectorEnv(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
